# Hợp đồng enum giữa backend và frontend

Nguồn duy nhất: các CHECK trong migration Flyway (`V1`, `V2`, ...). Frontend và backend dùng **đúng chuỗi trong cột "Giá trị trong DB"**; cột "Hiển thị / ý nghĩa" chỉ dành cho giao diện. Muốn đổi giá trị thì viết migration mới, rồi cập nhật file này trong cùng một commit.

Các cột có cùng bộ giá trị được gom thành một enum dùng chung (22 cột gom thành 15 enum). Mục 1 cho biết cột nào dùng enum nào.

Java: đặt tên hằng kiểu `ZERO_SHOT`, `FEW_SHOT_STRUCTURED`... và có bước chuyển đổi hai chiều (`AttributeConverter`) với chuỗi trong DB. Các giá trị DB có dấu gạch ngang (`zero-shot`, `few-shot`, `few-shot-structured`) và các giá trị viết thường (`multiple`, `none`, `easy`...) đều phải qua converter, không dùng `@Enumerated` mặc định.

## 1. Cột nào dùng enum nào

| Enum (mục bên dưới) | Cột dùng |
|---|---|
| `Strategy` | `experiment_run.strategy` |
| `Purpose` | `experiment_run.purpose`, `teacher_review.review_purpose` |
| `Difficulty` | `generated_question.llm_difficulty`, `generated_question.final_difficulty`, `teacher_review.difficulty_label`, `bank_item.difficulty` |
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
| `other` | Khác (bắt buộc nhập text) |

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
  - Trùng khi (a) cùng dấu vân tay sau chuẩn hóa (NFC, khoảng trắng, `\le`/`\leq`, `\ge`/`\geq`, `\ne`/`\neq` như nhau, tập 4 phương án đã sắp xếp; không gồm đáp án và lời giải), hoặc (b) `similarity >= τ` VÀ qua hai chốt chặn: số liệu và công thức trong thân câu giống nhau, hai câu không lệch nhau về phủ định. Phủ định = thân câu (phần ngoài công thức) chứa một cụm có ranh giới từ: `không`, `chưa`, `ngoại trừ`, `không phải`, `không đúng`, `không thuộc`, `không là`, `không thỏa mãn` (cả `không thoả mãn`), `không thể`, `phát biểu sai`, `nhận định sai`, `khẳng định sai`; không phân biệt hoa thường.
  - `similarity_score`, `threshold_used`, `predicted_similarity` là cosine (`1 - cosine distance`) giữa vectơ đếm 3-gram ký tự của văn bản đã chuẩn hóa. Câu không trùng lưu điểm của ứng viên gần nhất đã qua hai chốt chặn, nên luôn `< τ`.
  - `τ = 0.90` là hằng số tạm, CHƯA hiệu chỉnh trên gold set; chưa có precision/recall.
  - Phạm vi so: experiment chỉ trong cùng run; production trong các run production cùng môn. Không bao giờ so chéo experiment và production. Ứng viên là câu `qc_passed`, `in_bank` và câu cùng run đã có dòng dedup `passed = true`.
  - Dedup báo trùng: dòng dedup `passed = false` kèm `compared_to_question_id`, trạng thái `duplicate`. Dedup lỗi hoặc thiếu phạm vi: không ghi dòng dedup, giữ `generated`.
- `duplicate` là trạng thái cuối: câu không vào vòng chấm (production và experiment), không được nộp đánh giá, không bị ghi đè trạng thái; bản ghi được giữ để truy vết và thống kê. Người chấm không được biết câu nào bị loại vì trùng.
- Vòng chấm (quyết định 21/10 mục 5 và quyết định 22/10): danh sách `production` chỉ gồm câu `qc_passed` (mọi lần nộp production đều chuyển câu sang `in_bank` hoặc `rejected`, nên đó là câu chưa được chấm). Danh sách `experiment` loại `duplicate` và `format_failed`, giữ câu `generated` (kể cả khi `answer_check` lệch hoặc không chạy) và `qc_passed`, không lọc theo kết quả `answer_check`. Chi tiết và nộp đánh giá cho câu `duplicate` hoặc `format_failed` trả 404 `Không tìm thấy câu hỏi`, giống hệt câu không tồn tại.
- Ngân hàng câu hỏi là bảng `bank_item`. Chỉ duyệt thật (`review_purpose = 'production'`) mới sao chép câu sang `bank_item` và đặt `generated_question.status = 'in_bank'`. Câu thuộc mẫu `experiment` không vào ngân hàng.
- Chỉ `bank_item` mới được thêm vào đề (`exam_question.bank_item_id`).
- Khi lưu cặp gold set luôn sắp `question_id_1 < question_id_2`.

## 4. JSON mà LLM phải trả khi sinh câu hỏi (7 trường, không có độ khó)

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