# Quy tắc phát triển có AI hỗ trợ – v1

Phạm vi: toàn bộ repository `datn-mcq-llm` (backend Java 21/Spring Boot, PostgreSQL/Flyway, Gemini).
Áp dụng cho mọi agent/AI client và cho người dùng khi giao việc cho AI.

Ký hiệu:

- **M-x** – quy tắc **bắt buộc**, áp dụng ngay, không cần thay đổi kiến trúc.
- **K-x** – **khuyến nghị**, triển khai dần khi có thời gian; không phải điều kiện để tuân thủ v1.
- **Q-x** – **câu hỏi chờ chủ dự án quyết định**. AI không được tự chọn đáp án hay viết code giả định đáp án.
- **F-x** – phát hiện từ audit (mục 11), kèm mức độ chắc chắn.

Cách tham chiếu trong prompt: `Tuân thủ docs/ai-development-rules.md v1. Lượt này chỉ làm bước <...>.`

---

## 1. Nguồn chuẩn khi có mâu thuẫn

**M-1. Thứ tự ưu tiên** (nguồn trên thắng nguồn dưới):

| Ưu tiên | Nguồn | Chuẩn cho |
|---|---|---|
| 1 | Flyway `back_end/src/main/resources/db/migration/V1..Vn` (migration mới hơn thắng) | Cấu trúc bảng, CHECK, giá trị enum lưu DB |
| 2 | `docs/contract/enums.md` (mục 1–2 sau Flyway; mục 3–4 cho quy tắc đi kèm và JSON LLM) cùng các quyết định đã chốt (mục 10, hoặc `docs/decisions.md` nếu được tạo) | Ánh xạ cột–enum, quy tắc nghiệp vụ DB không tự chặn, hợp đồng JSON LLM |
| 3 | Test đã được chủ dự án duyệt | Đặc tả chạy được |
| 4 | `docs/architecture/backend-structure.md` | Module, hướng phụ thuộc, transaction với LLM |
| 5 | Code | Chỉ là hiện thực; **không** là bằng chứng về ý định |

**M-2. Không phải nguồn chuẩn:** `db/schema.sql`, `db/schema_final_erd.sql`, `README.md`, comment trong migration cũ đã bị migration sau thay thế (ví dụ comment `exam_question` ở V1 về `in_bank`), message/Javadoc trong code.

**M-3.** Khi phát hiện mâu thuẫn, AI phải trích **cả hai nguồn** (file + dòng) và nêu nguồn nào thắng theo M-1.

**M-4.** Nếu mâu thuẫn nằm ở **quy tắc nghiệp vụ** (không phải chi tiết schema) hoặc chạm một mục Q-x: **dừng, hỏi chủ dự án**. Không tự chọn, không viết test/code khóa chặt một phương án chưa chốt.

**M-5.** Đổi schema = viết migration mới `V{n+1}`; không sửa migration đã merge. Đổi enum thì cập nhật `enums.md` trong cùng commit. AI **không tự chạy** migration.

---

## 2. Bất biến nghiệp vụ

Chỉ ghi ở đây những điều đã có trong migration/`enums.md`. Phần chưa chốt nằm ở mục 10.

**M-6. Chấm mù – mọi `purpose`:** payload trả về cho người chấm (form, danh sách, phản hồi sau khi nộp) **không** chứa `correct_option` của LLM, **không** chứa `answer_correct`, kể cả trong JSON ẩn. Server tự tính và lưu `answer_correct`. (V1 dòng 136–138, `enums.md` mục 3.) **Ngoại lệ có chủ đích:** chế độ giáo viên khi tạo đề theo ma trận (`/api/exams/**`, production) không chấm mù, xem "Quyết định ngày 22/10/2026 – chế độ giáo viên" ở cuối tài liệu. Form chấm `/api/reviews/**` vẫn theo M-6.

**M-7. Chấm mù – `experiment`:** ngoài M-6, payload còn không chứa `llm_difficulty`, chiến lược sinh, và mọi kết quả QC (gồm `judge_answer`, similarity, `status`). Không được để lộ gián tiếp qua tham số lọc, thứ tự, hay giá trị giả.

