# Nhật ký lỗi và cách xử lý – backend

Tổng hợp các lỗi gặp trong quá trình phát triển backend (đến commit `5fefe87`, ngày 23/10/2026), dùng cho chương
cài đặt – thử nghiệm của báo cáo đồ án và ôn tập bảo vệ.

**Nguồn:** `git log` của các nhánh `backend/init-project` và `feature/question-types`, các test có ghi chú mô tả lỗi,
`docs/ai-development-rules.md` (mục 11 "Phát hiện từ audit" F-1…F-20 và các quyết định 21–22/10), `docs/api/README.md`,
comment trong code, header của migration V1–V10, và ghi chép của nhóm. Mục nào không tìm thấy dấu vết trong repo được
đánh dấu "nguồn: ghi chép ngoài repo"; cách xử lý lấy từ ghi chép của nhóm thì ghi "nguồn: ghi chép của nhóm". Chỗ
không chắc ghi "chưa xác minh". Bốn mục đánh dấu "(bổ sung từ repo)" không có trong danh sách ban đầu, được thêm khi
đọc git log và migration.

## Bảng tổng hợp

| Mã | Tên | Nhóm | Mức độ | Trạng thái |
|---|---|---|---|---|
| L-01 | File migration đặt tên `.sql.sql` | Môi trường | Thấp | Đã sửa |
| L-02 | Visual Paradigm không hiển thị bảng có cột JSONB | Môi trường | Thấp | Đã sửa |
| L-03 | Lệch package `com.datn.back_end` / `com.datn.mcq` | Môi trường | Thấp | Đã sửa |
| L-04 | DbEnumBindingTest: "No ServletContext set" | Môi trường | Thấp | Đã sửa |
| L-05 | `docker compose down -v` xóa luôn DB test | Môi trường | Trung bình | Đã sửa |
| L-06 | Script Python lỗi `UnicodeEncodeError` (cp1252) | Môi trường | Thấp | Đã sửa |
| L-07 | Model Gemini 404 / hạn mức 20 lượt mỗi ngày | Môi trường | Trung bình | Đã sửa |
| L-08 | curl gửi JSON tiếng Việt sai bảng mã; `docker exec -it` lỗi TTY | Môi trường | Thấp | Đã sửa |
| L-09 | PostgreSQL cài sẵn chiếm cổng 5432; thiếu JAVA_HOME; IDE không đọc `../.env` | Môi trường | Trung bình | Đã sửa |
| L-10 | Cổng 8080 bị chiếm; terminal mới mất biến → app nối nhầm DB dev | Môi trường | Trung bình | Đã sửa |
| L-11 | Cột `CHAR` lệch kiểu Hibernate validate (bổ sung từ repo) | Dữ liệu/DB | Trung bình | Đã sửa |
| L-12 | Test ghi dữ liệu vào DB dev (bổ sung từ repo) | Dữ liệu/DB | Cao | Đã sửa |
| L-13 | Lời giải bị bỏ khi chép vào ngân hàng (quy tắc `explanationForBank`) | Dữ liệu/DB | Cao | Đã sửa |
| L-14 | Lời giải mất ở chỗ 20 đề 2 (dữ liệu dev) | Dữ liệu/DB | Trung bình | Đã sửa |
| L-15 | Push nhầm nhánh; amend commit đã push | Quy trình git | Thấp | Đã sửa |
| L-16 | Commit thiếu file (`eca0720` chỉ có openapi.json) (bổ sung từ repo) | Quy trình git | Trung bình | Đã sửa |
| L-17 | LLM trả `\n` dạng chữ trong JSON | LLM & parse | Trung bình | Đã sửa |
| L-18 | JSON có khóa trùng (`option_c`) | LLM & parse | Trung bình | Đã sửa |
| L-19 | Lỗi LaTeX chỉ nằm trong lời giải làm hỏng cả câu | LLM & parse | Trung bình | Đã sửa |
| L-20 | Escape LaTeX sai trong JSON (`\circ` → "Invalid \escape") | LLM & parse | Cao | Đã sửa |
| L-21 | Thiếu `$` đóng ở cuối câu | LLM & parse | Trung bình | Đã sửa |
| L-22 | Ý Đúng/Sai có tiền tố "a)" ở đầu (bổ sung từ repo) | LLM & parse | Thấp | Đã sửa |
| L-23 | Câu SA/TF lặp dạng (cùng $2x - 6 = 0$) | LLM & parse | Trung bình | Đã sửa |
| L-24 | Các câu cùng một ô ma trận cùng dạng | LLM & parse | Trung bình | Đã sửa |
| L-25 | Mọi câu Đúng/Sai có nhãn T,T,F,F | LLM & parse | Trung bình | Đã sửa |
| L-26 | Phương án nhiễu tương đương đáp án đúng | LLM & parse | Trung bình | Đã sửa |
| L-27 | Đáp án lộ trong đề | LLM & parse | Trung bình | Chưa sửa |
| L-28 | Câu được `qc_passed` dù Solver lệch hoặc lỗi (bổ sung từ repo) | QC | Cao | Đã sửa |
| L-29 | Câu Đúng/Sai gần trùng không bị bắt | QC | Trung bình | Đã sửa |
| L-30 | Đề tự mâu thuẫn (Solver bắt được) | QC | Trung bình | Đã sửa |
| L-31 | Cảnh báo format bị mất ở luồng job | QC | Thấp | Đã sửa |
| L-32 | needsReview bật bởi cảnh báo tự sửa escape (báo động giả) | QC | Thấp | Đã sửa |
| L-33 | Đổi câu ở chỗ TF vi phạm `chk_es_ready` | Hàng đợi/đồng thời | Cao | Đã sửa |
| L-34 | Job kẹt RUNNING | Hàng đợi/đồng thời | Cao | Đã sửa |
| L-35 | Nhận job không khóa (hai worker nhận trùng) | Hàng đợi/đồng thời | Cao | Đã sửa |
| L-36 | Lỗi Gemini không được phân loại khi thử lại | Hàng đợi/đồng thời | Cao | Chưa sửa |
| L-37 | Lệch đồng hồ JVM và container DB làm test chập chờn | Hàng đợi/đồng thời | Trung bình | Đã sửa |
| L-38 | File Word mở ở Compatibility Mode | Xuất file | Trung bình | Đã sửa |
| L-39 | Job sinh câu `done` trước khi QC xong, đối soát sinh lại câu tốt (bổ sung 24/10) | Hàng đợi/đồng thời | Cao | Đã sửa |
| L-40 | Chạy lại QC làm mất cảnh báo từ bước giải mã (bổ sung 24/10) | QC | Thấp | Chưa sửa |
| L-41 | Dedup bỏ sót câu cùng đề ở ô khác chưa qua QC và văn bản câu ngân hàng đã sửa (bổ sung 24/10, W1-3) | QC | Trung bình | Đã sửa |
| L-42 | Solver lỗi với câu LLM gốc không để lại dấu vết, lẫn với "chưa chạy" (bổ sung 24/10, W1-3) | QC | Thấp | Đã sửa |
| L-43 | Hai ô dùng chung template có thể nhận cùng một câu ngân hàng (bổ sung 24/10, W1-3) | Dữ liệu/DB | Thấp | Đã sửa |
| L-44 | Khóa ghi hàng ma trận không chờ tạo đề đang dở (bổ sung 25/10, W1-4) | Hàng đợi/đồng thời | Trung bình | Đã sửa |
| L-45 | Trích dẫn đúng chữ của judge "bám tài liệu" bị coi là không tìm thấy vì bỏ cặp `$` (bổ sung 26/10, W2-3) | QC | Trung bình | Đã sửa |
| L-46 | Script đo trên Git Bash: `curl` mã 26 và thông báo lỗi gộp (bổ sung 26/10, W2-3) | Môi trường | Thấp | Đã sửa |
| L-47 | Câu Đúng/Sai bị gán nhãn theo mẫu bắt buộc bất chấp toán học, lời giải tự thú nhận (bổ sung 26/10) | LLM & parse | Cao | Đang sửa |

**Thống kê theo nhóm** (47 mục):

| Nhóm | Số lỗi | Đã sửa | Đang sửa | Chưa sửa |
|---|---|---|---|---|
| Môi trường | 11 | 11 | 0 | 0 |
| Dữ liệu/DB | 5 | 5 | 0 | 0 |
| Quy trình git | 2 | 2 | 0 | 0 |
| LLM & parse | 12 | 10 | 1 | 1 |
| QC | 9 | 8 | 0 | 1 |
| Hàng đợi/đồng thời | 7 | 6 | 0 | 1 |
| Xuất file | 1 | 1 | 0 | 0 |
| **Tổng** | **47** | **43** | **1** | **3** |

Theo mức độ: Cao 10, Trung bình 23, Thấp 14.

---

## Môi trường

### L-01 File migration đặt tên `.sql.sql`
- Nhóm: Môi trường
- Triệu chứng: file migration V2 có tên `V2__bank_item_generation_job_answer_check.sql.sql`. Thông báo lỗi nguyên văn
  chưa được lưu lại.
- Nguyên nhân gốc: Windows Explorer ẩn đuôi file, nên khi đặt tên "….sql" thì đuôi `.sql` bị nhân đôi mà không thấy.
- Cách phát hiện: đọc danh sách file trong thư mục migration (chưa xác minh cách phát hiện cụ thể).
- Cách sửa: xóa file sai tên, giữ file V2 đúng tên. Commit `4d2b4ab` ("xóa file V2 đặt nhầm tên .sql.sql").
- Chống tái phát: bật hiển thị đuôi file trên Windows. Chưa có test kiểm tra tên file migration.
- Bài học: với công cụ dựa vào quy ước tên file như Flyway, phải nhìn thấy tên file thật.
- Trạng thái: Đã sửa.

### L-02 Visual Paradigm không hiển thị bảng có cột JSONB
- Nhóm: Môi trường
- Triệu chứng: khi reverse DDL để vẽ ERD, bảng có cột JSONB không hiển thị. Nguồn: ghi chép của nhóm.
- Nguyên nhân gốc: chức năng reverse DDL của công cụ không nhận kiểu `JSONB` của PostgreSQL.
- Cách phát hiện: chạy công cụ vẽ ERD.
- Cách sửa (nguồn: ghi chép của nhóm): trong file ERD dùng riêng cho Visual Paradigm, đổi các cột `JSONB` thành `TEXT`
  để reverse DDL hiển thị đủ bảng. Schema thật (migration Flyway) giữ nguyên `JSONB`. Ghi chú: bản
  `db/schema_final_erd.sql` đang có trong repo vẫn còn hai cột `JSONB` (`exemplar_ids`, `hyperparams`), nên file đã sửa
  dùng để vẽ là bản ngoài repo, hoặc chưa được commit lại.
- Chống tái phát: chỉ đổi kiểu ở file ERD, không đổi schema thật.
- Bài học: kiểm tra trước công cụ vẽ sơ đồ có hỗ trợ kiểu dữ liệu riêng của PostgreSQL không; nếu phải đổi để vẽ thì
  tách hẳn file vẽ khỏi schema thật.
- Trạng thái: Đã sửa.

