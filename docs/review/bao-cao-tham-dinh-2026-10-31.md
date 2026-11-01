> Đánh giá bằng AI đóng vai giáo viên, dùng để chẩn đoán, không phải đánh giá của giáo viên thật.

# Báo cáo thẩm định đề: 4 đề trong DB `datn_mcq_e2e`

Tôi chỉ dùng SELECT để đọc dữ liệu, không gọi API và không gọi Gemini. Cờ "cần xem kỹ" được tính lại từ `quality_check_result` và `question_grounding` theo logic của `ExamViewService.item` + `QcSummary.reviewReasons`. Tôi đã tự giải lại tất cả các câu.

**Về nguồn gốc câu:**
- Câu từ ngân hàng không bao giờ bị gắn cờ, đây là chủ ý thiết kế. Có 3 câu như vậy: BI1 (từ câu sinh 1), BI2 (từ câu sinh 22), BI3 (từ câu sinh 14). Chúng được dùng lại trong đề 1, 3 và 4.
- **Câu sinh 25 và 26 là bản giáo viên sửa trong lúc chạy E2E** (`exam_slot_action` 5 và 6), không phải do LLM sinh. Bản gốc của LLM là câu 3 và câu 23, cả hai đều đúng.

**Ký hiệu trong bảng:**
- Các tiêu chí: 1 Toán, 2 Chương trình, 3 Mức độ, 4 Nhiễu, 5 Đ/S, 6 Trả lời ngắn, 7 Trình bày, 8 Trùng.
- Đánh giá: Đ = Đạt, N = Cần sửa nhẹ, K = Không dùng được, – = không áp dụng.
- Tiêu chí đạt thì không ghi lý do. Tiêu chí không đạt có lý do ở cột Ghi chú.

## A. Bảng từng câu

