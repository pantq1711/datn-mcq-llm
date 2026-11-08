# Số liệu đối chiếu trước / sau khi port các nhóm cải tiến B, D, E vào backend bankimport (08/11/2026)

## 1. Bối cảnh & Mục tiêu

- **Nguồn gốc:** Các cải tiến trích xuất nhóm B (hình nổi), D (ngữ cảnh dùng chung Phần III) và E (dọn dẹp đề Đúng/Sai Phần II) đã được kiểm thử và commit trên `tools/bank-import` (29/10/2026). Khi đưa vào `back_end/src/main/java/com/datn/mcq/bankimport`, patch tự động bị xung đột (8/14 hunk) do cấu trúc package và các ràng buộc an toàn (an toàn zip, giới hạn bộ nhớ, bảo vệ log rò rỉ thông tin câu hỏi) của backend.
- **Thứ tự port:** Theo đúng thứ tự **E → B → D** và bổ sung tái lập thời gian qua `Clock`:
  1. **Nhóm E (Commit `bc6a326`):** Loại bỏ placeholder `"Câu hỏi..."` và các đoạn câu phụ tự luận thừa (`a/`, `b/`) ở cuối stem của câu Đúng/Sai; chuyển hàng `"Gợi ý"` 1 ô sau các ý vào đầu `solution` (giữ cờ `hint_after_statements`); thêm cờ thông tin `stem_extra_text`. Test khóa: `TfStemCleanupTest` (5 test).
  2. **Nhóm B (Commit `3f32d04`):** Đánh dấu hình nổi (`wp:anchor`) trong `DocxReader`. Hình nổi neo trong vùng phương án của câu $k$ Phần I được chuyển sang câu $k+1$ khi đề câu $k+1$ nhắc hình và chưa có ảnh; giữ ảnh ở dòng `"Trả lời:"` Phần III làm ảnh stem; thêm cờ `figure_from_previous_question` và `mentions_figure_no_image`. Test khóa: `FloatingFigureTest` (7 test).
  3. **Nhóm D (Commit `541551e`):** Trích xuất ngữ cảnh dùng chung (đoạn giới thiệu, ảnh, bảng) đứng trước câu 1 Phần III hoặc đứng sau dòng `"Đáp số"` của câu có bảng; nhân bản ngữ cảnh vào các câu tiếp theo cho đến khi gặp ngữ cảnh mới hoặc gặp câu có bảng riêng trong thân câu (câu `sa-009`); thêm cờ `shared_context`. Test khóa: `SaSharedContextTest` (6 test).
  4. **Clock tái lập được (Commit `3dab3ea`):** Inject `Clock` vào `BankImportService` cho các mốc `createdAt`, `expiresAt`, `confirmedAt`, `purgeExpired` và `checkRate`. Test khóa: `BankImportServiceClockTest`.

## 2. Số liệu đo đạc trên 4 file Word nguồn

Đo thực tế trên 4 file Word tài liệu bộ đề tại `data/private/bo-cau-hoi/`:
- `k8c3b1`: Khối 8 - Chương 3 - Bài 1 (Định lý Pythagore)
- `k8c3b2`: Khối 8 - Chương 3 - Bài 2 (Tứ giác)
- `k8c4b1`: Khối 8 - Chương 4 - Bài 1 (Thu thập và phân loại dữ liệu)
- `k8c5b2`: Khối 8 - Chương 5 - Bài 2 (Tọa độ của một điểm và đồ thị của hàm số)

### 2.1. Thống kê tổng quan số lượng câu

| Mã tài liệu | Tổng số câu | Trắc nghiệm (MCQ) | Đúng/Sai (TF) | Trả lời ngắn (SA) | Xem trước (Preview) | Bỏ qua (Skipped) |
|---|---|---|---|---|---|---|
| `k8c3b1` | 40 | 20 | 10 | 10 | 26 | 14 (`has_image`) |
| `k8c3b2` | 40 | 20 | 10 | 10 | 20 | 20 (`has_image`: 18, `has_table`: 2) |
| `k8c4b1` | 40 | 20 | 10 | 10 | 13 | 27 (`has_image`: 17, `has_table`: 10) |
| `k8c5b2` | 41 | 21 | 10 | 10 | 22 | 19 (`has_image`) |
| **Tổng cộng** | **161** | **81** | **40** | **40** | **81** | **80** |

