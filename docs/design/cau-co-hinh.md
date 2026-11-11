# G0 – Nhập câu có hình từ Word: số liệu, thử nghiệm, hợp đồng dùng chung

## TIẾN ĐỘ (cập nhật 11/11/2026)

- Phần 1, 2a, 2b, 2c, 3: **xong**. **Thiết kế đã được duyệt 11/11** kèm 5 quyết định (cuối file); H3 đổi sang URL có chữ ký,
  H9 thêm 3 việc bắt buộc.
- Giới hạn của số đo: vai trò ảnh lấy từ bản tách `bank-json-c2` ngày 27/10 (trước PR #56); 2b đo bằng pandoc 3.12.1 trên
  Windows (cùng phiên bản `back_end/Dockerfile:24`), **chưa chạy lại trong container Linux**.
- Nền: `origin/main` 71b5b2b, migration mới nhất V26. Mã thử nghiệm và ảnh so sánh nằm ngoài repo.

## 1. Số liệu (4 bộ `bo-cau-hoi`, 161 câu)

| Bộ | Câu | Câu có ảnh | Chỉ có hình thật | Có ảnh công thức WMF | Có hình vẽ OLE | Ảnh nổi / trong dòng |
|---|---|---|---|---|---|---|
| k8c3b1 | 40 | 14 | 14 | 0 | 0 | 11 / 3 |
| k8c3b2 | 40 | 20 | 12 | 6 | 2 | 6 / 113 |
| k8c4b1 | 40 | 11 | 1 | 10 | 0 | 1 / 36 |
| k8c5b2 | 41 | 18 | 18 | 0 | 0 | 16 / 4 |
| Tổng | 161 | 63 | **45** (44 không vướng lý do bỏ khác) | 16 | 2 | 34 / 156 |

- "Ảnh công thức" là loại `formula_image` của `QCtx.classify` (`QCtx.java:171-180`: cao < 0,9 cm, hoặc WMF cao < 2 cm).
- **Hình thật: 52 ảnh** = 19 EMF + 33 PNG. Không có JPEG, GIF. **Ảnh công thức: 136, toàn bộ WMF**, trung vị 1,0 KB.
  Hai file còn lại: `mau-de` 4 ảnh (3 WMF, 1 PNG, tổng 19 KB), `materials-test` 0 ảnh. Đuôi file khớp magic bytes ở mọi ảnh raster.
- Vai trò hình thật: câu dẫn 42, lời giải 9, ý tf 1, **phương án 0**. Ảnh công thức: lời giải 98, phương án 27, câu dẫn 11.
- **Cả 4 phương án là hình thật: 0 câu.** Cả 4 phương án là ảnh công thức WMF: 6 câu (k8c4b1).
- Hình thật mỗi câu: nhiều nhất 2. Một câu của k8c3b2 có 41 ảnh công thức trong lời giải.
- Ngữ cảnh chung Phần III: 10 câu Phần III có hình, mỗi câu một hình riêng; không hình thật nào dùng chung cho nhiều câu.
  [chưa kiểm cách `DocParser` gán hình khi có ngữ cảnh chung thật]

| Hình thật | n | KB trung vị / lớn nhất | Pixel trung vị / lớn nhất | Rộng hiển thị trung vị / lớn nhất |
|---|---|---|---|---|
| PNG | 33 | 20,6 / 237 | 369×357 / 939×541 | 5,3 / 11,4 cm |
| EMF (pixel = khung thiết bị) | 19 | 22,3 / 443 | 1617×1174 / 3082×1772 | 5,9 / 10,0 cm |
| Nổi (anchor) | 33 | 21,5 / 443 | 454×460 | 5,7 / 11,4 cm |
| Trong dòng (inline) | 19 | 22,3 / 43 | 573×403 | 5,7 / 7,7 cm |

Dung lượng: 52 hình = 2088 KB nguồn trên 161 câu → **khoảng 13 MB cho 1000 câu** (323 hình, trung bình 40 KB).
Sau khi đổi EMF → PNG (mục 2a): 5 mẫu từ 9–228 KB, cùng cỡ với nguồn.

## 2. Thử nghiệm

### 2a. EMF/WMF → PNG trong image `datn-backend:check` (Ubuntu noble, dựng 04/11), 5 EMF + 3 WMF công thức

| Cách | Chạy | Image tăng (nén / trên đĩa) | Thời gian | 5 hình EMF | 3 ảnh công thức WMF |
|---|---|---|---|---|---|
| A. libwmf-bin + gsfonts + emf2svg + rsvg | EMF 5/5; WMF 1/3 (SVG sai UTF-8); thiếu gsfonts thì WMF 0/3 | +42 / +140 MB | 81–360 ms | 3 đúng; 1 sai màu tô và không cắt theo đường tròn; 1 ký hiệu góc vuông bị tô đen | hỏng |
| B. LibreOffice 24.2 headless | 8/8, chạy được với uid 10001, không cần mạng | +128 / +467 MB | 0,7–1,0 s/ảnh gọi riêng; 8 ảnh một lần gọi 2,0 s (96 dpi) và 5,2 s (2550×3300); RSS đỉnh 214 MB | **5 đúng hình và chữ** (1 hình có 2 nhãn số chồng nhẹ); xuất cả trang, phải tự cắt lề trắng | đúng bố cục nhưng dấu mũ (phông MT Extra) thành ô vuông |
| C. Inkscape 1.2.2 | 8/8 | +95 / +417 MB | 0,30–0,55 s/ảnh | 2 đúng; 2 có khung thừa; 1 sai màu tô và không cắt | chữ dồn chồng lên nhau |
| D. Apache POI 5.4.1 `poi-scratchpad` (Java, không cài gói hệ điều hành) | 8/8 không báo lỗi | +1,9 MB | 15–600 ms | **0 đúng**: mất đường tròn, điểm, ký hiệu góc vuông; chữ bị cắt, đổi phông | như C |

**Đề xuất: B (LibreOffice)**, gọi một lần cho tất cả EMF của một lô, sau đó cắt lề trắng và thu về cạnh dài tối đa 1600 px
bằng ImageIO. **Dự phòng khi đổi thất bại** (lỗi, quá 60 giây, máy không có `soffice`): bỏ riêng câu đó với lý do
`image_convert_failed`, không nhập câu thiếu hình; các câu khác của lô vẫn nhập.
**Ảnh công thức WMF: không cách nào vẽ đúng** → vẫn bỏ câu như hiện nay (quyết định 1).
LibreOffice chỉ chạy khi nhập, không chạy khi xem hay xuất đề (quyết định 2).

### 2b. Xuất Word (đo trên `PandocRunner.MARKDOWN_FORMAT`, `PandocRunner.java:51`, có `--sandbox` như dòng 163)

| Cách nhúng | Kết quả dưới `--sandbox` |
|---|---|
| `![](data:image/png;base64,…){width=5cm}` | **Chạy**: ảnh vào `word/media`; 33 ảnh (953 KB, Markdown 1,27 MB): 0,6–1,1 s, 26 MiB |
| `![](fig.png)` file trong thư mục tạm | **Không chạy**: `PandocResourceNotFound`, **exit 0**, docx thiếu ảnh mà không báo lỗi (bỏ `--sandbox` thì chạy) |

MathType (`MathTypeDocxConverter.convert`, lớp trong `back_end/target/classes`; mã module `mathtype` không khác `origin/main`):
file 12 công thức + 4 ảnh (ảnh cùng dòng với công thức, có công thức dài bị ngắt) → 12/12, **Kept-Omml = 0**; file 165 công
thức + 33 ảnh → 165/165, **Kept-Omml = 0**. Số `w:drawing` giữ nguyên (4 và 33); Word mở được cả hai file. Lý do khớp: bộ
chuyển chỉ đếm `m:oMath` (`MathTypeDocxConverter.java:93-101`), ảnh không sinh `m:oMath`.

### 2c. Hiển thị web

`MathText` (`front_end/src/components/common/MathText/MathText.tsx:14-52, 71-102`) tách chuỗi thành đoạn chữ / công thức, dùng ở
`QuestionContent`, `QuestionDetail`, `QuestionTable`, `QcVersionCard`. Giải pháp: `splitMath` thêm loại đoạn `image` cho token
ảnh (H2) ngoài công thức, vẽ bằng component mới `QuestionImage`. Token Bearer nằm trong header (`apiClient.ts:65-67`) mà
`<img>` không gửi header đó, nên DTO trả sẵn URL có chữ ký ngắn hạn (H3): `QuestionImage` là `<img src={url}>` (ghép với gốc
API của `apiClient`), không tải blob. Token riêng một dòng → ảnh khối căn giữa. `front_end/src` trên main chưa có màn nhập (`bank-imports`: 0 kết quả).

## 3. Hợp đồng dùng chung

**H1. Bảng `question_image`, lưu `bytea` trong DB.** Lý do: 13 MB/1000 câu là nhỏ; nằm sẵn trong `pg_dump`; ghi và xóa cùng
transaction với lô nhập; không thêm volume. Truy vấn danh sách không được chọn cột `data`.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | BIGSERIAL PK | chính là id trong token và URL |
| `owner_id` | INT NOT NULL → `users(id)` | người tải |
| `import_batch_id` | INT NOT NULL → `import_batch(id)` ON DELETE CASCADE | lô nhập |
| `item_key` | VARCHAR(20) NOT NULL | mã câu trong lô (`ImportItem.key`, ví dụ `mcq-007`) |
| `generated_question_id` | INT NULL → `generated_question(id)` ON DELETE CASCADE | NULL khi lô còn `preview`; gắn lúc xác nhận |
| `role` | VARCHAR(12) NOT NULL | `stem`, `option_a`..`option_d`, `statement_a`..`statement_d`, `solution` |
| `ordinal` | SMALLINT NOT NULL | thứ tự trong câu, từ 1 |
| `mime` | VARCHAR(10) NOT NULL | `image/png` hoặc `image/jpeg` |
| `data` | BYTEA NOT NULL, STORAGE EXTERNAL | ảnh đã chuẩn hóa (H4) |
| `byte_size`, `width_px`, `height_px` | INT NOT NULL | CHECK theo H4 |
| `display_width_mm` | SMALLINT NOT NULL | bề rộng hiển thị trong Word gốc, 5–120 |
| `sha256` | CHAR(64) NOT NULL | của **byte gốc trong .docx** (dùng cho H7 và làm ETag) |
| `source_format` | VARCHAR(5) NOT NULL | `png`, `jpeg`, `gif`, `bmp`, `emf` |
| `created_at` | TIMESTAMP NOT NULL | |

UNIQUE (`import_batch_id`, `item_key`, `ordinal`); INDEX (`generated_question_id`) WHERE NOT NULL. `bank_item` không có cột mới.

**H2. Token `⟦img:<id>⟧`** (`<id>` = `question_image.id`, số thập phân; thay cho `⟦imgN⟧` hiện ở `QCtx.java:153-169`).
- Vị trí hợp lệ: câu dẫn, phương án, ý tf, lời giải. Không hợp lệ: đáp số sa, trong `$...$`. Hình thật luôn đứng riêng một dòng.
- Phương án là hình: `optionA = "⟦img:831⟧"` (có thể kèm chữ); thỏa `chk_bi_not_blank`.
- Thoát: bộ nhập đổi ký tự `⟦` `⟧` có sẵn trong Word thành `[` `]`. Mọi đường ghi văn bản câu không phải bộ nhập (sửa câu,
  đầu ra LLM) gặp `⟦img:` thì: sửa câu trả 400 `INVALID_REQUEST`, đầu ra LLM bị xóa token. Đây là chốt bảo mật: không ai tự gõ
  id ảnh của người khác vào câu của mình.
- Xuất Word: token riêng dòng → đoạn riêng **căn giữa**, ngay tại vị trí token (bộ nhập đặt sau câu dẫn), rộng
  `min(display_width_mm, 120 mm)` (lề chữ 18,0 cm theo `ExamPage.java:14-26`); ảnh nổi trong Word gốc thành ảnh khối, không
  bọc chữ. Ảnh trong phương án: rộng tối đa 40 mm, 4 phương án xếp 2×2. `ExamMarkdownBuilder` chèn `![](data:…){width=…}`
  sau bước thoát ký tự (`ExamMarkdownBuilder.java:74` đã thoát `[` `]` nên nội dung đề không tự tạo được ảnh).

**H3. API: URL có chữ ký ngắn hạn** (duyệt 11/11; `<img>` không gửi được header `Authorization`).
- `GET /api/question-images/{id}?exp=<epoch giây>&u=<userId>&sig=<hex>` → 200, thân là byte ảnh. Endpoint **permitAll**
  (thêm vào danh sách ở `SecurityConfig.java:130-139`), không đọc JWT; chỉ kiểm chữ ký và hạn.
- `sig = HMAC-SHA256(key, "<id>|<exp>|<userId>")`, hex thường. Khóa riêng `app.question-images.url-signing-key` (biến môi
  trường `QUESTION_IMAGE_URL_KEY`, ≥ 32 byte, **không dùng `JWT_SECRET`**); thiếu khóa thì ứng dụng không khởi động. So chữ ký
  bằng phép so thời gian hằng (`MessageDigest.isEqual`). `u` có trong URL vì endpoint không có JWT để biết `userId`.
- Kiểm quyền **hai lần**. (a) Lúc dựng DTO: chỉ ký URL cho ảnh nằm trong câu mà người gọi đang được xem. (b) Lúc tải ảnh,
  sau khi chữ ký và hạn đúng: kiểm lại người dùng `u` **còn quyền xem ảnh** (là `owner_id`, hoặc `generated_question_id` là
  câu nguồn của `bank_item` đang hiển thị được với `u` theo `VISIBLE` ở `BankSearchRepository.java:42`; người dùng bị khóa
  hoặc xóa cũng coi là hết quyền); hết quyền → 404. Nhờ vậy quyền bị thu hồi có hiệu lực ngay, không phải chờ đến `exp`.
- Hạn: `exp` = hết giờ tròn kế tiếp + 1 giờ (URL sống 1–2 giờ và **giữ nguyên trong cùng một giờ**, để cache trình duyệt còn
  dùng được khi DTO được tải lại). Cấu hình `app.question-images.url-ttl`, mặc định 1 giờ.
- Header: `Content-Type` theo `mime`; `Cache-Control: private, max-age=<số giây còn lại đến exp>`; `ETag: "<sha256>"`
  (`If-None-Match` khớp → 304, vẫn phải qua kiểm chữ ký và kiểm quyền). **Ghi chú:** `sha256` là của byte gốc trong .docx; nếu
  sau này mã hóa lại ảnh đã lưu thì phải đổi ETag (ví dụ thêm cột sha256 của byte lưu); `X-Content-Type-Options: nosniff`; `Referrer-Policy: no-referrer`.
- Lỗi: thiếu tham số, chữ ký sai, hết hạn, ảnh không tồn tại → đều **404 `IMAGE_NOT_FOUND`** (không phân biệt). Không ghi
  `sig` vào log truy cập.
- Xuất Word **không dùng URL**: `ExamMarkdownBuilder` nhận byte ảnh đọc từ DB và nhúng data URI (2b).

Trường mới `images` (mảng, bỏ khi rỗng) trong `ExamItemDto` (`ExamViewDtos.java:43`), `BankListItemDto`
(`BankSearchDtos.java:30`), `ImportItem` (`BankImportDtos.java:35`); văn bản vẫn giữ token; `url` là đường dẫn tương đối, giao
diện dùng nguyên, không tự ghép tham số:

```json
{ "slotId": 412, "questionType": "mcq", "source": "bank", "bankItemId": 977,
  "questionText": "Cho tam giác $ABC$ vuông tại $A$ như hình vẽ. Tính $BC$.\n⟦img:812⟧",
  "optionA": "$5$", "optionB": "$7$", "optionC": "⟦img:813⟧", "optionD": "$25$", "correctOption": "A",
  "images": [
    { "id": 812, "role": "stem", "ordinal": 1, "mime": "image/png", "widthPx": 1235, "heightPx": 681,
      "displayWidthMm": 100,
      "url": "/api/question-images/812?exp=1794409200&u=27&sig=9f2c41d07a6be3158c0d5e7fa1b24c6390e8d7a5b3f1c2e4d6a8b0c1e3f5a7d9" },
    { "id": 813, "role": "option_c", "ordinal": 2, "mime": "image/png", "widthPx": 300, "heightPx": 280,
      "displayWidthMm": 35,
      "url": "/api/question-images/813?exp=1794409200&u=27&sig=41b7e0c93d5a2f68170e9c4b8a6d3f5271c0e9b8a7d6f5e4c3b2a1908f7e6d5c" } ] }
```

(`sig` trong ví dụ là giá trị minh họa.) Xem trước nhập: `items[].images` cùng dạng, `items[].flags` thêm cờ thông tin
`has_figure` (giao diện hiện nhãn "Có hình"; **không** đưa vào `reviewReasons`, quyết định 4). Lý do bỏ mới trong `skipped`:
`image_convert_failed`, `image_limit`. `has_image` giữ cho ảnh công thức WMF và hình vẽ OLE, giao diện hiện "công thức dạng
ảnh, chưa nhập được" (quyết định 1).

**H4. Chuẩn hóa khi nhập.** Kiểm magic bytes, không tin đuôi file: PNG `89 50 4E 47`, JPEG `FF D8 FF`, GIF `GIF8`, BMP `BM`,
EMF (bản ghi 1 + ` EMF` ở offset 40). Đọc kích thước từ header trước khi giải mã. Mọi ảnh giải mã rồi mã hóa lại
(bỏ metadata): JPEG giữ JPEG, còn lại thành PNG.

| Giới hạn | Giá trị | Số đo lớn nhất |
|---|---|---|
| Ảnh nguồn | ≤ 2 MB, ≤ 4000×4000 px và ≤ 12 MP | 443 KB; 3082×1772 |
| Ảnh lưu | ≤ 500 KB, cạnh dài ≤ 1600 px | 228 KB (EMF đã đổi) |
| Số hình mỗi câu | ≤ 6 | 2 |
| Mỗi lô | ≤ 100 hình, ≤ 20 MB sau chuẩn hóa | 20 hình; 857 KB nguồn |

Vượt giới hạn → bỏ câu, lý do `image_limit`. LibreOffice: chỉ khi nhập, **một tiến trình cho mỗi lô**, trong permit của
`PandocLimiter`, thư mục tạm riêng, timeout 60 giây; câu có ảnh đổi lỗi bỏ với `image_convert_failed` (quyết định 2).
Hình ở lời giải được nhập như hình ở câu dẫn (quyết định 5).

**H5. Ngân hàng → đề: tham chiếu, không chép.** `bank_item` chép văn bản từ câu nguồn nên giữ nguyên token; ảnh thuộc câu
nguồn (`generated_question_id`). Giữ câu sinh có hình: chưa có đường nào tạo ra, không làm. Sửa câu có hình: không mở trong
phạm vi này; câu lấy từ ngân hàng vốn không sửa được trong đề (409 `SLOT_FROM_BANK`, `ExamActionService.java:253-256`).
"Giữ hình / bỏ hình" để lại đến khi mở sửa câu ngân hàng.

**H6. Dọn dẹp.** Hủy lô (`BankImportService.java:303-308`): CASCADE. Xác nhận (`:318`): gắn `generated_question_id` cho câu
được chọn, xóa ảnh của câu không chọn, cùng transaction. Hết hạn (`purgeExpired`, `:509-512`): xóa ảnh có
`generated_question_id IS NULL` của lô đó. Xóa câu nguồn: CASCADE. Sao lưu: `pg_dump -Fc` cả DB (`backup.sh:31`), **không đổi**;
file dump tăng khoảng 13 MB cho 1000 câu (PNG không nén thêm được).

**H7. Loại trùng.** `DedupCandidate` (`DedupCandidate.java:19-29`) thêm `imageKey`: các `sha256` của ảnh ở câu dẫn, phương án,
ý tf (không tính lời giải), sắp theo (`role`, `ordinal`), nối bằng dấu phẩy; không có hình thì rỗng. `DedupChecker.check` chỉ so
các câu **cùng `imageKey`** (đặt cạnh điều kiện cùng dạng, `DedupChecker.java:62`), và bỏ token khỏi chữ trước khi so. Khác hình →
không bao giờ trùng; cùng chữ cùng hình → trùng như cũ. Nơi sửa: `ImportPreviewBuilder.java:216`, `ImportDedupRepository.java:54`,
`ExamSlotSiblings.java:126,143`, `DedupService.java:65`, `QcOrchestrator.java:407`.

**H8. QC.** Câu nhập có hình không qua QC và Solver, như mọi câu nhập (M-10, quyết định 3/11 mục 4). Trong phạm vi này không
đường nào đưa câu có hình vào QC. Chốt phòng ngừa: `QuestionEditService.enqueueAnswerCheck` (`QuestionEditService.java:276`)
không xếp Solver cho câu có token ảnh và ghi lý do thông tin `has_figure_unchecked` ("có hình, chưa kiểm đáp án tự động").

**H9. Chia PR** (thứ tự merge: I1 → I2, O1, U1 song song → I3, O2 → U2)

| PR | Nội dung | Giờ | Test chính |
|---|---|---|---|
| I1 ĐẦU VÀO | migration V27 `question_image`, entity, bộ ký URL, `GET /api/question-images/{id}`, `QuestionImageDto`, tiện ích token | 6 | MockMvc không kèm JWT: chữ ký đúng 200 + header; sai chữ ký, đổi `id`/`u`/`exp`, hết hạn → 404; 304; chữ ký còn hạn nhưng `u` đã hết quyền (không còn là chủ, câu ngân hàng không còn hiển thị với `u`, `u` bị khóa) → 404 |
| I2 ĐẦU VÀO | chuẩn hóa ảnh (H4), chạy LibreOffice, Dockerfile | 8 | PNG khai 20000×20000 bị từ chối trước khi giải mã; magic sai đuôi; EMF mẫu tự vẽ |
| I3 ĐẦU VÀO | `DocxReader` nạp ảnh, token mới, bỏ `has_image` cho hình thật, lưu / xác nhận / hết hạn, dedup khi nhập | 8 | `DocxBuilder` thêm ảnh; hai câu cùng chữ khác hình không trùng; ảnh câu không chọn bị xóa |
| O1 ĐẦU RA | `images` trong xem đề và ngân hàng; chặn `⟦img:` khi sửa câu; H7, H8 phía QC | 5 | xem đề trả `images`; sửa câu có token → 400 |
| O2 ĐẦU RA | `ExamMarkdownBuilder` chèn data URI; đếm ảnh trong docx | 6 | docx có đủ ảnh; thiếu ảnh → lỗi rõ; MathType Kept-Omml = 0 |
| U1 GIAO DIỆN | `MathText` + `QuestionImage` (`<img src={url}>`), nhãn "Có hình" | 3 | token → ảnh; ảnh lỗi hoặc URL hết hạn hiện chữ thay thế, tải lại DTO |
| U2 GIAO DIỆN | màn xem trước nhập: hình, cờ, lý do bỏ mới | 4 | |

Migration: 1 file, **V27 thuộc câu có hình** (quyết định 3); PR phương án nhiễu (thiết kế của nó cũng ghi V27, dòng 138 của
`phuong-an-nhieu-loi-sai.md`) lấy V28. O1 và U1 không chờ I3: dùng một dòng `question_image` tự chèn.

**Bắt buộc ghi trong PR** (điều kiện để merge):
- **I2:** đo RAM đỉnh của LibreOffice **trong container app dựng từ Dockerfile thật** khi đổi một lô 20 ảnh EMF, kèm thời
  gian; ghi số vào mô tả PR. (Số hiện có: 214 MB cho 8 ảnh, đo trong image thử, không có JVM chạy cùng.)
- **O2:** chạy lại thử nghiệm 2b trong container Linux dựng từ Dockerfile thật (data URI dưới `--sandbox`, MathType
  Kept-Omml = 0), ghi kết quả vào PR; số ở mục 2b mới đo trên Windows.
- **O2:** sau pandoc và sau bước MathType, đếm ảnh câu hỏi trong docx (`w:drawing`) so với số token đã chèn; lệch thì ném
  `DocumentExportException` 500 có mã riêng (`EXPORT_IMAGE_MISSING`) và ghi số thiếu vào log, không trả file thiếu hình.

**H10. Rủi ro và ngoài phạm vi**

- LibreOffice: RSS đỉnh 214 MB trong container giới hạn 1536 MB (`compose.prod.yaml:67`; VPS 2 GB đặt 1 GB), lại đọc file
  không tin cậy → chung permit với pandoc, 60 giây, uid 10001, thư mục tạm; đo `docker stats` trước khi triển khai.
- pandoc bỏ ảnh mà vẫn exit 0 → O2 đếm lại `w:drawing`, lệch thì báo lỗi rõ.
- URL ảnh có chữ ký là "ai có URL thì xem được" cho đến `exp`: hạn ngắn (1–2 giờ), chữ ký gắn `id` + `exp` + `userId` nên
  không đổi sang ảnh khác được; khóa riêng, đổi khóa là vô hiệu mọi URL; `Cache-Control: private`, `Referrer-Policy:
  no-referrer`, không ghi `sig` vào log. Endpoint kiểm lại quyền của `u` mỗi lần tải nên thu hồi quyền có hiệu lực ngay; phần
  còn lại là ảnh đã nằm trong cache trình duyệt của người đó tới `exp`.
- Hình nổi gán nhầm câu (`figure_from_previous_question` chỉ là cờ thông tin, `ImportFlags.java:34,42`) → xem trước hiện hình
  ngay trong câu để giáo viên soát.
- Ảnh là nội dung bộ đề thương mại → chỉ ký URL cho người có quyền xem câu; không đưa ảnh thật vào fixture, log.
- I3 phải chạy bộ nhập hiện tại trên 4 bộ và so lại với bảng mục 1 (số vai trò ở đây đo trước PR #56).

Ngoài phạm vi: AI vẽ hình; giáo viên tải hình mới; đồ thị vẽ từ dữ liệu; sửa câu có hình; ảnh công thức WMF và hình vẽ OLE;
bảng dữ liệu; hình trong đáp số sa; chữ bọc quanh hình.

## Quyết định đã chốt (An, 11/11/2026)

1. Tiếp tục bỏ câu có ảnh công thức WMF (16/161 câu) và hình vẽ OLE (2 câu); lý do hiển thị "công thức dạng ảnh, chưa nhập được".
2. Dùng LibreOffice: chỉ khi nhập, một tiến trình mỗi lô, trong permit `PandocLimiter`, có timeout; câu đổi lỗi bỏ với
   `image_convert_failed`.
3. Migration câu có hình là V27.
4. Câu ngân hàng có hình chỉ hiện nhãn "Có hình", không vào `reviewReasons`.
5. Nhập cả hình ở lời giải.
