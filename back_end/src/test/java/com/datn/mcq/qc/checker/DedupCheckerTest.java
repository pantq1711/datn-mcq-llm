package com.datn.mcq.qc.checker;

import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.qc.dto.DedupOutcome;
import com.datn.mcq.qc.dto.DedupStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DedupChecker là hàm thuần: không Spring, không DB, không LLM.
 * Trùng khi (a) cùng dấu vân tay, hoặc (b) cosine 3-gram >= tau VÀ cùng số liệu/công thức trong thân câu
 * VÀ cùng trạng thái phủ định.
 */
class DedupCheckerTest {

    private static final double TAU = DedupChecker.PROVISIONAL_THRESHOLD;

    private static DedupCandidate q(int id, String stem, String a, String b, String c, String d) {
        return new DedupCandidate(id, stem, a, b, c, d);
    }

    @Test
    void noCandidatesMeansNoDuplicateWithoutComparison() {
        DedupOutcome outcome = DedupChecker.check(q(10, "Tính $2+3$", "$5$", "$6$", "$7$", "$8$"), List.of(), TAU);
        assertEquals(DedupStatus.NO_DUPLICATE, outcome.status());
        assertNull(outcome.comparedToQuestionId());
        assertNull(outcome.similarity());
        assertEquals(TAU, outcome.thresholdUsed());
    }

    @Test
    void sameFingerprintIsDuplicateWithScoreOne() {
        DedupCandidate subject = q(10, "Tính  $2 + 3$", "$8$", "$7$", "$6$", "$5$");
        DedupOutcome outcome = DedupChecker.check(subject,
                List.of(q(3, "Tính $2+3$", "$5$", "$6$", "$7$", "$8$")), TAU);
        assertEquals(DedupStatus.DUPLICATE, outcome.status());
        assertEquals(3, outcome.comparedToQuestionId());
        assertEquals(1.0, outcome.similarity(), 1e-9);
        assertEquals(DedupOutcome.Layer.FINGERPRINT, outcome.layer());
    }

    @Test
    void sameExerciseWithMinorWordingChangeIsDuplicate() {
        DedupCandidate subject = q(10,
                "Kết quả của phép tính $\\sqrt{(-5)^2}$ là:",
                "$-5$", "$5$", "$-25$", "$25$");
        DedupCandidate existing = q(4,
                "Kết quả của phép tính $\\sqrt{(-5)^2}$ là gì?",
                "$-5$", "$5$", "$-25$", "$25$");
        DedupOutcome outcome = DedupChecker.check(subject, List.of(existing), TAU);
        assertEquals(DedupStatus.DUPLICATE, outcome.status());
        assertEquals(DedupOutcome.Layer.TRIGRAM, outcome.layer());
        assertTrue(outcome.similarity() >= TAU);
    }

    @Test
    void sameSkillWithDifferentNumbersIsNotDuplicate() {
        DedupCandidate subject = q(10,
                "Rút gọn phân thức $\\frac{4x^2y}{8xy^3}$ với $x \\neq 0, y \\neq 0$ ta được:",
                "$\\frac{x}{2y^2}$", "$\\frac{2x}{y^2}$", "$\\frac{x}{4y^2}$", "$\\frac{x}{2y}$");
        DedupCandidate existing = q(4,
                "Rút gọn phân thức $\\frac{6x^3y}{9xy^2}$ với $x \\neq 0, y \\neq 0$ ta được:",
                "$\\frac{2x^2}{3y}$", "$\\frac{3x^2}{2y}$", "$\\frac{2x}{3y}$", "$\\frac{x^2}{3y}$");
        DedupOutcome outcome = DedupChecker.check(subject, List.of(existing), TAU);
        assertEquals(DedupStatus.NO_DUPLICATE, outcome.status());
    }

    @Test
    void negatedQuestionWithSameOptionsIsNotDuplicate() {
        DedupCandidate subject = q(10,
                "Bất phương trình nào sau đây không phải là bất phương trình bậc nhất một ẩn?",
                "$2x - 3 > 0$", "$x^2 + 1 \\ge 0$", "$0x + 5 < 0$", "$\\frac{1}{x} - 2 \\le 0$");
        DedupCandidate existing = q(4,
                "Bất phương trình nào sau đây là bất phương trình bậc nhất một ẩn?",
                "$2x - 3 > 0$", "$x^2 + 1 \\ge 0$", "$0x + 5 < 0$", "$\\frac{1}{x} - 2 \\le 0$");
        DedupOutcome outcome = DedupChecker.check(subject, List.of(existing), TAU);
        assertEquals(DedupStatus.NO_DUPLICATE, outcome.status());
    }

