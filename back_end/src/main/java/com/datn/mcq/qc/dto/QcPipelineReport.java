package com.datn.mcq.qc.dto;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.QuestionStatus;

import java.util.List;

public record QcPipelineReport(
        QuestionStatus resultingStatus,
        boolean formatPassed,
        List<String> formatErrors,
        Boolean answerCheckPassed,
        AnswerChoice judgeAnswer,
        String judgeModel,
        String solverReasoning
) {
}

