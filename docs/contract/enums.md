# Hợp đồng enum giữa backend và frontend

Nguồn duy nhất: các CHECK trong migration Flyway (`V1`, `V2`, ...). Frontend và backend dùng **đúng chuỗi trong cột "Giá trị trong DB"**; cột "Hiển thị / ý nghĩa" chỉ dành cho giao diện. Muốn đổi giá trị thì viết migration mới, rồi cập nhật file này trong cùng một commit.

Các cột có cùng bộ giá trị được gom thành một enum dùng chung (48 cột gom thành 21 enum, tính đến V4). Mục 1 cho biết cột nào dùng enum nào.

Java: đặt tên hằng kiểu `ZERO_SHOT`, `FEW_SHOT_STRUCTURED`... và có bước chuyển đổi hai chiều (`AttributeConverter`) với chuỗi trong DB. Các giá trị DB có dấu gạch ngang (`zero-shot`, `few-shot`, `few-shot-structured`) và các giá trị viết thường (`multiple`, `none`, `easy`...) đều phải qua converter, không dùng `@Enumerated` mặc định.

## 1. Cột nào dùng enum nào

| Enum (mục bên dưới) | Cột dùng |
|---|---|
| `Strategy` | `experiment_run.strategy` |
| `Purpose` | `experiment_run.purpose`, `teacher_review.review_purpose` |
| `Difficulty` | `generated_question.llm_difficulty`, `generated_question.final_difficulty`, `teacher_review.difficulty_label`, `bank_item.difficulty`, `generation_job.target_difficulty` (V2); V4: `generated_question_statement.llm_difficulty`, `generated_question_statement.final_difficulty`, `teacher_review_statement.difficulty_label`, `bank_item_statement.difficulty` |
| `Option` | `generated_question.correct_option`, `teacher_review.final_option`, `bank_item.correct_option` |
| `AnswerChoice` | `teacher_review.reviewer_answer`, `quality_check_result.judge_answer` |
| `QuestionStatus` | `generated_question.status` |
| `CheckType` | `quality_check_result.check_type` |
| `ReviewDecision` | `teacher_review.decision` |
| `ReasonCode` | `teacher_review_reason.reason_code` |
| `SamplingStratum` | `dedup_gold_pair.sampling_stratum` |
| `GoldSplit` | `dedup_gold_pair.split` |
| `ExamStatus` | `exam.status` |
| `JobStatus` | `generation_job.status` |
| `SourceType` | `question_template.source_type` |
| `UserRole` | `users.role` |
| `QuestionType` (V4) | `question_type` của `experiment_run`, `generated_question`, `generation_job`, `teacher_review`, `quality_check_result`, `bank_item`, `generated_question_statement`, `teacher_review_statement`, `answer_check_statement`, `bank_item_statement` |
| `StatementKey` (V4) | `position` của `generated_question_statement`, `teacher_review_statement`, `answer_check_statement`, `bank_item_statement` |
| `TruthValue` (V4) | `generated_question_statement.llm_label`, `teacher_review_statement.final_label`, `bank_item_statement.label` |
| `TruthChoice` (V4) | `teacher_review_statement.reviewer_label`, `answer_check_statement.judge_label` |
| `SaAnswerKind` (V4) | `teacher_review.reviewer_sa_kind` |
| `SaJudgeKind` (V4) | `quality_check_result.judge_sa_kind` |

## 2. Giá trị của từng enum

### `Strategy`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `zero-shot` | C1: không ví dụ |
| `few-shot` | C2: kèm 3 ví dụ |
| `few-shot-structured` | C3: kèm ví dụ và đầu ra có cấu trúc |

### `Purpose`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `experiment` | Thực nghiệm. Với `experiment_run`: lần chạy phục vụ thực nghiệm. Với `teacher_review`: gán nhãn mù |
| `production` | Dùng thật. Với `experiment_run`: lần chạy sinh câu cho ngân hàng. Với `teacher_review`: duyệt thật |

### `Difficulty`