**M-8. Provenance:** `purpose` của một lần chấm phải khớp `experiment_run.purpose` của câu hỏi. Giá trị client gửi lên không được quyết định `purpose`; nếu khác thì từ chối.

**M-9. `bank_item`:** chỉ được tạo/cập nhật từ duyệt thật (`production`), với quyết định `approved_as_is` hoặc `approved_with_edit`, và trong **cùng transaction** với `teacher_review` tương ứng (kể cả Giữ/Sửa ở chế độ giáo viên: `teacher_review.review_mode = 'assisted'`). Câu thuộc run `experiment` không bao giờ vào `bank_item`. Chỉ `bank_item` được đưa vào đề (`exam_question.bank_item_id`).

**M-10. Trạng thái QC:** chỉ chuyển `generated_question.status` theo kết quả pipeline QC hoặc theo duyệt production. Không ép trạng thái (ví dụ đặt thẳng `qc_passed`) ngoài pipeline. Định nghĩa đầy đủ của `qc_passed` đang chờ Q-2.

**M-11. Experiment:** mỗi cặp (`question_id`, `reviewer_id`) chỉ có một lần nộp; không cho ghi đè nhãn thực nghiệm.

**M-12.** Mỗi bất biến M-6 → M-11 khi được sửa/chạm vào phải có ít nhất một test bảo vệ (xem mục 4).

---

## 3. Inspect trước khi sửa

**M-13.** Trước khi sửa bất kỳ file code nào, AI nộp **báo cáo inspect** gồm:

1. Entry point (controller / scheduler / endpoint) → call chain đến repository hoặc `LlmClient`.
2. Caller của mọi method sẽ sửa, callee bị ảnh hưởng.
3. Test hiện có liên quan (tên file + tên test) và chúng đang kiểm tra gì.
4. Phạm vi ảnh hưởng: module, bảng, endpoint, payload JSON, migration.
5. Nguồn chuẩn liên quan (M-1) và mâu thuẫn tìm thấy.
6. Mục Q-x bị chạm (nếu có → dừng theo M-4).

**M-14.** Không sửa code dựa trên ghi nhớ hoặc báo cáo cũ nếu file có thể đã thay đổi; đọc lại file trước khi sửa.

---

## 4. Test-first và hồi quy

**M-15.** Mỗi yêu cầu nghiệp vụ → một test có thể thất bại trên code hiện tại. Chạy và ghi nhận **FAIL** trước khi sửa (nếu được phép chạy test; nếu không, ghi nhãn theo mục 7). Ngoại lệ: nếu issue là sửa lỗi không compile được (như F-1) hoặc chỉ sửa tài liệu/cấu hình tĩnh, ghi nhận lý do tại bước Test và chạy xác minh sau khi sửa.

**M-16.** Hợp đồng về payload (M-6, M-7) phải được kiểm ở mức HTTP thật: MockMvc (hoặc tương đương) gọi endpoint `/api/...`, assert trên JSON trả về. Test reflection hoặc `ObjectMapper` tự tạo chỉ là bổ sung, không thay thế.

**M-17.** Test cho luồng có ghi DB phải kiểm cả trạng thái sau lỗi (rollback): khi service ném lỗi, `teacher_review`, `generated_question.status`, `bank_item` đều không bị thay đổi dở dang.

**M-18.** Test không được gọi mạng/API tính phí: mock `LlmClient` hoặc dùng `rawLlmOutputOverride`.

**M-19. Hồi quy:** sau khi sửa, chạy lại test mới **và** toàn bộ test của các module bị chạm (theo báo cáo inspect) khi đã được phép theo M-27. Lưu ý: khi Q-7/F-3 chưa xử lý, không tự chạy `@SpringBootTest` chạm DB dev nếu chưa được chủ dự án cho phép rõ; phần chưa được phép chạy phải gắn nhãn theo M-30.

- **K-1.** Bổ sung test MockMvc cho các endpoint review hiện có.
- **K-2.** Test claim job song song, test nộp review song song (kỳ vọng 409, không phải 500).

---

## 5. Transaction, retry, idempotency, job bất đồng bộ

