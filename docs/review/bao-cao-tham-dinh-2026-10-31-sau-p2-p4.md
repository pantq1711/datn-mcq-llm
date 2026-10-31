> Đánh giá bằng AI đóng vai giáo viên, dùng để chẩn đoán, không phải đánh giá của giáo viên thật.

# Báo cáo thẩm định đề sau P2, P4: đề 5, 6, 7 trong DB `datn_mcq_e2e`

**Đề mới: [ID ĐỀ MỚI] = 5, 6, 7.**
- Mã đề: MT261031230348649, MT261031230626471, MT261031230849519.
- Tạo ngày 31/10/2026 bằng `POST /api/exams/build` với `matrixId: 1` ("Giữa kì I – Toán 8"), không kèm tài liệu, giống đề 1, 3, 4.
- Mỗi đề có 18 chỗ: 3 câu lấy từ ngân hàng (BI1, BI2, BI3) và 15 câu sinh mới. Không chỗ nào sinh lỗi.

**Code lúc tạo đề:** `main` tại `a859c22`, đã gồm:
- P2: đa dạng câu trong đề, PR #30.
- P4: Solver xét từng phương án, PR #29.
- Bản sửa ký hiệu độ và siết prompt ma trận (`ea1ca61`).

Tôi không tìm thấy mục "P5" trong repo nên **không chắc P5 đã merge hay chưa**.

**Cách lấy dữ liệu:**
- Nội dung câu và cờ lấy qua `GET /api/exams/{id}` bằng tài khoản GV1. Cờ `similar_in_exam` của P2 chỉ được tính khi xem đề, nên phải lấy qua API.
- Kết quả QC lấy thêm bằng SELECT chỉ đọc.
- Tôi tự giải lại tất cả các câu. Ký hiệu và cách đếm giống mốc 31/10 (`bao-cao-tham-dinh-2026-10-31.md`).

**Ký hiệu:**
- Các tiêu chí: 1 Toán, 2 Chương trình, 3 Mức độ, 4 Nhiễu, 5 Đ/S, 6 Trả lời ngắn, 7 Trình bày, 8 Trùng.
- Đánh giá: Đ = Đạt, N = Cần sửa nhẹ, K = Không dùng được, – = không áp dụng.
- Cột "Dùng": dùng = dùng ngay, sửa = sửa nhẹ, bỏ = thay câu khác.
- Cột QC:
  - bắt đúng: có cờ và câu phải sửa hoặc bỏ.
  - báo nhầm: có cờ nhưng câu dùng ngay được.
  - bỏ sót: không cờ nhưng câu phải sửa hoặc bỏ.
  - đúng: không cờ và câu dùng ngay được.
- Câu từ ngân hàng không bao giờ bị gắn cờ (chủ ý thiết kế).

## A. Bảng từng câu

