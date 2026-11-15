# D2 (+D4) – Bộ chấm mù: cờ QC so với nhãn người chấm

Thiết kế sửa theo phản biện 15/11 (L1–L10). Cấu hình lần sinh: [`cau-hinh.md`](cau-hinh.md). Kế hoạch phân tích định
trước (commit trước khi mở khóa): [`ke-hoach-phan-tich.md`](ke-hoach-phan-tich.md).

## Câu hỏi

1. Cờ chính (lý do thuộc bước Solver, format, grounding; không tính cờ trùng và cờ hạ tầng) bắt được bao nhiêu câu lỗi thật
   (recall), bao nhiêu cờ là đúng (precision), và bao nhiêu câu lỗi lọt qua dưới dạng "câu sạch" (tỉ lệ lọt)?
2. (D4) Câu hệ thống giáo viên nhận như sạch (không cờ) có ít lỗi đáp án hơn câu gọi thẳng Gemini bằng prompt tốt nhất
   (baseline 2) không? Tách hiệu ứng chiến lược sinh với hiệu ứng QC theo ba so sánh định trước.

## Thiết kế

| Nhóm | Số câu | Cách lấy |
|---|---|---|
| Hệ thống – cờ chính | 18 (mcq 6, tf 12, sa 0) | **Mọi** câu có cờ chính của 4 đề thực nghiệm (tổng điều tra) |
| Hệ thống – không cờ | 36 | 12 câu mỗi dạng, rút ngẫu nhiên trong 4 đề |
| Baseline 2 | 36 | Prompt tốt nhất ([`../d4/prompts.md`](../d4/prompts.md)), 4 lượt, 12 câu mỗi dạng, chia đều theo lượt |
| (không vào phiếu) | 3 | Câu chỉ có cờ trùng: đếm riêng |

Cả hệ thống và baseline dùng cùng ma trận [`ma-tran-6-6-6.json`](ma-tran-6-6-6.json) (Toán 8 giữa kì I, 6 mcq / 6 tf / 6 sa,
mức Chuẩn). 4 đề hệ thống sinh tuần tự trên DB sạch (`fromBank = 0`), main `ae71b1f` (mcq-v9, tf/sa-v12, Solver
solver-v6-two-pass; chưa có PA1). Baseline 1 (prompt kiểu giáo viên, 4 lượt) không vào phiếu mù: chỉ dùng cho phần định
tính D4 (số câu parse được, số câu "đáp án mâu thuẫn").

Trộn, xáo, gán mã ngẫu nhiên; mọi câu đi qua cùng một đường Markdown → pandoc; không hiện lời giải. Hai người chấm độc lập
(Quân, Đang) chấm **hai pha**: pha 1 tự giải không có khóa, pha 2 chấm có khóa và cuối phiếu đoán nguồn. Một giáo viên phân
xử ô bất đồng và các câu pha 1 không nhất quán với pha 2. Tiêu chí và định nghĩa: [`huong-dan-cham.md`](huong-dan-cham.md).

## Hạn chế cần ghi

- Tầng cờ chính là tổng điều tra, tầng không cờ là mẫu: precision và tỉ lệ lọt ước lượng đúng theo tầng; recall và tỉ lệ
  lỗi "trước QC" là ước lượng có trọng số theo cỡ tầng (`lay-mau-log.json`), kèm bootstrap (kế hoạch phân tích).
- Câu trong cùng đề / cùng lượt gọi không độc lập; chỉ 4 cụm mỗi bên.
- Không có câu sa nào bị cờ chính ở 4 đề này: không có số cờ × nhãn cho riêng dạng sa.
- Prompt Solver không lưu trong DB; phiên bản thật ghi ở `cau-hinh.md`.
- Một ma trận, một lớp, mức Chuẩn, một model (`gemini-3.5-flash-lite`), một ngày.

Cách chạy: `tools/measurement/README.md`.
