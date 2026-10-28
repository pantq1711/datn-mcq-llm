# Xây dựng hệ thống sinh và kiểm soát chất lượng câu hỏi trắc nghiệm tự động bằng mô hình ngôn ngữ lớn cho nền tảng học tập trực tuyến

- **Nhóm thực hiện:**
  - Phan Hồng An — backend, CSDL, hàng đợi, QC
  - Vũ Hồng Quân — frontend
  - [TÊN THÀNH VIÊN 3] — công cụ nhập ngân hàng câu hỏi, xuất Word MathType
- **Giảng viên hướng dẫn:** ThS. Nguyễn Mạnh Sơn

## 1. Giới thiệu

Hệ thống hỗ trợ giáo viên THCS (môn Toán) tạo đề kiểm tra theo ma trận/đặc tả hoặc tạo bộ câu hỏi theo bài học có tài liệu đính kèm. Khi tạo đề, hệ thống ưu tiên lấy câu hỏi phù hợp có sẵn trong ngân hàng; phần còn thiếu được sinh tự động bằng mô hình ngôn ngữ lớn (LLM) thông qua hàng đợi bất đồng bộ. Mọi câu hỏi sinh ra đều đi qua chuỗi kiểm soát chất lượng tự động trước khi đưa vào đề để giáo viên xem xét, thực hiện **Giữ / Đổi / Sửa** từng câu và xuất đề ra file Word (`.docx`).

## 2. Chức năng chính

- **Xác thực và quản trị tài khoản (`/api/auth/*`, `/api/admin/users`):** Đăng nhập JWT (`accessToken` HS256 ngắn hạn kết hợp `refresh_token` xoay vòng trong cookie `HttpOnly`), giới hạn đăng nhập sai theo email và IP, đổi mật khẩu; quản trị viên tạo tài khoản giáo viên, khóa/mở khóa và đặt lại mật khẩu.
- **Quản lý ma trận đề kiểm tra (`/api/exam-matrices`):** Tạo ma trận bằng cấu trúc JSON hoặc khởi tạo từ mẫu có sẵn (`giuaki1-toan8`), xem danh sách/chi tiết, nhân bản, sửa hoặc xóa (tự động chặn sửa cấu trúc ô và chặn xóa với mã `409 MATRIX_IN_USE` khi ma trận đã có đề).
- **Tài liệu bài học đính kèm (`/api/materials`):** Giáo viên tải lên tệp `.docx`, `.pdf`, `.txt` (tối đa 10 MB, nhận dạng định dạng thực qua magic bytes); hệ thống trích xuất văn bản (Pandoc cho `.docx`, PDFBox cho `.pdf`), chỉ lưu văn bản đã làm sạch và mã băm `sha256`, không lưu tệp gốc.
- **Tạo đề theo ma trận hoặc theo bài học (`/api/exams/build`, `/api/exams/build-by-lesson`):** Cho phép gắn tài liệu bài học (tự động chia đoạn và chọn đoạn phù hợp bằng BM25 qua `PassageSelector`) cùng yêu cầu bổ sung cho AI; ưu tiên lấy câu khớp từ ngân hàng theo xếp hạng mềm (ưu tiên câu ít đề dùng và dùng lâu nhất), các chỗ còn thiếu được xếp job sinh qua hàng đợi.
- **Xem đề, duyệt câu và truy vết QC (`/api/exams/{id}`, `/progress`, `/qc-trace`):** Hiển thị đề theo Phần I–III kèm đáp án, lời giải, trạng thái từng chỗ và cờ cảnh báo "cần xem kỹ" (`needsReview`, `reviewReasons`); xem chi tiết lịch sử các lần sinh, phiên bản câu và kết quả từng bước QC của mỗi chỗ.
- **Thao tác Giữ / Đổi / Sửa trên từng vị trí câu trong đề:**
  - **Giữ (`/keep`):** Duyệt câu do hệ thống sinh (hoặc bản giáo viên đã sửa) và lưu vào ngân hàng câu hỏi trong cùng một giao dịch.
  - **Đổi (`/replace`):** Sinh câu mới qua hàng đợi (có thể kèm yêu cầu bổ sung `instruction` tối đa 500 ký tự) hoặc đổi sang câu khác từ ngân hàng (`source = "bank"`).
  - **Sửa (`/question`):** Tạo phiên bản câu hỏi mới (`edited_from_question_id`), giữ nguyên bản gốc của LLM; kiểm tra định dạng và trùng lặp ngay trong request, đồng thời xếp job kiểm tra lại đáp án (và độ bám tài liệu nếu có) qua hàng đợi.
