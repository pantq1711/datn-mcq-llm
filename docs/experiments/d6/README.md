# D6 – Hiệu chỉnh ngưỡng loại trùng 3-gram

## Câu hỏi

Ngưỡng tạm `DedupChecker.PROVISIONAL_THRESHOLD = 0.90` (chọn 21/10 bằng cách xem tay, chưa có precision/recall) có hợp lý
không? Quét ngưỡng 0,50 → 0,95 (bước 0,05), tính precision, recall, F1 trên tập cặp gán nhãn.

## Hiện trạng goldset (kiểm 13/11)

| Hạng mục | Kết quả |
|---|---|
| Bảng `dedup_gold_pair`, `dedup_gold_label` | Có trong schema từ V1, có entity/repository trong `goldset/`, **không** service/API nào ghi |
| Dữ liệu cặp trong repo | **0 cặp**: không migration/seed nào chèn, không có file cặp |
| Dữ liệu cặp trong DB `datn_mcq_e2e` | **0 cặp, 0 nhãn** |
| Ai gán nhãn | Chưa có. Theo quyết định 13/11: An gán toàn bộ; Quân gán độc lập nếu kịp (có kappa), không kịp thì ghi "một người gán" ở phần hạn chế |

Vì vậy D6 tạo tập cặp mới bằng `DedupGoldRunner` (chỉ đọc DB). Tập cặp và nhãn nằm trong file ở `data/private/do-luong/`,
**không** ghi vào hai bảng trên.

## Thiết kế lấy mẫu (~150 cặp)

| Tầng (`sampling_stratum`) | Số cặp | Nghĩa |
|---|---|---|
| `same_run` | 40 | Cùng run (cùng đề, cùng dạng) |
| `cross_run` | 45 | Khác run, cùng template (cùng ô ma trận ở hai đề): nơi có cặp "cùng dạng, đổi số liệu" |
| `cross_template` | 35 | Khác template |
| `random` | 30 | Rút đều trên mọi cặp còn lại (đại diện phân bố thật, chủ yếu cặp khác xa) |

- Ba tầng đầu: 70% cặp có cosine trong [0,60; 0,95), phần còn lại chia đôi trên/dưới vùng.
- Bắt buộc có ít nhất 15 cặp "đổi số liệu" (chốt chặn số liệu khác, cosine ≥ 0,80) và 10 cặp lệch phủ định.
- Chỉ ghép câu cùng dạng (như pipeline). Kho câu: mọi `generated_question` có nội dung và không trượt bước format
  (e2e 13/11: 455 câu).
- Seed cố định; thứ tự cặp trên phiếu đã xáo, số Cặp không lộ tầng hay điểm.
- Dự đoán ở mỗi ngưỡng gọi đúng `DedupChecker.check(câu id lớn, [câu id nhỏ], t)` như lúc QC (gồm dấu vân tay, hai chốt
  chặn và tầng theo ý của câu Đ/S).

Hạn chế cần ghi: tập cặp dồn vào vùng khó nên P/R là trên tập hiệu chỉnh, không phải tỉ lệ trên mọi cặp trong ngân hàng.

## Cách chạy

Xem `tools/measurement/README.md`, mục D6. Hướng dẫn gán nhãn: [`huong-dan-gan-nhan.md`](huong-dan-gan-nhan.md).