| Đề | Câu (id) | Dạng | Ô (mức) | 1 2 3 4 5 6 7 8 | Cờ | QC | Dùng | Ghi chú |
|---|---|---|---|---|---|---|---|---|
| 5 | C1 (BI1) | MCQ | Đơn thức, đa thức (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | dùng | |
| 5 | C2 (67) | MCQ | như trên (B) | N Đ Đ Đ – – N N | có (`similar_in_exam: 3`) | bắt đúng | sửa | C $-7x^3y^2$ là phương án đúng duy nhất. Lời giải vỡ LaTeX (`\$-7x^3y^2\$`) và ghi "các biến $-x, y$". Cùng nhóm "nhận biết đơn thức" với BI1 |
| 5 | C3 (65) | MCQ | như trên (B) | Đ Đ Đ Đ – – Đ K | có (`similar_in_exam: 1`, `2`) | bắt đúng | bỏ | Cùng câu hỏi "biểu thức nào là đơn thức?" với C2, đặt liền nhau. Thay bằng câu hỏi khác về bậc |
| 5 | C4 (66) | MCQ | như trên (B) | Đ Đ Đ Đ – – N Đ | không | bỏ sót | sửa | Bậc 4 đúng. Câu dẫn thiếu dấu "?". Cả 4 phương án lặp "Đa thức $P$ có bậc là". Nên hỏi "Bậc của đa thức $P$ là" và để phương án là số |
| 5 | C5 (68) | MCQ | Phép tính đa thức (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | dùng | $3x^2-7x+4$. Phương án nhiễu bám lỗi dấu khi bỏ ngoặc |
| 5 | C6 (69) | MCQ | Hằng đẳng thức (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | dùng | $25x^2+20x+4$. Nhiễu: thiếu hệ số 2, quên bình phương 5, mất hạng tử giữa |
| 5 | C7 (79) | MCQ | Phân tích nhân tử (B) | Đ Đ Đ Đ – – N N | có (`similar_in_exam: 8`) | bắt đúng | sửa | Câu dẫn có "nhân tử chung lớn nhất" nên chỉ A đúng (B, D bằng đa thức nhưng chưa lớn nhất, C sai). "Hãy nhận biết phương án nào biểu diễn đúng kết quả…" là văn dịch máy |
| 5 | C8 (71) | MCQ | Phân tích nhân tử (B) | Đ Đ Đ Đ – – N K | có (`similar_in_exam: 7`) | bắt đúng | bỏ | Cùng khuôn y hệt C7: $ax^2 - bxy$, 4 phương án cùng dạng, cùng câu dẫn. Thay bằng câu dùng hằng đẳng thức |
| 5 | C9 (73) | MCQ | Tứ giác (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | dùng | B đúng. Nhiễu C "hai góc kề một cạnh đáy bù nhau" bám lỗi nhầm với góc kề cạnh bên |
| 5 | C10 (72) | MCQ | Tứ giác (B) | Đ Đ Đ N – – N K | không | **bỏ sót** | bỏ | **Cùng kiến thức và cùng đáp án với C9** ("hai đường chéo bằng nhau"): làm C9 là biết đáp án C10. Câu dẫn hỏi "độ dài" nhưng A, D nói về vị trí. D "song song" vô lý |
| 5 | C11 (82) | MCQ | Tứ giác (B) | K Đ K Đ – – K N | **có** (`solver_no_correct`, `similar_in_exam: 12`) | **bắt đúng** | bỏ | $G+H=200^\circ$, $G-H=40^\circ$ nên $G=120^\circ$, không có trong A–D. Lời giải viết "Xin lỗi, tính lại…" rồi tự đổi dữ kiện thành $180^\circ$. Bài tổng–hiệu 2 bước là mức Vận dụng |
| 5 | C12 (81) | MCQ | Tứ giác (B) | Đ Đ Đ Đ – – Đ Đ | có (`similar_in_exam: 11`) | báo nhầm* | dùng | $160^\circ$ đúng. *Cờ chỉ đúng cặp trùng với C11. Bỏ C11 thì câu này dùng được |
| 5 | C13 (BI2) | Đ/S | Phép tính đa thức (B,B,H,VD) | Đ Đ N – Đ – Đ Đ | không | – | dùng | Như mốc: $M(-1;2)=11$, nhãn T,T,F,F đúng. Ý d chỉ ở mức Hiểu |
| 5 | C14 (77) | Đ/S | Hằng đẳng thức (B,B,H,VD) | Đ Đ Đ – N – Đ N | có (`warning: label: 4 ý cùng nhãn F`) | bắt đúng | sửa | Tự giải: $Q=20x^2-30x+20$, giá trị nhỏ nhất $35/4=8{,}75$. Nhãn F,F,F,F đúng nhưng cả 4 ý đều Sai. Sửa ý c thành $Q=20x^2-30x+20$ (Đúng). Ý a $(5x-2)^2$ gần C6 $(5x+2)^2$: làm C6 thấy hệ số $20x$ là đoán được ý a Sai |
| 5 | C15 (BI3) | TLN | Đơn thức, đa thức (H) | Đ Đ Đ – – Đ Đ Đ | không | đúng | dùng | 10 |
| 5 | C16 (76) | TLN | Phép tính đa thức (H) | Đ Đ Đ – – Đ N Đ | không | bỏ sót | sửa | $C=x+3$, đáp số 1 đúng. Lời giải để công thức trần, không có cặp `$…$` |
| 5 | C17 (78) | TLN | Phân tích nhân tử (VD) | Đ N* N – – Đ Đ Đ | không | bỏ sót | sửa | $(5x-1)^3=8$ nên $x=0{,}6$, đúng. "0,6" đúng định dạng phiếu. Vẫn là tìm x bằng cách nhận ra lập phương, chỉ ở mức Hiểu. *Không chắc câu tìm x có khớp ô "phân tích dạng phức tạp". Đã viết đúng "thỏa mãn đẳng thức" |
| 5 | C18 (80) | TLN | Tứ giác (H) | Đ Đ Đ – – Đ Đ Đ | không | đúng | dùng | 105 |
| 6 | C1 (BI1) | MCQ | | như đề 5 | không | đúng | dùng | |
| 6 | C2 (83) | MCQ | Đơn thức, đa thức (B) | Đ Đ N Đ – – Đ Đ | không | bỏ sót | sửa | A đúng duy nhất. Nhiễu tốt (sai dấu, trừ nhầm hệ số). "Thu gọn đa thức" là yêu cầu mức Hiểu (chính ma trận ghi ở mức medium của ô C13), không phải nhận biết |
| 6 | C3 (84) | MCQ | như trên (B) | N Đ Đ Đ – – Đ N | không | bỏ sót | sửa | C đúng duy nhất. **Lời giải sai**: ghi các biểu thức còn lại "là đa thức nhưng không phải đơn thức", nhưng $x+\frac{1}{y}$ không phải đa thức. Cùng nhóm nhận biết đơn thức với BI1 |
| 6 | C4 (85) | MCQ | như trên (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | dùng | 5 hạng tử. Nhiễu 4 (quên hạng tử tự do) tốt |
| 6 | C5 (86) | MCQ | Phép tính đa thức (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | dùng | $5x^2-7x+8$ |
| 6 | C6 (89) | MCQ | Hằng đẳng thức (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | dùng | $x^2+8x+16$ |
| 6 | C7 (87) | MCQ | Phân tích nhân tử (B) | N Đ Đ N – – N Đ | không | bỏ sót | sửa | A $5x(2x-3y)$ đúng. Nhưng C $10x(x-1{,}5y)$ cũng bằng đa thức, và nhân tử $10x$ "lớn hơn" $5x$ nếu chấp nhận hệ số thập phân, học sinh khá có thể cãi. Đổi C thành $5x(2x+3y)$. Văn "Hãy nhận biết…" |
| 6 | C8 (88) | MCQ | Phân tích nhân tử (B) | Đ Đ Đ Đ – – Đ K | không | **bỏ sót** | bỏ | Cùng khuôn với C7: đặt nhân tử chung lớn nhất, 3 phương án nhiễu là cách đặt chưa hết. Hệ thống không gắn cờ (dưới ngưỡng 0,82, không biết điểm cụ thể). Thay bằng câu dùng hằng đẳng thức |
| 6 | C9 (90) | MCQ | Tứ giác (B) | Đ Đ Đ Đ – – Đ Đ | có (`similar_in_exam: 12`) | báo nhầm | dùng | $240^\circ$ đúng. C9 hỏi tổng hai góc còn lại, C12 hỏi góc thứ tư: khác dạng con |
| 6 | C10 (91) | MCQ | Tứ giác (B) | Đ Đ Đ N – – Đ Đ | không | bỏ sót | sửa | C đúng. D "luôn kề bù" vô nghĩa với hai góc của tứ giác, và gần trùng nghĩa với A "bù nhau" |
| 6 | C11 (92) | MCQ | Tứ giác (B) | Đ Đ Đ N – – N Đ | không | bỏ sót | sửa | A đúng. Câu dẫn hỏi "độ dài" nhưng B, C nói về vị trí. D "bằng tổng độ dài hai đáy" là phương án độn |
| 6 | C12 (97) | MCQ | Tứ giác (B) | Đ Đ Đ N – – Đ N | có (`similar_in_exam: 9`) | bắt đúng (lệch cặp) | sửa | $100^\circ$ đúng. Trùng thật là với C18 (TLN), cùng dạng "cho 3 góc, tính góc thứ tư". Hệ thống không so MCQ với TLN. Nhiễu 90, 120, 130 là số tùy ý |
| 6 | C13 (BI2) | Đ/S | | như đề 5 | | | dùng | |
| 6 | C14 (95) | Đ/S | Hằng đẳng thức (B,B,H,VD) | Đ Đ Đ – Đ – Đ Đ | không | đúng | dùng | $N=-12x^2-6x+26$, giá trị lớn nhất $26{,}75$ và không có giá trị nhỏ nhất. Nhãn T,T,F,F đúng |
| 6 | C15 (BI3) | TLN | | như đề 5 | | | dùng | |
| 6 | C16 (94) | TLN | Phép tính đa thức (H) | Đ Đ Đ – – Đ Đ Đ | không | đúng | dùng | $C=-x+4$, đáp số 8 |
| 6 | C17 (96) | TLN | Phân tích nhân tử (VD) | Đ N* N – – Đ Đ Đ | không | bỏ sót | sửa | $(2x+3)^3=125$ nên $x=1$, đúng. Cùng nhận xét với câu 78 |
| 6 | C18 (98) | TLN | Tứ giác (H) | Đ Đ Đ – – Đ Đ Đ | không | đúng | dùng | 90 |
| 7 | C1 (BI1) | MCQ | | như đề 5 | không | đúng | dùng | |
| 7 | C2 (114) | MCQ | Đơn thức, đa thức (B) | Đ Đ Đ Đ – – Đ Đ | có (`similar_in_exam: 3`) | báo nhầm* | dùng | Bậc 6. Nhiễu tốt: 7 là hệ số sau thu gọn, 8 là hạng tử tự do, 4 là bậc theo $x$. *Cờ chỉ đúng cặp trùng với C3 |
| 7 | C3 (115) | MCQ | như trên (B) | Đ Đ Đ Đ – – Đ K | có (`similar_in_exam: 2`) | bắt đúng | bỏ | Cùng hỏi "bậc của đa thức $P$" như C2, đặt liền nhau |
| 7 | C4 (101) | MCQ | như trên (B) | Đ N Đ Đ – – Đ Đ | không | bỏ sót | sửa | B đúng duy nhất. "Đa thức một biến" là trọng tâm Toán 7. Ô ma trận là đa thức (nhiều biến) của Toán 8. Đổi thành "biểu thức nào là đa thức?" |
| 7 | C5 (103) | MCQ | Phép tính đa thức (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | dùng | $11x^2-5x+5$ |
| 7 | C6 (116) | MCQ | Hằng đẳng thức (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | dùng | $4x^2+12x+9$ |
| 7 | C7 (117) | MCQ | Phân tích nhân tử (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | dùng | $3x$. Nhiễu tốt: $3x^2$ không là nhân tử của $9x$, còn $6x$ và $9x$ không chia hết hệ số còn lại |
| 7 | C8 (105) | MCQ | Phân tích nhân tử (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | dùng | $5x^2(x-2)$, câu dẫn có "lớn nhất". Khác dạng con với C7 (C7 hỏi nhân tử, C8 hỏi kết quả), chấp nhận được |
| 7 | C9 (118) | MCQ | Tứ giác (B) | Đ Đ Đ N – – Đ Đ | có (`similar_in_exam: 10`) | bắt đúng (lệch lý do) | sửa | $360^\circ$ đúng. Nhiễu 90°, 270° không gắn với lỗi nào (như câu 20 ở mốc). C9 hỏi định lí, C10 áp dụng định lí: không trùng |
| 7 | C10 (107) | MCQ | Tứ giác (B) | Đ Đ Đ N – – Đ N | có (`similar_in_exam: 9`) | bắt đúng (lệch cặp) | sửa | $90^\circ$ đúng. Trùng thật là với C18, cùng dạng "cho 3 góc, tính góc thứ tư". Nhiễu 80, 100, 110 là số tùy ý |
| 7 | C11 (120) | MCQ | Tứ giác (B) | Đ Đ Đ N – – Đ Đ | không | bỏ sót | sửa | A đúng. B, C, D là quan hệ độ dài tùy tiện, C và D đối xứng nhau, không phản ánh lỗi nào |
| 7 | C12 (113) | MCQ | Tứ giác (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | dùng | B đúng. Nhiễu A "hai góc kề một cạnh bên luôn phụ nhau" bám lỗi nhầm bù/phụ. C sai nhờ chữ "luôn" |
| 7 | C13 (BI2) | Đ/S | | như đề 5 | | | dùng | |
| 7 | C14 (109) | Đ/S | Hằng đẳng thức (B,B,H,VD) | N Đ Đ – N – N Đ | không | bỏ sót | sửa | $K=-14x^2-25x+13$. Nhãn a T, b T, c F đúng. Ý d "đạt giá trị nhỏ nhất bằng 13 khi x là số thực dương": với $x>0$ thì $K<13$ và không đạt giá trị đó, nên nhãn F đúng. Nhưng phát biểu ghép điều kiện lạ, và lời giải ý d không chứng minh gì. Viết lại ý d, ví dụ "Tại $x=-1$ thì $K=24$" |
| 7 | C15 (BI3) | TLN | | như đề 5 | | | dùng | |
| 7 | C16 (110) | TLN | Phép tính đa thức (H) | Đ Đ Đ – – Đ Đ Đ | không | đúng | dùng | $C=-x+2$, đáp số 7 |
| 7 | C17 (111) | TLN | Phân tích nhân tử (VD) | Đ N* N – – Đ Đ Đ | không | bỏ sót | sửa | $(4x-1)^3=27$ nên $x=1$, đúng. Cùng nhận xét với câu 78 |
| 7 | C18 (112) | TLN | Tứ giác (H) | Đ Đ Đ – – Đ Đ Đ | không | đúng | dùng | 100 |

## B. Tổng hợp

**Theo câu (54 vị trí):**

| Mức dùng | Số câu | Tỉ lệ |
|---|---|---|
| Dùng ngay | 29 | 53,7% |
| Sửa nhẹ | 19 | 35,2% |
| Bỏ | 6 | 11,1% |

- Nếu không tính 9 vị trí là câu từ ngân hàng (cả 9 đều dùng ngay), còn **45 câu do hệ thống sinh: 20 dùng ngay (44,4%), 19 sửa nhẹ (42,2%), 6 bỏ (13,3%)**.
- Theo từng đề (dùng / sửa / bỏ): đề 5 là 8/6/4, đề 6 là 10/7/1, đề 7 là 11/6/1.

**Lỗi theo loại** (45 câu sinh, đếm theo vị trí, một câu có thể dính nhiều loại):

| Loại lỗi | Số vị trí | Chi tiết |
|---|---|---|
| Toán | 5 | Không có đáp án đúng: 1 (câu 82, Solver đã bắt). Phương án gây tranh cãi: 1 (câu 87). Lời giải sai chi tiết: 3 (câu 67, 84, 109). **Không còn câu nào trong đề có ≥ 2 phương án tương đương** |
| Chương trình | 4 | 1 câu chắc chắn (câu 101, đa thức một biến). 3 câu không chắc (câu 78, 96, 111, ô C17) |
| Mức độ | 5 (+BI2 = 6) | Câu 82, 83, 78, 96, 111 |
| Nhiễu | 8 | Câu 72, 87, 91, 92, 97, 118, 107, 120 |
| Đúng/sai | 2 | Câu 77 (4 ý cùng Sai), câu 109 (ý d mơ hồ) |
| Trả lời ngắn | 0 | |
| Trình bày | 10 | **Ký hiệu độ sai: 0.** "thỏa mãn biểu thức": 0. Còn lại: văn "Hãy nhận biết…" (79, 71, 87), câu dẫn hỏi "độ dài" nhưng phương án nói vị trí (72, 92), lời giải vỡ hoặc thiếu LaTeX (67, 76), lời giải tự sửa đề (82), câu dẫn lặp (66), ý mơ hồ (109) |
| Trùng | 12 | Mức K: 5 (câu 65, 71, 72, 88, 115). Mức N: 7 (67, 79, 82, 77, 84, 97, 107) |

**Lọc trước khi vào đề** (mốc 31/10 không đo phần này):
- Hệ thống sinh 56 câu cho 45 chỗ.
- Bước format loại 7 câu vì lỗi `$` (id 75, 99, 100, 102, 106, 108, 119).
- Solver v3/v4 phát hiện "nhiều phương án đúng" ở 4 câu (70, 74, 93, 104). Các câu này được sinh lại theo `ExamBuildService` (giữ làm câu dự phòng) và **không vào đề**.
- Câu 82 ("không có phương án đúng") vào đề có cờ. Có lẽ vì đã hết lượt sinh lại, tôi không kiểm lại lý do.

**Hiệu quả QC trên 45 câu sinh có trong đề:**
- 13 câu có cờ: 10 bắt đúng, 3 báo nhầm.
  - 2 trong 3 câu báo nhầm (81, 114) là câu nên giữ lại trong một cặp trùng thật. Cờ chỉ đúng cặp, chỉ là bật ở cả hai câu.
  - Chỉ câu 90 là báo nhầm thật.
- Trong 10 câu bắt đúng, 7 câu có lý do khớp với lỗi chính. 3 câu (97, 118, 107) có cờ trùng nhưng chỉ sai cặp, hoặc lỗi chính là nhiễu.
- Trong 25 câu phải sửa hoặc bỏ, **QC bỏ sót 15**. Phân loại theo bước QC lẽ ra phải bắt:

| Bước QC lẽ ra phải bắt | Số câu | Các câu |
|---|---|---|
| Solver | 1 | 87 (phương án $10x(x-1{,}5y)$ cũng bằng đa thức) |
| Dedup trong đề | 2 | 72 (cùng kiến thức và cùng đáp án với 73 nhưng khác chữ), 88 (cùng khuôn với 87, dưới ngưỡng) |
| Format | 1 | 76 (lời giải để công thức ngoài `$…$`) |
| Khác | 11 | Mức độ hoặc chương trình: 83, 101, 78, 96, 111. Nhiễu yếu: 91, 92, 120. Lời giải hoặc ngôn ngữ: 66, 84, 109 |

Ghi chú về câu Đúng/Sai:
- Mẫu nhãn mục tiêu vẫn không được tuân theo: câu 95 và 109 nhận T,T,F,F (mục tiêu lần lượt là F,F,T,T và F,F,F,T), còn câu 77 nhận F,F,F,F.
- Ở đề 6 và đề 7, cả 2 câu Đ/S (gồm BI2) đều là T,T,F,F, nên học sinh có thể đoán đáp án theo vị trí ý.

## C. So sánh với mốc 31/10

**Cỡ mẫu:**
- Mốc 31/10 có 4 đề, 60 vị trí (51 câu sinh, 9 câu ngân hàng). Đề 2 dùng ma trận khác (Bài 6, 6 câu). 2 câu sinh (25, 26) là bản giáo viên sửa trong E2E.
- Lần này có 3 đề, 54 vị trí (45 câu sinh, 9 câu ngân hàng), cùng ma trận 1.
- Để so cùng điều kiện, cột "Mốc (đề 1, 3, 4)" chỉ lấy 3 đề cùng ma trận: 54 vị trí, 45 câu sinh.

| Chỉ số | Mốc (4 đề, 60 vị trí) | Mốc (đề 1, 3, 4, 54 vị trí) | Lần này (đề 5, 6, 7, 54 vị trí) | Thay đổi so với mốc cùng ma trận |
|---|---|---|---|---|
| Dùng ngay, tất cả vị trí | 31 (51,7%) | 26 (48,1%) | 29 (53,7%) | tăng 5,6 điểm % |
| Sửa nhẹ, tất cả vị trí | 22 (36,7%) | 22 (40,7%) | 19 (35,2%) | giảm |
| Bỏ, tất cả vị trí | 7 (11,7%) | 6 (11,1%) | 6 (11,1%) | không đổi |
| Dùng ngay, câu sinh | 22/51 (43%) | 17/45 (37,8%) | 20/45 (44,4%) | tăng 6,6 điểm % |
| Sửa nhẹ, câu sinh | 22/51 (43%) | 22/45 (48,9%) | 19/45 (42,2%) | giảm |
| Bỏ, câu sinh | 7/51 (14%) | 6/45 (13,3%) | 6/45 (13,3%) | không đổi |
| Câu sinh có cờ | 2/51 | 1/45 | 13/45 | tăng mạnh |
| Bắt đúng | 1 | 1 | 10 | tăng mạnh |
| Báo nhầm | 1 | 0 | 3 (1 nhầm thật) | tăng |
| Bỏ sót / số câu phải sửa hoặc bỏ | 28/29 | 28/28 | 15/25 | giảm |
| Tỉ lệ bắt (recall) | 3% | 4% | 40% | tăng mạnh |
| Độ chính xác của cờ (precision) | 50% | 100% (1/1) | 77% | (mẫu mốc quá nhỏ) |

Ghi chú: cột "Mốc (đề 1, 3, 4)" tôi tự tách từ bảng A của mốc. Câu 30 (báo nhầm) và câu 29 thuộc đề 2 nên không có trong cột này.

**Theo loại lỗi** (đếm theo vị trí, câu sinh):

| Loại lỗi | Mốc 31/10 | Lần này | Đánh giá |
|---|---|---|---|
| Nhiều phương án tương đương / thiếu "luôn" | 8 | 0 trong đề (1 câu gây tranh cãi), 4 câu bị Solver chặn trước khi vào đề | **Đã khắc phục** |
| Sai đáp án hoặc không có đáp án | 2 (đều do giáo viên sửa, LLM 0) | 1 (LLM, Solver bắt được) | Không đổi về số lượng. Lỗi lần này bị bắt |
| Lời giải sai hoặc lệch | 1 (câu 25) | 4 (67, 82, 84, 109) | **Tăng** |
| Ký hiệu độ sai (•, △) | 6 câu | 0 | **Đã khắc phục** |
| "thỏa mãn biểu thức" | 3 | 0 | **Đã khắc phục** |
| Trình bày (tổng) | 16 | 10 | Giảm |
| Chương trình | 4 (1 chắc) | 4 (1 chắc) | Không đổi. Ô C17 vẫn ra dạng tìm x ở cả 3 đề |
| Mức độ | 5 | 5 (+BI2) | Không đổi |
| Nhiễu | 12 | 8 | Giảm |
| Đúng/sai | 1 | 2 | Tăng nhẹ. Mẫu nhãn và thiên lệch theo vị trí vẫn còn |
| Trả lời ngắn | 0 | 0 | Không đổi |
| Trùng (tổng) | 12 (11 ở đề 1, 3, 4) | 12 | Không đổi về số lượng |
| Trùng mức K (phải thay) | 5 (4 ở đề 1, 3, 4) | 5 | Không đổi về số lượng. **Số được gắn cờ tăng từ 0 lên 3** |
| Bỏ sót do Solver | 9 | 1 | Giảm mạnh |
| Bỏ sót do Dedup | 7 | 2 | Giảm |
| Bỏ sót do Format | 5 | 1 | Giảm |
| Bỏ sót loại khác (mức độ, chương trình, nhiễu, lời giải) | 7 | 11 | Tăng. Các lỗi này giờ chiếm phần lớn số bỏ sót |

Nhận xét về P2:
- Lớp xem đề (cờ `similar_in_exam`) bắt được 3/5 câu trùng phải thay: 65, 71, 115.
- Lớp sinh (gán chuẩn đầu ra theo thứ tự) không giúp được ô 1, 4, 5: mỗi ô này chỉ có **một** chuẩn đầu ra, nên mọi chỗ trong ô nhận cùng một yêu cầu.
- Dù vậy, đề 6 và 7 ở ô Tứ giác đã phủ được 3–4 dạng con khác nhau. Đề 5 vẫn có 2 cặp trùng.

## D. 5 câu tệ nhất lần này

1. **Đề 5 C11 (câu 82):** "Cho tứ giác $EFGH$ có tổng số đo góc $E$ và góc $F$ bằng $160^{\circ}$, hiệu số đo giữa góc $G$ và góc $H$ bằng $40^{\circ}$. Số đo của góc $G$ là bao nhiêu độ? A. $110^{\circ}$ B. $70^{\circ}$ C. $100^{\circ}$ D. $80^{\circ}$"
   - Đáp án thật là 120°, không có trong 4 phương án.
   - Lời giải lưu kèm: "…$= 120^{\circ}$ (không có trong phương án). Xin lỗi, tính lại… Ta sửa lại: tổng góc $E$ và $F$ là $180^{\circ}$…", tức là tự đổi đề cho khớp đáp án.
   - Câu ở mức Vận dụng nhưng nằm trong ô Nhận biết. Hệ thống đã gắn cờ `solver_no_correct`.
2. **Đề 5 C9 (câu 73) và C10 (câu 72):**
   - C9: "Trong các phát biểu sau về tính chất của hình thang cân, phát biểu nào là ĐÚNG? … B. Hình thang cân có hai đường chéo bằng nhau"
   - C10: "…Phát biểu nào sau đây là ĐÚNG khi nói về độ dài hai đường chéo của hình thang cân đó? … B. Hai đường chéo luôn bằng nhau"
   - Hai câu liền nhau, cùng một kiến thức, cùng đáp án B. Không có cờ, vì khác chữ nên so 3-gram không bắt được.
3. **Đề 5 C2 (câu 67) và C3 (câu 65):** "Trong các biểu thức đại số cho dưới đây, biểu thức nào là đơn thức?" và "Trong các biểu thức đại số sau, biểu thức nào là đơn thức?"
   - Hai câu liền nhau, cùng câu hỏi, cùng vị trí đáp án C. Câu 67 còn có lời giải vỡ LaTeX ("$\$-7x^3y^2\$$", "các biến $-x, y$"). Hệ thống đã gắn cờ.
4. **Đề 6 C7 (câu 87) và C8 (câu 88):** "…phân tích đa thức $10x^2 - 15xy$ thành nhân tử bằng cách đặt nhân tử chung lớn nhất? … C. $10x(x - 1{,}5y)$" và "…phân tích đa thức $12x^2 - 18x$ thành nhân tử bằng cách đặt nhân tử chung lớn nhất?"
   - Hai câu cùng khuôn nhưng không có cờ.
   - Phương án C của câu 87 cũng bằng đa thức đã cho, và nhân tử $10x$ "lớn hơn" $5x$, nên học sinh có thể cãi.
5. **Đề 7 C14 (câu 109) ý d:** "Giá trị của biểu thức $K$ đạt giá trị nhỏ nhất bằng $13$ khi $x$ là số thực dương."
   - Nhãn Sai là đúng, nhưng phát biểu ghép điều kiện $x>0$ một cách lạ, học sinh không rõ cần chứng minh gì.
   - Lời giải chỉ viết "không phải là hàm bậc nhất và cũng không có giá trị nhỏ nhất bằng 13 tại $x>0$", không chứng minh. Không có cờ.

Suýt vào danh sách:
- Câu 84: lời giải gọi $x+\frac{1}{y}$ là đa thức.
- Câu 77: cả 4 ý đều Sai.

## E. Kết luận nếu là tổ trưởng

- **Không cho dùng nguyên trạng đề nào**, nhưng đề 6 và đề 7 đã gần mức dùng được.
- **Đề 7:** sửa 6 câu (thay nhiễu ở 3 câu Tứ giác, đổi câu 101, viết lại ý d câu 14, chấp nhận hoặc đổi C17) và thay câu 115. Khoảng 1 giờ.
- **Đề 6:** sửa 7 câu và thay câu 88. Khoảng 1 giờ.
- **Đề 5:** thay 4 câu (65, 71, 72, 82) và sửa 6 câu. Khoảng 1,5 giờ. Đề này chưa nên dùng trước khi thay đủ.

Ước lượng cho một đề 18 câu:

| Việc | Mốc 31/10 | Lần này |
|---|---|---|
| Tự giải và soát lại 18 câu | ~30 phút | ~25–30 phút (vẫn phải làm, vì 60% lỗi chưa có cờ) |
| Sửa câu (3–5 phút mỗi câu) | 6–8 câu, ~30 phút | 6–7 câu, ~25 phút |
| Thay câu (5–10 phút mỗi câu) | 2–3 câu, ~20 phút | 1 câu (đề 6, 7) đến 4 câu (đề 5), ~10–35 phút |
| **Tổng** | **~1–1,5 giờ** | **~1 giờ (đề 6, 7), ~1,5 giờ (đề 5)** |

- Thời gian giảm ít. Phần cải thiện lớn nhất là **rủi ro**: không còn câu nào có 2 đáp án đúng lọt vào đề, ký hiệu độ in đúng, và câu sai đáp án duy nhất đã có cờ.
- Các lỗi còn lại chủ yếu là lỗi sư phạm (mức độ, nhiễu, trùng kiến thức), không làm học sinh mất điểm oan.
- Phần tiết kiệm so với tự soạn (3–4 giờ, không chắc) khoảng 65–70%.

## F. Đề xuất cải thiện còn lại (xếp theo tác động lên độ chính xác)

1. **Kiểm lời giải, không chỉ đáp án.**
   - Lỗi liên quan: 82, 84, 67, 109; 4 trên 5 lỗi Toán còn lại nằm ở lời giải.
   - Solver không đọc lời giải nên các lỗi này lọt qua.
   - Tối thiểu: chặn ở bước format các cụm tự sửa như "Xin lỗi", "tính lại", "ta sửa lại", "không có trong phương án" (giống cách `TfExplanationLeak` đang làm cho Đ/S, mở rộng sang mcq và tln).
   - Thêm một lượt kiểm "lời giải có khớp đề và đáp án không".
2. **Trùng kiến thức trong đề (vẫn là nguyên nhân chính của câu bỏ).**
   - Lỗi liên quan: 72, 88, 97, 107. Trong 6 câu bỏ thì 5 là câu trùng.
   - Hai MCQ cùng ô có đáp án đúng giống nhau sau chuẩn hóa thì gắn cờ. Cách này bắt được cặp 73/72 mà so 3-gram bỏ qua.
   - So MCQ với TLN cùng chủ đề (bắt được 97 và 107 với C18).
   - Thêm danh sách **dạng con** cho từng ô trong ma trận. Ví dụ ô 1: nhận biết đơn thức / đa thức / bậc / hạng tử; ô 4: đặt nhân tử chung / hằng đẳng thức / nhóm hạng tử; ô 5: tổng góc / góc thứ tư / góc kề đáy / đường chéo / cạnh bên. Lớp sinh của P2 sẽ xoay vòng theo dạng con thay vì theo chuẩn đầu ra, vì mỗi ô hiện chỉ có 1 chuẩn đầu ra.
3. **Ô C17 "phân tích đa thức thành nhân tử dạng phức tạp".**
   - Lỗi liên quan: 78, 96, 111. Cả 3 đề đều ra cùng khuôn "tìm x từ lập phương = số", chỉ ở mức Hiểu.
   - Prompt cho ô này phải yêu cầu phân tích thật, hoặc kết quả phải là số chỉ có được sau khi phân tích. Ví dụ: "phân tích $x^3 - 4x^2 + 4x$ rồi tính giá trị tại…".
   - Ô này cần giáo viên chốt dạng câu hỏi trước khi sửa prompt.
4. **Mẫu nhãn Đ/S.**
   - Lỗi liên quan: 77, 95, 109. 3/3 câu lệch mẫu mục tiêu, 1 câu toàn Sai, và vị trí T,T,F,F lặp lại.
   - Đây chính là task còn chờ "thiên lệch nhãn theo vị trí" (đưa lời giải lên trước trong JSON, bắt buộc lại mẫu nhãn). Nên làm trước khi đo tiếp.
5. **Mức độ và chương trình.**
   - Lỗi liên quan: 82, 83, 101.
   - Quy tắc trong prompt: ô Biết của MCQ tối đa 1 bước tính, không thu gọn.
   - Lập danh sách khái niệm Toán 7 không được làm trọng tâm, ví dụ "đa thức một biến", "hệ số cao nhất".
6. **Phương án nhiễu.**
   - Lỗi liên quan: 72, 91, 92, 107, 118, 120, 97.
   - Cấm các cụm "kề bù" (với góc tứ giác) và "bằng tổng độ dài hai đáy".
   - Câu dẫn hỏi "độ dài" thì cả 4 phương án phải nói về độ dài.
   - Nhiễu cho câu tính góc phải là kết quả của một lỗi cụ thể: dùng 180° thay vì 360°, quên một góc, lấy góc bù.
7. **Format lời giải.**
   - Lỗi liên quan: 76, 67.
   - Lời giải để công thức ngoài `$…$` hoặc có `\$` thì gắn cảnh báo bật cờ, như đã làm cho phương án.
8. **Đa dạng giữa các đề cùng ma trận** (ngoài phạm vi tiêu chí 8, nhưng ảnh hưởng đến đề dự phòng):
   - C14, C16, C17, C18 của 3 đề giống hệt khuôn, chỉ khác số. Ví dụ ô C16 luôn là "$C = A - kx \cdot B$ tại $x = …$".
   - 3 câu ngân hàng lặp lại ở cả 6 đề dùng ma trận 1.
