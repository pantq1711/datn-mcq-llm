# Truy vấn thống kê cho chương thử nghiệm (W1-3)

Chỉ là **truy vấn đọc** (PostgreSQL) cho chương thử nghiệm trong báo cáo; chưa có trang thống kê. Mỗi file `.sql` là một
câu lệnh `SELECT` độc lập, chạy được bằng `psql -f` trên database của hệ thống đã triển khai. Test
`StatisticsSqlTest` chạy cả bốn file trên `datn_mcq_test` với dữ liệu mẫu để bảo đảm cú pháp và số đếm đúng.

| File | Trả lời câu hỏi |
|---|---|
| [`01-ket-cuc-cau-llm.sql`](01-ket-cuc-cau-llm.sql) | Tỉ lệ câu LLM được Giữ nguyên / Sửa rồi Giữ / Sửa rồi Đổi / Đổi / chỉ Sửa / chưa thao tác |
| [`02-buoc-qc-bat-loi.sql`](02-buoc-qc-bat-loi.sql) | Bước QC nào bắt lỗi nhiều nhất (format, trùng, Solver lệch, Solver lỗi / chưa chạy), theo dạng câu |
| [`03-truong-giao-vien-hay-sua.sql`](03-truong-giao-vien-hay-sua.sql) | Giáo viên hay sửa trường nào (đề, phương án, đáp án, lời giải...), theo dạng câu |
| [`04-solver-lech-giao-vien-xu-ly.sql`](04-solver-lech-giao-vien-xu-ly.sql) | Với câu Solver báo lệch, giáo viên Giữ nguyên, sửa hay Đổi |

## Bộ lọc chung (đã cài trong từng file)

1. **Chỉ đề chế độ giáo viên**: `exam.mode = 'teacher'` (đề tạo theo ma trận, `/api/exams/**`). Không có đề thủ công.
2. **Chỉ đầu ra của LLM**: câu thuộc run production của ma trận (`experiment_run.prompt_version LIKE 'matrix-%'`) và
   `generated_question.edited_from_question_id IS NULL` (quy tắc bắt buộc của W1-2: bản giáo viên sửa không phải đầu ra
   LLM, xem `docs/contract/enums.md`). Kết cục của một câu LLM được gom theo cả chuỗi bản sửa của nó.
3. **Loại tài khoản của nhóm phát triển**: mỗi file có CTE `dev_users` lấy theo email trong mảng `ARRAY[]::text[]`
   (mặc định rỗng). **Điền email của nhóm vào mảng trước khi chạy** để số liệu không lẫn thao tác thử của nhóm.
4. Các file 02 và 04 chỉ tính câu đã nằm (hoặc từng nằm) trong một chỗ của đề giáo viên, nên câu sinh ra nhưng chưa vào đề
   (ví dụ lần sinh bị lỗi định dạng rồi sinh lại) vẫn được tính ở file 02 qua `generation_job.exam_slot_id`.
5. Dữ liệu trước V13 (chưa có chuỗi bản sửa) và trước V14 (chưa có `solver_error`, `solver_reasoning`) vẫn truy vấn được;
   phần "Solver lỗi" của dữ liệu trước V14 nằm chung trong "Solver không chạy hoặc lỗi".

## Giới hạn phải ghi trong báo cáo

> **Tỉ lệ Giữ / Sửa / Đổi là mức chấp nhận, không phải độ đúng.** Ở chế độ giáo viên (quyết định 22/10 và 23/10) giáo viên
> nhìn thấy đáp án, lời giải và cờ "cần xem kỹ" của hệ thống khi quyết định, nên có thể thiên về chấp nhận. Giữ nguyên
> không có nghĩa câu đúng, và Đổi không có nghĩa câu sai. Độ đúng đáp án trong thực nghiệm phải do nhóm tự kiểm tra hoặc
> tính toán, không suy ra từ việc giáo viên Giữ câu.

Các giới hạn khác:

- Thống kê Giữ/Sửa/Đổi lấy từ `exam_slot_action`; đánh giá `assisted` đã bị loại khỏi mọi chỉ số chấm mù.
- "Bước QC bắt lỗi" nói về việc bước đó **phát hiện** vấn đề (cờ, loại câu), không chứng minh vấn đề có thật: Solver lệch
  có thể do chính Solver sai. Không dùng làm độ chính xác của Solver; phần đó thuộc thử nghiệm "Solver bắt lỗi".
- Số liệu theo **chỗ** và theo **câu LLM**: một chỗ bị Đổi nhiều lần sinh nhiều câu LLM, mỗi câu tính một kết cục.
- Tài khoản và đề thử của nhóm (kể cả bước chạy thử trước khi mời giáo viên) phải được loại bằng `dev_users`.