- **Ngân hàng câu hỏi (`/api/bank-items`):** Tra cứu, lọc đa tiêu chí (môn, khối, dạng câu, chủ đề, nội dung ô, độ khó, mức độ sử dụng), tìm kiếm từ khóa, phân trang và xem nguồn gốc phiên bản; câu hỏi sinh từ đề có tài liệu đính kèm chỉ hiển thị riêng cho giáo viên sở hữu (`visible_to_user_id`).
- **Xuất đề ra Word (`/api/exams/{id}/export/docx`):** Xuất tệp `.docx` bằng Pandoc theo mẫu `reference.docx` (font Times New Roman cỡ 13, tắt Compatibility Mode) gồm phần đề (Phần I, II, III), bảng **ĐÁP ÁN** và **LỜI GIẢI**, với công thức toán ở định dạng Equation gốc của Word (OMML).
- **Công cụ thí nghiệm nội bộ (`/api/generation/*`, `/api/catalog/templates`):** Mặc định tắt (`EXPERIMENT_ENABLED=false`); chỉ mở khi bật cấu hình để quản trị viên gọi sinh câu đồng bộ hoặc thao tác hàng đợi trực tiếp.

## 3. Kiến trúc tóm tắt

- **Công nghệ sử dụng:** Backend Java 21, Spring Boot 4.1.1, Spring Security (OAuth2 Resource Server JWT), JPA/Hibernate, Flyway; CSDL PostgreSQL 16 (`pgvector/pgvector:pg16`); Frontend React 19, Vite 8, TypeScript, Ant Design, KaTeX; AI Google Gemini API (`gemini-3.5-flash-lite`); xử lý tài liệu bằng Pandoc và Apache PDFBox 3.0.8.
- **Ba dạng câu hỏi:** Trắc nghiệm 4 phương án lựa chọn A–D (`mcq`, Phần I), trắc nghiệm Đúng/Sai gồm đề dẫn và 4 ý a–d (`tf`, Phần II — mức độ nhận thức gắn theo từng ý), và trắc nghiệm trả lời ngắn (`sa`, Phần III).
- **Chuỗi kiểm soát chất lượng (QC):** Kiểm tra định dạng JSON và cú pháp LaTeX (`FormatChecker`, kèm `LlmOutputRepair` tự sửa lỗi thoát chuỗi LaTeX và đóng dấu `$` cuối trường cho luồng production) → kiểm tra trùng lặp bằng dấu vân tay văn bản và độ tương đồng cosine 3-gram ký tự kèm chốt chặn số liệu/từ phủ định (`DedupChecker`) → gọi mô hình thứ hai giải độc lập để đối chiếu đáp án (`AnswerSolverChecker`) → kiểm tra độ bám tài liệu kèm xác minh trích dẫn nguyên văn khi đề có tài liệu đính kèm (`GroundingChecker`).
- **Hàng đợi công việc (`generation_job`):** Nhận job nguyên tử bằng `SELECT ... FOR UPDATE SKIP LOCKED`, dùng hàng rào `claim_seq` (V22) chặn worker bị thu hồi ghi đè kết quả muộn, và tự động trả các job kẹt ở trạng thái `running` quá ngưỡng (`WORKER_STALE_AFTER_SECONDS`, mặc định 300 giây) về `pending` (`StaleJobRecovery`).
- **Điều tiết gọi Gemini và worker nền:** Luồng worker chạy liên tục (`GenerationJobWorkerLifecycle`) đi qua bộ điều tiết dùng chung `GeminiRateLimiter` (giãn cách tối thiểu 4200 ms giữa các lượt gọi, tôn trọng `retryDelay` khi gặp HTTP 429 cho toàn bộ luồng, dừng nhận job mới khi chạm trần 480 lượt/ngày và trả job đang dở về `pending`).
- **Tài liệu kiến trúc chi tiết:** Xem cấu trúc package, hướng phụ thuộc giữa các module và quy tắc giao dịch tại [`docs/architecture/backend-structure.md`](docs/architecture/backend-structure.md).

