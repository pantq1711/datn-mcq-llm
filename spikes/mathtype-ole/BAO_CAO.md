# Spike: xuất công thức dạng đối tượng MathType (OLE) trong .docx

Ngày 24/10/2026. Chỉ làm trong `spikes/mathtype-ole/`; không sửa code production, không commit, không gọi Gemini,
không đụng DB. Mỗi kết luận ghi rõ **[đã chạy]** hoặc **[suy luận]**.

## 0. Kết luận

1. **omml-converter: không dùng được.** Code thuần Python, không cần Word/MathType, nhưng Word không bao giờ
   hiển thị đối tượng MathType nó tạo (vẫn hiện Equation của Word). Ngoài ra MTEF sai cấu trúc ở 8/10 biểu thức mẫu.
   **[đã chạy]**
2. **Không có thư viện mở nào sinh MTEF đúng** từ LaTeX, MathML hay OMML. Các thư viện mở tìm được chỉ đọc MTEF
   (chiều ngược lại). Bộ chuyển chính thức là MathType SDK, thương mại và chạy trên Windows. **[suy luận từ tìm kiếm]**
3. **Cách khả thi nhất: tự viết OMML → MTEF bằng Java thuần cho tập con THCS**, chạy ngay sau pandoc (giữ nguyên
   `PandocRunner`). Bản thử Java khoảng 1.200 dòng, dùng Apache POI (POIFS) và JLaTeXMath. Kết quả **[đã chạy]**:
   - Cấu trúc MTEF khớp đúng với MathType 7.12 tự sinh cho cùng biểu thức: 10/10 biểu thức mẫu, 49/49 bộ THCS,
     63/65 bộ ký hiệu. 2 ca lệch là do MathType tự nhập TeX sai, phía spike đúng; xem mục 3.
   - Word nhận đủ đối tượng `Equation.DSMT4`; MathType trên máy dev đọc lại được toàn bộ đối tượng spike
     (10/10 và 116/116 ở hai file kiểm tra).
4. **Còn chờ bạn kiểm tra:** bấm đúp mở, sửa và lưu bằng MathType đầy đủ và MathType Lite; in ra giấy.
   Trên máy dev MathType chưa bị khóa (Toggle TeX vẫn chạy), nên chưa thử được chế độ Lite.
   Câu "Lite [SỬA ĐƯỢC / KHÔNG SỬA ĐƯỢC] công thức MathType có sẵn" trong yêu cầu **chưa được điền**. Đây là điểm
   quyết định giá trị của hướng này: nếu Lite không sửa được, file vẫn hiển thị và in được nhưng giáo viên dùng Lite
   không sửa được công thức.

## 1. Cấu trúc đối tượng MathType trong .docx [đã chạy]

Nguồn: file do MathType 7.12 tự sinh trên máy dev (lệnh Toggle TeX qua Word), xem `out/ref_unz/` và
`out/ref_mathtype_textoggle.mtef.txt`. Đối chiếu đặc tả MTEF v5 (bản lưu `_downloads/spec/MTEF5.html`).

**`word/document.xml`** (mỗi công thức là một run):

```xml
<w:r>
  <w:rPr><w:position w:val="-24"/></w:rPr>          <!-- hạ xuống cho khớp đường cơ sở, đơn vị nửa point -->
  <w:object w:dxaOrig="240" w:dyaOrig="620">        <!-- kích thước gốc, twip -->
    <v:shapetype id="_x0000_t75" .../>              <!-- chỉ ở đối tượng đầu tiên -->
    <v:shape id="_x0000_i1027" type="#_x0000_t75" style="width:12pt;height:31.2pt" o:ole="">
      <v:imagedata r:id="rId4" o:title=""/>         <!-- ảnh xem trước -->
    </v:shape>
    <o:OLEObject Type="Embed" ProgID="Equation.DSMT4" ShapeID="_x0000_i1027"
                 DrawAspect="Content" ObjectID="_1854363691" r:id="rId5"/>
  </w:object>
</w:r>
```

- **`document.xml.rels`**: một quan hệ `.../relationships/oleObject` trỏ `embeddings/oleObjectN.bin` và một quan hệ
  `.../relationships/image` trỏ `media/imageN.wmf`.
