# Đánh giá độ sâu kỹ thuật – góc nhìn phản biện

Câu hỏi trung tâm: **"Hệ thống này có phải chỉ là CRUD + gọi API LLM không?"**

Phạm vi: chỉ đọc repo tại commit `5fefe87` (23/10/2026). Bằng chứng lấy từ code, không lấy từ tài liệu tự mô tả. Số
dòng code là dòng Java trong `back_end/src/main/java/com/datn/mcq`, **không tính** dòng trống, comment/Javadoc và
`import` (code có rất nhiều Javadoc: 15 944 dòng thô, 11 064 dòng code, 175 file). Số test là số phương thức `@Test` và
`@ParameterizedTest` (468 phương thức; một số test tham số hóa nên lần chạy toàn bộ gần nhất có 587 lượt), xếp theo
package của test. Code test có 9 722 dòng.

---

## Phần 1 – Kiểm kê và phân loại

### 1.1 Cách xếp loại

Xếp ở mức sub-package. Package nào trộn nhiều loại thì tách theo file và ghi rõ. Repository, entity, DTO, controller
luôn xếp A, kể cả khi trong đó có một câu SQL đáng kể; các câu SQL như vậy được nêu riêng ở 1.3.

| Loại | Gồm |
|---|---|
| A. CRUD/nhập liệu | entity, dto, repository, controller; `bank`, `catalog`, `experiment`, `matrix`, `user`, `goldset`, `enums`, `config`, `common/exception`; `ExamViewService`; `PreviousQuestionLookup`, `TrueFalseQuestionStore`; `qc/service` (ghi kết quả, tóm tắt cờ); `MathDemoSeedService`; `JsonStringArray` |
| B. Gọi dịch vụ ngoài | `llm/client/LlmClient`; `llm/prompt` (trừ `TfLabelPattern`); `GenerationService` (điều phối gọi LLM rồi lưu); `AnswerSolverChecker`; `PandocRunner`, `ExamExportService`, `DocumentExportException` |
| C. Logic nghiệp vụ không tầm thường | `qc/orchestrator`; `FormatChecker`; `ExamBuildService`, `ExamActionService`; `ExamMarkdownBuilder`; `TeacherReviewService`; `GenerationJobPoller`, `GenerationJobProcessor`; `QcRerunService`, `DedupScopeResolver`; `TfLabelPattern` |
| D. Thuật toán/kỹ thuật | `ShortAnswerValue`, `LatexValidationRules`, `SimpleJson`, `TextDistanceUtils`; `generation/parser`; `DedupChecker`, `McqTextNormalizer`; `StaleJobRecovery`, `StaleJobScheduler` |

Các chỗ có thể bị cho là xếp "nặng tay", đã hạ xuống:
- `MatrixService` (302 dòng) có 18 câu `throw IllegalArgumentException`, thực chất là kiểm tra đầu vào cộng tạo bản ghi,
  nên xếp A.
- `llm/prompt` (609 dòng) là ghép chuỗi prompt, nên xếp B dù tốn công thiết kế.
- `GenerationJobPoller`/`GenerationJobProcessor` (352 dòng) là điều phối, nên xếp C. Chỉ phần khôi phục job kẹt xếp D.

### 1.2 Số liệu theo module

| Module | Dòng code | A | B | C | D | Số test |
|---|---:|---:|---:|---:|---:|---:|
| bank | 416 | 416 | | | | 3 |
| catalog | 239 | 239 | | | | 5 |
| common (util, exception) | 1 048 | 70 | | | 978 | 53 |
| config | 70 | 70 | | | | 13 |
| enums | 522 | 522 | | | | 8 |
| exam (+ export) | 1 274 | 600 | 111 | 563 | | 29 |
| experiment | 231 | 231 | | | | 0 |
| generation (parser, service, worker…) | 2 109 | 688 | 558 | 466 | 397 | 111 |
| goldset | 140 | 140 | | | | 0 |
| llm (client, prompt) | 839 | 10 | 769 | 60 | | 38 |
| matrix | 586 | 586 | | | | 4 |
| qc (checker, orchestrator, service…) | 1 772 | 757 | 158 | 485 | 372 | 124 |
| review | 1 742 | 799 | | 943 | | 59 |
| user | 69 | 69 | | | | 0 |
| (gốc) BackEndApplication | 7 | 7 | | | | 1 |
| migration (test schema, V4–V9) | – | | | | | 20 |
| **Tổng** | **11 064** | **5 204** | **1 596** | **2 517** | **1 747** | **468** |
| **Tỷ lệ** | 100 % | **47,0 %** | **14,4 %** | **22,7 %** | **15,8 %** | |

Nhận xét về test: 347/468 phương thức test (74 %) nằm ở `qc`, `generation`, `common`, `review`, tức là tập trung vào phần
C và D. Các module A thuần (`experiment`, `goldset`, `user`) không có test riêng.

### 1.3 Đoạn tiêu biểu của loại D (thuật toán/kỹ thuật)