## 4. Ràng buộc nghiệp vụ đáng chú ý

- **Đáp án câu trả lời ngắn (`sa`):** Theo quy cách phiếu trả lời trắc nghiệm năm 2025 của Bộ GD&ĐT (Phần III, điền tối đa 4 cột), đáp án câu `sa` ở chế độ chặt (khi sinh câu mới và khi giáo viên sửa câu) chỉ chấp nhận **số nguyên hoặc số thập phân tối đa 4 ký tự** tính cả dấu `-` ở đầu và dấu phẩy `,` phân cách thập phân (ví dụ: `5`, `-12`, `2,75`, `0,5`); hệ thống từ chối phân số, căn thức, hỗn số hoặc đơn vị đo, và yêu cầu đề bài phải ghi rõ quy tắc làm tròn nếu kết quả lẻ.

## 5. Chạy trên máy phát triển (dev)

1. **Khởi động CSDL PostgreSQL bằng Docker:**
   ```bash
   cp .env.example .env
   ```
   Mở `.env` và điền các biến bắt buộc: `DB_PASSWORD`, `JWT_SECRET` (chuỗi base64 tối thiểu 32 byte, tạo bằng `openssl rand -base64 48`), `LLM_API_KEY` (khóa Gemini API), và `ADMIN_EMAIL` / `ADMIN_PASSWORD` (từ 12 ký tự trở lên để tạo quản trị viên đầu tiên). Sau đó chạy:
   ```bash
   docker compose up -d
   ```
   Container `datn-mcq-db` lắng nghe tại cổng `5432` (theo `DB_PORT` trong `.env`) và tự tạo cả hai database `datn_mcq` (dev) cùng `datn_mcq_test` (test).

2. **Bật worker xử lý hàng đợi:**
   Trong `.env.example` và `application.yaml`, biến `WORKER_ENABLED` mặc định là `false`. Khi chạy ở môi trường dev và muốn hệ thống thực sự xử lý các job sinh câu hỏi/kiểm tra đáp án trong hàng đợi, cần đặt:
   ```ini
   WORKER_ENABLED=true
   ```
   *(Lưu ý: chức năng xuất Word và trích xuất tệp `.docx` yêu cầu máy đã cài `pandoc` trên `PATH` hoặc cấu hình đường dẫn qua biến `PANDOC_PATH`).*

3. **Chạy backend (cổng mặc định `8080`):**
   ```bash
   cd back_end
   ./mvnw spring-boot:run
   ```
   *(Trên Windows PowerShell dùng `.\mvnw.cmd spring-boot:run`, hoặc trên Git Bash dùng `./back_end/run-dev.sh`). Flyway sẽ tự động chạy các migration `V1`–`V23` khi khởi động.*

4. **Chạy frontend (cổng mặc định `5173`):**
   ```bash
   cd front_end
   npm install
   npm run dev
   ```

## 6. Chạy kiểm thử (test)

- **Lệnh chạy test backend duy nhất trên Windows PowerShell:**
  ```powershell
  powershell -ExecutionPolicy Bypass -File back_end/run-tests.ps1
  ```
- **Chạy toàn bộ test:** Đảm bảo không có tệp `back_end/.test-selection`, chạy lệnh trên (dòng đầu kết quả in `=== CHAY TOAN BO TEST ===`). Mặc định Maven loại trừ nhóm test hiệu năng `@Tag("perf")`.
- **Chạy riêng một hoặc vài test:** Ghi tên các lớp hoặc phương thức test (phân tách bằng dấu phẩy, không có khoảng trắng, ví dụ: `PassageSelectorTest,GroundingCheckerTest`) vào tệp `back_end/.test-selection`, rồi chạy lại `back_end/run-tests.ps1`. Script sẽ tự xóa tệp `.test-selection` ngay sau khi đọc.
- **Cô lập CSDL và LLM khi test:** Cấu hình `back_end/src/test/resources/application.yaml` khóa cứng kết nối vào database riêng `datn_mcq_test` (không dùng `${DB_NAME}` của dev), tắt worker nền (`app.worker.enabled=false`) và dùng client giả để không phát sinh cuộc gọi Gemini tính phí.

