# Truy vấn thống kê cho chương thử nghiệm (W1-3)

Chỉ là **truy vấn đọc** (PostgreSQL) cho chương thử nghiệm trong báo cáo; chưa có trang thống kê. Mỗi file `.sql` là một
câu lệnh `SELECT` độc lập, chạy được bằng `psql -f` trên database của hệ thống đã triển khai. Test
`StatisticsSqlTest` chạy bốn file đầu trên `datn_mcq_test` với dữ liệu mẫu để bảo đảm cú pháp và số đếm đúng; file 05 và 06 (W2-3) được
`GroundingPipelineTest` kiểm số đếm, file 07 (giám sát, 08/11) được `StatisticsSqlTest` kiểm số đếm job, file 11 (hậu kiểm, PR B
17/11) được `HauKiemSqlTest` bắt khớp với service, và `StatisticsSqlTest` kiểm mọi file chỉ là một câu lệnh đọc.

| File | Trả lời câu hỏi |
|---|---|
| [`01-ket-cuc-cau-llm.sql`](01-ket-cuc-cau-llm.sql) | Tỉ lệ câu LLM được Giữ nguyên / Sửa rồi Giữ / Sửa rồi Đổi / Đổi / chỉ Sửa / chưa thao tác |
| [`02-buoc-qc-bat-loi.sql`](02-buoc-qc-bat-loi.sql) | Bước QC nào bắt lỗi nhiều nhất (format, trùng, Solver lệch, Solver lỗi / chưa chạy), theo dạng câu |
| [`03-truong-giao-vien-hay-sua.sql`](03-truong-giao-vien-hay-sua.sql) | Giáo viên hay sửa trường nào (đề, phương án, đáp án, lời giải...), theo dạng câu |
| [`04-solver-lech-giao-vien-xu-ly.sql`](04-solver-lech-giao-vien-xu-ly.sql) | Với câu Solver báo lệch, giáo viên Giữ nguyên, sửa hay Đổi |
| [`05-grounding.sql`](05-grounding.sql) | Câu sinh có tài liệu bài học: tỉ lệ SUPPORTED / NOT_SUPPORTED / UNKNOWN, chưa chấm, lỗi; trích dẫn kiểm chứng được; câu bị hạ vì trích dẫn không có thật (theo dạng câu, có/không tài liệu) |
| [`06-chi-phi.sql`](06-chi-phi.sql) | Chi phí trung bình mỗi câu LLM (token và thời gian gọi LLM) = sinh + Solver + grounding, theo dạng câu và có/không tài liệu; số câu thiếu số đo (V21) |
| [`07-van-hanh.sql`](07-van-hanh.sql) | Vận hành hàng đợi theo ngày và loại job: số job đã kết thúc, tỉ lệ thất bại, thời gian chờ và xử lý (p50, p95), job bị stale reset |
| [`10-yeu-cau-giao-vien.sql`](10-yeu-cau-giao-vien.sql) | Yêu cầu của giáo viên (chip, PR B): tỉ lệ `request_check` met / partial / not_met theo từng mã và theo mức lấy yêu cầu (replace, cell, exam), kèm tỉ lệ tính cả câu chưa kiểm (cận dưới); số lượt Đổi câu ngay sau câu not_met hay partial của cùng chỗ (gom theo chuỗi bản sửa); lọc theo cửa sổ `checked_at` của Solver |
| [`11-hau-kiem.sql`](11-hau-kiem.sql) | Hậu kiểm bằng dữ liệu học sinh (bài giao trực tuyến): mỗi câu của mỗi bài giao đã đóng quá 2 phút một dòng gồm n, k, p, D và cờ hậu kiểm, **tự tính lại từ bài làm gốc** (không đọc `item_stat`), chỉ lượt đã nộp và không trùng lớp + số thứ tự (hoặc lớp + họ tên ở bài giao chế độ họ tên, V36); khớp service (`HauKiemSqlTest`). Bảng chéo tiền kiểm × hậu kiểm làm bằng Python (`co_qc.py`) qua `bank_item_id`, không bằng SQL |