*Lưu ý: Dữ liệu DB giữ nguyên 3 bậc để khớp DB V1. Frontend hiển thị nhãn theo chuẩn Ma trận đề kiểm tra cấp 2.*

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `easy` | Biết (Nhận biết) |
| `medium` | Hiểu (Thông hiểu) |
| `hard` | Vận dụng |

### `Option`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `A` | Phương án A |
| `B` | Phương án B |
| `C` | Phương án C |
| `D` | Phương án D |

### `AnswerChoice`

Dùng cho đáp án mà một người chấm hoặc một model chấm đưa ra (không phải đáp án đúng cuối cùng).

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `A` | Phương án A |
| `B` | Phương án B |
| `C` | Phương án C |
| `D` | Phương án D |
| `multiple` | Có hơn 1 đáp án đúng |
| `none` | Không có đáp án đúng |

### `QuestionStatus`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `generated` | Vừa sinh và parse xong, chưa qua QC |
| `format_failed` | Không đạt kiểm tra định dạng |
| `duplicate` | Bị bước dedup loại; trạng thái cuối, không vào vòng chấm |
| `qc_passed` | Qua QC tự động, chờ giáo viên duyệt |
| `rejected` | Giáo viên từ chối (luồng duyệt thật) |
| `in_bank` | Đã duyệt thật, đã sao chép sang `bank_item` |

### `CheckType`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `format` | Kiểm tra định dạng |
| `dedup` | Kiểm tra trùng lặp |
| `answer_check` | Model thứ hai tự giải câu hỏi, ghi `judge_answer` và `judge_model` |

### `ReviewDecision`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `approved_as_is` | Dùng nguyên trạng |
| `approved_with_edit` | Dùng được nếu sửa nhẹ |
| `rejected` | Từ chối |

### `ReasonCode`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `content_inaccurate` | Câu hỏi chứa thông tin không chính xác |
| `content_off_curriculum` | Câu hỏi không phù hợp với kiến thức của môn học |
| `content_insufficient_info` | Câu hỏi thiếu thông tin để trả lời |
| `content_unclear` | Câu hỏi diễn đạt không rõ ràng |
| `options_out_of_scope` | Phương án không cùng phạm vi/nội dung với câu hỏi |
| `options_indistinct` | Sự khác biệt giữa các phương án không rõ ràng |
| `presentation_question_hard_to_read` | Hình thức trình bày câu hỏi gây khó khăn cho đọc hiểu |
| `presentation_options_hard_to_read` | Hình thức trình bày đáp án gây khó khăn cho đọc hiểu |
| `presentation_missing_question` | Thiếu nội dung câu hỏi |
| `presentation_grammar` | Lỗi ngữ pháp |
| `presentation_formula_symbol_error` | Công thức/kí hiệu bị lỗi |
| `answer_multiple_correct` | Có hơn 1 đáp án đúng |
| `answer_none_correct` | Không có đáp án đúng |
| `statement_undecidable` | (V4, chỉ câu Đúng/Sai) Có ý không kết luận được Đúng hay Sai |
| `answer_ambiguous` | (V4, chỉ câu Trả lời ngắn) Đề có nhiều cách hiểu cho ra đáp số khác nhau |
| `other` | Khác (bắt buộc nhập text) |

Mã dùng theo dạng câu (service kiểm, DB không chặn): `options_out_of_scope`, `options_indistinct`, `presentation_options_hard_to_read`, `answer_multiple_correct`, `answer_none_correct` chỉ cho `mcq`; `statement_undecidable` chỉ cho `tf`; `answer_ambiguous` chỉ cho `sa`; các mã còn lại dùng cho mọi dạng.

### `SamplingStratum`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `same_run` | Cùng template, cùng lần chạy |
| `cross_run` | Cùng template, khác lần chạy |
| `cross_template` | Khác template (ứng viên tìm bằng mô hình embedding khác) |
| `random` | Ngẫu nhiên |

### `GoldSplit`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `dev` | Tập chỉnh ngưỡng τ |
| `test` | Tập kiểm tra |

