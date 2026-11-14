package com.datn.mcq.questionimage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionImageDto(
        Long id,
        String role,
        Short ordinal,
        String mime,
        Integer widthPx,
        Integer heightPx,
        Short displayWidthMm,
        String url
) {}