- **`[Content_Types].xml`**: `Default bin = application/vnd.openxmlformats-officedocument.oleObject`,
  `wmf = image/x-wmf` (spike dùng `png = image/png`).
- **`oleObjectN.bin`**: file compound (CFB v3), CLSID gốc `{0002CE03-0000-0000-C000-000000000046}`, gồm 4 stream:
  - `\1CompObj`: tên kiểu "MathType 7.0 Equation", định dạng clipboard "MathType EF", ProgID "Equation.DSMT4".
  - `\1Ole` (20 byte) và `\3ObjInfo` (6 byte).
  - `Equation Native`: header EQNOLEFILEHDR 28 byte (cbHdr=28, version=0x00020000, cf, cbObject=độ dài MTEF), sau đó
    là MTEF.
- **MTEF v5**: header (5, platform 1 = Windows, product 0, phiên bản, khóa "DSMT7", tùy chọn 1 = inline), các
  record định nghĩa (ENCODING_DEF, FONT_DEF Times New Roman/Symbol/Courier New/MT Extra, EQN_PREFS với cỡ chữ Full 12pt)
  rồi đến cây công thức. Những điều chỉ biết được khi so với file thật (đặc tả không nói rõ hoặc khác):
  - Chỉ số trên (`tmSUP`): ô dưới rỗng đặt trước, ô trên đặt sau. Cơ số đứng trước template.
  - `\sqrt`: ô biểu thức dưới căn, sau đó là ô chỉ số rỗng ở cỡ SUB.
  - `cases`: `tmBRACE` chỉ có rào trái, chứa một LINE bọc MATRIX n×1 (không dùng PILE).
  - `\binom`: PILE canh giữa là ô chính của `tmPAREN`.
  - `^\circ` được ghi thành dấu độ `°` (U+00B0).
  - `\hat{A}`, `\vec{a}`, `\bar{x}`, `f'`: gắn embellishment lên ký tự, không dùng template.
  - Dấu câu ASCII `( ) [ ] { } | , ; : / %` dùng style Function. Toán tử dùng style Symbol kèm vị trí trong font
    Symbol. △ ∥ ⋮ ∓ ⋯ ℕ ℤ ℚ ℝ ⌢ lấy từ font MT Extra.
  - Rào `|` `‖` `⌊⌋` `⌈⌉` dùng mã riêng của MathType (U+EC07/EC08, EC09/EC0A, F8F0/F8FB, F8EE/F8F9).
  - Cờ tích phân/tổng: 0x10 = có cận dưới, 0x20 = có cận trên. Đặc tả ghi 0x01/0x02, nhưng file thật dùng 0x10/0x20.
  - MathType 7 còn lưu TeX gốc trong một record type 102 ("TeX Input Language"); record này không có trong đặc tả,
    spike không ghi.
- **Ảnh xem trước**: MathType ghi WMF. Word hiển thị và in ảnh này cho tới khi đối tượng được mở bằng MathType.

## 2. Đánh giá thư viện

| Thư viện | License | Duy trì | Chiều | Kết quả |
|---|---|---|---|---|
| **omml-converter** 0.1.0 (PyPI) | MIT | 1 commit duy nhất ngày 01/06/2026, 1 sao | OMML → MTEF/OLE | Không dùng được, xem dưới **[đã chạy]** |
| MathType SDK (Wiris) | Thương mại | Có | TeX/MathML ↔ MTEF | Chỉ chạy trên Windows, cần MathType, không hợp máy chủ Linux **[suy luận từ tài liệu]** |
| `jure/mathtype`, `mathtype_to_mathml` (Ruby) | MIT | Cũ (2015) | MTEF → XML/MathML (chiều ngược) | Chỉ dùng mẫu thử (fixtures) và bộ đọc làm tham chiếu **[đã chạy: đọc 29 + 32 file mẫu]** |
| **Apache POI 5.4.1** (POIFS) | Apache-2.0 | Có | Ghi compound file | Ghi được `oleObject.bin` mà Word và MathType nhận **[đã chạy]** |
| **JLaTeXMath 1.0.7** | GPL-2.0+ kèm ngoại lệ liên kết (classpath exception, đã đọc trong jar) | Bản cuối 2018 | LaTeX → ảnh | Dựng ảnh PNG đúng cho 116/116 công thức **[đã chạy]** |
| olefile 0.47 (Python) | BSD | Có | Đọc CFB | Chỉ dùng cho công cụ kiểm tra **[đã chạy]** |