### `ExamStatus`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `draft` | Vừa sinh, chờ giáo viên kiểm tra |
| `confirmed` | Giáo viên xác nhận sử dụng |

### `JobStatus`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `pending` | Đang chờ chạy (hoặc chờ thử lại tại `next_run_at`) |
| `running` | Đang gọi LLM |
| `done` | Hoàn tất, đã có `question_id` |
| `failed` | Thất bại hẳn sau khi hết số lần thử, lỗi ghi ở `last_error` |

### `SourceType`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `text` | Câu mẫu dạng văn bản |
| `pdf` | Câu mẫu lấy từ file PDF |
| `image` | Câu mẫu lấy từ ảnh |

### `UserRole`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `teacher` | Giáo viên |
| `researcher` | Người nghiên cứu (nhóm đồ án) |

### `QuestionType` (V4)

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `mcq` | Trắc nghiệm 4 phương án (mọi dữ liệu trước V4) |
| `tf` | Đúng/Sai: một đề dẫn và đúng 4 ý a-d |
| `sa` | Trả lời ngắn: đề và một đáp số |

### `StatementKey` (V4)

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `a` | Ý a |
| `b` | Ý b |
| `c` | Ý c |
| `d` | Ý d |

### `TruthValue` (V4)

Nhãn Đúng/Sai của một ý: nhãn LLM, nhãn cuối của giáo viên, nhãn trong ngân hàng. Dùng `T`/`F`, không dùng `true`/`false`, để chuỗi `"false"` không bị hiểu là giá trị đúng ở frontend.

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `T` | Đúng |
| `F` | Sai |

### `TruthChoice` (V4)

Nhãn mà người chấm hoặc Solver đưa ra cho một ý (giống quan hệ giữa `Option` và `AnswerChoice`).

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `T` | Đúng |
| `F` | Sai |
| `undecidable` | Không kết luận được (ý mơ hồ, thiếu dữ kiện) |

### `SaAnswerKind` (V4)

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `number` | Giáo viên nhập một đáp số |
| `ambiguous` | Đề có nhiều cách hiểu cho ra đáp số khác nhau |

### `SaJudgeKind` (V4)

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `number` | Solver trả một đáp số phân tích được |
| `ambiguous` | Solver báo đề có nhiều cách hiểu cho ra đáp số khác nhau |
| `unparseable` | Solver trả chuỗi không phân tích được thành số, không so tự động được (tính INCONSISTENT) |

## 3. Quy tắc đi kèm (cần nhớ khi code, DB không tự đảm bảo hết)