| Đoạn | Vì sao không phải CRUD |
|---|---|
| [ShortAnswerValue.java:171-182](../../back_end/src/main/java/com/datn/mcq/common/util/ShortAnswerValue.java#L171-L182) (`dividedBy`), [:335](../../back_end/src/main/java/com/datn/mcq/common/util/ShortAnswerValue.java#L335) (`squareFree`), ngữ pháp ở dòng 18–26 | Parser đệ quy cho đáp số dạng số nguyên, thập phân, `\frac`, `\sqrt`, đưa về dạng chuẩn duy nhất k·√n (k là phân số tối giản `BigInteger`, n không chứa thừa số chính phương). Phép chia √m/√n được khử căn ở mẫu bằng gcd. Nhờ vậy hai đáp số viết khác nhau (`\frac{\sqrt{8}}{2}` và `√2`) được so bằng nhau một cách chính xác, không qua số thực dấu phẩy động. |
| [LatexValidationRules.java:84-170](../../back_end/src/main/java/com/datn/mcq/common/util/LatexValidationRules.java#L84-L170), [:259](../../back_end/src/main/java/com/datn/mcq/common/util/LatexValidationRules.java#L259), [:360](../../back_end/src/main/java/com/datn/mcq/common/util/LatexValidationRules.java#L360) | Bộ quét từ vựng một lượt, dùng ngăn xếp khung để ghép `\left`/`\right`, `\begin`/`\end` và ngoặc nhọn; kiểm số đối số của `\frac`/`\sqrt`. Lệnh lạ được gợi ý tên đúng nếu cách 1 lần sửa. Tự ghi rõ giới hạn: không phải parser LaTeX đầy đủ. |
| [SimpleJson.java:146-150](../../back_end/src/main/java/com/datn/mcq/common/util/SimpleJson.java#L146-L150), [:231](../../back_end/src/main/java/com/datn/mcq/common/util/SimpleJson.java#L231) | Parser JSON viết tay theo RFC 8259, từ chối khóa trùng ở mọi độ sâu và escape không hợp lệ, hai lỗi thật của LLM (L-18, L-20). Kỹ thuật chuẩn (đệ quy xuống), giá trị nằm ở việc chặt hơn thư viện thông thường. |
| [LlmOutputRepair.java:63](../../back_end/src/main/java/com/datn/mcq/generation/parser/LlmOutputRepair.java#L63) (`repairLatexEscapes`), [:137-170](../../back_end/src/main/java/com/datn/mcq/generation/parser/LlmOutputRepair.java#L137-L170) | Máy trạng thái chỉ quét bên trong chuỗi JSON, phân biệt `\frac` (LaTeX) với `\f` (escape hợp lệ), và `\neq` với xuống dòng `\n` theo danh sách lệnh. Mỗi lần sửa ghi cảnh báo. Phần tự đóng `$` dựa trên heuristic `looksLikeMath`, là điểm yếu có ca biên. |
| [McqJsonParser.java:96](../../back_end/src/main/java/com/datn/mcq/generation/parser/McqJsonParser.java#L96) | Đổi `\n` dạng chữ thành xuống dòng nhưng chỉ ở ngoài công thức, và xử lý chữ tiếng Việt dạng NFD đứng sau (`\n` + "nếu" không bị đọc thành lệnh `\ne`). |
| [DedupChecker.java:155-190](../../back_end/src/main/java/com/datn/mcq/qc/checker/DedupChecker.java#L155-L190), [:193](../../back_end/src/main/java/com/datn/mcq/qc/checker/DedupChecker.java#L193); [McqTextNormalizer.java:169](../../back_end/src/main/java/com/datn/mcq/qc/checker/McqTextNormalizer.java#L169), [:119](../../back_end/src/main/java/com/datn/mcq/qc/checker/McqTextNormalizer.java#L119), [:39](../../back_end/src/main/java/com/datn/mcq/qc/checker/McqTextNormalizer.java#L39) | Loại trùng nhiều tầng: (1) dấu vân tay sau chuẩn hóa (NFC, lệnh đồng nghĩa `\leq`=`\le`, sắp xếp phương án); (2) cosine 3-gram ký tự kèm chốt chặn số liệu và phủ định. Câu Đúng/Sai có thêm tầng (3): ma trận cosine 4×4 giữa các ý, duyệt 24 hoán vị tìm cách ghép một-một tốt nhất. Thuật toán cơ bản (n-gram), nhưng thiết kế theo cấu trúc câu toán. |
| [GenerationJobRepository.java:28-35](../../back_end/src/main/java/com/datn/mcq/generation/repository/GenerationJobRepository.java#L28-L35), [StaleJobRecovery.java:33](../../back_end/src/main/java/com/datn/mcq/generation/worker/StaleJobRecovery.java#L33) | Hàng đợi trên PostgreSQL với `FOR UPDATE SKIP LOCKED`, đổi trạng thái trong cùng transaction; job `running` quá hạn được trả về `pending` mà không tính lượt. Có test hai luồng tái hiện lỗi nhận trùng trước khi sửa. Mẫu thiết kế phổ biến, không mới. |
| [TextDistanceUtils.java](../../back_end/src/main/java/com/datn/mcq/common/util/TextDistanceUtils.java) | Levenshtein quy hoạch động hai hàng, dùng đo mức sửa của giáo viên. Bản giáo khoa; xếp D cho đúng loại, nhưng không phải điểm mạnh. |

### 1.4 Đoạn tiêu biểu của loại C (nghiệp vụ không tầm thường)

| Đoạn | Vì sao không phải CRUD |
|---|---|
| [QcStatusDecider.java:19-30](../../back_end/src/main/java/com/datn/mcq/qc/orchestrator/QcStatusDecider.java#L19-L30); [QcOrchestrator.java:86-270](../../back_end/src/main/java/com/datn/mcq/qc/orchestrator/QcOrchestrator.java#L86-L270) | Pipeline format → dedup → answer_check. Mỗi bước có trạng thái riêng (`NOT_RUN`, `ERROR`…), và lỗi một bước không làm sập bước khác. Trạng thái cuối do một bảng quyết định duy nhất: `qc_passed` chỉ khi cả ba bước đạt. Bản thân bảng chỉ là if-else 4 nhánh, nhưng nó mã hóa một định nghĩa nghiệp vụ đã chốt và có test cho mọi tổ hợp. |
| [ExamBuildService.java:236-292](../../back_end/src/main/java/com/datn/mcq/exam/service/ExamBuildService.java#L236-L292), [:316](../../back_end/src/main/java/com/datn/mcq/exam/service/ExamBuildService.java#L316); [ExamSlotRepository.java:17](../../back_end/src/main/java/com/datn/mcq/exam/repository/ExamSlotRepository.java#L17), [:30-48](../../back_end/src/main/java/com/datn/mcq/exam/repository/ExamSlotRepository.java#L30-L48) | Máy trạng thái của từng chỗ trong đề: lấy câu ngân hàng trước (SQL khớp môn, dạng, mức, chủ đề; ưu tiên câu chưa dùng), phần thiếu xếp hàng sinh. Đối soát dưới khóa `PESSIMISTIC_WRITE`: lệch Solver thì sinh lại 1 lần rồi dùng câu kèm cờ; trùng hoặc lỗi thì thử tối đa 3 lần, cuối cùng dùng câu dự phòng hoặc `failed`. |
| [GenerationJobPoller.java:88-130](../../back_end/src/main/java/com/datn/mcq/generation/worker/GenerationJobPoller.java#L88-L130); [GenerationJobProcessor.java:254-263](../../back_end/src/main/java/com/datn/mcq/generation/worker/GenerationJobProcessor.java#L254-L263) | Gọi LLM nằm ngoài transaction DB; lưu câu và QC trong các transaction ngắn riêng. Lỗi ở mọi giai đoạn đi cùng một đường thử lại có giãn cách. Chưa phân loại lỗi (xem Phần 2). |
| [TeacherReviewService.java:188-410](../../back_end/src/main/java/com/datn/mcq/review/service/TeacherReviewService.java#L188-L410), [:410](../../back_end/src/main/java/com/datn/mcq/review/service/TeacherReviewService.java#L410), [:1022](../../back_end/src/main/java/com/datn/mcq/review/service/TeacherReviewService.java#L1022) | Bất biến của chấm mù: giấu đáp án/QC theo `purpose`; câu bị loại khỏi chấm trả cùng một 404; từ chối trường của dạng câu khác; đo `edit_distance`; chuyển `in_bank`. **Hạ mức:** phần lớn 943 dòng là kiểm tra đầu vào (57 câu `throw`), và `submitReview` dài khoảng 220 dòng, nên đây là C ở mức thấp và có vấn đề về thiết kế lớp. |
| [FormatChecker.java](../../back_end/src/main/java/com/datn/mcq/qc/checker/FormatChecker.java) (ví dụ [:179](../../back_end/src/main/java/com/datn/mcq/qc/checker/FormatChecker.java#L179)); [ExamMarkdownBuilder.java:145](../../back_end/src/main/java/com/datn/mcq/exam/export/ExamMarkdownBuilder.java#L145); [TfLabelPattern.java:45](../../back_end/src/main/java/com/datn/mcq/llm/prompt/TfLabelPattern.java#L45), [:76](../../back_end/src/main/java/com/datn/mcq/llm/prompt/TfLabelPattern.java#L76); [QcRerunService.java:65-82](../../back_end/src/main/java/com/datn/mcq/generation/service/QcRerunService.java#L65-L82) | Quy tắc miền (mức lỗi theo trường, 4 ý cùng nhãn, thoát Markdown nhưng giữ công thức, mẫu nhãn tránh trùng, chỉ chạy lại QC sau lỗi hạ tầng). **Hạ mức:** đều là if-else quy tắc, đúng nghĩa C thấp. |

---

## Phần 2 – Phán quyết

### 2.1 "Chỉ là CRUD + gọi API" đúng bao nhiêu?

- **Đúng khoảng 60 % theo dòng code** (A 47 % + B 14 %). Các phần đúng là như vậy:
  - ngân hàng câu, danh mục, run thực nghiệm, ma trận đề (kiểm tra đầu vào rồi lưu), người dùng;
  - toàn bộ entity/DTO/controller;
  - `GenerationService` (gọi LLM rồi lưu);
  - `LlmClient` và các prompt (ghép chuỗi);
  - xuất Word là gọi pandoc.
- **Sai ở khoảng 40 % còn lại** (C 23 % + D 16 %). Đây là lớp kiểm soát chất lượng
  quanh LLM:
  - parser chặt và bộ sửa đầu ra có ghi vết;
  - kiểm tra LaTeX;
  - so đáp số chính xác bằng số học hữu tỉ có căn;
  - loại trùng nhiều tầng;
  - bộ giải độc lập (Solver);
  - bảng quyết định QC;
  - máy trạng thái tạo đề;
  - hàng đợi chịu lỗi.

  Khoảng 3/4 số test nằm ở các module chứa lớp này.
- **Kết luận phản biện:** không phải "chỉ CRUD + gọi API". Nhưng cũng **không có mô hình học máy, không có thuật toán
  mới**. Các kỹ thuật đều cổ điển (n-gram, Levenshtein, parser đệ quy, `SKIP LOCKED`). Điểm yếu lớn nhất không nằm ở code
  mà ở **thiếu số liệu đo**: chưa có thí nghiệm nào trong backend chứng minh các lớp kiểm soát này hiệu quả (xem 2.3).

### 2.2 Năm điểm mạnh nhất có thật

| # | Điểm mạnh | Bằng chứng |
|---|---|---|
| 1 | So đáp số Trả lời ngắn chính xác theo giá trị, có ngữ pháp và dạng chuẩn duy nhất | `ShortAnswerValue.java` (296 dòng), `ShortAnswerValueTest` (tham số hóa); được dùng ở Solver và dedup (`AnswerSolverChecker.java:183`, `DedupChecker.answerKey`) |
| 2 | Pipeline QC có định nghĩa "đạt" duy nhất, bộ giải độc lập không thấy đáp án, chấm từng ý Đúng/Sai, phân biệt lỗi hạ tầng với kết quả | `QcStatusDecider.java:19-30`, `QcOrchestrator.java:86-270`, `AnswerSolverChecker.java:108-153`; `QcStatusDeciderTest`, `QcOrchestratorStatusTest` (24 test) |
| 3 | Loại trùng theo cấu trúc câu toán: vân tay, cosine 3-gram, chốt chặn số liệu và phủ định, ghép ý TF một-một | `DedupChecker.java:155-215`, `McqTextNormalizer.java`; `DedupCheckerTrueFalseTest` dùng cặp câu trùng từ dữ liệu thật |
| 4 | Xử lý đầu ra LLM có kiểm soát: parser RFC chặt, kiểm LaTeX bằng ngăn xếp, sửa hẹp có cảnh báo, chỉ sửa ở run production | `SimpleJson.java`, `LatexValidationRules.java`, `LlmOutputRepair.java`; `LlmOutputRepairTest` dùng fixture câu thật id 20, 24 |
| 5 | Hàng đợi và tạo đề chịu lỗi, có kiểm chứng đồng thời | `GenerationJobRepository.java:28-49` (`SKIP LOCKED`, reset job kẹt), `ExamBuildService.java:236-292` (khóa bi quan); `GenerationJobQueueTest` (2 luồng × 20 job), `StaleJobSchedulerTest` |

Điểm cộng về quy trình kỹ thuật (không tính vào độ sâu thuật toán):
- 10 migration, có test nâng cấp schema giữ dữ liệu (`V4UpgradeFromV3Test` … `V9UpgradeFromV8Test`) và ràng buộc CHECK/khóa ngoại kép ở DB;
- guard chặn test chạm DB thật hay gọi Gemini (`TestSafetyConfiguration`);
- tỷ lệ code test/code chính khoảng 0,88.

### 2.3 Năm điểm yếu nhất về độ sâu

| # | Điểm yếu | Bằng chứng |
|---|---|---|
| 1 | **Ngưỡng loại trùng chưa được hiệu chỉnh.** Hai bảng cho tập cặp gán nhãn (`dedup_gold_pair`, `dedup_gold_label`) đã có trong schema nhưng không có service hay API nào dùng. | `DedupChecker.java:43` (`PROVISIONAL_THRESHOLD = 0.90`); `goldset/` chỉ có entity + repository |
| 2 | **Solver không độc lập với bộ sinh**: mặc định cùng một model, nên lỗi có thể tương quan. Độ chính xác của Solver chưa được đo trong backend; pilot ngoài backend rất nhỏ (20 câu, 8 câu lỗi parse, đánh giá được 12 câu). | `application.yaml:37-38`; `llm-probe/m35_lite/solver_m35_lite.txt` |
| 3 | **Độ khó không được đánh giá**: `llm_difficulty` chỉ là mức mục tiêu truyền vào, cột `difficulty_judge_model` không bao giờ được ghi; không có kiểm tra câu có đúng mức nhận thức của ô ma trận. | `GenerationService.java:494`; `GeneratedQuestion.java:65` (chỉ có setter, không nơi gọi) |
| 4 | **Không kiểm đáp án bằng tính toán**: tính đúng của đáp án MCQ chỉ dựa vào một LLM khác; phương án nhiễu tương đương đáp án và đáp án lộ trong đề chỉ chặn bằng prompt hoặc chưa chặn. | SymPy chỉ được nhắc trong docstring của `llm-probe/solver_probe.py`, không có dòng code SymPy nào; `MatrixPromptDraftV0.MCQ_DISTRACTOR_RULE` |
| 5 | **So khớp ngân hàng và loại trùng chỉ theo chuỗi**: câu ngân hàng khớp ô bằng `lower(btrim(topic))` chính xác; dedup không dùng embedding dù đã có extension pgvector (cột `question_embedding.embedding` vẫn là `BYTEA`, "chỉ để import ERD"). | `ExamSlotRepository.java:30-48`; `V1__init_schema.sql:92-98`; comment trong `qc/package-info.java` ("không embedding") |

Điểm yếu khác (thiết kế, không phải độ sâu):
- hàng đợi một luồng, không phân loại lỗi Gemini (L-36);
- `TeacherReviewService` 943 dòng;
- không có xác thực: frontend có trang đăng nhập nhưng `front_end/src/features/auth/services/authApi.ts` là file rỗng;
- `cost_usd` luôn 0.

### 2.4 Chỉ có trong tài liệu hoặc schema, chưa có trong code (không được trình bày như đã làm)

| Ý định | Hiện trạng trong code |
|---|---|
| Loại trùng bằng embedding / pgvector | Extension `vector` bật, nhưng cột là `BYTEA`, không có code tạo hay so embedding |
| Tập vàng để hiệu chỉnh dedup | Bảng `dedup_gold_*` có, không có service/API |
| "Chấm độ khó" (README) | Không có bước chấm; `llm_difficulty` = mức mục tiêu |
| Kiểm đáp án bằng SymPy | Chỉ trong docstring của script thử nghiệm |
| Thí nghiệm so sánh C1/C2/C3 | Code còn (`experiment`, form chấm mù, `few-shot-structured`), nhưng đã bỏ theo quyết định 23/10; không có kết quả |
| Phân công chấm (`label_assignment`), duyệt hai bước | Có entity/bảng, không dùng; duyệt hai bước chưa làm |
| Sửa câu trong đề (`exam_slot_action.action = 'edit'`) | Chỉ có hằng số; chưa có endpoint |
| Trộn mã đề, đề dự phòng, xuất PDF | Chưa có (README ghi "Chưa có") |
| Đăng nhập, phân quyền | Không có Spring Security; `authApi.ts` rỗng |
| Chi phí mỗi lần gọi LLM | `cost_usd` luôn 0 |
| Endpoint chạy lại QC | Có `QcRerunService`, không có endpoint |
| Chương thử nghiệm mới (4 thí nghiệm) | Kế hoạch ngày 23/10; chưa có code đo hay số liệu |

---

## Phần 3 – Đề xuất tính năng AI/kỹ thuật bổ sung

Ước lượng cho 1 người, đã quen code. "Rủi ro" là khả năng không xong trong thời gian ước lượng.

### 3.1 Đánh giá từng ứng viên

| # | Ứng viên | (a) Giá trị cho giáo viên | (b) Độ sâu khi bảo vệ | (c) Ngày | (d) Rủi ro | (e) Ra bảng số liệu? | (f) Chạm vào |
|---|---|---|---|---:|---|---|---|
| 1 | Kiểm đáp án bằng SymPy (Toán 8: rút gọn, phân tích nhân tử, giải PT/BPT bậc nhất, tính giá trị) | Cao: bắt câu sai khóa mà không cần LLM | Cao: kiểm chứng ký hiệu, độc lập hẳn với LLM | 3–4 | TB–Cao: chuyển LaTeX sang SymPy dễ vỡ, độ phủ giới hạn | Có: độ phủ, tỷ lệ bắt lỗi, so với Solver | sidecar Python gọi bằng `ProcessBuilder` (giống `PandocRunner`), `qc/checker` mới, `QcOrchestrator`, `QcStatusDecider` |
| 2 | Dedup theo nghĩa bằng embedding, so với 3-gram trên tập cặp gán nhãn | TB: ít câu trùng lọt, ít loại nhầm | Cao: so sánh định lượng P/R/F1, quét ngưỡng | 2,5 (gồm 1 ngày gán nhãn) | Thấp–TB: cần migration đổi cột sang `vector(N)` và quota embedding | Có, rất rõ | `DedupChecker`, `DedupService`, `goldset/` (bảng đã có), `QuestionEmbedding`, `LlmClient` (embedding), V11 |
| 3 | Solver nhiều model khác hãng, bất đồng thì gắn cờ | TB | TB: ensemble đơn giản | 2 | TB: cần key hãng khác, có thể tốn phí | Có: tỷ lệ bắt lỗi 1 vs 2 Solver | `llm/client` (client mới), `AnswerSolverChecker`, `QcOrchestrator`, `QcSummaryService` |
| 4 | Phát hiện phương án nhiễu tương đương đáp án và đáp án lộ trong đề | Cao: đúng lỗi đã gặp trong đề thật (L-26, L-27) | Cao nếu làm bằng SymPy (`simplify(a − b) == 0`) | +1,5 (dựa trên #1) | TB: phụ thuộc độ phủ của #1 | Có: số ca phát hiện trên dữ liệu thật và dữ liệu tiêm lỗi | như #1, cộng `FormatChecker` (cảnh báo) |
| 5 | Kiểm câu đúng mức nhận thức của ô (bộ phân loại hoặc LLM-judge, có đo độ chính xác) | Cao: ma trận đề bắt buộc mức | Cao nếu có nhãn chuẩn | 3–4 + công gán nhãn | Cao: nhãn mức chủ quan, cần giáo viên, dữ liệu ít | Có, nhưng cần nhãn của chuyên gia (độ chính xác, kappa) | `qc/checker` mới, `llm/prompt`, `GeneratedQuestion.difficultyJudgeModel` (cột đã có) |
| 6 | Hình học phẳng: LLM sinh tọa độ, hệ thống vẽ SVG và kiểm khớp số đo | Cao cho chủ đề Tứ giác | Cao | 5–7 | Cao | Yếu | prompt mới, bộ vẽ SVG, xuất Word ảnh, `ExamMarkdownBuilder` |
| 7 | Biểu đồ thống kê từ bảng số liệu | TB | Thấp–TB | 2–3 | TB | Yếu | prompt mới, bộ vẽ, xuất Word |
| 8 | Độ bền hàng đợi: phân loại lỗi, ngắt mạch, nhiều worker có giới hạn tốc độ, thử tải bằng LLM giả lập | TB: nhiều giáo viên cùng lúc | TB–Cao: đồng thời, đo được | 2,5 | Thấp: hạ tầng test sẵn | Có: thông lượng, thời gian chờ p95, thời gian hồi phục khi Gemini sập | `LlmClient.errorForStatus`, `GenerationJobProcessor.recordJobFailure`, `generation/worker`, cấu hình pool |
| 9 | Học từ thao tác giáo viên (câu Giữ/Đổi làm ví dụ cho lần sinh sau) | TB–Cao | TB | 2 | TB: ít dữ liệu, khó đo hiệu quả, dễ lẫn thiên lệch "đã thấy đáp án" | Yếu | `PreviousQuestionLookup`, `MatrixPromptDraftV0`, `exam_slot_action` |
| 10 | *(đề xuất thêm)* Bộ tiêm lỗi có kiểm soát cho QC: từ câu đã kiểm đúng, tạo bản sai khóa, nhiễu tương đương, sai số liệu, rồi đo tỷ lệ bắt của từng tầng | Gián tiếp | Cao: phương pháp đánh giá kiểu mutation testing | 1 | Thấp: pilot sẵn ở `llm-probe/solver_probe.py` | Có: ma trận nhầm lẫn từng tầng | script thử nghiệm, `AnswerSolverChecker`, `DedupChecker` (không đổi code chính) |
| 11 | *(đề xuất thêm)* Phát lại ngoại tuyến `raw_llm_output` đã lưu qua parser chặt và chế độ sửa: đo tỷ lệ câu cứu được và tỷ lệ sửa sai | Gián tiếp | TB–Cao: chứng minh bộ sửa có ích bằng số, không tốn lượt gọi LLM | 0,5–1 | Thấp | Có | `McqJsonParser`/`SaJsonParser`/`TfJsonParser`, `LlmOutputRepair`, `FormatChecker` (chỉ đọc dữ liệu) |
| 12 | *(đề xuất thêm)* Ghi chi phí thật (token × đơn giá) và thời gian cho mỗi đề | TB: biết giá mỗi đề | Thấp | 0,5 | Thấp | Có: chi phí và thời gian mỗi đề, dùng cho so sánh với prompt cộng đồng | `LlmClient` (`costUsd`), báo cáo theo `exam` |

### 3.2 Xếp hạng theo giá trị trên công sức (ngân sách 10 ngày)

| Hạng | Ứng viên | Ngày | Cộng dồn | Quyết định | Lý do |
|---:|---|---:|---:|---|---|
| 1 | #11 Phát lại đầu ra LLM đã lưu | 1 | 1 | **LÀM** | Rẻ nhất, cho ngay số liệu về lớp xử lý đầu ra, không tốn quota |
| 2 | #10 Bộ tiêm lỗi + đo Solver | 1 | 2 | **LÀM** | Chính là thí nghiệm (1) của chương thử nghiệm mới; pilot có sẵn |
| 3 | #2 Hiệu chỉnh dedup trên tập gán nhãn, so 3-gram với embedding | 2,5 | 4,5 | **LÀM** | Bảng đã có trong schema; vá điểm yếu "ngưỡng tạm 0,90"; ra P/R/F1 như đồ án 5 |
| 4 | #1 + #4 SymPy: kiểm đáp án, nhiễu tương đương, đáp án lộ | 4 + 1,5 | 10 | **LÀM** (thu hẹp còn 3–4 dạng) | Giá trị cao nhất cho giáo viên và độ sâu cao nhất; vá điểm yếu 2 và 4. Tốn nhất nên làm sau ba mục rẻ |
| 5 | #12 Chi phí mỗi đề | 0,5 | (vượt) | **LÀM NẾU KỊP** | Cần cho thí nghiệm (3) so với prompt cộng đồng; rất rẻ |
| 6 | #8 Độ bền hàng đợi + thử tải | 2,5 | (vượt) | **LÀM NẾU KỊP** (tối thiểu: phân loại lỗi, 1 ngày) | Vá L-36; ra số liệu thời gian chờ |
| 7 | #3 Solver nhiều hãng | 2 | (vượt) | **LÀM NẾU KỊP** | SymPy đã giải quyết tính độc lập rẻ hơn |
| 8 | #5 Kiểm mức nhận thức | 4+ | (vượt) | **LÀM NẾU KỊP**, chỉ khi có giáo viên gán nhãn | Giá trị cao nhưng phụ thuộc nhãn chuyên gia |
| 9 | #9 Học từ thao tác giáo viên | 2 | – | **BỎ** (hướng phát triển) | Ít dữ liệu, khó đo, dễ lẫn thiên lệch |
| 10 | #7 Biểu đồ thống kê | 2–3 | – | **BỎ** | Ít độ sâu, khó đo |
| 11 | #6 Hình học SVG | 5–7 | – | **BỎ** (hướng phát triển) | Rủi ro cao, vượt ngân sách |

Nếu SymPy không kịp, thu hẹp #1 xuống còn 2 dạng (giải phương trình bậc nhất, tính giá trị biểu thức) để vẫn có số
liệu. Dời #4 thì giữ nguyên #10 và #11.

---

## Phần 4 – Trả lời mẫu cho câu hỏi "Khác gì CRUD + gọi ChatGPT?"

1. **"Chúng em không tin đầu ra của LLM."** Phản hồi được đọc bằng parser JSON chặt, từ chối khóa trùng và escape sai,
   hai lỗi gặp thật ở dữ liệu sinh. Bộ sửa chỉ sửa các ca chắc chắn (ví dụ `\circ` một dấu gạch) và ghi cảnh báo cho mỗi
   lần sửa. Bằng chứng: `common/util/SimpleJson.java`, `generation/parser/LlmOutputRepair.java`, `LlmOutputRepairTest`.
2. **"Đáp số được so theo giá trị toán học, không theo chuỗi."** `ShortAnswerValue` phân tích đáp số thành dạng chuẩn
   k·√n bằng số nguyên lớn, nên `\frac{\sqrt{8}}{2}` và `√2` được coi là bằng nhau mà không dùng số thực. Đây là điều gọi
   ChatGPT trực tiếp không làm được. Bằng chứng: `common/util/ShortAnswerValue.java`, `ShortAnswerValueTest`.
3. **"Mỗi câu được một model khác giải lại mà không thấy đáp án."** Solver chỉ nhận đề và phương án. Câu Đúng/Sai được
   chấm từng ý, có lựa chọn "không kết luận được". Lệch thì sinh lại, vẫn lệch thì đưa vào đề kèm cờ "cần xem kỹ". Bằng
   chứng: `qc/checker/AnswerSolverChecker.java`, `ExamBuildService.reconcileSlot`. (Giới hạn: Solver hiện cùng model
   với bộ sinh; chương thử nghiệm sẽ đo nó.)
4. **"'Qua kiểm tra' có định nghĩa chính xác."** Một câu chỉ `qc_passed` khi đúng định dạng, đã kiểm trùng và không
   trùng, và Solver chạy thành công với kết quả khớp. Định nghĩa nằm trong một bảng quyết định có test cho mọi tổ hợp.
   Bằng chứng: `qc/orchestrator/QcStatusDecider.java`, `QcStatusDeciderTest`.
5. **"Loại trùng theo cấu trúc của câu toán."** Hai câu chỉ đổi số liệu không bị coi là trùng (có chốt chặn số liệu),
   còn câu Đúng/Sai chỉ đảo thứ tự ý vẫn bị bắt (ghép 4 ý một-một qua 24 hoán vị). Bằng chứng:
   `qc/checker/DedupChecker.java`, `DedupCheckerTrueFalseTest` (cặp câu lấy từ dữ liệu thật).
6. **"Hệ thống quyết định những gì LLM hay làm lệch."** Ví dụ mẫu nhãn Đúng/Sai do hệ thống chọn ngẫu nhiên và kiểm lại
   sau khi sinh, vì để LLM tự chọn thì lần nào cũng ra T,T,F,F. Bằng chứng: `llm/prompt/TfLabelPattern.java`,
   `ExamBuildTest.moiCauTfCoMauNhanBatBuocKhacCauTfGanNhat`.
7. **"Tạo đề là một quy trình chịu lỗi, không phải một lời gọi."** Mỗi chỗ trong đề là một máy trạng thái (ngân hàng →
   sinh → kiểm → sinh lại → dự phòng). Hàng đợi dùng `FOR UPDATE SKIP LOCKED` và tự trả job kẹt; đã kiểm bằng test hai
   luồng. Bằng chứng: `exam/service/ExamBuildService.java`, `generation/repository/GenerationJobRepository.java`,
   `GenerationJobQueueTest`.

Không nên nói: "hệ thống dùng embedding", "đã đánh giá độ khó", "đã kiểm đáp án bằng SymPy", "đã so sánh 3 chiến lược
prompt". Cả bốn đều chưa có trong code (mục 2.4).

---

## Phần 5 – So với mặt bằng đồ án cùng giảng viên hướng dẫn

Dữ liệu 6 đồ án khóa trước do nhóm cung cấp (không có trong repo). Không suy diễn điểm số. Mức Thấp/TB/Cao là đánh
giá tương đối giữa 7 đồ án theo mô tả, không phải thang chuẩn.

### 5.1 Đặt lên cùng thang

| Đồ án | Người | Độ rộng chức năng | Độ sâu kỹ thuật | Thử nghiệm / so sánh định lượng |
|---|---:|---|---|---|
| 1. Game STEM | 2 | TB | TB (BKT) | Thấp: kịch bản minh họa, không thống kê |
| 2. Học tập trực tuyến | 3 | TB–Cao | Thấp | Không có |
| 3. Tuyển dụng | 2 | TB | Thấp (có CI/CD) | Không có |
| 4. Học trực tuyến + Stripe | 3 | Cao (thanh toán) | Thấp | Không có |
| 5. Du lịch (Android) | 2 | TB | Cao (4 thuật toán gợi ý + RRF + tối ưu lộ trình) | **Cao**: P@K/R@K/F1, thời gian chạy, MovieLens 100K |
| 6. TMĐT khóa tiếng Anh | 4 | Cao | TB–Cao (dự đoán bỏ học) | **Cao**: nhiều mô hình, Group K-Fold |
| **Đồ án này** | 2 | **TB**. Đã có: 24 endpoint; 3 dạng câu; QC; chấm mù; ngân hàng; ma trận; tạo đề một lần bấm; Giữ/Đổi; xuất Word. Frontend 17 trang, chưa xác minh mức độ nối API (trang đăng nhập chưa nối). Chưa có: đăng nhập/phân quyền, thanh toán, thống kê. | **TB–Cao**: 16 % code là thuật toán/kỹ thuật, 23 % nghiệp vụ không tầm thường (Phần 1); không có mô hình học máy | **Hiện tại: Thấp.** 468 test tự động (kiểm tính đúng của code, không phải thí nghiệm đánh giá) và 1 pilot Solver 20 câu ngoài backend. **Dự kiến:** 4 thí nghiệm của chương mới (Solver bắt lỗi, ngưỡng dedup, so với prompt cộng đồng, khảo sát giáo viên) |

Kết luận: về độ sâu, đồ án này hơn nhóm 1–4 và ngang hoặc gần nhóm 5–6. Về thử nghiệm định lượng, hiện **kém** nhóm 5–6,
và đây là khoảng cách lớn nhất.

### 5.2 Nhóm 2 người: tối thiểu về độ rộng, phần bù bằng độ sâu

Tối thiểu nên có để không bị chê "thiếu chức năng":
1. **Đăng nhập và 2 vai trò** (giáo viên, quản trị/nghiên cứu). Bảng `users.role` đã có CHECK `teacher`/`researcher`;
   frontend đã có trang nhưng chưa nối. Khoảng 1,5–2 ngày.
2. **Sửa câu trong đề** (hằng số `edit` và cột `edit_distance` đã có). Đây là thao tác giáo viên mong đợi nhất sau Giữ/Đổi.
3. **Màn thống kê đơn giản**: số đề, số câu, tỷ lệ qua QC, tỷ lệ Giữ/Sửa/Đổi lấy từ `exam_slot_action`, chi phí mỗi đề
   (ứng viên #12). Màn này cũng là nơi trình bày số liệu.
4. **Luồng đầu-cuối chạy được khi demo**: ma trận → đề → xem → Giữ/Đổi → Word. Luồng này đã có; cần ổn định.

Không nên cạnh tranh về thanh toán hay nhiều vai trò. Phần bù bằng độ sâu là lớp kiểm soát chất lượng (Phần 1, mục C/D),
**với điều kiện có số liệu chứng minh** (5.3). Có độ sâu mà không có số liệu thì hội đồng khó phân biệt với "nhiều if-else".

### 5.3 Để đạt mức đồ án 5 và 6

Hai đồ án đó có cùng một mẫu: một mô-đun thông minh, một tập dữ liệu có nhãn chuẩn, nhiều phương án so với nhau theo
chỉ số chuẩn. Đồ án này cần đúng các thí nghiệm sau:

| Thí nghiệm | So sánh | Chỉ số | Dữ liệu cần | Ứng viên Phần 3 |
|---|---|---|---|---|
| T1. Bắt lỗi đáp án | Solver LLM vs SymPy vs kết hợp (vs Solver hãng khác nếu kịp) | Recall bắt lỗi, tỷ lệ báo động giả, độ phủ của SymPy | Câu đã kiểm đúng + bản tiêm lỗi (≥ 50 mỗi loại để có khoảng tin cậy) | #10, #1, #4, (#3) |
| T2. Ngưỡng loại trùng | Vân tay vs 3-gram (quét ngưỡng) vs embedding | Precision/Recall/F1, đường P–R, ngưỡng tối ưu | ~150 cặp tự gán nhãn, lưu vào `dedup_gold_*` | #2 |
| T3. Hệ thống vs cách giáo viên đang dùng | Pipeline đầy đủ vs prompt mẫu cộng đồng trên Gemini, cùng ma trận | Tỷ lệ câu dùng được (đúng định dạng, đúng đáp án theo kiểm tra của nhóm/SymPy, không trùng), chi phí và thời gian mỗi đề | Cùng một bộ ma trận, đủ số câu mỗi bên | #11, #12, #1 |
| T4. Khảo sát giáo viên | Định tính, thang Likert | Mức hài lòng, tỷ lệ Giữ/Sửa/Đổi | Giáo viên dùng thử chế độ giáo viên | Ghi rõ giới hạn: giáo viên đã thấy đáp án |

T1 và T2 là hai thí nghiệm tương đương "so sánh nhiều thuật toán theo chỉ số chuẩn" của đồ án 5–6. T3 trả lời trực tiếp
câu hỏi "khác gì gọi ChatGPT" bằng số liệu. T4 chỉ bổ trợ, không thay được T1–T3.
