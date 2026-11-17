# Nhập ngân hàng câu từ Word: giới hạn đã biết

Nguồn chuẩn của chức năng: quyết định ngày 3/11/2026 trong `docs/ai-development-rules.md` (mô hình B1, phạm vi bản đầu) và
API ở `docs/api/README.md` mục 10. File này chỉ ghi các giới hạn đã chốt giữ nguyên và hướng phát triển; không lặp lại
đặc tả.

## G1. Câu đúng/sai nhập từ Word không được tự lấy vào đề theo ma trận (NV-52, chốt 17/11/2026)

**Hiện trạng.** File Word chỉ ghi một nhãn mức cho cả câu, nên khi nhập, cả bốn ý a–d nhận chung mức đó (quyết định 3/11
mục 3; `docs/api/README.md` mục 10.2, dòng "Hạn chế"). Ví dụ câu ghi `[TH]` thành bốn ý cùng mức Hiểu.

**Quy tắc chọn câu ngân hàng cho ô đúng/sai** đòi chuỗi mức bốn ý trùng khít ô ma trận theo thứ tự a–d
(`ExamSlotRepository.findBankCandidates`). Ô đúng/sai của ma trận thường trộn mức theo ý (ví dụ Biết, Biết, Hiểu, Vận
dụng), nên câu nhập có bốn ý cùng mức gần như không bao giờ khớp. Câu vẫn nằm trong ngân hàng, xem và tìm được ở trang Ngân
hàng, nhưng không được tự lấy vào đề.

**Quyết định:** giữ nguyên quy tắc, không bỏ hay nới điều kiện mức cho câu `origin = 'import'`. Bỏ hoặc nới điều kiện sẽ
làm sai phân bố mức theo ý của ô đúng/sai trong ma trận theo Công văn 7991 (đề in ra có số lệnh hỏi theo mức lệch với ma
trận). Xem "Quyết định ngày 17/11/2026" trong `docs/ai-development-rules.md`.

**Hướng phát triển (chưa làm):**

1. **Gán mức từng ý khi nhập:** nhận nhãn mức riêng cho từng ý trong file (hoặc cho giáo viên chọn mức từng ý ở bản xem
   trước), ghi vào `bank_item_statement.difficulty`. Câu có chuỗi mức đúng thì khớp ô như câu do hệ thống soạn.
2. **Chọn câu ngân hàng bằng tay cho một chỗ (NV-32):** giáo viên tự chọn câu cho chỗ trong đề, khi đó không cần khớp tự
   động chuỗi mức.

Khi làm màn nhập Word ở giao diện (NV-50), nên nói rõ giới hạn này ở bước xác nhận cho câu đúng/sai.
