# Cấu trúc thư mục backend

Package gốc: `com.datn.mcq` (lớp `BackEndApplication` nằm ở đây, nên mọi thứ dưới nó được Spring quét,
kể cả `@Entity` và `@Converter` tự áp dụng).

Chia theo **tính năng** (mỗi module có `entity`, `repository`, `service`, `controller`, `dto` riêng), không chia theo tầng. Riêng 3 module nặng (`llm`, `generation`, `qc`) chia thêm sub-package chuyên biệt để tránh class phình to:

```
com.datn.mcq
├── BackEndApplication
├── enums/                  15 enum + converter (đã có)
├── common/
│   ├── exception/
│   ├── security/           CurrentUser (người gọi lấy từ JWT) + argument resolver (W1-1)
│   └── util/
├── config/                 WebConfig (DbEnum ConverterFactory), SecurityConfig (filter chain, JWT, CORS),
│                           JsonSecurityErrorHandler (401/403 JSON), OpenApiConfig (bearer), cấu hình chung
├── auth/                   refresh_token (W1-1): login, refresh xoay vòng, logout, đổi mật khẩu, admin đầu tiên,
│                           quản trị tài khoản /api/admin/users
├── user/                   users
├── catalog/                subject, question_template
├── experiment/             experiment_run
├── llm/                    gọi dịch vụ AI ngoài
│   ├── client/             LlmClient, EmbeddingClient
│   ├── prompt/             lắp ghép prompt C1, C2, C3 + exemplar
│   └── dto/
├── generation/             generation_job, generated_question
│   ├── worker/             GenerationJobPoller, GenerationJobProcessor
│   ├── parser/             McqJsonParser, LatexFormatValidator
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── dto/
├── qc/                     quality_check_result, question_embedding
│   ├── orchestrator/       điều phối tuần tự format -> dedup -> answer_check
│   ├── checker/            FormatChecker, DedupChecker, AnswerSolverChecker
│   ├── repository/
│   ├── entity/
│   └── dto/
├── review/                 label_assignment, teacher_review, teacher_review_reason
├── bank/                   bank_item
├── matrix/                 exam_matrix, exam_matrix_cell (V6: ma trận đề, mỗi ô một question_template)
├── exam/                   exam, exam_question, exam_slot, exam_slot_action (V7: tạo đề theo ma trận)
└── goldset/                dedup_gold_pair, dedup_gold_label
```

## Hướng phụ thuộc (không được tạo vòng)

```
exam        ──► matrix, generation, qc, review, bank, catalog, user   (V7: tạo đề theo ma trận)
matrix      ──► catalog
review      ──► generation, qc, bank, user
generation  ──► qc, llm
experiment  ──► generation, catalog
goldset     ──► qc, user
qc          ──► llm
auth        ──► user                                   (W1-1, duyệt ngày 24/10)
bank, catalog, user, llm ──► (chỉ common/config)
```

`common.security.CurrentUser` dùng chung cho controller mọi module (không phải chiều phụ thuộc giữa các module).

