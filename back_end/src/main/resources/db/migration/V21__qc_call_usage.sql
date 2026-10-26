-- V21__qc_call_usage.sql
-- W2-3 (ngày 26/10): đo chi phí của hai bước QC gọi LLM. Không sửa V1-V20.
--
-- Token và độ trễ của lượt gọi LLM được ghi vào DÒNG KẾT QUẢ mà lượt gọi đó cho ra, thay vì vào job:
-- * quality_check_result (chỉ dòng answer_check): Solver chạy trong job sinh câu không phải một job riêng nên job không có
--   chỗ ghi riêng cho nó (generation_job.latency_ms là của lượt SINH câu); kết quả Solver nằm cùng một chỗ dù chạy ở đâu.
-- * question_grounding: kết quả của judge "bám tài liệu".
-- Dòng cũ để NULL. Kết quả Solver được CHÉP từ bản trước (bản giáo viên chỉ đổi lời giải) cũng để NULL vì không có lượt gọi
-- nào, nên tổng chi phí không bị đếm hai lần. Chỉ lưu lượt gọi thành công cho ra kết quả; lượt thử lại thất bại không có dòng
-- kết quả nên không được tính. Chưa có cột chi phí tiền: LlmClient không tính giá (cost_usd luôn 0), nhân token với đơn giá
-- model ở nơi báo cáo.

ALTER TABLE quality_check_result
    ADD COLUMN token_count INT,
    ADD COLUMN latency_ms  DOUBLE PRECISION,
    ADD CONSTRAINT chk_qcr_usage CHECK (
        (token_count IS NULL OR token_count >= 0) AND (latency_ms IS NULL OR latency_ms >= 0)),
    ADD CONSTRAINT chk_qcr_usage_answer_check CHECK (
        check_type = 'answer_check' OR (token_count IS NULL AND latency_ms IS NULL));

ALTER TABLE question_grounding
    ADD COLUMN token_count INT,
    ADD COLUMN latency_ms  DOUBLE PRECISION,
    ADD CONSTRAINT chk_qg_usage CHECK (
        (token_count IS NULL OR token_count >= 0) AND (latency_ms IS NULL OR latency_ms >= 0));

COMMENT ON COLUMN quality_check_result.token_count IS
    'V21: token của lượt gọi Solver cho ra dòng answer_check; NULL với dòng khác, dòng cũ và kết quả chép từ bản trước.';
COMMENT ON COLUMN quality_check_result.latency_ms IS
    'V21: độ trễ (mili giây) của lượt gọi Solver đó; cùng quy tắc NULL với token_count.';
COMMENT ON COLUMN question_grounding.token_count IS 'V21: token của lượt gọi judge cho ra kết quả này; NULL với dòng cũ.';
COMMENT ON COLUMN question_grounding.latency_ms IS 'V21: độ trễ (mili giây) của lượt gọi judge đó; NULL với dòng cũ.';
