# Đo lại trước / sau khi port nhóm E, B, D vào backend bankimport (08/11/2026)

Bổ sung cho `2026-11-08-port-b-d-e-so-lieu.md`. Khác biệt chính: cả hai cột **trước** và **sau** ở đây đều do **module backend** tạo ra,
chạy bằng cùng một chương trình đo trên cùng 4 file Word (`data/private/bo-cau-hoi`). Chỉ ghi id câu, cờ, số đếm và hash; không có
nội dung đề (quyết định 3/11 mục 5).

- **Trước** = mã `back_end/.../bankimport` ở commit `75dba12` (chưa có E, B, D, Clock).
- **Sau** = mã ở `df13cfc` (HEAD lúc đo).
- Chương trình đo: `docs/dev-log/port-bde-measure/Measure.java`. Không gọi mạng, DB, LLM. Chạy hai lần liên tiếp cho đầu ra giống hệt
  từng byte (tái lập; chương trình không in thời gian).

## 1. Số liệu theo file

| File | Tổng | mcq/tf/sa | Vào xem trước (trước → sau) | Bỏ `has_image` (trước → sau) | Bỏ `has_table` (trước → sau) |
|---|---|---|---|---|---|
| k8c3b1 | 40 | 20/10/10 | 26 → 26 | 14 → 14 | 0 → 0 |
| k8c3b2 | 40 | 20/10/10 | 20 → 20 | 20 → 20 | 0 → 0 |
| k8c4b1 | 40 | 20/10/10 | **19 → 13** | 11 → 15 | 10 → 12 |
| k8c5b2 | 41 | 21/10/10 | 22 → 22 | 19 → 19 | 0 → 0 |
| **Tổng** | 161 | 81/40/40 | **87 → 81** | 64 → 68 | 10 → 12 |

Số câu có cờ chặn trong xem trước: k8c4b1 1 → 0; k8c5b2 1 → 1 (có từ trước, không liên quan); hai file còn lại 0.

## 2. Đếm cờ mới (sau)

| Cờ | k8c3b1 | k8c3b2 | k8c4b1 | k8c5b2 | Trước |
|---|---|---|---|---|---|
| `stem_extra_text` (E) | 0 | 0 | 2 | 0 | 0 |
| `figure_from_previous_question` (B) | 1 | 0 | 0 | 1 | 0 |
| `mentions_figure_no_image` (B) | 0 | 1 | 0 | 0 | 0 |
| `shared_context` (D) | 0 | 0 | 8 | 0 | 0 |

Cờ có sẵn (`hint_after_statements`, `formula_image`, `ole_drawing`, `plain_math_vertalign`, `empty_formula`) không đổi số đếm.

## 3. Câu đổi nội dung (so `hash` từng câu): đúng 16 câu, các câu khác giữ nguyên hash

| Nhóm | Câu | Thay đổi đo được |
|---|---|---|
| E | k8c4b1-tf-001 | bỏ chữ giữ chỗ; cờ `stem_extra_text` |
| E | k8c4b1-tf-007 | gợi ý chuyển sang `solution`; `solution` 0 → 333 ký tự |
| E | k8c4b1-tf-010 | bỏ câu phụ cuối đề; cờ `stem_extra_text` |
| B | k8c3b1-mcq-011 / mcq-012 | hình chuyển từ 011 sang 012; 012 có cờ `figure_from_previous_question` |
| B | k8c5b2-mcq-012 / mcq-013 | như trên |
| B | k8c3b2-sa-010 | cờ `mentions_figure_no_image` |
| D | k8c4b1-sa-001..004 | thêm đoạn giới thiệu + 1 ảnh, cờ `shared_context` |
| D | k8c4b1-sa-005..008 | thêm đoạn giới thiệu + 1 bảng, cờ `shared_context` |

Ca đối chứng: `k8c4b1-sa-009` (có bảng riêng trong thân câu) và `k8c4b1-sa-010` có `hash` trước = sau (`10b086258719`, `83b0aa175299`), không nhận ngữ cảnh.

## 4. Điều đáng chú ý

1. **Số câu vào xem trước k8c4b1 giảm 19 → 13, và đó là đúng.** Trước khi port, sa-001..003 (không có hình/bảng ngữ cảnh) lọt vào xem trước
   dù thiếu dữ kiện của đề; nay có ảnh hoặc bảng nên bị bỏ theo lý do `has_image`/`has_table`. Tổng toàn bộ 87 → 81. Nhóm D vì vậy *làm
   giảm* số câu nhập được ở bộ này, nhưng loại câu thiếu đề. Mở nhập câu có ảnh/bảng (ngoài phạm vi, phương án 2) mới làm các câu này vào lại.
2. **k8c5b2-sa-007 không đổi** (1 ảnh vai đề, cả trước và sau). Backend trước khi port đã coi ảnh giữa "Trả lời" và "Đáp số" là ảnh
   của câu (`DocParser` ở `75dba12`, dòng 711), nên mục 3.2 của kế hoạch nhóm B không có tác dụng trên câu này ở backend. Log
   `2026-11-08-port-b-d-e-so-lieu.md` mục 3.2 ghi "trước: bị bỏ qua" là mô tả hành vi của công cụ `tools/bank-import`, không phải backend.
3. **Phân rã lý do bỏ theo file trong log cũ khác số đo ở đây.** Log cũ: k8c3b2 `has_image` 18 + `has_table` 2; k8c4b1 `has_image` 17 +
   `has_table` 10. Đo được: k8c3b2 20 + 0; k8c4b1 15 + 12. Tổng toàn bộ trùng (68 và 12), nên chỉ phần phân rã theo file trong log cũ
   cần sửa.
4. Số liệu tổng "81 câu vào xem trước" của log cũ khớp số đo (81).

## 5. Chạy lại

Chương trình đo cần classpath của backend. Cách lấy mà không dùng lệnh Maven ngoài `run-tests.ps1`: tạo tạm một test
JUnit ghi `System.getProperty("surefire.test.class.path")` ra file, chạy bằng `run-tests.ps1`, rồi xóa test (đã làm và đã xóa trong lượt này).

```
javac -encoding UTF-8 -cp "<classpath>" -d cls-new docs/dev-log/port-bde-measure/Measure.java
git archive 75dba12 back_end/src/main/java/com/datn/mcq/bankimport | tar -x -C old-src
javac -encoding UTF-8 -cp "<classpath>" -d cls-old <các file parse, docx, model, dto, service/ImportPreviewBuilder, ImportFlags của old-src> docs/dev-log/port-bde-measure/Measure.java
"$JAVA_HOME/bin/java" -cp "cls-old;<classpath>" Measure data/private/bo-cau-hoi     # trước
"$JAVA_HOME/bin/java" -cp "cls-new;<classpath>" Measure data/private/bo-cau-hoi     # sau
```

`cls-old` đứng trước trên classpath nên che các lớp `bankimport` mới trong `target/classes`.