**M-20.** Không gọi LLM/embedding trong transaction DB (backend-structure quy tắc 5). Claim job → transaction ngắn; gọi LLM ngoài transaction; ghi kết quả → transaction ngắn.

**M-21.** Mọi thay đổi nhiều bảng thuộc một thao tác nghiệp vụ (review + status + bank) nằm trong một transaction ở service ngoài cùng.

**M-22.** Retry chỉ cho lỗi tạm thời (mạng, 429, 5xx), có giới hạn `max-attempts` và backoff. Lỗi parse/validation không retry vô hạn.

**M-23.** Khi sửa code job/worker: không làm tăng khả năng một job bị gọi LLM hai lần. Nếu thay đổi chạm vào cơ chế claim, phải nêu rõ cách đảm bảo claim nguyên tử.

**M-24.** Lưu ý `@Transactional` gọi nội bộ trong cùng class không có hiệu lực (Spring proxy); không dựa vào nó.

- **K-3.** Claim job bằng `FOR UPDATE SKIP LOCKED` hoặc `UPDATE ... WHERE status='pending'` (đúng comment V2).
- **K-4.** Cơ chế xử lý job kẹt ở `running`; đánh dấu job `done` sau khi QC hoàn tất.
- **K-5.** Map `DataIntegrityViolationException` sang 409 trong `ApiExceptionHandler`.

---

## 6. Secret, database, API tính phí

**M-25.** Không đọc, in, sao chép, commit giá trị trong `.env` hoặc biến môi trường chứa key. Được phép kiểm tra **tên** biến có tồn tại/rỗng hay không.

**M-26.** Không gọi Gemini, embedding hay dịch vụ tính phí khi chưa có cho phép **rõ ràng trong lượt hiện tại**. Lưu ý: `LlmClient` còn đọc biến hệ thống `GEMINI_API_KEY`/`LLM_API_KEY`, nên `.env` trống **không** đảm bảo không có cuộc gọi tính phí.

**M-27.** Các thao tác sau cần cho phép rõ ràng trước khi chạy, vì có thể chạy Flyway, ghi DB dev hoặc gọi LLM:
`mvn test` / `mvnw test` (hiện các `@SpringBootTest` dùng datasource từ `.env`), `spring-boot:run`, chạy app, gọi endpoint `/api/generation/*`, `/api/reviews/demo/seed-math`, bật `WORKER_ENABLED=true`, mọi lệnh `psql`/SQL.

**M-28.** Không xóa/sửa dữ liệu DB, không `flyway clean`/`repair`, không drop schema.

**M-29.** Không commit, push, checkout, reset, rebase, stash hay sửa lịch sử Git khi chưa được yêu cầu.

---

## 7. Báo cáo test

**M-30.** Mỗi test/nhóm test được báo cáo bằng **đúng một** nhãn:

| Nhãn | Điều kiện |
|---|---|
| `ĐÃ CHẠY – PASS` | Có lệnh, thời điểm, kết quả trong lượt này; chạy **sau** lần sửa file cuối cùng |
| `ĐÃ CHẠY – FAIL` | Như trên, kèm thông điệp lỗi chính |
| `ĐÃ VIẾT – CHƯA CHẠY` | Test có trong diff nhưng chưa chạy (ví dụ chưa được phép chạy theo M-27) |
| `KHÔNG THỂ CHẠY` | Kèm nguyên nhân cụ thể: lỗi compile, thiếu DB, shell lỗi, không được phép... |

**M-31.** Không dùng báo cáo cũ (`back_end/target/surefire-reports`, lời kể từ lượt trước) làm bằng chứng PASS hiện tại, trừ khi chứng minh được report mới hơn mọi file nguồn liên quan.

**M-32.** Lỗi compile được khẳng định là "đã xác nhận" chỉ khi có output compiler; nếu chỉ đọc code thì ghi "suy luận từ đọc code".

---

## 8. Review diff, phạm vi, điểm dừng

