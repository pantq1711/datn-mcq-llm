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

`distractor-errors.json`: lỗi sai của học sinh cho từng phương án sai của 29 câu (cả 29 đều là `mcq`), dùng để đo hồi quy Solver v5 (`docs/design/phuong-an-nhieu-loi-sai.md` mục 8.2); agent soạn nháp ngày 11/11/2026 theo đáp án đã lưu của từng câu, An duyệt hướng xử lý ngày 11/11/2026. 83 lỗi trên 29 câu, ba nhóm ngoại lệ:

- Câu 30 phương án A, câu 60 phương án B: lỗi kép (2 lỗi cùng lúc).
- Câu 15 D, 25 D, 41 D, 61 D: không có lỗi tự nhiên, để trống (`null`).
- Câu 39 D, 53 D: cố ý không khớp, dùng để kiểm Solver có báo không khớp; khi đo báo riêng `error_checks` của 2 mục này
  (mong đợi: không khớp hoặc `none`).

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

## Đo ngày 14/11/2026: khối thêm vào đầu vào làm Solver bắt kém đi

Cùng 29 câu, cùng model `gemini-3.5-flash-lite`, `temperature = 0`, mỗi cấu hình 1 lần.

| Cấu hình | Bắt được trên 9 câu lỗi | Báo nhầm trên 20 câu tốt | Câu lỗi bắt được |
|---|---|---|---|
| v4, không chip (31/10, 2 lần, để so) | 7/9, 5/9 | 0/19, 0/20 | 7, 8, 37, 39, 52, 53, 15 / 7, 8, 37, 39, 15 |
| v5 (PR B, một lượt) + chip `common_trap` ở mọi câu | **4/9** | **0/20** | 7, 37, 39, 53 |
| PA1 một lượt (nhánh PA1, `solver-v6-distractor-check`): kèm lỗi sai của 3 phương án sai | **3/9** | **0/20** | 8, 37, 39 |

- **v5 + `common_trap`** (`tn1_20261114_solver-v5-chip-common_trap*.{csv,jsonl}`): đo trên `main` (`304a782`) với BẢN SAO
  TẠM của `datn_mcq_e2e`, trong đó 29 câu được gắn `request_codes = {common_trap}` (DB thật không đổi). Công cụ đo không ghi
  `request_check` ra CSV nên phản hồi đầy đủ của Gemini được ghi qua một proxy cục bộ (chỉ phần chữ trả về, không có khóa) vào
  `*_responses.jsonl` (dòng thứ k là câu thứ k của `ids.txt`). Câu 29 lần đầu Gemini trả 503 (dòng `error` trong CSV), đã chạy
  lại riêng (`*_rerun-q29*`): consistent, nên báo nhầm 0/20. `request_check` cả 29 câu đều `met`; lý do đều xoay quanh "phương án
  nhiễu ứng với lỗi học sinh hay mắc". Tổng 30 lượt gọi.
- **PA1 một lượt** (`tn1_20261114_pa1-onepass-errors_solver-v6-distractor-check.csv`): đo trên nhánh
  `feature/misconception-distractors` với `docs/experiments/tn1/distractor-errors.json` của nhánh đó; cột cuối `error_checks` là
  kết quả ghép lỗi theo chữ người soạn gán (39D: `match`, 53D: `mismatch`). Không ghi phản hồi đầy đủ. 29 lượt gọi. Tên phiên
  bản này thuộc nhánh PA1 (chưa merge); trên `main`, `solver-v6` là bản hai lượt (dưới).
- So với v4: v5 + chip sót thêm 8, 52, 15; PA1 một lượt sót 7, 52, 53, 15. Ở các câu phân tích nhân tử, Solver có xu hướng coi
  phương án "chưa triệt để" là phương án nhiễu khi đầu vào nói về "bẫy / lỗi học sinh". Đặt `request_check`/`error_checks` ở
  cuối schema không ngăn được, vì khối nằm trong ĐẦU VÀO. Mẫu nhỏ, mỗi cấu hình 1 lần.
- Hệ quả: tách Solver thành hai lượt (`solver-v6-two-pass`, hotfix 14/11; `docs/design/kiem-soat-do-kho.md` mục 4.3a): lượt 1
  giữ đúng v4, lượt 2 mới thấy yêu cầu (và về sau lỗi phương án của PA1).

### Hotfix `solver-v6-two-pass` (14/11, 3 lần)

Đo trên nhánh `fix/solver-two-pass` với bản sao tạm của `datn_mcq_e2e`. Lượt 1 của v6 giống hệt v4 từng byte cho mọi câu, nên
kết luận đáp án ở đây là ba mẫu v4 nữa. Gemini trả 503 nhiều trong buổi đo; câu lỗi được chạy lại riêng một lần. File:
`tn1_20261114_hotfix-solver-v6-two-pass_{nochip,chip1,chip2}[_rerun].csv` và `*_responses.jsonl` (mỗi dòng một lượt gọi, cột
`kind`: `solve_pass` là lượt giải, `request_pass` là lượt kiểm yêu cầu; `question` là 80 ký tự đầu của đề để ghép với câu).
Tổng 145 lượt gọi cho ba lần đo, kể cả chạy lại.

| Lần | Bắt được | Báo nhầm | Câu lỗi bắt được | Ghi chú |
|---|---|---|---|---|
| Không chip | 5/8 | 0/20 | 7, 8, 15, 37, 39 | câu 61 vẫn 503 sau khi chạy lại (mẫu số 8) |
| Chip `common_trap`, lần 1 | 5/9 | 0/19 | 7, 8, 37, 39, 52 | câu 62 vẫn 503 (mẫu số 19); `request_check`: 21 met, 2 not_met |
| Chip `common_trap`, lần 2 | 4/9 | 0/20 | 7, 8, 37, 39 | `request_check`: 19 met, 1 not_met, 5 lượt kiểm yêu cầu trả 503 (thành `unchecked`) |

- **Hệ quả cho cách đọc số liệu:** cùng prompt v4 mà hôm nay có lần chỉ được 4/9, nên khoảng dao động của v4 là ít nhất
  4/9–7/9. Hai cấu hình một lượt ở trên (4/9 và 3/9) nằm ở đáy hoặc ngay dưới khoảng này: bằng chứng về hiệu ứng neo yếu hơn
  nhận định ban đầu. Cách hai lượt vẫn giữ được: lượt giải không thấy yêu cầu theo cấu trúc, không phụ thuộc vào số đo.
- Các câu không ổn định là 15, 52, 53 (và 37 ở một lần); 61, 25 chưa lần nào bắt được.

## Hạn chế

- Nhãn "câu lỗi"/"câu tốt" do AI gán trong buổi thẩm định nội dung 31/10, CHƯA được người xác nhận; câu 61 và 25 còn tranh
  cãi. Số liệu chỉ dùng làm kết quả chính thức sau khi người xác nhận nhãn.
- Solver và mô hình sinh câu là cùng một model (`gemini-3.5-flash-lite`): mô hình tự kiểm tra đầu ra của chính nó; chưa thử
  Solver bằng model khác.
- Mẫu nhỏ (29 câu, mỗi phiên bản 2 lần), nên các tỉ lệ chỉ mang tính chỉ báo.
- v4 chưa ổn định với câu phân tích nhân tử có 2 phương án đúng (52, 53).
- Hạn mức 15 lượt/phút của gói miễn phí làm có thể có 1 lượt lỗi 429 mỗi lần đo.