| Đề | Câu (id) | Dạng | Ô (mức) | 1 2 3 4 5 6 7 8 | Cờ | QC | Ghi chú |
|---|---|---|---|---|---|---|---|
| 1 | C1 (BI1) | MCQ | Đơn thức, đa thức (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | |
| 1 | C2 (24) | MCQ | như trên (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | Gần C1 nhưng hỏi khác ý, chấp nhận được |
| 1 | C3 (25) | MCQ | như trên (B) | K Đ Đ K – – N Đ | không | **bỏ sót** | Phương án D "6 km" đọc là đơn thức 6, nên có 2 đáp án. Lời giải nhắc $x-\frac1x$ nhưng phương án này không còn. Sửa: D → `$x - \frac{1}{x}$`. Cờ bị tắt vì cảnh báo `\text{}` thuộc loại "đã sửa escape" |
| 1 | C4 (4) | MCQ | (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | Bậc 6 đúng |
| 1 | C5 (6) | MCQ | Phép tính đa thức (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | Các phương án nhiễu bám đúng lỗi dấu |
| 1 | C6 (5) | MCQ | Hằng đẳng thức (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | |
| 1 | C7 (7) | MCQ | Phân tích nhân tử (B) | N Đ Đ K – – Đ N | không | **bỏ sót** | B $3(x^2-4x)$ cũng là một cách phân tích đúng. Sửa câu dẫn: "…bằng cách đặt nhân tử chung lớn nhất". Thay B bằng $3x(x+4)$ |
| 1 | C8 (8) | MCQ | Phân tích nhân tử (B) | N Đ Đ K – – Đ N | không | **bỏ sót** | B và C đều bằng đa thức đã cho. Cùng khuôn với C7, nên đổi sang dạng dùng hằng đẳng thức (ví dụ $x^2-9$) |
| 1 | C9 (20) | MCQ | Tứ giác (B) | Đ Đ Đ N – – N Đ | không | bỏ sót | Câu dẫn "Cho tứ giác ABCD có tổng… là bao nhiêu?" sai ngữ pháp. Sửa: "Tổng số đo các góc của một tứ giác bằng". Nhiễu 90° và 270° không gắn với lỗi nào |
| 1 | C10 (21) | MCQ | Tứ giác (B) | Đ Đ Đ N – – Đ N | không | bỏ sót | Cùng dạng "tính góc thứ tư" với C18. Nhiễu 70, 90, 100 là số tùy ý |
| 1 | C11 (11) | MCQ | Tứ giác (B) | Đ Đ Đ Đ – – N Đ | không | bỏ sót | Lặp "sau đây" hai lần. Đề hỏi "tính chất" nhưng phương án là định nghĩa |
| 1 | C12 (15) | MCQ | Tứ giác (B) | N Đ Đ N – – N Đ | không | bỏ sót | "Biết độ dài hai đường chéo là MP và NQ" sai nghĩa. Với một hình thang cân cụ thể ("này"), A "vuông góc" có thể đúng. Sửa thành "…của hình thang cân luôn…". D là phương án độn |
| 1 | C13 (BI2) | Đ/S | Phép tính đa thức (B,B,H,VD) | Đ Đ N – Đ – Đ Đ | không (đã duyệt) | – | Nhãn T,T,F,F đúng ($M(-1;2)=11$). Ý d chỉ ở mức Hiểu (nhận ra không chia hết), chưa đạt yêu cầu Vận dụng ở ô ma trận |
| 1 | C14 (13) | Đ/S | Hằng đẳng thức (B,B,H,VD) | Đ Đ Đ – N – N Đ | không | bỏ sót | Thu gọn được $A=-4x$, nhãn F,F,F,T đúng. Ý b đưa thêm tên "biểu thức B" thừa, nên viết "Thu gọn A ta được $A=-6x+18$". Tỉ lệ 3 S / 1 Đ hơi lệch |
| 1 | C15 (BI3) | TLN | Đơn thức, đa thức (H) | Đ Đ Đ – – Đ Đ Đ | không | đúng | Đáp số 10 đúng |
| 1 | C16 (16) | TLN | Phép tính đa thức (H) | Đ Đ Đ – – Đ Đ Đ | không | đúng | $C=x+2$, đáp số −1 đúng |
| 1 | C17 (26) | TLN | Phân tích nhân tử (VD) | K N N – – Đ N Đ | **có** (Solver lệch) | **bắt đúng** | Đáp án lưu 2,75. Tự giải $(x-1)^3=8$ được $x=3$, lời giải trong đề cũng ra 3. Viết "thỏa mãn biểu thức" phải là "đẳng thức". Câu là tìm x, không phải phân tích nhân tử dạng phức tạp, và chỉ ở mức Hiểu |
| 1 | C18 (17) | TLN | Tứ giác (H) | Đ Đ Đ – – Đ Đ Đ | không | đúng | Đáp số 75 đúng |
| 2 | C1 (32) | MCQ | Bài 6 (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | |
| 2 | C2 (27) | MCQ | Bài 6 (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | |
| 2 | C3 (29) | MCQ | Bài 6 (B) | Đ Đ Đ Đ – – Đ K | không | **bỏ sót** | Cùng kiến thức với C2, chỉ đảo vị trí công thức. Thay bằng dạng có số, ví dụ $x^2-16$ |
| 2 | C4 (28) | MCQ | Bài 6 (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | |
| 2 | C5 (30) | MCQ | Bài 6 (H) | Đ Đ Đ Đ – – Đ Đ | **có** (grounding_unknown) | **báo nhầm** | Đáp án $9x^2-12x+4$ đúng, các phương án nhiễu tốt |
| 2 | C6 (31) | MCQ | Bài 6 (H) | Đ Đ Đ Đ – – Đ Đ | không | đúng | |
| 3 | C1 (BI1) | MCQ | (B) | như đề 1 | không | đúng | |
| 3 | C2 (33) | MCQ | (B) | Đ Đ Đ Đ – – Đ N | không | đúng | |
| 3 | C3 (34) | MCQ | (B) | Đ Đ Đ Đ – – Đ K | không | **bỏ sót** | Cùng câu hỏi "biểu thức nào là đa thức?" với C2 |
| 3 | C4 (36) | MCQ | (B) | Đ N Đ Đ – – Đ Đ | không | bỏ sót | "Hệ số cao nhất" của đa thức một biến là kiến thức Toán 7. Ô ma trận yêu cầu nhận biết và bậc, nên đổi sang hỏi bậc |
| 3 | C5 (35) | MCQ | Phép tính (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | |
| 3 | C6 (38) | MCQ | Hằng đẳng thức (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | |
| 3 | C7 (37) | MCQ | Phân tích nhân tử (B) | N Đ Đ K – – Đ N | không | **bỏ sót** | **Cả 4 phương án đều bằng** $2x^3-6x^2$ |
| 3 | C8 (39) | MCQ | Phân tích nhân tử (B) | N Đ Đ K – – Đ K | không | **bỏ sót** | Cùng lỗi và cùng khuôn với C7. Ba phương án A, C, D đều bằng đa thức đã cho |
| 3 | C9 (40) | MCQ | Tứ giác (B) | Đ Đ Đ Đ – – N Đ | không | bỏ sót | "160•" trong câu dẫn |
| 3 | C10 (41) | MCQ | Tứ giác (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | |
| 3 | C11 (44) | MCQ | Tứ giác (B) | Đ Đ Đ Đ – – N Đ | không | bỏ sót | "115•". Nhiễu 65° (góc bù) tốt |
| 3 | C12 (42) | MCQ | Tứ giác (B) | Đ Đ K Đ – – N N | không | **bỏ sót** | Bài tổng–hiệu 2 bước là mức Vận dụng, không phải Biết. Ký hiệu • ở cả câu dẫn, phương án và lời giải. Đây là câu tính góc thứ 4 trong đề |
| 3 | C13 (BI2) | Đ/S | | như đề 1 | | | |
| 3 | C14 (43) | Đ/S | Hằng đẳng thức | Đ Đ Đ – Đ – Đ Đ | không | đúng | $M=-8x^2+8x+5$, giá trị lớn nhất bằng 7. Nhãn T,T,F,F đúng |
| 3 | C15 (BI3) | TLN | | như đề 1 | | | |
| 3 | C16 (45) | TLN | Phép tính (H) | Đ Đ Đ – – Đ Đ Đ | không | đúng | $R=x-1$, đáp số 3 |
| 3 | C17 (46) | TLN | Phân tích nhân tử (VD) | Đ N* N – – Đ N Đ | không | bỏ sót | $(2x-1)^3=27$ cho $x=2$, đúng. "thỏa mãn biểu thức". *Không chắc câu tìm x có khớp ô "phân tích nhân tử dạng phức tạp" |
| 3 | C18 (47) | TLN | Tứ giác (H) | Đ Đ Đ – – Đ N Đ | không | bỏ sót | "85△", "360△", tổng cộng 14 chỗ |
| 4 | C1 (BI1) | MCQ | | như đề 1 | | | |
| 4 | C2 (48) | MCQ | (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | Bậc 5 |
| 4 | C3 (62) | MCQ | (B) | Đ Đ Đ Đ – – Đ N | không | bỏ sót | Na ná C1: cùng khái niệm, cùng phương án "−7" (độ tương đồng 0,773 so với ngưỡng 0,9) |
| 4 | C4 (49) | MCQ | (B) | Đ Đ Đ Đ – – Đ K | không | **bỏ sót** | Cùng hỏi bậc như C2, **cùng đáp số 5** |
| 4 | C5 (50) | MCQ | Phép tính (B) | Đ Đ Đ Đ – – Đ Đ | không | đúng | |
| 4 | C6 (60) | MCQ | Hằng đẳng thức (B) | Đ Đ Đ Đ – – Đ N | không | **bỏ sót** | Cùng biểu thức $(4x-1)^2$ với câu 14 ý a, và phương án C chính là ý a, nên **lộ đáp án** ý a. Đổi số |
| 4 | C7 (52) | MCQ | Phân tích nhân tử (B) | N Đ Đ K – – Đ Đ | không | bỏ sót | B và D bằng đa thức đã cho |
| 4 | C8 (53) | MCQ | Phân tích nhân tử (B) | N Đ Đ N – – Đ Đ | không | bỏ sót | B bằng đa thức đã cho |
| 4 | C9 (61) | MCQ | Tứ giác (B) | N Đ Đ N – – N Đ | không | bỏ sót | "Song song" đúng với hình chữ nhật (một hình thang cân đặc biệt). "Vuông góc" đúng khi góc đáy 45°. Cần thêm "luôn". Câu dẫn hỏi "độ dài" nhưng A, C nói về vị trí |
| 4 | C10 (57) | MCQ | Tứ giác (B) | Đ Đ Đ Đ – – N K | không | **bỏ sót** | Gần như y hệt C11: cùng 75°, cùng nhiễu 105°. Có ký hiệu • |
| 4 | C11 (55) | MCQ | Tứ giác (B) | Đ Đ Đ Đ – – N Đ | không | bỏ sót | Ký hiệu • |
| 4 | C12 (64) | MCQ | Tứ giác (B) | Đ Đ Đ N – – N Đ | không | bỏ sót | A "giá trị khác" là phương án độn |
| 4 | C13 (BI2) | Đ/S | | như đề 1 | | | |
| 4 | C14 (56) | Đ/S | Hằng đẳng thức | Đ Đ Đ – Đ – Đ Đ | không | đúng | $P=8x^2-14x+10$, giá trị nhỏ nhất $31/8$. Nhãn F,T,F,F đúng. Ý a trùng với C6 |
| 4 | C15 (BI3) | TLN | | như đề 1 | | | |
| 4 | C16 (59) | TLN | Phép tính (H) | Đ Đ Đ – – Đ Đ Đ | không | đúng | $C=5$, không phụ thuộc biến. Câu tốt |
| 4 | C17 (58) | TLN | Phân tích nhân tử (VD) | Đ N* N – – Đ N Đ | không | bỏ sót | $(3x+1)^3=64$ cho $x=1$, đúng. Cùng nhận xét với câu 46 |
| 4 | C18 (63) | TLN | Tứ giác (H) | Đ Đ Đ – – Đ Đ Đ | không | đúng | Đáp số 85 |

## B. Tổng hợp

**Theo câu (60 vị trí):**

| Mức dùng | Số câu | Tỉ lệ |
|---|---|---|
| Dùng ngay | 31 | 51,7% |
| Sửa nhẹ | 22 | 36,7% |
| Bỏ | 7 | 11,7% |

- Nếu không tính 9 vị trí là câu từ ngân hàng, còn 51 câu do hệ thống sinh: 22 dùng ngay (43%), 22 sửa nhẹ (43%), 7 bỏ (14%).
- Theo từng đề (dùng / sửa / bỏ): đề 1 là 9/8/1, đề 2 là 5/0/1, đề 3 là 9/6/3, đề 4 là 8/8/2.

**Lỗi theo loại** (đếm theo vị trí, một câu có thể dính nhiều loại):

| Loại lỗi | Số vị trí | Chi tiết |
|---|---|---|
| Toán | 10 | Sai đáp án hoặc 2 đáp án: 2 câu, đều do giáo viên sửa trong E2E. **Câu gốc LLM sai đáp án: 0.** Nhiều phương án tương đương hoặc thiếu chữ "luôn": 8 |
| Chương trình | 4 | 1 câu chắc chắn; 3 câu không chắc |
| Mức độ | 5 | |
| Nhiễu | 12 | |
| Đúng/sai | 1 | |
| Trả lời ngắn | 0 | |
| Trình bày | 16 | Có 6 câu dùng sai ký hiệu độ (•, △) |
| Trùng | 12 | |

**Hiệu quả QC:**
- Có 2 trên 51 câu sinh bị gắn cờ: 1 bắt đúng (câu 26), 1 báo nhầm (câu 30).
- Trong 29 vị trí phải sửa hoặc bỏ, **QC bỏ sót 28**. Phân loại theo lỗi QC lẽ ra phải bắt:

| Bước QC lẽ ra phải bắt | Số câu | Các câu |
|---|---|---|
| Solver | 9 | Không kiểm phương án tương đương hoặc tính duy nhất của đáp án: 7, 8, 37, 39, 52, 53, 15, 61, 25 |
| Dedup | 7 | 29, 34, 49, 57, 60, 21, 62 |
| Format | 5 | 40, 42, 44, 47, 55. Nguyên nhân: cảnh báo "json: đã sửa …" được cố ý **không bật cờ** (`QcSummaryService` dòng 96–98) |
| Khác | 7 | Mức độ, chương trình, ngôn ngữ, nhiễu yếu: 20, 11, 13, 36, 46, 58, 64 |

Ghi chú về dedup:
- Dedup có ghi dòng `passed=true`, nhưng phần lớn **không có điểm tương đồng**.
- Nguyên nhân: chốt chặn "số liệu/công thức phải giống nhau" loại hết các câu cùng khuôn nhưng khác số (`DedupChecker`). Dedup cũng chỉ so trong cùng dạng câu (MCQ không so với ý Đ/S).
- Bảng `question_embedding` có 0 dòng. Tôi không chắc đây là thiết kế hay lỗi.

## C. 5 câu tệ nhất

Chỉ xét câu gốc do LLM sinh, không tính 25 và 26 vì đó là bản giáo viên sửa trong E2E.

1. **Đề 3 C7 (câu 37):** "Phân tích đa thức $2x^3 - 6x^2$ thành nhân tử, ta thu được kết quả là: A. $2x(x^2 - 3x)$ B. $2x^2(x - 3)$ C. $x^2(2x - 6)$ D. $2(x^3 - 3x^2)$"
   - Cả 4 phương án đều đúng về mặt đẳng thức. Lời giải tự thừa nhận A, C, D chỉ "chưa triệt để", trong khi đề không hề yêu cầu triệt để. Học sinh chọn A, C hay D đều có lý.
2. **Đề 4 C10 (câu 57) so với C11 (câu 55):** "Cho hình thang cân $WXYZ$… góc $W$ bằng $75^\bullet$…" và "Cho hình thang cân $PQRS$… góc $P$ bằng $75^\bullet$…"
   - Hai câu trong cùng một đề chỉ khác tên đỉnh, cùng 75°, cùng nhiễu 105° và 90°. Ký hiệu độ hiển thị thành "•".
3. **Đề 3 C12 (câu 42):** "Cho tứ giác $RSTV$ có tổng số đo góc $R$ và góc $S$ là $200^\bullet$, đồng thời số đo góc $T$ lớn hơn góc $V$ là $40^\bullet$…"
   - Bài tổng–hiệu 2 bước nằm trong ô "Nhận biết". Có 15 ký hiệu "•". Đây là câu tính góc thứ 4 trong cùng một đề.
4. **Đề 4 C6 (câu 60) so với câu 14 ý a:**
   - MCQ: "Khai triển biểu thức $(4x - 1)^2$… C. $16x^2 - 4x + 1$"
   - Ý a: "Khai triển biểu thức $(4x - 1)^2$ ta được kết quả là $16x^2 - 4x + 1$."
   - Làm xong câu trắc nghiệm là biết ngay ý a sai.
5. **Đề 3 C18 (câu 47):** "Cho tứ giác $MNPQ$ có số đo góc $M$ bằng $85^\triangle$…"
   - Mọi ký hiệu độ hiển thị thành tam giác. Đề in ra như vậy không dùng được.

Ngoài 5 câu trên còn hai câu do giáo viên sửa trong E2E:
- Câu 26: đáp án 2,75 sai. Đây là câu QC bắt đúng duy nhất.
- Câu 25: có 2 đáp án, và lời giải lệch với phương án. QC không bắt được.

## D. Kết luận nếu là tổ trưởng

- **Không cho dùng nguyên trạng đề nào.**
- Đề 1 sau khi sửa khoảng 1 giờ thì dùng được: 8 câu sửa nhẹ, thay câu 17 bằng câu phân tích nhân tử thật.
- Đề 3 và đề 4 mất khoảng 1–1,5 giờ mỗi đề, chủ yếu vì phải thay câu trùng và sửa ký hiệu độ.
- Đề 2 dùng được sau khi thay C3, nhưng 4 trên 6 câu chỉ kiểm tra thuộc công thức.

Ước lượng cho một đề 18 câu:

| Việc | Thời gian |
|---|---|
| Tự giải và soát lại 18 câu | ~30 phút |
| Sửa 6–8 câu (3–5 phút mỗi câu) | ~30 phút |
| Thay 2–3 câu (5–10 phút mỗi câu) | ~20 phút |
| **Tổng** | **~1–1,5 giờ** |

Tự soạn một đề tương đương, có lời giải và khớp ma trận, theo kinh nghiệm mất khoảng 3–4 giờ (không chắc). Như vậy hệ thống tiết kiệm được khoảng 60%. Đổi lại, người duyệt phải tự giải lại mọi câu, vì cờ QC hiện gần như không báo được lỗi nào.

## E. Đề xuất cải thiện (xếp theo tác động lên độ chính xác)

1. **Solver kiểm tính duy nhất của đáp án.**
   - Lỗi liên quan: 9 câu (7, 8, 37, 39, 52, 53, 15, 61, 25).
   - Thêm bước so tương đương ký hiệu giữa từng phương án và đa thức gốc. Nếu từ 2 phương án trở lên bằng đa thức gốc thì gắn cờ.
   - Yêu cầu Solver trả lời "có phương án nào khác cũng đúng không?" thay vì chỉ chọn một đáp án.
   - Prompt cho ô phân tích nhân tử: bắt buộc ghi "đặt nhân tử chung lớn nhất" hoặc "triệt để", và các phương án nhiễu phải **khác** đa thức gốc.
   - Câu tính chất hình thang cân bắt buộc có chữ "luôn".
2. **Dedup trong phạm vi một đề.**
   - Lỗi liên quan: 7 câu (29, 34, 49, 57, 60, 21, 62).
   - Bỏ chốt chặn số liệu khi so các câu cùng đề, hoặc chuẩn hóa số thành ký hiệu giữ chỗ để bắt câu cùng khuôn.
   - So chéo MCQ với từng ý Đ/S.
   - Khi sinh câu cho một ô, đưa vào prompt danh sách các câu đã có trong đề và các dạng con cần phủ. Ví dụ ô Tứ giác 4 câu: tổng góc, góc hình thang cân, đường chéo hình thang cân, nhận biết.
3. **Format: không nuốt cờ khi sửa ký hiệu làm đổi nghĩa.**
   - Lỗi liên quan: 6 câu (40, 42, 44, 47, 55, 57) và câu 25.
   - Nếu `^\bullet` hoặc `^\triangle` đứng sau số thì thay bằng `^\circ` hoặc bật cờ.
   - Cảnh báo sửa `\text{}` trong phương án không được miễn cờ.
   - Thêm kiểm tra lời giải không được nhắc biểu thức không có trong phương án (câu 25).
4. **Chấm mức độ độc lập.**
   - Lỗi liên quan: câu 42, BI2 ý d, câu 26, 46, 58.
   - Hiện `llm_difficulty` chính là độ khó mục tiêu khi sinh (F-8), nên không có bước nào kiểm lại mức.
   - Quy tắc tối thiểu: ô Biết của MCQ không được đòi hỏi quá 1 bước tính.
5. **Ràng buộc chương trình theo lớp.**
   - Lỗi liên quan: câu 36. Câu 26, 46, 58 thì không chắc.
   - Lập danh sách khái niệm Toán 7 không dùng làm trọng tâm ô Toán 8, ví dụ hệ số cao nhất của đa thức một biến.
   - Ô "phân tích nhân tử dạng phức tạp" phải yêu cầu phân tích, không chuyển thành tìm x.
6. **Prompt về trình bày.**
   - Lỗi liên quan: câu 20, 11, 15, 13, 46, 58, 64.
   - Dùng "thỏa mãn đẳng thức", không dùng "thỏa mãn biểu thức".
   - Cấm phương án độn kiểu "giá trị khác".
   - Câu dẫn không lặp "sau đây".
7. **Grounding giảm báo nhầm.**
   - Lỗi liên quan: câu 30.
   - Áp dụng một công thức có trong tài liệu vào số cụ thể nên được tính là có căn cứ (supported), không phải chưa rõ (unknown).

