package com.datn.mcq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;

/** Cột dùng: generated_question.llm_difficulty/final_difficulty, teacher_review.difficulty_label, bank_item.difficulty, generation_job.target_difficulty. */
public enum Difficulty implements DbEnum {
    EASY("easy"),
    MEDIUM("medium"),
    HARD("hard");

    private final String dbValue;

    Difficulty(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Converter(autoApply = true)
    public static class Conv extends DbEnumConverter<Difficulty> {
        public Conv() {
            super(Difficulty.class);
        }
    }
}
