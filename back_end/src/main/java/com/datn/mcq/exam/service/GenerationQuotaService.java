package com.datn.mcq.exam.service;

import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.config.GenerationLimitProperties;
import com.datn.mcq.exam.repository.GenerationQuotaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Giới hạn sinh câu theo từng giáo viên (D1): trần job sinh câu đang chờ/đang chạy và trần số câu yêu cầu sinh mỗi ngày
 * (giờ Việt Nam), để một người không làm tắc hàng đợi dùng chung và không cạn quota Gemini của cả hệ thống. Cấu hình ở
 * {@code app.generation-limits.*}.
 *
 * <p>{@link #reserve} khóa dòng {@code generation_quota} của giáo viên rồi kiểm trần, và phải chạy TRONG transaction của
 * nơi tạo job (propagation MANDATORY) ngay trước khi xếp job: khóa giữ tới commit, nên hai request song song của cùng giáo
 * viên được tuần tự hóa và request sau thấy job của request trước. Vượt trần thì ném 429, transaction rollback, không để lại
 * gì. Chỉ gọi cho câu hệ thống phải sinh; câu lấy từ ngân hàng không gọi. Nơi gọi phải gọi TRƯỚC khi sửa chỗ (truy vấn native
 * ở đây tự ghi các thay đổi đang chờ xuống DB, xem chk_es_ready).
 */
@Service
public class GenerationQuotaService {

    /** 429: giáo viên đang có quá nhiều job sinh câu chờ/chạy. */
    public static final String QUEUE_LIMIT = "GENERATION_QUEUE_LIMIT";
    /** 429: giáo viên đã yêu cầu sinh quá số câu cho phép trong ngày (giờ Việt Nam). */
    public static final String DAILY_LIMIT = "DAILY_GENERATION_LIMIT";
    static final String QUEUE_LIMIT_MESSAGE =
            "Thầy/cô đang có quá nhiều câu chờ soạn. Vui lòng đợi các đề hiện tại soạn xong rồi tạo tiếp.";
    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final GenerationQuotaRepository repository;
    private final GenerationLimitProperties limits;
    private final Clock clock;

    @Autowired
    public GenerationQuotaService(GenerationQuotaRepository repository, GenerationLimitProperties limits) {
        this(repository, limits, Clock.systemUTC());
    }

    GenerationQuotaService(GenerationQuotaRepository repository, GenerationLimitProperties limits, Clock clock) {
        this.repository = repository;
        this.limits = limits;
        this.clock = clock;
    }

    /**
     * Giữ chỗ {@code newJobs} câu cần sinh cho giáo viên: kiểm trần ngày rồi trần job chờ, đạt thì cộng vào bộ đếm ngày.
     *
     * @throws ApiErrorException 429 {@value #DAILY_LIMIT} (kèm Retry-After tới 0 giờ giờ Việt Nam) hoặc 429
     *                           {@value #QUEUE_LIMIT}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Integer userId, int newJobs) {
        if (newJobs <= 0) {
            return;
        }
        LocalDate today = today(clock);
        repository.ensureRow(userId, today);
        int used = repository.lockAndReadUsed(userId, today);
        if (used + newJobs > limits.getMaxDailyQuestions()) {
            long retryAfter = secondsUntilNextDay(clock);
            throw ApiErrorException.tooManyRequests(DAILY_LIMIT,
                    dailyMessage(used, newJobs, limits.getMaxDailyQuestions(), retryAfter, today), retryAfter);
        }
        if (repository.countActiveGenerateJobs(userId) + newJobs > limits.getMaxPendingJobs()) {
            throw ApiErrorException.tooManyRequests(QUEUE_LIMIT, QUEUE_LIMIT_MESSAGE, null);
        }
        repository.setUsed(userId, today, used + newJobs);
    }

    static String dailyMessage(int used, int requested, int limit, long retryAfterSeconds, LocalDate today) {
        int remaining = Math.max(0, limit - used);
        Duration wait = Duration.ofSeconds(retryAfterSeconds);
        long hours = wait.toHours();
        long minutes = wait.toMinutesPart() + (wait.toSecondsPart() > 0 ? 1 : 0);
        if (minutes == 60) {
            hours++;
            minutes = 0;
        }
        String in = hours > 0 ? hours + " giờ " + minutes + " phút" : Math.max(1, minutes) + " phút";
        return "Thầy/cô chỉ được yêu cầu soạn tối đa " + limit + " câu mỗi ngày (giờ Việt Nam). Hôm nay thầy/cô đã dùng "
                + used + " câu, còn " + remaining + " câu, trong khi yêu cầu này cần " + requested
                + " câu. Thầy/cô có thể tạo tiếp từ 0 giờ ngày " + today.plusDays(1).format(DATE_VN) + " (sau khoảng "
                + in + "), hoặc dùng câu có sẵn trong ngân hàng.";
    }

    /** Ngày hiện tại theo giờ Việt Nam. */
    static LocalDate today(Clock clock) {
        return ZonedDateTime.now(clock.withZone(VIETNAM)).toLocalDate();
    }

    /** Số giây từ bây giờ tới 0 giờ giờ Việt Nam kế tiếp (ít nhất 1). */
    static long secondsUntilNextDay(Clock clock) {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(VIETNAM));
        return Math.max(1, Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(VIETNAM)).getSeconds());
    }
}