**omml-converter, trả lời 3 câu hỏi:**

- **(a)** Sinh MTEF/OLE bằng code Python thuần (lxml, olefile; tự ghi CFB), không gọi COM, Word hay MathType.
  **[đã chạy: đọc toàn bộ mã nguồn]** Chạy được trên Linux về mặt kỹ thuật, nhưng kết quả không dùng được vì:
  - Đối tượng OLE bị đặt trong `mc:AlternateContent` với `Requires` là một namespace Word không nhận, nên Word luôn
    hiển thị phần Fallback (OMML). Mở bằng Word: **10 Equation, 0 đối tượng OLE**. **[đã chạy]**
  - CLSID là `{0002CE02-…}` (Microsoft Equation 3.0) thay vì MathType; thiếu 3 stream; ProgID `Equation.DSMT6`
    trong khi MathType đăng ký `Equation.DSMT4`; không có ảnh xem trước; kích thước cố định 1"×0,5". **[đã chạy: đọc file]**
- **(b)** License MIT. Bản 0.1.0 duy nhất, repo chỉ có 1 commit, cần Python ≥ 3.12. Đầu vào là OMML trong .docx
  (bên trong đổi OMML → MathML → MTEF).
- **(c)** Chạy với 10 biểu thức mẫu (giải mã MTEF bằng `tools/mtef_dump.py`, so với MathType) **[đã chạy]**:
  - Cấu trúc đúng: `\frac{3}{4}`, `x_1`.
  - Hiển thị được nhưng sai style: `\Delta ABC`, `a\ne b` (Δ và ≠ ở style Text).
  - Sai: `2^{10}`, `\pi r^2`, `x^2` trong căn, `60^\circ` (chỉ số trên bị ghi vào ô chỉ số dưới); `\widehat{ABC}`
    (thành chỉ số trên); `\left(...\right)` (ngoặc thường, không giãn). `cases` mất hết nội dung, chỉ còn `{;`.
  - So với MathType bằng `tools/compare_mtef.py`: 0/10 khớp. Lệnh CLI còn báo lỗi `UnicodeEncodeError` trên console
    Windows khi in tóm tắt (file đã được ghi trước đó).

## 3. Bản thử: OMML → MTEF → OLE (Java thuần)

Luồng xử lý: Markdown → pandoc (đúng lệnh và `reference.docx` của `PandocRunner`) → docx có OMML → `java-proto`
đổi mỗi `m:oMath` thành `w:object`, gồm:

- MTEF từ OMML (`OmmlToMtef`, `MtefWriter`).
- File OLE ghi bằng POIFS (`MathTypeOle`).
- Ảnh PNG 600 dpi từ LaTeX gốc bằng JLaTeXMath (`PreviewRenderer`).
- Run `w:object` ghép bằng `DocxMathTypeConverter`.

LaTeX của từng công thức lấy theo thứ tự từ `pandoc -t json` (`tools/pandoc_math_list.py`), tức cùng bộ phân tích với
lệnh xuất docx. Công thức có cấu trúc hoặc ký hiệu chưa hỗ trợ được **giữ nguyên dạng Equation của Word**; không sinh
MTEF đoán mò.

Ảnh xem trước: dùng PNG, chưa cần WMF/EMF. Word hiển thị và xuất PDF đúng **[đã chạy]**; in ra giấy **chưa kiểm**.

