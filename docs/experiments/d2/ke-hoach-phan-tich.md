# D2/D4 – Kế hoạch phân tích (định trước)

**Phải commit file này TRƯỚC khi mở `khoa.csv` để ghép nhãn với nguồn/cờ** (L8, phản biện `.local/review/03-thuc-nghiem.md`).
Sau khi commit, mọi thay đổi định nghĩa, so sánh hay phép thống kê ghi vào mục "Thay đổi sau khi commit" ở cuối, kèm ngày và
lý do; kết quả theo kế hoạch gốc vẫn phải được báo.

Cấu hình sinh dữ liệu: [`cau-hinh.md`](cau-hinh.md). Định nghĩa nhãn: [`huong-dan-cham.md`](huong-dan-cham.md).

## 1. Dữ liệu

| Mục | Giá trị |
|---|---|
| Quần thể hệ thống | Câu LLM gốc đã qua format của 4 đề thực nghiệm (id 1–4, ma trận 6/6/6, mức Chuẩn), gồm cả câu bị QC bắt rồi sinh lại: 85 câu |
| Tầng (lay-mau-log, seed 20261115) | cờ chính: mcq 6, tf 12, sa 0 (tổng điều tra); không cờ: mcq 22, tf 18, sa 24 (rút 12 mỗi dạng); chỉ cờ phụ: 3 câu (cờ trùng), không vào phiếu |
| Baseline | Chỉ prompt 2 vào phiếu: 36 câu (12 mỗi dạng, 3 mỗi dạng mỗi lượt, 4 lượt). Prompt 1 chỉ dùng định tính (mục 7) |
| Bộ chấm | 90 câu = 18 cờ chính + 36 không cờ + 36 baseline 2; xáo trộn, mã ngẫu nhiên |
| Người chấm | Quân, Đang chấm độc lập hai pha; giáo viên phân xử |

Câu baseline 2 không phải sửa tay câu nào (72/72 parse được). Không có câu hệ thống lỗi cấu trúc.

## 2. Định nghĩa

| Khái niệm | Định nghĩa |
|---|---|
| **Lỗi đáp án (CHÍNH)** | Nhãn cuối của cột Đáp án (pha 2, sau phân xử) khác "Đúng": sai, nhiều đáp án đúng, không có đáp án đúng; Đ/S có ít nhất một ý sai nhãn; sa sai đáp số |
| Lỗi bất kỳ (mô tả) | Lỗi đáp án, hoặc Vượt lớp = Có, hoặc Mơ hồ = Có |
| **Cờ chính (dương tính D2)** | Câu có ít nhất một lý do thuộc bước Solver (`solver_*` trừ cờ hạ tầng, `statement_undecidable`), format (`warning:*`, `explanation_draft_suspect`) hoặc grounding (`grounding_not_supported`, `grounding_unknown`) – `co_qc.co_chinh` |
| Cờ phụ (báo riêng) | Cờ trùng (`duplicate:*`) và cờ hạ tầng (`solver_not_run/pending/failed`, `dedup_not_run`, `grounding_pending/failed`). Không vào số chính (L10) |
| Sau QC | Câu hệ thống không có cờ nào (giáo viên nhận như câu sạch) |
| Trước QC | Toàn bộ quần thể hệ thống ở mục 1, ước lượng bằng trọng số cỡ tầng (dạng × tầng); tầng chỉ-cờ-phụ (3 câu) không có nhãn, ghi là giới hạn |
| Nhãn cuối | Hai người đồng ý thì lấy; khác nhau hoặc pha 1 không nhất quán với pha 2 (mục 4) thì lấy kết luận phân xử |

## 3. So sánh định trước

### 3.1 So sánh chính (một kiểm định duy nhất)

**Tỉ lệ lỗi đáp án: hệ thống sau QC (không cờ, n = 36) so với baseline 2 (n = 36).**
Báo: hai tỉ lệ (KTC Wilson 95%), chênh lệch (KTC Newcombe 95%), Fisher exact hai phía, α = 0,05. Kèm KTC bootstrap theo cụm
(mục 5) như phân tích nhạy. Không kết luận "tương đương" khi p > 0,05; chỉ viết "chưa đủ bằng chứng có khác biệt" kèm KTC.

### 3.2 Ba so sánh tách chiến lược sinh với QC (L9) – mô tả, không p-value

| # | So sánh | Ý nghĩa | Ước lượng |
|---|---|---|---|
| (i) | Trước QC vs baseline 2 | Hiệu ứng chiến lược sinh (ô ma trận, prompt hệ thống) | Chênh lệch tỉ lệ lỗi đáp án, KTC bootstrap phân tầng |
| (ii) | Trước QC vs sau QC | Hiệu ứng kiểm soát (QC + cờ) | Chênh lệch, KTC bootstrap phân tầng |
| (iii) | Sau QC vs baseline 2 | Cái giáo viên nhận | = so sánh chính 3.1 |

Luôn báo kèm **tỉ lệ câu có cờ chính** trong quần thể (18/85 = 21,2%) và số cờ phụ, vì đó là chi phí soát của giáo viên.

