package com.datn.mcq.qc.dto;

import com.datn.mcq.enums.Option;

public record QuestionQcInput(
        Integer questionId,
        boolean parseSuccess,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        Option correctOption,
        String explanation,
        boolean validateLatex,
        boolean runAnswerCheck
) {
}

