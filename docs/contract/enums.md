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
  - `exam_slot_action.replace_source` (V16, W1-4): `bank` (Đổi bằng câu ngân hàng) hoặc `generate` (sinh mới); bắt buộc với
    dòng `replace`, NULL với `keep`/`edit`; dòng replace trước V16 được điền `generate`. Với dòng `replace`,
    `generated_question_id`/`bank_item_id` là câu bị đổi RA khỏi chỗ.
  - `exam_slot_action.action`: `keep`, `edit`, `replace` (`edit_distance` chỉ với `edit`). `instruction` (V9): yêu cầu bổ sung của giáo viên khi Đổi câu, 1–500 ký tự, chỉ dòng `replace`; NULL khi không có. `changed_fields` (V13): chỉ dòng `edit` (xem mục "Giáo viên sửa câu trong đề").
  - `teacher_review.review_mode`: `blind` (mặc định cột; chỉ còn ở dữ liệu cũ vì form chấm mù đã gỡ ngày 25/10) hoặc `assisted` (Giữ/Sửa ở chế độ giáo viên,
    mọi dòng mới). Đánh giá `assisted` bị loại khỏi mọi chỉ số chấm mù cũ (`answer_correct`, duyệt không sửa).
  - Run sinh câu theo ô: `purpose = 'production'`, `strategy = 'zero-shot'`, `prompt_version` `matrix-mcq-v1-draft`,
    `matrix-tf-v2-draft` hoặc `matrix-sa-v1-draft` (run trước ngày 23/10 là `v0`; `tf` có thêm `v1`); `hyperparams` là thông số ô (chủ đề, nội dung, yêu cầu cần đạt, mức).
    Không thuộc thực nghiệm C1/C2/C3.
  - `generation_job.exam_slot_id`: job sinh câu cho một chỗ trong đề; job thường và job `answer_check` (V13) để NULL.
  - Hàng đợi `generation_job` (đợt A, ngày 23/10): worker và `POST /api/generation/jobs/process-next` nhận job bằng `SELECT ... WHERE status = 'pending' AND (next_run_at IS NULL OR next_run_at <= :now) ORDER BY next_run_at, id LIMIT 1 FOR UPDATE SKIP LOCKED` rồi đổi sang `running` trong cùng transaction, nên hai luồng hoặc hai instance không nhận trùng job (`:now` là giờ JVM, cùng đồng hồ ghi `next_run_at`). `started_at` (V10) ghi lúc nhận job. Khi worker bật, lúc khởi động và mỗi `WORKER_STALE_CHECK_MS` (mặc định 120000), job `running` có `started_at` (dòng trước V10: `updated_at`) quá `WORKER_STALE_AFTER_SECONDS` (mặc định 300) được trả về `pending`, `next_run_at` = lúc đó, `last_error = 'stale: reset'`, hoàn lại lượt `attempt` đã tăng lúc nhận (không tính vào attempt). Lỗi ở phần sau lời gọi LLM (giải mã, lưu câu, QC) đi cùng đường thử lại/`failed` như lỗi gọi LLM, `last_error` bắt đầu bằng `Lỗi sau lời gọi LLM:`. Lưu câu thì job đã có `question_id` nhưng vẫn `running`; job chỉ `done` cùng transaction với trạng thái QC cuối của câu (L-39, ngày 24/10), nên đối soát không coi câu đang QC là lệch Solver.
  - Câu `tf` trong đề theo ma trận (ngày 23/10, đợt 2): mỗi job có một mẫu nhãn bắt buộc chọn ngẫu nhiên trong 14 mẫu có 1–3 ý Đúng, khác mẫu của job `tf` gần nhất cùng đề; lưu ở `generation_job.prompt_note` dạng `Mẫu nhãn bắt buộc (T,F,F,T): ý a phải Đúng, ý b phải Sai, ...` (truy vấn: `prompt_note LIKE '%Mẫu nhãn bắt buộc (%'`). Nhãn trả về khác mẫu thì thêm cảnh báo `label: khác mẫu bắt buộc T,F,F,T (nhận T,T,F,F)` vào `warnings`, câu vẫn được dùng nhưng gắn cờ "cần xem kỹ". Chỉ luồng ma trận (run `production`).
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
  - Lý do "cần xem kỹ" thêm (W2-3): `grounding_not_supported`, `grounding_unknown` (từ kết quả), `grounding_pending`, `grounding_failed`
    (từ trạng thái job).
  - `lesson_material.extracted_text`: văn bản thường (docx đã làm sạch markdown, giữ `$...$`); xem `docs/api/README.md` mục 9.3.
  - `bank_item.visible_to_user_id` (V19): NULL là câu dùng chung (mọi câu cũ); khác NULL thì chỉ người dùng đó thấy và dùng
    lại. Đặt khi Giữ câu sinh từ đề có tài liệu đính kèm. Không xóa dây chuyền theo `users`.
  - Cờ "cần xem kỹ" bỏ qua cảnh báo `json: đã sửa ... escape LaTeX` (vẫn lưu ở `warnings`); cảnh báo `<trường>: đã tự đóng $` và các cảnh báo khác vẫn bật cờ.