*Ghi chú:* Backend `ImportPreviewBuilder` áp dụng chính sách an toàn, chủ động bỏ qua câu có ảnh (`SKIP_HAS_IMAGE`: 68 câu) hoặc có bảng (`SKIP_HAS_TABLE`: 12 câu) để ngăn câu thiếu hình ảnh/bảng lọt vào ngân hàng khi hệ thống chưa hỗ trợ lưu trữ media cho câu hỏi nhập. 81 câu thuần văn bản và công thức LaTeX hợp lệ được đưa vào bản xem trước với 0 cờ chặn.

### 2.2. Thống kê cờ trích xuất (Extraction Flags)

| Tên cờ | Số lượng | Tính chất cờ | Nhóm liên quan | Ý nghĩa |
|---|---|---|---|---|
| `stem_extra_text` | **2** | Thông tin | **E** | Câu tf được dọn dẹp chữ thừa trong stem (`k8c4b1-tf-001`, `k8c4b1-tf-010`) |
| `hint_after_statements` | **1** | Thông tin | **E** | Hàng gợi ý sau 4 ý được chuyển sang đầu solution (`k8c4b1-tf-007`) |
| `figure_from_previous_question` | **2** | Thông tin | **B** | Hình nổi neo ở phương án câu trước được chuyển sang câu sau (`k8c3b1-mcq-012`, `k8c5b2-mcq-013`) |
| `mentions_figure_no_image` | **1** | Thông tin | **B** | Đề nhắc hình nhưng câu không có ảnh stem (`k8c3b2-sa-010`, hình ở solution) |
| `shared_context` | **8** | Thông tin | **D** | Câu nhận ngữ cảnh dùng chung Phần III (`k8c4b1-sa-001` .. `sa-008`) |
| `formula_image` | 16 | Thông tin | F | Câu có công thức MathType dưới dạng ảnh |
| `ole_drawing` | 2 | Thông tin | - | Đối tượng vẽ OLE |
| `plain_math_vertalign` | 1 | Thông tin | - | Căn dọc toán học dạng văn bản |
| `empty_formula` | 1 | Thông tin | - | Công thức rỗng trong đề gốc |

*An toàn cờ:* Không có cờ mới nào chứa tiền tố `answer_`, `structure_` hoặc thuộc tập `BLOCKING`, đảm bảo giáo viên hoàn toàn không bị chặn nhầm khi phê duyệt câu vào ngân hàng cá nhân.

---

## 3. Đối chiếu chi tiết từng câu trong các nhóm cải tiến

### 3.1. Nhóm E: Dọn dẹp đề dẫn Đúng/Sai Phần II (`k8c4b1`)

| Mã câu | Trước cải tiến | Sau cải tiến | Kết quả đo thực tế |
|---|---|---|---|
| `k8c4b1-tf-001` | Đề dẫn bị dính chữ giữ chỗ `"Câu hỏi..."` ngay sau nhãn mức | Bỏ chữ `"Câu hỏi..."`, giữ nguyên nội dung bài toán | Cờ `[stem_extra_text]`, độ dài stem giảm còn 126 ký tự, sạch hoàn toàn placeholder |
| `k8c4b1-tf-007` | Hàng `"Gợi ý: ..."` 1 ô sau 4 ý bị dính vào cuối stem | Chuyển hàng `"Gợi ý"` vào đầu `solution` | Cờ `[hint_after_statements]`, `solution_not_null=true`, stem (165 ký tự) không còn dính gợi ý |
| `k8c4b1-tf-010` | Đề dẫn dính các câu phụ tự luận cũ `"a/ ... b/ ..."` ở cuối đoạn | Cắt bỏ các câu phụ thừa ở đuôi stem | Cờ `[stem_extra_text]`, `solution_not_null=true`, stem còn 192 ký tự |