- **Đáp án đúng luôn do giáo viên quyết.** API lấy câu để chấm không được trả `correct_option` về form, kể cả trong JSON ẩn. Backend tự so để ra `answer_correct`.
- **Cả `production` và `experiment`** (quyết định 21/10 mục 2): API chấm (danh sách, chi tiết, phản hồi sau khi nộp) không trả `explanation`, `llm_difficulty`, chiến lược, `status`, `latexValid`, `latexErrors` và toàn bộ kết quả QC, kể cả `judge_answer` và kết quả dedup. Danh sách không có tham số lọc theo trạng thái. Riêng phản hồi nộp `production` trả `questionStatus` và `bankItemId`, là kết quả hành động của chính giáo viên; `experiment` không trả `questionStatus`.
- `reviewer_answer` là `multiple` hoặc `none` thì không được chọn `approved_as_is`.
- Duyệt (`approved_as_is`, `approved_with_edit`) thì bắt buộc có `final_option` (A-D). `rejected` thì `final_option` phải trống và không lưu bản sửa.
- `rejected` thì có ít nhất một lý do trong `teacher_review_reason`. Mã `other` bắt buộc có `detail`.
- `edited = true` chỉ khi văn bản sau khi trim và chuẩn hóa xuống dòng thật sự khác bản gốc (`edit_distance > 0`). `edit_distance` là tổng Levenshtein trên `question_text` và 4 phương án, không tính lời giải. Bước chấm hiện tại không nhận sửa lời giải: request có `editedExplanation` khác null trả 400 với thông điệp trung tính, `teacher_review.edited_explanation` luôn NULL (quyết định 22/10).
- `judge_answer` và `judge_model` chỉ có giá trị khi `check_type = 'answer_check'`.
- Dedup (chốt 2026-10-21, không dùng embedding):
  - Thứ tự QC: `format` → `dedup` → `answer_check`. Solver chỉ được gọi khi dedup kết luận không trùng.
  - Trùng khi (a) cùng dấu vân tay sau chuẩn hóa (NFC, khoảng trắng, `\le`/`\leq`, `\ge`/`\geq`, `\ne`/`\neq` như nhau, tập 4 phương án đã sắp xếp; không gồm đáp án và lời giải), hoặc (b) `similarity >= τ` VÀ qua hai chốt chặn: số liệu và công thức trong thân câu giống nhau, hai câu không lệch nhau về phủ định. Phủ định = thân câu (phần ngoài công thức) chứa một cụm có ranh giới từ: `không`, `chưa`, `ngoại trừ`, `không phải`, `không đúng`, `không thuộc`, `không là`, `không thỏa mãn` (cả `không thoả mãn`), `không thể`, `phát biểu sai`, `nhận định sai`, `khẳng định sai`, `sai`; không phân biệt hoa thường.
  - `similarity_score`, `threshold_used`, `predicted_similarity` là cosine (`1 - cosine distance`) giữa vectơ đếm 3-gram ký tự của văn bản đã chuẩn hóa. Câu không trùng lưu điểm của ứng viên gần nhất đã qua hai chốt chặn, nên luôn `< τ`.
  - `τ = 0.90` là hằng số tạm, CHƯA hiệu chỉnh trên gold set; chưa có precision/recall.
  - Phạm vi so: experiment chỉ trong cùng run; production trong các run production cùng môn. Không bao giờ so chéo experiment và production. Ứng viên là câu `qc_passed`, `in_bank` và câu cùng run đã có dòng dedup `passed = true`.
  - Dedup báo trùng: dòng dedup `passed = false` kèm `compared_to_question_id`, trạng thái `duplicate`. Dedup lỗi hoặc thiếu phạm vi: không ghi dòng dedup, giữ `generated`.
