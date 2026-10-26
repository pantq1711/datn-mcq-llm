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
│   ├── prompt/             lắp ghép prompt (zero-shot; few-shot C2/C3 đã gỡ 25/10), prompt ma trận, schema JSON
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
├── review/                 teacher_review, teacher_review_statement (AssistedKeepService: Giữ câu ở chế độ giáo viên;
│                           form chấm mù đã gỡ 25/10). label_assignment, teacher_review_reason: bảng cũ, không còn code ghi
├── bank/                   bank_item
├── matrix/                 exam_matrix, exam_matrix_cell (V6: ma trận đề, mỗi ô một question_template)
├── material/               lesson_material (V18, W2-3: tài liệu bài học đính kèm, chỉ lưu văn bản đã trích)
├── exam/                   exam, exam_question, exam_slot, exam_slot_action (V7: tạo đề theo ma trận)
└── goldset/                dedup_gold_pair, dedup_gold_label
```

## Hướng phụ thuộc (không được tạo vòng)

```
exam        ──► matrix, generation, qc, review, bank, catalog, user, material   (V7: tạo đề theo ma trận; W2-3: material)
matrix      ──► catalog
material    ──► (chỉ common; passage/PassageSelector là hàm thuần)   (W2-3 bước 4: exam ──► material qua MaterialService; material khai báo MaterialUsageLookup, exam cài đặt)
review      ──► generation, bank, catalog, experiment, user   (25/10: chỉ còn AssistedKeepService)
generation  ──► qc, llm
experiment  ──► generation, catalog
goldset     ──► qc, user
qc          ──► llm   (GroundingChecker và AnswerSolverChecker gọi LlmClient)
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
6. Không tự sửa (sanitize) công thức LaTeX bị hỏng: `LatexFormatValidator` chỉ phát hiện lỗi định dạng để đánh dấu `format_failed` hoặc yêu cầu sinh lại, không sửa nội dung câu. Ngoại lệ có chủ đích (ngày 23/10), chỉ cho run `production`: chế độ sửa của parser (`LlmOutputRepair`) nhân đôi dấu `\` của lệnh LaTeX chưa thoát trong chuỗi JSON và tự đóng `$` quên ở cuối trường, mỗi lần sửa ghi cảnh báo; `raw_llm_output` vẫn giữ bản gốc. Run `experiment` không sửa gì (thí nghiệm C1/C2/C3 đã bỏ 25/10).
7. `llm` chỉ gọi dịch vụ ngoài, không biết đến entity của dự án.
8. Quy tắc DB không tự chặn được (xem `docs/contract/enums.md`, mục "Quy tắc đi kèm") đặt trong service của module `review`, `exam`, `goldset`.
9. Chế độ giáo viên là chế độ duy nhất (25/10, form chấm mù đã gỡ): API xem đề của module `exam` (`/api/exams/**`) có đáp án, lời giải và cờ từ kết quả QC (quyết định 22/10 trong `docs/ai-development-rules.md`). Module `review` không còn controller và DTO.
10. `generation` không biết `exam`/`matrix`: run sinh câu theo ô mang thông số ô ở `experiment_run.hyperparams` (`MatrixCellSpec`), worker dựng prompt từ đó; `exam` đối soát kết quả qua `GenerationService.latestJobForSlot`. Ngoại lệ đọc chéo bảng: `ExamSlotRepository.findBankCandidates` là truy vấn native **chỉ đọc** trên `bank_item`, `bank_item_statement`, `generated_question`, `question_template`, `exam_matrix_cell`, `exam_question` để chọn câu ngân hàng khớp ô (W1-3: thêm `exam_slot` và `exam` để xếp hạng mềm theo số đề đã dùng và lần dùng gần nhất).
11. Xác thực và phân quyền (W1-1): quyền thô theo URL và vai trò (TEACHER, ADMIN) đặt ở `config.SecurityConfig`; đường chưa khai báo bị chặn. Quyền sở hữu (giáo viên chỉ thấy ma trận, đề của mình) kiểm tra ở **service**: controller nhận `CurrentUser` rồi truyền `actorId` xuống; service tìm bằng `findByIdAndCreatedBy` và trả 404 giống bản ghi không tồn tại. Kiểm tra chủ sở hữu phải đứng **trước** `reconcile` (đối soát có ghi DB). Service không đọc `SecurityContext`, nên worker, scheduler và job phục hồi (không đi qua HTTP) không cần đăng nhập.
12. Ngân hàng câu (`bank_item`) dùng chung cho mọi giáo viên (quyết định H1 ngày 24/10): tạo đề lấy câu ngân hàng không lọc theo người.
13. Giáo viên sửa câu (W1-2, V13): `exam` (`ExamActionService.editQuestion`) kiểm chủ sở hữu, khóa chỗ, kiểm trạng thái rồi gọi `generation` (`QuestionEditService`) tạo bản sửa là một dòng `generated_question` mới (`edited_from_question_id`), không sửa dòng cũ. QC không cần LLM chạy trong transaction đó; Solver chạy qua hàng đợi `generation_job` có sẵn (`job_type = 'answer_check'`, không gắn chỗ), poller gọi Solver ngoài transaction rồi ghi kết quả, trạng thái câu và job `done` trong một transaction ngắn (`QcOrchestrator.callSolver` / `recordSolver`). Không thêm chiều phụ thuộc module. Mọi thống kê về đầu ra LLM phải lọc `edited_from_question_id IS NULL`.
14. Ngân hàng có lọc (W1-3, quyết định D2): `bank` (`BankSearchRepository`, `NamedParameterJdbcTemplate`) chạy truy vấn native **chỉ đọc**, SQL ghép từ mảnh cố định và giá trị đi qua tham số có tên, trên `bank_item`, `bank_item_statement`, `generated_question`, `question_template`, `subject`, `exam_matrix_cell`, `teacher_review`, `users`, `experiment_run`, `exam`, `exam_slot`, `exam_question`, `exam_slot_action` để lọc, đếm số đề đã dùng và dựng nguồn gốc. Đây là ngoại lệ thứ ba của quy tắc 2 (cùng loại với quy tắc 4 và 10); `bank` vẫn không gọi service của module khác. (Vi phạm cũ F-10: phần `BankService` gọi `ExamQuestionRepository` đã hết từ 25/10 khi gỡ `removeBankItemIfUnused`; `exam_question` và các truy vấn đọc nó chưa xử lý, chờ task sau.) Chỉ GET; sửa, xóa câu ngân hàng chưa làm (Q-4c).
15. Dedup câu trong cùng đề (W1-3, quyết định D4): `generation` định nghĩa interface `SlotDedupContext`; `exam` cài đặt (`ExamSlotSiblings`, chỉ đọc, đọc câu qua `BankService` và `GenerationService`) và trả nội dung các câu đang nằm ở chỗ khác cùng đề. `DedupScopeResolver.resolveForSlot` (worker, job có `exam_slot_id`) và `QuestionEditService.createVersion` (giáo viên sửa) đưa chúng vào `DedupScope.extraCandidates`; `DedupService` so thêm với chúng, chỉ cùng dạng câu. `DedupScopeResolver` lấy interface qua `ObjectProvider` (muộn) nên không vòng phụ thuộc lúc khởi tạo. `generation` vẫn không biết `exam`/`matrix`; `qc` không đọc entity.
16. Vết QC theo ô (W1-3): `exam` (`ExamQcTraceService`) CHỈ ĐỌC và không đối soát (khác xem đề và tiến độ), kiểm chủ sở hữu rồi ghép job sinh (`GenerationService.generateJobsForSlot`), chuỗi bản sửa (`editedChild`), chi tiết QC từng bước (`QcSummaryService.detail`), nhật ký thao tác. Không trả `raw_llm_output`, prompt, token, chi phí.

17. Sinh theo bài và quản lý ma trận (W1-4): đề theo bài là ma trận ẩn (`exam_matrix.kind = 'lesson'`, V15) do `MatrixService.createLesson` tạo trong transaction của `ExamBuildService.buildByLesson`, nên `exam ──► matrix` không đổi chiều. Sửa/xóa ma trận cần biết số đề đã tạo nhưng `matrix` không được phụ thuộc `exam`: `matrix` khai báo interface `MatrixUsageLookup`, `exam` cài (`ExamMatrixUsage`), cùng mẫu với `SlotDedupContext`. Sửa/xóa khóa hàng ma trận bằng `SELECT ... FOR UPDATE` native (`@Lock(PESSIMISTIC_WRITE)` của Hibernate trên PostgreSQL là `FOR NO KEY UPDATE`, không xung đột với khóa `FOR KEY SHARE` của khóa ngoại, nên không chờ tạo đề đang dở), tạo đề khóa chia sẻ (`PESSIMISTIC_READ`). Ngoại lệ đọc chéo bảng thứ tư của quy tắc 2: `QuestionTemplateRepository.deleteUnreferenced` (module `catalog`) xóa template chỉ khi `NOT EXISTS` tham chiếu từ `exam_matrix_cell`, `generated_question`, `generation_job`. `findBankCandidates` (quy tắc 10) đọc thêm `exam_slot_action` và `generated_question` (CTE `out_of_reach`: câu đang nằm trong đề hoặc đã bị Đổi ra, kể cả bản gốc theo `edited_from_question_id`) để Đổi bằng câu ngân hàng không lấy lại câu đã có hay đã đổi.
18. Lỗi toàn vẹn dữ liệu (W1-4, K-5): `ApiExceptionHandler` trả 409 `FOREIGN_KEY_CONFLICT` (SQLState 23503) hoặc `UNIQUE_CONFLICT` (23505) cho hai thao tác đồng thời tranh chấp; lỗi toàn vẹn khác là lỗi chương trình, 500 kèm `DATA_INTEGRITY`.
19. Công cụ thí nghiệm (25/10): `GenerationController` (`/api/generation/*`) và `CatalogTemplateController` (`/api/catalog/templates`) có `@ConditionalOnProperty(app.experiment.enabled=true)`, mặc định tắt; không có bean thì không có đường nên 404 cho mọi vai trò. `GET /api/generation/jobs` và `enqueue` trả `GenerationJobDto`, không trả entity. `SecurityConfig` vẫn khai báo quyền ADMIN cho `/api/generation/**` để khi bật thì chỉ admin gọi được.
20. Trích văn bản tài liệu bài học (W2-3, `material.extract`): loại file chỉ nhận dạng từ nội dung (magic bytes), không dùng đuôi file hay Content-Type. docx: ghi vào thư mục tạm riêng, `ZipSafetyScanner` quét central directory và đếm byte giải nén thật (số mục, dung lượng mỗi mục và tổng, tỉ lệ nén, tên mục thoát thư mục), `DocxFormulaCounter` đếm công thức/ảnh và từ chối XML có DTD hay thực thể, rồi mới chạy `pandoc --sandbox +RTS -M<heap>` (module `material` tự gọi pandoc, dùng chung `app.export.pandoc-path`, không phụ thuộc `exam`). Đối tượng MathType nhúng (OLE) bị pandoc bỏ qua nên được đếm và báo trong `warnings`. pdf: PDFBox 3.0.8, không OCR, có giới hạn trang, độ dài, thời gian. `MaterialTextExtractor` giới hạn số lần trích đồng thời (`app.materials.max-concurrent-extractions`). Không lưu file gốc.
21. Ngữ cảnh sinh câu của đề (W2-3, V19): `ExamContextService` (module `exam`) kiểm tra và lưu tài liệu đính kèm và "yêu cầu thêm cho AI" theo từng đề (`exam_generation_context`, `exam_context_material`); kiểm quyền và khóa chia sẻ tài liệu qua `MaterialService.requireOwnedShared` (`exam ──► material`, chiều mới, đã được duyệt cùng phần audit). `material` chặn xóa tài liệu đang dùng qua interface `MaterialUsageLookup` (cài đặt `ExamMaterialUsage` trong `exam`), cùng mẫu với `MatrixUsageLookup`. "Yêu cầu thêm" đi vào `MatrixCellSpec` (`experiment_run.hyperparams`), nên `generation` vẫn không biết `exam`/`matrix`/`material`. Ngân hàng riêng tư: `bank_item.visible_to_user_id` (V19) do `BankService.restrictToUser` đặt khi Giữ câu sinh từ đề có tài liệu; `BankSearchRepository` (mọi truy vấn của người xem) và `ExamSlotRepository.findBankCandidates` lọc theo `visible_to_user_id IS NULL OR = người xem/người tạo đề`.
22. Đoạn tài liệu vào prompt (W2-3 bước 5): `ExamBuildService` chọn đoạn lúc xếp hàng job (tạo đề, sinh lại, Đổi câu) qua `MaterialPassageService` (nạp văn bản) và `PassageSelector` (hàm thuần: chia đoạn, BM25, cửa sổ theo chỗ), rồi truyền ảnh chụp cho `GenerationService.enqueueMatrixJob`, ghi ở `generation_job.material_context/material_refs` (V19). `GenerationJobProcessor` dựng prompt từ đúng ảnh chụp này (`MatrixPromptDraftV0.documentBlock`), nên `generation` vẫn không biết `exam`/`matrix`/`material` và không đọc tài liệu. Dữ liệu của người dùng đi vào prompt luôn qua `PromptData.neutralize` (bỏ mọi `<<<`, `>>>`) và nằm trong khối có ranh giới, sau thông số của ô và trước quy tắc, yêu cầu đầu ra. Văn bản tài liệu docx được làm sạch markdown ở khâu trích (`MarkdownCleaner`), nên offset của đoạn khớp văn bản đã lưu.
23. QC "bám tài liệu" (W2-3 bước 6): job `grounding_check` (V20) đi qua `generation_job` hiện có, KHÔNG gộp vào `answer_check` (job đó gắn với điều kiện `status = generated` và đổi trạng thái câu). `GenerationJobProcessor.finishGeneratedJob` xếp job trong cùng transaction với lúc job sinh câu done (chỉ khi job mang `material_context` và câu là `qc_passed`/`generated`); worker claim `SKIP LOCKED`, `StaleJobRecovery` dọn job kẹt như mọi job. `GenerationJobPoller.processGrounding`: transaction ngắn dựng đầu vào (`prepareGrounding`), gọi judge ngoài transaction (`QcOrchestrator.callGrounding` -> `GroundingChecker`), transaction ngắn ghi `question_grounding` và đánh dấu job done (`completeGrounding`). Kết quả CHỈ là cảnh báo: không đổi `status` của câu (Q-2, M-10); `exam` đọc kết quả qua `QcSummaryService` (cờ "cần xem kỹ" và vết QC). `GroundingChecker` kiểm chứng trích dẫn của judge trong đoạn đã đưa nên `SUPPORTED` không có bằng chứng bị hạ xuống `UNKNOWN`.