### L-03 Lệch package `com.datn.back_end` và `com.datn.mcq`
- Nhóm: Môi trường
- Triệu chứng: lớp khởi động nằm ở `com.datn.back_end` (theo tên project tạo ban đầu), còn code dự kiến ở
  `com.datn.mcq`. Triệu chứng cụ thể chưa xác minh; thường gặp là component scan không thấy bean, hoặc test không nạp
  được context.
- Nguyên nhân gốc: Spring Initializr sinh package từ tên artifact `back_end`.
- Cách phát hiện: chưa xác minh.
- Cách sửa: đổi package sang `com.datn.mcq` (`BackEndApplication.java`, `BackEndApplicationTests.java`). Commit
  `58326f3` ("setup docker, db migration and refactor package name").
- Chống tái phát: cấu trúc package theo module được ghi ở `docs/architecture/backend-structure.md`.
- Bài học: chốt tên package gốc ngay khi tạo project; đổi muộn thì phải sửa nhiều file.
- Trạng thái: Đã sửa.

### L-04 DbEnumBindingTest: "No ServletContext set"
- Nhóm: Môi trường
- Triệu chứng: test `DbEnumBindingTest` lỗi khi dựng context, thông báo "No ServletContext set".
- Nguyên nhân gốc: test tự dựng `AnnotationConfigWebApplicationContext` mà không cấp `ServletContext`, trong khi cấu
  hình Web MVC cần nó.
- Cách phát hiện: chạy test.
- Cách sửa: thêm `context.setServletContext(new MockServletContext())` trong `DbEnumBindingTest.setUp`. Commit
  `bee21e4`.
- Chống tái phát: chính `DbEnumBindingTest` (kiểm tra binding enum giá trị DB qua Web MVC).
- Bài học: test tự dựng context web phải giả lập đủ môi trường servlet.
- Trạng thái: Đã sửa.

### L-05 `docker compose down -v` xóa luôn DB test `datn_mcq_test`
- Nhóm: Môi trường
- Triệu chứng: sau khi chạy `docker compose down -v`, database test `datn_mcq_test` mất, test không kết nối được.
  Nguồn: ghi chép của nhóm.
- Nguyên nhân gốc: `-v` xóa volume `pgdata`, trong đó có mọi database của container, kể cả DB test tạo bằng tay.
- Cách phát hiện: chạy test sau khi dựng lại container.
- Cách sửa (nguồn: ghi chép của nhóm): khi sự cố xảy ra, tạo lại `datn_mcq_test` thủ công. Hướng dài hạn là script
  khởi tạo DB test: repo có `db/init/01-create-test-db.sql` (`CREATE DATABASE datn_mcq_test;`), mount vào
  `/docker-entrypoint-initdb.d` trong `docker-compose.yml` (commit `0c123a8`), nên khi volume mới được tạo thì DB test
  được tạo lại.
- Chống tái phát: script init ở trên. Không dùng `down -v` khi chỉ muốn tắt container.
- Bài học: dữ liệu và DB phụ trợ của môi trường dev phải tạo lại được bằng script, không tạo bằng tay.
- Trạng thái: Đã sửa.

### L-06 Script Python lỗi `UnicodeEncodeError` (cp1252) trên Windows
- Nhóm: Môi trường
- Triệu chứng: script Python in hoặc ghi chuỗi tiếng Việt báo `UnicodeEncodeError` với codec `cp1252`. Nguồn: ghi chép
  ngoài repo.
- Nguyên nhân gốc: trên Windows, `open()` và `print()` mặc định dùng bảng mã hệ thống (cp1252), không phải UTF-8.
- Cách phát hiện: chạy script.
- Cách sửa (nguồn: ghi chép của nhóm): thêm `sys.stdout.reconfigure(encoding="utf-8")` và
  `sys.stderr.reconfigure(encoding="utf-8")` ở đầu script Python.
- Chống tái phát: dòng cấu hình trên ở đầu mỗi script. `docs/ai-development-rules.md` M-38 nhắc rằng PowerShell hiển thị
  sai UTF-8 tiếng Việt, và không được "sửa encoding" dựa trên output hiển thị.
- Bài học: luôn chỉ rõ bảng mã khi đọc/ghi văn bản tiếng Việt trên Windows.
- Trạng thái: Đã sửa.

### L-07 Model Gemini trả 404; free tier giới hạn 20 lượt/ngày
- Nhóm: Môi trường
- Triệu chứng: gọi `gemini-2.5-flash` bằng tài khoản mới trả HTTP 404; `gemini-3.8-flash` ở free tier chỉ cho 20 lượt
  mỗi ngày. Nguồn: ghi chép của nhóm.
- Nguyên nhân gốc: model không mở cho tài khoản mới, và hạn mức free tier khác nhau theo model.
- Cách phát hiện: chạy thật.
- Cách sửa (nguồn: ghi chép của nhóm): chuyển sang `gemini-3.5-flash-lite`. Nhóm không dùng nhiều API key để lách quota.
  Repo dùng model này làm mặc định cho cả sinh câu và Solver (`application.yaml`, `LLM_MODEL`/`LLM_SOLVER_MODEL`), có từ
  commit `9d845e6`. Job sinh câu cách nhau 13 giây để "tránh đụng trần 5 RPM trên Free Tier" (`GenerationService`,
  comment ở chỗ đặt `nextRunAt`).
- Chống tái phát: tên model cấu hình qua biến môi trường. 429/503 được trả lại đúng mã cho API (`LlmClientUpstreamErrorTest`,
  `ApiExceptionHandlerUpstreamTest`). Phân loại lỗi khi thử lại chưa có (xem L-36).
- Bài học: kiểm tra model và hạn mức của tài khoản thật trước khi thiết kế số lần gọi LLM.
- Trạng thái: Đã sửa.

### L-08 curl trong Git Bash gửi JSON tiếng Việt sai bảng mã; `docker exec -it` lỗi TTY
- Nhóm: Môi trường
- Triệu chứng: `curl` gửi body JSON tiếng Việt thì backend trả 400; `docker exec -it` báo lỗi TTY trong Git Bash.
  Nguồn: ghi chép của nhóm.
- Nguyên nhân gốc: chuỗi tiếng Việt gõ trên dòng lệnh không được gửi đúng UTF-8; Git Bash (mintty) không cấp TTY như
  `docker exec -it` cần. Ngoài ra, Git Bash (MSYS) tự đổi đường dẫn dạng `/…` trong tham số thành đường dẫn Windows,
  làm hỏng đường dẫn bên trong container.
- Cách phát hiện: thử API bằng tay.
- Cách sửa (nguồn: ghi chép của nhóm):
  - ghi body JSON ra file UTF-8, gửi bằng `curl --data-binary @file` với header
    `Content-Type: application/json; charset=UTF-8`, hoặc thử qua Swagger;
  - trong Git Bash dùng `docker exec -i` (bỏ `-t`);
  - đặt `MSYS_NO_PATHCONV=1` khi truyền đường dẫn bên trong container.
- Chống tái phát: các test API dùng MockMvc với `MediaType.APPLICATION_JSON` thay cho thử bằng tay.
- Bài học: kiểm tra API tiếng Việt bằng test hoặc công cụ có đặt bảng mã rõ ràng, không dựa vào shell.
- Trạng thái: Đã sửa.

### L-09 PostgreSQL cài sẵn chiếm cổng 5432; thiếu JAVA_HOME; IDE sai thư mục làm việc
- Nhóm: Môi trường
- Triệu chứng: máy một thành viên báo "permission denied for schema public"; thiếu `JAVA_HOME` nên `mvnw` không chạy;
  chạy app từ IDE không đọc được `../.env`. Nguồn: ghi chép của nhóm (một phần có dấu vết trong repo).
- Nguyên nhân gốc: PostgreSQL cài sẵn trên máy chiếm cổng 5432, app nối vào đó thay vì container (với user khác quyền);
  cấu hình chạy của IDE dùng thư mục làm việc khác `back_end/`, nên đường dẫn `../.env` không trỏ tới file `.env` ở
  thư mục gốc repo; máy chưa cài JDK hoặc chưa đặt `JAVA_HOME`.
- Cách phát hiện: chạy app trên máy thành viên.
- Cách sửa (nguồn: ghi chép của nhóm):
  - máy có PostgreSQL cài sẵn chiếm 5432 thì đưa container Docker sang `DB_PORT=5433` (`docker-compose.yml` và
    `application.yaml` đều đọc `DB_PORT`);
  - đặt thư mục làm việc (cwd) trong cấu hình chạy của IDE là `back_end/` để đọc được `../.env`;
  - cài JDK và đặt `JAVA_HOME`;
  - trong repo, `application.yaml` nạp cả `optional:file:.env` và `optional:file:../.env` (commit `0490e0f`).
- Chống tái phát: cấu hình đọc `.env` theo cả hai thư mục; cổng DB cấu hình được.
- Bài học: môi trường dev phải chạy được từ cả IDE lẫn CLI, và không giả định cổng mặc định còn trống.
- Trạng thái: Đã sửa.

### L-10 Cổng 8080 bị app cũ chiếm; terminal mới mất biến `export` nên app nối nhầm DB dev
- Nhóm: Môi trường
- Triệu chứng: app không khởi động vì cổng 8080 đang bị một tiến trình cũ dùng; mở terminal mới thì các biến đã
  `export` mất, app nối nhầm vào DB dev. Nguồn: ghi chép của nhóm.
- Nguyên nhân gốc: tiến trình backend cũ chưa tắt; biến môi trường đặt bằng `export` chỉ sống trong một phiên shell.
- Cách phát hiện: chạy thật.
- Cách sửa (nguồn: ghi chép của nhóm): cổng 8080 bị chiếm thì tắt app cũ; mở terminal mới thì `export` lại các biến,
  hoặc ghi chúng vào `.env` (app đọc `.env` qua `spring.config.import`, xem L-09). Phía test có bộ chặn riêng (L-12).
- Chống tái phát: ghi biến vào `.env` thay vì `export`. Test gắn cứng `datn_mcq_test` và có guard chặn mọi DB khác
  (`TestSafetyConfiguration`). Phía app chưa có cơ chế chặn nối nhầm DB.
- Bài học: không để cấu hình kết nối DB phụ thuộc vào trạng thái của một phiên shell.
- Trạng thái: Đã sửa.

### L-46 Script đo trên Git Bash: `curl` mã 26 và thông báo lỗi gộp (bổ sung 26/10, W2-3)
- Nhóm: Môi trường
- Triệu chứng: script đo thật (đăng nhập → tải tài liệu → sinh đề) báo `curl: (26) Failed to open/read local data from
  file/application` ở bước tải tài liệu, rồi in "không kết nối được", dù app đang chạy và đã đăng nhập được.
- Nguyên nhân gốc: `curl` trong Git Bash là bản Windows; tham số dạng `-F "file=@/tmp/x"` không được MSYS đổi đường dẫn vì chuỗi
  không bắt đầu bằng `/` (khác L-08), nên curl không mở được file. Script cũ còn gộp mọi lỗi curl vào một thông báo.