| Kiểm chứng (đều chạy sau lần sửa code cuối) | Kết quả |
|---|---|
| Cấu trúc MTEF so với MathType 7.12 tự sinh (bỏ qua record COLOR) | 10/10 mẫu; 49/49 bộ THCS (1 câu MathType không nhập được); 63/65 bộ ký hiệu (2 câu lệch do MathType nhập TeX sai: "ậ" thành `?`, `\lfloor` thành U+FFFD; 3 câu MathType không nhập được) **[đã chạy]** |
| Word mở file (COM): số đối tượng `Equation.DSMT4` | `KIEM_TRA_10.docx`: 10 OLE, 0 Equation. `KIEM_TRA_mo_rong.docx`: 116 OLE, 2 Equation (cố ý giữ) **[đã chạy]** |
| MathType đọc ngược đối tượng spike (Toggle TeX, đối tượng không chứa TeX gốc) | 10/10 và 116/116 đổi được sang TeX **[đã chạy]**. Đính chính 24/10: lần chạy này để mất `\,` ở 3 câu (B26 ra `ab`, B58 và C5 mất khoảng trước `dx`); sau bản sửa mục 9 thì 116/116 đúng |
| Xuất PDF (thay cho in) và xem ảnh | Đúng dòng chữ; công thức hiển thị riêng dòng được căn giữa **[đã chạy]** |
| Thời gian | 1,2–3,5 giây mỗi file, gồm cả khởi động JVM, cho 10–118 công thức **[đã chạy]** |
| Bấm đúp sửa, lưu, mở lại; MathType Lite; MathType 6.x; in giấy; WPS/LibreOffice | **Chưa kiểm**, chờ bạn |

## 4. Tích hợp vào backend Java

**Khuyến nghị: viết thuần Java** trong `exam/export`, không cần tiến trình con mới. Lý do: bản thử đã là Java; chỉ
thêm 2 thư viện (POI, JLaTeXMath) và vẫn dùng pandoc sẵn có. **[suy luận dựa trên bản thử]**

Việc cần làm:

1. Chuyển 5 lớp của `java-proto` vào `com.datn.mcq.exam.export`; thêm dependency `poi` và `jlatexmath`.
2. `PandocRunner` thêm một lần gọi `pandoc -t json` để lấy danh sách LaTeX, sau đó hậu xử lý bytes docx.
3. Thêm lựa chọn định dạng công thức khi xuất (Equation hay MathType). Việc này **đổi hợp đồng API**, cần bạn duyệt
   trước (M-35).
4. Test: dùng các file `.bin` do MathType sinh trong `out/` làm mẫu chuẩn (golden), so cấu trúc MTEF như
   `compare_mtef.py`; thêm test cho đường dự phòng giữ OMML.
5. Máy chủ Linux: chạy AWT ở chế độ headless và cài font có tiếng Việt cho JLaTeXMath (chữ trong `\text{}`).

Ước lượng **[suy luận, chưa làm]**:

- Chuyển code và viết test: khoảng 2–3 ngày công.
- Phần lớn công sức còn lại nằm ở việc sửa theo kết quả kiểm tra trên máy giáo viên. Phần này không ước lượng được
  trước khi có kết quả của bạn.

## 5. Rủi ro

1. **MathType Lite**: chưa biết Lite có sửa được đối tượng có sẵn không (câu hỏi bỏ ngỏ ở mục 0).
2. **Ảnh xem trước khác MathType**: ảnh dùng font Computer Modern và hơi to hơn (vd. `\frac{3}{4}` cao 30,5pt so với
   31,2pt của MathType nhưng rộng hơn). Sau khi bấm đúp và đóng, MathType vẽ lại bằng Times New Roman 12pt, nên công
   thức sẽ "đổi dáng". Phần chữ quanh công thức là 13pt.
3. **Cỡ chữ và tùy chọn MathType**: khối định nghĩa (font, cỡ 12pt, khoảng cách) được chép từ một đối tượng do
   MathType sinh trên máy dev. Có thể tự sinh khối này theo đặc tả nếu cần đổi cỡ.
4. **Phiên bản**: header ghi MathType 6.9 / "DSMT6". Mới thử trên MathType 7.12; MathType 6.x chưa thử.
5. **Chữ tiếng Việt trong `\text{}`**: chính MathType nhập TeX cũng làm mất dấu, và chưa biết MathType hiển thị
   U+1EAD… ở style Text ra sao. Đã đưa vào file kiểm tra (C1).
6. **Ký hiệu ngoài bảng đã kiểm chứng**: công thức giữ dạng Equation. An toàn, nhưng file có thể lẫn hai loại
   công thức.
7. **Trình soạn thảo khác** (WPS, LibreOffice, Google Docs, Word trên điện thoại): có lẽ chỉ hiện ảnh, không sửa
   được **[suy luận]**.
