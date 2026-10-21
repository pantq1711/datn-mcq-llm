# API backend: tóm tắt cho frontend

Cập nhật 2026-10-21. Nguồn chuẩn là code trong `back_end/` và các migration Flyway; file này chỉ tóm tắt.
Giá trị enum trong JSON là chuỗi viết thường như cột "Giá trị trong DB" của [`docs/contract/enums.md`](../contract/enums.md).

> **Trạng thái tài liệu OpenAPI:** `docs/api/openapi.json` CHƯA được ghi vào thư mục này. Lý do nằm ở mục
> "Trường bị giấu khỏi form chấm": schema form chấm production hiện còn chứa `status`, `latexValid`, `latexErrors`,
> mâu thuẫn với quyết định Q-1, đang chờ chủ dự án chốt. Sau khi chốt, sinh lại bằng:
>
> ```
> cd back_end
> ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.export.path=../docs/api/openapi.json
> ```
>
> Khi chạy app, springdoc phục vụ `/v3/api-docs` và Swagger UI (`/swagger-ui.html`). Đường Swagger UI chưa được kiểm chứng trong lượt này.

## 1. Quy ước chung

- Base URL: `http://localhost:8080`. CORS cho phép mọi cổng của `localhost` và `127.0.0.1` trên `/api/**`.
- Chưa có xác thực. `reviewerId` lấy từ body; bỏ trống thì dùng giáo viên mặc định.
- Lỗi do backend chủ động trả có dạng `{"error": "thông báo tiếng Việt"}`:

| Mã | Khi nào |
|---|---|
| 400 | Dữ liệu không hợp lệ, ví dụ thiếu trường bắt buộc hoặc bản sửa còn lỗi LaTeX fatal |
| 404 | Không tìm thấy bản ghi, ví dụ `subjectId` hoặc `templateId` không tồn tại |
| 409 | Xung đột trạng thái, ví dụ nộp đánh giá cho câu `duplicate` hoặc nộp lại câu experiment |
| 429 | Gemini đang giới hạn tần suất gọi; chờ khoảng một phút rồi thử lại |
| 503 | Gemini tạm thời quá tải hoặc bảo trì |
| 500 | Lỗi khác chưa được phân loại, body theo định dạng mặc định của Spring |

## 2. Endpoint

| Phương thức | Đường dẫn | Việc |
|---|---|---|
| GET | `/api/catalog/subjects` | Danh sách môn |
| GET | `/api/catalog/templates?subjectId=` | Danh sách template, lọc theo môn nếu có |
| POST | `/api/catalog/templates` | Tạo template cho môn đã tồn tại (không tạo môn) |
| POST | `/api/generation/generate-sync` | Sinh 1 câu và chạy QC ngay. Gọi Gemini, có thể tốn phí |
| POST | `/api/generation/jobs/enqueue` | Tạo job sinh câu bất đồng bộ (tối đa 50) |
| POST | `/api/generation/jobs/process-next?runAnswerCheck=true` | Xử lý ngay 1 job đến hạn. Gọi Gemini |
| GET | `/api/generation/jobs` | Danh sách job |
| GET | `/api/reviews/questions?status=` | Danh sách câu để chấm |
| GET | `/api/reviews/questions/{id}` | Form chấm một câu |
| POST | `/api/reviews/questions/{id}` | Nộp đánh giá |
| POST | `/api/reviews/demo/seed-math` | Tạo 5 câu demo; đi qua format và dedup, không chạy answer_check, nên giữ `generated` |
| GET | `/api/bank-items` | Ngân hàng câu hỏi đã duyệt |

### Tạo template

```http
POST /api/catalog/templates
{"subjectId": 1, "gradeLevel": 8, "topic": "Phân thức đại số",
 "learningOutcome": "Rút gọn được phân thức.",
 "sourceQuestion": "Rút gọn phân thức $\\frac{4x^2y}{8xy^3}$.", "sourceType": "text"}
```

- `subjectId`, `sourceQuestion` bắt buộc. `gradeLevel` bỏ trống hoặc từ 6 đến 9. `topic` tối đa 255 ký tự.
- `sourceType` bỏ trống thì là `text`. Chuỗi được trim; chuỗi rỗng ở trường không bắt buộc thành `null`.
- Trả về template vừa tạo: `id, subjectId, gradeLevel, topic, learningOutcome, sourceType, sourceQuestion, version, isActive`.
- Môn chỉ được tạo qua seed hoặc trực tiếp trong DB; chưa có endpoint tạo môn.

### Sinh câu đồng bộ

```http
POST /api/generation/generate-sync
{"templateId": 3, "strategy": "few-shot-structured", "targetDifficulty": "easy", "runAnswerCheck": true}
```

- Thử offline không tốn phí: truyền `rawLlmOutputOverride` là chuỗi JSON 7 trường VÀ `"runAnswerCheck": false`.
  Nếu không tắt answer-check, backend vẫn gọi Solver Gemini.
- Response: `{"question": {id, templateId, runId, questionText, optionA..optionD, parseSuccess, status, tokenCount, latencyMs, createdAt}, "qcReport": {resultingStatus, formatPassed, formatErrors, answerCheckPassed, judgeAnswer, judgeModel, solverReasoning, answerCheckStatus, dedupStatus, dedup}}`.
- `dedupStatus`: `NOT_RUN` (format lỗi), `NO_DUPLICATE`, `DUPLICATE`, `ERROR`. `dedup` gồm `status, similarity, comparedToQuestionId, thresholdUsed, layer, detail`.
  Solver chỉ được gọi khi `dedupStatus = NO_DUPLICATE`; câu trùng có `status = duplicate`, dedup lỗi giữ `generated`.
