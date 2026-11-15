# D2/D4 – Cấu hình lần sinh dữ liệu (15/11/2026)

Ghi lại TRƯỚC khi gọi Gemini (L1, L14). Mục "chờ" điền khi chạy phần B; không sửa các mục đã ghi sau khi sinh.

## Mã nguồn

| Mục | Giá trị | Nguồn |
|---|---|---|
| Commit sinh đề (backend) | `ae71b1f811625054155f04ed37f767c2264111e7` (main, merge #76, 14/11/2026 19:24 +07) | `git log -1` trên worktree `feature/d2-fixes` |
| Có PA1 (nhiễu kèm lỗi sai) không | **Không** (PA1 chưa merge) | |
| Ngày sinh | 15/11/2026 (từ 15:00) | |
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
| Temperature | chờ: đề xuất `--temperature 0.7` (bằng temperature sinh câu của app, L9); giá trị thật ghi trong từng `*.meta.json` |
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
| 1 | chờ | | | | |
| 2 | chờ | | | | |
| 3 | chờ | | | | |
| 4 | chờ | | | | |

Không Giữ / Đổi / Sửa trên 4 đề này. Chỉ xuất sau khi không còn job `pending`/`running` (L10c).
