# Đo F-07: số câu SQL và thời gian của xem đề và danh sách đề

Mục đích: đo (CHƯA sửa) chi phí truy vấn của `GET /api/exams/{id}` (đề 18 câu) và `GET /api/exams` (từ 10 đề trở lên) trên
database `datn_mcq_e2e`, để quyết định có cần gộp truy vấn (N+1) hay không. Không đổi cấu hình mặc định: thống kê Hibernate
chỉ bật bằng tham số dòng lệnh khi chạy.

## Điều kiện

- Chỉ ĐỌC: hai endpoint trên là GET, nhưng `GET /api/exams/{id}` đối soát chỗ đang sinh (có thể ghi). Đo trên đề đã xong
  (mọi chỗ `ready`/`kept`/`edited`) để không phát sinh ghi; tắt worker (`WORKER_ENABLED` không đặt) để không gọi Gemini.
- Chạy app một lần, dùng đúng DB `datn_mcq_e2e` (đặt `DB_NAME` trước khi chạy). Không đụng DB dev khác.
- Cần một tài khoản giáo viên sở hữu các đề; đề 18 câu: chọn đề có `SELECT count(*) FROM exam_slot WHERE exam_id = ?` bằng 18.

## Bước 1. Khởi động app với thống kê Hibernate (chỉ lần chạy này)

PowerShell, trong thư mục `back_end` (nạp biến môi trường từ `.env` như `run-dev.sh`; đừng in giá trị bí mật):

```powershell
$env:DB_NAME = 'datn_mcq_e2e'
$env:SERVER_PORT = '8085'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Dspring.jpa.properties.hibernate.generate_statistics=true -Dlogging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=INFO -Dlogging.level.org.hibernate.SQL=DEBUG" *> f07.log
```

- `hibernate.generate_statistics=true` bật số liệu phiên; `StatisticalLoggingSessionEventListener` in dòng
  `Session Metrics { ... N JDBC statements ... }` khi mỗi phiên đóng (mỗi `@Transactional` ngoài cùng là một phiên vì
  `spring.jpa.open-in-view=false`). Tổng số câu SQL của một request = tổng `N JDBC statements` của các phiên trong request.
- `org.hibernate.SQL=DEBUG` in từng câu SQL, dùng để đếm chéo và xem câu nào lặp (N+1).

## Bước 2. Lấy token và gọi hai endpoint

```powershell
$base = 'http://localhost:8085'
$login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' `
  -Body (@{ email = '<email giáo viên>'; password = '<mật khẩu>' } | ConvertTo-Json)
$h = @{ Authorization = "Bearer $($login.accessToken)" }

# Danh sách đề (cần từ 10 đề trở lên): đo 3 lần, bỏ lần đầu (nóng JVM)
1..3 | ForEach-Object { (Measure-Command { Invoke-RestMethod -Uri "$base/api/exams" -Headers $h | Out-Null }).TotalMilliseconds }