### 3.2. Nhóm B: Hình vẽ nổi và ảnh dòng trả lời (`k8c3b1`, `k8c5b2`, `k8c3b2`)

| Mã câu | Trước cải tiến | Sau cải tiến | Kết quả đo thực tế |
|---|---|---|---|
| `k8c3b1-mcq-012` | Hình vẽ bị gán nhầm vào phương án của câu `mcq-011` do neo `wp:anchor`; câu 12 có `images: []` | Hình nổi chuyển sang đề câu 12 (vì câu 12 nhắc "hình vẽ bên") | Cờ `[figure_from_previous_question]`, `images_count=1`, `has_image=true` |
| `k8c5b2-mcq-013` | Tương tự, hình vẽ bị gắn vào phương án câu 12; câu 13 thiếu ảnh | Hình nổi chuyển từ phương án câu 12 sang đề câu 13 | Cờ `[figure_from_previous_question]`, `images_count=1`, `has_image=true` |
| `k8c5b2-sa-007` | Ảnh nằm ở đoạn `"Trả lời: [ảnh]"` bị bỏ qua | Ảnh ở dòng `"Trả lời:"` được nhận làm ảnh của stem | `images_count=1`, `has_image=true` (không bị mất ảnh) |
| `k8c3b2-sa-010` | Đề nhắc "hình bên" nhưng ảnh nằm ở phần lời giải, không có cờ cảnh báo | Gắn cờ cảnh báo thông tin | Cờ `[mentions_figure_no_image]`, `has_image=true` |

### 3.3. Nhóm D: Ngữ cảnh dùng chung Phần III (`k8c4b1`)

| Nhóm câu | Ngữ cảnh dùng chung | Trước cải tiến | Sau cải tiến |
|---|---|---|---|
| `k8c4b1-sa-001` .. `sa-004` | Khối mở đầu trước câu 1 (đoạn giới thiệu biểu đồ + hình vẽ biểu đồ) | Khối mở đầu bị bỏ qua; câu 1-3 mất toàn bộ dữ kiện đề bài | Nhân bản khối mở đầu vào đầu stem của cả 4 câu; cờ `[shared_context]`, `images_count=1`, stem từ 161–183 ký tự |
| `k8c4b1-sa-005` .. `sa-006` | Khối sau "Đáp số" câu 4: đoạn dẫn `"Bảng thống kê số lượng..."` + bảng `"Loại trái cây"` | Bị gắn nhầm vào làm lời giải / bảng của câu 4 | Trở thành ngữ cảnh dùng chung, nhân bản vào đầu stem và bảng của câu 5 và 6; cờ `[shared_context]`, `tables_count=1` |
| `k8c4b1-sa-007` .. `sa-008` | Khối sau "Đáp số" câu 6: đoạn dẫn `"Bảng thống kê sản lượng..."` + bảng `"Sản lượng thủy sản"` | Bị gắn nhầm vào làm lời giải / bảng của câu 6 | Nhân bản vào đầu stem và bảng của câu 7 và 8; cờ `[shared_context]`, `tables_count=1` |
| `k8c4b1-sa-009` | Câu 9 có bảng riêng trong thân câu | — | **Mốc chặn dừng:** Không nhận ngữ cảnh dùng chung của câu 7-8 (`flags=[]`), giữ nguyên bảng riêng (`tables_count=1`) |

---

## 4. Xác minh kiểm thử tự động (Regression Tests)

Chạy bộ test kiểm thử đơn vị của `bankimport` bằng lệnh quy chuẩn:
`powershell -ExecutionPolicy Bypass -File back_end/run-tests.ps1` với 5 test suite:
- `BankImportServiceClockTest`: 1/1 PASS
- `BankImportParseTest`: 15/15 PASS
- `TfStemCleanupTest`: 5/5 PASS
- `FloatingFigureTest`: 7/7 PASS
- `SaSharedContextTest`: 6/6 PASS

**Tổng số test pass:** 34/34 (100% green, thời gian chạy: ~8.8 giây, không phụ thuộc cơ sở dữ liệu hay mạng bên ngoài).
