# D4 – Prompt đường cơ sở (gọi thẳng Gemini) · CHỜ AN DUYỆT

> **Trạng thái: CHỜ AN DUYỆT.** Chưa gọi Gemini lần nào. Sửa chữ trong hai khối `text` bên dưới là đủ:
> `tools/measurement/do_luong/baseline_goi.py` đọc đúng hai khối này (theo dấu `<!-- prompt-1 -->`, `<!-- prompt-2 -->`)
> và thay `{MA_TRAN}` bằng ma trận mẫu `giuaki1-toan8`
> (`back_end/src/main/resources/samples/matrix_giuaki1_toan8.json`: 12 câu trắc nghiệm, 2 câu Đúng/Sai, 4 câu trả lời ngắn).

## Mục đích

So câu của hệ thống (sinh theo ô + QC + cờ) với cách giáo viên có thể làm mà không có hệ thống: dán ma trận vào Gemini rồi
nhận đề. Hai mức:

| Prompt | Ý nghĩa | Cách gọi |
|---|---|---|
| 1. Kiểu giáo viên hay dùng | Dán ma trận như chép từ Word, một câu yêu cầu ngắn | Một lượt, đầu ra chữ tự do |
| 2. Prompt tốt nhất nhóm viết được | Ràng buộc rõ (một đáp án đúng, phạm vi lớp 8, mức độ từng câu, LaTeX), đầu ra JSON cùng các trường với câu hệ thống | Một lượt, `responseMimeType = application/json`, KHÔNG QC, KHÔNG sinh lại |

Cả hai: cùng model sinh với app (`LLM_MODEL`, mặc định `gemini-3.5-flash-lite`), mỗi prompt chạy **2 đề** (2 lượt gọi), rồi
lấy mẫu ~20 câu mỗi prompt theo dạng vào bộ chấm mù chung với câu hệ thống. Không có bước sửa hay chọn lọc nào trước khi
lấy mẫu; câu không parse được ghi ra `parse-loi.jsonl` để sửa tay **phần định dạng** (không sửa nội dung toán), và được
báo số lượng trong chương 4.

`{MA_TRAN}` của prompt 1 là bảng chữ (giống chép từ bản đặc tả); của prompt 2 là danh sách đánh số từng câu kèm mức độ.

<!-- prompt-1 -->
```text
Soạn giúp tôi đề kiểm tra giữa kì I môn Toán 8 theo ma trận dưới đây, kèm đáp án.

{MA_TRAN}
```

<!-- prompt-2 -->
```text
Bạn là giáo viên Toán THCS giàu kinh nghiệm ra đề kiểm tra theo chương trình GDPT 2018. Hãy soạn ĐÚNG 18 câu cho đề
kiểm tra giữa học kì I môn Toán lớp 8, theo đúng danh sách câu dưới đây (mỗi dòng một câu, đúng chủ đề, nội dung, yêu cầu
cần đạt và mức độ đã ghi).

{MA_TRAN}

Quy tắc bắt buộc:
1. Chỉ dùng kiến thức đến hết phần chương trình Toán 8 đã nêu trong ma trận; không dùng kiến thức lớp trên (ví dụ không
   giải phương trình hay bất phương trình bậc hai, không dùng căn bậc hai của biểu thức chứa biến).
2. Câu trắc nghiệm (dang = "mcq"): 4 phương án A, B, C, D, khác nhau về giá trị (không có hai phương án bằng nhau sau khi
   rút gọn); có ĐÚNG MỘT phương án đúng. Các phương án sai là kết quả của lỗi sai học sinh hay mắc.
3. Câu Đúng/Sai (dang = "tf"): một đề dẫn chung và 4 ý a, b, c, d; mỗi ý là một khẳng định chỉ có thể Đúng hoặc Sai,
   mức độ 4 ý theo thứ tự đã ghi; có cả ý Đúng và ý Sai.
4. Câu trả lời ngắn (dang = "sa"): đáp số là MỘT số (số nguyên hoặc số thập phân, tối đa 4 ký tự kể cả dấu "-" và dấu ","),
   không có đơn vị.
5. Đề phải làm được không cần hình vẽ; câu hình học cho số đo bằng lời.
6. Mọi biểu thức toán viết bằng LaTeX trong cặp $...$. Không dùng $$...$$.
7. Tự giải lại từng câu trước khi chốt đáp án; nếu thấy câu có hai đáp án đúng hoặc không có đáp án đúng thì sửa câu.

Trả về DUY NHẤT một mảng JSON 18 phần tử theo thứ tự câu 1 đến 18, mỗi phần tử có các trường:
- "cau": số thứ tự câu (1-18)
- "dang": "mcq", "tf" hoặc "sa"
- "de": đề bài (câu Đúng/Sai: chỉ đề dẫn)
- "phuong_an": với mcq là đối tượng {"A": "...", "B": "...", "C": "...", "D": "..."}; dạng khác là null
- "y": với tf là mảng 4 đối tượng {"vi_tri": "a", "noi_dung": "...", "nhan": "Đ" hoặc "S"}; dạng khác là null
- "dap_an": mcq là một chữ "A"-"D"; sa là chuỗi đáp số; tf là null
- "loi_giai": lời giải ngắn gọn
Vì là JSON, mọi dấu "\" trong LaTeX phải viết thành "\\", ví dụ "$\\frac{1}{2}$".
```

## Ghi chú cho An khi duyệt

- Prompt 1 cố ý ngắn, không nói định dạng: đó là điểm cần đo (giáo viên phải tự tách đáp án, tự sửa công thức).
- Prompt 2 đưa đúng các quy tắc mà prompt hệ thống có (một đáp án đúng, phạm vi lớp, LaTeX, đáp số sa), nhưng không có
  các phần chỉ hệ thống mới có: giao trước chữ đáp án, ví dụ mẫu theo ô, Solver, dedup, sinh lại.
- Nếu muốn prompt 2 sát hơn nữa với prompt hệ thống, có thể chép khối "PHẠM VI KIẾN THỨC" của
  `MatrixPromptDraftV0.gradeScopeBlock` cho lớp 8 vào quy tắc 1.
