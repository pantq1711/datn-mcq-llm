# D2/D4 – Cấu hình lần sinh dữ liệu (15/11/2026)

Ghi lại TRƯỚC khi gọi Gemini (L1, L14). Mục "chờ" điền khi chạy phần B; không sửa các mục đã ghi sau khi sinh.

## Mã nguồn

| Mục | Giá trị | Nguồn |
|---|---|---|
| Commit sinh đề (backend) | `ae71b1f811625054155f04ed37f767c2264111e7` (main, merge #76, 14/11/2026 19:24 +07) | `git log -1` trên worktree `feature/d2-fixes` |
| Có PA1 (nhiễu kèm lỗi sai) không | **Không** (PA1 chưa merge) | |
| Ngày sinh | 15/11/2026, 16:12–16:29 (đề hệ thống), 16:30–16:31 (baseline) | |
| Ma trận | [`ma-tran-6-6-6.json`](ma-tran-6-6-6.json): 18 câu, 6 mcq / 6 tf / 6 sa; dùng cho CẢ hệ thống và baseline | |
| Mức thử thách đề | `standard` (Chuẩn), không yêu cầu giáo viên (`requestCodes` rỗng), không tài liệu | |

## Mô hình và prompt

| Mục | Giá trị | Nguồn |
|---|---|---|
| `LLM_MODEL` | `gemini-3.5-flash-lite` (biến trong `.env` để trống → mặc định `app.llm.model`) | `application.yaml:65` |
| `LLM_SOLVER_MODEL` | `gemini-3.5-flash-lite` (không có trong `.env` → mặc định `app.llm.solver-model`) | `application.yaml:66`, `AnswerSolverChecker` |
| Prompt sinh mcq | `matrix-mcq-v9-draft` | `MatrixPromptDraftV0.promptVersion` |
| Prompt sinh tf | `matrix-tf-v12-draft` | như trên |
| Prompt sinh sa | `matrix-sa-v12-draft` | như trên |
| Prompt Solver | `solver-v6-two-pass` (không đặt `app.llm.solver-prompt-version` → `PromptBuilder.SOLVER_PROMPT_VERSION`); KHÔNG lưu trong DB | `AnswerSolverChecker`, `PromptBuilder:30` |
| Temperature sinh câu | 0,7 (cột `experiment_run.temperature` của run ma trận) | `GenerationService.createMatrixRun` |
| Temperature Solver | 0,0 | `AnswerSolverChecker` (`callJsonModeWithSchema(..., 0.0, ...)`) |
| Dedup | 3-gram, ngưỡng mặc định của main; `EMBEDDING_MODEL` để trống (không gọi embedding) | `.env` (chỉ kiểm tên biến) |
| Grounding | Không chạy (đề không đính tài liệu) | `GenerationService` (job grounding chỉ có khi có tài liệu) |
| Bộ điều tiết | `LLM_MIN_SPACING_MS` 4200, `LLM_DAILY_QUOTA` 480 (mặc định) | `application.yaml:70-71` |

## Baseline (D4)

| Mục | Giá trị |
|---|---|
| Prompt | `docs/experiments/d4/prompts.md` prompt 1 và prompt 2, nguyên văn |
| Model | như `LLM_MODEL` ở trên |
| Số lượt | 4 lượt mỗi prompt (= số đề hệ thống, L3) |
| Temperature | 0,7 cho cả prompt 1 và 2 (An chốt 15/11, bằng temperature sinh câu của app, L9); ghi trong từng `*.meta.json` |
| SHA-256 prompt (dry-run, ma trận 6/6/6) | prompt 1 `5436779bd707…`, prompt 2 `aaa05dc776f9…` (đủ 64 ký tự trong meta) |

## Môi trường sinh

| Mục | Giá trị |
|---|---|
| DB | `datn_mcq_d2` trên container `datn-mcq-db`, tạo mới 15/11 13:06, Flyway từ đầu đến V29, `bank_item` = 0 |
| Backend | một tiến trình duy nhất, cổng 8095, worker bật, trỏ `datn_mcq_d2` (`.env` của repo chính) |
| Tài khoản giáo viên | `gv-d2@local.test` (id 2), tạo qua `POST /api/admin/users`; admin bootstrap từ `ADMIN_*`. Mật khẩu chỉ nằm ở `data/private/do-luong/d2-tai-khoan.txt` và `.env` |

## Đề thực nghiệm (điền khi sinh, theo thứ tự)

| Thứ tự | id đề | Lúc tạo | `fromBank` | Số lần sinh lại | Ghi chú |
|---|---|---|---|---|---|
| 1 | 1 | 15/11 16:12:17 | 0 | 1 (1 chỗ) | 18/18 sẵn sàng, 0 chỗ hỏng; 44 lượt Gemini |
| 2 | 2 | 15/11 16:15:45 | 0 | 6 (6 chỗ) | 18/18, 0 chỗ hỏng; 64 lượt Gemini |
| 3 | 3 | 15/11 16:22:00 | 0 | 4 (3 chỗ) | 18/18, 0 chỗ hỏng; 53 lượt Gemini |
| 4 | 4 | 15/11 16:25:59 | 0 | 6 (4 chỗ) | 18/18, 0 chỗ hỏng; 47 lượt Gemini |

Ma trận trong DB: `exam_matrix.id = 1` (tạo qua `POST /api/exam-matrices`, `sample_key` NULL). Số lần sinh lại = tổng
`exam_slot.attempts` − 18. Lượt Gemini = hiệu `datn_gemini_daily_used` (bộ đếm của app) trước/sau từng đề; tổng 208.
Kết thúc 16:29:27: 89 job `generate` và 18 job `answer_check`, tất cả `done`; không còn `pending`/`running`/`failed`;
`bank_item` = 0, `exam_slot_action` = 0. Backend đã tắt sau đó.

Baseline gọi lúc 16:30–16:31: 8 lượt (prompt 1 và 2, mỗi prompt 4 lượt), `temperature` 0,7, model `gemini-3.5-flash-lite`,
không lượt nào phải thử lại; meta ghi dấu vân tay ma trận `d6f524bd41…`. Tổng lượt Gemini phần B: 208 + 8 = 216.

Không Giữ / Đổi / Sửa trên 4 đề này. Chỉ xuất sau khi không còn job `pending`/`running` (L10c).

## Can thiệp khi chạy

Quy tắc An duyệt 15/11: đọc tiến độ 10 giây một lần (như frontend; đối soát và sinh lại do QC chỉ chạy khi có lần đọc này).
Trước mỗi lần đọc, job `generate`/`answer_check` của đề đang sinh ở trạng thái `failed` vì lỗi hạ tầng tạm thời (HTTP 429, 503,
5xx khác, hết thời gian chờ) được đặt về `pending` (giữ `attempt`, chạy lại sau 90 s) bằng SQL trên `datn_mcq_d2`. Mỗi chỗ tối
đa 3 lần đặt lại; quá 3 lần thì để nguyên `failed` và ghi chú. Nhật ký từng lần: `data/private/do-luong/d2-sinh-de-log.jsonl`.

| Đề | 429 | 503 | 5xx khác | timeout | Tổng | Chỗ để nguyên failed vì quá 3 lần |
|---|---|---|---|---|---|---|
| 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2 | 0 | 1 | 0 | 0 | 1 | 0 |
| 3 | 0 | 0 | 0 | 0 | 0 | 0 |
| 4 | 0 | 0 | 0 | 0 | 0 | 0 |

Lần duy nhất: đề 2, job 52 (`answer_check`, chỗ 30, câu 42) `failed` sau 3 lần thử vì HTTP 503, đặt lại lúc 16:21:28, chạy
xong ở lần thử 4 (16:21:33). Ghi chú: `next_run_at` đặt bằng `now()` của Postgres (UTC) trong khi app so theo giờ máy
(+07), nên khoảng chờ 90 s không có hiệu lực và job chạy lại ngay; không ảnh hưởng kết quả.

Không đặt lại job nào hỏng vì parse, vì format hay vì Solver kết luận: điều kiện đặt lại chỉ khớp thông điệp lỗi HTTP
429/5xx hoặc hết thời gian chờ, và khi kết thúc không còn job `failed` nào (danh sách `loi_job_failed_con_lai` của cả 4 đề
rỗng).