## Số liệu vận hành thời gian thực (Prometheus)

Các truy vấn `.sql` ở trên đo **chất lượng** và vận hành theo ngày từ DB. Số liệu vận hành thời gian thực nằm ở
`/actuator/prometheus` (xem [`docs/deploy/README.md`](../deploy/README.md) mục 6b), tiền tố `datn_`:

| Nhóm | Metric Prometheus | Ý nghĩa |
|---|---|---|
| Gemini | `datn_gemini_calls_total{result}` | số lượt gọi theo kết quả `ok`, `http_429`, `http_error`, `network_error` |
| Gemini | `datn_gemini_call_seconds`, `datn_gemini_limiter_wait_seconds` | thời gian một lượt gọi; thời gian chờ ở `GeminiRateLimiter` |
| Gemini | `datn_gemini_daily_used`, `datn_gemini_daily_quota`, `datn_gemini_tokens_total` | mức dùng hạn mức ngày so với trần (480); tổng token |
| Hàng đợi | `datn_jobs{status}` | số job theo trạng thái (cache 15 giây) |
| Hàng đợi | `datn_job_seconds{type,outcome}`, `datn_job_stale_reset_total` | thời gian xử lý job; số job stale reset |
| QC | `datn_qc_rejected_total{step}` | số câu bị loại hoặc bị cờ ở `format`, `dedup`, `solver`, `solver_error`, `grounding` |
| QC | `datn_qc_pipeline_total{status}`, `datn_qc_grounding_total{verdict}` | kết quả pipeline QC; kết luận bám tài liệu |
| Xuất Word | `datn_exam_export_seconds{format,outcome}`, `datn_exam_export_busy_total` | thời gian xuất `omml`/`mathtype`; số lần bị từ chối vì bận (503) |

Giới hạn: bộ đếm của Prometheus bắt đầu lại khi `app` khởi động lại; `datn_gemini_daily_used` nạp lại từ DB nên có thể thấp
hơn thực tế sau khi khởi động lại giữa ngày (xem Javadoc `GeminiRateLimiter`). `datn_qc_rejected_total` đếm theo **lần chạy QC**,
một câu sinh lại nhiều lần được đếm nhiều lần, khác với file 02 đếm theo câu LLM gốc.

## Bộ lọc chung (đã cài trong từng file)

1. **Chỉ đề chế độ giáo viên**: `exam.mode = 'teacher'` (đề tạo theo ma trận, `/api/exams/**`). Không có đề thủ công.
2. **Chỉ đầu ra của LLM**: câu thuộc run production của ma trận (`experiment_run.prompt_version LIKE 'matrix-%'`) và
   `generated_question.edited_from_question_id IS NULL` (quy tắc bắt buộc của W1-2: bản giáo viên sửa không phải đầu ra
   LLM, xem `docs/contract/enums.md`) và `generated_question.origin = 'llm'` (V26: câu nhập từ Word không phải đầu ra LLM).
   Kết cục của một câu LLM được gom theo cả chuỗi bản sửa của nó.