8. **Protected View / chính sách chặn OLE**: phải bấm "Enable Editing" thì mới sửa được.
9. **Dung lượng**: khoảng 5 KB mỗi công thức (PNG). 116 công thức cho file 635 KB.

## 6. Cú pháp chưa hỗ trợ (công thức giữ dạng Equation của Word)

- Ký hiệu chưa có mã hóa đã kiểm chứng: `\lfloor \rfloor`, `\lceil \rceil` dạng ký tự rời; `\circ` đứng riêng (ngoài
  `^\circ`); `\ll`, `\gg`, `\mapsto`, `\nparallel`… (mọi ký tự không có trong bảng của `OmmlToMtef`).
- `m:nary` ngoài ∫ ∑ ∏ (∬, ∮, ⋃, ⋂ lớn).
- `\overbrace`/`\underbrace` (`m:groupChr`), chỉ số đứng trước (`m:sPre`), `\phantom`, `\boxed`, `aligned`/`eqnarray`
  (`m:eqArr`).
- Rào không cùng loại (`[a;b)`, tmINTERVAL); dấu mũ khác (`\breve`, `\check`; `\dot`/`\ddot` trên nhiều ký tự).
- `\mathbb` ngoài N Z Q R; `\mathcal`, `\mathfrak` (`m:scr` khác); ký tự ngoài BMP.

Đã hỗ trợ nhưng **chưa đối chiếu với MathType**:

- Ma trận canh giữa/phải (`pmatrix`, `bmatrix`).
- `\mathbf` (style Vector).
- Phân số gạch chéo (`m:type="lin"`).
- `\left\langle`.

Đã hỗ trợ và **đã đối chiếu**: toàn bộ biểu thức trong `samples/thcs50.tex.txt` và `samples/chars68.tex.txt` trừ
`\lfloor`, gồm:

- Phân số, căn bậc n, chỉ số trên/dưới/cả hai, `\lim`, ∫ ∑ có cận.
- Mọi loại rào `\left \right`, `cases`, `array`, `\binom`.
- `\widehat`, `\overline`, `\overrightarrow`, cung `\overset\frown`, dấu mũ/phẩy trên một ký tự.
- Chữ Hy Lạp, ℕℤℚℝ, `\text`, khoảng cách `\, \; \quad \ ` (riêng `\,` cố ý khác MathType, xem mục 9).

## 7. Hướng dẫn kiểm tra (2 máy: MathType đầy đủ và MathType Lite)

File cần kiểm tra:

- `spikes/mathtype-ole/KIEM_TRA_10.docx`: 10 biểu thức mẫu, mỗi biểu thức nằm giữa câu chữ. Đây là file chính.
- `spikes/mathtype-ole/KIEM_TRA_mo_rong.docx` (không bắt buộc): 118 công thức. Phần C có chữ tiếng Việt, công thức
  riêng dòng, 4 phương án trên một dòng, và 1 công thức cố ý giữ Equation (C4).

Trên mỗi máy:

1. Ghi lại phiên bản Word và MathType (MathType → Help → About), cùng trạng thái bản quyền (đầy đủ, dùng thử hay Lite).
2. **Mở file**. Nếu Word báo Protected View, bấm Enable Editing. Kiểm tra:
   - Đủ 10 công thức, nằm đúng dòng chữ, không ô trống hay dấu X đỏ.
   - Bấm chuột phải vào một công thức: menu phải có mục "Equation Object" hoặc "MathType Object".
   - Lúc mới mở, công thức là **ảnh xem trước** kiểu chữ LaTeX (Computer Modern). Đây là điều bình thường.
3. **In thử** ra giấy (và Microsoft Print to PDF nếu tiện): công thức rõ nét, không bị cắt, đúng vị trí.
4. **Bấm đúp** công thức số 5 (`ABC = 60°`) và số 9 (hệ phương trình):
   - MathType có mở không? Cấu trúc có đúng không (dấu mũ trên ABC, dấu độ, ngoặc nhọn với 2 dòng canh trái)?
   - Sửa thử (vd. đổi 60 thành 45), rồi đóng cửa sổ MathType (File → Close and Return to Word, hoặc nút X, chọn Yes
     nếu được hỏi cập nhật).
   - Công thức trong Word phải đổi sang chữ Times New Roman và hiện số mới.
