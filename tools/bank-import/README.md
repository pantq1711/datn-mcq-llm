# bank-import: bộ câu hỏi Word → JSON (W2-1)

Công cụ **offline**, không phải tính năng sản phẩm: không đụng DB, API hay `back_end`. Đọc các file `.docx` bộ câu hỏi
(công thức MathType OLE `Equation.DSMT4`) và ghi JSON chuẩn hóa làm dữ liệu cho thí nghiệm (Solver bắt lỗi, loại trùng, so sánh câu AI
với câu giáo viên). Sau này là lõi cho tính năng "Nhập ngân hàng từ Word".

**Dữ liệu thật là nguồn thương mại, chỉ dùng nội bộ.** Đầu vào và đầu ra nằm trong `data/private/` (bị `.gitignore`); không commit,
không chép nội dung câu vào repo. Fixture của test hoàn toàn tự tạo bằng code (`DocxBuilder`, `MtefBuilder`, `FixtureDoc`).

## Chạy

```bash
cd tools/bank-import
mvn test                    # 70 test, không cần DB hay mạng
mvn -q package -DskipTests  # tạo target/classes và target/lib
cd ../..
java -Djava.awt.headless=true -cp "tools/bank-import/target/classes;tools/bank-import/target/lib/*" \
     com.datn.mcq.bankimport.Main                       # Linux/macOS: dùng dấu : thay cho ;
```

Mặc định: `--in data/private/bo-cau-hoi --out data/private/bank-json --expect 20,10,10`.

| Tùy chọn | Ý nghĩa |
|---|---|
| `--in <thư mục>` / `--out <thư mục>` | nơi đọc `.docx` / nơi ghi |
| `--expect mcq,tf,sa` hoặc `none` | số câu mỗi bộ để báo lệch (mặc định 20,10,10) |
| `--convert-images` | **chỉ Windows**: đổi ảnh WMF/EMF sang PNG bằng System.Drawing (cho `file_png` và `formulas.html`) |
| `--no-validate` | bỏ kiểm LaTeX bằng JLaTeXMath (nhanh hơn) |

Console chỉ in số liệu và id; không in nội dung câu hay tên file gốc.

## Đầu ra (trong `--out`)

| Tệp | Nội dung |
|---|---|
| `<doc_id>.json` | một bộ câu hỏi (ví dụ `k8c3b1.json` = khối 8, chương 3, bài 1); schema bên dưới |
| `images/<doc_id>/` | ảnh nguyên bản (png, emf, wmf) của từng câu |
| `catalog.csv` | danh mục mọi câu để lọc tập thí nghiệm (cột `usable_text_only`, `formula_status`, `has_image`, `structure_ok`...) |
| `review.csv` | danh sách câu cần người kiểm: 100% Đ/S và trả lời ngắn, mọi câu có cờ, mẫu 20% trắc nghiệm (hạt giống cố định) |
| `formulas.html` | bảng đối chiếu ~60 công thức: ảnh MathType, ảnh vẽ từ LaTeX, mã LaTeX |
| `report.json` | số liệu chất lượng: câu theo dạng/mức, ảnh, công thức ok/partial/unsupported, lệch số lượng, bất thường cấu trúc |

### Schema JSON (tóm tắt)

```json
{ "schema_version": "1.0",
  "source": { "doc_id": "k8c3b1", "grade": 8, "chapter": 3, "lesson": 1, "lesson_title": "…", "file_sha256": "…" },
  "questions": [ {
    "id": "k8c3b1-mcq-001", "type": "mcq|tf|sa", "part": "I|II|III", "ordinal": 1, "locator": "Phần I, câu thứ 1",
    "level_raw": "NB|TH|VD|VDC|null", "level": "easy|medium|hard|null",
    "stem": "…", "tables": [[["ô","ô"]]],
    "options": [{"key":"A","text":"…"}],                       // mcq
    "statements": [{"key":"a","text":"…","truth":true,"evidence":"highlight_yellow|dap_an_line"}],   // tf
    "answer": {"value":"B","evidence":"highlight_yellow"}      // mcq; sa: {"text":"7,5","numeric":"7.5","evidence":"dap_so_line"}
    "solution": "…|null",
    "formulas": [{"id":"f1","status":"ok|partial|unsupported","latex":"…|null","reason":"…","source_ref":"word/embeddings/…"}],
    "images": [{"id":"img1","kind":"figure|formula_image|ole_drawing","role":"stem|option_A|…","file":"images/…","file_png":"…"}],
    "has_image": false, "flags": ["…"], "content_hash": "…" } ] }
```

