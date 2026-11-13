# D6 – Hướng dẫn gán nhãn cặp câu trùng

Dành cho người gán nhãn (An gán toàn bộ; Quân gán độc lập nếu kịp). Nhãn dùng để tính precision/recall của bộ loại trùng
3-gram (`DedupChecker`) ở các ngưỡng 0,50–0,95.

## 1. Quy tắc nghiệp vụ

> **Trùng** = hai câu hỏi **cùng nội dung VÀ cùng số liệu**; chỉ khác cách diễn đạt.
> **Đổi số liệu = KHÔNG trùng** (ý giáo viên: câu chỉ đổi số không tính là trùng; đó là "cùng dạng", dùng được trong
> hai đề khác nhau).

Quy tắc này khớp với cách `DedupChecker` được thiết kế (Javadoc lớp `back_end/.../qc/checker/DedupChecker.java`):

| Trong `DedupChecker` | Với người gán nhãn |
|---|---|
| Cùng "dấu vân tay" sau chuẩn hóa (khoảng trắng, cách viết LaTeX, thứ tự phương án) | Trùng |
| Cosine 3-gram cao **và** cùng số liệu/công thức trong thân câu **và** cùng phủ định | Trùng nếu thật sự cùng câu hỏi |
| Khác số liệu/công thức trong thân câu (chốt chặn số liệu) | Không trùng |
| Một câu có phủ định ("không", "sai"), câu kia không (chốt chặn phủ định) | Không trùng |
| Câu Đ/S: đề dẫn giống và ít nhất 3/4 ý ghép được thành cặp giống nhau | Trùng |
| Câu trả lời ngắn: thân câu + **giá trị** đáp số ("−0,75" và "$-\frac{3}{4}$" là một) | Trùng khi cùng đề và cùng giá trị đáp số |

Bạn **không** cần biết điểm cosine (phiếu không hiện). Gán theo ý nghĩa, không theo độ giống chữ.

## 2. Ba nhãn

| Nhãn | Chọn khi |
|---|---|
| **Trùng** | Học sinh đã làm câu thứ nhất thì làm câu thứ hai không cần làm lại: cùng đối tượng, cùng số liệu, cùng câu hỏi, cùng đáp án. Khác nhau chỉ ở cách nói ("Tính giá trị" / "Giá trị của ... bằng"), thứ tự phương án, cách viết công thức ($2x$ / $2 \cdot x$), dấu câu. Câu Đ/S: đề dẫn như nhau và ít nhất 3 trong 4 ý như nhau (thứ tự ý có thể khác). |
| **Không trùng** | Đổi số liệu hoặc biểu thức (dù cùng dạng bài); cùng số liệu nhưng hỏi điều khác (bậc / hệ số / giá trị); một câu hỏi "đúng", câu kia hỏi "không đúng"; cùng đề nhưng đáp số khác; khác chủ đề. |
| **Không chắc** | Hiếm. Ví dụ hai câu cùng số liệu nhưng một câu thêm điều kiện làm đổi cách giải. Ghi lý do vào Ghi chú. Cặp "Không chắc" bị loại khỏi P/R (báo số lượng). |

## 3. Ví dụ

| Câu thứ nhất | Câu thứ hai | Nhãn | Vì sao |
|---|---|---|---|
| Kết quả của phép nhân $2x \cdot 3x^2$ là | Tích của $2x$ và $3x^2$ bằng | Trùng | Diễn đạt lại, cùng số liệu |
| Kết quả của phép nhân $2x \cdot 3x^2$ là | Kết quả của phép nhân $3x \cdot 4x^2$ là | **Không trùng** | Đổi số liệu |
| Khẳng định nào sau đây **đúng** về hình thang cân? | Khẳng định nào sau đây **không đúng** về hình thang cân? | Không trùng | Lệch phủ định |
| Bậc của đa thức $x^3 + 2x + 1$ là | Hệ số tự do của đa thức $x^3 + 2x + 1$ là | Không trùng | Cùng số liệu, hỏi điều khác |
| Đ/S: "Cho $P=(x+2)^2$" a) b) c) d) | Cùng đề dẫn; 3 ý giống, ý d khác | Trùng | Đề dẫn giống, 3/4 ý giống |
| SA: Tính $A=2x-3$ tại $x=1{,}25$ (đáp số −0,5) | SA: Giá trị của $2x-3$ khi $x=1{,}25$ (đáp số $-\frac{1}{2}$) | Trùng | Cùng đề, cùng giá trị đáp số |

## 4. Quy trình

1. Mở `cap-gan-nhan.docx` (đọc công thức dễ) và `cap-<tên>.xlsx` (phiếu). Mỗi cặp có số **Cặp**; thứ tự cặp đã xáo,
   không theo độ giống.
2. **Luyện tập trước:** sheet **Luyện tập** (đứng đầu phiếu, cũng ở đầu `cap-gan-nhan.docx`) có 3 cặp mẫu L1–L3 lấy từ
   các ví dụ ở mục 3. Gán thử, rồi đối chiếu cột *Đáp án mẫu*: L1 diễn đạt lại cùng số liệu → Trùng; L2 đổi số liệu →
   Không trùng; L3 (trả lời ngắn) cùng đề, cùng giá trị đáp số → Trùng. Ba cặp này **không tính vào kết quả** (phân tích
   chỉ đọc sheet **Gán nhãn cặp**).
3. Sang sheet **Gán nhãn cặp**: chọn nhãn ở cột **Nhãn**; ghi chú tùy ý.
4. Người thứ hai (nếu có) gán độc lập, không xem phiếu người thứ nhất.
