package com.datn.mcq.exam.service;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.JobStatus;
import com.datn.mcq.enums.SaJudgeKind;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.qc.dto.GroundingVerdict;
import com.datn.mcq.qc.service.QcSummaryService.QcSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Giữ công cụ đo chương 4 (tools/measurement/do_luong/co_qc.py) tính lý do "cần xem kỹ" giống backend: cùng fixture
 * tools/measurement/tests/fixtures/co-qc-parity.json được test Python đọc. Đổi QcSummary.reviewReasons hay
 * ExamViewService.addGroundingJobReason mà test này đỏ thì sửa luôn co_qc.py và fixture.
 * Thuần (không Spring, không DB).
 */
class QcFlagParityTest {

    static final Path FIXTURE = Path.of("..", "tools", "measurement", "tests", "fixtures", "co-qc-parity.json");

    @Test
    void reviewReasonsMatchSharedFixture() throws Exception {
        JsonNode root = new ObjectMapper().readTree(Files.readString(FIXTURE));
        int checked = 0;
        for (JsonNode c : root.get("cases")) {
            JsonNode qc = c.get("qc");
            List<String> warnings = new ArrayList<>();
            qc.path("format_warnings").forEach(w -> warnings.add(w.asText()));
            boolean answerRan = qc.path("answer_ran").asBoolean(false);
            boolean dedupRan = qc.path("dedup_ran").asBoolean(false);
            Integer duplicateOf = dedupRan && !qc.path("dedup_passed").asBoolean(true)
                    ? qc.get("dedup_compared_to").asInt() : null;
            QcSummary summary = new QcSummary(
                    dedupRan,
                    answerRan ? qc.path("answer_passed").asBoolean() : null,
                    text(qc, "judge_sa_kind") == null ? null : SaJudgeKind.valueOf(upper(qc, "judge_sa_kind")),
                    answerRan && qc.path("has_undecidable").asBoolean(false),
                    warnings,
                    duplicateOf,
                    text(qc, "grounding_verdict") == null ? null : GroundingVerdict.fromDb(text(qc, "grounding_verdict")),
                    text(qc, "judge_answer") == null ? null : AnswerChoice.valueOf(upper(qc, "judge_answer")),
                    qc.path("solver_option_check").asBoolean(false));
            List<String> reasons = new ArrayList<>(summary.reviewReasons());
            Optional<GenerationJob> groundingJob = Optional.empty();
            if (text(qc, "grounding_job_status") != null) {
                GenerationJob job = new GenerationJob();
                job.setStatus(JobStatus.valueOf(upper(qc, "grounding_job_status")));
                groundingJob = Optional.of(job);
            }
            ExamViewService.addGroundingJobReason(groundingJob, reasons);

            List<String> expected = new ArrayList<>();
            c.get("ly_do_qc_summary").forEach(r -> expected.add(r.asText()));
            assertThat(reasons).as(c.get("ten").asText()).containsExactlyElementsOf(expected);
            checked++;
        }
        assertThat(checked).isGreaterThanOrEqualTo(15);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String upper(JsonNode node, String field) {
        return text(node, field).toUpperCase(Locale.ROOT);
    }
}