## 7. Triển khai máy chủ

Hướng dẫn chi tiết về kiến trúc container (`web` Caddy HTTPS + `app` Spring Boot + `db` PostgreSQL), cấu hình `deploy/.env`, giới hạn tài nguyên Pandoc/PDFBox, sao lưu – khôi phục và quy trình CI/CD nằm tại [`docs/deploy/README.md`](docs/deploy/README.md).

## 8. Bản đồ tài liệu (`docs/`)

- [`docs/ai-development-rules.md`](docs/ai-development-rules.md): Quy tắc phát triển có AI hỗ trợ (v2), thứ tự ưu tiên nguồn chuẩn, các bất biến nghiệp vụ và lịch sử quyết định.
- [`docs/api/README.md`](docs/api/README.md): Tài liệu tổng hợp các endpoint HTTP API, phân quyền, mẫu payload, mã lỗi và quy tắc xử lý công thức/tài liệu cho frontend.
- [`docs/api/openapi.json`](docs/api/openapi.json): Đặc tả OpenAPI 3.0 của backend được sinh tự động qua test `OpenApiContractTest`.
- [`docs/architecture/backend-structure.md`](docs/architecture/backend-structure.md): Cấu trúc package theo tính năng, hướng phụ thuộc giữa các module và các quy tắc thiết kế backend.
- [`docs/contract/enums.md`](docs/contract/enums.md): Hợp đồng giá trị enum giữa DB – backend – frontend, quy tắc nghiệp vụ đi kèm và hợp đồng JSON của LLM/Solver.
- [`docs/deploy/README.md`](docs/deploy/README.md): Hướng dẫn dựng máy chủ Ubuntu, cấu hình Docker Compose production, Caddy, sao lưu định kỳ và xử lý sự cố.
- [`docs/dev-log/loi-va-cach-xu-ly.md`](docs/dev-log/loi-va-cach-xu-ly.md): Nhật ký các lỗi thực tế gặp phải trong quá trình phát triển, phân tích nguyên nhân gốc và biện pháp chống tái phát.
- [`docs/review/danh-gia-do-sau-ky-thuat.md`](docs/review/danh-gia-do-sau-ky-thuat.md): Báo cáo kiểm kê mã nguồn và phân loại độ sâu kỹ thuật, thuật toán của hệ thống backend.
- [`docs/sample/matrix_giuaki1_toan8.json`](docs/sample/matrix_giuaki1_toan8.json): Tệp JSON mẫu ma trận đề kiểm tra Giữa kì I môn Toán lớp 8.
- [`docs/thong-ke/README.md`](docs/thong-ke/README.md): Hướng dẫn chạy bộ truy vấn thống kê PostgreSQL chỉ đọc, bộ lọc chung và các giới hạn khi giải thích số liệu.
- [`docs/thong-ke/01-ket-cuc-cau-llm.sql`](docs/thong-ke/01-ket-cuc-cau-llm.sql): Truy vấn thống kê tỉ lệ câu hỏi do LLM sinh được Giữ nguyên, Sửa rồi Giữ, Sửa rồi Đổi, Đổi hoặc chưa thao tác.
- [`docs/thong-ke/02-buoc-qc-bat-loi.sql`](docs/thong-ke/02-buoc-qc-bat-loi.sql): Truy vấn thống kê số lượng và tỉ lệ câu bị phát hiện lỗi ở từng bước QC (format, trùng lặp, Solver lệch/lỗi) theo dạng câu.
- [`docs/thong-ke/03-truong-giao-vien-hay-sua.sql`](docs/thong-ke/03-truong-giao-vien-hay-sua.sql): Truy vấn thống kê các trường nội dung (thân câu, phương án, đáp án, lời giải...) mà giáo viên thường chỉnh sửa.
- [`docs/thong-ke/04-solver-lech-giao-vien-xu-ly.sql`](docs/thong-ke/04-solver-lech-giao-vien-xu-ly.sql): Truy vấn thống kê cách giáo viên xử lý (Giữ nguyên, Sửa hay Đổi) đối với các câu bị Solver báo lệch đáp án.
- [`docs/thong-ke/05-grounding.sql`](docs/thong-ke/05-grounding.sql): Truy vấn thống kê kết quả kiểm tra độ bám tài liệu (`supported`, `not_supported`, `unknown`) và tỉ lệ xác minh trích dẫn.
- [`docs/thong-ke/06-chi-phi.sql`](docs/thong-ke/06-chi-phi.sql): Truy vấn thống kê chi phí trung bình (số token và thời gian gọi LLM của bước sinh câu, Solver và grounding) theo dạng câu.