**M-33.** Trước khi báo hoàn tất: chạy `git status` và `git diff --stat`, đọc **toàn bộ** diff. Kiểm tra:
code trùng lặp/dán thừa, cân bằng ngoặc, import thừa, line ending và encoding UTF-8 giữ như file gốc, comment/Javadoc không liên quan không bị xóa, hướng phụ thuộc module, không lộ dữ liệu theo M-6/M-7.

**M-34.** Chỉ sửa trong phạm vi issue đã được duyệt ở bước plan. Phát hiện vấn đề khác → ghi vào mục "Phát hiện thêm" trong báo cáo, **không tự sửa**.

**M-35.** Thêm chiều phụ thuộc module mới hoặc đổi hợp đồng API → dừng, xin xác nhận (liên quan Q-8).

**M-36.** Kết thúc mỗi lượt bằng điểm dừng rõ ràng; không tự chuyển sang issue/bước tiếp theo khi chưa được xác nhận.

---

## 9. Shell lỗi

**M-37.** Một mục tiêu (ví dụ "đọc file X", "chạy lệnh Y") được thử **tối đa 2 lần**. Sau đó dừng, báo: lệnh, output/lỗi, giả thuyết nguyên nhân, đề xuất cho người dùng.

**M-38.** Không suy luận nội dung file từ output bị lỗi hiển thị (PowerShell thường hiển thị UTF-8 tiếng Việt thành ký tự lỗi). Không "sửa encoding" hay ghi đè file dựa trên output đó; dùng công cụ đọc file trực tiếp.

**M-39.** Không dùng lệnh shell để ghi/sửa file code (redirect, `Set-Content`, `sed`) khi đã có công cụ sửa file; không thao tác hàng loạt khi chưa thấy kết quả bước trước.

---

## 10. Câu hỏi chờ quyết định (Q-x)

Chưa phải hợp đồng. AI không tự chọn; khi chạm vào → dừng theo M-4. Khi chốt, ghi quyết định, ngày, người chốt vào đây (hoặc `docs/decisions.md`) và tăng version tài liệu.

| ID | Câu hỏi | Trạng thái |
|---|---|---|
| Q-1 | `explanation` có xuất hiện trong payload chấm production không? Nếu có thì khi nào (ví dụ chỉ sau khi giáo viên chọn đáp án)? Lưu ý `explanation` có thể làm lộ đáp án (liên quan M-6). Với experiment, hiện code đang giấu. | **Đã chốt (2026-10-21)**: Form chấm ban đầu tuyệt đối không trả `explanation`, đáp án LLM, `answerCorrect` hay kết quả QC cho cả `EXPERIMENT` và `PRODUCTION`. `SubmitReviewResponse` không trả `explanation` hay `answerCorrect`. Lời giải (`explanation`) chỉ được mở sau khi người chấm đã nộp đánh giá độc lập: với `PRODUCTION` mở sau khi đánh giá độc lập đã được lưu; với `EXPERIMENT` chỉ mở sau khi người chấm hoàn thành toàn bộ câu được giao. Phần mở lời giải này vẫn bị chặn bởi Q-6 (chưa có xác thực danh tính người chấm và chưa chốt việc dùng `label_assignment`). |
| Q-2 | `qc_passed` nghĩa chính xác là gì: chỉ qua format? Bắt buộc dedup? `answer_check` sai hoặc lỗi có chặn không? | **Đã chốt (2026-10-21)**: `QC_PASSED` yêu cầu đồng thời: format hợp lệ; dedup đã chạy và không phát hiện trùng; `answer_check` thực thi thành công và nhất quán. Logic pipeline hiện tại chưa theo định nghĩa này (xem F-7) và còn chờ triển khai. |
| Q-3 | Luồng tạo experiment run (endpoint/công cụ nào), và `purpose` lấy từ đâu; có cho phép giá trị mặc định không? | Chờ |
| Q-4a | Seed demo: giữ, xóa, hay chỉ bật theo profile; run seed mang `purpose` gì; có được ghi kết quả QC không phải từ pipeline thật không? | Chờ |
| Q-4b | Câu production đã `rejected` có được duyệt lại không? | Chờ |
| Q-4c | `bank_item` đã nằm trong đề có được sửa/gỡ không? | Chờ |
| Q-5 | `llm_difficulty` là kết quả của bước chấm độ khó riêng (theo comment V1) hay là độ khó mục tiêu khi sinh? | Chờ |
| Q-6 | Phạm vi xác thực người chấm; có bắt buộc `label_assignment` khi chấm experiment không? | Chờ |
| Q-7 | Cô lập DB test: DB riêng (ví dụ `datn_mcq_test` + profile `test`) hay Testcontainers? | **Đã triển khai (2026-10-21)**: dùng database riêng `datn_mcq_test` kèm guard chặn kết nối tới database khác và `BlockingTestLlmClient`. Smoke test và toàn bộ 33 test đã PASS ngày 2026-10-21 theo báo cáo surefire có sẵn (không chạy lại trong lượt cập nhật tài liệu này). |
| Q-8 | Ranh giới phụ thuộc module: có cho `bank → exam`, `review → experiment/catalog/generation.worker` không, hay phải đi qua service khác? | Chờ |

