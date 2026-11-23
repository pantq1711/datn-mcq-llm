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

*Chạy lại trong container Linux dựng từ Dockerfile thật (23/11, O2):* data URI dưới `--sandbox` chạy, ảnh dạng file vẫn exit 0 mà thiếu ảnh, MathType
Kept-Omml = 0 với 12 công thức + 4 ảnh và 165 + 33, số `w:drawing` giữ nguyên; heap pandoc theo cỡ ảnh ở bảng mục 4.2 (20 MB ảnh không vừa 256 MB).

### 2c. Hiển thị web

`MathText` (`front_end/src/components/common/MathText/MathText.tsx:14-52, 71-102`) tách chuỗi thành đoạn chữ / công thức, dùng ở
`QuestionContent`, `QuestionDetail`, `QuestionTable`, `QcVersionCard`. Giải pháp: `splitMath` thêm loại đoạn `image` cho token
ảnh (H2) ngoài công thức, vẽ bằng component mới `QuestionImage`. Token Bearer nằm trong header (`apiClient.ts:65-67`) mà
`<img>` không gửi header đó, nên DTO trả sẵn URL có chữ ký ngắn hạn (H3): `QuestionImage` là `<img src={url}>` (ghép với gốc
API của `apiClient`), không tải blob. Token riêng một dòng → ảnh khối căn giữa. ~~`front_end/src` trên main chưa có màn nhập~~ (lỗi thời, ghi chú 24/11: màn nhập
Word đã có trên main từ PR #105, `front_end/src/features/bank-import`).

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

*Cài đặt `images` ở ngân hàng và xem đề (22/11/2026, sau E2E 22/11 B8):* một hàm duy nhất
`QuestionImageService.signedImagesBySourceQuestion(questionIds, viewerId)` nạp metadata ảnh của nhiều câu nguồn bằng MỘT truy vấn
(chiếu không có cột `data`) và ký URL cho người xem. `BankService` gọi một lần mỗi trang danh sách và mỗi lần xem chi tiết (câu
riêng của người khác đã bị lọc trước đó nên không có URL); `ExamViewService` gọi một lần cho cả đề (danh sách đề chỉ cần số đếm nên truyền
người xem null: không ký URL, không truy vấn ảnh), chỉ với các chỗ `source = "bank"` (ảnh thuộc câu nguồn của `bank_item`, H5), bỏ qua truy vấn khi đề không có chỗ ngân hàng; người
xem là chủ đề (`actorId`, đã đi qua tới `ExamViewService.item` cho 4 thao tác trong `ExamActionService`). `images` bỏ khỏi JSON khi
rỗng nên JSON của câu không hình và ảnh chụp `golden.json` không đổi. Thêm phụ thuộc `bank ──► questionimage` và
`exam ──► questionimage` (`docs/architecture/backend-structure.md`). Còn lại của H5 (đường đưa câu có hình vào đề) vẫn chưa làm:
tạo đề và Đổi câu hiện không chọn câu ngân hàng có hình, nên `images` của mục đề chưa xuất hiện trong luồng thường.

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
đường nào đưa câu có hình vào QC. Chốt phòng ngừa: `QuestionEditService.enqueueAnswerCheck`
không xếp Solver cho câu có token ảnh và ghi lý do thông tin `has_figure_unchecked` ("có hình, chưa kiểm đáp án tự động").

*Điều chỉnh 22/11/2026 (sau E2E 22/11, B7 và B10; chủ dự án duyệt):* chỉ làm phần "không xếp Solver"; **bỏ cờ
`has_figure_unchecked`**. Lý do: sau chốt H2 (sửa câu có `⟦img:` trả 400) không còn bản sửa nào chứa token, nên đường này không
thể tới được bằng HTTP; cờ thông tin (`infoReasons`) lại lấy từ kết quả QC đã lưu (`QcSummaryService`), không có chỗ lưu cho
một lý do không đến từ QC. Chốt chặn nằm ở `QuestionEditService.enqueueAnswerCheck`: câu có token ở phần Solver nhìn thấy
(đề dẫn, phương án, ý Đúng/Sai) thì không xếp job; test khóa ở `QuestionEditImageGuardTest`. Nếu sau này mở "sửa câu có hình"
(H5) thì phải quyết lại cách báo "chưa kiểm đáp án" cho giáo viên.

**Việc phải xử lý trước khi mở đường đưa câu có hình vào đề** (ghi nhận 22/11, chưa làm; hiện `ExamSlotRepository.java:104` còn
chặn câu có hình): kiểm quyền tải ảnh của I1 (`QuestionImageService.hasAccessPermission`) lệch với quyết định 16/11 ("câu đã
gỡ khỏi ngân hàng vẫn hiện ở đề cũ"). Người không phải chủ ảnh chỉ tải được khi câu ngân hàng chưa gỡ (`removed_at IS NULL`), nên
giáo viên B dùng câu chung của A, A gỡ câu thì đề cũ của B mất ảnh (giao diện hiện chữ thay thế); ngược lại chủ ảnh luôn tải
được kể cả khi đã gỡ. Cần quyết định: người có chỗ đề đang dùng câu ngân hàng đó có được xem ảnh không (H3(b)).

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

## 4. Bổ sung 23/11/2026 – đưa câu có hình vào đề (đã duyệt 23/11)

Trạng thái: **thiết kế, chưa sửa mã, chưa chạy gì** (không Maven, Docker, DB, Gemini). Căn cứ: đọc mã `origin/main` 96087d1
(PR #101) và nhánh giao diện `fe/figure-display` 8a3f3ff; số dòng dưới đây theo hai bản đó. Mục này bổ sung cho H5, H8, H9; nếu
mâu thuẫn với H9 thì mục này thắng, nhưng chỉ sau khi chủ dự án duyệt mục 4.6.

### 4.0 Hiện trạng đã kiểm

| Chỗ | Tình trạng | Bằng chứng |
|---|---|---|
| Ứng viên ngân hàng cho tạo đề, Đổi | Loại câu có hình. **X1 đã làm 24/11:** bỏ điều kiện, câu có hình là ứng viên như mọi câu | `ExamSlotRepository.java:104`; một truy vấn dùng chung `findBankCandidates` (gọi từ `ExamBuildService.java:209-210` và `ExamActionService.java:237-245`) |
| Xem đề, ngân hàng (giáo viên) | Có `images`, URL ký cho người xem | `ExamViewService.java:196-206`, `BankService.java:124-141` |
| Giao diện giáo viên | Vẽ ảnh | PR2 (`MathText`, `QuestionImage`) |
| **Xuất Word** | **Chưa**: `ExamMarkdownBuilder.withoutLiteralNewlines(ExamItemDto)` dựng lại `ExamItemDto` bằng 26 tham số, **rơi `images`** (`ExamMarkdownBuilder.java:185-195`); token `⟦img:id⟧` đi qua `cleanText` và ra file Word thành chữ thường | đọc mã |
| **Học sinh** | **Chưa**: `AssignmentPaper.publicParts` và `reviewItem` chỉ trả `b.questionText()` (`AssignmentPaper.java:93-114, 218-247`); `PublicQuestionDto`, `ReviewItemDto` không có trường ảnh; 3 màn học sinh gọi `MathText` không truyền `images` nên hiện chữ thay thế | đọc mã |
| Chặn giao bài có hình | **Chưa có**: `AssignmentService.create` chỉ kiểm `ASSIGN_NOT_ALL_KEPT` và `ASSIGN_SA_KEY_UNSUPPORTED` (`AssignmentService.java:54-55, 98-144`), trong khi `lam-bai-truc-tuyen.md` mục 6 đã ghi TODO `ASSIGN_HAS_FIGURE` (14/11). A1 thêm chặn 23/11; **A2 đã làm 24/11:** gỡ `ASSIGN_HAS_FIGURE`, đề có hình giao được | đọc mã, đọc tài liệu |
| `QcVersionCard` | Chỉ dùng `MathText` cho `solverReasoning`; `VersionDto` của vết QC **không có chữ đề, không có ảnh** (`ExamQcTraceDtos`, `qcTrace.types.ts`); Solver không chạy cho câu có hình (H8) | đọc mã |
| Cờ "giống câu khác trong đề" | `ExamSlotSiblings.fromBank` (`:124-130`) dựng `DedupCandidate` **không có `imageKey`**: phần còn lại của H7. *Đính chính 24/11 (X1):* `ExamSlotSiblings` là dedup câu sinh mới/câu sửa với các chỗ khác; cờ thông tin `similar_in_exam` tính riêng ở `ExamQuestionSimilarity` lúc xem đề. **X1 đã làm 24/11** ở cả hai: khóa hình một định nghĩa (`QuestionImageService.imageKeysBySourceQuestion`, cũng dùng cho so trùng khi nhập Word), cùng chữ khác hình không báo | đọc mã |

**Thứ tự bắt buộc:** bỏ điều kiện ở `ExamSlotRepository.java:104` chỉ được merge sau khi xong (F1) sửa quyền xem ảnh, (O2) xuất
Word, (A1) chặn giao bài có hình. Thiếu O2 thì file Word ra chữ `⟦img:812⟧` mà không báo lỗi; thiếu A1 thì học sinh nhận câu
thiếu hình, không trả lời được; thiếu F1 thì đề cũ mất ảnh khi người Giữ gỡ câu (xem H8, "Việc phải xử lý trước...").

### 4.1 Học sinh xem ảnh thế nào

**Ràng buộc rút từ mã:**
1. Học sinh không có tài khoản. Danh tính duy nhất là token lượt làm ở header `X-Attempt-Token` (`PublicAttemptController.java:30`);
   `<img>` không gửi header đó. Thiết kế đã chốt "không cookie", nên H3 (`id|exp|userId`, kiểm quyền theo người dùng) không dùng được.
2. Link bài giao đã cho **bất kỳ ai có link** xem chữ của đề (trần 300 lượt, `PublicAttemptService.start`). Vì vậy ảnh không thể
   bí mật hơn chữ; mục tiêu thực tế là "không rộng hơn những gì JSON đã trả cho đúng lượt đó".
3. Thời gian: lượt làm 5–180 phút, nộp trễ ân hạn 2 phút (`AttemptRules.GRACE`); xem lại sau khi đóng có thể nhiều ngày sau (P3 dựng
   lại DTO mỗi lần). URL H3 sống 1–2 giờ nên không đủ cho lượt 180 phút.
4. Ảnh `solution` (lời giải) **không được lộ khi còn làm bài**: lời giải chỉ có khi bài đóng + hết ân hạn + `showSolutions`.
5. Hạn mức hiện có: tạo lượt 200/phút/IP, mở lại 30/phút/lượt, nộp 20/phút/lượt (`PublicLimitProperties`); mọi phản hồi `no-store`.

**So sánh bốn cách** (số liệu kích thước từ mục 1: hình thật trung vị 40 KB, lớn nhất 237 KB PNG; đề 40 câu thật ≈ 13 hình ≈ 0,5 MB;
cận trên theo số đo = 40 câu × 2 hình × 237 KB ≈ 19 MB):

| | Cách | Ưu | Nhược, rủi ro |
|---|---|---|---|
| **A** | **URL ký gắn lượt làm**: `GET /api/public/question-images/{id}?exp=&a=<attemptId>&sig=`; `sig = HMAC(khóa ảnh, "p\|id\|exp\|attemptId")`; P2/P3 trả `images[].url` | `<img>` thường, tận dụng cache; thu hồi theo lượt/bài; cào ảnh phải tạo lượt trước (trần 300, 200/phút/IP, để lại dấu lớp + STT); khóa chung H3, tiền tố `p\|` tách miền chữ ký nên URL giáo viên không dùng được cho học sinh và ngược lại | Thêm một đường `permitAll` (đổi hợp đồng API, M-35); URL là "ai có URL thì xem" tới `exp`; mỗi ảnh một truy vấn kiểm quyền |
| **B** | Nhúng data URI vào JSON P2/P3 | Không thêm endpoint, không URL bí mật, không phụ thuộc mạng giữa chừng | Cận trên 19 MB (base64 ≈ 25 MB) mỗi lượt; cả lớp 40 em bấm bắt đầu cùng lúc ≈ 1 GB qua một tiến trình có heap 846 MiB (`Dockerfile:51`); `no-store` nên mỗi lần tải lại gửi lại toàn bộ; P3 hạn mức 30/phút |
| **C** | `fetch` kèm `X-Attempt-Token` rồi dùng blob URL | Không có URL mang quyền; dùng đúng cơ chế token hiện có | Component riêng, bất đồng bộ, mất `loading="lazy"` và cache trình duyệt; H3/U1 đã bỏ cách này cho giáo viên vì phức tạp; blob giữ trong bộ nhớ trang |
| **D** | URL ký theo **mã bài giao** (không theo lượt) | Một URL cho cả lớp | Không thu hồi theo lượt; **không cần tạo lượt** để cào ảnh, qua mặt trần 300 và hạn mức tạo lượt; không gắn được với quyền xem lời giải |

**Đề xuất: A.** B bị loại vì cận trên; D bị loại vì mở rộng hơn JSON; C là phương án dự phòng nếu chủ dự án không chấp nhận URL mang
quyền (cách làm: thêm header vào cùng endpoint, bỏ `exp`/`sig`).

**Thiết kế A:**
- `exp`: khi đang làm bài = `deadline_at + 2 phút` (cố định cả lượt nên URL giữ nguyên qua các lần P3, cache dùng được); sau khi kết quả
  mở = hết giờ tròn kế tiếp + 1 giờ (như H3), vì P3 ký lại mỗi lần mở.
- Khi tải ảnh, sau khi chữ ký và hạn đúng, kiểm theo **cùng điều kiện hiển thị với JSON**: (1) lượt `a` còn tồn tại (T5/T6 xóa lượt hoặc
  bài thì 404 ngay); (2) ảnh thuộc một câu của bài giao của lượt đó, tức `question_image.generated_question_id = bank_item.source_question_id`
  với `bank_item.id ∈ assignment_item(assignment_id)`; (3) ảnh khác `solution`: `AttemptRules.canSubmit` **hoặc** (`resultsReleased` và
  `showSolutions`); ảnh `solution`: chỉ khi `resultsReleased` và `showSolutions`. Không đạt thì 404 `IMAGE_NOT_FOUND` (cùng mã H3, không phân biệt lý do).
- **Không dùng `hasAccessPermission` của H3** cho học sinh: hàm đó đòi `removed_at IS NULL`, còn bài giao đọc câu theo `assignment_item.bank_item_id`
  và không lọc `removed_at` (`BankService.getItems`); dùng lại sẽ tái hiện đúng lỗi quyền 22/11 ngay trên màn học sinh.
- DTO mới `PublicImageDto(id, mime, widthPx, heightPx, displayWidthMm, url)` (không `role`, `sha256`, kích thước byte), thêm trường `images`
  (bỏ khi rỗng) vào `PublicQuestionDto` và `ReviewItemDto`. Khi còn làm bài **không đưa** ảnh role `solution` vào `images`. Nạp theo lô, một truy vấn cho cả đề.
- Hạn mức: bộ đếm thứ tư theo lượt, `imagePerAttemptPerMinute` mặc định 300 (đề 80 ảnh + tải lại), 429 khi vượt. Header như H3, `Cache-Control: private,
  max-age=<min(giây còn lại đến exp, 3600)>`.
- Phụ thuộc module: cần `assignment ──► questionimage` (cạnh mới), hoặc đi qua `BankService` mà `assignment` đã phụ thuộc. **Chạm Q-8 và M-35: chờ chủ dự án.**

**Rủi ro lộ ảnh bộ đề thương mại (đã cân nhắc):**
1. Người có link: vốn đã thấy chữ, cũng chụp màn hình được; kỹ thuật không chặn được. Không nên hứa hơn mức này trong báo cáo.
2. URL bị chuyển tiếp: chỉ sống tới `exp`, gắn một lượt, không đổi sang ảnh khác được; mất hiệu lực khi xóa lượt/bài hoặc khi hết quyền hiển thị ở (3).
3. Cào hàng loạt: phải tạo lượt rồi mới có URL; mỗi lượt để lại lớp + STT, bị trần 300 và hạn mức theo IP. Với D thì không.
4. Máy phòng tin học dùng chung: ảnh nằm trong cache đĩa trình duyệt tới hết `max-age`; `no-store` chặt hơn nhưng mất cache khi tải lại. Mặc định `private`; chủ dự án quyết.
5. Bản quyền: đưa hình của bộ đề thương mại cho học sinh là phân phối lại, khác việc giáo viên dùng riêng. Hộp Giao bài có ô đồng ý (`consent_confirmed_at`) nhưng chỉ nói về học sinh và phụ huynh, chưa nói về hình. Đây là quyết định chính sách, không phải kỹ thuật.

**Bài giao đã đóng:** đóng sớm hoặc đóng lười thì lượt chưa nộp không còn `parts` nên ảnh 404 theo (3) (giao diện đã chuyển sang màn "đã đóng"); sau ân hạn,
`showSolutions = true` thì phần xem lại có ảnh, ký lại mỗi lần P3; `showSolutions = false` thì không có ảnh nào. Câu bị gỡ khỏi ngân hàng sau khi giao: vẫn hiện (đúng 16/11), ảnh đi
theo `assignment_item` nên vẫn tải được. Ảnh thuộc câu nguồn và ngân hàng chưa sửa được (H5) nên bài đã giao không đổi ảnh; khi mở "sửa câu ngân hàng" phải quyết lại có chụp ảnh khi giao hay không.

### 4.2 O2 – xuất Word theo H9

Ba điều bắt buộc ngay sau bảng H9 giữ nguyên; phần dưới là cách làm cụ thể.

1. **Nạp byte.** `ExamExportService.docx` sau `viewService.view` gom id ảnh từ `images` của view (đã lọc theo quyền người xuất, H3(a)), nạp byte bằng **một** truy vấn
   chiếu (`id, mime, data, widthPx, heightPx, displayWidthMm`) trong transaction đọc ngắn, **trước** khi vào `PandocRunner` (không giữ transaction lúc chạy pandoc).
   Không đi qua URL. `withoutLiteralNewlines(ExamItemDto)` phải giữ `images` (đang rơi).
2. **Data URI trong `ExamMarkdownBuilder`.** Hàm nhận thêm bản đồ id → ảnh. Token riêng một dòng (câu dẫn, ý tf, lời giải) thành một đoạn kiểu `ExamFigure` (căn giữa,
   thêm vào `ExamReferenceDoc`) chứa `![qimg:812](data:image/png;base64,…){width=<W>mm}` với `W = min(displayWidthMm, 120)`. Ảnh ở phương án: tối đa 40 mm và **ép bố cục 2 cột**
   khi có phương án chứa ảnh (hiện `OptionLayout.choose` chỉ đo chữ); không thêm dấu chấm sau token (`endWithPeriod`). Ảnh role `solution` chỉ ra ở phần lời giải của file đầy đủ, không ra ở `ExportContent.EXAM`.
   Alt `qimg:<id>` để đếm lại (bước 3). Token còn lại mà không có ảnh tương ứng: dừng ở bước dựng Markdown, cùng mã lỗi dưới.
3. **Đếm lại.** Trong `PandocRunner.doMarkdownToDocx`, trên docx **cuối** (sau `finish`, ở cả ba nhánh trả về: Equation, rơi về Equation, MathType) đếm trong `word/document.xml`: số `w:drawing`
   và số `wp:docPr` có `descr="qimg:<id>"` theo từng id; kỳ vọng = số lần token được chèn theo id (cùng ảnh dùng hai chỗ = 2). Lệch thì ném
   `DocumentExportException(500, "EXPORT_IMAGE_MISSING", …)`, ghi log id đề, số kỳ vọng, số đếm, danh sách id thiếu (không ghi chữ đề, URL hay `sig`); không trả file. Kỳ vọng tổng `w:drawing` chỉ đúng khi
   file mẫu không có drawing nào: test khóa số này ở file mẫu khi không có ảnh câu. `ApiExceptionHandler` đã trả `code` của `DocumentExportException`.
4. **Giới hạn dung lượng** (chốt 23/11 sau số đo container, D5 cách A): tổng `byte_size` của ảnh một lần xuất, **tính theo số lần chèn**
   (pandoc đọc từng lần chèn trong Markdown), ≤ **10 MB** (10 485 760 byte, `app.export.max-image-bytes`); vượt thì `422 EXPORT_IMAGES_TOO_LARGE`,
   lời nhắn "Tổng hình trong đề quá lớn để xuất Word (X MB, tối đa 10 MB). Hãy bỏ bớt câu có hình." (X làm tròn lên một chữ số thập phân). Kiểm
   bằng truy vấn không nạp cột `data`, trước khi nạp byte và trước khi chạy pandoc. **Heap pandoc giữ 256 MB** (`app.materials.pandoc-heap-mb`,
   `application.yaml:143`, đúng giá trị `PandocRunner` dùng). Số đo (bảng dưới): 20 và 24 MB **không chạy** dưới 256 MB (Windows: "Heap exhausted"),
   nên đề xuất 24 MB ban đầu bị bỏ; trần là cỡ lớn nhất trong 1/5/8/10/12 MB vừa chạy được vừa dùng ≤ 180 MiB heap (còn dư ≥ 30%): 10 MB dùng 169 MiB,
   12 MB dùng 187 MiB. Đề thật ≈ 13 hình ≈ 0,5 MB nên trần chỉ chặn đề rất nhiều hình lớn; cận trên lý thuyết 19 MB (40 câu × 2 hình × 237 KB) vượt trần.
   **Hướng phát triển (cách B, chưa làm):** đưa ảnh giữ chỗ 1×1 vào Markdown rồi thay `word/media/*` sau pandoc; khi đó bộ nhớ pandoc không còn phụ
   thuộc cỡ ảnh và trần có thể nâng lên theo bộ nhớ JVM.

**Chạy lại thử nghiệm 2b trong container Linux (chưa chạy; cần cho phép riêng khi tới lượt):**
- Dựng image **từ `back_end/Dockerfile` thật** (không dùng image thử `datn-backend:check`), chạy bằng uid 10001 và giới hạn bộ nhớ 1536 MB như `compose.prod.yaml:67`.
- Fixture **tự sinh**, không dùng ảnh bộ đề thương mại (quyết định 3/11 mục 5): PNG nhiễu ngẫu nhiên (không nén được) cỡ 20 / 237 / 500 KB; file có công thức dùng `DocxBuilder` và `spikes/mathtype-ole/KIEM_TRA_mo_rong.docx`.
- Đo: (1) data URI dưới `--sandbox` chạy được; (2) đối chứng: ảnh dạng file vẫn exit 0 mà thiếu ảnh (chứng minh cần đếm lại); (3) `descr` của `wp:docPr` có đúng `qimg:<id>` (**giả định, chưa xác minh**); (4) MathType `Kept-Omml = 0` với 12 công thức + 4 ảnh và 165 công thức + 33 ảnh, số `w:drawing` giữ nguyên;
  (5) RSS đỉnh và thời gian với ba cỡ: ≈ 1 MB (so với số Windows), 10 MB, 24 MB, dưới `+RTS -M256m` như ứng dụng (đọc `memory.peak` của cgroup hoặc `/usr/bin/time -v` nếu có);
  (6) mở file bằng Word thật: kiểm tay, container không làm được.
- Khung lệnh (minh họa, chưa kiểm cú pháp, chốt khi viết PR): `docker build -t datn-backend:o2 back_end`, rồi `docker run --rm --user 10001 --memory 1536m -v <thư mục fixture>:/work:ro --entrypoint /bin/sh datn-backend:o2 -c "pandoc --sandbox …"`.
- Kết quả ghi vào mô tả PR O2 và thay số ở bảng 2b.

**Kết quả chạy 23/11** (image tạm dựng từ `back_end/Dockerfile` của `origin/main` b742b12: pandoc 3.12.1, JRE 21; mỗi cỡ một container mới `--user 10001
--memory 1536m`; fixture PNG nhiễu ngẫu nhiên tự sinh, 500 KB mỗi ảnh (cận trên H4), mỗi ảnh một đoạn `ExamFigure` alt `qimg:<id>` như builder, mỗi câu một
công thức). "Heap pandoc" là `total memory in use` của `pandoc +RTS -M256m -s -RTS --sandbox` (phần `-M` giới hạn); "đường ứng dụng" là `PandocRunner` của
nhánh `feature/figure-export` (sandbox, đếm ảnh, MathType) gọi từ một lớp chạy thử trong container; "docker stats" là mức cao nhất lấy mẫu mỗi ~0,5 s của cả
container (JVM + pandoc). `memory.peak` của cgroup không đọc được trong container này.

| Tổng ảnh | Số ảnh | Heap pandoc docx / json | Thời gian pandoc docx / json | Đường ứng dụng Equation / MathType | docker stats | `w:drawing` = `descr qimg` | Kept-Omml |
|---|---|---|---|---|---|---|---|
| 1 MB | 3 | 29 / 27 MiB | 0,9 / 0,4 s | 3,7 / 5,7 s | 150 MiB | 3 = 3 | 0 |
| 5 MB | 11 | 95 / 70 MiB | 2,5 / 1,5 s | 5,1 / 9,2 s | 231 MiB | 11 = 11 | 0 |
| 8 MB | 17 | 139 / 105 MiB | 3,8 / 2,3 s | 7,2 / 16,1 s | 357 MiB | 17 = 17 | 0 |
| **10 MB** | 21 | **169** / 131 MiB | 6,7 / 4,1 s | 10,6 / 19,2 s | 405 MiB | 21 = 21 | 0 |
| 12 MB | 25 | **187** / 155 MiB (> 180) | 7,7 / 4,9 s | 11,7 / 21,3 s | 427 MiB | 25 = 25 | 0 |
| 20 MB, 24 MB (Windows) | 40, 49 | **Heap exhausted** (exit 251, không có file) | – | – | – | – | – |

Các mục còn lại: (1) data URI dưới `--sandbox` chạy ở mọi cỡ; (2) đối chứng ảnh dạng file: `PandocResourceNotFound "fig.png"`, **exit 0**, docx không có ảnh,
`PandocRunner` của nhánh báo `EXPORT_IMAGE_MISSING` ("kỳ vọng 1 ảnh, đếm được 0 w:drawing") ở cả Equation và MathType; (3) `descr` của `wp:docPr` **đúng**
`qimg:<id>` (Linux và Windows, pandoc 3.12.1); (4) MathType: 12 công thức + 4 ảnh → 12 đối tượng, Kept-Omml = 0, 4 `w:drawing`; 165 công thức + 33 ảnh →
165, Kept-Omml = 0, 33 `w:drawing` (fixture Markdown tự sinh, không dùng `KIEM_TRA_mo_rong.docx`); (6) mở bằng Word thật: chưa kiểm.

**Test chính O2:** docx có đủ ảnh theo từng id (ảnh dùng hai chỗ đếm 2); thiếu ảnh trong DB hoặc token không có ảnh → `EXPORT_IMAGE_MISSING`, không trả file; ảnh phương án 2×2, không dấu chấm sau token; token đi qua `cleanText`
không đổi; `images` không rơi ở `withoutLiteralNewlines`; MathType `Kept-Omml = 0` với file có ảnh; vượt giới hạn dung lượng → 422; xuất `EXAM` không có ảnh `solution`; MockMvc `/api/exams/{id}/export` trả 500 + `code`.

### 4.3 Bỏ điều kiện loại ở `ExamSlotRepository.java:104`

Đây là **một dòng SQL** nhưng `findBankCandidates` dùng chung cho mọi nơi chọn câu ngân hàng:

| Nơi | Thay đổi khi bỏ điều kiện | Việc cần làm |
|---|---|---|
| Tạo đề theo ma trận và theo bài (`ExamBuildService.java:209-210`) | Ô có thể nhận câu có hình; câu mới nhập có `uses = 0` nên **xếp lên đầu** (xếp hạng mềm W1-3), đề có thể dồn câu nhập có hình | Test: đề có câu hình trả `images` đúng; không đổi gì cho câu không hình |
| Đổi bằng ngân hàng (`ExamActionService.java:237-245`) | Câu thay có thể có hình; câu cũ có hình vào `exam_slot_action` và vào `out_of_reach` như thường | Test Đổi qua lại câu có hình |
| Đổi bằng **sinh mới** có lý do quá dễ / quá khó (`ExamActionService.java:247-249, 292-300`) | `currentQuestionText` lấy chữ câu ngân hàng **kèm token** rồi đưa vào prompt Gemini; mô hình không thấy hình nên lời nhận xét "quá dễ" vô nghĩa | Đề xuất: câu cũ có hình thì **không** đưa chữ câu cũ vào prompt, vẫn giữ lý do (D4); test khóa |
| Sửa (`ExamActionService.java:373-377`) | Chỗ ngân hàng vốn 409 `SLOT_FROM_BANK`; không đổi | Mục 4.4 |
| Giao bài | Học sinh chưa có đường ảnh | A1 (guard) trước, gỡ guard ở A2 sau khi S1+S2 xong |
| Cờ "giống câu khác trong đề" | Hai câu cùng chữ khác hình bị báo giống (`ExamSlotSiblings.java:124-130`, thiếu `imageKey`); chỉ là cờ thông tin | Làm nốt H7 phía đề: nạp `imageKey` cho câu ngân hàng |
| Thống kê câu | `item_stat` (p, D) không dùng chữ đề; tab thống kê chỉ hiện Phần + số câu (`ItemStatsTab.tsx`) → không đổi. Thống kê sử dụng ngân hàng (`usageCount`, `lastUsedAt`) tính theo `bank_item_id` → không đổi. Thống kê đầu ra LLM đã lọc `origin = 'llm'` (quyết định 3/11 mục 4) nên câu nhập có hình không lẫn vào | Chỉ thêm test khóa; không sửa mã |
| Quyền xem ảnh (F1) | Giáo viên B xem đề dùng câu chung của A; A gỡ câu thì B mất ảnh | Đề xuất sửa `hasAccessPermission`: thêm điều kiện "người xem là chủ (`exam.created_by`) của một đề chưa xóa có chỗ dùng câu ngân hàng có `source_question_id` = câu nguồn của ảnh", **không** đòi `removed_at IS NULL` cho trường hợp đó (D2) |
| `QcVersionCard` | Không hiện chữ đề hay ảnh (xem 4.0); chữ thay thế của `MathText` đã bảo hiểm | **Không sửa**; thêm 1 test khóa chữ thay thế khi thiếu `images` |

Cũng ghi nhận (không sửa trong đợt này): chưa kiểm `ExamSpecBuilder` có in chữ câu hỏi hay không (tìm thấy dùng `cellId`, số câu); nếu có thì cần cùng xử lý token như O2.
*Đã kiểm 23/11 (O2):* `ExamSpecBuilder` chỉ dùng `cellId` và vị trí câu, không in chữ câu hỏi; bản đặc tả không nạp ảnh (test `ExamFigureExportApiTest`).

### 4.4 Sửa câu có hình: vẫn ngoài phạm vi (xác nhận lại H5)

Giữ nguyên: lý do (i) chỗ ngân hàng không sửa được trong đề, 409 `SLOT_FROM_BANK` (`ExamActionService.java:373-377`: "sửa câu ngân hàng chưa hỗ trợ"); (ii) sửa câu ngân hàng còn chờ ở Q-4c
(quyết định 16/11 chỉ chốt "Gỡ"); (iii) sửa câu LLM chứa `⟦img:` trả 400, test `QuestionEditImageGuardTest`. Giáo viên gặp hình sai thì dùng Đổi. Không mở lại vì mở nó kéo theo
"giữ hình / bỏ hình", chụp lại hình khi sửa, và `has_figure_unchecked` (H8), tức một thiết kế riêng. **Không chạm Q-4c.**

### 4.5 Chia PR, giờ, test chính

| PR | Nội dung | Giờ | Sau | Test chính |
|---|---|---|---|---|
| **F1** | `hasAccessPermission` theo 16/11 (D2) | 2 | – | Người xem là chủ đề có chỗ dùng câu đã gỡ → 200; người không có chỗ → 404; đề đã xóa → 404; `u` bị khóa → 404 |
| **A1** | `AssignmentService.create` trả 409 `ASSIGN_HAS_FIGURE` nếu có chỗ ngân hàng nào của đề có ảnh; nút Giao bài mờ kèm lý do | 2 | – | Đề không hình giao được như cũ; đề có một câu hình → 409, không ghi gì |
| **O2** | Mục 4.2 | 12 (6 mã + 3 test + 3 chạy container) | – | Mục 4.2 |
| **S1** | Học sinh phía BE: endpoint, ký theo lượt, điều kiện hiển thị, DTO, hạn mức, `SecurityConfig`, tài liệu API | 10 | duyệt D1, D3 | Chữ ký sai/hết hạn/đổi `id`,`a`,`exp` → 404; lượt bị xóa → 404; **ảnh `solution` trong lúc làm bài → 404 và không có trong `images`**; sau ân hạn + `showSolutions` → 200; `showSolutions=false` → 404; câu đã gỡ vẫn tải được; vượt hạn mức → 429; MockMvc không JWT |
| **S2** | Học sinh phía FE: truyền `images` cho `MathText` ở 3 màn; ảnh lỗi hiện chữ thay thế | 3 | S1 | Token → ảnh; URL hết hạn → chữ thay thế rồi tải lại bằng P3 |
| **X1** | Bỏ điều kiện dòng 104; không gửi chữ câu cũ có hình vào prompt Đổi; `imageKey` cho `ExamSlotSiblings` (và cờ `similar_in_exam`, xem 4.0). **Đã làm 24/11**, nhánh `feature/figure-enable` | 4 | F1, O2, A1 | Tạo đề, Đổi (ngân hàng và sinh mới) với câu hình; cờ giống không báo khi khác hình; test khóa thống kê (`ExamFigureBankTest`, `ReplaceReasonWithoutOldQuestionTest`) |
| **A2** | Gỡ `ASSIGN_HAS_FIGURE`. **Đã làm 24/11**, cùng nhánh: gỡ cả `figureGuard.ts` và dòng nhắc "chưa dùng để tạo đề" ở màn nhập Word | 1 | S1, S2, X1 | Đề có hình giao được; học sinh làm được (`AssignmentFigureFlowTest`, `AssignmentTeacherApiTest`) |
| (nhỏ) | Test khóa `QcVersionCard` | 0,5 | – | Thiếu `images` → chữ thay thế |

Tổng ≈ 34,5 giờ. Thứ tự merge: F1 → (O2 ∥ A1 ∥ S1) → X1 → S2 → A2. Giữa X1 và A2 giáo viên đã tạo, xem và xuất Word được đề có hình nhưng chưa giao được.
Giờ O2 lớn hơn H9 (6 giờ) vì H9 chỉ tính builder và đếm; thêm bố cục phương án, lời giải, giới hạn dung lượng và chạy container.

### 4.6 Việc chờ chủ dự án quyết (chưa tự chọn)

| ID | Câu hỏi | Đề xuất | Đã chốt 23/11 |
|---|---|---|---|
| D1 | Học sinh xem ảnh bằng cách nào (A/B/C/D, mục 4.1) | A; C là dự phòng | **A** (URL ký theo lượt làm); C để dự phòng |
| D2 | Người có chỗ đề dùng câu ngân hàng đã gỡ có được xem ảnh (H3(b), 22/11)? | Có, theo quyết định 16/11 | **Có** |
| D3 | Thêm cạnh `assignment ──► questionimage` hay đi qua `BankService` (**Q-8, M-35**) | Đi qua `BankService`, không thêm cạnh mới | **Đi qua `BankService`**, không thêm phụ thuộc module mới; Q-8 giữ nguyên |
| D4 | Đổi bằng sinh mới từ chỗ có hình: bỏ chữ câu cũ khỏi prompt | Bỏ, giữ lý do | **Bỏ chữ câu cũ, giữ lý do** |
| D5 | Trần dung lượng ảnh khi xuất Word | 24 MB, chốt sau số đo container | ~~24 MB~~ → **10 MB** (cách A: hạ trần, giữ heap pandoc 256 MB, không làm ảnh giữ chỗ). Đo trong container: 10 MB dùng 169 MiB heap pandoc, 12 MB dùng 187 MiB (> 180 MiB, dư < 30%), 20–24 MB hết heap (mục 4.2). Cách B (ảnh giữ chỗ, thay `word/media` sau pandoc) là hướng phát triển |
| D6 | Cache ảnh học sinh: `private, max-age` hay `no-store` | `private` | **`private`** |
| D7 | Có thêm dòng xác nhận quyền dùng hình ở hộp Giao bài không | Cho chủ dự án quyết (chính sách) | **Không thêm** |

**Chưa xác minh** (ghi rõ để không coi là sự thật): `MatrixPromptDraftV0.replaceReasonBlock` xử lý được `oldQuestion = null` cho nhánh D4.
*Đã xác minh 23/11 (O2, mục 4.2):* `descr` của `wp:docPr` lấy từ alt của pandoc (đúng); heap pandoc 256 MB **không** đủ cho ảnh 19–24 MB (trần hạ còn
10 MB, D5); `ExamSpecBuilder` không in chữ câu hỏi (đúng).
*Đã xác minh 24/11 (X1):* `replaceReasonBlock` với `oldQuestion = null` (`PromptData.neutralize(null)` trả chuỗi rỗng) cho khối
lý do một dòng, không có đoạn "Câu bị đổi" hay đáp số cũ, cả ba dạng câu; không đổi văn bản hay phiên bản prompt. Khóa bằng
`ReplaceReasonWithoutOldQuestionTest` và `ExamFigureBankTest`. Với câu sa/tf, khối vẫn nói "khác câu bị đổi" dù không kèm câu
cũ (văn bản có sẵn, không sửa).

## Quyết định đã chốt (An, 11/11/2026)

1. Tiếp tục bỏ câu có ảnh công thức WMF (16/161 câu) và hình vẽ OLE (2 câu); lý do hiển thị "công thức dạng ảnh, chưa nhập được".
2. Dùng LibreOffice: chỉ khi nhập, một tiến trình mỗi lô, trong permit `PandocLimiter`, có timeout; câu đổi lỗi bỏ với
   `image_convert_failed`.
3. Migration câu có hình là V27. *(Ghi chú 15/11/2026: thực tế là `V30__question_image.sql`, vì V27 đã thuộc `exam_challenge`; số V27 trong tài liệu này là số lúc thiết kế.
   Ghi chú 17/11/2026: đổi thành `V34__question_image.sql` khi merge `main`, vì main đã dùng V30–V32 và V33 thuộc PR #93
   (`item_stat`).)*
4. Câu ngân hàng có hình chỉ hiện nhãn "Có hình", không vào `reviewReasons`.
5. Nhập cả hình ở lời giải.