Quy tắc:
1. Controller -> Service -> Repository. Controller không chạm repository.
2. Module khác chỉ gọi **service** (hoặc orchestrator) của nhau, không gọi repository của nhau.
3. Tham chiếu chéo module bằng **id** (`Integer questionId`, `Integer templateId`...), không dùng `@ManyToOne` sang entity của module khác. Nhờ vậy `bank` (trỏ tới `teacher_review`) và `review` (tạo `bank_item`) không vòng nhau.
4. `qc` không đọc entity `generated_question`: `generation` truyền nội dung câu hỏi và phạm vi dedup (`DedupScope`: purpose, run của câu và danh sách run được so, do `DedupScopeResolver` dựng) vào qua tham số, nhận kết quả về rồi tự cập nhật `status`. **Ngoại lệ duy nhất:** các truy vấn ứng viên dedup của `QualityCheckResultRepository` trong `qc` được phép là truy vấn native **chỉ đọc** trên `generated_question` (kèm `quality_check_result` để nhận câu cùng run đã có dòng dedup passed) ở bước dedup, để lấy ứng viên đúng trong danh sách run mà `generation` truyền vào. Mỗi dạng câu một truy vấn và chỉ lấy câu cùng dạng (V4): `findDedupCandidateRows` cho `mcq` (thân câu và 4 phương án), `findShortAnswerDedupCandidateRows` cho `sa` (thân câu và `sa_answer`; khóa chuẩn của đáp số tính trong Java), `findTrueFalseDedupCandidateRows` cho `tf` (đề dẫn và 4 ý, đọc thêm `generated_question_statement`; không đọc nhãn). Không ghi `generated_question` từ `qc`. (`QuestionEmbeddingRepository` hiện không có truy vấn nào; dedup chưa dùng embedding.)
5. Không giữ transaction DB trong lúc gọi LLM: `GenerationJobPoller` và `GenerationJobProcessor` tách thành hai bean riêng để `@Transactional` hoạt động qua proxy. Poller lấy job và đặt `status = 'running'` trong một transaction ngắn, gọi LLM ngoài transaction, rồi ghi kết quả trong một transaction ngắn khác.
6. Không tự sửa (sanitize) công thức LaTeX bị hỏng: `LatexFormatValidator` chỉ phát hiện lỗi định dạng để đánh dấu `format_failed` hoặc yêu cầu sinh lại, giữ nguyên dữ liệu thô để đánh giá khách quan chiến lược prompting. Ngoại lệ có chủ đích (ngày 23/10), chỉ cho run `production`: chế độ sửa của parser (`LlmOutputRepair`) nhân đôi dấu `\` của lệnh LaTeX chưa thoát trong chuỗi JSON và tự đóng `$` quên ở cuối trường, mỗi lần sửa ghi cảnh báo; `raw_llm_output` vẫn giữ bản gốc. Run `experiment` (C1/C2/C3) không sửa gì.
7. `llm` chỉ gọi dịch vụ ngoài, không biết đến entity của dự án.
8. Quy tắc DB không tự chặn được (xem `docs/contract/enums.md`, mục "Quy tắc đi kèm") đặt trong service của module `review`, `exam`, `goldset`.
9. DTO trả về form chấm của module `review` không chứa `correct_option`, `llm_difficulty`, kết quả QC. Ngoại lệ có chủ đích: API xem đề của module `exam` ở chế độ giáo viên (`/api/exams/**`) có đáp án, lời giải và cờ từ kết quả QC (quyết định 22/10 trong `docs/ai-development-rules.md`).
10. `generation` không biết `exam`/`matrix`: run sinh câu theo ô mang thông số ô ở `experiment_run.hyperparams` (`MatrixCellSpec`), worker dựng prompt từ đó; `exam` đối soát kết quả qua `GenerationService.latestJobForSlot`. Ngoại lệ đọc chéo bảng: `ExamSlotRepository.findBankCandidates` là truy vấn native **chỉ đọc** trên `bank_item`, `bank_item_statement`, `generated_question`, `question_template`, `exam_matrix_cell`, `exam_question` để chọn câu ngân hàng khớp ô.
11. Xác thực và phân quyền (W1-1): quyền thô theo URL và vai trò (TEACHER, ADMIN) đặt ở `config.SecurityConfig`; đường chưa khai báo bị chặn. Quyền sở hữu (giáo viên chỉ thấy ma trận, đề của mình) kiểm tra ở **service**: controller nhận `CurrentUser` rồi truyền `actorId` xuống; service tìm bằng `findByIdAndCreatedBy` và trả 404 giống bản ghi không tồn tại. Kiểm tra chủ sở hữu phải đứng **trước** `reconcile` (đối soát có ghi DB). Service không đọc `SecurityContext`, nên worker, scheduler và job phục hồi (không đi qua HTTP) không cần đăng nhập.
12. Ngân hàng câu (`bank_item`) dùng chung cho mọi giáo viên (quyết định H1 ngày 24/10): tạo đề lấy câu ngân hàng không lọc theo người.