    @Test
    void askingForTheFalseStatementIsNotADuplicateOfAskingForTheTrueOne() {
        // Quyết định 22/10: "sai" đứng riêng (có ranh giới từ) là dấu hiệu phủ định.
        String a = "$x^2 \\ge 0$ với mọi $x$";
        String b = "$\\sqrt{4} = 2$";
        String c = "$2^3 = 6$";
        String d = "$\\frac{1}{2} = 0,5$";
        DedupCandidate askFalse = q(10, "Khẳng định nào sau đây là sai?", a, b, c, d);
        DedupCandidate askTrue = q(4, "Khẳng định nào sau đây là đúng?", a, b, c, d);

        assertEquals(DedupStatus.NO_DUPLICATE, DedupChecker.check(askFalse, List.of(askTrue), TAU).status());
        assertEquals(DedupStatus.NO_DUPLICATE, DedupChecker.check(askTrue, List.of(askFalse), TAU).status());
    }

    @Test
    void storedSimilarityForNoDuplicateIsBelowThreshold() {
        // Ứng viên bị chốt chặn loại không được dùng làm "câu gần nhất", để điểm lưu luôn < tau khi không trùng.
        DedupCandidate subject = q(10,
                "Rút gọn phân thức $\\frac{4x^2y}{8xy^3}$ với $x \\neq 0, y \\neq 0$ ta được:",
                "$\\frac{x}{2y^2}$", "$\\frac{2x}{y^2}$", "$\\frac{x}{4y^2}$", "$\\frac{x}{2y}$");
        DedupCandidate vetoed = q(4,
                "Rút gọn phân thức $\\frac{6x^3y}{9xy^2}$ với $x \\neq 0, y \\neq 0$ ta được:",
                "$\\frac{2x^2}{3y}$", "$\\frac{3x^2}{2y}$", "$\\frac{2x}{3y}$", "$\\frac{x^2}{3y}$");
        DedupCandidate unrelated = q(5, "Phân tích đa thức $x^2 - 9$ thành nhân tử", "A", "B", "C", "D");
        DedupOutcome outcome = DedupChecker.check(subject, List.of(vetoed, unrelated), TAU);
        assertEquals(DedupStatus.NO_DUPLICATE, outcome.status());
        if (outcome.similarity() != null) {
            assertTrue(outcome.similarity() < TAU, () -> "similarity=" + outcome.similarity());
        }
    }

    @Test
    void thresholdBoundaryCountsAsDuplicate() {
        DedupCandidate subject = q(10, "Tính $2+3$ bằng bao nhiêu", "$5$", "$6$", "$7$", "$8$");
        DedupCandidate existing = q(4, "Tính $2+3$ bằng", "$5$", "$6$", "$7$", "$8$");
        double score = McqTextNormalizer.trigramCosine(
                McqTextNormalizer.canonicalText(subject.questionText(), subject.optionA(), subject.optionB(),
                        subject.optionC(), subject.optionD()),
                McqTextNormalizer.canonicalText(existing.questionText(), existing.optionA(), existing.optionB(),
                        existing.optionC(), existing.optionD()));
        assertEquals(DedupStatus.DUPLICATE, DedupChecker.check(subject, List.of(existing), score).status());
        assertEquals(DedupStatus.NO_DUPLICATE,
                DedupChecker.check(subject, List.of(existing), Math.nextUp(score)).status());
    }

    @Test
    void picksHighestScoreAndBreaksTiesByLowestId() {
        DedupCandidate subject = q(10, "Tính $2+3$", "$5$", "$6$", "$7$", "$8$");
        DedupOutcome outcome = DedupChecker.check(subject, List.of(
                q(7, "Tính $2+3$", "$5$", "$6$", "$7$", "$8$"),
                q(2, "Tính $2 + 3$", "$8$", "$7$", "$6$", "$5$")), TAU);
        assertEquals(DedupStatus.DUPLICATE, outcome.status());
        assertEquals(2, outcome.comparedToQuestionId());
    }

    @Test
    void candidateWithMissingTextIsSkipped() {
        DedupCandidate subject = q(10, "Tính $2+3$", "$5$", "$6$", "$7$", "$8$");
        DedupOutcome outcome = DedupChecker.check(subject,
                List.of(new DedupCandidate(3, null, null, null, null, null)), TAU);
        assertEquals(DedupStatus.NO_DUPLICATE, outcome.status());
        assertNull(outcome.comparedToQuestionId());
    }

    @Test
    void selfIsNeverComparedWithItself() {
        DedupCandidate subject = q(10, "Tính $2+3$", "$5$", "$6$", "$7$", "$8$");
        DedupOutcome outcome = DedupChecker.check(subject, List.of(subject), TAU);
        assertEquals(DedupStatus.NO_DUPLICATE, outcome.status());
    }
}
