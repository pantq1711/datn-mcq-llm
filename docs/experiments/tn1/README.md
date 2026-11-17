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
  - Nhãn phụ (An chốt 17/11/2026): 15 và 61 giữ nhãn lỗi, loại "mơ hồ do diễn đạt"; 25 tách khỏi tập chính và báo riêng. Xem
    mục "Soát lại 16–17/11/2026".
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
- Cách chia tập câu lỗi đổi ngày 17/11: 15 và 61 cùng một nhóm, 25 tách riêng; số theo từng tập ở mục "Soát lại
  16–17/11/2026". Dòng cũ "nếu chỉ tính 7 câu lỗi rõ ràng (bỏ 61 và 25): v4 bắt 7/7 và 5/7" xếp 15 khác nhóm với 61 nên không
  dùng nữa.

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

## Đo ngày 14/11/2026: thêm khối vào đầu vào của Solver (chưa đủ bằng chứng là bắt kém đi)

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
  - Sửa 17/11: hiện tượng coi phương án "chưa triệt để" là sai có cả khi KHÔNG có khối nào. Ở lần "không chip" và ở lượt giải
    của v6 (không thấy yêu cầu), Solver vẫn ghi cờ sai cho 52B, 52D, 53B với lý do "chưa đặt nhân tử chung lớn nhất". Khối thêm
    vào có thể làm tăng hiện tượng này, nhưng không phải nguyên nhân duy nhất. Chỗ khớp với giả thuyết "neo" là câu 7 và 8:
    lượt giải dùng prompt v4 bắt cả hai ở 5/5 lần, lần sót duy nhất của câu 8 là v5 + chip, của câu 7 là PA1 một lượt.
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
- Các câu không ổn định là 15, 52, 53; 61, 25 chưa lần nào bắt được. (Sửa 17/11: câu 37 KHÔNG đổi kết luận. Mọi lần có kết
  quả đều là 4 phương án đúng; hai lần thiếu là Gemini trả 503, chạy lại đều bắt được.)

## Soát lại 16–17/11/2026: nhãn, độ ổn định, cách đếm

Không đo thêm và không gọi Gemini: chỉ đọc lại các CSV và `*_responses.jsonl` ở trên.

### Nhãn (An chốt 17/11/2026)

| Nhóm | Câu | Ghi chú |
|---|---|---|
| Lỗi, nhiều phương án đúng (nghĩa hẹp) | 7, 8, 37, 39, 52, 53 | Phương án bằng đa thức đã cho: 7 (A, B); 8 (A, B, C); 37, 39 (cả 4); 52 (A, B, D); 53 (A, B) |
| Lỗi, mơ hồ do diễn đạt | 15, 61 | Đề thiếu "luôn". Theo quy ước ra đề thông thường chỉ B đúng; câu 61 còn có phương án lệch câu dẫn |
| Tách khỏi tập chính | 25 | Bản người sửa trong E2E, không phải đầu ra LLM; phương án D "$6$ km" hỏng. Báo riêng: Solver sót 5/5 lần |
| Tốt | 20 câu như cũ | Câu 24 giữ nhãn tốt dù phương án D $\frac{x^2 - 1}{x + 1}$ rút gọn được thành đa thức; không sửa tập đo |

Tập chính có 8 câu lỗi (6 câu nghĩa hẹp và 2 câu mơ hồ do diễn đạt). 9 câu nhãn gốc là tập chính cộng câu 25.

### Lượt giải dùng prompt v4: 5 mẫu

Hai lần v4 ngày 31/10 và ba lần v6 ngày 14/11 (lượt giải của v6 giống v4 từng byte). Ô có mẫu số nhỏ hơn là do lượt lỗi gọi.

| Lần | 9 câu (nhãn gốc) | 8 câu (tập chính) | 6 câu (nghĩa hẹp) | Báo nhầm trên 20 câu tốt |
|---|---|---|---|---|
| v4, 31/10 lần 1 | 7/9 | 7/8 | 6/6 | 0/19 |
| v4, 31/10 lần 2 | 5/9 | 5/8 | 4/6 | 0/20 |
| v6 không chip, 14/11 | 5/8 | 5/7 | 4/6 | 0/20 |
| v6 chip lần 1, 14/11 | 5/9 | 5/8 | 5/6 | 0/19 |
| v6 chip lần 2, 14/11 | 4/9 | 4/8 | 4/6 | 0/20 |
| Cộng 5 lần | 26/44 | 26/39 | 23/30 | 0/98 |
| v5 + chip (1 lần, để so) | 4/9 | 4/8 | 4/6 | 0/20 |
| PA1 một lượt (1 lần, để so) | 3/9 | 3/8 | 3/6 | 0/20 |

Câu 25 không lần nào bắt được, nên nếu giữ câu 25 trong tập nghĩa hẹp thì chỉ mẫu số đổi (6 thành 7).

Theo từng câu, 5 mẫu trên:

| Nhóm | Câu | Số lần bắt |
|---|---|---|
| Bắt ổn định | 7, 8, 37, 39 | 5/5 mỗi câu |
| Lúc bắt lúc sót | 15 | 3/5 |
| | 52 | 2/5 |
| | 53 | 1/5 |
| Sót ổn định | 61 | 0/4 (1 lần 503 kể cả khi chạy lại) |
| | 25 | 0/5 |

