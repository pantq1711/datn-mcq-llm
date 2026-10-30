package com.datn.mcq.config;

import com.datn.mcq.common.util.CellTextLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Giới hạn theo từng giáo viên để một người không làm tắc hàng đợi sinh câu và không cạn quota Gemini dùng chung (D1),
 * cấu hình ở {@code app.generation-limits.*}. Chỉ câu hệ thống phải sinh mới bị tính; câu lấy từ ngân hàng thì không.
 */
@Component
@ConfigurationProperties("app.generation-limits")
public class GenerationLimitProperties {

    /** Tổng số câu tối đa của một ma trận, hoặc của một đề sinh theo bài (kiểm khi tạo/sửa ma trận và khi sinh theo bài). */
    private int maxQuestionsPerExam = 60;
    /** Số job sinh câu đang chờ hoặc đang chạy tối đa của một giáo viên (đếm qua exam_slot → exam.created_by). */
    private int maxPendingJobs = 60;
    /** Số câu yêu cầu sinh tối đa mỗi ngày (theo giờ Việt Nam) của một giáo viên, tính cả Đổi câu bằng sinh mới. */
    private int maxDailyQuestions = 120;

    /** D2: độ dài tối đa của {@code content} một ô ma trận (ký tự); không vượt {@link CellTextLimits#CONTENT_MAX_CHARS}. */
    private int cellContentMaxChars = CellTextLimits.CONTENT_MAX_CHARS;
    /** D2: độ dài tối đa của mỗi yêu cầu cần đạt (ký tự); không vượt {@link CellTextLimits#OUTCOME_MAX_CHARS}. */
    private int cellOutcomeMaxChars = CellTextLimits.OUTCOME_MAX_CHARS;
    /** D2: số mục yêu cầu cần đạt tối đa của một ô; không vượt {@link CellTextLimits#OUTCOMES_MAX_ITEMS}. */
    private int cellOutcomesMaxItems = CellTextLimits.OUTCOMES_MAX_ITEMS;

    public int getCellContentMaxChars() {
        return cellContentMaxChars;
    }

    public void setCellContentMaxChars(int value) {
        this.cellContentMaxChars = within("cell-content-max-chars", value, CellTextLimits.CONTENT_MAX_CHARS);
    }

    public int getCellOutcomeMaxChars() {
        return cellOutcomeMaxChars;
    }

    public void setCellOutcomeMaxChars(int value) {
        this.cellOutcomeMaxChars = within("cell-outcome-max-chars", value, CellTextLimits.OUTCOME_MAX_CHARS);
    }

    public int getCellOutcomesMaxItems() {
        return cellOutcomesMaxItems;
    }

    public void setCellOutcomesMaxItems(int value) {
        this.cellOutcomesMaxItems = within("cell-outcomes-max-items", value, CellTextLimits.OUTCOMES_MAX_ITEMS);
    }

    public int getMaxQuestionsPerExam() {
        return maxQuestionsPerExam;
    }

    public void setMaxQuestionsPerExam(int maxQuestionsPerExam) {
        this.maxQuestionsPerExam = positive("max-questions-per-exam", maxQuestionsPerExam);
    }

    public int getMaxPendingJobs() {
        return maxPendingJobs;
    }

    public void setMaxPendingJobs(int maxPendingJobs) {
        this.maxPendingJobs = positive("max-pending-jobs", maxPendingJobs);
    }

    public int getMaxDailyQuestions() {
        return maxDailyQuestions;
    }

    public void setMaxDailyQuestions(int maxDailyQuestions) {
        this.maxDailyQuestions = positive("max-daily-questions", maxDailyQuestions);
    }

    /** Từ 1 tới trần cứng của prompt (xem {@link CellTextLimits}): nới quá trần thì khởi động báo lỗi, không cắt âm thầm. */
    private static int within(String name, int value, int ceiling) {
        if (value < 1 || value > ceiling) {
            throw new IllegalArgumentException("app.generation-limits." + name + " phải từ 1 đến " + ceiling
                    + " (trần cứng của prompt), đang là " + value);
        }
        return value;
    }

    private static int positive(String name, int value) {
        if (value < 1) {
            throw new IllegalArgumentException("app.generation-limits." + name + " phải từ 1 trở lên (đang là " + value + ")");
        }
        return value;
    }
}
