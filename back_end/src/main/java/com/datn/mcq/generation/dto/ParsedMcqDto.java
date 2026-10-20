package com.datn.mcq.generation.dto;

import com.datn.mcq.enums.Option;

public record ParsedMcqDto(
        boolean parseSuccess,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        Option correctOption,
        String explanation,
        String parseError
) {
}

