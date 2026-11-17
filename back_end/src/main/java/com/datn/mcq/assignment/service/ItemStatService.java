package com.datn.mcq.assignment.service;

import com.datn.mcq.assignment.dto.ItemStatDtos.ExcludedDto;
import com.datn.mcq.assignment.dto.ItemStatDtos.ItemStatDto;
import com.datn.mcq.assignment.dto.ItemStatDtos.ItemStatsDto;
import com.datn.mcq.assignment.repository.AssignmentRepository;
import com.datn.mcq.assignment.repository.AssignmentRepository.AnswerRow;
import com.datn.mcq.assignment.repository.AssignmentRepository.AssignmentRow;
import com.datn.mcq.assignment.repository.AssignmentRepository.AttemptRow;
import com.datn.mcq.assignment.repository.ItemStatRepository;
import com.datn.mcq.assignment.repository.ItemStatRepository.StatRow;
import com.datn.mcq.assignment.service.ItemStatCalculator.ItemResult;
import com.datn.mcq.assignment.service.ItemStatCalculator.ItemSpec;
import com.datn.mcq.assignment.service.ItemStatCalculator.Result;
import com.datn.mcq.assignment.service.ItemStatCalculator.Sheet;
import com.datn.mcq.bank.dto.BankItemDto;
import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.exam.service.ExamAssignmentSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Phân tích câu sau khi làm bài (PR B, docs/design/lam-bai-truc-tuyen.md mục 5 và 14): B1 xem, B2 tính lại.
 *
 * <ul>
 *   <li>Chỉ có khi bài đã đóng VÀ đã qua ân hạn nộp ({@link AttemptRules#GRACE}) tính từ lúc đóng: trong ân hạn các lượt có
 *       hạn không muộn hơn lúc đóng vẫn nộp được, nên tính lúc đóng sẽ sót lượt. Bài mở: 409 {@code ASSIGNMENT_OPEN};
 *       đã đóng nhưng còn ân hạn: 409 {@value #STATS_NOT_READY}, {@code errors[0]} là lúc có thống kê.</li>
 *   <li>Tính khi đọc lần đầu (hoặc lần đầu sau khi bị vô hiệu do xóa lượt), dưới khóa hàng bài giao; B2 luôn tính lại.</li>
 *   <li>Chỉ lượt đã nộp và không trùng lớp + số thứ tự ({@link AttemptRules#duplicateIds}, cùng quy tắc bảng điểm).</li>
 * </ul>
 */
@Service
public class ItemStatService {

    public static final String ASSIGNMENT_OPEN = "ASSIGNMENT_OPEN";
    public static final String STATS_NOT_READY = "STATS_NOT_READY";

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private final AssignmentRepository repository;
    private final ItemStatRepository stats;
    private final AssignmentLoader loader;
    private final ExamAssignmentSource examSource;

    public ItemStatService(AssignmentRepository repository, ItemStatRepository stats, AssignmentLoader loader,
                           ExamAssignmentSource examSource) {
        this.repository = repository;
        this.stats = stats;
        this.loader = loader;
        this.examSource = examSource;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** B1: thống kê của bài giao; tính khi chưa có. */
    @Transactional
    public ItemStatsDto get(Integer examId, Integer assignmentId, Integer actorId) {
        return read(examId, assignmentId, actorId, false);
    }

    /** B2: xóa rồi tính lại thống kê trong một transaction. */
    @Transactional
    public ItemStatsDto recompute(Integer examId, Integer assignmentId, Integer actorId) {
        return read(examId, assignmentId, actorId, true);
    }

    private ItemStatsDto read(Integer examId, Integer assignmentId, Integer actorId, boolean force) {
        LocalDateTime now = now();
        examSource.requireOwned(examId, actorId);
        AssignmentRow row = repository.findInExam(assignmentId, examId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy bài giao id=" + assignmentId));
        row = loader.refresh(row, now);
        requireReady(row, now);
        AssignmentPaper paper = loader.paper(row);
        if (force || row.statsComputedAt() == null) {
            row = repository.lockById(row.id()).orElseThrow();
            if (force || row.statsComputedAt() == null) {
                compute(row, paper, now);
                row = repository.findById(row.id()).orElseThrow();
            }
        }
        return toDto(row, paper);
    }

    private static void requireReady(AssignmentRow row, LocalDateTime now) {
        if (row.open()) {
            throw ApiErrorException.conflict(ASSIGNMENT_OPEN,
                    "Bài giao còn mở. Thống kê câu có sau khi đóng bài " + AttemptRules.GRACE.toMinutes() + " phút.");
        }
        LocalDateTime readyAt = row.closedAt().plus(AttemptRules.GRACE);
        if (now.isBefore(readyAt)) {
            throw new ApiErrorException(HttpStatus.CONFLICT, STATS_NOT_READY,
                    "Học sinh còn được nộp trong " + AttemptRules.GRACE.toMinutes() + " phút ân hạn sau khi đóng."
                            + " Thống kê câu có từ " + readyAt.truncatedTo(ChronoUnit.SECONDS) + ".",
                    List.of(readyAt.toString()));
        }
    }

    private void compute(AssignmentRow row, AssignmentPaper paper, LocalDateTime now) {
        List<AttemptRow> attempts = repository.attempts(row.id());
        Set<Integer> duplicates = AttemptRules.duplicateIds(attempts);
        Map<Integer, Map<Integer, AnswerRow>> answers = new HashMap<>();
        for (AnswerRow a : repository.answersOfAssignment(row.id())) {
            answers.computeIfAbsent(a.attemptId(), x -> new HashMap<>()).put(a.slotId(), a);
        }
        List<Sheet> sheets = new ArrayList<>();
        for (AttemptRow t : attempts) {
            if (t.submittedAt() != null && !duplicates.contains(t.id())) {
                sheets.add(new Sheet(t.id(), t.score(), answers.getOrDefault(t.id(), Map.of())));
            }
        }
        List<ItemSpec> specs = new ArrayList<>();
        Map<Integer, AssignmentPaper.Item> bySlot = new HashMap<>();
        for (AssignmentPaper.Item item : paper.items()) {
            BankItemDto b = item.bank();
            specs.add(new ItemSpec(item.row().slotId(), item.type(),
                    b.correctOption() == null ? null : b.correctOption().getDbValue(),
                    AssignmentPaper.tfLabels(b), b.saAnswer()));
            bySlot.put(item.row().slotId(), item);
        }
        Result result = ItemStatCalculator.compute(specs, sheets);
        List<StatRow> rows = new ArrayList<>(result.items().size());
        for (ItemResult r : result.items()) {
            AssignmentPaper.Item item = bySlot.get(r.slotId());
            rows.add(new StatRow(r.slotId(), item.row().bankItemId(), item.row().questionType(), result.n(),
                    result.groupSize(), r.p(), r.d(), json(r.choiceCounts()), json(r.flags()), now));
        }
        stats.replace(row.id(), rows, now);
    }

    private ItemStatsDto toDto(AssignmentRow row, AssignmentPaper paper) {
        Map<Integer, StatRow> bySlot = new HashMap<>();
        for (StatRow r : stats.list(row.id())) {
            bySlot.put(r.slotId(), r);
        }
        int n = bySlot.isEmpty() ? 0 : bySlot.values().iterator().next().n();
        int k = bySlot.isEmpty() ? 0 : bySlot.values().iterator().next().groupSize();

        List<AttemptRow> attempts = repository.attempts(row.id());
        Set<Integer> duplicates = AttemptRules.duplicateIds(attempts);
        int notSubmitted = 0;
        int duplicateSubmitted = 0;
        for (AttemptRow t : attempts) {
            if (t.submittedAt() == null) {
                notSubmitted++;
            } else if (duplicates.contains(t.id())) {
                duplicateSubmitted++;
            }
        }

        Map<Integer, Integer> current = examSource.currentBankItems(row.examId());
        List<ItemStatDto> items = new ArrayList<>();
        for (AssignmentPaper.Item item : paper.items()) {
            StatRow r = bySlot.get(item.row().slotId());
            if (r == null) {
                continue;
            }
            items.add(new ItemStatDto(item.row().slotId(), item.row().part(), item.row().numberInPart(),
                    item.row().questionType(), item.row().bankItemId(), answerKey(item), r.p(), r.d(),
                    parse(r.choiceCountsJson(), MAP), parse(r.flagsJson(), STRINGS),
                    !Objects.equals(current.get(item.row().slotId()), item.row().bankItemId())));
        }
        return new ItemStatsDto(row.id(), n, k, row.statsComputedAt(), n >= ItemStatCalculator.MIN_SAMPLE,
                ItemStatCalculator.MIN_SAMPLE, sampleNote(n, k), new ExcludedDto(duplicateSubmitted, notSubmitted),
                List.copyOf(items));
    }

    private static String answerKey(AssignmentPaper.Item item) {
        BankItemDto b = item.bank();
        return switch (item.type()) {
            case MCQ -> b.correctOption() == null ? null : b.correctOption().getDbValue();
            case TF -> AssignmentPaper.tfLabels(b);
            case SA -> b.saAnswer();
        };
    }

    /** Câu nhắc giới hạn mẫu hiện ngay trên bảng (thiết kế mục 5.5, 5.7). */
    static String sampleNote(int n, int k) {
        if (n == 0) {
            return "Chưa có lượt nộp hợp lệ nào nên chưa có số liệu.";
        }
        if (n < ItemStatCalculator.MIN_SAMPLE) {
            return "Mới có " + n + " lượt hợp lệ (cần ít nhất " + ItemStatCalculator.MIN_SAMPLE + "): độ phân biệt D và"
                    + " các cờ chưa đáng tin, chỉ nên xem p và số lượt chọn.";
        }
        return n + " lượt hợp lệ; nhóm điểm cao và nhóm điểm thấp mỗi nhóm " + k + " lượt (27%). Một học sinh trả lời"
                + " khác đi có thể làm D của một câu đổi tới 1/" + k + ", nên cờ là gợi ý xem lại, không phải kết luận"
                + " câu kém.";
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không ghi được thống kê câu", e);
        }
    }

    private static <T> T parse(String text, TypeReference<T> type) {
        try {
            return JSON.readValue(text, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("item_stat không đọc được", e);
        }
    }
}