### 3.3 D2: cờ có khớp nhãn không

Trên lỗi đáp án (chính) và lỗi bất kỳ (mô tả), gộp ba dạng:
precision = lỗi ∩ cờ chính / cờ chính (tổng điều tra tầng cờ nên không cần trọng số); tỉ lệ lọt = lỗi ∩ không cờ / không cờ;
recall ước lượng tổng thể = trọng số theo cỡ tầng (`thong_ke.recall_trong_so`), KTC bootstrap phân tầng. Bảng riêng bước
Solver (lỗi đáp án). Số theo từng dạng chỉ mô tả (n nhỏ; sa không có câu cờ chính).

## 4. Nhãn

- Pha 1 (tự giải, không có khóa) rồi pha 2 (có khóa). Máy so pha 1 với khóa. Câu mà một người tự giải khác khóa nhưng chấm
  Đáp án "Đúng" (nghi neo), hoặc tự giải trùng khóa nhưng chấm khác "Đúng", được đưa vào phân xử ô Đáp án kể cả khi hai
  người đồng ý (`phan_tich_d2.can_xem_lai`).
- Báo Cohen's κ từng tiêu chí (pha 2, trước phân xử) và tỉ lệ pha 1 khớp khóa theo người × nhóm.
- "Không rõ" (mức độ) không vào mẫu số, báo số lượng. Đ/S: "Đúng mức độ" khi ≥ 3/4 ý khớp (theo hướng dẫn chấm).
- Cột "Đoán nguồn": báo tỉ lệ đoán đúng trên câu có đoán (KTC Wilson). Nếu cận dưới > 50% thì ghi là mối đe dọa (người chấm
  phân biệt được nguồn).

## 5. Thống kê

| Phép | Dùng cho |
|---|---|
| Wilson 95% | Mọi tỉ lệ |
| Newcombe 95% + Fisher exact hai phía | Chỉ so sánh chính 3.1 |
| Bootstrap phân tầng (B = 10 000, seed 20261115) | Trước QC, recall tổng thể, (i), (ii): rút lại có hoàn lại trong từng tầng dạng × tầng (hệ thống) và trong từng dạng (baseline) |
| Bootstrap theo cụm (B = 10 000) | Nhạy cho 3.1: rút lại đề (hệ thống, 4 cụm) và lượt gọi (baseline, 4 cụm). Chỉ 4 cụm nên KTC thô; báo kèm tỉ lệ lỗi theo từng đề/lượt |

Không báo p-value cho dòng "cả mẫu" hệ thống (mẫu phân tầng không tỉ lệ, L8a). Không kiểm định theo dạng.

## 6. Bảng trước/sau theo tiêu chí (mục 1 của phản biện)

| Tiêu chí | Trước QC | Sau QC (không cờ) | Nguồn số |
|---|---|---|---|
| Định dạng hợp lệ | câu qua format / câu sinh ra (job generate có câu) của 4 đề | 100% (câu trượt format không đến tay giáo viên) | DB `quality_check_result` format, giới hạn 4 đề |
| Không trùng | 1 − câu bị dedup loại / câu qua format | không có câu trùng trong phiếu | DB dedup, giới hạn 4 đề |
| Đáp án đúng | 1 − lỗi đáp án (trọng số) | 1 − lỗi đáp án, tầng không cờ | Nhãn người chấm |
| Không mơ hồ | 1 − Mơ hồ = Có (trọng số) | tầng không cờ | Nhãn |
| Không vượt lớp | 1 − Vượt lớp = Có (trọng số) | tầng không cờ | Nhãn |
| Đúng mức độ | Đúng / (Đúng + Không đúng), trọng số | tầng không cờ | Nhãn; báo số "Không rõ" |

## 7. Định tính D4 (không kiểm định)

- Baseline 1 (prompt kiểu giáo viên, 4 lượt): đầu ra chữ tự do; 64/72 câu parse được, **8 câu "đáp án mâu thuẫn"** (model
  tự sửa giữa chừng: "Xin lỗi", "Khoan", "Sửa lại", "Kiểm tra lại đề"; không chọn đáp án nào cho các câu này). Báo số theo
  lượt và ví dụ.
- So về định dạng: số câu phải sửa tay (baseline 2: 0), số câu thiếu/thừa so với ma trận, câu đổi đề trong phần đáp án.

## 8. Phân tích nhạy

1. Bỏ các câu được đưa vào phân xử vì pha 1 không nhất quán.
2. Bỏ câu baseline đã sửa tay (hiện 0 câu, mục giữ để đủ quy trình L6c).
3. Bootstrap theo cụm cho 3.1 (mục 5).

## 9. Hiện trạng công cụ (15/11)

`do_luong.phan_tich_d2` đã có: cờ chính làm dương tính, so sánh chính, dòng trước QC (điểm ước lượng), pha 1, phân xử do pha 1,
đoán nguồn, mức độ tách tầng. **Chưa có (viết trước khi chạy bao-cao, không đổi định nghĩa ở trên):** bootstrap phân tầng và
theo cụm, bảng mục 6, bảng theo từng đề/lượt.

## Thay đổi sau khi commit

(chưa có)
