package com.datn.mcq.llm.prompt;

import com.datn.mcq.enums.Difficulty;

/**
 * Một câu mẫu độ sâu (12/11) do giáo viên chọn, theo mức. Chỉ dùng để model học độ sâu và cách hỏi, đưa vào prompt như
 * DỮ LIỆU ({@link MatrixPromptDraftV0#depthExamplesBlock}); nội dung nằm ngoài repo ({@link DepthExampleCatalog}).
 */
public record DepthExample(Difficulty level, String text) {}