- Trong văn bản: công thức chuyển được là `$latex$` ngay trong chuỗi; không chuyển được là token `⟦fN⟧`; ảnh là `⟦imgN⟧`.
- `level`: NB → easy, TH → medium, VD/VDC → hard; câu không có nhãn thì `null` (không suy ra).
- `usable_text_only` (trong `catalog.csv`): không có ảnh nào (kể cả ảnh công thức và đối tượng vẽ) **và** mọi công thức `ok`.
  Cột `structure_ok` riêng cho biết cấu trúc/đáp án của câu có tin cậy không; để lọc tập cho Solver nên dùng cả hai.
- Cờ cấu trúc (đáng xem): `answer_missing`, `answer_ambiguous`, `answer_conflict`, `structure_*`. Cờ thông tin: `level_missing`,
  `formula_unsupported`, `formula_partial`, `formula_image`, `ole_drawing`, `plain_math_vertalign`, `hint_after_statements`, `empty_formula`.

## Quy ước trong file Word mà công cụ nhận ra

Ba phần "PHẦN I/II/III"; đầu câu là đoạn có nhãn mức (`[NB]`, `[Nhận biết]`, `[[NHẬN BIẾT]`...) hoặc "Câu N."; đáp án trắc nghiệm là
phương án có chữ tô vàng (xét theo vị trí tô, không phụ thuộc phạm vi tô); phần II ba biến thể (bảng có ô đề gộp, đề dẫn nằm
trong đoạn đứng trước bảng, bảng gộp nhiều câu), đáp án từ tô vàng hoặc dòng "Đáp án: Đ S S Đ"; phần III lấy từ dòng "Đáp số:".
Bảng tiêu đề đầu file, header/footer, dòng quảng cáo cuối file bị bỏ. Văn bản chuẩn hóa NFC.

## Công thức MathType → LaTeX

`mtef/MtefReader` đọc MTEF v5 (khuôn theo `spikes/mathtype-ole/tools/mtef_dump.py` nhưng sửa lỗi RULER trong LINE/PILE: ruler
trong LINE **không** có byte thẻ 7 đứng trước; bộ cũ báo "expected RULER got 1" ở 14/891 đối tượng MathType 7.4). `mtef/MtefToLatex`
dịch cây sang LaTeX theo bảng; gặp thứ chưa có trong bảng thì `unsupported` kèm lý do và `latex: null`, **không đoán**.

Template hỗ trợ: phân số, căn (kể cả bậc n), chỉ số trên/dưới, ngoặc (tròn, vuông, nhọn, trị tuyệt đối...), dấu mũ/ngang/véc-tơ,
`PILE` (nhiều dòng), hệ phương trình (ngoặc nhọn + `MATRIX` một cột). Chưa hỗ trợ: tổng, tích phân, giới hạn, ma trận nhiều cột...

License phụ thuộc: Apache POI (Apache-2.0, chỉ POIFS), Jackson (Apache-2.0), JLaTeXMath (GPL-2.0 kèm Classpath Exception, chỉ dùng để
kiểm LaTeX và vẽ `formulas.html`), JUnit (EPL-2.0, chỉ test).

## Chống rò rỉ

Sau khi ghi, công cụ quét mọi `.json/.csv/.html` trong đầu ra để tìm tên giáo viên (cắt từ tên file) và dấu hiệu quảng cáo
(`http`, `www.`, `.edu.vn`, "Tài liệu được chia sẻ"). Thấy thì **xóa toàn bộ đầu ra**, thoát mã 2, thông báo không chứa chuỗi bị lộ.
Test `OutputTest` khóa hành vi này.

## Cách kiểm tay (đề xuất)

1. `report.json` / console: 40 câu mỗi bộ (20 + 10 + 10) là kỳ vọng; lệch số lượng phải có lý do trong file Word.
2. `review.csv`: mở file Word, đếm đến "Phần X, câu thứ k", đối chiếu cột `answer`. Nên xem 100% các dòng `tf_100`, `sa_100`, `flagged`.
3. `formulas.html`: so ảnh MathType (cột 2) với ảnh vẽ từ LaTeX (cột 3) cho mẫu phân tầng theo loại cấu trúc.
