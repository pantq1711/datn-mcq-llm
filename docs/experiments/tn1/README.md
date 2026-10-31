# TN1 – Solver bắt câu trắc nghiệm có nhiều phương án đúng

Thí nghiệm ngày 31/10/2026. Lỗi và cách sửa: `docs/dev-log/loi-va-cach-xu-ly.md`, mục L-54.

## Câu hỏi thí nghiệm

Solver (model thứ hai tự giải câu mcq, bước `answer_check` của QC) có bắt được câu có từ 2 phương án đúng trở lên không, và
có báo nhầm câu tốt không? So 3 phiên bản prompt Solver mcq:

| Phiên bản | Ý chính |
|---|---|
| `solver-v2-delimited` | Solver cũ: giải rồi ghi một kết luận `judge_answer` (A–D, `multiple`, `none`) |
| `solver-v3-option-check` | Xét TỪNG phương án A–D (`options_verdict`, `correct_count`); backend tự đếm số phương án đúng |
| `solver-v4-literal-reading` | v3 + quy tắc "phương án ĐÚNG nếu học sinh bảo vệ được theo đúng chữ của đề" và 2 ví dụ (mặc định hiện tại) |

## Thiết lập

- Dữ liệu: 29 câu mcq trong DB `datn_mcq_e2e` (danh sách ở `ids.txt`).
  - 9 câu "lỗi": 7, 8, 37, 39, 52, 53 (phân tích nhân tử, nhiều phương án nhân lại bằng đa thức gốc); 15, 61 (tính chất
    hình thang cân, đề không có "luôn"); 25 (bản giáo viên sửa).
  - 20 câu "tốt": 24, 4, 6, 5, 32, 27, 28, 30, 31, 33, 35, 38, 41, 48, 50, 62, 29, 34, 49, 60.
- Model Solver: `gemini-3.5-flash-lite`, `temperature = 0.0` (đã kiểm trong code: `AnswerSolverChecker` gửi `0.0` cho
  cả mcq, tf, sa; không đặt `seed`, `topP`, `topK`).
- Mỗi phiên bản chạy 2 lần, mỗi lần 29 lượt gọi.
- Commit: v2 và v3 đo trên `366b4b0`; v4 đo trên `9c514eb`. Prompt v2 và v3 giống nhau từng chữ ở hai commit này
  (`SolverPromptFrozenTextTest` ghim SHA-256).
- Công cụ: `back_end/src/main/java/com/datn/mcq/generation/tool/SolverProbeRunner.java`. Gọi Solver qua đúng `LlmClient`
  và bộ điều tiết Gemini của app, KHÔNG ghi DB.

## Cách chạy lại