## 9. Quy ước làm việc

- **Nhánh và Pull Request:** Mỗi công việc thực hiện trên một nhánh riêng và mở PR vào `main`; phải có thành viên khác trong nhóm duyệt rồi mới merge; **không tự merge PR của mình**; merge bằng **merge commit** (không dùng squash merge).
- **Kiểm tra trước khi merge PR backend:** Bắt buộc chạy xanh toàn bộ bộ test **và** khởi động ứng dụng backend thật ít nhất một lần để kiểm tra ngữ cảnh Spring/Flyway.
- **Schema CSDL và Enum:** Mọi thay đổi cấu trúc bảng hoặc ràng buộc phải viết trong migration Flyway mới (`V{n+1}__*.sql`), tuyệt đối không sửa các migration đã merge. Nếu thay đổi giá trị enum thì phải cập nhật [`docs/contract/enums.md`](docs/contract/enums.md) trong cùng commit.
- **Quy ước Commit và Git Hook:** Commit message viết bằng tiếng Anh, không chứa dòng `Co-Authored-By`, `Claude-Session` hoặc chữ ký AI; không dùng `--no-verify`. Mỗi máy phát triển cần cài đặt hook `.git/hooks/commit-msg` (và cấp quyền thực thi `chmod +x .git/hooks/commit-msg`) với nội dung:
  ```sh
  #!/bin/sh
  sed -i '/^[Cc]o-[Aa]uthored-[Bb]y:.*\([Cc]laude\|[Aa]nthropic\|[Gg]emini\|[Gg]oogle\)/d;/^Claude-Session:/d' "$1"
  ```
- **Bảo mật dữ liệu (Repo đang ở chế độ public):** Tuyệt đối không commit `.env`, `deploy/.env`, thư mục `data/private`, khóa API hay các tài liệu, bộ câu hỏi thương mại/nội bộ.

## 10. Trạng thái hiện tại (28/10/2026)

- **Backend & CSDL:** Đã hoàn thành 23 bản migration Flyway (`V1`–`V23`), hoạt động đầy đủ các luồng xác thực JWT, quản lý ma trận, trích xuất tài liệu bài học (`.docx`/`.pdf`/`.txt`), tạo đề theo ma trận và theo bài học, chuỗi QC 4 bước (`format` → `dedup` 3-gram → `Solver` → `grounding`), hàng đợi `generation_job` có hàng rào `claim_seq` cùng bộ điều tiết `GeminiRateLimiter`, và xuất đề ra Word qua Pandoc.
- **Đo kiểm thực tế:** Theo nhật ký kiểm chứng (`docs/dev-log/loi-va-cach-xu-ly.md`, `docs/deploy/README.md`), thời gian sinh mới hoàn toàn một đề 18 câu trên Gemini thật đạt khoảng 3 phút; image Docker của backend, cấu hình Caddy và kịch bản sao lưu/khôi phục CSDL đã được kiểm chứng trên môi trường container.
- **Frontend & CI/CD:** Đã có giao diện cho các luồng đăng nhập, quản lý tài khoản, ma trận, tạo/xem đề và ngân hàng câu hỏi; tuy nhiên lệnh build production (`npm run build` / `tsc -b`) hiện còn 3 lỗi TypeScript (`Header.tsx`, `Sidebar.tsx`, `ReviewQuestionForm.tsx`) nên image Docker `web` và workflow CI trên GitHub đang chờ sửa để thông suốt.
