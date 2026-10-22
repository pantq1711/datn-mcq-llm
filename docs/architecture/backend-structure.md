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
│   └── util/
├── config/                 WebConfig (DbEnum ConverterFactory), cấu hình chung
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
├── exam/                   exam, exam_question
└── goldset/                dedup_gold_pair, dedup_gold_label
```

## Hướng phụ thuộc (không được tạo vòng)

```
exam        ──► bank, catalog, user
review      ──► generation, qc, bank, user
generation  ──► qc, llm
experiment  ──► generation, catalog
goldset     ──► qc, user
qc          ──► llm
bank, catalog, user, llm ──► (chỉ common/config)
```

Quy tắc:
1. Controller -> Service -> Repository. Controller không chạm repository.
2. Module khác chỉ gọi **service** (hoặc orchestrator) của nhau, không gọi repository của nhau.
3. Tham chiếu chéo module bằng **id** (`Integer questionId`, `Integer templateId`...), không dùng `@ManyToOne` sang entity của module khác. Nhờ vậy `bank` (trỏ tới `teacher_review`) và `review` (tạo `bank_item`) không vòng nhau.
4. `qc` không đọc entity `generated_question`: `generation` truyền nội dung câu hỏi và phạm vi dedup (`DedupScope`: purpose, run của câu và danh sách run được so, do `DedupScopeResolver` dựng) vào qua tham số, nhận kết quả về rồi tự cập nhật `status`. **Ngoại lệ duy nhất:** các truy vấn ứng viên dedup của `QualityCheckResultRepository` trong `qc` được phép là truy vấn native **chỉ đọc** trên `generated_question` (kèm `quality_check_result` để nhận câu cùng run đã có dòng dedup passed) ở bước dedup, để lấy ứng viên đúng trong danh sách run mà `generation` truyền vào. Mỗi dạng câu một truy vấn và chỉ lấy câu cùng dạng (V4): `findDedupCandidateRows` cho `mcq` (thân câu và 4 phương án), `findShortAnswerDedupCandidateRows` cho `sa` (thân câu và `sa_answer`; khóa chuẩn của đáp số tính trong Java), `findTrueFalseDedupCandidateRows` cho `tf` (đề dẫn và 4 ý, đọc thêm `generated_question_statement`; không đọc nhãn). Không ghi `generated_question` từ `qc`. (`QuestionEmbeddingRepository` hiện không có truy vấn nào; dedup chưa dùng embedding.)
5. Không giữ transaction DB trong lúc gọi LLM: `GenerationJobPoller` và `GenerationJobProcessor` tách thành hai bean riêng để `@Transactional` hoạt động qua proxy. Poller lấy job và đặt `status = 'running'` trong một transaction ngắn, gọi LLM ngoài transaction, rồi ghi kết quả trong một transaction ngắn khác.
6. Không tự sửa (sanitize) công thức LaTeX bị hỏng: `LatexFormatValidator` chỉ phát hiện lỗi định dạng để đánh dấu `format_failed` hoặc yêu cầu sinh lại, giữ nguyên dữ liệu thô để đánh giá khách quan chiến lược prompting.
7. `llm` chỉ gọi dịch vụ ngoài, không biết đến entity của dự án.
8. Quy tắc DB không tự chặn được (xem `docs/contract/enums.md`, mục "Quy tắc đi kèm") đặt trong service của module `review`, `exam`, `goldset`.
9. DTO trả về form chấm của module `review` không chứa `correct_option`, `llm_difficulty`, kết quả QC.