---

## 11. Phát hiện từ audit (snapshot 2026-10-21, so với checkpoint `9d845e6` + thay đổi chưa commit)

Đây là ảnh chụp tại thời điểm audit, không phải sự thật vĩnh viễn. Trước khi xử lý một mục, xác minh lại theo M-14.

### 11.1 Đã xác nhận bằng bằng chứng (đọc file / metadata)

| ID | Phát hiện | Bằng chứng |
|---|---|---|
| F-1 | `TeacherReviewService.java` chứa bản sao các method `validateSubmitRequest`, `collectLatexErrors`, `appendFieldErrors`, `fallback` nằm **sau** dấu `}` đóng class. Lỗi compile là **suy luận mạnh từ đọc code**, chưa có output compiler (M-32). | Dòng 409–479 |
| F-2 | Báo cáo "32 test pass" không chứng minh trạng thái hiện tại: surefire reports ghi lúc 01:13:21, file `TeacherReviewService.java` sửa lúc 01:14:59, `.class` lúc 01:10:38. | mtime trong `back_end/target` |
| F-3 | Các `@SpringBootTest` dùng datasource từ `.env`, không có `@Transactional`/Testcontainers → chạy test sẽ chạy Flyway và ghi dữ liệu vào DB được cấu hình. | `TeacherReviewSecurityAndIntegrityTest`, `BackEndApplicationTests`, `application.yaml` |
| F-4 | `generateSync` và `enqueueJobs` hardcode `Purpose.PRODUCTION`; `ExperimentService.createRun` mặc định `PRODUCTION` khi null, trong khi DB mặc định `'experiment'` (V2 dòng 7). | `GenerationService`, `ExperimentService` |
| F-5 | `MathDemoSeedService` tạo run `PRODUCTION`, ghi kết quả `answer_check` dựng sẵn với tên model thật, ép `QC_PASSED`; mở qua `POST /api/reviews/demo/seed-math`. | `MathDemoSeedService` dòng 215–222 |
| F-6 | Danh sách review lọc theo `status` không phân biệt `purpose` → với câu experiment, tham số lọc có thể làm lộ kết quả QC dù payload đã giấu `status`. | `TeacherReviewService.listQuestionsForReview` |
| F-7 | Pipeline QC hiện là format → answer_check; không có dedup; `answer_check` sai hoặc lỗi vẫn trả `QC_PASSED`. | `QcOrchestrator.evaluateAndSave` |
| F-8 | `llm_difficulty` được gán bằng độ khó mục tiêu khi sinh; `difficulty_judge_model` không được ghi. | `GenerationService.saveParsedQuestion`, `GenerationJobProcessor` |
| F-9 | Claim job không khóa hàng (không `SKIP LOCKED`, trái comment V2 dòng 27); không xử lý job kẹt `running`; job đánh dấu `done` trước khi QC chạy. | `GenerationJobRepository`, `GenerationJobPoller` |
| F-10 | `BankService` gọi trực tiếp `ExamQuestionRepository` (trái backend-structure quy tắc 2 và "bank chỉ phụ thuộc common/config"). `review` phụ thuộc `experiment`, `catalog`, `generation.worker` chưa có trong tài liệu. | `BankService`, `TeacherReviewService` |
| F-11 | Payload production trả `explanation`; fixture test có explanation nêu đáp án và test khẳng định trường này có mặt. Xung đột tiềm ẩn với M-6 → Q-1. | `TeacherReviewSecurityAndIntegrityTest` |
| F-12 | Test payload hiện gọi service trực tiếp + `ObjectMapper` tự tạo; không có MockMvc cho `/api/reviews`. | Thư mục test |
| F-13 | Duyệt lại production cập nhật `bank_item` mà không kiểm tra câu đã nằm trong đề; chỉ đường gỡ có kiểm tra. | `BankService.createOrUpdateFromReview` |
| F-14 | Sau khi production `rejected`, điều kiện QC chặn việc duyệt lại. | `TeacherReviewService.submitReview` |
| F-15 | Không có xác thực; `reviewerId` lấy từ body, null → giáo viên mặc định; không kiểm tra `label_assignment`. | `TeacherReviewService.submitReview` |
| F-16 | `cost_usd` luôn 0.0. | `LlmClient` |
| F-17 | `LlmClient` dùng biến hệ thống `GEMINI_API_KEY`/`LLM_API_KEY` khi cấu hình trống. | `LlmClient.resolveApiKey` |
| F-18 | Comment V1 về `exam_question` kiểm tra `in_bank` đã lỗi thời sau V2; README ghi "chưa có code". | V1 dòng 270–271, `README.md` |
| F-19 | Các file trong diff chưa commit đang ở LF, Git cảnh báo sẽ đổi sang CRLF. | Output `git diff` |
| F-20 | Experiment trả `latexValid = true` cố định thay vì giá trị trung tính. | `TeacherReviewService` |