- Response này dành cho người nghiên cứu, không phải form chấm: nó có kết quả QC và `judgeAnswer`.

### Nộp đánh giá

```http
POST /api/reviews/questions/42
{"reviewerId": 1, "reviewPurpose": "production", "reviewerAnswer": "B", "formatOk": true,
 "difficultyLabel": "easy", "decision": "approved_with_edit", "finalOption": "B",
 "editedQuestionText": "Kết quả của phép tính $\\sqrt{(-5)^2}$ là:"}
```

- `reviewerAnswer`: `A`..`D`, `multiple`, `none`. `decision`: `approved_as_is`, `approved_with_edit`, `rejected`.
- `rejected` cần ít nhất một `reasonCodes`; mã `other` cần `otherReasonDetail`. `rejected` thì `finalOption` phải trống.
- Duyệt production chỉ nhận câu `qc_passed` hoặc `in_bank`. Câu `duplicate` trả 409.
- Response: `reviewId, questionId, reviewPurpose, reviewerAnswer, formatOk, difficultyLabel, decision, finalOption, edited, editDistance, questionStatus (chỉ production), bankItemId, reviewedAt`. Không có `answerCorrect`, `explanation`.

## 3. Trường bị giấu khỏi form chấm

Form chấm gồm `GET /api/reviews/questions`, `GET /api/reviews/questions/{id}` và response sau khi nộp.

- **Không bao giờ có, cả production lẫn experiment:** `correctOption` (đáp án LLM), `explanation`, `llmDifficulty`,
  `answerCorrect`, chiến lược sinh, kết quả answer-check (`judgeAnswer`, `judgeModel`) và kết quả dedup
  (`similarityScore`, `comparedToQuestionId`, `thresholdUsed`). Test `OpenApiContractTest` chặn các trường này trong schema.
- **Experiment còn giấu thêm:** `status`, `latexValid`, `latexErrors` (bị loại khỏi JSON).
- **Chờ quyết định:** form production hiện TRẢ `status`, `latexValid`, `latexErrors`; danh sách production trả `status`, `latexValid`.
  Đây là kết quả QC, mâu thuẫn với Q-1 trong `docs/ai-development-rules.md`. Chưa đổi code vì cần chủ dự án chốt.
- Hợp đồng (`docs/contract/enums.md` mục 3): câu `duplicate` không vào vòng chấm của cả production và experiment.
  Hiện code mới loại câu production; danh sách experiment chưa loại `duplicate` và `format_failed` (bước sau).

Trạng thái câu (`status`): `generated`, `format_failed`, `duplicate`, `qc_passed`, `rejected`, `in_bank`.
Dedup đã nối vào pipeline: câu sinh thật chỉ đạt `qc_passed` khi format đạt, dedup không trùng và answer_check
(Solver Gemini) chạy thành công và khớp đáp án. Dedup chưa khóa theo phạm vi, nên tránh sinh song song cùng môn.

## 4. Định dạng công thức toán

Các trường `questionText`, `optionA`..`optionD`, `explanation` là văn bản tiếng Việt xen công thức LaTeX:

- `$...$` là công thức trong dòng; `$$...$$` là công thức hiển thị riêng dòng.
- `\$` ngoài công thức là dấu đô la thường, không mở công thức.
- Môi trường được kiểm tra: `matrix`, `cases`, `aligned`, đặt trong công thức; `\\` là xuống hàng trong môi trường.
  Ví dụ hệ phương trình: `$\left\{\begin{matrix} 2x + y = 5 \\ x - y = 1 \end{matrix}\right.$`.
- Ký tự xuống dòng thật chỉ hợp lệ ngoài công thức, trong `$$...$$` hoặc trong môi trường.
- Trong JSON, mỗi dấu `\` của LaTeX được viết thành `\\`, ví dụ `"$\\frac{1}{2}$"`.

Backend kiểm tra cú pháp bề mặt, không bảo đảm KaTeX render được:

| Mức | Trường hợp | Hệ quả |
|---|---|---|
| Lỗi | Ký tự điều khiển, số dấu `$` lẻ, ngoặc `{}` không cân bằng, lệnh nằm ngoài `$...$`, thiếu đối số `\frac`/`\sqrt`, `\left`/`\right` hoặc `\begin`/`\end` không khớp, môi trường ngoài danh sách | Câu sinh ra thành `format_failed`; bản sửa của giáo viên bị từ chối (400); `latexValid = false` |
| Cảnh báo | Lệnh nằm ngoài danh sách cho phép, ví dụ `\widehat`, `\alpha` | Không ảnh hưởng trạng thái, không hiện trong `latexErrors` |

Gợi ý render phía frontend: tách chuỗi theo `$$...$$` và `$...$` (bỏ qua `\$`), render từng đoạn công thức bằng KaTeX
(`displayMode` cho `$$`), để `throwOnError: false` hoặc bắt lỗi từng đoạn và hiện chuỗi gốc khi không render được.
KaTeX ở chế độ `strict: "error"` có thể từ chối chữ tiếng Việt nằm trong công thức; backend không kiểm tra điều này.