- Cách phát hiện: người chạy script trên Git Bash (Windows) gửi lại đầu ra; tái hiện bằng server giả và curl thật.
- Cách sửa: curl chạy trong thư mục tạm với tên file tương đối; tách thông báo lỗi curl (mã thoát, gợi ý theo mã, stderr) khỏi
  lỗi HTTP (mã trạng thái, `code`, `error`).
- Chống tái phát: script nằm ngoài repo nên không có test; đã thử khô lại trên Git Bash với curl thật và server giả.
- Bài học: script chạy trên máy người dùng phải thử trên đúng shell của họ; đừng dồn mọi lỗi vào một thông báo.
- Trạng thái: Đã sửa.

## Dữ liệu/DB

### L-11 Cột `CHAR` lệch kiểu với Hibernate validate (bổ sung từ repo)
- Nhóm: Dữ liệu/DB
- Triệu chứng: với `ddl-auto: validate`, Hibernate báo kiểu cột không khớp ở 4 cột `CHAR(1)`/`CHAR(4)`. Thông báo
  nguyên văn chưa được lưu lại.
- Nguyên nhân gốc: Hibernate ánh xạ `String` sang `VARCHAR`; schema V1 dùng `CHAR`. Các CHECK liên quan còn giữ ép kiểu
  `::bpchar` trong `pg_constraint`.
- Cách phát hiện: khởi động app hoặc test (Hibernate validate lúc dựng context).
- Cách sửa: migration `V3__char_to_varchar.sql` đổi sang `VARCHAR` và tạo lại các CHECK. Commit `aed47ff`. Không sửa V1.
- Chống tái phát: `ddl-auto: validate` chạy mỗi lần dựng context test; quy tắc "đổi schema thì viết migration mới,
  không sửa file cũ" (README, AGENTS.md).
- Bài học: chọn kiểu cột theo cách ORM ánh xạ, và để ORM validate schema thay vì tự tạo.
- Trạng thái: Đã sửa.

### L-12 Test ghi dữ liệu vào DB dev (bổ sung từ repo)
- Nhóm: Dữ liệu/DB
- Triệu chứng: các `@SpringBootTest` dùng datasource từ `.env`, nên chạy test sẽ chạy Flyway và ghi dữ liệu vào DB dev
  (phát hiện F-3 trong audit 21/10).
