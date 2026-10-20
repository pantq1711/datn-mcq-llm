package com.datn.mcq.llm.dto;

public record LlmCallResult(
        String rawText,
        Integer tokenCount,
        Double latencyMs,
        Double costUsd,
        String modelName
) {
}