- `duplicate` là trạng thái cuối: câu không vào vòng chấm (production và experiment), không được nộp đánh giá, không bị ghi đè trạng thái; bản ghi được giữ để truy vết và thống kê. Người chấm không được biết câu nào bị loại vì trùng.
- Vòng chấm (quyết định 21/10 mục 5 và quyết định 22/10): danh sách `production` chỉ gồm câu `qc_passed` (mọi lần nộp production đều chuyển câu sang `in_bank` hoặc `rejected`, nên đó là câu chưa được chấm). Danh sách `experiment` loại `duplicate` và `format_failed`, giữ câu `generated` (kể cả khi `answer_check` lệch hoặc không chạy) và `qc_passed`, không lọc theo kết quả `answer_check`. Chi tiết và nộp đánh giá cho câu `duplicate` hoặc `format_failed` trả 404 `Không tìm thấy câu hỏi`, giống hệt câu không tồn tại.
- Định dạng (việc 1 và 2 ngày 22/10): sau khi giải mã JSON, chuỗi `\n` theo nghĩa đen nằm ngoài `$...$`/`$$...$$` ở các trường văn bản được đổi thành ký tự xuống dòng (trong công thức giữ nguyên; `raw_llm_output` giữ bản gốc). Lỗi LaTeX ở `question_text` và 4 phương án làm câu `format_failed`; lỗi LaTeX ở `explanation` chỉ là cảnh báo. Khi chép vào `bank_item`, lời giải còn lỗi định dạng nghiêm trọng được lưu `explanation = NULL`.
- Ngân hàng câu hỏi là bảng `bank_item`. Chỉ duyệt thật (`review_purpose = 'production'`) mới sao chép câu sang `bank_item` và đặt `generated_question.status = 'in_bank'`. Câu thuộc mẫu `experiment` không vào ngân hàng.
- Chỉ `bank_item` mới được thêm vào đề (`exam_question.bank_item_id`).
- Khi lưu cặp gold set luôn sắp `question_id_1 < question_id_2`.
- Dạng câu (V4, thiết kế duyệt 22/10):
  - Mọi dữ liệu trước V4 là `mcq`. `question_type` không đổi sau khi tạo; các bảng con dùng khóa ngoại kép `(id, question_type)` nên DB chặn ý TF gắn vào câu khác dạng, `bank_item`/`teacher_review`/`quality_check_result` khác dạng câu gốc, và câu khác dạng run.
  - Run `tf`/`sa` chỉ cho `production`, `strategy = 'zero-shot'`, `prompt_version` là `sa-v0-draft` hoặc `tf-v0-draft`. Phân tích C1/C2/C3 lọc `question_type = 'mcq'`.
  - Câu `tf` và `sa` không dùng `option_a..d`, `correct_option`; `teacher_review.reviewer_answer`, `final_option` để NULL. Câu `tf` để NULL mức độ cấp câu (`llm_difficulty`, `final_difficulty`, `difficulty_label`, `bank_item.difficulty`): mức độ ghi theo từng ý. `llm_difficulty` của ý là mức mục tiêu khi sinh (Q-5 chốt thế nào áp cho cả hai).
  - Câu `tf` có đúng 4 ý a-d ở mỗi bảng con: DB chỉ chặn quá 4 (khóa chính + `position`), service kiểm đủ 4.
  - Đáp số `sa` lưu `TEXT` đúng chuỗi được viết (đã trim), DB không kiểm định dạng; phân tích và so theo giá trị làm trong Java.
  - `teacher_review.answer_correct`: `mcq` như cũ; `sa` NULL chỉ khi đáp số LLM đã lưu không còn phân tích được, `ambiguous` luôn FALSE; `tf` NULL, đúng/sai tính theo từng ý ở `teacher_review_statement.label_correct` (`undecidable` luôn FALSE).
  - `answer_check` của `sa`: `judge_sa_kind` bắt buộc, `judge_sa_answer` có khi kind là `number` hoặc `unparseable`. Của `tf`: nhãn từng ý ở `answer_check_statement`, xóa dây chuyền theo dòng `answer_check`.

## 4. JSON mà LLM phải trả

Quy tắc chung cho mọi JSON dưới đây:

