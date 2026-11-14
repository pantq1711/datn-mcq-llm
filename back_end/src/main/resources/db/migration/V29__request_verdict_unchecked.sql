-- Solver hai lượt (14/11, hotfix, solver-v6-two-pass). Không sửa V1-V28.
-- Đo TN1 với chip common_trap: khối yêu cầu trong CÙNG lượt gọi làm Solver bắt câu nhiều đáp án đúng kém đi (4/9 so với
-- 7/9 và 5/9 của v4), nên yêu cầu của giáo viên được kiểm ở lượt gọi thứ hai.
--
-- * quality_check_result.request_verdict thêm giá trị 'unchecked': câu có yêu cầu nhưng không được kiểm (lượt 1 gắn cờ đáp
--   án nên không gọi lượt 2, hoặc lượt 2 lỗi / trả kết quả hỏng). Do backend đặt, lý do ở request_reason. Không bật cờ.
-- Lưu ý số migration: PR phương án nhiễu (PA1) và câu có hình (I1) đang dùng V29 TẠM; PR merge sau đổi số khi rebase.

ALTER TABLE quality_check_result DROP CONSTRAINT chk_qcr_request_verdict;
ALTER TABLE quality_check_result
    ADD CONSTRAINT chk_qcr_request_verdict CHECK (request_verdict IS NULL OR request_verdict IN ('met', 'partial',
        'not_met', 'unchecked'));

COMMENT ON COLUMN quality_check_result.request_verdict IS
    'Kết luận Solver về yêu cầu của giáo viên (V28, V29): met, partial, not_met; unchecked khi có yêu cầu mà không kiểm '
    '(lượt giải gắn cờ hoặc lượt kiểm yêu cầu lỗi); NULL khi không có yêu cầu.';