### 11.2 Cần xác minh thêm

| ID | Nghi vấn |
|---|---|
| V-1 | Trạng thái compile/test hiện tại: cần chạy compile/test (sau khi giải quyết Q-7 hoặc được cho phép theo M-27). |
| V-2 | Spring Boot 4.x có thể dùng Jackson 3 cho HTTP, còn test dùng Jackson 2 (`com.fasterxml.jackson.databind`) → serialization trong test có thể khác payload thật. |
| V-3 | `db/schema.sql` có thực sự khớp V1–V3 không (chưa đối chiếu từng dòng). |
| V-4 | Experiment có đang đưa câu `format_failed`/`parse_success = false` ra chấm, trái comment `label_assignment` ở V1 không. |
| V-5 | Dữ liệu test tích tụ trong DB, `exam_code` sinh theo millis → test có thể phụ thuộc thứ tự/va chạm. |
| V-6 | Nộp review experiment song song → vi phạm unique → có thể trả 500 thay vì 409. |

### 11.3 Chờ quyết định nghiệp vụ

F-4, F-5 → Q-3, Q-4a · F-7 → Q-2 · F-8 → Q-5 · F-10 → Q-8 · F-11 → Q-1 · F-13, F-14 → Q-4b, Q-4c · F-15 → Q-6 · F-3 → Q-7.

---

## 12. Quy trình chuẩn cho mỗi issue

```
inspect → plan → [xác nhận] → test (đỏ) → implement → verify → diff review → [chờ xác nhận]
```

1. **Inspect** – báo cáo theo M-13. Không sửa file.
2. **Plan** – file sẽ sửa, file *không* sửa, test sẽ thêm, lệnh định chạy (đánh dấu lệnh cần cho phép theo M-27), Q-x liên quan. **Dừng chờ xác nhận.** Nếu có Q-x chưa chốt → không qua bước 3.
3. **Test** – viết test theo mục 4; chạy nếu được phép (M-27), ghi nhãn theo M-30 (kỳ vọng FAIL; hoặc `KHÔNG THỂ CHẠY` nếu code đang lỗi compile như F-1).
4. **Implement** – thay đổi nhỏ nhất làm test qua; không refactor ngoài phạm vi (M-34).
5. **Verify** – chạy test mới + hồi quy (M-19) trong phạm vi được phép (M-27); báo cáo nhãn M-30, lệnh, thời điểm.
6. **Diff review** – theo M-33.
7. **Chờ xác nhận** – không commit, không sang bước/issue khác (M-29, M-36).

