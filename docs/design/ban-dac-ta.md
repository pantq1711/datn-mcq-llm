# Xuất bản đặc tả đề kiểm tra (Phụ lục 2, Công văn 7991)

## Trạng thái (18/11/2026)

- Bước 0 (inspect, plan) chủ dự án duyệt 18/11 với 7 quyết định D1–D7 (mục 4). Nhánh `feature/exam-spec-export`, nền
  `origin/main` 7f3f9d2 (migration mới nhất V34). **Không có migration.**
- API: tham số `content=spec` của `GET /api/exams/{examId}/export/docx` (`docs/api/README.md` mục 7.6).
- Mã: `ExamSpecBuilder` (bảng), `ExamExportService` (nạp ô ma trận), `ExamReferenceDoc` / `ExamPage` (section A4 ngang,
  chân trang, kiểu chữ 10 pt của bảng), `ExportWordDialog` (nhóm chọn "Nội dung file").

## 1. Mẫu gốc

Công văn 7991/BGDĐT-GDTrH ngày 17/12/2024 (V/v thực hiện kiểm tra, đánh giá đối với cấp THCS, THPT), phần Phụ lục, mục
"2. BẢN ĐẶC TẢ ĐỀ KIỂM TRA ĐỊNH KÌ". File PDF của công văn là bản scan (không trích được chữ); cấu trúc đọc từ ảnh chụp
Phụ lục 1 và 2 (có chú thích 2–6 và chữ ký như bản gốc):

- cột: TT | Chủ đề/Chương | Nội dung/đơn vị kiến thức | Yêu cầu cần đạt ("- Biết…", "- Hiểu…", "- VD…") | "Số câu hỏi ở
  các mức độ đánh giá": TNKQ (Nhiều lựa chọn, "Đúng – Sai", Trả lời ngắn) và Tự luận, mỗi dạng Biết / Hiểu / Vận dụng;
- chân bảng: Tổng số câu (theo cột), Tổng số điểm và Tỉ lệ % (theo dạng; mẫu: 3,0/30, 2,0/20, 2,0/20, 3,0/30);
- chú thích 4 (Phụ lục 1): ô "thể hiện số câu hỏi hoặc câu hỏi số bao nhiêu"; chú thích 6 (Phụ lục 2): một số ô ghi tắt
  năng lực "(NL?)" (không làm: DB không có năng lực);
- chú thích 2: mỗi câu Đúng – Sai gồm 4 ý.

Một nguồn thứ cấp (soande.vn) ghi Phụ lục 2 "không có chân bảng"; bản scan có 3 dòng tổng nên theo bản scan.

## 2. Dữ liệu và cách tính

| Cột | Nguồn | Thiếu dữ liệu |
|---|---|---|
| TT | thứ tự chủ đề theo lần xuất hiện đầu (`exam_matrix_cell.position`) | — |
| Chủ đề/Chương | `exam_matrix_cell.topic`, gộp dọc các hàng cùng chủ đề; đề sinh theo bài: tên bài | không trống (CHECK V6) |
| Nội dung/đơn vị kiến thức | `exam_matrix_cell.content`; các ô cùng (chủ đề, nội dung) gộp thành một hàng | để trống |
| Yêu cầu cần đạt | `learning_outcome_easy/medium/hard` của các ô trong hàng, các mức hàng dùng; trùng hệt nhau ghi một lần | "—" |
| 9 cột TNKQ | `exam_slot` của đề theo ô và mức của ô (`difficulty`; tf: `statement_difficulties` từng ý) | trống |
| 3 cột Tự luận | không có dữ liệu | trống (D3) |
| Tổng số câu | cộng theo cột | 0 để trống |
| Tổng số điểm / Tỉ lệ % | `ExamScore.perQuestion` × số chỗ của phần; tỉ lệ = điểm × 10 | dạng không có câu, Tự luận: trống |

- Đề sinh theo bài (W1-4) có ma trận ẩn `kind = lesson`: yêu cầu cần đạt mặc định ("Nhận biết được kiến thức cơ bản của
  bài: …") in nguyên văn. Đề không gắn ma trận (dữ liệu cũ, không có ở chế độ giáo viên): 409 `SPEC_UNAVAILABLE`.
- Số câu đánh lại từ 1 mỗi phần theo thứ tự của file đề (ô theo vị trí, rồi `slot_no`, như `ExamViewService`); mức lấy từ ô
  ma trận vì câu ngân hàng được chọn theo đúng mức của ô (`ExamBuildService.bankCandidates`) và Sửa câu không đổi mức.
- Chỗ chưa có câu (đang soạn, hỏng) vẫn được đếm: file đề vẫn in "Câu k." cho chỗ đó và điểm tính trên số chỗ.

## 3. File Word

- Một section A4 ngang (16838 × 11906 twips, lề như file đề, bề rộng chữ 15137), chân trang "Bản đặc tả đề kiểm tra" +
  "Trang x/y" (tab phải ở lề phải trang ngang).
- Đầu đề: bảng 2 cột không viền như file đề (tỉ lệ cột 4194 : 6011 giãn theo trang ngang), cột phải gồm tên kì, năm học,
  môn và lớp, thời gian làm bài; không có dòng ngày và bảng mã đề (D7). Tiếp theo là tiêu đề "BẢN ĐẶC TẢ ĐỀ KIỂM TRA ĐỊNH KÌ".
- Bảng openxml thô (như đầu đề và bảng đáp án), bố cục cố định, chữ 10 pt (kiểu `ExamSpecCell` căn giữa, `ExamSpecText`
  căn trái), 4 tầng tiêu đề lặp lại ở mỗi trang, hàng không bị cắt giữa hai trang. Bề rộng cột (twips): TT 450, chủ đề 1500,
  nội dung 1900, yêu cầu cần đạt 3847, mỗi cột mức 620.
- Dưới bảng: ghi chú cách đọc chỉ định câu, cách đếm ý Đúng – Sai, và "Phần tự luận (nếu có) do giáo viên bổ sung." khi
  tổng điểm dưới 10.
- Hạn chế: chữ $...$ trong yêu cầu cần đạt (nếu giáo viên nhập) in nguyên dạng chữ, không thành công thức.

## 4. Quyết định 18/11 (chủ dự án)

| | Quyết định |
|---|---|
| D1 | Câu Đúng – Sai đếm theo ý, mỗi ý ở mức của ý: "2 (C1a, C1b)" |
| D2 | Ô ghi "số câu (C1, C2)", 3 câu liên tiếp trở lên ghi dải "C1–C4"; ghi chú cách đọc dưới bảng ("C3 ở cột Nhiều lựa chọn là Phần I – Câu 3") |
| D3 | Giữ 3 cột Tự luận, để trống |
| D4 | Mỗi hàng là (chủ đề, nội dung); ô yêu cầu cần đạt liệt kê các mức |
| D5 | `content=spec` trên endpoint xuất Word hiện có (đổi hợp đồng API: `docs/api/README.md`, `openapi.json`) |
| D6 | Hộp xuất Word: nhóm chọn "Nội dung file" 3 lựa chọn; chọn bản đặc tả thì ẩn ô Mã đề; các mặc định khác không đổi |
| D7 | Đầu đề không có Mã đề / Điểm bài thi / Mã phách |
| Tên file | Cùng quy tắc với file đề: giao diện "<tên đề> – Bản đặc tả – <dd-mm-yyyy>"; `Content-Disposition` như file đề (`MT….docx`) |
