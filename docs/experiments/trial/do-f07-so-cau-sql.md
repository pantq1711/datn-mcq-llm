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