- Giáo viên sửa câu trong đề (W1-2, V13, ngày 24/10):
  - Chỉ sửa được chỗ `ready` có câu hệ thống sinh (chưa Giữ). Chỗ `kept`/`edited`, chỗ đang sinh/hỏng và chỗ lấy câu từ ngân hàng không sửa được (sửa câu ngân hàng là việc riêng, chưa làm).
  - Mỗi lần sửa tạo **một dòng `generated_question` mới** (bản sửa), không bao giờ sửa dòng cũ: `edited_from_question_id` trỏ bản ngay trước (bản gốc LLM là đầu chuỗi, `edited_from_question_id IS NULL`). Bản sửa cùng `template_id`, `run_id`, `question_type`; chép `llm_difficulty` (mcq/sa) và mức từng ý (tf) của bản trước (không sửa mức độ, không định nghĩa lại `llm_difficulty`, Q-5); `raw_llm_output = ''`, `parse_success = TRUE`, token/độ trễ/chi phí NULL. Câu tf: nhãn giáo viên ghi ở `generated_question_statement.llm_label` của bản sửa. Nội dung một bản không đổi sau khi tạo, nên kết quả QC (`quality_check_result` theo `question_id`) luôn gắn đúng bản. DB chặn bản sửa khác dạng câu (khóa ngoại kép) và hai bản sửa từ cùng một bản (UNIQUE).
  - **Mọi truy vấn thống kê về đầu ra LLM** (số câu sinh, tỉ lệ `qc_passed`, kết quả Solver, dedup, chi phí...) **phải lọc `generated_question.edited_from_question_id IS NULL`**; bản giáo viên sửa không phải đầu ra LLM. Thống kê sửa câu lấy từ `exam_slot_action` (`action = 'edit'`).
  - QC của bản sửa: format (FormatChecker, như câu sinh ra) và dedup chạy ngay trong request; dedup loại trừ cả chuỗi bản trước (gồm bản gốc LLM). Trước đó các trường văn bản được trim và tự đóng `$` quên ở cuối trường (cảnh báo `<trường>: đã tự đóng $` trong `warnings`, bật cờ cần xem kỹ). Lỗi format chặn thì từ chối (400), không ghi gì. Trạng thái luôn do `QcStatusDecider` quyết (Q-2).
  - Solver: đổi đề, phương án/ý hoặc đáp án/đáp số/nhãn thì xếp job `generation_job.job_type = 'answer_check'` (có `question_id` là bản sửa, `exam_slot_id` NULL); chỉ đổi lời giải thì không gọi LLM mà chép dòng `answer_check` của bản trước (Solver không thấy lời giải; quyết định D5 ngày 24/10). Job `answer_check` dùng cùng hàng đợi, claim, thử lại (`max-attempts`, backoff) và phục hồi job kẹt như job sinh câu; thiếu API key thì `failed` ngay. Kết quả Solver chỉ ghi vào QC, không bao giờ đổi đáp án giáo viên; lệch thì `solver_inconsistent`, lỗi/hết lượt thì `solver_failed`, không bao giờ coi là đạt. Sửa tiếp khi job của bản trước còn chờ: job đó `failed`, `last_error = 'superseded'`; kết quả về muộn của bản đã bị thay không được ghi (job `done`, `last_error = 'skipped: superseded'`).
  - Bản sửa trùng câu khác: câu `duplicate`, lý do `duplicate: <id>`, không gọi Solver, không Giữ được (sửa tiếp hoặc Đổi câu khác).
  - Giữ: bị chặn (409 `QC_CHECKING`) khi job `answer_check` của câu còn `pending`/`running`; job `failed` thì Giữ được kèm cờ. Giữ bản sửa thì chỗ thành `edited` (câu chưa sửa vẫn `kept`); `bank_item.source_question_id` là bản sửa, truy ngược bản gốc LLM qua `edited_from_question_id`.
  - `generation_job.job_type`: `generate` (mặc định, mọi dòng trước V13) hoặc `answer_check`. `answer_check` bắt buộc có `question_id`, không có `exam_slot_id`.
  - `exam_slot_action.changed_fields` (V13, JSONB mảng): tên trường đã đổi khi Sửa, theo thứ tự `question_text`, `option_a`..`option_d`, `correct_option`, `sa_answer`, `statement_a`..`statement_d`, `label_a`..`label_d`, `explanation`; bắt buộc với dòng `edit`, NULL với dòng khác. `edit_distance` của dòng `edit` so với bản ngay trước, như chấm mù: `mcq` đề và 4 phương án, `sa` đề, `tf` đề dẫn và 4 ý; không tính lời giải, đáp án, nhãn.