- Nguyên nhân gốc: test và app dùng chung cấu hình kết nối; không có DB riêng cho test.
- Cách phát hiện: audit repository ngày 21/10 (`docs/ai-development-rules.md` mục 11, F-3).
- Cách sửa: DB riêng `datn_mcq_test` gắn cứng trong `src/test/resources/application.yaml` ("tuyệt đối KHÔNG dùng
  ${DB_NAME}"); `TestSafetyConfiguration` chặn kết nối tới mọi DB khác (cả DataSource của Flyway) và thay `LlmClient`
  bằng `BlockingTestLlmClient` để test không gọi Gemini. Có từ commit `6fa334f` ("blind review and test isolation
  baseline"); Q-7 ghi "Đã triển khai (2026-10-21)".
- Chống tái phát: `TestSafetyConfiguration` tự nạp cho mọi `@SpringBootTest`; `BlockingTestLlmClientGuardTest`.
- Bài học: test tích hợp phải bị chặn cứng khỏi DB thật và dịch vụ tính phí, không chỉ "nhớ cấu hình đúng".
- Trạng thái: Đã sửa.

### L-13 Lời giải bị bỏ khi chép vào ngân hàng (quy tắc `explanationForBank`)
- Nhóm: Dữ liệu/DB
- Triệu chứng: câu sinh ra có lời giải, nhưng sau khi Giữ (hoặc duyệt chấm mù) thì `bank_item.explanation` là NULL.
  API xem đề hiển thị bản trong ngân hàng nên không có `explanation`. Ví dụ cụ thể ở L-14.
- Nguyên nhân gốc: `TeacherReviewService.explanationForBank` trả `null` khi lời giải có lỗi LaTeX nghiêm trọng (ví dụ
  `\frac{5x + 3}{y}` nằm ngoài `$...$`). Quy tắc này đưa vào ở commit `0c123a8` (22/10, "việc 2"). Nó mâu thuẫn với
  quyết định 21/10 mục 3 ("giữ lời giải LLM… Chưa làm việc đổi lời giải ngân hàng thành NULL") và với quy tắc chính
  cùng commit đó: lỗi LaTeX ở lời giải chỉ là cảnh báo.
- Cách phát hiện: người dùng đọc đề thật (đề 2, chỗ 20), sau đó lần theo code.
- Cách sửa: `explanationForBank` luôn trả nguyên lời giải; áp cho cả 4 đường chép vào ngân hàng (Giữ, duyệt chấm mù
  MCQ/SA/TF), đã được nhóm đồng ý. Commit `5fefe87`.
- Chống tái phát: `BankExplanationPolicyTest` (lời giải có lỗi LaTeX vẫn được giữ);
  `ExamBuildTest.loiGiaiCoLatexNgoaiCongThucVanConSauKhiGiu` (sinh câu → Giữ → xem đề vẫn có lời giải);
  `TeacherReviewEditPolicyTest` (đường duyệt chấm mù). `docs/contract/enums.md` đã ghi quy tắc mới.
- Bài học: hai quy tắc về cùng một dữ liệu (lời giải lỗi LaTeX là "cảnh báo" và "bị bỏ") phải được đối chiếu trước khi
  cài đặt.
- Trạng thái: Đã sửa (code). Dữ liệu đã lưu NULL trước bản sửa: xem L-14.

### L-14 Lời giải mất ở chỗ 20 đề 2 (dữ liệu dev)
- Nhóm: Dữ liệu/DB
- Triệu chứng: đề 2, chỗ 20 (`bankItemId` 2, `questionId` 46): API xem đề không trả `explanation`. Lời giải gốc có
  `\frac{5x + 3}{y}` nằm ngoài `$...$`.
- Nguyên nhân gốc: L-13. Câu được Giữ trước bản sửa nên `bank_item` id 2 đã lưu `explanation = NULL`.
- Cách phát hiện: người dùng đọc đề thật.
- Cách sửa: code đã sửa ở `5fefe87`, nhưng bản sửa không tự khôi phục dòng cũ. Dữ liệu dev (nguồn: ghi chép của nhóm)
  được sửa bằng một câu SQL cập nhật `bank_item.explanation` từ `generated_question.explanation` của câu gốc. Đây là dữ
  liệu dev, nên nhóm không làm migration.
- Chống tái phát: như L-13. `docs/contract/enums.md` ghi rằng các dòng NULL theo quy tắc cũ không được khôi phục tự động.
- Bài học: sửa một quy tắc ghi dữ liệu phải tính luôn phần dữ liệu đã ghi theo quy tắc cũ.
- Trạng thái: Đã sửa (code ở `5fefe87`, dữ liệu dev bằng SQL thủ công).

### L-43 Hai ô dùng chung template có thể nhận cùng một câu ngân hàng (bổ sung 24/10, W1-3)
- Nhóm: Dữ liệu/DB
- Triệu chứng: khi tạo đề, nếu hai ô ma trận dùng chung một `question_template` (DB không cấm) và ô đầu cần từ 2 câu trở
  lên, hai chỗ của ô đầu có thể cùng lấy một `bank_item`. Test trước khi sửa:
  `haiOChungMotTemplateKhongNhanDoiMotCauNganHang ==> expected: <[2117]> but was: <[2117, 2117]>`. Chưa gặp trên dữ liệu
  thật (hiện mỗi ô tạo template riêng), nên là lỗi tiềm ẩn.
- Nguyên nhân gốc: `ExamSlotRepository.findBankCandidates` nối `LEFT JOIN exam_matrix_cell c ON c.template_id = t.id`, nhân
  dòng khi một template có nhiều ô, rồi `LIMIT` lấy cả hai bản sao của cùng một câu.
- Cách phát hiện: audit thiết kế W1-3 (đọc truy vấn, suy luận), rồi dựng test.
- Cách sửa: thay phép nối bằng `NOT EXISTS`/`EXISTS` trên `exam_matrix_cell`, nên mỗi câu chỉ ra một dòng.
- Chống tái phát: `ExamBankSelectionTest.haiOChungMotTemplateKhongNhanDoiMotCauNganHang`.
- Bài học: nối với bảng có quan hệ một-nhiều rồi mới `LIMIT` thì phải kiểm tra trùng dòng; dùng `EXISTS` khi chỉ cần điều
  kiện.
- Trạng thái: Đã sửa.

## Quy trình git

### L-15 Push nhầm nhánh; amend commit đã push gây non-fast-forward
- Nhóm: Quy trình git
- Triệu chứng: push lên nhầm nhánh; sau khi amend một commit đã push thì `git push` bị từ chối "non-fast-forward".
  Nguồn: ghi chép của nhóm.
- Nguyên nhân gốc: amend tạo commit mới thay cho commit đã có trên remote, nên lịch sử local và remote rẽ nhánh.
- Cách phát hiện: thông báo của `git push`.
- Cách sửa (nguồn: ghi chép của nhóm):
  - push nhầm nhánh: fast-forward merge các commit sang nhánh đúng rồi push nhánh đúng;
  - thêm alias `git where` để xem nhanh nhánh hiện tại trước khi push;
  - amend commit đã push: kiểm tra diff giữa bản local và bản trên remote là rỗng (chỉ khác message hoặc metadata), rồi
    `git push --force-with-lease`, chỉ trên nhánh feature; không bao giờ force push `main` hay `backend/init-project`.
- Chống tái phát: alias `git where`; quy tắc force push ở trên. README ("Không push thẳng vào `main`. Làm trên nhánh, mở
  pull request…"); AGENTS.md (không commit, push hay sửa lịch sử Git khi chưa được yêu cầu).
- Bài học: không amend hay rebase commit đã push lên nhánh dùng chung; kiểm tra nhánh hiện tại trước khi push.
- Trạng thái: Đã sửa.

### L-16 Commit thiếu file: `eca0720` chỉ chứa openapi.json (bổ sung từ repo)
- Nhóm: Quy trình git
- Triệu chứng: commit `eca0720` có message mô tả cả tính năng tạo đề theo ma trận (V6, V7, API, prompt, test), nhưng chỉ
  chứa `openapi.json`.
- Nguyên nhân gốc: chỉ một file được stage trước khi commit (chưa xác minh chi tiết thao tác).
- Cách phát hiện: commit sau `ed8db66` ghi rõ "Completes eca0720, which only contained openapi.json".
- Cách sửa: commit bổ sung `ed8db66` chứa migration V6, V7, code, test và tài liệu.
- Chống tái phát: chưa có quy tắc riêng. Nên xem `git status` và `git diff --stat --cached` trước khi commit.
- Bài học: message commit phải khớp nội dung commit; kiểm tra danh sách file đã stage trước khi commit.
- Trạng thái: Đã sửa.

## LLM & parse

### L-17 LLM trả chuỗi `\n` dạng chữ trong JSON
- Nhóm: LLM & parse
- Triệu chứng: sau khi giải mã JSON, văn bản chứa hai ký tự `\` và `n` thay cho xuống dòng. Trong công thức, `\neq`
  viết thiếu escape lại bị giải mã thành ký tự xuống dòng kèm "eq".
- Nguyên nhân gốc: model viết `\\n` trong JSON (thoát hai lần) khi muốn xuống dòng; và viết `\neq` một dấu gạch, mà
  `\n` là escape JSON hợp lệ.
- Cách phát hiện: đọc câu sinh thật (ghi chú "Việc 1 (22/10)" trong `McqLiteralNewlineTest`).
- Cách sửa: ngoài `$...$` thì đổi `\n` dạng chữ thành xuống dòng, trong công thức giữ nguyên (`McqJsonParser`). Commit
  `0c123a8`. Commit `580f5c0` xử lý thêm chữ tiếng Việt dạng NFD đứng sau `\n`.
- Chống tái phát: `McqLiteralNewlineTest`, `LatexFormatValidatorTest.unescapedNeqInJsonDecodedAsNewlineInsideMathShouldBeReportedAsError`.
  Quy tắc ghi ở `docs/contract/enums.md`.
- Bài học: đầu ra LLM có thể đúng cú pháp JSON mà vẫn sai ý; phải kiểm tra nghĩa sau khi giải mã.
- Trạng thái: Đã sửa.

### L-18 JSON có khóa trùng (`option_c`)
- Nhóm: LLM & parse
- Triệu chứng: câu thật `generated_question` id 8 có hai khóa `"option_c"`, khóa thứ hai đáng lẽ là `"correct_option"`.
  Parser cũ lặng lẽ lấy một trong hai giá trị. Solver cũng từng trả hai khóa `"judge_answer"`.
- Nguyên nhân gốc: model sinh nhầm tên khóa; parser cho phép khóa trùng.
- Cách phát hiện: đọc dữ liệu thật (ghi chú trong `McqDuplicateKeyTest`).
- Cách sửa: `SimpleJson` từ chối khóa trùng ở mọi độ sâu, thông báo "Khóa JSON bị trùng: …". Commit `580f5c0`. Với Solver:
  `answer_check = ERROR`, câu giữ `generated` (commit `93a1734`).
- Chống tái phát: `McqDuplicateKeyTest`, `SaJsonParserTest.khoaTrungLaLoiGiaiMa`, `TfJsonParserTest.khoaTrungLaLoiGiaiMa`,
  `SolverDuplicateJudgeAnswerTest`.
- Bài học: parser cho đầu ra LLM phải chặt hơn parser thông thường; mơ hồ thì báo lỗi, không đoán.
- Trạng thái: Đã sửa.

### L-19 Lỗi LaTeX chỉ nằm trong lời giải làm hỏng cả câu
- Nhóm: LLM & parse
- Triệu chứng: đề và 4 phương án đúng định dạng, nhưng câu vẫn bị `format_failed` vì lời giải có lỗi LaTeX.
- Nguyên nhân gốc: bước format coi lỗi LaTeX ở mọi trường như nhau.
- Cách phát hiện: đọc kết quả QC của câu sinh thật (chưa xác minh chi tiết).
- Cách sửa: lỗi LaTeX ở `question_text` và 4 phương án vẫn làm câu `format_failed`; ở `explanation` chỉ là cảnh báo
  (`FormatChecker`). Commit `0c123a8`.
- Chống tái phát: `FormatCheckerLatexSeverityTest`, `TeacherReviewLatexWarningTest`; quy tắc ở `docs/contract/enums.md`.
- Bài học: mức nghiêm trọng của lỗi định dạng phải theo vai trò của trường đối với học sinh.
- Trạng thái: Đã sửa.

### L-20 Escape LaTeX sai trong JSON (`\circ` → "Invalid \escape")
- Nhóm: LLM & parse
- Triệu chứng: giải mã JSON lỗi `Invalid \escape: '\c' tại vị trí …` với câu có `$75^\circ$`. Theo ghi chép ngoài
  repo, 9/9 lần sinh câu chủ đề Tứ giác bị hỏng vì lỗi này (con số chưa xác minh trong repo). Lần chạy thật đề 1 được
  14/18 câu.
- Nguyên nhân gốc: model viết lệnh LaTeX với một dấu `\` trong chuỗi JSON. `\c` không phải escape JSON hợp lệ nên lỗi
  giải mã. Nguy hiểm hơn, `\f`, `\b`, `\t`, `\r` là escape hợp lệ: `\frac` âm thầm thành ký tự form feed cộng "rac".
- Cách phát hiện: chạy thật đề 1, đọc `raw_llm_output` của câu hỏng.
- Cách sửa (commit `4964fd5`):
  - `LlmOutputRepair` nhân đôi `\` trước escape không hợp lệ, trước `\u` không theo sau bởi 4 chữ số hex, trước
    `\b \f \r \t` đứng trước chữ cái ASCII, và trước `\n` khi đúng là một lệnh trong danh sách `\neq`, `\ne`, `\neg`…;
    mỗi lần sửa ghi cảnh báo `json: đã sửa N escape LaTeX (…)`.
  - MCQ production gọi Gemini kèm schema JSON như SA/TF.
  - V8 lưu lý do format không qua ở `quality_check_result.errors`.
  - Chế độ sửa chỉ áp cho run `production`; run `experiment` (thực nghiệm C1/C2/C3) giữ giải mã chặt để không đổi kết
    quả đo. Ngày 23/10 nhóm bỏ thí nghiệm C1/C2/C3 (quyết định 23/10 trong `docs/ai-development-rules.md`); code vẫn giữ
    cách tách này.
- Chống tái phát: `LlmOutputRepairTest` (fixture từ câu thật id 20 và id 24), `V8UpgradeFromV7Test`. Ngoại lệ có chủ
  đích của quy tắc 6 ghi ở `docs/architecture/backend-structure.md`.
- Bài học: sửa đầu ra LLM phải có quy tắc hẹp, ghi lại mỗi lần sửa, và không áp lên dữ liệu dùng để đo thực nghiệm.
- Trạng thái: Đã sửa.

### L-21 Thiếu `$` đóng ở cuối câu
- Nhóm: LLM & parse
- Triệu chứng: trường văn bản có số dấu `$` lẻ vì quên đóng công thức ở cuối (ví dụ câu id 24 trong đề 1), làm câu
  `format_failed`.
- Nguyên nhân gốc: model quên `$` đóng, thường ngay trước dấu chấm cuối câu.
- Cách phát hiện: chạy thật đề 1.
- Cách sửa: tự chèn `$` ở cuối trường, trước dấu câu kết thúc, chỉ khi số `$` lẻ (không tính `\$`) và phần sau `$`
  cuối trông như công thức; ghi cảnh báo `<trường>: đã tự đóng $`. `$` lẻ ở giữa câu vẫn là lỗi format. Prompt TF thêm
  quy tắc "mọi công thức mở bằng $ phải đóng bằng $ trước dấu câu". Commit `4964fd5`.
- Chống tái phát: `LlmOutputRepairTest`, `TfPromptDraftV0Test.quyTacYBamDeDanVaDongDoLa`.
- Bài học: tự sửa chỉ ở trường hợp đoán chắc được ý, còn lại vẫn báo lỗi.
- Trạng thái: Đã sửa (cách nhận biết "trông như công thức" là heuristic, có ca biên như `$xy = 3` không được tự đóng).

### L-22 Ý Đúng/Sai có tiền tố "a)" ở đầu (bổ sung từ repo)
- Nhóm: LLM & parse
- Triệu chứng: nội dung ý TF bắt đầu bằng "a)", "b)"…, khi hiển thị thành "a) a) …".
- Nguyên nhân gốc: model tự đánh nhãn ý trong nội dung.
- Cách phát hiện: đọc câu sinh thật (chưa xác minh chi tiết).
- Cách sửa: parser bỏ tiền tố khớp vị trí ý và ghi cảnh báo `statement_a: đã bỏ tiền tố "a)" ở đầu ý`; prompt dặn
  "Không ghi "a)", "b)" ở đầu nội dung ý". Commit `5f511fe`, tinh chỉnh ở `f0b1ca0`.
- Chống tái phát: `TfJsonParserTest.boTienToKhopViTriKemCanhBao`, `khongBoKhiKhongPhaiTienTo` và các test cùng nhóm.
- Bài học: đã ra lệnh trong prompt vẫn phải kiểm tra lại ở parser.
- Trạng thái: Đã sửa.

### L-23 Câu SA/TF lặp dạng (cùng $2x - 6 = 0$)
- Nhóm: LLM & parse
- Triệu chứng: sinh nhiều câu từ cùng một câu nguồn thì gần như chép lại cùng bài toán $2x - 6 = 0$.
- Nguyên nhân gốc: prompt đưa câu nguồn mà không cấm chép số liệu, và model không biết các câu đã sinh trước đó.
- Cách phát hiện: đọc dữ liệu sinh thật.
- Cách sửa: thêm quy tắc "BẮT BUỘC tạo một bài toán MỚI…" và danh sách tối đa 10 câu đã sinh của template vào prompt
  (`PreviousQuestion`); nâng phiên bản prompt lên `sa-v1-draft`, `tf-v1-draft`. Commit `dfbc120`.
- Chống tái phát: `PreviousQuestionTest`, `PreviousQuestionsPromptTest`, `SaPromptDraftV0Test`, `TfPromptDraftV0Test`.
- Bài học: muốn LLM sinh đa dạng thì phải cho nó biết đã sinh gì, không chỉ dặn "hãy đa dạng".
- Trạng thái: Đã sửa (giảm thiểu bằng prompt; mức đa dạng chưa được đo lại bằng số liệu).

### L-24 Các câu cùng một ô ma trận cùng dạng
- Nhóm: LLM & parse
- Triệu chứng: đọc đề 1, các câu cùng một ô ma trận cùng một dạng hỏi.
- Nguyên nhân gốc: chưa xác minh trên prompt thật. Code đã dựng prompt lúc worker nhận job, kèm câu đã sinh của ô,
  nhưng chưa phân biệt câu thứ mấy của ô.
- Cách phát hiện: người dùng đọc đề thật.
- Cách sửa: ghi chú riêng của mỗi job (`generation_job.prompt_note`, V8): "Đây là câu thứ k/n của ô này: dạng hỏi phải
  khác các câu còn lại của ô…; mỗi câu kiểm tra một ý khác". Commit `4964fd5`.
- Chống tái phát: `ExamBuildTest.moiChoTrongOBietCauThuKVaCauDaSinhCuaO`, `MatrixPromptDraftV0Test`.
- Bài học: khi sinh nhiều câu cho cùng một yêu cầu, mỗi lời gọi cần biết vị trí của mình trong nhóm.
- Trạng thái: Đã sửa (hiệu quả trên đề thật chưa đo lại).

### L-25 Mọi câu Đúng/Sai có nhãn T,T,F,F
- Nhóm: LLM & parse
- Triệu chứng: cả 4 câu TF trong 2 đề thật đều có nhãn T,T,F,F; ý b luôn là công thức chung, ý c và d luôn cùng Sai.
- Nguyên nhân gốc: model có thiên lệch về mẫu nhãn khi để nó tự chọn.
- Cách phát hiện: người dùng đọc đề thật.
- Cách sửa (commit `06e9441`):
  - Hệ thống tự chọn mẫu nhãn ngẫu nhiên trong 14 mẫu có 1–3 ý Đúng, tránh trùng mẫu của câu TF gần nhất cùng đề
    (`TfLabelPattern`), và ghi mẫu vào prompt: "Mẫu nhãn bắt buộc (T,F,F,T): ý a phải Đúng, …".
  - Mẫu lưu ở `generation_job.prompt_note`, truy vấn được.
  - Nhãn trả về khác mẫu thì ghi cảnh báo và bật "cần xem kỹ", không loại câu.
  - Thêm quy tắc "ý d (Vận dụng) phải cần suy luận thêm, không được chỉ là hệ quả trực tiếp của ý c".
  - Chỉ áp cho luồng ma trận; prompt `matrix-tf-v2-draft`.
- Chống tái phát: `TfLabelPatternTest`, `ExamBuildTest.moiCauTfCoMauNhanBatBuocKhacCauTfGanNhat`,
  `ExamBuildTest.nhanKhacMauBatBuocCanhBaoVaCanXemKyKhongLoaiCau`,
  `ExamBuildTest.giuCauTfNhanLechMauLuuNhanLlmVaoNganHang` (khi Giữ, ngân hàng lưu nhãn thật của LLM, không lấy mẫu).
- Bài học: phân bố đáp án nên do hệ thống quyết định, không để LLM tự chọn.
- Trạng thái: Đã sửa.

### L-26 Phương án nhiễu tương đương đáp án đúng
- Nhóm: LLM & parse
- Triệu chứng: câu MCQ có phương án nhiễu là biểu thức tương đương đáp án, ví dụ $3(x^2 - 4x)$ khi đáp án là
  $3x(x - 4)$, nên câu có hai đáp án đúng.
- Nguyên nhân gốc: model tạo nhiễu bằng cách biến đổi hình thức đáp án mà không kiểm giá trị.
- Cách phát hiện: người dùng đọc đề thật.
- Cách sửa: thêm quy tắc vào prompt MCQ của luồng ma trận: "Mỗi phương án nhiễu phải SAI về giá trị, không được là biểu
  thức tương đương với đáp án đúng…". Prompt MCQ của thực nghiệm C1/C2/C3 (`PromptBuilder`) không đổi. Commit `4964fd5`.
- Chống tái phát: `MatrixPromptDraftV0Test.mcqPhuongAnNhieuPhaiSaiVeGiaTri` (chỉ kiểm prompt có quy tắc). Chưa có kiểm
  tra tự động phát hiện phương án tương đương.
- Bài học: lỗi về giá trị toán học khó chặn bằng prompt; cần thêm một bước kiểm tra tính toán.
- Trạng thái: Đã sửa (giảm thiểu bằng prompt; chưa có kiểm tra tự động).

### L-27 Đáp án lộ trong đề
- Nhóm: LLM & parse
- Triệu chứng: nội dung đề nêu sẵn đáp án hoặc gợi ý trực tiếp đáp án. Nguồn: ghi chép ngoài repo.
- Nguyên nhân gốc: chưa xác minh.
- Cách phát hiện: người dùng đọc đề thật.
- Cách sửa: chưa có. Không tìm thấy quy tắc prompt hay bước QC nào cho lỗi này trong repo. (Khác với vấn đề lời giải làm
  lộ đáp án ở form chấm mù, đã xử lý theo Q-1.)
- Chống tái phát: chưa có.
- Bài học: chưa rút ra.
- Trạng thái: Chưa sửa. Kế hoạch (nguồn: ghi chép của nhóm): thêm quy tắc phát hiện đáp án lộ trong đề, và kiểm đáp án
  bằng tính toán ký hiệu.

### L-47 Câu Đúng/Sai bị gán nhãn theo mẫu bắt buộc bất chấp toán học, lời giải tự thú nhận (bổ sung 26/10)
- Nhóm: LLM & parse
- Triệu chứng: đo baseline 26/10 (2 đề × 10 câu tf, có tài liệu, prompt `matrix-tf-v2-draft`; 27 lần sinh cho 20 chỗ):
  - 11 trên 27 lần sinh (41%) có ít nhất một nhãn sai thật so với toán học (đọc tay từng lời giải), tổng 15 ý trên 108: 11 ý
    sai nhưng gán T và 4 ý đúng nhưng gán F.
  - Solver lệch 10 trên 26 lần có kết quả (38,5%); 10 trong 11 lần sinh sai nhãn thật bị Solver bắt (lần còn lại là câu trùng nên
    chưa chạy Solver).
  - 6 lần sinh (22%) có lời giải tự thú nhận, ví dụ "đúng nhưng theo mẫu yêu cầu gán nhãn F", "theo mẫu nhãn yêu cầu c phải Sai",
    "Xin lỗi, ta làm lại biểu thức"; regex chỉ bắt được 3 trong số đó. 5 lần sinh khác sai nhãn một cách lặng lẽ (lời giải tính
    ra ý sai rồi kết luận "Đúng").
  - 6 trên 20 chỗ phải sinh lại; 4 chỗ kết thúc ở câu lệch Solver (kèm cờ "cần xem kỹ"); 5 trong 7 lần sinh lại cũng sai.
- Nguyên nhân gốc: prompt v2 ghi "Mẫu nhãn bắt buộc (T,F,F,T): ý c phải Sai…" (lệnh đặt trên nhãn) mà không nói cách đạt mẫu
  bằng cách viết ý sai, cũng không cấm gán nhãn trái lời giải. JSON ra ý trước lời giải trước nhãn, nên ý "Đúng" có số liệu cụ
  thể (giá trị biểu thức, giá trị nhỏ nhất) được viết trước khi tính; không viết được ý đúng, model làm theo lệnh gán nhãn.
  Code chỉ so nhãn với mẫu (đo mức tuân thủ, nên hai câu đã gán đúng mẫu không bị cảnh báo), không so nhãn với lời giải; chỉ
  Solver bắt được. Sai tập trung ở ý gán T (Solver khác 11/51 = 21,6%, so với 4/53 = 7,5% ở ý gán F) và ở đề có biểu thức ba hạng
  tử (8/15 lần sinh sai, so với 3/12 ở đề hai hạng tử).
- Cách phát hiện: đo thật 26/10 bằng `measure_tf.sql` (ngoài repo) và đọc tay từng lần sinh.
- Cách sửa: (a) prompt `matrix-tf-v3-draft`: mẫu là MỤC TIÊU ("Mẫu nhãn mục tiêu"), nhãn phải theo đúng/sai thật, muốn ý Sai thì
  đổi hệ số, dấu hoặc kết luận, được lệch mẫu khi không viết được ý hợp lý, cấm gán nhãn trái lời giải và nhắc tới mẫu trong
  lời giải; `fromNote` nhận cả tiền tố cũ, cảnh báo đổi thành `label: khác mẫu mục tiêu`. (b) chờ làm: kiểm tra định dạng không
  tốn LLM bắt lời giải tự thú nhận (→ `format_failed`, sinh lại).
- Chống tái phát: `TfLabelPatternTest` (`dongPromptNoiMauLaMucTieuVaNhanPhaiTheoDungSaiThat`, `docLaiMauTuGhiChuMoiVaCu`),
  `MatrixPromptDraftV0Test.phienBanVaThongSoOLuuDuoc`, `ExamBuildTest`. Hiệu quả thật chỉ đo được bằng lần đo sau (cùng cỡ mẫu).
- Bài học: một ràng buộc ép trên đầu ra (nhãn) phải đi kèm cách thỏa nó mà vẫn đúng, và phải có lối thoát khi không thỏa được;
  kiểm tra "tuân thủ mẫu" không thay được kiểm tra "đúng sự thật". Đo đọc tay phát hiện nhiều hơn regex (3 so với 6).
- Trạng thái: Đang sửa ((a) đã làm; (b) và đo lại chưa). Chưa xử lý: ý "Đúng" có số liệu cụ thể vẫn được viết trước khi tính
  (thứ tự JSON); chỉ xem xét nếu số đo sau vẫn tệ.

## QC

### L-28 Câu được `qc_passed` dù Solver lệch hoặc lỗi (bổ sung từ repo)
- Nhóm: QC
- Triệu chứng: pipeline QC chỉ có format → answer_check, không có dedup; `answer_check` sai hoặc lỗi vẫn cho
  `QC_PASSED` (phát hiện F-7 trong audit 21/10).
- Nguyên nhân gốc: chưa có định nghĩa chính xác của `qc_passed` (câu hỏi Q-2) khi viết pipeline.
- Cách phát hiện: audit repository ngày 21/10 (`docs/ai-development-rules.md` F-7, Q-2).
- Cách sửa: chốt Q-2 (21/10): `QC_PASSED` khi và chỉ khi format hợp lệ, dedup đã chạy và không trùng, và `answer_check`
  chạy thành công và nhất quán. Bảng quyết định tách ra `QcStatusDecider`; Solver dùng `responseSchema`. Commit `55588cc`.
- Chống tái phát: `QcStatusDeciderTest` (bảng quyết định đầy đủ), `QcOrchestratorStatusTest`.
- Bài học: trạng thái "đạt" phải có định nghĩa viết ra trước khi code, và cài thành một bảng quyết định duy nhất.
- Trạng thái: Đã sửa.

### L-29 Câu Đúng/Sai gần trùng không bị bắt
- Nhóm: QC
- Triệu chứng: câu TF thứ 8 trong dữ liệu thật gần trùng câu 2 và câu 4 (cùng đề dẫn, các ý chỉ đổi thứ tự hoặc đổi
  chút số liệu), nhưng dedup không bắt.
- Nguyên nhân gốc: dedup ban đầu so cả câu như một khối văn bản; câu chỉ dẫn chung ("Xét tính đúng sai của các khẳng
  định sau") làm các câu trông giống hoặc khác nhau một cách giả.
- Cách phát hiện: đọc dữ liệu thật.
- Cách sửa: dedup TF so đề dẫn và 4 ý đã chuẩn hóa, ghép ý không phụ thuộc thứ tự, có chốt chặn số liệu và phủ định
  (commit `dfbc120`). Bỏ câu chỉ dẫn trước khi so đề dẫn (commit `ed8db66`).
- Chống tái phát: `DedupCheckerTrueFalseTest` (gồm `duLieuThat_cau8TrungCau2TheoY`,
  `duLieuThat_cau8TrungCau4SauKhiBoCauChiDan`, `boCauChiDanTruocKhiSoDeDan`).
- Bài học: so trùng phải theo cấu trúc của dạng câu, không coi mọi câu là một đoạn văn.
- Trạng thái: Đã sửa.

### L-30 Đề tự mâu thuẫn (Solver bắt được)
- Nhóm: QC
- Triệu chứng: có câu do LLM sinh mà đề tự mâu thuẫn hoặc đáp án không khớp lời giải; Solver giải độc lập cho kết quả
  khác. Ví dụ cụ thể: nguồn ghi chép ngoài repo.
- Nguyên nhân gốc: LLM không tự kiểm tra tính nhất quán của câu mình sinh.
- Cách phát hiện: bước `answer_check` (Solver), có trong pipeline từ trước.
- Cách sửa: trong luồng tạo đề, câu lệch Solver được sinh lại 1 lần; vẫn lệch thì dùng câu đó, kèm cờ "cần xem kỹ"
  (`solver_inconsistent`) để giáo viên thấy (`ExamBuildService`, `MAX_SOLVER_RETRIES = 1`). Commit `ed8db66`.
- Chống tái phát: `ExamBuildTest.lechSolverThuLaiMotLanRoiDungCauKemCo`, `TfSolverCheckerTest`, `SaSolverCheckerTest`.
- Bài học: một LLM thứ hai giải độc lập là lớp kiểm tra rẻ và hữu ích, nhưng chỉ phát hiện được chứ không ngăn được
  lỗi.
- Trạng thái: Đã sửa (đã có cơ chế phát hiện và gắn cờ; LLM vẫn có thể sinh câu mâu thuẫn).

### L-31 Cảnh báo format bị mất ở luồng job
- Nhóm: QC
- Triệu chứng: cảnh báo không chặn của bước format (ví dụ "label: 4 ý cùng nhãn T", "đã bỏ tiền tố a)") chỉ có trong
  response của generate-sync; câu sinh qua job không còn cảnh báo nào.
- Nguyên nhân gốc: cảnh báo chỉ nằm trong đối tượng kết quả trong bộ nhớ, không được lưu (header V5).
- Cách phát hiện: so kết quả giữa hai luồng sinh câu (chưa xác minh chi tiết).
- Cách sửa: migration `V5__qc_format_warnings.sql` thêm `quality_check_result.warnings` (JSONB mảng chuỗi); cả sync và
  job đều ghi. Commit `dfbc120`.
- Chống tái phát: `V5UpgradeFromV4Test`; các test luồng TF/SA kiểm cột `warnings`.
- Bài học: thông tin QC nào cần xem lại sau này thì phải lưu vào DB, không chỉ trả trong response.
- Trạng thái: Đã sửa.

### L-32 needsReview bật bởi cảnh báo tự sửa escape (báo động giả)
- Nhóm: QC
- Triệu chứng: sau khi thêm chế độ sửa escape (L-20), nhiều câu đúng bị gắn cờ "cần xem kỹ" chỉ vì có cảnh báo
  `json: đã sửa … escape LaTeX`.
- Nguyên nhân gốc: mọi cảnh báo format đều được tính là lý do "cần xem kỹ", trong khi sửa escape không đổi nghĩa công
  thức.
- Cách phát hiện: người dùng đọc đề thật.
- Cách sửa: `QcSummaryService` bỏ qua cảnh báo bắt đầu bằng `json: đã sửa ` (hằng
  `LlmOutputRepair.ESCAPE_WARNING_PREFIX`). Cảnh báo vẫn lưu; "đã tự đóng $" vẫn bật cờ. Commit `06e9441`.
- Chống tái phát: `QcSummaryReviewReasonsTest`, `ExamBuildTest.canhBaoSuaEscapeKhongBatCanXemKyConTuDongDongDoLaVanBat`.
- Bài học: cờ cảnh báo cho người dùng phải phân biệt việc hệ thống sửa an toàn với việc có thể sai nghĩa, không thì
  người dùng sẽ bỏ qua mọi cờ.
- Trạng thái: Đã sửa.

### L-40 Chạy lại QC làm mất cảnh báo từ bước giải mã (bổ sung 24/10)
- Nhóm: QC
- Triệu chứng: chạy lại QC cho một câu `generated` (`QcRerunService`, quy tắc QC-RERUN) ghi đè cột
  `quality_check_result.warnings` của dòng `format` mà không còn các cảnh báo từ bước giải mã, ví dụ
  `option_a: đã tự đóng $` hay `json: đã sửa 1 escape LaTeX (\circ)`. Cảnh báo "đã tự đóng $" đang bật cờ "cần xem kỹ"
  (L-32), nên sau khi chạy lại, cờ có thể biến mất dù câu đã bị hệ thống tự sửa. Chưa gặp trên dữ liệu thật (chưa có
  endpoint gọi `QcRerunService`); suy luận từ đọc code.
- Nguyên nhân gốc: `QcRerunService` dựng đầu vào QC từ câu đã lưu (từ W1-2 qua `StoredQcInput`), không có
  `parseWarnings`, vì cảnh báo giải mã chỉ có lúc giải mã phản hồi LLM; `QcOrchestrator.evaluateAndSave` luôn ghi đè dòng
  `format` theo kết quả mới.
- Cách phát hiện: audit thiết kế W1-2 (giáo viên sửa câu) ngày 24/10, khi xem lại cách dựng đầu vào QC từ câu đã lưu.
- Cách sửa: chưa có. Hướng có thể: khi chạy lại, giữ các cảnh báo giải mã của dòng `format` cũ (nhận diện theo tiền tố
  `json: đã sửa ` và hậu tố `: đã tự đóng $`) và gộp vào kết quả mới. Luồng sửa câu của giáo viên (W1-2) không bị ảnh
  hưởng: cảnh báo tự đóng `$` được truyền vào QC ngay trong request.
- Chống tái phát: chưa có.
- Bài học: dữ liệu chỉ có ở một bước (cảnh báo khi giải mã) phải được giữ lại khi bước sau ghi đè kết quả.
- Trạng thái: Chưa sửa (mới ghi nhận).

### L-41 Dedup bỏ sót câu cùng đề ở ô khác chưa qua QC và văn bản câu ngân hàng đã sửa (bổ sung 24/10, W1-3)
- Nhóm: QC
- Triệu chứng: trong cùng một đề, câu sinh cho ô B (hoặc bản giáo viên sửa) có thể trùng hệt một câu đã nằm ở ô A mà không
  bị bắt. Hai trường hợp tái hiện được bằng test: (1) câu ở ô A đang là câu lệch Solver (trạng thái `generated`, được dùng
  kèm cờ); (2) câu ở ô A lấy từ ngân hàng và giáo viên đã sửa văn bản lúc duyệt (văn bản trong `bank_item` khác câu nguồn).
  Test trước khi sửa: `sinhTrungCauODaKhacChuaQuaSolverThiBiDedup ==> expected: <duplicate> but was: <qc_passed>`.
- Nguyên nhân gốc: dedup chỉ so với câu `qc_passed`/`in_bank` của các run production cùng môn và câu cùng run. Mỗi ô ma
  trận là một run riêng, câu `generated` không phải ứng viên, và `DedupService` đọc nội dung từ `generated_question` nên
  không thấy văn bản đã sửa trong `bank_item` (README 7.2 đã ghi hạn chế này).
- Cách phát hiện: audit thiết kế W1-3 (đọc `QualityCheckResultRepository.find*DedupCandidateRows` và `DedupScopeResolver`).
- Cách sửa: `DedupScope.extraCandidates` mang nội dung các câu đang nằm ở chỗ khác cùng đề; module `exam` cung cấp qua
  interface `SlotDedupContext` (`ExamSlotSiblings`), `DedupScopeResolver.resolveForSlot` dùng cho job có `exam_slot_id`
  và `QuestionEditService.createVersion` dùng cho bản giáo viên sửa; `DedupService` so thêm cùng dạng câu.
- Chống tái phát: `ExamInExamDedupTest` (7 test, gồm bài đối chứng chứng minh văn bản dựng tay khớp đầu ra LLM giả).
- Bài học: "trùng" phải được định nghĩa theo tập câu thực sự đang cùng xuất hiện (cùng đề), không chỉ theo trạng thái QC;
  và dữ liệu đối chiếu phải là văn bản người dùng thấy, không phải bản ghi nguồn.
- Trạng thái: Đã sửa. Hạn chế còn lại: hai job của cùng đề chạy đồng thời có thể cùng qua dedup.

### L-42 Solver lỗi với câu LLM gốc không để lại dấu vết, lẫn với "chưa chạy" (bổ sung 24/10, W1-3)
- Nhóm: QC
- Triệu chứng: câu mà Solver gặp lỗi (hết thời gian chờ, phản hồi hỏng) trông giống câu Solver chưa chạy: không có dòng
  `answer_check`, lý do chỉ có trong `solverReasoning` của response sinh đồng bộ nên mất với câu sinh qua job. Giáo viên và
  nhóm không biết Solver "lỗi" hay "chưa chạy"; lời giải thích của Solver cũng không được lưu nên không giải thích được vì
  sao một câu bị coi là lệch.
- Nguyên nhân gốc: `chk_qcr_judge_only_answer_check` bắt dòng `answer_check` phải có kết quả hợp lệ nên lỗi không ghi được
  vào `quality_check_result`; `SolverVerdict.reasoning()` chỉ nằm trong bộ nhớ.
- Cách phát hiện: audit thiết kế W1-3 (yêu cầu vết QC phân biệt chưa chạy / lỗi / lệch).
- Cách sửa: migration V14: `quality_check_result.solver_reasoning` (lời giải thích, tối đa 4000 ký tự, chỉ dòng
  `answer_check`) và `generated_question.solver_error` (lý do lỗi, tối đa 1000 ký tự, ghi bởi `finishGeneratedJob` khi
  `answerCheckStatus = ERROR`); bản giáo viên sửa dùng `generation_job.last_error` của job `answer_check`.
- Chống tái phát: `ExamQcTraceTest.solverLoiVoiCauLlmGocDuocGhiLaiVaPhanBietVoiChuaChay`, `banSuaSolverThatBaiGhiLoiTrongJob`,
  `loiGiaiThichDaiBiCatVaBanChiSuaLoiGiaiChepLoiGiaiThichCuaSolver`, `V14UpgradeFromV13Test`.
- Bài học: một trạng thái "không có dữ liệu" phải phân biệt được nguyên nhân (chưa chạy, lỗi, bị thay thế) ngay lúc ghi,
  không suy ngược lại sau.
- Trạng thái: Đã sửa cho đường job sinh câu cho chỗ trong đề; sinh đồng bộ và chạy lại QC chưa ghi `solver_error`. Việc
  còn nợ (đã duyệt 24/10): khi làm công cụ thí nghiệm Solver phải ghi cả hai đường đó (xem `docs/contract/enums.md`, mục
  "Vết QC theo ô đề và ngân hàng có lọc").

### L-45 Trích dẫn đúng chữ của judge "bám tài liệu" bị coi là không tìm thấy vì bỏ cặp `$` (bổ sung 26/10, W2-3)
- Nhóm: QC
- Triệu chứng: đo thật đề 4 (Gemini, 5 câu có tài liệu): 2 trên 6 câu bị judge kết luận SUPPORTED nhưng hệ thống hạ xuống
  UNKNOWN với lý do `evidence_not_found:`. Trích dẫn lưu ở DB là `(A - B)^2 = A^2 - 2AB + B^2.` còn tài liệu có
  `$(A - B)^2 = A^2 - 2AB + B^2$.`. Một lần judge còn viết `reason` không dấu tiếng Việt.
- Nguyên nhân gốc: judge chép đúng chữ nhưng bỏ cặp `$` bao công thức. So khớp ban đầu chỉ bỏ khoảng trắng và phân biệt hoa
  thường, nên `b^2.` không là chuỗi con của `b^2$.`; prompt cũng chưa yêu cầu chép từng ký tự.
- Cách phát hiện: so `evidence_quote` với file .txt tự soạn sau lần đo thật (test dùng LLM giả luôn trích đúng nên không lộ).
- Cách sửa: chuẩn hóa đối xứng cho cả quote và tài liệu (bỏ `$`, `\(`, `\)`, `\[`, `\]`, `\left`, `\right`, `\,`, `\;`, `\!`;
  gộp dấu trừ/gạch dài và dấu nháy cong; bỏ khoảng trắng đặc biệt và ký tự vô hình; `^{x}` → `^x`, `_{x}` → `_x` chỉ khi
  trong ngoặc đúng một chữ hoặc số); quote còn bị cắt dấu câu hai đầu; độ dài tối thiểu tính sau chuẩn hóa. Không bỏ dấu
  tiếng Việt, không so mờ. Prompt thêm yêu cầu sao chép từng ký tự, UNKNOWN khi không chép được, `reason` có dấu. Commit
  `fix(qc): tolerate formula delimiters when verifying grounding evidence quotes`.
- Chống tái phát: `GroundingCheckerTest` (`trichDanBoCapDauDoLaVanKhopCaDoThatCau75Va76`,
  `ngoacNhonNhieuKyTuKhongDuocRutGonVaMuKhacThiKhongKhop`, `chuanHoaKhongChoLotTrichDanBiaDoiHeSoDauHoacBoDauTiengViet`,
  `trichDanChiGomKyHieuHoacNganSauChuanHoaKhongDuLamBangChung`).
- Bài học: kiểm chứng trích dẫn phải dung sai về cách viết công thức nhưng không nới tới mức bỏ dấu hay so mờ; đo thật sớm
  với model thật vì LLM giả không lộ lỗi kiểu này.
- Trạng thái: Đã sửa. Hai câu của đề đo vẫn ghi UNKNOWN trong DB (không chạy lại); đo đề mới để kiểm.

## Hàng đợi/đồng thời

### L-33 Đổi câu ở chỗ TF vi phạm `chk_es_ready`
- Nhóm: Hàng đợi/đồng thời
- Triệu chứng: Đổi câu ở một chỗ TF lỗi `ERROR: new row for relation "exam_slot" violates check constraint
  "chk_es_ready"`.
- Nguyên nhân gốc: thao tác Đổi xóa câu khỏi chỗ trong khi trạng thái còn `ready`, rồi mới chuyển sang `generating`.
  Trong lúc đó, hàm tìm mẫu nhãn của câu TF gần nhất (L-25) truy vấn bảng `exam_slot`, khiến Hibernate tự ghi trạng thái
  dở dang xuống DB trước khi truy vấn. Lỗi do chính phần mẫu nhãn mới gây ra, chỉ xảy ra ở chỗ TF.
- Cách phát hiện: test mới `ExamBuildTest.doiCauTfKemYeuCauGiuMauNhanHeThong` đỏ khi làm tính năng "Đổi câu kèm yêu cầu".
  Test cũ chỉ Đổi chỗ MCQ nên không bắt được.
- Cách sửa: `ExamBuildService` chuyển chỗ sang `generating` trước mọi truy vấn. Bản sửa nằm trong commit `06e9441`, cùng
  commit với phần mẫu nhãn, nên lỗi chưa từng nằm trong một commit nào.
- Chống tái phát: `ExamBuildTest.doiCauTfKemYeuCauGiuMauNhanHeThong`; ràng buộc CHECK của V7 tự chặn trạng thái sai ở DB.
- Bài học: với ORM, một truy vấn đọc cũng có thể kích hoạt ghi; đặt trạng thái hợp lệ trước khi truy vấn.
- Trạng thái: Đã sửa.

### L-34 Job kẹt RUNNING
- Nhóm: Hàng đợi/đồng thời
- Triệu chứng: nếu backend tắt hoặc khởi động lại khi job đang `running`, hoặc phần lưu câu/QC sau lời gọi Gemini ném
  lỗi, job ở `running` mãi; chỗ tương ứng trong đề đứng ở `generating` mãi.
- Nguyên nhân gốc: không có cơ chế trả job quá hạn về hàng đợi; khối bắt lỗi trong `GenerationJobPoller` chỉ bọc lời gọi
  Gemini.
- Cách phát hiện: audit 21/10 đã ghi (F-9: "không xử lý job kẹt `running`"); báo cáo kiểm tra tải ngày 23/10 (đọc code)
  xếp đây là điểm yếu nghiêm trọng.
- Cách sửa (commit `5fefe87`):
  - V10 thêm `generation_job.started_at`.
  - `StaleJobRecovery`, với lịch `StaleJobScheduler` chạy lúc khởi động và mỗi 2 phút khi worker bật: trả job `running`
    quá `WORKER_STALE_AFTER_SECONDS` (mặc định 300) về `pending`, ghi `last_error = 'stale: reset'`, hoàn lại lượt
    `attempt`.
  - Lỗi sau lời gọi LLM đi cùng đường thử lại hoặc `failed`.
- Chống tái phát: `GenerationJobQueueTest` (job cũ được trả về, job mới không bị đụng, lỗi lưu câu do ký tự NUL, lỗi QC),
  `StaleJobSchedulerTest`; quy tắc ghi ở `docs/contract/enums.md`.
- Bài học: mọi trạng thái "đang chạy" lưu trong DB phải có cách tự hết hạn.
- Trạng thái: Đã sửa. Còn một điểm để đợt B: nếu QC lỗi sau khi câu đã lưu, job thử lại và câu đã lưu nằm lại ở
  `generated` mà không gắn với chỗ nào.

### L-35 Nhận job không khóa (hai worker nhận trùng)
- Nhóm: Hàng đợi/đồng thời
- Triệu chứng: hai luồng cùng nhận job thì gần như mọi job bị nhận hai lần. Test tái hiện trước khi sửa cho ra
  `[1807, 1807, 1808, 1808, 1809, 1809, …]`. Hệ quả: gọi Gemini hai lần và lưu hai câu cho một job.
- Nguyên nhân gốc: câu JPQL tải mọi job `pending` đến hạn rồi lấy phần tử đầu, không khóa dòng, không `@Version`.
- Cách phát hiện: audit 21/10 (F-9: "Claim job không khóa hàng (không `SKIP LOCKED`, trái comment V2 dòng 27)"); báo
  cáo kiểm tra tải 23/10; test đồng thời hai luồng trên `datn_mcq_test`.
- Cách sửa: native query `... ORDER BY next_run_at, id LIMIT 1 FOR UPDATE SKIP LOCKED`, đổi sang `running` trong cùng
  transaction; áp cho cả scheduler và `POST /api/generation/jobs/process-next`. Commit `5fefe87`.
- Chống tái phát: `GenerationJobQueueTest.haiLuongNhanJobDongThoiKhongNhanTrung`,
  `GenerationJobQueueTest.jobDangDuocLuongKhacNhanThiBoQuaKhongCho`.
- Bài học: hàng đợi trên PostgreSQL nên dùng `FOR UPDATE SKIP LOCKED`; lỗi đồng thời cần test nhiều luồng mới thấy.
- Trạng thái: Đã sửa. Ghi chú: lỗi được ghi trong audit từ 21/10 nhưng tới 23/10 mới sửa.

### L-36 Lỗi Gemini không được phân loại khi thử lại
- Nhóm: Hàng đợi/đồng thời
- Triệu chứng: mọi lỗi gọi Gemini (429 theo phút, hết quota ngày, 5xx, timeout, lỗi mạng, 400/403) đều được thử lại
  giống nhau: tối đa 3 lần, chờ 15 rồi 30 giây; không đọc `Retry-After`. Khi Gemini sập lâu, lượt thử bị tiêu hết
  nhanh và chỗ trong đề thành `failed`.
- Nguyên nhân gốc: `LlmClient.errorForStatus` chỉ tách 429 và 503 để trả mã cho API; `GenerationJobProcessor.recordJobFailure`
  không xét loại lỗi.
- Cách phát hiện: báo cáo kiểm tra tải ngày 23/10 (đọc code, không chạy). Báo cáo đó nằm ngoài repo; hiện trạng kiểm
  được trong `LlmClient.java` và `GenerationJobProcessor.java`.
- Cách sửa: chưa có.
- Chống tái phát: chưa có.
- Bài học: chưa rút ra (chưa sửa).
- Trạng thái: Chưa sửa (dự kiến các đợt sau demo, chưa lên lịch).

### L-37 Lệch đồng hồ JVM và container DB làm test chập chờn
- Nhóm: Hàng đợi/đồng thời
- Triệu chứng: sau khi đổi câu nhận job sang `next_run_at <= now()`, hai test cũ đỏ chập chờn:
  `ShortAnswerFlowTest.jobSaQuaHangDoi:492 expected: <true> but was: <false>` và
  `TrueFalseFlowTest.jobTfQuaHangDoiVaChayLaiQc:553 expected: <generated> but was: <null>`. Chạy riêng lại thì có lúc qua.
- Nguyên nhân gốc: `next_run_at` được ghi bằng giờ JVM (Windows), còn `now()` là giờ của PostgreSQL trong container
  (WSL2). Hai đồng hồ lệch nhau vài trăm mili giây và độ lệch thay đổi: lúc đo, DB nhanh hơn 0,25–0,6 giây; lúc test đỏ
  thì có lẽ chậm hơn (chưa xác minh trực tiếp tại thời điểm đỏ). Job vừa xếp hàng vì thế bị coi là "chưa đến hạn". Múi
  giờ đã được kiểm và không phải nguyên nhân (phiên JDBC và JVM cùng `Asia/Bangkok`).
- Cách phát hiện: chạy toàn bộ test, đo giờ JVM và `clock_timestamp()` của DB, in trạng thái job ngay trước assertion.
- Cách sửa: câu nhận job và câu reset job quá hạn dùng tham số `:now` lấy từ giờ JVM, cùng nguồn với giờ ghi
  `next_run_at` (`GenerationJobRepository`). Lệch so với yêu cầu ban đầu (`now()`), đã ghi trong Javadoc và báo cáo.
  Commit `5fefe87`.
- Chống tái phát: hai test nêu trên; `GenerationJobQueueTest`.
- Bài học: một mốc thời gian chỉ nên do một đồng hồ sinh ra và so sánh.
- Trạng thái: Đã sửa.

### L-39 Job sinh câu `done` trước khi QC xong, đối soát sinh lại câu tốt (bổ sung 24/10)
- Nhóm: Hàng đợi/đồng thời
- Triệu chứng: giáo viên mở tiến độ hoặc xem đề đúng lúc worker đang chạy dedup/Solver cho câu vừa sinh thì chỗ đó bị
  xếp hàng sinh lại: `exam_slot.attempts` từ 1 lên 2, `solver_retries = 1`, câu (thực ra sẽ `qc_passed`) bị coi như lệch
  Solver và chỉ còn làm câu dự phòng. Tốn thêm một lời gọi Gemini và có thể mất câu tốt. Test tái hiện trước khi sửa:
  `GenerationJobDoneAfterQcTest ... không xếp hàng sinh lại ==> expected: <1> but was: <2>`; trạng thái job thấy được
  trong lúc Solver chạy là `done`.
- Nguyên nhân gốc: `GenerationJobProcessor.save*FromJob` lưu câu và đánh dấu job `done` trong cùng transaction, TRƯỚC khi
  `GenerationJobPoller` chạy dedup và Solver (gọi LLM, vài giây); trạng thái QC cuối của câu chỉ được ghi ở transaction
  sau. Trong khoảng đó câu ở `generated`, đúng là trạng thái mà `ExamBuildService.reconcileSlot` hiểu là "lệch Solver
  hoặc chưa kết luận" nên sinh lại. Audit 21/10 đã ghi ý này (F-9: "job đánh dấu done trước khi QC chạy").
- Cách phát hiện: audit thiết kế W1-2 ngày 24/10 (đọc code), rồi test hai bước có móc trong Solver giả gọi API tiến độ
  đúng lúc Solver đang chạy.
- Cách sửa: lưu câu thì chỉ ghi `question_id` và số liệu lời gọi LLM vào job, job vẫn `running`;
  `GenerationJobProcessor.finishGeneratedJob` ghi trạng thái QC cuối của câu và đánh dấu job `done` trong cùng một
  transaction ngắn sau khi QC xong. Đối soát thấy job `running` thì chờ như trước. Job `answer_check` của W1-2 cũng chỉ
  `done` cùng transaction với kết quả Solver.
- Chống tái phát: `GenerationJobDoneAfterQcTest.xemTienDoTrongLucSolverDangChayKhongSinhLaiCauTot`.
- Hạn chế còn lại: nếu backend chết sau khi lưu câu mà trước khi QC xong, job ở `running` tới khi `StaleJobRecovery` trả về
  `pending` và sinh câu mới; câu đã lưu nằm lại ở `generated`, không gắn chỗ nào (như ghi chú còn lại của L-34).
- Bài học: một trạng thái "xong" chỉ được ghi cùng lúc với kết quả cuối mà người khác sẽ đọc; trạng thái trung gian
  không được nhìn giống trạng thái kết luận.
- Trạng thái: Đã sửa.

### L-44 Khóa ghi hàng ma trận không chờ tạo đề đang dở (bổ sung 25/10, W1-4)
- Nhóm: Hàng đợi/đồng thời
- Triệu chứng: xóa ma trận đúng lúc một lần tạo đề đã ghi dòng `exam` nhưng chưa commit không chờ và không thấy đề đó:
  lần xóa đếm được 0 đề, rồi `DELETE` ma trận bị khóa ngoại chặn và trả `FOREIGN_KEY_CONFLICT` (409 do K-5) thay vì
  `MATRIX_IN_USE` như thiết kế. Nếu chưa có K-5 thì là 500. Test trước khi sửa:
  `MatrixManageTest.xoaMaTranLucDangTaoDeChoXongRoiTra409 ==> {"code":"FOREIGN_KEY_CONFLICT"…} expected: <true>`.
- Nguyên nhân gốc: `@Lock(PESSIMISTIC_WRITE)` của Hibernate trên PostgreSQL sinh `FOR NO KEY UPDATE`, loại khóa này không
  xung đột với `FOR KEY SHARE` mà khóa ngoại `exam.matrix_id` giữ trên hàng ma trận khi chèn đề. Kết luận suy ra từ kết quả
  test (hành vi khớp với tài liệu PostgreSQL về ma trận xung đột khóa hàng); chưa đối chiếu câu SQL Hibernate sinh ra.
- Cách phát hiện: test hai luồng dựng sẵn tình huống tranh chấp (một kết nối thô giữ giao dịch dở, một yêu cầu HTTP bị chặn
  chờ khóa).
- Cách sửa: `ExamMatrixRepository.lockRowForUpdate` là `SELECT ... FOR UPDATE` native (xung đột với `FOR KEY SHARE`), đọc
  entity sau khi có khóa; tạo đề khóa chia sẻ (`PESSIMISTIC_READ`) để gặp ma trận vừa xóa thì 404.
- Chống tái phát: `MatrixManageTest.xoaMaTranLucDangTaoDeChoXongRoiTra409`, `taoDeLucDangXoaMaTranChoXongRoiTra404`;
  K-5 trong `ApiExceptionHandler` (409 `FOREIGN_KEY_CONFLICT` / `UNIQUE_CONFLICT`) làm lưới an toàn cho các tranh chấp khác,
  `ApiExceptionHandlerDataIntegrityTest`, `MatrixManageTest.loiRangBuocThatCuaPostgresDuocAnhXaDungMaHttp`.
- Bài học: tên chế độ khóa của ORM không nói loại khóa thật ở CSDL; khóa để chặn tranh chấp phải được kiểm bằng test hai
  luồng, không chỉ đọc code.
- Trạng thái: Đã sửa.

## Xuất file

### L-38 File Word xuất ra mở ở Compatibility Mode
- Nhóm: Xuất file
- Triệu chứng: Word mở file .docx xuất từ pandoc ở Compatibility Mode, nên lệnh Convert Equations của MathType không
  chạy.
- Nguyên nhân gốc: `word/settings.xml` của file pandoc sinh ra không khai báo `compatSetting compatibilityMode`.
- Cách phát hiện: người dùng mở file thật bằng Word và MathType.
- Cách sửa: dùng `--reference-doc` với `back_end/src/main/resources/export/reference.docx`. File này tạo từ
  `reference.docx` mặc định của pandoc, chèn `<w:compat><w:compatSetting w:name="compatibilityMode" … w:val="15"/></w:compat>`
  vào `settings.xml`, và đặt font mặc định cùng kiểu Normal là Times New Roman 13. Mỗi lần xuất, file mẫu được chép ra
  thư mục tạm (`PandocRunner`). Commit `5fefe87`.
- Chống tái phát: `PandocRunnerTest.fileMauCoCompatibilityMode15VaFontTimesNewRoman` (luôn chạy),
  `PandocRunnerTest.pandocThatSinhDocxCoEquationGocCuaWord` (chạy khi máy có pandoc). Cách tạo file mẫu ghi ở
  `docs/api/README.md` mục 7.6.
- Bài học: xuất file cho phần mềm khác dùng tiếp thì phải kiểm cả siêu dữ liệu của file, không chỉ nội dung.
- Trạng thái: Đã sửa. Chưa kiểm bằng Word và MathType thật sau bản sửa; mới kiểm XML bên trong file. Tiêu đề (Heading)
  vẫn dùng font theme của pandoc.

---

## Bài học chung

- Đầu ra LLM phải được coi là dữ liệu không tin cậy: parser chặt, quy tắc sửa hẹp, và mỗi lần tự sửa đều để lại cảnh
  báo (L-17, L-18, L-20, L-21).
- Thay đổi nào có thể làm lệch số liệu thực nghiệm (sửa đầu ra, schema, prompt) phải tách theo `purpose`, và đánh phiên
  bản prompt (L-20, L-26).
- Dữ liệu đọc từ đề thật phát hiện nhiều lỗi chất lượng mà test với LLM giả không thấy (L-23 đến L-27, L-29).
- Định nghĩa trạng thái và quy tắc nghiệp vụ phải viết ra và đối chiếu với nhau trước khi code (L-13, L-28).
- Một lỗi được ghi trong audit mà chưa sửa vẫn là rủi ro; nên có danh sách theo dõi và hạn sửa (L-34, L-35).
- Lỗi đồng thời và thời gian chỉ lộ ra khi test có nhiều luồng hoặc chạy nhiều lần (L-35, L-37).
- Test tích hợp phải bị chặn cứng khỏi DB thật và dịch vụ tính phí (L-12, L-10).
- Viết test đỏ trước giúp bắt được lỗi do chính thay đổi mới gây ra trước khi commit (L-33).

## Lỗi tiêu biểu nên trình bày khi bảo vệ

1. **L-20 – Escape LaTeX sai trong JSON.** Model viết lệnh LaTeX như `\circ`, `\frac` với một dấu gạch trong chuỗi
   JSON. Có lệnh làm giải mã lỗi ngay, có lệnh như `\frac` âm thầm thành ký tự điều khiển, làm hỏng hàng loạt câu hình
   học. Nhóm viết bộ sửa với quy tắc hẹp: chỉ nhân đôi dấu gạch khi chắc đó là lệnh LaTeX, mỗi lần sửa ghi cảnh báo, và
   chỉ áp cho dữ liệu dùng thật. Run thực nghiệm vẫn giải mã chặt, vì sửa đầu ra sẽ làm lệch kết quả đo (thí nghiệm so
   sánh các cách prompt đã bỏ từ 23/10, nhưng nguyên tắc tách vẫn giữ).
2. **L-25 – Mọi câu Đúng/Sai có nhãn T,T,F,F.** Để LLM tự chọn nhãn thì nó lặp một mẫu, khiến học sinh đoán được đáp án.
   Nhóm chuyển quyền quyết định phân bố nhãn về hệ thống: chọn ngẫu nhiên trong 14 mẫu có 1–3 ý Đúng, tránh trùng câu
   gần nhất, rồi đưa mẫu bắt buộc vào prompt. Nếu LLM không theo mẫu, câu không bị loại mà được gắn cờ để giáo viên
   xem; ngân hàng vẫn lưu nhãn thật khớp lời giải.
3. **L-28 – Định nghĩa "qua QC".** Ban đầu câu được coi là đạt dù bộ giải độc lập (Solver) cho kết quả lệch hoặc gặp
   lỗi. Nhóm chốt định nghĩa: đạt khi và chỉ khi đúng định dạng, đã kiểm trùng và không trùng, và Solver chạy thành công
   với kết quả khớp. Định nghĩa được cài thành một bảng quyết định duy nhất (`QcStatusDecider`) có test cho mọi tổ hợp.
4. **L-29 – Câu Đúng/Sai gần trùng.** So hai câu như hai đoạn văn không bắt được câu chỉ đảo thứ tự ý hoặc đổi chút số
   liệu, và bị câu chỉ dẫn chung làm nhiễu. Nhóm so theo cấu trúc của dạng câu: đề dẫn và từng ý, ghép ý không phụ thuộc
   thứ tự, có chốt chặn số liệu và phủ định, bỏ câu chỉ dẫn trước khi so. Test dùng chính các cặp câu trùng lấy từ dữ
   liệu thật.
5. **L-35 – Hai worker nhận trùng một job.** Câu lệnh nhận job cũ không khóa dòng, nên khi có hai luồng hoặc hai
   backend thì một job bị xử lý hai lần, tốn gấp đôi lời gọi LLM. Test hai luồng trên DB test tái hiện được việc gần như
   mọi job bị nhận hai lần. Nhóm chuyển sang `SELECT … FOR UPDATE SKIP LOCKED` và đổi trạng thái trong cùng transaction,
   kèm cơ chế trả job kẹt `running` về hàng đợi (L-34).