3. **Loại tài khoản của nhóm phát triển và tài khoản dùng thử** (11/11, N-10): cả 7 file ở đây và
   `docs/experiments/trial/queries.sql` dùng CHUNG biến psql `dev_emails` (danh sách email cách nhau bằng dấu phẩy, không
   phân biệt hoa thường, khoảng trắng bị bỏ). Đầu mỗi file khai báo mặc định rỗng (`\set dev_emails ''`, chỉ khi chưa được
   truyền vào) rồi CTE `dev_users` đọc `:'dev_emails'`. **Truyền email khi chạy, không sửa file và không commit email
   thật** để số liệu không lẫn thao tác thử của nhóm:

   ```bash
   # một file
   psql "$DATABASE_URL" -v dev_emails='nhom1@truong.edu.vn,nhom2@truong.edu.vn' -f docs/thong-ke/01-ket-cuc-cau-llm.sql
   # cả 7 file và truy vấn dùng thử với cùng một danh sách (đặt một lần trong biến môi trường của phiên shell)
   DEV_EMAILS='nhom1@truong.edu.vn,nhom2@truong.edu.vn'
   for f in docs/thong-ke/*.sql docs/experiments/trial/queries.sql; do
     psql "$DATABASE_URL" -v dev_emails="$DEV_EMAILS" -f "$f"
   done
   ```

   Không truyền `-v dev_emails` thì không loại ai, và (D-09, 15/11) psql in dòng `CANH BAO: chua truyen -v dev_emails, …`
   trước kết quả: thấy dòng này thì số liệu đang gồm cả tài khoản nhóm, chạy lại với `-v dev_emails`. Cần psql 10 trở lên
   (`\if`). Chạy trong công cụ khác psql (không hiểu `\set`, `\echo`, `:'dev_emails'`): bỏ các dòng bắt đầu bằng `\` và
   thay `:'dev_emails'` bằng chuỗi email trong dấu nháy đơn. Test (`StatisticsSqlTest`, `TrialQueriesSqlTest`,
   `GroundingPipelineTest`) đọc file qua `PsqlFile`, làm đúng hai việc đó với giá trị mặc định.
4. Các file 02 và 04 chỉ tính câu đã nằm (hoặc từng nằm) trong một chỗ của đề giáo viên, nên câu sinh ra nhưng chưa vào đề
   (ví dụ lần sinh bị lỗi định dạng rồi sinh lại) vẫn được tính ở file 02 qua `generation_job.exam_slot_id`.
   - **Đề đã xóa (V31, `exam.deleted_at`) VẪN được tính, có chủ ý:** xóa đề là xóa mềm, các lần sinh, thao tác Giữ/Sửa/Đổi
     và chi phí gọi LLM của đề đó đã xảy ra thật. Không file nào ở đây lọc `deleted_at`. Muốn bỏ đề đã xóa thì thêm
     `AND e.deleted_at IS NULL` ở chỗ nối bảng `exam`.
5. Dữ liệu trước V13 (chưa có chuỗi bản sửa) và trước V14 (chưa có `solver_error`, `solver_reasoning`) vẫn truy vấn được;
   phần "Solver lỗi" của dữ liệu trước V14 nằm chung trong "Solver không chạy hoặc lỗi".

## Mốc phiên bản prompt và Solver (D-07, 15/11)

DB **không** lưu phiên bản prompt thật của từng câu, cũng không lưu phiên bản Solver của từng dòng `answer_check`:

- `experiment_run.prompt_version` là nhãn ghi **lúc tạo run** (lúc tạo đề). Đổi câu trên đề tạo trước một lần deploy, và
  job còn `pending` lúc deploy, sinh câu bằng prompt **mới** nhưng vẫn thuộc run nhãn **cũ** (worker dựng lại prompt bằng code
  đang chạy lúc nhận job). Lọc theo `prompt_version` vì vậy gán nhầm một phần câu.
- `quality_check_result` (dòng `answer_check`) không có cột phiên bản Solver: kết quả v4, v5, v6 nằm chung bảng.

Cách tách (bản rẻ, không migration): so **thời điểm** với mốc deploy ở bảng dưới.

- Phiên bản **prompt sinh câu** của một câu: theo `generated_question.created_at` (lúc lưu câu, sau lời gọi sinh vài giây).
- Phiên bản **Solver** của một kết luận: theo `quality_check_result.checked_at` của dòng `answer_check`. Dòng chép từ bản
  trước khi giáo viên chỉ sửa lời giải (`token_count` NULL) mang kết luận của bản trước; loại bằng `token_count IS NOT NULL`
  khi cần đúng phiên bản.
- Cột thời gian là `TIMESTAMP` không múi giờ, ghi theo giờ VPS (Asia/Bangkok, `deploy/compose.prod.yaml`).

**Giờ trong bảng là giờ merge vào `main`, tức mốc SỚM NHẤT**; giờ deploy thật trên VPS muộn hơn và chưa được ghi ở đâu
trong repo. Xác nhận trên DB production rồi sửa bảng: các bản có migration đi kèm thì đọc
`SELECT version, installed_on FROM flyway_schema_history ORDER BY installed_rank;` (cột "Migration"); bản không có migration
(#75) thì lấy theo lần khởi động lại `app` gần nhất sau giờ merge (log `update.sh` / `docker compose logs app`).

| Merge vào `main` (giờ +07) | PR | Migration | Prompt sinh câu (`matrix-<dạng>-vN-draft`) | Solver mặc định |
|---|---|---|---|---|
| 26/10 23:15:59 | #12 | | (xem Javadoc `MatrixPromptDraftV0.promptVersion`) | `solver-v2-delimited` |
| 31/10 20:54:13 | #29 | | | `solver-v4-literal-reading` (v3 chỉ đo thử, không làm mặc định trên `main`) |
| 03/11 17:52:58 | #40 | | mcq v5, sa v7, tf v7 | |
| 04/11 14:15:01 | #45 | | mcq v6, sa v8, tf v8 | |
| 06/11 14:50:15 | #50 | | mcq v7, sa v9, tf v9 | |
| 11/11 19:00:23 | #55 | | mcq v8, sa v10, tf v10 | |
| 13/11 21:09:02 | #67 | V27 | mcq v9, sa v11, tf v11 | |
| 13/11 23:24:18 | #72 | V28 | | `solver-v5-request-check` (yêu cầu giáo viên kiểm cùng lượt giải) |
| 14/11 19:16:43 | #75 | | mcq v9 (văn bản mcq không đổi), sa v12, tf v12 | |
| 14/11 19:24:15 | #76 | V29 | | `solver-v6-two-pass` (lượt 1 = prompt v4, yêu cầu kiểm ở lượt 2) |

Bảng giả định VPS không đặt `app.llm.solver-prompt-version` (mặc định theo code); nếu có đặt thì mốc Solver theo lần đổi cấu
hình đó. Lọc theo mốc:

```sql
-- phiên bản Solver của từng kết luận (thay giờ merge bằng giờ deploy thật)
CASE
    WHEN a.checked_at >= TIMESTAMP '2026-11-14 19:24:15' THEN 'solver-v6-two-pass'
    WHEN a.checked_at >= TIMESTAMP '2026-11-13 23:24:18' THEN 'solver-v5-request-check'
    WHEN a.checked_at >= TIMESTAMP '2026-10-31 20:54:13' THEN 'solver-v4-literal-reading'
    ELSE 'solver-v2-delimited'
