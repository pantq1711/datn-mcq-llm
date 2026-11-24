package com.datn.mcq.generation.service;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GeneratedQuestionStatement;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.generation.repository.GeneratedQuestionRepository;
import com.datn.mcq.generation.repository.GeneratedQuestionStatementRepository;
import com.datn.mcq.generation.repository.GenerationJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H8 (thiết kế G0, chốt phòng ngừa): câu có token hình {@code ⟦img:…⟧} ở phần Solver nhìn thấy (đề dẫn, phương án,
 * ý Đúng/Sai) không bao giờ được xếp job Solver, vì mô hình chỉ thấy chuỗi token chứ không thấy ảnh nên kết luận
 * "consistent" sẽ như một câu sạch (B10, E2E 22/11). Sau chốt H2 ({@code ExamQuestionEditTest}) không còn đường HTTP
 * nào đưa token tới đây; test này khóa chốt ở mức dịch vụ.
 */
class QuestionEditImageGuardTest {

    private GenerationJobRepository jobRepository;
    private GeneratedQuestionStatementRepository statementRepository;
    private QuestionEditService service;

    @BeforeEach
    void setUp() {
        jobRepository = mock(GenerationJobRepository.class);
        statementRepository = mock(GeneratedQuestionStatementRepository.class);
        service = new QuestionEditService(mock(GeneratedQuestionRepository.class), statementRepository, jobRepository,
                null, null, null, null);
    }

    private static GeneratedQuestion mcq(String stem, String a, String b, String c, String d) {
        GeneratedQuestion q = mock(GeneratedQuestion.class);
        when(q.getId()).thenReturn(5);
        when(q.getRunId()).thenReturn(1);
        when(q.getTemplateId()).thenReturn(1);
        when(q.getQuestionType()).thenReturn(QuestionType.MCQ);
        when(q.getQuestionText()).thenReturn(stem);
        when(q.getOptionA()).thenReturn(a);
        when(q.getOptionB()).thenReturn(b);
        when(q.getOptionC()).thenReturn(c);
        when(q.getOptionD()).thenReturn(d);
        return q;
    }

    @Test
    void cauSachDuocXepJobSolver() {
        service.enqueueAnswerCheck(mcq("Tính $x$.", "$1$", "$2$", "$3$", "$4$"), QuestionType.MCQ);

        ArgumentCaptor<GenerationJob> job = ArgumentCaptor.forClass(GenerationJob.class);
        verify(jobRepository).save(job.capture());
        assertEquals(GenerationJob.TYPE_ANSWER_CHECK, job.getValue().getJobType());
    }

    @Test
    void tokenODeDanHoacPhuongAnKhongXepJobSolver() {
        String t = "⟦img:7⟧";
        service.enqueueAnswerCheck(mcq("Cho hình " + t + " Tính $x$.", "$1$", "$2$", "$3$", "$4$"), QuestionType.MCQ);
        service.enqueueAnswerCheck(mcq("Tính $x$.", t, "$2$", "$3$", "$4$"), QuestionType.MCQ);
        service.enqueueAnswerCheck(mcq("Tính $x$.", "$1$", "$2$", "$3$", "$4$ " + t), QuestionType.MCQ);

        verify(jobRepository, never()).save(any(GenerationJob.class));
    }

    @Test
    void tokenOYDungSaiKhongXepJobSolver() {
        GeneratedQuestion q = mock(GeneratedQuestion.class);
        when(q.getId()).thenReturn(6);
        when(q.getQuestionType()).thenReturn(QuestionType.TF);
        when(q.getQuestionText()).thenReturn("Cho hình thang.");
        GeneratedQuestionStatement clean = mock(GeneratedQuestionStatement.class);
        when(clean.getStatementText()).thenReturn("Ý sạch");
        when(clean.getLlmDifficulty()).thenReturn(Difficulty.EASY);
        GeneratedQuestionStatement figure = mock(GeneratedQuestionStatement.class);
        when(figure.getStatementText()).thenReturn("Ý có hình ⟦img:9⟧");
        when(figure.getLlmDifficulty()).thenReturn(Difficulty.EASY);
        when(statementRepository.findByQuestionIdOrderByPositionAsc(6)).thenReturn(List.of(clean, clean, clean, figure));

        service.enqueueAnswerCheck(q, QuestionType.TF);

        verify(jobRepository, never()).save(any(GenerationJob.class));
    }
}