5. **Lưu** (Ctrl+S, giữ định dạng .docx), đóng Word, mở lại: công thức đã sửa vẫn còn, bấm đúp vẫn mở được.
6. Máy **Lite**: ghi chính xác điều xảy ra ở bước 4. Mở được và sửa được? Mở nhưng không cho sửa? Báo hết hạn hay
   không phản hồi? Chụp màn hình thông báo nếu có.
7. (Không bắt buộc) Với file mở rộng:
   - Bấm đúp C1 xem chữ "Vậy", "Số học sinh" hiển thị trong MathType có đúng dấu không.
   - C4 phải là Equation của Word.
   - Thử Convert Equations / Toggle TeX trên máy đầy đủ.

Mẫu ghi kết quả:

| Bước | MathType đầy đủ | MathType Lite |
|---|---|---|
| Phiên bản Word / MathType / bản quyền | | |
| 2. Hiển thị đủ 10, đúng dòng | | |
| 3. In giấy rõ nét | | |
| 4. Bấm đúp mở MathType, cấu trúc đúng | | |
| 4. Sửa rồi cập nhật về Word | | |
| 5. Lưu, mở lại, còn sửa được | | |
| Ghi chú / ảnh thông báo | | |

## 8. Thư mục và cách chạy lại

| Đường dẫn | Nội dung |
|---|---|
| `KIEM_TRA_10.docx`, `KIEM_TRA_mo_rong.docx` | File để bạn kiểm tra |
| `java-proto/` | Bản thử Java (Maven): `mvn -o package` rồi `java -cp "target/mathtype-ole-proto-0.0.1.jar;target/lib/*" spike.mathtype.Main <pandoc.docx> <latex.txt> <out.docx>` |
| `tools/mtef_dump.py`, `compare_mtef.py`, `inspect_ole.py` | Giải mã MTEF, so với MathType, xem stream OLE (chạy bằng `.venv313`, Python 3.13) |
| `tools/mathtype_textoggle_ref.ps1` | Nhờ MathType trên máy dev sinh đối tượng tham chiếu từ danh sách TeX (cần Word + MathType) |
| `tools/mathtype_roundtrip.ps1`, `word_probe.ps1` | MathType đọc ngược đối tượng (`-Translator Texvc.tdl` để thử bộ dịch khác); Word đếm đối tượng và xuất PDF |
| `tools/office_guard.ps1`, `office_guard_watchdog.ps1` | Dùng chung cho các script Word/MathType: luôn đóng Word/MathType do script mở, khôi phục Cut and Copy Preferences (mục 9) |
| `tools/extract_mtef.py`, `mathtype_measure.ps1` | Tách MTEF thô từ docx; nhờ MathType (`MT6.dll`) dựng và trả kích thước để so độ rộng (mục 9) |
| `java-proto/src/test` | `SpaceCodeTest`: ký tự khoảng cách sinh ra phải nằm trong danh sách đã đối chiếu (`mvn -o test`) |
| `samples/` | Biểu thức thử (`exprs`, `thcs50`, `chars68`), file Markdown kiểm tra, bản sao `reference.docx` |
| `out/` | File trung gian: `ref_*` là file MathType sinh, `01_omml_converter.*` là kết quả omml-converter, `*.compare.txt` / `*.roundtrip.txt` là kết quả kiểm chứng |

## 9. Lỗi "No translation available for Inner space (0xEF07)" (24/10)

**Triệu chứng:** MathType báo "MathType Translation Error: No translation available for Inner space (0xEF07) ...
texvc.tdl" khi dịch 3 đối tượng của `KIEM_TRA_mo_rong.docx` (oleObjectMT66, MT98, MT118). `KIEM_TRA_10.docx` không có
mã này.

**Kiểm tra [đã chạy: giải mã MTEF bằng `tools/mtef_dump.py`, đọc thư mục Translators của MathType 7.12.3]:**

- Cả 3 đối tượng là `\,`: B26 `a\,b`, B58 `\int_0^1 x\,dx`, C5 `\int_0^1 x^2\,dx`. Pandoc ghi `\,` thành U+2009, bộ sinh
  đổi thành U+EF07.
