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
| `QuestionOrigin` (V26) | `generated_question.origin` |
| `ImportBatchStatus` (V26) | `import_batch.status` (chưa có enum Java, hằng ở `ImportBatch.STATUS_*`) |

## 2. Giá trị của từng enum

### `Strategy`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `zero-shot` | C1: không ví dụ (thí nghiệm C1/C2/C3 đã bỏ ngày 25/10; ba giá trị giữ cho dữ liệu và CHECK cũ) |
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
| `failed` | Thất bại hẳn sau khi hết số lần thử, lỗi ghi ở `last_error` (job `answer_check` bị hủy vì bản sửa mới: `superseded`) |

`generation_job.job_type` (V13, cột chuỗi, không có enum Java riêng): `generate` (sinh câu, mặc định) hoặc `answer_check`
(chạy lại Solver cho bản giáo viên sửa).

### `SourceType`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `text` | Câu mẫu dạng văn bản |
| `pdf` | Câu mẫu lấy từ file PDF |
| `image` | Câu mẫu lấy từ ảnh |

### `UserRole`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `teacher` | Giáo viên. Đăng nhập được (W1-1), dùng ma trận, tạo đề, Giữ/Đổi, xuất Word (form chấm mù đã gỡ 25/10) |
| `researcher` | Người nghiên cứu (nhóm đồ án). Chưa được đăng nhập qua API (W1-1) |
| `admin` | Quản trị (V11, W1-1): quản lý tài khoản, xem thống kê; không dùng chức năng của giáo viên. Tài khoản đầu tiên tạo từ biến môi trường `ADMIN_EMAIL`, `ADMIN_PASSWORD` |

Claim `role` trong access token (JWT) dùng tên hằng viết hoa (`TEACHER`, `ADMIN`); JSON trả frontend (`user.role`) dùng
giá trị DB viết thường như trên.

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

### `QuestionOrigin` (V26)

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `llm` | Câu do LLM sinh, kể cả bản giáo viên sửa (mọi dòng trước V26). API ngân hàng trả `origin.type = ai` |
| `import` | Câu nhập từ Word (`/api/bank-imports`). API ngân hàng trả `origin.type = import` |

### `ImportBatchStatus` (V26)

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `preview` | Bản xem trước chờ giáo viên xác nhận (có `preview`, hạn `expires_at` 24 giờ) |
| `confirmed` | Đã xác nhận: câu đã vào ngân hàng riêng tư, `preview` đã xóa (NULL), có `confirmed_at`, `imported_count` |
| `expired` | Quá hạn chưa xác nhận: `preview` đã xóa (NULL). Lô `preview` bị hủy thì xóa hẳn dòng, không có trạng thái riêng |

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
  - Câu `tf` có thêm tầng (c) `TF_STATEMENTS` (quyết định Q6-Q7 ngày 22/10), xét sau (a) và (b): trùng khi đề dẫn giống nhau sau chuẩn hóa (hoặc cosine đề dẫn `>= τ` VÀ đề dẫn qua chốt chặn số liệu và phủ định) VÀ ít nhất 3/4 ý ghép được thành cặp có cosine `>= τ`. Ghép không phụ thuộc thứ tự, mỗi ý ghép tối đa với một ý của câu kia; KHÔNG áp chốt chặn số liệu hay phủ định ở mức ý. `similarity_score` là giá trị nhỏ nhất trong cosine đề dẫn và cosine các cặp ý đã ghép, nên vẫn `>= τ`. Với câu `tf`, ở cả ba tầng, đề dẫn được bỏ câu chỉ dẫn chung trước khi so (quyết định 4 ngày 22/10; danh sách tường minh `McqTextNormalizer.TF_INSTRUCTION_PHRASES`, ví dụ "Xét tính đúng sai của các khẳng định sau", "Các khẳng định sau đây là đúng hay sai"; cụm phải đứng trọn vẹn, ngay sau là `:`, `?`, `.` hoặc hết chuỗi), nên chữ `sai` trong cụm đó không bị tính là phủ định.
