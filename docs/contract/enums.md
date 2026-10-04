# Hợp đồng enum giữa backend và frontend

Nguồn duy nhất: các CHECK trong `db/schema_final.sql`. Các giá trị dưới đây được trích tự động từ file đó. Frontend và backend dùng **đúng chuỗi trong cột "Giá trị trong DB"**; cột "Hiển thị / ý nghĩa" chỉ dành cho giao diện. Muốn đổi giá trị thì sửa schema bằng migration mới, rồi cập nhật file này trong cùng một commit.

Java: đặt enum kiểu `ZERO_SHOT`, `FEW_SHOT_STRUCTURED`... và có bước chuyển đổi hai chiều với chuỗi trong DB (đặc biệt `zero-shot`, `few-shot`, `few-shot-structured` có dấu gạch ngang).

## `experiment_run.strategy`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `zero-shot` | C1: không ví dụ |
| `few-shot` | C2: kèm 3 ví dụ |
| `few-shot-structured` | C3: kèm ví dụ và đầu ra có cấu trúc |

## `generated_question.correct_option`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `A` | A |
| `B` | B |
| `C` | C |
| `D` | D |

## `generated_question.llm_difficulty`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `easy` | Dễ |
| `medium` | Trung bình |
| `hard` | Khó |

## `generated_question.final_difficulty`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `easy` | Dễ |
| `medium` | Trung bình |
| `hard` | Khó |

## `generated_question.status`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `generated` | Vừa sinh và parse xong, chưa qua QC |
| `format_failed` | Không đạt kiểm tra định dạng |
| `duplicate` | Bị bước dedup loại |
| `qc_passed` | Qua QC tự động, chờ giáo viên duyệt |
| `rejected` | Giáo viên từ chối (luồng duyệt thật) |
| `in_bank` | Đã duyệt, nằm trong ngân hàng |

## `quality_check_result.check_type`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `format` | Kiểm tra định dạng |
| `dedup` | Kiểm tra trùng lặp |

## `teacher_review.review_purpose`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `experiment` | Gán nhãn thực nghiệm (mù) |
| `production` | Duyệt thật |

## `teacher_review.reviewer_answer`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `A` | Phương án A |
| `B` | Phương án B |
| `C` | Phương án C |
| `D` | Phương án D |
| `multiple` | Có hơn 1 đáp án đúng |
| `none` | Không có đáp án đúng |

## `teacher_review.final_option`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `A` | Phương án A |
| `B` | Phương án B |
| `C` | Phương án C |
| `D` | Phương án D |

## `teacher_review.difficulty_label`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `easy` | Dễ |
| `medium` | Trung bình |
| `hard` | Khó |

## `teacher_review.decision`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `approved_as_is` | Dùng nguyên trạng |
| `approved_with_edit` | Dùng được nếu sửa nhẹ |
| `rejected` | Từ chối |

## `teacher_review_reason.reason_code`

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

## `dedup_gold_pair.sampling_stratum`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `same_run` | Cùng template, cùng lần chạy |
| `cross_run` | Cùng template, khác lần chạy |
| `cross_template` | Khác template (ứng viên tìm bằng mô hình embedding khác) |
| `random` | Ngẫu nhiên |

## `dedup_gold_pair.split`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `dev` | Tập chỉnh ngưỡng τ |
| `test` | Tập kiểm tra |

## `exam.status`

| Giá trị trong DB | Hiển thị / ý nghĩa |
|---|---|
| `draft` | Vừa sinh, chờ giáo viên kiểm tra |
| `confirmed` | Giáo viên xác nhận sử dụng |

## Quy tắc đi kèm (cần nhớ khi code, DB không tự đảm bảo hết)

- **Đáp án đúng luôn do giáo viên quyết.** API lấy câu để chấm không được trả `correct_option` về form, kể cả trong JSON ẩn. Backend tự so để ra `answer_correct`.
- `reviewer_answer` là `multiple` hoặc `none` thì không được chọn `approved_as_is`.
- Duyệt (`approved_as_is`, `approved_with_edit`) thì bắt buộc có `final_option` (A-D). `rejected` thì `final_option` phải trống và không lưu bản sửa.
- `rejected` thì có ít nhất một lý do trong `teacher_review_reason`. Mã `other` bắt buộc có `detail`.
- `edited = true` chỉ khi văn bản sau khi trim và chuẩn hóa xuống dòng thật sự khác bản gốc (`edit_distance > 0`).
- Chỉ câu `status = 'in_bank'` mới được thêm vào đề. Khi lưu cặp gold set luôn sắp `question_id_1 < question_id_2`.

## JSON mà LLM phải trả khi sinh câu hỏi (6 trường, không có độ khó)

```json
{
  "question_text": "Nội dung câu hỏi",
  "option_a": "Phương án A",
  "option_b": "Phương án B",
  "option_c": "Phương án C",
  "option_d": "Phương án D",
  "correct_option": "A"
}
```

Độ khó do bước đánh giá riêng trả về, giá trị phải là một trong `easy`, `medium`, `hard`.