- **MathType 7.12 tự nhập TeX cũng ghi `\,` thành U+EF07** (`out/ref_chars68_mathtype.docx`, đối tượng 26 `a\,b` và 55
  `\int_0^1 x\,dx`). Bộ sinh khớp MathType; lỗi nằm ở bộ dịch của chính MathType.
- Bộ dịch Texvc (`Texvc (chars).tdl`) có dòng U+EF07 nhưng bị chú thích, nên không có bản dịch. Bộ LaTeX/TeX
  (`TeX (base rules).tdl`, được LaTeX, AMS LaTeX, MathJax-LaTeX, Plain TeX dùng lại) dịch U+EF07 thành `{\mkern 1mu}`,
  chỉ bằng 1/3 độ rộng của `\,`.

| Mã MathType | Texvc | LaTeX/TeX | MathType nhập TeX |
|---|---|---|---|
| U+EF02 thin space | `\,` | `\,` | (không gặp) |
| U+EF04 thick space | `\ ` | `\;` | `\;`, `\ ` |
| U+EF05 em space | `\quad` | `\quad` | `\quad` |
| U+EF07 inner space | **không có** | `{\mkern 1mu}` | `\,` |

**Sửa [đã chạy]:** `\,` (U+2009, U+2006, U+202F, U+200A) nay ghi thành **U+EF02**: mọi bộ dịch TeX của MathType đều dịch
lại thành `\,`, đúng độ rộng. Đây là chỗ duy nhất bộ sinh cố ý khác MathType tự nhập TeX, nên `compare_mtef.py` với
`ref_chars68` sẽ lệch ở 2 câu `a\,b` và `\int_0^1 x\,dx`. Danh sách mã khoảng cách được phép ghi là
`OmmlToMtef.VERIFIED_SPACE_CODES` (U+EF02, U+EF04, U+EF05); `SpaceCodeTest` kiểm mọi ký tự đầu vào và mọi ký tự khoảng
trắng Unicode chỉ sinh ra các mã trong danh sách này.

**Script tự động [đã chạy]:** cả 3 script Word/MathType dùng `tools/office_guard.ps1`:

- Đóng đúng tiến trình Word do script mở (tìm qua cửa sổ `OpusApp` có caption riêng) và MathType/MathTypeLib khởi
  động trong lúc chạy; không đụng Word/MathType đã chạy từ trước.
- Một tiến trình canh chạy song song: quá `-TimeoutSec` (mặc định 600) thì đóng Word để lời gọi COM bị treo (hộp thoại lỗi
  của MathType là modal) thoát ra. Script bị dừng giữa chừng thì tiến trình canh tự dọn.
- Khôi phục `CutAndCopy`, `OutputTranslator`, `MTEFInTeX`, `TransNameInTeX` (HKCU\Software\Design Science\DSMT7\General)
  nếu bị đổi, sau khi MathType đã thoát.
- Cuối mỗi lần chạy in "Guard: ..." (đã đóng gì, đã khôi phục gì, còn sót tiến trình nào).
- Đã chạy 3 lần (1 roundtrip, 2 lần đo): mỗi lần guard đóng MathType và MathTypeLib do script khởi động, không còn
  tiến trình nào sót; lần roundtrip khôi phục `OutputTranslator` từ `Texvc.tdl` về `LaTeX.tdl`. Đường hết giờ (tiến trình
  canh đóng Word đang treo) **chưa được thử**, vì không lần nào bị treo.

**Kiểm chứng sau khi sửa [đã chạy 24/10, 21:13–21:25]:**

- `mvn -o test` (`SpaceCodeTest`): 4/4 qua. Tạm đưa `\,` về U+EF07 thì 3/4 test hỏng (U+EF07 bị bắt), rồi trả lại;
  `mvn -o package` qua.
- Sinh lại `KIEM_TRA_mo_rong.docx` từ `out/KIEM_TRA_mo_rong.pandoc.docx` và `out/KIEM_TRA_mo_rong.tex.txt` (tạo cùng lúc
  với `samples/KIEM_TRA_mo_rong.md`): 116 đối tượng MathType, 2 công thức giữ Equation như trước; không còn U+EF07;
  MT66, MT98, MT118 ghi U+EF02.
