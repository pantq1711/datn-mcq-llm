package com.datn.mcq.bank.dto;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthValue;

/** Bản cuối của một ý câu Đúng/Sai do giáo viên chốt, để chép vào bank_item_statement (GĐ3). */
public record BankStatementInput(StatementKey key, String text, TruthValue label, Difficulty difficulty) {
}