Đứng ở `back_end`, tắt các backend khác, DB trỏ tới `datn_mcq_e2e` (qua `.env` hoặc biến `SPRING_DATASOURCE_URL`):

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/datn_mcq_e2e'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=0 --app.worker.enabled=false --app.solver-probe.ids-file=<đường dẫn>/ids.txt --app.solver-probe.prompt-version=<phiên bản> --app.solver-probe.out-dir=<thư mục ngoài repo>"
```

- `<phiên bản>`: `solver-v2-delimited`, `solver-v3-option-check` hoặc `solver-v4-literal-reading`.
- Thư mục ghi phải nằm ngoài repo (công cụ tự chặn). Không dùng `--spring.main.web-application-type=none`: app không khởi
  động được.
- Mỗi lần khoảng 2–3 phút (giãn cách 4,2 giây giữa các lượt gọi).

## Các file

| File | Phiên bản | Lần |
|---|---|---|
| `solver-probe_solver-v2-delimited_20261031-193707.csv` | v2 | 1 |
| `solver-probe_solver-v2-delimited_20261031-194024.csv` | v2 | 2 |
| `solver-probe_solver-v3-option-check_20261031-194248.csv` | v3 | 1 |
| `solver-probe_solver-v3-option-check_20261031-194514.csv` | v3 | 2 |
| `solver-probe_solver-v4-literal-reading_20261031-203738.csv` | v4 | 1 |
| `solver-probe_solver-v4-literal-reading_20261031-204020.csv` | v4 | 2 |

CSV có BOM UTF-8 (để Excel đọc đúng tiếng Việt), mỗi dòng một câu:

| Cột | Ý nghĩa |
|---|---|
| `question_id` | Id câu (`generated_question.id`) |
| `prompt_version` | Phiên bản prompt Solver |
| `question_type` | Dạng câu (ở đây luôn `mcq`) |
| `question_answer` | Đáp án lưu trong câu |
| `solver_answer` | Kết luận Solver: A–D, `multiple`, `none` |
| `verdict` | `consistent` (khớp đáp án), `inconsistent` (lệch, kể cả `multiple`/`none`), `error` (lỗi gọi) |
| `multiple_correct_flag` | `1` khi Solver cho từ 2 phương án đúng |
| `solver_correct_count` | Số phương án Solver cho là đúng (v2 chỉ biết `0`, `1`, `2+`) |
| `options_verdict` | v3, v4: kết luận từng phương án, ví dụ `A:1\|B:0\|C:1\|D:0` (1 là đúng) |
| `token_count`, `latency_ms` | Token và độ trễ của lượt gọi |
| `solver_model` | Model Solver |
| `error` | Lý do lỗi (chỉ với `verdict = error`) |

Cách tính: một câu "lỗi" là **bắt được** khi `verdict = inconsistent`; một câu "tốt" là **báo nhầm** khi
`verdict = inconsistent`. Dòng `error` không tính vào mẫu số.

## Kết quả

| Phiên bản | Bắt được trên 9 câu lỗi | Báo nhầm trên 20 câu tốt | Token TB | Độ trễ TB | Ổn định giữa 2 lần |
|---|---|---|---|---|---|
| v2 | 0/9, 0/9 | 0/19, 0/20 | 687, 684 | 1179, 1087 ms | 28/28 |
| v3 | 0/9, 0/9 | 1/20, 2/20 | 1206, 1203 | 1640, 1712 ms | 28/29 |
| v4 | **7/9, 5/9** | **0/19, 0/20** | 1622, 1604 | 1853, 2133 ms | 26/28 |

- Câu 27 lỗi HTTP 429 ở lần 1 của v2 và v4 (vượt 15 lượt/phút của gói miễn phí), nên mẫu số báo nhầm là 19 và ổn định so
  trên 28 câu.
- Nếu chỉ tính 7 câu lỗi rõ ràng (bỏ 61 và 25): v4 bắt 7/7 và 5/7.

Từng câu lỗi với v4:

| Câu | Lần 1 | Lần 2 |
|---|---|---|
| 37, 39 | 4 phương án đúng | 4 phương án đúng |
| 7 | A, B | A, B |
| 8 | A, B, C | A, B, C |
| 15 | A, B | A, B |
| 52, 53 | A, B (bắt được) | chỉ A (bỏ sót) |
| 61, 25 | bỏ sót | bỏ sót |

Nhận xét:

- v3 bắt 0/9 vì Solver hiểu "đúng" hẹp: chỉ nhận cách phân tích triệt để, và chính prompt v3 dặn khẳng định không "luôn"
  đúng là sai. v4 bỏ hai cụm đó, thêm quy tắc đọc đúng chữ của đề.
- v4 không báo nhầm câu tốt nào; 2 lần báo nhầm của v3 (câu 32, 35, là Solver tính sai) không còn.
- v4 tốn khoảng 2,4 lần token của v2.
- `temperature` đã bằng 0 ở mọi lần đo, nên khác biệt giữa 2 lần (câu 52, 53 của v4; câu 32 của v3) là do Gemini vẫn không
  tất định ở `temperature = 0`, không phải do cấu hình.

## Hạn chế

- Nhãn "câu lỗi"/"câu tốt" do AI gán trong buổi thẩm định nội dung 31/10, CHƯA được người xác nhận; câu 61 và 25 còn tranh
  cãi. Số liệu chỉ dùng làm kết quả chính thức sau khi người xác nhận nhãn.
- Solver và mô hình sinh câu là cùng một model (`gemini-3.5-flash-lite`): mô hình tự kiểm tra đầu ra của chính nó; chưa thử
  Solver bằng model khác.
- Mẫu nhỏ (29 câu, mỗi phiên bản 2 lần), nên các tỉ lệ chỉ mang tính chỉ báo.
- v4 chưa ổn định với câu phân tích nhân tử có 2 phương án đúng (52, 53).
- Hạn mức 15 lượt/phút của gói miễn phí làm có thể có 1 lượt lỗi 429 mỗi lần đo.