- `mathtype_roundtrip.ps1 -Translator Texvc.tdl` (`out/KIEM_TRA_mo_rong.roundtrip.texvc.txt`): 116/116 đổi sang TeX,
  không có hộp thoại lỗi; B26 ra `a\,b`, B58 và C5 ra `...\,dx`. Lần chạy cũ (`out/KIEM_TRA_mo_rong.roundtrip.txt`,
  đối tượng còn U+EF07) đã lặng lẽ bỏ khoảng: B26 ra `ab`, B58 và C5 ra `{x}dx`.
- **Toggle TeX có vẻ không dùng `OutputTranslator`** khi `CutAndCopy = Std`: cả lần cũ lẫn lần này đều dịch U+EF04 thành
  `\ ` (kiểu Texvc; `LaTeX.tdl` sẽ cho `\;`) và không hiện hộp thoại lỗi U+EF07. Không đổi `CutAndCopy` trên máy để thử
  thêm. Lỗi "No translation available" có lẽ xuất hiện ở đường Cut/Copy dùng bộ dịch Texvc **[suy luận]**.

**Độ rộng hiển thị do chính MathType vẽ [đã chạy, `tools/extract_mtef.py` + `tools/mathtype_measure.ps1`]:**

MathType dựng ảnh từ MTEF qua `MT6.dll` (`MTXFormEqn`, `MTGetLastDimension`; hằng số và chữ ký lấy từ mã VBA của
"MathType Commands 2016.dotm"). Đơn vị trả về là 1/32 point; đã đối chiếu với `w:dxaOrig` của 4 đối tượng MathType tự
sinh (vd. `a\,b`: 512 = 16 pt = 320 twip). Kích thước bị làm tròn tới 1 pt, nên đo chuỗi 12 khoảng giống nhau giữa `a`
và `b` rồi chia 12. Cỡ chữ Full 12 pt (1 em = 12 pt).

| Mẫu | Độ rộng một khoảng | Theo em | TeX tương ứng |
|---|---|---|---|
| U+EF07, MathType tự nhập `\,` | 1,08 pt | 0,09 em | `\,` = 2 pt (TeX), dịch ngược thành `{\mkern 1mu}` = 0,67 pt |
| U+EF07, spike cũ | 1,08 pt (giống hệt MathType) | 0,09 em | |
| **U+EF02, spike mới** | **2,17 pt** | **0,18 em** | `\,` = 1/6 em = 2 pt |
| U+EF04 (`\;`, `\ `) | 3,33 pt | 0,28 em | `\;` = 5/18 em = 3,33 pt |
| U+EF05 (`\quad`) | 11,9 pt | 0,99 em | `\quad` = 12 pt |

Một khoảng đơn lẻ: `a\,b` rộng 16 pt (MathType, U+EF07) so với 17 pt (U+EF02); `\int_0^1 x\,dx` rộng 32 pt so với
33 pt; bỏ hẳn khoảng thì 16 pt và 31 pt.

Kết luận: U+EF02 hiển thị gần đúng `\,` của TeX (2,17 so với 2 pt). `\,` mà MathType tự nhập (U+EF07) chỉ rộng khoảng một
nửa. Công thức của spike vì vậy rộng hơn công thức MathType tự nhập khoảng 1 pt ở mỗi `\,`. Nếu sau này muốn khớp đúng
độ rộng MathType, không có mã nào vừa rộng 1,08 pt vừa có bản dịch Texvc **[suy luận từ bảng dịch]**, nên giữ U+EF02.

**Dữ liệu cho tích hợp (chưa làm):** `out/KIEM_TRA_10.after_mathtype_edit.docx` là bản `KIEM_TRA_10` do người kiểm tra
mở, sửa và lưu bằng MathType (10 đối tượng, có preview WMF và kích thước do MathType dựng lại). Dùng để hiệu chỉnh kích
thước preview khi đưa vào backend (tuần 2). `KIEM_TRA_10.docx` giữ bản spike sinh ra.

Spike đóng băng sau mục này: không thêm tính năng; tích hợp vào `exam/export` để sang tuần 2.