- Vết QC theo ô đề và ngân hàng có lọc (W1-3, V14, ngày 24/10):
  - `quality_check_result.solver_reasoning` (V14): lời giải thích của Solver, chỉ dòng `answer_check` (CHECK), tối đa 4000 ký tự (cắt khi ghi); NULL với dòng cũ. Ghi cùng lúc với kết quả Solver (mọi dạng câu); khi bản sửa chỉ đổi lời giải và kết quả Solver được chép sang, lời giải thích cũng được chép. API xem đề (`/api/exams/{id}`) không trả cột này; chỉ vết QC trả.
  - `generated_question.solver_error` (V14): lý do Solver LỖI với câu sinh từ job (mạng, hết thời gian chờ, phản hồi hỏng), tối đa 1000 ký tự. Lỗi không tạo dòng `answer_check` (`chk_qcr_judge_only_answer_check` bắt buộc kết quả Solver hợp lệ). Ba trạng thái Solver của một câu: **chưa chạy** (không có dòng `answer_check`, `solver_error` NULL), **lỗi** (không có dòng, `solver_error` có giá trị; với bản giáo viên sửa: job `answer_check` ở trạng thái `failed`, lý do ở `generation_job.last_error`), **lệch** (có dòng `answer_check`, `passed = FALSE`). Chỉ đường job sinh câu cho chỗ trong đề ghi `solver_error`; sinh đồng bộ và chạy lại QC không ghi. **Việc phải làm trước khi dùng cho thí nghiệm Solver (đã duyệt 24/10):** khi làm công cụ thí nghiệm Solver, phải ghi `solver_error` (và `solver_reasoning`) cả ở đường sinh đồng bộ (`GenerationService.generate*Sync`) và đường chạy lại QC (`QcRerunService`), nếu không thống kê "Solver lỗi" sẽ lẫn vào "chưa chạy" ở hai đường đó.
  - Dedup câu trong cùng đề: câu sinh cho một chỗ (job có `exam_slot_id`) và bản giáo viên sửa được so thêm với nội dung các câu đang nằm ở những chỗ khác cùng dạng của đề: câu hiện tại của chỗ (kể cả `generated` chưa qua Solver), văn bản bản ngân hàng đang dùng (có thể đã sửa lúc duyệt, nên khác câu nguồn), câu vừa sinh của chỗ đang sinh. Ứng viên của câu ngân hàng mang id câu nguồn (`generated_question.id`), nên `quality_check_result.compared_to_question_id` trỏ tới câu nguồn. Câu đã bị Đổi ra khỏi chỗ, câu `format_failed`/`duplicate`/`rejected` không tính.
  - Chọn câu ngân hàng khi tạo đề: mỗi câu chỉ vào một chỗ của đề; giữa các đề chỉ xếp hạng mềm (không cấm): ít đề khác dùng trước, hòa thì lần dùng gần nhất (`exam.created_at` lớn nhất) cũ hơn trước, hòa nữa theo `bank_item.id`. "Dùng" là có `exam_slot.bank_item_id` hoặc `exam_question.bank_item_id` của một đề (mọi giáo viên, trừ đề đang tạo); Đổi câu bỏ `bank_item_id` khỏi chỗ nên không còn tính. Đổi luôn sinh câu mới (chưa lấy từ ngân hàng).
  - `bank_item` không có cột môn, khối, chủ đề: lọc nối qua `source_question_id` → `generated_question.template_id` → `question_template` (`subject_id`, `grade_level`, `topic`) và `exam_matrix_cell.content` của ô dùng template đó. `origin.type` hiện chỉ có `ai`; `import` dành cho câu nhập từ Word sau này (khi đó phải nới `source_question_id`/`review_id` NOT NULL bằng migration riêng).

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
| `answer` | **chuỗi** | Không trống; phải là chuỗi JSON (`"5"`, không phải `5`); phân tích được ở chế độ chặt (STRICT), xem bảng đáp số ở `docs/api/README.md`. Lỗi LaTeX là lỗi chặn. Phân số chưa tối giản (`2/4`) vẫn hợp lệ, kèm cảnh báo `answer: phân số chưa tối giản` trong `answerWarnings` |

Mức mục tiêu (`targetDifficulty`, bỏ trống là `easy`) đi trong prompt, không nằm trong JSON; backend lưu nó ở `generated_question.llm_difficulty`.

```json
{
  "question_text": "Cho tam giác $ABC$ vuông cân tại $A$ có $AB = AC = 3$. Tính độ dài cạnh $BC$.",
  "explanation": "Theo định lí Pythagore, $BC^2 = AB^2 + AC^2 = 9 + 9 = 18$, nên $BC = \\sqrt{18} = 3\\sqrt{2}$.",
  "answer": "$3\\sqrt{2}$"
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
