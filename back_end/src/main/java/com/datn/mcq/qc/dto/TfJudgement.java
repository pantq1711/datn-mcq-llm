package com.datn.mcq.qc.dto;

import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthChoice;

/** Nhãn Solver cho một ý câu Đúng/Sai trong báo cáo QC (người nghiên cứu, không trả về form chấm). */
public record TfJudgement(StatementKey key, TruthChoice judgeLabel) {
}