- Một đối tượng JSON duy nhất; được phép bọc trong rào ` ```json `. Khóa trùng trong cùng đối tượng là lỗi: ở JSON sinh câu thì câu thành `format_failed`, ở JSON Solver thì `answer_check` là `ERROR` (không ghi dòng).
- Mỗi dấu `\` của LaTeX viết thành `\` trong JSON, ví dụ `"$\frac{1}{2}$"`. Chuỗi `\n` theo nghĩa đen nằm ngoài công thức ở các trường văn bản được đổi thành ký tự xuống dòng.
- Thứ tự trường là `propertyOrdering` của schema gửi Gemini (`GENERATION_RESPONSE_SCHEMA_JSON`, `SOLVER_RESPONSE_SCHEMA_JSON`). Backend đọc theo tên khóa nên không kiểm thứ tự.
- Prompt `sa` và `tf` là bản nháp (`sa-v0-draft` trong `SaPromptDraftV0`, `tf-v0-draft` trong `TfPromptDraftV0`), chờ thành viên phụ trách prompt duyệt. Hợp đồng JSON dưới đây là phần backend kiểm; nội dung prompt có thể đổi mà không đổi hợp đồng.

### 4.1. Sinh câu `mcq` (7 trường, không có độ khó)

```json
{
  "question_text": "Nội dung câu hỏi",
  "option_a": "Phương án A",
  "option_b": "Phương án B",
  "option_c": "Phương án C",
  "option_d": "Phương án D",
  "correct_option": "A",
  "explanation": "Giải thích ngắn gọn vì sao đáp án đó đúng"
}
```

### 4.2. Sinh câu `sa` (3 trường, không có độ khó)

Thứ tự: `question_text`, `explanation`, `answer`. Lời giải đứng trước đáp số để đáp số dựa vào lời giải vừa viết.

| Trường | Kiểu | Backend kiểm |
|---|---|---|
| `question_text` | chuỗi | Không trống. Với môn Toán, lỗi LaTeX là lỗi chặn (`format_failed`) |
| `explanation` | chuỗi | Không trống. Lỗi LaTeX chỉ là cảnh báo |
| `answer` | **chuỗi** | Không trống; phải là chuỗi JSON (`"5"`, không phải `5`); phân tích được ở chế độ chặt (STRICT), xem bảng đáp số ở `docs/api/README.md`. Lỗi LaTeX là lỗi chặn. Phân số chưa tối giản (`2/4`) vẫn hợp lệ, kèm cảnh báo `answer: phân số chưa tối giản` trong `answerWarnings` |

Mức mục tiêu (`targetDifficulty`, bỏ trống là `easy`) đi trong prompt, không nằm trong JSON; backend lưu nó ở `generated_question.llm_difficulty`.

```json
{
  "question_text": "Cho tam giác $ABC$ vuông cân tại $A$ có $AB = AC = 3$. Tính độ dài cạnh $BC$.",
  "explanation": "Theo định lí Pythagore, $BC^2 = AB^2 + AC^2 = 9 + 9 = 18$, nên $BC = \sqrt{18} = 3\sqrt{2}$.",
  "answer": "$3\sqrt{2}$"
}
```

### 4.3. Sinh câu `tf` (10 trường, không có độ khó)

Thứ tự: `question_text`, `statement_a`, `statement_b`, `statement_c`, `statement_d`, `explanation`, `label_a`, `label_b`, `label_c`, `label_d`. Bốn nhãn đứng sau lời giải để nhãn dựa vào lời giải vừa viết.

| Trường | Kiểu | Backend kiểm |
|---|---|---|
| `question_text` | chuỗi | Đề dẫn. Không trống; lỗi LaTeX là lỗi chặn |
| `statement_a` .. `statement_d` | chuỗi | Không trống; lỗi LaTeX là lỗi chặn; 4 ý phải khác nhau sau chuẩn hóa (trùng thì `format_failed`) |
| `explanation` | chuỗi | Lời giải cho từng ý. Không trống; lỗi LaTeX chỉ là cảnh báo |
| `label_a` .. `label_d` | chuỗi `T` hoặc `F` | Sau khi trim, không phân biệt hoa thường; giá trị khác là lỗi chặn. Không bắt buộc số ý Đúng hay số ý Sai: 4 nhãn giống nhau chỉ cảnh báo `label: 4 ý cùng nhãn T` (hoặc `F`) trong `answerWarnings` |

- Ý không ghi nhãn ở đầu. Nếu ý mở đầu bằng `a)`, `a.`, `(a)` hoặc `a:` (không phân biệt hoa thường, cho phép khoảng trắng) và chữ cái khớp đúng vị trí của ý, backend bỏ tiền tố đó một lần trước khi lưu và ghi cảnh báo, ví dụ `statement_a: đã bỏ tiền tố "a)" ở đầu ý`, vào `answerWarnings`. Chữ cái không khớp vị trí (ví dụ `statement_b` mở đầu bằng `a)`) thì giữ nguyên. `raw_llm_output` vẫn giữ bản gốc.
- Mức mục tiêu của 4 ý (`targetStatementDifficulties`) đi trong prompt, không nằm trong JSON; backend lưu ở `generated_question_statement.llm_difficulty`. Mức cấp câu để NULL.
- JSON giải mã lỗi (ví dụ khóa trùng) thì không lưu ý nào.

```json
{
  "question_text": "Cho biểu thức $P = \frac{x^2 - 1}{x^2 - x}$.",
  "statement_a": "Điều kiện xác định của $P$ là $x \neq 0$ và $x \neq 1$.",
  "statement_b": "Với điều kiện xác định, $P = \frac{x+1}{x}$.",
  "statement_c": "Tại $x = 2$ thì $P = 2$.",
  "statement_d": "Có đúng hai giá trị nguyên của $x$ để $P$ nhận giá trị nguyên.",
  "explanation": "a) $x^2 - x = x(x-1) \neq 0$. b) Rút gọn tử và mẫu cho $x - 1$. c) $P = \frac{3}{2}$. d) $\frac{x+1}{x} = 1 + \frac{1}{x}$ nguyên khi $x = -1$; $x = 1$ bị loại.",
  "label_a": "T",
  "label_b": "T",
  "label_c": "F",
  "label_d": "F"
}
```

### 4.4. Solver câu `sa` (2 trường)

Solver chỉ thấy đề (`question_text`), không thấy đáp số và lời giải của câu. Thứ tự: `reasoning`, `judge_answer`.

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `reasoning` | chuỗi | Lời giải của Solver |
| `judge_answer` | chuỗi | Một đáp số (đọc ở chế độ rộng LENIENT), hoặc `ambiguous` khi đề có nhiều cách hiểu cho ra đáp số khác nhau (so sau khi trim, không phân biệt hoa thường) |

| Solver trả | `answerCheckStatus` | `judge_sa_kind` | `judge_sa_answer` |
|---|---|---|---|
| Đáp số cùng giá trị với đáp số LLM (ví dụ `3√2` và `$\sqrt{18}$`) | `CONSISTENT` | `number` | chuỗi Solver trả |
| Đáp số khác giá trị | `INCONSISTENT` | `number` | chuỗi Solver trả |
| `ambiguous` | `INCONSISTENT` | `ambiguous` | NULL |
| Chuỗi không phân tích được thành số (ví dụ `2 cm`) | `INCONSISTENT` | `unparseable` | chuỗi Solver trả |
| JSON hỏng, khóa trùng, thiếu hoặc rỗng `judge_answer`, lỗi gọi | `ERROR` | không ghi dòng | không ghi dòng |

```json
{
  "reasoning": "Tam giác vuông cân nên $BC = AB\sqrt{2} = 3\sqrt{2}$.",
  "judge_answer": "$3\sqrt{2}$"
}
```

### 4.5. Solver câu `tf` (8 trường)

Solver chỉ thấy đề dẫn và 4 ý, không thấy nhãn và lời giải của câu. Thứ tự: `reasoning_a`, `judge_a`, `reasoning_b`, `judge_b`, `reasoning_c`, `judge_c`, `reasoning_d`, `judge_d`, để ý sau dùng được kết quả ý trước.

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `reasoning_a` .. `reasoning_d` | chuỗi | Lập luận cho từng ý |
| `judge_a` .. `judge_d` | chuỗi `T`, `F` hoặc `undecidable` | Nhãn Solver cho từng ý (trim, không phân biệt hoa thường), lưu ở `answer_check_statement.judge_label` |

- `CONSISTENT` chỉ khi cả 4 ý có nhãn `T`/`F` và đều khớp nhãn LLM. Có ý lệch hoặc `undecidable` thì `INCONSISTENT` (vẫn ghi dòng `answer_check` và 4 dòng `answer_check_statement`).
- JSON hỏng, khóa trùng, thiếu hoặc sai giá trị `judge_x`, lỗi gọi: `ERROR`, không ghi dòng nào.

```json
{
  "reasoning_a": "Mẫu $x^2 - x = x(x-1)$ khác 0 khi $x \neq 0$ và $x \neq 1$.",
  "judge_a": "T",
  "reasoning_b": "Tử $(x-1)(x+1)$, mẫu $x(x-1)$, rút gọn cho $x - 1$.",
  "judge_b": "T",
  "reasoning_c": "$P(2) = \frac{3}{2}$.",
  "judge_c": "F",
  "reasoning_d": "$1 + \frac{1}{x}$ nguyên khi $x = \pm 1$, loại $x = 1$, còn một giá trị.",
  "judge_d": "F"
}
```