### Mẫu báo cáo cuối lượt

```
Bước đã làm: ...
Inspect / Plan: (tóm tắt hoặc link)
File đã sửa: ...           File cố ý không sửa: ...
Test: | tên | nhãn M-30 | lệnh | thời điểm |
git diff --stat: ...
Phát hiện thêm (không sửa): ...
Câu hỏi cần chủ dự án quyết định: ...
Xác nhận: không chạy lệnh thuộc M-27 / không gọi API tính phí / không commit (hoặc nêu rõ ngoại lệ đã được cho phép)
```

---

## 13. Khuyến nghị triển khai sau (không bắt buộc cho v1)

- K-1 → K-5 ở trên.
- K-6. Profile `test` với DB riêng (sau khi chốt Q-7), để `mvn test` không chạm DB dev.
- K-7. Tạo `docs/decisions.md` khi bắt đầu chốt các Q-x.
- K-8. Cập nhật `backend-structure.md` sau khi chốt Q-8; cập nhật README trạng thái dự án.
- K-9. Thêm `.gitattributes` ở gốc repo để cố định line ending.

## Lịch sử

- v1 (2026-10-21): bản đầu, tổng hợp từ audit repository.
- 2026-10-22: thêm "Quyết định ngày 22/10/2026 – chế độ giáo viên" (ngoại lệ của M-6 và quyết định 21/10 mục 2 cho
  luồng tạo đề theo ma trận); ghi chú ở M-6, M-9. Không đổi số phiên bản tài liệu.
- 2026-10-23: thêm "Quyết định ngày 23/10/2026 – bỏ thí nghiệm C1/C2/C3, chương thử nghiệm mới" (bỏ thí nghiệm so sánh
  C1/C2/C3; giáo viên dùng thử và khảo sát qua chế độ giáo viên; 4 thử nghiệm mới). Không đổi số phiên bản tài liệu.
## Quyết định ngày 21/10/2026 (thay thế các comment xung đột trong V1__init_schema.sql)

Không sửa file V1 (đã chạy, sửa sẽ lệch checksum Flyway). Khi comment trong V1 mâu thuẫn với mục này, mục này thắng.

1. Quy tắc trong V1 "không báo giáo viên biết đáp án họ chọn trùng hay lệch đáp án LLM, trước hay sau khi nộp" chỉ bắt buộc cho review_purpose = experiment. Với production, dự kiến cho xem đáp án và lời giải LLM sau khi giáo viên đã chấm (mục 4). Chưa làm.
2. Demo 24/10: chấm một bước như hiện tại. Form chấm của cả hai purpose không hiện đáp án LLM, lời giải, độ khó LLM, kết quả QC. API chấm không trả status, latexValid, latexErrors.
3. Ngân hàng (demo): giữ lời giải LLM, frontend gắn nhãn "Lời giải do AI gợi ý, chưa được giáo viên kiểm tra". Chưa làm việc đổi lời giải ngân hàng thành NULL.
4. Sau demo, nếu thầy đồng ý, làm duyệt hai bước trên cùng một màn:
   - Bước 1 (mù, khóa sau khi ghi nhận): đáp án giáo viên, độ khó, "đề và 4 phương án dùng được nguyên trạng không".
   - Bước 2: mở đáp án và lời giải LLM; giáo viên duyệt, sửa (kể cả lời giải) hoặc từ chối; đánh giá lời giải đúng / cần sửa / sai.
   - Lưu riêng đáp án ban đầu (bước 1) và đáp án cuối (bước 2), không ghi đè.
   - Chỉ số "duyệt không sửa" theo đề cương lấy từ bước 1; bước 2 là chỉ số bổ sung.
   - Chỉ lời giải đã qua bước 2 mới được coi là lời giải chính thức trong ngân hàng.
   - Cần migration, API và frontend mới. Chưa quyết thực nghiệm có làm bước 2 không.
5. Phạm vi dedup: experiment chỉ so trong cùng run; production so cùng môn, cùng purpose; không so giữa experiment và production. Mẫu chấm experiment chỉ loại duplicate và format_failed, không lọc theo answer_check.