# Xem một đề 18 câu
$examId = <id đề>
1..3 | ForEach-Object { (Measure-Command { Invoke-RestMethod -Uri "$base/api/exams/$examId" -Headers $h | Out-Null }).TotalMilliseconds }
```

Tên trường token trong response đăng nhập xem `docs/api/README.md` mục 8 (sửa `accessToken` nếu khác).

## Bước 3. Đếm câu SQL của từng lần gọi

Mốc đánh dấu trước và sau mỗi lần gọi để cắt log (thêm một dòng đệm bằng cách gọi một endpoint rẻ như `GET /api/auth/me`
nếu có, hoặc ghi số dòng của `f07.log` trước và sau):

```powershell
$before = (Get-Content f07.log).Count
Invoke-RestMethod -Uri "$base/api/exams/$examId" -Headers $h | Out-Null
$lines = (Get-Content f07.log | Select-Object -Skip $before)
# Cách 1: tổng từ Session Metrics
($lines | Select-String 'Session Metrics' -Context 0,12 | Out-String)
# Cách 2: đếm dòng SQL (select|insert|update|delete)
($lines | Select-String '^\s*(\S+\s+)*Hibernate:\s+(select|insert|update|delete)' ).Count
```

(Định dạng dòng SQL tùy cấu hình log; nếu cách 2 không khớp, mở `f07.log` và đếm các dòng `org.hibernate.SQL`.)

## Cách ghi kết quả

| Endpoint | Số đề / số câu | Số câu SQL | Thời gian (ms, lần 2 và 3) | Ghi chú (câu lặp theo N) |
|---|---|---|---|---|
| `GET /api/exams/{id}` | 1 đề / 18 câu | | | |
| `GET /api/exams` | ≥ 10 đề | | | |

Nhận định khi đọc: nếu số câu SQL tăng tuyến tính theo số câu (hoặc số đề) với cùng một mẫu câu lặp, đó là N+1 (xem
`QcSummaryService.summarize`, đã bỏ truy vấn ý cho câu mcq/sa ở F-07 phần rẻ). Chỉ sửa khi số đo cho thấy cần.

## Kết quả TRƯỚC khi sửa (đo 3/11, `datn_mcq_e2e`, 4 lượt, tài khoản GV1)

| Endpoint | Số đề / số câu | Số câu SQL | Thời gian 4 lượt (ms) | Câu lặp theo N |
|---|---|---|---|---|
| `GET /api/exams/13` | 1 đề / 18 câu | 117 | 546 / 359 / 295 / 307 | `quality_check_result` 3 lần/câu (format, dedup, answer_check), `generation_job`, `question_grounding`, `generated_question` |
| `GET /api/exams` | 10 đề / 152 câu | 774 | 1056 / 1186 / 992 / 743 | `quality_check_result` 456, `question_grounding` 152, `generation_job` 152, `answer_check_statement` 10 |

Số "cần xem lại" lệch: đề 8 ở danh sách 3, ở trang chi tiết 7 (danh sách không gắn cờ `similar_in_exam`, chỉ đếm slot `ready`).

## Sau khi sửa (đo 4/11, cùng DB `datn_mcq_e2e`, cùng đề 13, cùng tài khoản GV1)

Cách đo: app chạy `spring-boot:run` với `DB_NAME=datn_mcq_e2e`, cổng 8085, worker tắt, `generate_statistics=true` và
`org.hibernate.SQL=DEBUG` như Bước 1; gọi bằng `curl` (thời gian `time_total`), 4 lượt mỗi endpoint sau khi đã gọi thử vài lần
(không phải khởi động lạnh). Số câu SQL đếm dòng `org.hibernate.SQL` của từng request, bỏ hai câu `SELECT * FROM generation_job` của bộ
nhận job nền (không thuộc request).

| Endpoint | Số đề / số câu | Số câu SQL | Thời gian 4 lượt (ms) |
|---|---|---|---|
| `GET /api/exams/13` | 1 đề / 18 câu | 13 | 81 / 81 / 70 / 102 |
| `GET /api/exams` (size mặc định 10) | 10 đề / 180 slot | 14 | 422 / 354 / 388 / 379 |

Ghi chú:

- 13 câu SQL của xem đề: đề, `exists generating`, slot, ô ma trận, ngân hàng (2), câu sinh, ý tf, job, QC (3), ma trận (loại đề).
  Thêm 1 truy vấn đệ quy bản gốc khi đề có câu đã sửa hoặc câu đã Giữ (đề 13 không có nên không chạy; trang đầu của danh sách có nên chạy).
  Danh sách: trang đề, đếm tổng, loại đề, slot, ô ma trận, ngân hàng (2), câu sinh, ý tf, bản gốc, job, QC (3) = 14.
  Số này không đổi theo số câu hay số đề (test
  `ExamViewBatchTest`: đề 18 và 36 câu cùng số truy vấn, ≤ 25; danh sách 3 và 10 đề cùng số truy vấn, ≤ 20).
- DB lúc đo sau không còn đúng như lúc đo trước: có thêm đề 15 (15 đề thay vì 14), nên trang đầu của danh sách gồm 10 đề / 180 slot
  thay vì 152 câu. Số câu SQL "sau" không phụ thuộc quy mô nên vẫn so được; thời gian danh sách "sau" đo trên dữ liệu lớn hơn.
- Thời gian danh sách (~0,4 s) còn cao so với số truy vấn vì phần so khuôn `similar_in_exam` (`ExamQuestionSimilarity`) tính trong bộ nhớ,
  O(n²) theo số câu của từng đề và chuẩn hóa lại văn bản cho mỗi cặp. Chưa tối ưu (ngoài phạm vi F-07); nếu cần, lưu sẵn chuẩn hóa của mỗi câu.
- Số "cần xem lại": cả 15 đề ở danh sách bằng `needsReviewCount` ở trang chi tiết (không đề nào lệch). Đề 8: 5 ở cả hai nơi
  (số 3 và 7 ghi ở trên là số của lần đo 3/11, trước khi hai nơi dùng chung hàm tính).
- Hai truy vấn mới tải dữ liệu theo lô không cần index mới: `DISTINCT ON (question_id, job_type)` dùng `idx_gj_question_type` (V13);
  truy vấn đệ quy bản gốc đi theo khóa chính. Không có migration.
