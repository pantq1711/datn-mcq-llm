# D2 (+D4) – Bộ chấm mù: cờ QC so với nhãn người chấm

## Câu hỏi

1. Cờ "cần xem kỹ" mà giáo viên thấy (needsReview, không tính `similar_in_exam`) bắt được bao nhiêu câu lỗi thật
   (recall), bao nhiêu cờ là đúng (precision), và bao nhiêu câu lỗi lọt qua dưới dạng "câu sạch" (tỉ lệ lọt)?
2. (D4) Câu hệ thống có ít lỗi hơn câu gọi thẳng Gemini không (2 prompt: kiểu giáo viên, prompt tốt nhất)?

## Thiết kế

| Nhóm | Số câu | Cách lấy |
|---|---|---|
| Hệ thống | ~60 | 20 câu mỗi dạng (mcq, tf, sa), mỗi dạng 1/2 có cờ; ưu tiên đề sinh từ ma trận `giuaki1-toan8`; chỉ prompt hiện tại (mcq v8, tf/sa v10, Solver v4) |
| Baseline 1 | ~20 | Prompt kiểu giáo viên, 2 đề, lấy theo tỉ lệ dạng |
| Baseline 2 | ~20 | Prompt tốt nhất, 2 đề, lấy theo tỉ lệ dạng |

Trộn, xáo, gán mã ngẫu nhiên; mọi câu đi qua cùng một đường Markdown → pandoc; không hiện lời giải (Q3). Hai người chấm
độc lập (Quân, Đang), một giáo viên phân xử ô bất đồng. Tiêu chí và định nghĩa: [`huong-dan-cham.md`](huong-dan-cham.md).

## Hạn chế cần ghi

- Mẫu phân tầng 1/2 có cờ: precision và tỉ lệ lọt ước lượng đúng theo tầng; recall trong mẫu lệch so với tổng thể, nên
  báo kèm recall ước lượng có trọng số theo cỡ hai tầng trong file xuất.
- So sánh D4 chính: câu hệ thống KHÔNG cờ với câu baseline (đều là "câu giáo viên nhận như sạch").
- Prompt Solver không lưu trong DB; giới hạn theo phiên bản prompt sinh câu (ra đời sau solver-v4).

Cách chạy: `tools/measurement/README.md`.