END
```

File `10-yeu-cau-giao-vien.sql` đã có sẵn cửa sổ `checked_at` (biến psql `solver_tu`, `solver_den`; mặc định từ mốc v6) để
không trộn kết luận một lượt (v5) với hai lượt (v6). Các file 02 và 04 (Solver lệch) chưa lọc: thêm điều kiện `checked_at`
như trên khi lấy số theo từng phiên bản. Bản đầy đủ (ghi `prompt_version` lên job và phiên bản Solver lên dòng
`answer_check`) cần migration, chưa làm.

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
- File 05: kết quả "bám tài liệu" là cảnh báo của một LLM judge cùng họ với bộ sinh câu, chưa đối chiếu với nhãn của người; nhóm
  "không tài liệu" chưa có đường chấm riêng nên chỉ hiện dòng `khong_cham`. Không dùng file này làm độ đúng của câu.
- File 06: số đo là token và thời gian gọi LLM (không phải thời gian chờ của người dùng, không có đơn giá). Chỉ tính lượt gọi
  thành công cho ra kết quả; dữ liệu trước V21 không có số của Solver và grounding (đếm ở `so_cau_thieu_so_do`). Các lần sinh bị
  loại rồi sinh lại và lượt Đổi câu là những câu LLM gốc khác, không gộp vào câu cuối của chỗ.
