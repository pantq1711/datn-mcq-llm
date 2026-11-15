# Đánh giá thủ công lỗi sai của phương án nhiễu (PA1)

Theo `docs/design/phuong-an-nhieu-loi-sai.md` mục 8.1. Thư mục này chỉ có **mẫu** phiếu chấm (`phieu-cham-mau.csv`: dòng tiêu
đề và 2 dòng ví dụ tự soạn). Phiếu đã điền và bảng nối `ma_cau` với kết luận Solver chỉ nằm ở `data/private/phuong-an-nhieu/`
(đã có trong `.gitignore`), không đưa vào repo, fixture hay log.

## Mẫu và người chấm

- 30 câu `mcq` chọn ngẫu nhiên (hạt giống cố định, ghi trong báo cáo) trong các câu LLM gốc có ít nhất một lỗi của đề chạy thật;
  đề không đủ 30 câu thì lấy hết và ghi rõ số câu. Đơn vị chấm là **từng lỗi** (tối đa 90 dòng).
- Hai người chấm độc lập (An và một giáo viên Toán), mỗi người một phiếu. Phiếu **không có** cột kết luận của Solver.

## Cột

| Cột | Ý nghĩa |
|---|---|
| `ma_cau` | Mã câu trong phiếu (không phải id DB; bảng nối nằm ở `data/private/`) |
| `cau_dan` | Câu dẫn |
| `phuong_an_dung` | Chữ của đáp án đúng |
| `chu_phuong_an`, `noi_dung_phuong_an` | Phương án sai đang chấm |
| `loi_hoc_sinh` | Lỗi sai hệ thống ghi cho phương án đó |
| `danh_gia` | `hop_ly` hoặc `khong_hop_ly` |
| `ghi_chu` | Tiêu chí còn thiếu khi `khong_hop_ly` |

## Tiêu chí "hợp lý" (thiếu một tiêu chí là "không hợp lý")

1. Áp lỗi vào lời giải đúng thì ra đúng phương án đó.
2. Là lỗi học sinh lớp đó thực sự hay mắc ở bài này.
3. Nằm trong phạm vi kiến thức của lớp và bài.

Solver chỉ kiểm tiêu chí (1), nên đồng thuận thấp ở (2), (3) là điều dự kiến và phải ghi như một giới hạn.

## Chỉ số (tính bằng bảng tính, không có code)

- Tỉ lệ lỗi "hợp lý" theo từng người chấm.
- Đồng thuận hai người chấm: tỉ lệ trùng $p_o$ và Cohen's kappa $\kappa = (p_o - p_e) / (1 - p_e)$, với $p_e$ là tổng theo
  từng nhãn của tích tỉ lệ biên hai người.
- Đồng thuận từng người chấm với Solver: bảng 2×2 (`match` so với `mismatch`; người: hợp lý so với không hợp lý), tỉ lệ trùng và
  kappa; lỗi `unknown`/`unchecked` báo riêng số lượng, không đưa vào bảng.
- Trong các lỗi Solver báo `match`, tỉ lệ người chấm cho là không hợp lý.