Cách đọc: không trích một lần chạy đơn lẻ (ví dụ "7/9"). Với 5 mẫu, v4 bắt 4–7 trên 9 câu; một lần chạy lệch lần khác tới 3
câu dù cùng prompt, cùng `temperature = 0`. Hai cấu hình chỉ đo 1 lần (v5 + chip, PA1 một lượt) nằm trong hoặc ngay dưới khoảng
này.

### Cờ từng phương án lệch với kết luận của chính Solver

Có phản hồi đầy đủ cho 114 lượt giải (v5 + chip và ba lần v6, kể cả chạy lại): 79 lượt trên câu tốt, 35 lượt trên 9 câu lỗi.

| | `judge_answer = multiple` | Từ 2 cờ `correct = true` trở lên |
|---|---|---|
| 20 câu tốt (79 lượt) | 0 | 0 |
| 9 câu lỗi (35 lượt) | 20 | 18 |

Hai lượt lệch nhau: câu 52 (không chip) và câu 53 (chip lần 1, lượt chạy lại). Ở cả hai, mảng cờ chỉ có A đúng, nhưng Solver
ghi `correct_count` là 3 và 2, `judge_answer = multiple`; lý do của phương án bị ghi sai tự kết luận "vẫn là một tích đúng",
"do đó phương án này đúng". Schema bắt ghi `correct` trước `reason`. Backend đếm theo cờ
(`AnswerSolverChecker.fromOptionsVerdict`) nên hai lượt này được ghi là bỏ sót. Nếu đếm theo `judge_answer` thì lần không chip
là 6/8 và chip lần 1 là 6/9, báo nhầm không đổi.

Từ 17/11 backend đã đổi theo hướng đó (dev-log L-70): từ 2 cờ đúng hoặc `judge_answer = multiple` đều tính là nhiều phương án
đúng. Mọi CSV trong thư mục này đo TRƯỚC thay đổi; các bảng ở trên giữ số đã đo, không tính lại. Quy tắc được tìm ra trên chính
29 câu này, chưa kiểm trên tập độc lập.

Không kiểm lại được với v3, v4 (31/10) và PA1 một lượt: các lần đó không lưu phản hồi đầy đủ, và cột `solver_answer` của CSV
là kết luận backend suy từ cờ, không phải `judge_answer` gốc. Với v2, `solver_answer` chính là `judge_answer`: 0 lần `multiple`.

### Ví dụ trong prompt v4 trùng câu của tập đo

- Ví dụ 1 của prompt v4 ("Phân tích $2x^3 - 6x^2$ thành nhân tử", $2x(x^2 - 3x)$, $2x^2(x - 3)$) là đa thức và hai phương án
  A, B của câu 37.
- Ví dụ 2 ("Hai đường chéo vuông góc với nhau" của hình thang cân) là phương án A của câu 15.
- Năm câu nhân tử còn lại cùng khuôn với ví dụ 1 (đặt nhân tử chung của một nhị thức).

Vì vậy 29 câu này là tập phát triển của prompt v4, không phải tập kiểm tra. Hai câu lỗi không có ví dụ tương ứng (61, 25)
không lần nào được bắt.

## Hạn chế

- Nhãn "câu lỗi"/"câu tốt" do AI gán trong buổi thẩm định nội dung 31/10. Ngày 16/11 AI tự giải lại cả 29 câu (dựng lại đề
  từ phản hồi của Solver, không đọc được nguyên văn trong DB) và đồng ý 27 nhãn; ngày 17/11 An chốt các câu còn tranh cãi
  (15, 61, 25, 24) như ở mục "Soát lại 16–17/11/2026". Chưa có giáo viên ngoài nhóm xác nhận nhãn.
- 29 câu là tập phát triển: prompt v4 được viết sau khi xem chính các câu này và hai ví dụ của prompt trùng câu 37, 15. Kết
  quả là mức khớp với câu đã thấy, không phải ước lượng trên câu mới.
- Chỉ có 9 câu lỗi, trong đó 6 câu cùng một khuôn (đặt nhân tử chung). Tập này không nói được gì về câu sai đáp án do tính
  toán hay câu không có phương án đúng.
- Solver và mô hình sinh câu là cùng một model (`gemini-3.5-flash-lite`): mô hình tự kiểm tra đầu ra của chính nó; chưa thử
  Solver bằng model khác.
- Mẫu nhỏ: 29 câu; v2 và v3 mỗi bản 2 lần; lượt giải dùng prompt v4 có 5 mẫu (2 lần 31/10, 3 lần 14/11); v5 + chip và PA1 một
  lượt mỗi cấu hình 1 lần. Các tỉ lệ chỉ mang tính chỉ báo; so sánh giữa các cấu hình đo 1 lần chưa kết luận được.
- v4 chưa ổn định ở câu 52, 53 (phân tích nhân tử) và câu 15, dù `temperature = 0`. Các lần đo cách nhau hai tuần, nên không
  tách được dao động ngẫu nhiên với thay đổi phía dịch vụ.
- Lượt lỗi gọi làm mẫu số thay đổi: 429 (hạn mức 15 lượt/phút của gói miễn phí) ở các lần 31/10, 503 nhiều ở các lần 14/11.
- v3, v4 (31/10) và PA1 một lượt không lưu phản hồi đầy đủ của Solver, nên không kiểm lại được lý do từng phương án của các
  lần đó.