- `duplicate` là trạng thái cuối: câu không vào vòng chấm (production và experiment), không được nộp đánh giá, không bị ghi đè trạng thái; bản ghi được giữ để truy vết và thống kê. Người chấm không được biết câu nào bị loại vì trùng.
- Vòng chấm (quyết định 21/10 mục 5 và quyết định 22/10): danh sách `production` chỉ gồm câu `qc_passed` (mọi lần nộp production đều chuyển câu sang `in_bank` hoặc `rejected`, nên đó là câu chưa được chấm). Danh sách `experiment` loại `duplicate` và `format_failed`, giữ câu `generated` (kể cả khi `answer_check` lệch hoặc không chạy) và `qc_passed`, không lọc theo kết quả `answer_check`. Chi tiết và nộp đánh giá cho câu `duplicate` hoặc `format_failed` trả 404 `Không tìm thấy câu hỏi`, giống hệt câu không tồn tại.
- Định dạng (việc 1 và 2 ngày 22/10): sau khi giải mã JSON, chuỗi `\n` theo nghĩa đen nằm ngoài `$...$`/`$$...$$` ở các trường văn bản được đổi thành ký tự xuống dòng (trong công thức giữ nguyên; `raw_llm_output` giữ bản gốc). Lỗi LaTeX ở `question_text` và 4 phương án làm câu `format_failed`; lỗi LaTeX ở `explanation` chỉ là cảnh báo. Khi chép vào `bank_item` (Giữ ở chế độ giáo viên và duyệt chấm mù), lời giải luôn được giữ nguyên, kể cả khi còn lỗi LaTeX (đợt A ngày 23/10; thay quy tắc việc 2 ngày 22/10 từng lưu `explanation = NULL`). Các dòng `bank_item` đã lưu NULL theo quy tắc cũ không được khôi phục tự động.
- Ngân hàng câu hỏi là bảng `bank_item`. Chỉ duyệt thật (`review_purpose = 'production'`) mới sao chép câu sang `bank_item` và đặt `generated_question.status = 'in_bank'`. Câu thuộc mẫu `experiment` không vào ngân hàng.
- Chỉ `bank_item` mới được thêm vào đề (`exam_question.bank_item_id`).
- Khi lưu cặp gold set luôn sắp `question_id_1 < question_id_2`.
- Dạng câu (V4, thiết kế duyệt 22/10):
  - Mọi dữ liệu trước V4 là `mcq`. `question_type` không đổi sau khi tạo; các bảng con dùng khóa ngoại kép `(id, question_type)` nên DB chặn ý TF gắn vào câu khác dạng, `bank_item`/`teacher_review`/`quality_check_result` khác dạng câu gốc, và câu khác dạng run.
  - Run `tf`/`sa` chỉ cho `production`, `strategy = 'zero-shot'`. `prompt_version` là `sa-v1-draft` / `tf-v2-draft` (`v1` ngày 22/10 thêm quy tắc đa dạng; `tf-v2-draft` ngày 23/10 thêm quy tắc ý bám đề dẫn và đóng `$`); run cũ giữ `sa-v0-draft` / `tf-v0-draft` / `tf-v1-draft`. Phân tích C1/C2/C3 lọc `question_type = 'mcq'`.
  - Độ đa dạng (Q1-Q3 ngày 22/10, chỉ `sa`/`tf`, không áp cho `mcq`): prompt gửi model có thêm tối đa 10 câu gần nhất cùng template và cùng dạng câu, ở mọi run và mọi trạng thái (kể cả `duplicate`, `rejected`), chỉ câu `parse_success = TRUE`; `sa` lấy đề, `tf` lấy đề dẫn và 4 ý; không kèm đáp số hay nhãn; mỗi mục (đề, đề dẫn, một ý) cắt tối đa 200 ký tự. Áp cho cả `generate-sync` và job. `experiment_run.prompt_text` giữ prompt gốc (không có danh sách). Dựng lại danh sách của một câu: lấy câu cùng `template_id` và `question_type`, `parse_success = TRUE`, `created_at` nhỏ hơn câu đó, sắp `created_at DESC, id DESC`, lấy 10. Hạn chế: hai lần sinh chạy đồng thời không thấy câu của nhau, và danh sách dựng lại có thể lệch nếu dữ liệu bị sửa sau đó.
  - `quality_check_result.warnings` (V5, JSONB mảng chuỗi): cảnh báo không chặn của bước format (`answer: phân số chưa tối giản`, `label: 4 ý cùng nhãn T`, `statement_a: đã bỏ tiền tố "a)" ở đầu ý`), chỉ ở dòng `check_type = 'format'`, NULL khi không có. Cả sync và job đều ghi; `qcReport.answerWarnings` đọc lại từ cột này. Chạy lại QC ghi đè cột theo kết quả mới (cảnh báo bỏ tiền tố không tái tạo được vì ý đã lưu không còn tiền tố).
  - Chế độ sửa đầu ra LLM (ngày 23/10, chỉ run `production`; run `experiment` giữ giải mã chặt; từ 25/10 mcq mọi run gọi JSON mode kèm schema, cấu hình riêng theo `strategy` của C1/C2/C3 đã gỡ): (1) trước khi giải mã JSON, dấu `\` của lệnh LaTeX chưa nhân đôi trong chuỗi được nhân đôi: `\` trước ký tự không phải escape JSON hợp lệ, `\u` không theo sau bởi 4 chữ số hex, `\b` `\f` `\r` `\t` ngay trước chữ cái ASCII (`\beta`, `\frac`, `\right`, `\times`...), và `\n` chỉ khi đúng một lệnh `\neq` `\ne` `\neg` `\nu` `\not` `\ni` `\nabla` `\nleq` `\ngeq` `\nparallel` (còn lại vẫn là xuống dòng); cảnh báo `json: đã sửa N escape LaTeX (\circ, ...)`. (2) Sau khi giải mã, trường văn bản có số `$` lẻ (không tính `\$`) mà đoạn sau `$` cuối trông như công thức (không có chữ có dấu tiếng Việt, không có từ chỉ gồm 2 chữ cái ASCII trở lên) được tự chèn `$` ở cuối trường, trước dấu câu kết thúc; cảnh báo `<trường>: đã tự đóng $`. Mọi trường hợp lẻ `$` khác vẫn là lỗi format. Cả hai cảnh báo nằm trong `warnings` (V5). Ngoại lệ có chủ đích của quy tắc 6 trong `backend-structure.md`.
  - MCQ ở run `production` (job, generate-sync, ma trận) gọi Gemini JSON mode kèm schema `McqProductionSchema` (7 trường, lời giải trước `correct_option`), như `sa`/`tf`; run `experiment` vẫn gọi theo `strategy` (`few-shot-structured` có schema riêng).
  - `quality_check_result.errors` (V8, JSONB mảng chuỗi): lý do bước format không qua, chỉ dòng `format` có `passed = FALSE`, NULL khi qua. Lỗi giải mã JSON (trước FormatChecker) cũng ghi ở đây, dạng `Không giải mã được JSON hợp lệ từ phản hồi LLM: <chi tiết>`; `generation_job.last_error` của job vẫn giữ chi tiết lỗi giải mã như trước. Cả sync và job đều ghi.
  - `generation_job.prompt_note` (V8): ghi chú nơi tạo job thêm vào prompt. Job sinh câu cho chỗ trong đề: "Đây là câu thứ k/n của ô này: dạng hỏi phải khác các câu còn lại..." (khi ô có từ 2 câu) và, khi lần sinh trước của chỗ đó `format_failed`, "Lần sinh trước bị loại vì: <errors>. Tránh lặp lại lỗi này." (tối đa 300 ký tự). NULL với job thường.
  - Câu `tf` và `sa` không dùng `option_a..d`, `correct_option`; `teacher_review.reviewer_answer`, `final_option` để NULL. Câu `tf` để NULL mức độ cấp câu (`llm_difficulty`, `final_difficulty`, `difficulty_label`, `bank_item.difficulty`): mức độ ghi theo từng ý. `llm_difficulty` của ý là mức mục tiêu khi sinh (Q-5 chốt thế nào áp cho cả hai).
  - Câu `tf` có đúng 4 ý a-d ở mỗi bảng con: DB chỉ chặn quá 4 (khóa chính + `position`), service kiểm đủ 4.
  - Đáp số `sa` lưu `TEXT` đúng chuỗi được viết (đã trim), DB không kiểm định dạng; phân tích và so theo giá trị làm trong Java.
  - `teacher_review.answer_correct`: `mcq` như cũ; `sa` NULL chỉ khi đáp số LLM đã lưu không còn phân tích được, `ambiguous` luôn FALSE; `tf` NULL, đúng/sai tính theo từng ý ở `teacher_review_statement.label_correct` (`undecidable` luôn FALSE).
  - `answer_check` của `sa`: `judge_sa_kind` bắt buộc, `judge_sa_answer` có khi kind là `number` hoặc `unparseable`. Của `tf`: nhãn từng ý ở `answer_check_statement`, xóa dây chuyền theo dòng `answer_check`.

- Tạo đề theo ma trận (V6-V7, chế độ giáo viên, quyết định 22/10):
  - `exam_matrix.kind` (V15, W1-4): `matrix` (ma trận giáo viên tạo, mọi dòng cũ) hoặc `lesson` (ma trận ẩn của một lần
    sinh theo bài). Loại đề suy ra từ `exam.matrix_id → exam_matrix.kind`, không có cột riêng; thống kê tách đề theo bài
    bằng join này (đề `mode = 'teacher'` gồm cả hai loại).
  - `exam_matrix_cell.part`: `I` (chỉ `mcq`), `II` (chỉ `tf`), `III` (chỉ `sa`); `difficulty` cho `mcq`/`sa`,
    `statement_difficulties` (`"easy,easy,medium,hard"`, theo ý a-d) cho `tf`. Mỗi ô một `question_template`.
  - `exam.mode`: `manual` (mọi đề cũ) hoặc `teacher` (tạo theo ma trận, bắt buộc có `matrix_id`).
  - `exam_slot.state`: `pending`, `generating`, `ready`, `failed`, `kept`, `edited`. `ready`/`kept`/`edited` phải có câu;
    `kept`/`edited` phải có `bank_item_id`.
  - `exam_slot.failure_reason` (V23, W2-4b): lý do chỗ chuyển sang `failed` khi đối soát; bắt buộc có giá trị thuộc
    `('llm_error', 'qc_exhausted')` khi `state = 'failed'`, bắt buộc `NULL` ở mọi trạng thái khác (`chk_es_failure_reason`).
    `llm_error`: job `generate` của chỗ đã `failed` sau khi thử lại kỹ thuật tới `max-attempts` (hoặc `done` mà không có
    `question_id`), đối soát không xếp job thứ hai cho chỗ mà chuyển thẳng sang `failed` (nếu chưa có câu dự phòng từ lần
    lệch Solver trước). `qc_exhausted`: chỗ đã sinh lại hết `MAX_ATTEMPTS` (3) lần vì `format_failed` hoặc `duplicate` mà
    không có câu đạt hay câu dự phòng. Khi chỗ chuyển sang trạng thái khác (ví dụ giáo viên Đổi câu), `failure_reason` được
    xóa về `NULL`. JSON trả về ở `SlotDto`, `ExamItemDto`, `QcTraceDto` qua trường `failureReason` (bỏ qua khi `null`).
  - `exam_slot_action.replace_source` (V16, W1-4): `bank` (Đổi bằng câu ngân hàng) hoặc `generate` (sinh mới); bắt buộc với
    dòng `replace`, NULL với `keep`/`edit`; dòng replace trước V16 được điền `generate`. Với dòng `replace`,
    `generated_question_id`/`bank_item_id` là câu bị đổi RA khỏi chỗ.
  - Mã lỗi hết hạn mức gọi Gemini trong ngày (F-18, 3/11): `LLM_DAILY_QUOTA_EXHAUSTED` (HTTP 429 kèm `Retry-After`, `QuotaExhaustedException`).
    Tạo đề (`build`, `build-by-lesson`) và Đổi bằng sinh mới kiểm hạn mức ngày của Gemini (`GeminiRateLimiter`) TRƯỚC khi trừ
    `generation_quota` của giáo viên và xếp job; hết thì trả mã này và không ghi gì. QC Solver gặp hết hạn mức ngày giữa chừng
    thì ném tiếp `QuotaExhaustedException` (không ghi `answer_check` ERROR): job sinh câu giữ câu ở `generated` và xếp job
    `answer_check` riêng, job `answer_check`/`grounding_check` được hoãn tới mốc đặt lại. 429 của Gemini có `PerDay` /
    `RequestsPerDay` trong thân đánh dấu hết hạn mức ngày.
  - `exam_slot_action.replace_reason` (V25, B7): lý do giáo viên chọn khi Đổi (tùy chọn), chỉ dòng `replace`, `VARCHAR(40)` có
    CHECK `chk_esa_replace_reason`: `wrong_answer` (đáp án sai), `ambiguous_options` (phương án mơ hồ / nhiều đáp án), `off_matrix`
    (lệch ma trận), `duplicate_pattern` (trùng khuôn với câu khác), `latex_format` (lỗi công thức / định dạng), `too_hard`,
    `too_easy`, `other`; NULL khi không chọn và với `keep`/`edit`/dòng `replace` trước V25. API nhận qua trường `reason` của
    `POST .../replace`, giá trị ngoài danh sách là 400 `INVALID_REQUEST`.
  - `exam_slot_action.action`: `keep`, `edit`, `replace` (`edit_distance` chỉ với `edit`). `instruction` (V9): yêu cầu bổ sung của giáo viên khi Đổi câu, 1–500 ký tự, chỉ dòng `replace`; NULL khi không có. `changed_fields` (V13): chỉ dòng `edit` (xem mục "Giáo viên sửa câu trong đề").
  - `teacher_review.review_mode`: `blind` (mặc định cột; chỉ còn ở dữ liệu cũ vì form chấm mù đã gỡ ngày 25/10) hoặc `assisted` (Giữ/Sửa ở chế độ giáo viên,
    mọi dòng mới). Đánh giá `assisted` bị loại khỏi mọi chỉ số chấm mù cũ (`answer_correct`, duyệt không sửa).
  - Run sinh câu theo ô: `purpose = 'production'`, `strategy = 'zero-shot'`, `prompt_version` `matrix-mcq-v1-draft`,
    `matrix-tf-v3-draft` hoặc `matrix-sa-v1-draft` (run trước ngày 23/10 là `v0`; `tf` có thêm `v1`, `v2` đến ngày 26/10; `v3`: mẫu nhãn là mục tiêu, nhãn theo đúng/sai thật); `hyperparams` là thông số ô (chủ đề, nội dung, yêu cầu cần đạt, mức).
    Không thuộc thực nghiệm C1/C2/C3.
  - `generation_job.exam_slot_id`: job sinh câu cho một chỗ trong đề; job thường và job `answer_check` (V13) để NULL.
  - Hàng đợi `generation_job` (đợt A, ngày 23/10): worker và `POST /api/generation/jobs/process-next` nhận job bằng `SELECT ... WHERE status = 'pending' AND (next_run_at IS NULL OR next_run_at <= :now) ORDER BY next_run_at, id LIMIT 1 FOR UPDATE SKIP LOCKED` rồi đổi sang `running` trong cùng transaction, nên hai luồng hoặc hai instance không nhận trùng job (`:now` là giờ JVM, cùng đồng hồ ghi `next_run_at`). `started_at` (V10) ghi lúc nhận job. Khi worker bật, lúc khởi động và mỗi `WORKER_STALE_CHECK_MS` (mặc định 120000), job `running` có `started_at` (dòng trước V10: `updated_at`) quá `WORKER_STALE_AFTER_SECONDS` (mặc định 300) được trả về `pending`, `next_run_at` = lúc đó, `last_error = 'stale: reset'`, hoàn lại lượt `attempt` đã tăng lúc nhận (không tính vào attempt). Lỗi ở phần sau lời gọi LLM (giải mã, lưu câu, QC) đi cùng đường thử lại/`failed` như lỗi gọi LLM, `last_error` bắt đầu bằng `Lỗi sau lời gọi LLM:`. Lưu câu thì job đã có `question_id` nhưng vẫn `running`; job chỉ `done` cùng transaction với trạng thái QC cuối của câu (L-39, ngày 24/10), nên đối soát không coi câu đang QC là lệch Solver.
  - Câu `tf` trong đề theo ma trận (ngày 23/10, đợt 2): mỗi job có một mẫu nhãn chọn ngẫu nhiên trong 14 mẫu có 1–3 ý Đúng, khác mẫu của job `tf` gần nhất cùng đề; lưu ở `generation_job.prompt_note` dạng `Mẫu nhãn mục tiêu (T,F,F,T): cố gắng viết ý a Đúng, ý b Sai, ...` (từ `matrix-tf-v3-draft`, 26/10; trước đó `Mẫu nhãn bắt buộc (T,F,F,T): ý a phải Đúng, ...`; truy vấn: `prompt_note ~ 'Mẫu nhãn (bắt buộc|mục tiêu) \('`). Mẫu là MỤC TIÊU: nhãn trả về khác mẫu là hợp lệ (nhãn theo đúng/sai thật), chỉ thêm cảnh báo `label: khác mẫu mục tiêu T,F,F,T (nhận T,T,F,F)` (dòng cũ: `khác mẫu bắt buộc`) vào `warnings` của dòng format để thống kê mức tuân thủ và hiện trong vết QC; từ 26/10 cảnh báo này KHÔNG bật cờ "cần xem kỹ" (trước đó có). Chỉ luồng ma trận (run `production`).
  - `lesson_material` (V18, W2-3): tài liệu bài học của một giáo viên; chỉ lưu văn bản đã trích (`extracted_text`) và `sha256`
    của file gốc, không lưu file gốc. `mime_type` do server nhận dạng từ nội dung (không tin Content-Type hay đuôi file):
    `application/vnd.openxmlformats-officedocument.wordprocessingml.document` (docx, `extract_method = pandoc`),
    `application/pdf` (pdf, `pdfbox`, có `page_count`), `text/plain` (txt, `plain`). `char_count = char_length(extracted_text)`;
    trần DB 10 MiB và 1.000.000 ký tự (giới hạn nghiệp vụ chặt hơn ở cấu hình ứng dụng). `warnings`: mảng JSON hoặc NULL.
    `UNIQUE (owner_id, sha256)`; xóa người dùng xóa tài liệu của họ.
  - `exam_generation_context` (V19, W2-3): ngữ cảnh sinh câu của MỘT đề. `cell_id` NULL là cả đề (sinh theo bài, hoặc "cả ma
    trận"), khác NULL là một ô; mỗi đề tối đa một dòng cho mỗi phạm vi (`uq_egc_scope`, COALESCE). `extra_instruction`: "yêu cầu
    thêm cho AI", 1–1000 ký tự, hoặc NULL. Xóa đề xóa dây chuyền. `exam_context_material(context_id, material_id)`: tài liệu của
    phạm vi; khóa ngoại tới `lesson_material` KHÔNG xóa dây chuyền (xóa tài liệu đang dùng bị chặn, 409 `MATERIAL_IN_USE`).
    Tài liệu hiệu lực của ô = hợp tài liệu cả đề và của ô; yêu cầu thêm hiệu lực = của ô, không có thì của cả đề.
  - `generation_job.material_context` / `material_refs` (V19, ghi từ W2-3 bước 5): ảnh chụp đoạn tài liệu đã đưa vào prompt của
    job (đã bỏ mọi `<<<`, `>>>`; mỗi đoạn có dòng đầu `[Tài liệu n, đoạn k]`, không có tên file) và danh sách đoạn (mảng JSON
    `{materialId, sha256, from, to, rank, score, selection}`, `selection` là `whole`, `ranked` hoặc `fallback`, thứ tự theo
    tài liệu); cùng NULL hoặc cùng có; `material_context` tối đa 12000 ký tự. `[from, to)` là khoảng của
    `lesson_material.extracted_text`. NULL với mọi job không có tài liệu và job cũ. Không đưa ra API.
  - `generation_job.job_type = 'grounding_check'` (V20, W2-3): chấm "bám tài liệu" một câu; có `question_id` và `material_context`
    (bắt buộc), không gắn chỗ (`exam_slot_id` NULL), xếp cùng transaction với lúc job sinh câu xong, hoặc (bước 8) khi giáo viên
    sửa câu: bản sửa mới nhận một job riêng chép nguyên `material_context`/`material_refs` của chuỗi bản sửa (job grounding
    gần nhất, nếu không có thì job sinh câu). Mỗi câu (mỗi phiên bản) tối đa một job loại
    này (chỉ mục duy nhất một phần `uq_gj_grounding_question`). `chk_gj_question_job`: mọi job khác `generate` bắt buộc có
    `question_id` và không gắn chỗ (tổng quát hóa `chk_gj_answer_check`).
  - `question_grounding` (V20): kết quả chấm của một câu (mỗi câu một dòng). `verdict`: `supported`, `not_supported`, `unknown`;
    `reason` ≤ 600 ký tự; `evidence_quote` ≤ 400 ký tự; `evidence_found`: server có tìm thấy trích dẫn trong đoạn tài liệu đã đưa
    vào prompt không (NULL khi không có trích dẫn). `supported` BẮT BUỘC có trích dẫn và `evidence_found = TRUE` (`judge` nói bám tài
    liệu mà không chỉ ra được chỗ nào thì bị hạ xuống `unknown`). `material_refs` chép từ job. `generated_with_material` (mặc định
    TRUE) để so nhóm có/không tài liệu khi đo. KHÔNG đổi `generated_question.status` (Q-2, M-10): chỉ là cảnh báo.
  - Đo chi phí (V21, W2-3): `quality_check_result.token_count` (INT) và `latency_ms` (DOUBLE PRECISION, mili giây) là số liệu của
    lượt gọi Solver cho ra dòng `answer_check` (cả khi Solver chạy trong job sinh câu hay ở job `answer_check` riêng);
    `question_grounding.token_count`/`latency_ms` là của lượt gọi judge. Cùng quy tắc: ≥ 0 hoặc NULL; NULL với dòng cũ (trước
    V21) và với kết quả Solver CHÉP từ bản trước (không có lượt gọi nào, nên không bị đếm hai lần). Dòng khác `answer_check` luôn
    NULL (`chk_qcr_usage_answer_check`). Chỉ lưu lượt gọi thành công cho ra kết quả; lượt thử lại thất bại không được tính.
    Không có cột tiền: `LlmClient` không tính giá (`cost_usd` luôn 0). Tổng hợp: `docs/thong-ke/06-chi-phi.sql`. Không đưa ra API.
  - Lý do "cần xem kỹ" thêm (W2-3): `grounding_not_supported`, `grounding_unknown` (từ kết quả), `grounding_pending`, `grounding_failed`
    (từ trạng thái job).
  - Lý do "cần xem kỹ" thêm (thẩm định 31/10, câu `mcq`, Solver xét từng phương án `solver-v3-option-check`/`solver-v4-literal-reading`): `solver_multiple_correct` (Solver xét từng
    phương án thấy từ 2 phương án đúng, `judge_answer = 'multiple'`), `solver_no_correct` (không phương án nào đúng,
    `judge_answer = 'none'`). Thay cho `solver_inconsistent` ở hai trường hợp này; chỉ áp cho kết quả có khối xét từng phương án
    (mục 4.6). Chuỗi hiển thị đề xuất cho frontend: "Có thể có hơn một phương án đúng, thầy/cô kiểm tra lại" và "Hệ thống không tìm
    được phương án đúng, thầy/cô kiểm tra lại".
  - `lesson_material.extracted_text`: văn bản thường (docx đã làm sạch markdown, giữ `$...$`); xem `docs/api/README.md` mục 9.3.
  - `bank_item.visible_to_user_id` (V19): NULL là câu dùng chung (mọi câu cũ); khác NULL thì chỉ người dùng đó thấy và dùng
    lại. Đặt khi Giữ câu sinh từ đề có tài liệu đính kèm. Không xóa dây chuyền theo `users`.
  - Lý do "cần xem kỹ" thêm (thẩm định 31/10, câu cùng khuôn trong MỘT đề): `similar_in_exam: <Phần>.<số>` (**dạng đổi 2/11**; trước đó `similar_in_exam: <n>` với `n` là số thứ tự toàn đề, trang đề đánh số theo Phần nên dễ nhầm) — câu hệ thống sinh (chưa Giữ, không phải câu ngân hàng) cùng khuôn với câu đứng TRƯỚC nó trong CHÍNH đề này; `<Phần>` là `I`/`II`/`III`, `<số>` là số "Câu k" trong Phần đó (đếm từ 1 theo thứ tự hiển thị; mỗi chỗ một số, kể cả chỗ chưa có câu). Mỗi cặp chỉ câu có số thứ tự lớn hơn bị gắn; mỗi câu trước một lý do (`similar_in_exam: I.2`, `similar_in_exam: II.1`...). Tính lúc xem đề (`GET /api/exams/{id}`, `ExamQuestionSimilarity`), **không lưu DB, không có migration, không đổi `generated_question.status`, không loại câu**; chỉ bật `needsReview`, đưa `qcState` từ `passed` về `needs_review` (`checking` giữ nguyên) và cộng vào `needsReviewCount`. Danh sách đề (`GET /api/exams`, `needsReviewSlots`) chưa tính lý do này. Khuôn: bỏ `$`, thay mọi số bằng `#`, thay cụm 2–5 chữ in hoa (tên điểm, tên hình) bằng `@`, chữ thường, gộp khoảng trắng; độ giống là cosine 3-gram ký tự, ngưỡng `app.exam.similar-threshold` (`EXAM_SIMILAR_THRESHOLD`, mặc định 0,82, chọn trên 4 đề của `datn_mcq_e2e`, xem dev-log L-55). So câu trắc nghiệm với câu trắc nghiệm (thân câu và tập phương án) và câu trắc nghiệm với từng ý của câu đúng/sai (bắt câu trắc nghiệm làm lộ đáp án một ý); câu ngân hàng và câu đã Giữ/Sửa được so nhưng không bị gắn cờ (chỉ câu hệ thống sinh trong cặp bị gắn). Không đổi quy tắc dedup của ngân hàng và của câu sinh (câu chỉ khác số liệu không tính là trùng vẫn đúng ở đó); đề khác không bao giờ được so. Chuỗi hiển thị đề xuất cho frontend: "Gần giống câu {n} trong đề, nên đổi một trong hai câu".
  - Chống câu cùng khuôn lúc sinh (thẩm định 31/10, prompt ma trận mcq v4, sa v5, tf v6): prompt sinh câu cho một chỗ có khối "Các câu đã có trong ô này" (câu dẫn cắt 200 ký tự, tối đa 6 câu của các chỗ khác cùng ô trong cùng đề, đọc lúc worker nhận job; Đổi câu không gồm câu đang bị thay) và dòng "câu thứ k/N" giao khía cạnh thứ ((k−1) mod m)+1 trong m yêu cầu cần đạt của ô (m = số mức có yêu cầu cần đạt mà prompt liệt kê); m = 0 hoặc m < N thì dặn thêm dùng hình thức hỏi khác các câu cùng ô. Không thêm cột hay bảng.
  - Lý do "cần xem kỹ" thêm (thẩm định 31/10, mọi dạng câu): `explanation_draft_suspect` — lời giải có "tính lại", "làm lại" hoặc "sửa lại" (không phân biệt hoa thường và dấu). Các cụm này cũng gặp trong lời giải bình thường nên **không chặn**: bước format chỉ ghi cảnh báo `explanation: lời giải có thể còn phần nháp (tính lại, làm lại, sửa lại)` vào `warnings` (không đổi `format_failed`, không đổi `generated_question.status`, không migration) và `QcSummaryService` đổi cảnh báo đó thành mã `explanation_draft_suspect` thay vì `warning: ...`. Áp cho cả câu LLM sinh lẫn bản giáo viên sửa. Chuỗi hiển thị đề xuất cho frontend: "Lời giải có thể còn phần nháp, thầy/cô xem lại lời giải". Các cụm chắc chắn là nháp ("xin lỗi", "sorry", "wait", "nhầm" dạng hẹp, "à không") thì **chặn** câu LLM sinh (`format_failed`, lỗi "Lời giải còn chứa phần nháp/tự sửa; chỉ ghi lời giải cuối cùng."); bản giáo viên sửa chỉ có cảnh báo cùng nội dung (`warning: Lời giải còn chứa phần nháp/tự sửa; ...`). Lời giải đã bị cụm chặn bắt thì không thêm `explanation_draft_suspect`. Xem dev-log L-56.
  - **Cảnh báo nào bật cờ "cần xem kỹ" (2/11, `QcSummaryService.AUTO_FIXED_WARNING_PATTERNS`).** Mọi cảnh báo của bước format đều được lưu ở `quality_check_result.warnings` và hiện trong "Kết quả tự kiểm tra"; chỉ lý do `reviewReasons` khác nhau:
    - **KHÔNG bật cờ** (hệ thống đã tự sửa chắc chắn, không đổi nghĩa câu): `json: đã sửa …` (nhân đôi `\` của lệnh LaTeX, gỡ `\text{}` ngoài `$`, đổi ký hiệu độ sai thành `^{\circ}`); `<trường>: đã tự đóng $` (**chỉ khi chuỗi gốc có ĐÚNG 1 dấu `$` chưa thoát**, F-02, 3/11); `<trường>: đã tự bọc $ quanh … LaTeX nằm ngoài $`; `statement_x: đã bỏ tiền tố …`; `label: khác mẫu mục tiêu …` (và dạng cũ `khác mẫu bắt buộc`; nhãn do model chọn, không phải việc tự sửa).
    - **BẬT cờ** (`warning: <cảnh báo>`; riêng lời giải có "tính lại/làm lại/sửa lại" là mã `explanation_draft_suspect`): lệnh hoặc môi trường LaTeX không hỗ trợ (`Lệnh \x không hỗ trợ…`, `môi trường x không hỗ trợ`); lỗi LaTeX của lời giải (kể cả `Có lệnh LaTeX nằm ngoài cặp $…$` của bản giáo viên, ngoặc lệch…); lời giải còn nháp (`Lời giải còn chứa phần nháp/tự sửa…` ở bản giáo viên); `<trường>: có từ tiếng Anh ngoài công thức (…)`; `label: 4 ý cùng nhãn T|F`; `answer: phân số chưa tối giản`; **và mọi cảnh báo chưa biết** (mặc định bật, an toàn hơn).
    - Trước 2/11 `đã tự đóng $` bật cờ; nay không, **trừ khi chuỗi gốc có từ 3 dấu `$` chưa thoát trở lên** (3/11, F-02: vị trí đóng có thể sai chỗ). Khi đó cảnh báo mang tên khác `<trường>: đã tự đóng $ khi chuỗi có N dấu $ (có thể đóng sai chỗ)`, KHÔNG khớp mẫu không bật cờ nên hiện `warning: <trường>: đã tự đóng $ khi chuỗi có N dấu $ …` trong `reviewReasons` (frontend: `describeFormatIssue`). Thêm một việc tự sửa mới thì chỉ đưa vào nhóm không bật cờ khi chắc chắn đúng.
    - Vết QC (`qc.format.autoRepairs` / `warnings`) phân loại bằng cùng hàm `QcSummaryService.isAutoFixedWarning` (một nguồn duy nhất, F-12, 3/11): cảnh báo tự sửa → `autoRepairs`, còn lại (kể cả cảnh báo bật cờ) → `warnings`.
  - Lưu cảnh báo bước format (2/11, dev-log L-63): `quality_check_result.warnings` nay chứa CẢ cảnh báo giải mã/nhãn/đáp số lẫn cảnh báo LaTeX của `FormatChecker` (`<trường>: Lệnh \x không hỗ trợ…`, môi trường không hỗ trợ), `explanation: lời giải có thể còn phần nháp…` và từ tiếng Anh; trước đó nhóm thứ hai không được lưu nên không bật cờ. Ký hiệu độ viết sai (`^\bullet`, `^\triangle`, `^\toRadians`, `^\rceil`, `^\lceil`, `^\degree`, `^\deg`, `^\circle`, `^\o`) sau chữ số hoặc `}` được đổi thành `^{\circ}` ở bước giải mã. `^\cdot` KHÔNG nằm trong danh sách này từ 3/11 (F-02): `\cdot` là dấu nhân, đổi thành độ sẽ đổi nghĩa mà không bật cờ.
  - Cảnh báo mới (2/11, thẩm định UX đề 10; xem dev-log L-57 đến L-62), đều lưu ở `quality_check_result.warnings` và bật cờ `warning: ...`:
    - `<trường>: đã tự bọc $ quanh công thức LaTeX nằm ngoài $` (hoặc `... quanh <N> công thức ...` khi N > 1): bước giải mã ở chế độ sửa (chỉ đầu ra LLM của run `production`, mọi trường văn bản trừ `answer` của `sa`) thấy lệnh LaTeX (`\frac`, `\sqrt`, `\cdot`... trong danh sách cho phép, trừ `\text`/`\begin`/`\end`) hoặc mũ/chỉ số `^{`/`_{` nằm ngoài `$...$` và tự bọc `$...$` quanh biểu thức liền mạch: các "từ" (tách theo khoảng trắng ngoài ngoặc nhọn) không có chữ có dấu tiếng Việt, không là từ chỉ gồm từ 2 chữ cái ASCII trở lên (tiếng Anh, "Ta", "Cho"), ngoặc nhọn cân, không đi qua dấu hết câu; dấu câu cuối nằm ngoài `$`. Ví dụ `Ta có \frac{a}{b} = 2` thành `Ta có $\frac{a}{b} = 2$`. `\n` (gạch rồi n) không là lệnh. Không chắc (số `$` lẻ, ngoặc nhọn lệch, lệnh lạ) thì giữ nguyên: câu hỏi, phương án, ý vẫn là lỗi chặn `format_failed` như trước (sinh lại); **lời giải của đầu ra LLM** trước đây chỉ là cảnh báo nay cũng là lỗi chặn `explanation: Có lệnh LaTeX nằm ngoài cặp $...$ (...)` trong ca này (lời giải giáo viên viết/sửa vẫn chỉ là cảnh báo). Bộ kiểm tra LaTeX (`LatexValidationRules`) cũng báo `^{`/`_{` ngoài `$` như lệnh ngoài `$`. Chuỗi hiển thị đề xuất: "Công thức ở {nơi} chưa nằm trong dấu $, hệ thống đã tự thêm. Thầy/cô kiểm tra lại".
    - `<trường>: có từ tiếng Anh ngoài công thức (<từ>, ...)`: câu do LLM sinh có từ tiếng Anh đứng riêng (`and`, `or`, `the`, `is`, `true`, `false`; không phân biệt hoa thường; ngoài `$...$`) ở đề dẫn, phương án, ý hoặc lời giải. Chỉ cảnh báo, không loại câu; chữ giáo viên viết không bị kiểm. Chuỗi hiển thị đề xuất: "Có từ tiếng Anh trong {nơi}, thầy/cô xem lại".
  - Câu `tf` có 4 ý cùng nhãn (2/11): vẫn chỉ là cảnh báo `label: 4 ý cùng nhãn T|F` ở bước format (**không** phải lỗi chặn: câu không bị `format_failed` nên vẫn Giữ được). Khác trước: khi đối soát đề thấy cảnh báo này và chỗ còn lượt (`attempts` < 3) thì sinh lại chỗ (đường sinh lại sẵn có: câu cũ làm dự phòng như câu lệch Solver, `prompt_note` của lần sinh lại có dòng "Lần sinh trước bị loại vì: label: 4 ý cùng nhãn T ..."); hết lượt hoặc lần sinh lại hỏng thì dùng lại câu đó kèm cờ `warning: label: 4 ý cùng nhãn T` như cũ.
  - Lời giải còn phần nháp (cụm chắc chắn, chặn câu LLM sinh, `format_failed`): từ 2/11 thêm các cụm giải thích việc rút gọn đáp số cho vừa khung 4 ký tự: `ký tự`, `để đáp án gọn`, `cho gọn`, `đổi đơn vị ... để ...` (trong cùng một câu, chỉ khi sau "để" có gọn / vừa / đáp án / 4 ký tự; thu hẹp ngày 2/11). Không phân biệt hoa thường và dấu; "đơn vị đo", "Đổi 30 phút = 0,5 giờ.", "Đổi đơn vị phút sang giờ để tính kết quả" không khớp. Chữ giáo viên chỉ cảnh báo như các cụm cũ.
  - Prompt ma trận `sa` v7 (`matrix-sa-v7-draft`, 2/11): quy tắc đáp án thay "đổi đơn vị để đáp án gọn" (đã gây đáp án 100 thay 10000) bằng "chọn số liệu ngay từ đầu để đáp số đúng tự nhiên là số nguyên hoặc thập phân tối đa 4 ký tự; tuyệt đối không đổi đơn vị, không chia/nhân kết quả, không làm tròn để rút gọn; nếu đề cần làm tròn thì câu dẫn phải ghi rõ làm tròn đến đâu". Run cũ giữ `matrix-sa-v6-draft` trở về trước.
  - Prompt ma trận `mcq` v6 (`matrix-mcq-v6-draft`), `sa` v8 (`matrix-sa-v8-draft`), `tf` v8 (`matrix-tf-v8-draft`) (3/11, góp ý giáo viên Toán thật trên đề 14; dev-log L-68). Run cũ giữ `matrix-mcq-v5-draft`, `matrix-sa-v7-draft`, `matrix-tf-v7-draft` trở về trước.
    - Văn phong (cả 3 dạng, `MatrixPromptDraftV0.STYLE_RULES`): câu lệnh ngắn gọn đi thẳng vào yêu cầu (không mở đầu dài như "Hãy nhận biết…", "Trong các biểu thức đại số sau đây…" khi không cần); không đưa dữ kiện không dùng đến; dùng đúng thuật ngữ SGK ("cạnh bên", không "cạnh biên"); câu dẫn dạng câu nối kết thúc bằng "là" thì không có dấu hai chấm. Riêng `mcq`: mỗi phương án kết thúc bằng dấu chấm (sau dấu `$` đóng công thức). Riêng `sa`: câu dẫn không ghi hướng dẫn nhập đáp án ("(Nhập đáp án dưới dạng…)"); yêu cầu làm tròn khi đề cần vẫn phải ghi.
    - Chữ đáp án đúng của câu `mcq` do HỆ THỐNG giao trước (`McqAnswerPlan`), không để model tự chọn (đề 14 Phần I có 7/12 đáp án A). Lúc tạo đề, mọi chỗ mcq cần sinh được gán một chữ A-D theo thứ tự câu trong đề, seed là id đề: đếm trước chữ đáp án của các chỗ lấy từ ngân hàng (`bank_item.correct_option`), rồi chia chữ cho chỗ cần sinh sao cho toàn đề cân bằng nhất có thể (12 câu thì mỗi chữ 3 lần) và không để chỗ sinh nào tạo ra 3 câu liền nhau cùng chữ. Đổi câu hay sinh lại chọn chữ đang ít nhất trong các chỗ mcq còn lại. Chữ đã giao ghi trong `generation_job.prompt_note` dưới dạng dòng `Phương án đúng phải là C: …` (đọc lại chỉ từ phần hệ thống viết, không từ khối yêu cầu của giáo viên); prompt cấm lời giải nhắc chữ cái phương án ("Do đó chọn B").
  - Prompt ma trận `mcq` v7 (`matrix-mcq-v7-draft`), `sa` v9 (`matrix-sa-v9-draft`), `tf` v9 (`matrix-tf-v9-draft`) (4/11, góp ý giáo viên 4/11; dev-log L-70). Run cũ giữ `matrix-mcq-v6-draft`, `matrix-sa-v8-draft`, `matrix-tf-v8-draft` trở về trước. Thêm `MatrixPromptDraftV0.WORDING_RULES` cho cả 3 dạng (ngay sau `STYLE_RULES`):
    - Quan hệ toán học giữa các đối tượng đã đặt tên (vuông góc, song song, bằng, lớn hơn, nhỏ hơn, lớn hơn hoặc bằng, nhỏ hơn hoặc bằng, thuộc, không thuộc...) viết bằng KÝ HIỆU trong công thức: `$AD \perp BC$`, `$AD \parallel BC$`, `$AD = BC$`, `$AD > BC$`, `$x \ge 3$`, `$A \in d$`; không viết bằng chữ ("AD vuông góc với BC"). Ngoại lệ: câu mức Biết hỏi chính thuật ngữ hoặc định nghĩa thì giữ chữ.
    - Lời giải viết mỗi bước biến đổi một dòng (xuống dòng giữa các bước), không dồn nhiều phép biến đổi dài vào một công thức; mỗi dòng biến đổi vẫn là công thức đầy đủ trong cặp `$…$`, kể cả dòng bắt đầu bằng dấu `=` (ví dụ `$= 4(x - 2)(x + 2)$`). Không đổi hợp đồng JSON: `explanation` vẫn là một chuỗi, các bước cách nhau bằng ký tự xuống dòng (trang đề giữ xuống dòng nhờ `white-space: pre-wrap`; xuất Word mỗi dòng một đoạn, `ExamMarkdownBuilder.paragraphs`; lời giải câu `tf` vẫn tách theo ý a)–d)).
  - Dấu câu cuối câu dẫn và ý Đúng/Sai do BƯỚC GIẢI MÃ của parser (chế độ production, `StemPunctuation`; 4/11, dev-log L-70), không dựa vào prompt (đo đề 16: quy tắc "?" trong prompt chỉ được 0/4 câu tuân thủ nên đã bỏ khỏi prompt), nên web và Word thấy cùng một chữ:
    - Câu dẫn (`question_text`) mcq/sa/tf kết thúc bằng `là:` hoặc `là :` thành `là` (câu nối kết thúc bằng "là", không dấu câu); câu kết thúc bằng "là" giữ nguyên.
    - Câu dẫn **mcq và sa** có từ hỏi (`nào`, `bao nhiêu`, `gì`, `mấy`; so theo từ nguyên, không phân biệt hoa thường, chữ trong `$…$` không tính) ở CÂU CUỐI (sau dấu `. ? ! ; :` hoặc xuống dòng cuối cùng) mà không kết thúc bằng dấu câu (`. ? ! … : ; ,`) và không kết thúc bằng "là" thì thêm `?`. `đúng hay sai` và `có phải` KHÔNG là từ hỏi.
    - Câu dẫn **tf KHÔNG BAO GIỜ thêm `?`** (thường là câu mệnh lệnh "Xét tính đúng hay sai của các khẳng định sau"): thiếu dấu câu thì thêm `.` (kể cả khi có từ hỏi), kết thúc bằng `:`, `;`, `,`, `là` hoặc đã có dấu thì giữ nguyên. Câu dẫn mcq/sa không phải câu hỏi giữ nguyên.
    - Mỗi ý Đúng/Sai (`statement_a`..`statement_d`) thiếu dấu kết câu (`. ? ! …`, kể cả khi kết thúc bằng công thức) thì thêm `.` (cùng quy tắc của bản xuất Word `ExamMarkdownBuilder.endWithPeriod`).
    - Mỗi lần đổi ghi cảnh báo `json: đã sửa dấu câu cuối <trường> (thêm ?)`, `(thêm .)` hoặc `(bỏ : sau là)`: cùng tiền tố `json: đã sửa` nên **KHÔNG bật cờ**. Chế độ chặt (không sửa) giữ nguyên. Bản xuất Word vẫn giữ bước của nó (`là:` thành `là`, thêm `.` cho phương án và ý) cho dữ liệu cũ và chữ giáo viên sửa.
  - Dòng công thức của lời giải viết ngoài `$` (4/11, dev-log L-70; `LlmOutputRepair.wrapFormulaLines`, chỉ trường `explanation`, chế độ production, chạy TRƯỚC bước bọc theo đoạn liền mạch): dòng (tách theo `\n`) không có `$` nào, không có chữ tiếng Việt, không có từ ASCII dài từ 2 chữ (trừ tên in hoa 2-4 chữ như `AB`, `ABC` và tên lệnh LaTeX trong danh sách cho phép), có ký hiệu `= + ^ _ < >` hoặc lệnh LaTeX, ngoặc nhọn cân và không mở đầu bằng nhãn liệt kê (`a)`, `1.`, `- `) thì được bọc NGUYÊN dòng bằng `$…$` (dấu `. , ; : ! ?` cuối dòng ở ngoài `$`; dòng bắt đầu bằng `=` cũng bọc cả dòng). Cảnh báo `explanation: đã tự bọc $ quanh N dòng công thức nằm ngoài $` (khớp mẫu "đã tự bọc $ quanh", **KHÔNG bật cờ**). Dòng đã bị chia bởi `$` giữa biểu thức (`P $= … \cdot$ (-3)`: dòng không chữ, có cặp `$…$` mà phần ngoài còn số hoặc ký hiệu toán) không tự sửa: `FormatChecker` báo LỖI `explanation: Có dòng công thức bị chia bởi dấu $ giữa biểu thức (thiếu hoặc thừa $)` cho đầu ra LLM (câu `format_failed`, được sinh lại như lỗi "lệnh LaTeX nằm ngoài $"); chữ giáo viên viết hoặc sửa không bị chặn.
  - Cảnh báo `<trường>: wording_symbol_suggested (dùng chữ "<cụm>" thay vì ký hiệu)` (4/11), chỉ cho đầu ra LLM của câu `mcq`, chỉ ở 4 phương án (`option_a`..`option_d`): phương án chứa một trong các cụm `vuông góc với`, `song song với`, `lớn hơn`, `nhỏ hơn` (không phân biệt hoa thường; các từ cách nhau khoảng trắng tùy ý) ĐỨNG GIỮA HAI TÊN ĐỐI TƯỢNG IN HOA (1 đến 3 chữ in hoa ASCII, mỗi chữ có thể kèm dấu phẩy trên, dấu `$` được bỏ trước khi so; ví dụ `$AD$ vuông góc với $BC$`, `AB song song với CD`). Quan hệ đã viết bằng ký hiệu, hoặc cụm không đứng giữa hai tên (`hai đường thẳng vuông góc với nhau`, `cạnh đáy lớn hơn cạnh bên`) thì không cảnh báo. Mỗi cụm một cảnh báo cho mỗi phương án. Lưu ở `quality_check_result.warnings` (hiện ở vết QC, mục `warnings` của `qc.format`) và **KHÔNG bật cờ "cần xem kỹ"** (`QuestionWordingCheck.isSymbolSuggestedWarning`, loại trừ trong `QcSummaryService.reviewReasons`), không loại câu, không sinh lại: chỉ để đo mức tuân thủ quy tắc ký hiệu. Câu dẫn, lời giải, ý `tf` và câu `sa` chưa kiểm.
  - Cảnh báo `correct_option: khác chữ đã giao <chữ đã giao> (nhận <chữ nhận>)` (3/11): chữ đúng của câu mcq do model trả về khác chữ đã giao. Lưu ở `quality_check_result.warnings` (hiện ở vết QC), **KHÔNG bật cờ "cần xem kỹ"** (câu vẫn đúng toán học, chỉ lệch vị trí đáp án; như `label: khác mẫu mục tiêu …` của tf) và chưa sinh lại chỗ. Dùng để thống kê mức tuân thủ.
  - Cảnh báo văn phong mới (3/11), chỉ cho đầu ra LLM, lưu ở `quality_check_result.warnings`, **bật cờ** `warning: ...`, câu không bị loại:
    - `<trường>: dùng sai thuật ngữ "<sai>" (SGK: "<đúng>")`: hiện chỉ có `cạnh biên` → `cạnh bên` (không phân biệt hoa thường, khoảng trắng giữa hai từ tùy ý); danh sách `QuestionWordingCheck.WRONG_TERMS` mở rộng bằng cách thêm một dòng. Kiểm ở đề dẫn, phương án, ý và lời giải.
    - `question_text: có hướng dẫn nhập đáp án trong câu dẫn`: chỉ câu `sa` (khớp "nhập đáp án", "nhập đáp số", "nhập kết quả").
  - Mẫu ma trận "Giữa kì I – Toán 8" (`docs/sample`, `back_end/src/main/resources/samples`): bỏ ghi chú nội bộ ("Mức: Biết = easy, Hiểu = medium, Vận dụng = hard", "Cần giáo viên xác nhận") khỏi `source` và `notes` hiện cho giáo viên. Ma trận đã tạo từ mẫu trước 2/11 vẫn giữ chữ cũ trong `exam_matrix.source`/`notes` (không sửa dữ liệu).

- Giáo viên sửa câu trong đề (W1-2, V13, ngày 24/10):
  - Chỉ sửa được chỗ `ready` có câu hệ thống sinh (chưa Giữ). Chỗ `kept`/`edited`, chỗ đang sinh/hỏng và chỗ lấy câu từ ngân hàng không sửa được (sửa câu ngân hàng là việc riêng, chưa làm).
  - Mỗi lần sửa tạo **một dòng `generated_question` mới** (bản sửa), không bao giờ sửa dòng cũ: `edited_from_question_id` trỏ bản ngay trước (bản gốc LLM là đầu chuỗi, `edited_from_question_id IS NULL`). Bản sửa cùng `template_id`, `run_id`, `question_type`; chép `llm_difficulty` (mcq/sa) và mức từng ý (tf) của bản trước (không sửa mức độ, không định nghĩa lại `llm_difficulty`, Q-5); `raw_llm_output = ''`, `parse_success = TRUE`, token/độ trễ/chi phí NULL. Câu tf: nhãn giáo viên ghi ở `generated_question_statement.llm_label` của bản sửa. Nội dung một bản không đổi sau khi tạo, nên kết quả QC (`quality_check_result` theo `question_id`) luôn gắn đúng bản. DB chặn bản sửa khác dạng câu (khóa ngoại kép) và hai bản sửa từ cùng một bản (UNIQUE).
  - **Mọi truy vấn thống kê về đầu ra LLM** (số câu sinh, tỉ lệ `qc_passed`, kết quả Solver, dedup, chi phí...) **phải lọc `generated_question.edited_from_question_id IS NULL`**; bản giáo viên sửa không phải đầu ra LLM. Thống kê sửa câu lấy từ `exam_slot_action` (`action = 'edit'`). Từ V26 phải lọc thêm **`generated_question.origin = 'llm'`** (câu nhập từ Word không phải đầu ra LLM), kể cả bộ đếm lượt gọi Gemini khi khởi động (`countLlmGeneratedSince`).
  - QC của bản sửa: format (FormatChecker, như câu sinh ra) và dedup chạy ngay trong request; dedup loại trừ cả chuỗi bản trước (gồm bản gốc LLM). Trước đó các trường văn bản được trim và tự đóng `$` quên ở cuối trường (cảnh báo `<trường>: đã tự đóng $` trong `warnings`; từ 2/11 không bật cờ, trừ chuỗi gốc có từ 3 dấu `$`). Từ 3/11 (F-12) đường giáo viên sửa cũng bọc `$` quanh lệnh LaTeX liền mạch nằm ngoài `$...$` như parser sinh câu (cảnh báo `<trường>: đã tự bọc $ quanh …`, trừ đáp số `sa`), thay vì 400. Lỗi format chặn thì từ chối (400), không ghi gì. Trạng thái luôn do `QcStatusDecider` quyết (Q-2).
  - Solver: đổi đề, phương án/ý hoặc đáp án/đáp số/nhãn thì xếp job `generation_job.job_type = 'answer_check'` (có `question_id` là bản sửa, `exam_slot_id` NULL); chỉ đổi lời giải thì không gọi LLM mà chép dòng `answer_check` của bản trước (Solver không thấy lời giải; quyết định D5 ngày 24/10). Job `answer_check` dùng cùng hàng đợi, claim, thử lại (`max-attempts`, backoff) và phục hồi job kẹt như job sinh câu; thiếu API key thì `failed` ngay. Kết quả Solver chỉ ghi vào QC, không bao giờ đổi đáp án giáo viên; lệch thì `solver_inconsistent`, lỗi/hết lượt thì `solver_failed`, không bao giờ coi là đạt. Sửa tiếp khi job của bản trước còn chờ: job đó `failed`, `last_error = 'superseded'`; kết quả về muộn của bản đã bị thay không được ghi (job `done`, `last_error = 'skipped: superseded'`).
  - Bản sửa trùng câu khác: câu `duplicate`, lý do `duplicate: <id>`, không gọi Solver, không Giữ được (sửa tiếp hoặc Đổi câu khác).
  - Giữ: bị chặn (409 `QC_CHECKING`) khi job `answer_check` của câu còn `pending`/`running`; job `failed` thì Giữ được kèm cờ. Giữ bản sửa thì chỗ thành `edited` (câu chưa sửa vẫn `kept`); `bank_item.source_question_id` là bản sửa, truy ngược bản gốc LLM qua `edited_from_question_id`.
  - `generation_job.job_type`: `generate` (mặc định, mọi dòng trước V13) hoặc `answer_check`. `answer_check` bắt buộc có `question_id`, không có `exam_slot_id`.
  - `exam_slot_action.changed_fields` (V13, JSONB mảng): tên trường đã đổi khi Sửa, theo thứ tự `question_text`, `option_a`..`option_d`, `correct_option`, `sa_answer`, `statement_a`..`statement_d`, `label_a`..`label_d`, `explanation`; bắt buộc với dòng `edit`, NULL với dòng khác. `edit_distance` của dòng `edit` so với bản ngay trước, như chấm mù: `mcq` đề và 4 phương án, `sa` đề, `tf` đề dẫn và 4 ý; không tính lời giải, đáp án, nhãn.

- Vết QC theo ô đề và ngân hàng có lọc (W1-3, V14, ngày 24/10):
  - `quality_check_result.solver_reasoning` (V14): lời giải thích của Solver, chỉ dòng `answer_check` (CHECK), tối đa 4000 ký tự (cắt khi ghi); NULL với dòng cũ. Ghi cùng lúc với kết quả Solver (mọi dạng câu); khi bản sửa chỉ đổi lời giải và kết quả Solver được chép sang, lời giải thích cũng được chép. API xem đề (`/api/exams/{id}`) không trả cột này; chỉ vết QC trả.
  - `generated_question.solver_error` (V14; cập nhật W2-4b): lý do Solver LỖI với câu sinh từ job (mạng, hết thời gian chờ, phản hồi hỏng), tối đa 1000 ký tự. Lỗi không tạo dòng `answer_check` (`chk_qcr_judge_only_answer_check` bắt buộc kết quả Solver hợp lệ). Khi Solver lỗi kỹ thuật ngay trong `finishGeneratedJob` của câu gốc LLM (đã qua format + dedup), hệ thống xếp một job `generation_job.job_type = 'answer_check'` cho chính câu đó (thử lại tối đa `max-attempts` lần riêng bước Solver, không sinh lại câu mới); đối soát giữ chỗ ở `generating` khi job `answer_check` còn `pending`/`running`, nếu thành công thì xóa `solver_error` về `NULL` và câu thành `qc_passed`, nếu `failed` hết lượt thì cập nhật `solver_error` và giữ câu ở `ready` kèm lý do `solver_failed` trong `reviewReasons` (tách khỏi `solver_inconsistent` mới sinh lại câu tối đa 1 lần theo Q-2). Ba trạng thái Solver của một câu: **chưa chạy** (không có dòng `answer_check`, `solver_error` NULL, không có job `answer_check` `failed`), **lỗi** (`solver_error` có giá trị hoặc job `answer_check` ở trạng thái `failed` — `reviewReasons` trả `solver_failed`; khi job `answer_check` đang `pending`/`running` thì trả `solver_pending`), **lệch** (có dòng `answer_check`, `passed = FALSE` — `reviewReasons` trả `solver_inconsistent`). Chỉ đường job ghi `solver_error`; sinh đồng bộ và chạy lại QC không ghi. **Việc phải làm trước khi dùng cho thí nghiệm Solver (đã duyệt 24/10):** khi làm công cụ thí nghiệm Solver, phải ghi `solver_error` (và `solver_reasoning`) cả ở đường sinh đồng bộ (`GenerationService.generate*Sync`) và đường chạy lại QC (`QcRerunService`), nếu không thống kê "Solver lỗi" sẽ lẫn vào "chưa chạy" ở hai đường đó.
  - Dedup câu trong cùng đề: câu sinh cho một chỗ (job có `exam_slot_id`) và bản giáo viên sửa được so thêm với nội dung các câu đang nằm ở những chỗ khác cùng dạng của đề: câu hiện tại của chỗ (kể cả `generated` chưa qua Solver), văn bản bản ngân hàng đang dùng (có thể đã sửa lúc duyệt, nên khác câu nguồn), câu vừa sinh của chỗ đang sinh. Ứng viên của câu ngân hàng mang id câu nguồn (`generated_question.id`), nên `quality_check_result.compared_to_question_id` trỏ tới câu nguồn. Câu đã bị Đổi ra khỏi chỗ, câu `format_failed`/`duplicate`/`rejected` không tính.
  - Chọn câu ngân hàng khi tạo đề: mỗi câu chỉ vào một chỗ của đề; giữa các đề chỉ xếp hạng mềm (không cấm): ít đề khác dùng trước, hòa thì lần dùng gần nhất (`exam.created_at` lớn nhất) cũ hơn trước, hòa nữa theo `bank_item.id`. "Dùng" là có `exam_slot.bank_item_id` hoặc `exam_question.bank_item_id` của một đề (mọi giáo viên, trừ đề đang tạo); Đổi câu bỏ `bank_item_id` khỏi chỗ nên không còn tính. Đổi luôn sinh câu mới (chưa lấy từ ngân hàng).
  - `bank_item` không có cột môn, khối, chủ đề: lọc nối qua `source_question_id` → `generated_question.template_id` → `question_template` (`subject_id`, `grade_level`, `topic`) và `exam_matrix_cell.content` của ô dùng template đó. `origin.type` là `ai` (câu nguồn `origin = 'llm'`) hoặc `import` (câu nguồn `origin = 'import'`, V26); tham số lọc `origin=ai|import` của `GET /api/bank-items` lọc theo cột này, không truyền là mọi nguồn. Câu nhập theo mô hình B1 (quyết định 3/11) vẫn đi qua `generated_question` và `teacher_review` nên không phải nới `source_question_id`/`review_id`. Chuỗi phiên bản (`provenance.versions[].kind`) của câu nhập là `import`.

- Nhập ngân hàng câu từ Word (V26, quyết định 3/11, `/api/bank-imports`):
  - Xác nhận tạo trong MỘT transaction: một `question_template` (môn Toán, khối và chủ đề của lô, `source_question = 'Nhập từ Word: <tên hiển thị>'`), một `experiment_run` mỗi dạng câu (`production`, `zero-shot`, `model_name = 'import-docx'`, `prompt_version = 'import-docx-v1'`, `prompt_text = ''`, `status = 'imported'`), mỗi câu một `generated_question` (`origin = 'import'`, `import_batch_id`, `status = 'in_bank'`, `raw_llm_output = ''`, `parse_success = TRUE`, `llm_difficulty` NULL, `final_difficulty` theo file với mcq/sa), `generated_question_statement` cho tf (`llm_label` là nhãn trong file, `llm_difficulty` NULL, `final_difficulty` theo file), `teacher_review` (`production`, `assisted`, `approved_as_is`, người duyệt là người tải, `answer_correct = TRUE` với mcq/sa), `teacher_review_statement` cho tf, và `bank_item` + `bank_item_statement` với `visible_to_user_id` = người tải (không bao giờ là câu dùng chung). CHECK `chk_gq_import_not_llm`: câu nhập không có `edited_from_question_id` và `llm_difficulty`.
  - Mức: `NB` → `easy`, `TH` → `medium`, `VD`/`VDC` → `hard`; câu không có nhãn mức có cờ chặn `level_missing`. **Hạn chế:** file chỉ ghi một mức cho cả câu tf, nên cả 4 ý nhận chung mức của câu (khi tạo đề, câu tf nhập chỉ khớp ô có 4 mức giống nhau).
  - Không gọi LLM ở bất kỳ bước nào, không xếp job Solver (`answer_check`), không có dòng `quality_check_result`; không tính vào hạn mức sinh câu (D1, `generation_quota`) và bộ đếm Gemini.
  - Câu bị bỏ (đếm theo lý do trong `import_batch.skipped_counts` và `skipped` của API): `has_image` (ảnh, hình vẽ, ảnh công thức, đối tượng vẽ), `formula_unsupported` (công thức MathType chưa chuyển được), `has_table` (bảng dữ liệu trong đề).
  - Cờ chặn (câu không chọn được, 422 `IMPORT_ITEM_BLOCKED`): `level_missing`, `sa_answer_format` (đáp số không đạt STRICT, giữ nguyên văn), `latex_error`, `leak_suspect` (chuỗi giống tên tác giả bộ đề hoặc dòng quảng cáo/liên kết), mọi `answer_*` và `structure_*` (`answer_missing`, `answer_ambiguous`, `answer_conflict`, `structure_options_not_found`, `structure_statements_N`, `structure_empty_field`). Cờ thông tin: `no_solution` (explanation NULL; xuất Word bỏ khối lời giải của câu đó), `duplicate_in_bank`, `duplicate_in_batch`, `stem_extra_text` (đã bỏ chữ giữ chỗ hoặc câu phụ `a/ b/` thừa khỏi đề dẫn câu tf), `figure_from_previous_question` (hình nổi neo ở phương án câu trước đã chuyển vào câu này), `mentions_figure_no_image` (đề nhắc hình nhưng không có ảnh vai đề), `shared_context` (câu nhận đoạn/hình/bảng ngữ cảnh dùng chung của Phần III); bốn cờ này là cờ thông tin, không chặn, `latex_warning`, `solution_latex_error`, `formula_partial`, `plain_math_vertalign`, `hint_after_statements`, `empty_formula`.
  - Không lưu file gốc, tên file gốc hay phần "GV …" của tên file; `display_name` là tên đã làm sạch.

## 4. JSON mà LLM phải trả

Quy tắc chung cho mọi JSON dưới đây:

- Một đối tượng JSON duy nhất; được phép bọc trong rào ` ```json `. Khóa trùng trong cùng đối tượng là lỗi: ở JSON sinh câu thì câu thành `format_failed`, ở JSON Solver thì `answer_check` là `ERROR` (không ghi dòng).
- Mỗi dấu `\` của LaTeX viết thành `\\` trong JSON, ví dụ `"$\\frac{1}{2}$"`. Chuỗi `\n` theo nghĩa đen nằm ngoài công thức ở các trường văn bản được đổi thành ký tự xuống dòng.
- Thứ tự trường là `propertyOrdering` của schema gửi Gemini (`GENERATION_RESPONSE_SCHEMA_JSON`, `SOLVER_RESPONSE_SCHEMA_JSON`). Backend đọc theo tên khóa nên không kiểm thứ tự.
- Prompt `sa` và `tf` là bản nháp (`sa-v1-draft` trong `SaPromptDraftV0`, `tf-v2-draft` trong `TfPromptDraftV0`; tên lớp giữ hậu tố V0), chờ thành viên phụ trách prompt duyệt. Hợp đồng JSON dưới đây là phần backend kiểm; nội dung prompt có thể đổi mà không đổi hợp đồng.

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
| `answer` | **chuỗi** | Không trống; phải là chuỗi JSON (`"5"`, không phải `5`); đạt quy tắc đáp án bên dưới (chế độ chặt STRICT). Đáp án sai dạng thì chỉ báo lỗi dạng, không báo thêm lỗi LaTeX ngoài `$` |

**Quy tắc đáp án `sa` (quyết định 27/10/2026).** Nguồn: phiếu trả lời trắc nghiệm của Bộ GD&ĐT 2025, Phần III (trả lời ngắn):
đáp án điền vào tối đa 4 cột. Vì vậy `generated_question.sa_answer`, `bank_item.sa_answer` và bản giáo viên sửa của câu `sa` chỉ là
SỐ NGUYÊN hoặc SỐ THẬP PHÂN, tối đa 4 ký tự tính cả dấu `-` (chỉ ở đầu) và dấu `,` (phân cách thập phân):

- Trước khi kiểm: trim, bỏ cặp `$...$` (hoặc `$$...$$`) bao cả chuỗi, đổi `−` (U+2212) thành `-`, `{,}` thành `,`, dấu `.` thành `,`.
- Nhận: `^-?[0-9]+(,[0-9]+)?$`, dài tối đa 4 ký tự sau khi chuẩn hóa. Ví dụ `5`, `-12`, `2,75`, `2.75` (thành `2,75`), `$1,5$`, `0,5`, `1234`.
- Từ chối: phân số (`\frac{11}{4}`, `11/4`), hỗn số (`$3\frac{1}{4}$`), căn (`2\sqrt{3}`), đơn vị (`6 km`), quá 4 ký tự (`12,345`, `-1234`),
  dấu phẩy cuối (`3,`), chữ số 0 thừa ở đầu (`05`), số 0 mang dấu âm (`-0`). Thông báo (`format_failed`, ghi chú sinh lại): `Đáp số phải là số nguyên hoặc thập phân tối đa 4 ký tự. Hãy chọn lại số liệu của đề để đáp số tự nhiên thỏa điều kiện; không đổi đơn vị, không nhân/chia hay làm tròn kết quả để rút gọn.` (từ 2/11; trước đó `đáp án phải là … nếu kết quả lẻ thì đề phải ghi rõ làm tròn đến hàng nào`, còn trong dữ liệu `quality_check_result.errors` cũ). Frontend nhận ra theo phần đầu "Đáp số phải là" hoặc "đáp án phải là", không so cả câu.
- Lưu trữ: đáp án đạt STRICT được lưu ở dạng đã chuẩn hóa (`2.75` và `$2,75$` đều lưu `2,75`) ở cả đường sinh câu và đường giáo viên sửa; đáp án không đạt giữ nguyên văn để báo lỗi.
- Kết quả không gọn trong 4 ký tự thì đề phải yêu cầu làm tròn rõ ràng (ví dụ "làm tròn đến hàng phần mười"); hệ thống không tự đổi phân số sang thập phân.
- Không đổi: đáp số Solver (`judge_sa_answer`) và so khớp khi dedup đọc ở chế độ rộng LENIENT, vẫn nhận phân số và căn để so giá trị với đáp án câu.
- Dữ liệu cũ (trước 27/10) không bị sửa; chạy lại bước format cho câu cũ có phân số hoặc căn sẽ cho `format_failed`.

Mức mục tiêu (`targetDifficulty`, bỏ trống là `easy`) đi trong prompt, không nằm trong JSON; backend lưu nó ở `generated_question.llm_difficulty`.

```json
{
  "question_text": "Cho tam giác $ABC$ vuông cân tại $A$ có $AB = AC = 3$. Tính độ dài cạnh $BC$, làm tròn đến hàng phần mười.",
  "explanation": "Theo định lí Pythagore, $BC^2 = AB^2 + AC^2 = 9 + 9 = 18$, nên $BC = \\sqrt{18}$; làm tròn đến hàng phần mười được $4,2$.",
  "answer": "4,2"
}
```

### 4.3. Sinh câu `tf` (10 trường, không có độ khó)

Thứ tự: `question_text`, `statement_a`, `statement_b`, `statement_c`, `statement_d`, `explanation`, `label_a`, `label_b`, `label_c`, `label_d`. Bốn nhãn đứng sau lời giải để nhãn dựa vào lời giải vừa viết.

| Trường | Kiểu | Backend kiểm |
|---|---|---|
| `question_text` | chuỗi | Đề dẫn. Không trống; lỗi LaTeX là lỗi chặn |
| `statement_a` .. `statement_d` | chuỗi | Không trống; lỗi LaTeX là lỗi chặn; 4 ý phải khác nhau sau chuẩn hóa (trùng thì `format_failed`) |
| `explanation` | chuỗi | Lời giải cho từng ý. Không trống; lỗi LaTeX chỉ là cảnh báo. Với câu do LLM sinh (không áp cho câu giáo viên viết hay sửa): lời giải nhắc tới mẫu nhãn, yêu cầu gán nhãn ("theo mẫu yêu cầu", "gán nhãn"…) hoặc tự sửa đề giữa lời giải ("xin lỗi", "ta làm lại", "ta chỉnh lại"…) là lỗi chặn, câu thành `format_failed` (so không phân biệt dấu và hoa thường; xem `TfExplanationLeak`) |
| `label_a` .. `label_d` | chuỗi `T` hoặc `F` | Sau khi trim, không phân biệt hoa thường; giá trị khác là lỗi chặn. Không bắt buộc số ý Đúng hay số ý Sai: 4 nhãn giống nhau chỉ cảnh báo `label: 4 ý cùng nhãn T` (hoặc `F`) trong `answerWarnings` |

- Ý không ghi nhãn ở đầu. Nếu ý mở đầu bằng `a)`, `a.`, `(a)` hoặc `a:` (không phân biệt hoa thường, cho phép khoảng trắng) và chữ cái khớp đúng vị trí của ý, backend bỏ tiền tố đó một lần trước khi lưu và ghi cảnh báo, ví dụ `statement_a: đã bỏ tiền tố "a)" ở đầu ý`, vào `answerWarnings`. Chữ cái không khớp vị trí (ví dụ `statement_b` mở đầu bằng `a)`) thì giữ nguyên. Dạng `a.` chỉ bị bỏ khi ký tự ngay sau dấu chấm không phải chữ cái: `a.b = 0` giữ nguyên, `a. Điều kiện` và `d.  $x = 2$` bị bỏ. `raw_llm_output` vẫn giữ bản gốc.
- Mức mục tiêu của 4 ý (`targetStatementDifficulties`) đi trong prompt, không nằm trong JSON; backend lưu ở `generated_question_statement.llm_difficulty`. Mức cấp câu để NULL.
- JSON giải mã lỗi (ví dụ khóa trùng) thì không lưu ý nào.

```json
{
  "question_text": "Cho biểu thức $P = \\frac{x^2 - 1}{x^2 - x}$.",
  "statement_a": "Điều kiện xác định của $P$ là $x \\neq 0$ và $x \\neq 1$.",
  "statement_b": "Với điều kiện xác định, $P = \\frac{x+1}{x}$.",
  "statement_c": "Tại $x = 2$ thì $P = 2$.",
  "statement_d": "Có đúng hai giá trị nguyên của $x$ để $P$ nhận giá trị nguyên.",
  "explanation": "a) $x^2 - x = x(x-1) \\neq 0$. b) Rút gọn tử và mẫu cho $x - 1$. c) $P = \\frac{3}{2}$. d) $\\frac{x+1}{x} = 1 + \\frac{1}{x}$ nguyên khi $x = -1$; $x = 1$ bị loại.",
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
  "reasoning": "Tam giác vuông cân nên $BC = AB\\sqrt{2} = 3\\sqrt{2}$.",
  "judge_answer": "$3\\sqrt{2}$"
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
  "reasoning_a": "Mẫu $x^2 - x = x(x-1)$ khác 0 khi $x \\neq 0$ và $x \\neq 1$.",
  "judge_a": "T",
  "reasoning_b": "Tử $(x-1)(x+1)$, mẫu $x(x-1)$, rút gọn cho $x - 1$.",
  "judge_b": "T",
  "reasoning_c": "$P(2) = \\frac{3}{2}$.",
  "judge_c": "F",
  "reasoning_d": "$1 + \\frac{1}{x}$ nguyên khi $x = \\pm 1$, loại $x = 1$, còn một giá trị.",
  "judge_d": "F"
}
```

### 4.6. Solver câu `mcq` (4 trường, `solver-v4-literal-reading` và `solver-v3-option-check`, thẩm định 31/10)

Solver chỉ thấy đề và 4 phương án, không thấy đáp án và lời giải của câu. Solver giải bài rồi xét **từng** phương án A–D,
không dừng ở phương án đầu tiên khớp. Thứ tự: `reasoning`, `options_verdict`, `correct_count`, `judge_answer`.

- `solver-v4-literal-reading` (mặc định): một phương án là ĐÚNG nếu học sinh bảo vệ được nó là đáp án theo đúng chữ của đề,
  không thêm yêu cầu đề không nêu.
  - Phân tích nhân tử: mọi tích nhân lại bằng đa thức đã cho đều đúng, trừ khi đề ghi "triệt để" hoặc "đặt nhân tử chung
    lớn nhất".
  - Khẳng định hình học không có "luôn": phương án đúng trong ít nhất một trường hợp hợp lệ thì là đúng.
  - Đề mơ hồ khiến từ 2 phương án bảo vệ được thì `correct_count` từ 2.
- `solver-v3-option-check` (lần thử 1): cùng JSON, nhưng hiểu "đúng" hẹp (chỉ nhận cách phân tích triệt để; khẳng định không
  "luôn" đúng là sai).
- `solver-v2-delimited` (2 trường `reasoning`, `judge_answer`): Solver cũ.

v2 và v3 giữ nguyên để đo lại, chọn bằng `app.llm.solver-prompt-version`. Bảng dưới áp cho cả v3 và v4.

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `reasoning` | chuỗi | Lời giải của Solver |
| `options_verdict` | mảng 4 đối tượng `{option, correct, reason}` | `option` là `A`..`D` (mỗi chữ cái một lần, không cần đúng thứ tự, trim, không phân biệt hoa thường); `correct` là `true`/`false`; `reason` là lý do một dòng |
| `correct_count` | số nguyên | Số phương án đúng do Solver tự đếm (chỉ để đối chiếu) |
| `judge_answer` | chuỗi `A`..`D`, `multiple`, `none` | Kết luận do Solver ghi (chỉ để đối chiếu khi có `options_verdict` hợp lệ) |

Backend đếm số phương án đúng từ chính `options_verdict`. `correct_count` hoặc `judge_answer` lệch thì ghi cảnh báo vào
log và vẫn theo `options_verdict`.

`options_verdict` có nhưng hỏng (không đủ 4 phần tử A-D hợp lệ: thiếu, trùng chữ cái, `correct` không phải boolean, phần tử
không phải đối tượng) với prompt xét từng phương án (v3, v4) (F-03, 3/11): đã đọc được từ 2 phương án `correct = true` ở những
phần tử hợp lệ thì vẫn là `multiple` (`solver_multiple_correct`, không qua); còn lại là Solver lỗi (ERROR, đi đường job
`answer_check` thử lại), KHÔNG so `judge_answer` rồi cho qua. Thiếu hẳn `options_verdict` thì vẫn so `judge_answer` như Solver
cũ; prompt v2 không có trường này nên giữ cách so cũ.

| Số phương án đúng | `quality_check_result.judge_answer` | `answerCheckStatus` | `reviewReasons` |
|---|---|---|---|
| 1, trùng đáp án câu | chữ cái đó | `CONSISTENT` | không có |
| 1, khác đáp án câu | chữ cái đó | `INCONSISTENT` | `solver_inconsistent` |
| từ 2 trở lên | `multiple` | `INCONSISTENT` | `solver_multiple_correct` |
| 0 | `none` | `INCONSISTENT` | `solver_no_correct` |
| thiếu hoặc hỏng `options_verdict` (không phải mảng 4 phần tử, thiếu/trùng chữ cái, `correct` không phải true/false) | theo `judge_answer` như Solver cũ | như Solver cũ | `solver_inconsistent` khi lệch (kể cả `multiple`/`none`), không gắn lý do mới |
| JSON hỏng, khóa trùng, lỗi gọi; hoặc thiếu `options_verdict` hợp lệ mà `judge_answer` thiếu/sai | không ghi dòng | `ERROR` | `solver_failed`/`solver_pending` như cũ |

- `MULTIPLE_CORRECT` (`multiple`) và `NO_CORRECT` (`none`) là kết luận `INCONSISTENT`, đi đúng đường Q-2: câu mới sinh được
  giữ làm câu dự phòng và sinh lại 1 lần; vẫn lỗi thì giữ câu kèm cờ. Không thêm giá trị `AnswerCheckStatus`, không thêm cột.
- Kết quả từng phương án không có cột riêng: được ghi vào đầu `quality_check_result.solver_reasoning` (V14), dòng đầu là
  `[Xét từng phương án] <số>/4 phương án đúng`, mỗi phương án một dòng `A: đúng – <lý do>`, rồi một dòng trống và
  `reasoning`. `QcSummaryService` chỉ tách `solver_multiple_correct`/`solver_no_correct` khi ghi chú bắt đầu bằng
  `[Xét từng phương án]`; dòng lưu trước v3 có `multiple`/`none` vẫn là `solver_inconsistent`. Ghi chú bị cắt ở 4000 ký tự
  từ cuối, nên dòng đầu luôn còn; khi chép kết quả Solver sang bản sửa chỉ đổi lời giải, ghi chú được chép nguyên.

```json
{
  "reasoning": "$2x^3 - 6x^2 = 2x^2(x - 3)$.",
  "options_verdict": [
    {"option": "A", "correct": true, "reason": "Khai triển $2x^2(x-3) = 2x^3 - 6x^2$."},
    {"option": "B", "correct": true, "reason": "Khai triển $2x(x^2 - 3x) = 2x^3 - 6x^2$."},
    {"option": "C", "correct": false, "reason": "Khai triển $x^2(2x + 6) = 2x^3 + 6x^2$, khác đề."},
    {"option": "D", "correct": false, "reason": "$2(x^3 - 6x)$ khai triển ra $2x^3 - 12x$, khác đề."}
  ],
  "correct_count": 2,
  "judge_answer": "multiple"
}
```