## Quyết định ngày 22/10/2026 – chế độ giáo viên (tạo đề theo ma trận)

Theo ưu tiên của giảng viên hướng dẫn (trải nghiệm giáo viên tiện nhất). Đây là **thay đổi so với quyết định 21/10 mục 2 và quy tắc V1 dòng 136–138 (M-6)**, chỉ áp cho luồng tạo đề theo ma trận; mọi chỗ khác giữ nguyên.

1. Đề tạo theo ma trận (`exam.mode = 'teacher'`, API `/api/exams/**`, V7) là chế độ giáo viên, production, **không chấm mù**: API xem đề trả đáp án (`correctOption`), đáp số `sa`, nhãn và mức từng ý `tf`, lời giải và cờ "cần xem kỹ" (`needsReview`, `reviewReasons`: Solver lệch, không kết luận được hoặc chưa chạy; dedup chưa chạy; cảnh báo bước format).
2. Không đổi: form chấm mù `/api/reviews/**` của cả production và experiment (M-6, M-7, quyết định 21/10 mục 2); thực nghiệm C1/C2/C3; experiment không dùng chế độ giáo viên.
3. Giữ/Sửa ở chế độ giáo viên tạo `teacher_review` production với `review_mode = 'assisted'` và `bank_item` trong cùng transaction (M-9 vẫn giữ). Đánh giá `assisted` bị loại khỏi mọi chỉ số chấm mù (`answer_correct`, tỉ lệ duyệt không sửa); thống kê giữ/sửa/đổi lấy riêng từ `exam_slot_action`.
4. Câu vẫn lệch Solver sau 1 lần sinh lại được đưa vào đề kèm cờ "cần xem kỹ". Giáo viên được Giữ câu này vào ngân hàng sau khi đã thấy cờ; đây là ngoại lệ có chủ đích so với form chấm mù (duyệt production ở đó chỉ nhận câu `qc_passed`/`in_bank`).

## Quyết định ngày 23/10/2026 – bỏ thí nghiệm C1/C2/C3, chương thử nghiệm mới

Thay thế các nội dung về thí nghiệm so sánh C1/C2/C3 ở các mục trên (quyết định 21/10, quyết định 22/10 mục 2) và ở
README ("Phần thực nghiệm so sánh 3 cách sinh"). Không đổi code trong quyết định này.

1. **Bỏ thí nghiệm so sánh C1/C2/C3** (các câu hỏi nghiên cứu cũ về so sánh 3 cách prompting). Form chấm mù
   `/api/reviews/**` và chế độ `experiment` vẫn giữ trong code như một chức năng thực nghiệm, nhưng không dùng khi mời
   giáo viên.
2. **Giáo viên dùng thử và khảo sát đều qua chế độ giáo viên** (`/api/exams/**`: có đáp án, lời giải, cờ "cần xem kỹ").
   - Tính đúng đáp án trong thực nghiệm do nhóm tự kiểm tra hoặc tính toán, **không** suy ra từ việc giáo viên Giữ câu.
   - Giới hạn phải ghi trong báo cáo: giáo viên đánh giá khi đã thấy đáp án và lời giải của hệ thống, nên có thể thiên
     về chấp nhận. Tỉ lệ Giữ/Sửa/Đổi (`exam_slot_action`) chỉ phản ánh mức chấp nhận, không phải độ đúng.
3. **Chương thử nghiệm mới** gồm 4 phần:
   1. Solver bắt lỗi: chạy Solver trên các câu đúng và trên bản lỗi do nhóm tự tạo từ các câu đó, đo khả năng phát hiện
      lỗi và báo động giả.
   2. Ngưỡng dedup: so cách dùng 3-gram với embedding trên khoảng 150 cặp câu do nhóm tự gán nhãn trùng/không trùng.
   3. So sánh với cách dùng hiện tại: hệ thống so với các prompt mẫu mà cộng đồng giáo viên đang dùng trên Gemini.
   4. Khảo sát giáo viên dùng thử (theo mục 2, kèm giới hạn đã nêu).