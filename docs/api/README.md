# API backend: tóm tắt cho frontend

Cập nhật 2026-10-21. Nguồn chuẩn là code trong `back_end/` và các migration Flyway; file này chỉ tóm tắt.
Giá trị enum trong JSON là chuỗi viết thường như cột "Giá trị trong DB" của [`docs/contract/enums.md`](../contract/enums.md).

> **Trạng thái tài liệu OpenAPI:** `docs/api/openapi.json` CHƯA được ghi vào thư mục này. Schema form chấm đã bỏ
> `status`, `latexValid`, `latexErrors` theo quyết định 21/10 mục 2; việc xuất file chờ chủ dự án duyệt. Khi được duyệt, sinh bằng:
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
| GET | `/api/reviews/questions` | Danh sách câu để chấm (không có tham số lọc trạng thái) |
| GET | `/api/reviews/questions/{id}` | Form chấm một câu |
| POST | `/api/reviews/questions/{id}` | Nộp đánh giá |
| POST | `/api/reviews/demo/seed-math` | Tạo 5 câu demo; đi qua format và dedup, không chạy answer_check, nên giữ `generated` |
| GET | `/api/bank-items` | Ngân hàng câu hỏi đã duyệt |

Chạy lại QC cho câu `generated` (V4) đã có ở tầng service (`QcRerunService`) nhưng CHƯA có endpoint: backend chưa có
xác thực và phân quyền (Q-6), nên không mở đường công khai có thể gọi Solver tính phí cho câu tùy ý.

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
- Duyệt production chỉ nhận câu `qc_passed` hoặc `in_bank`; câu khác trả 400 với thông báo chung, không nêu trạng thái.
- Câu `duplicate` hoặc `format_failed` (cả hai purpose) trả 404 `{"error": "Không tìm thấy câu hỏi"}`, giống câu không tồn tại.
- Response: `reviewId, questionId, reviewPurpose, reviewerAnswer, formatOk, difficultyLabel, decision, finalOption, edited, editDistance, questionStatus (chỉ production), bankItemId, reviewedAt`. Không có `answerCorrect`, `explanation`.

## 3. Trường bị giấu khỏi form chấm

Form chấm gồm `GET /api/reviews/questions`, `GET /api/reviews/questions/{id}` và response sau khi nộp.

- **Không bao giờ có, cả production lẫn experiment:** `correctOption` (đáp án LLM), `explanation`, `llmDifficulty`,
  `answerCorrect`, chiến lược sinh, `status`, `latexValid`, `latexErrors`, kết quả answer-check (`judgeAnswer`, `judgeModel`)
  và kết quả dedup (`similarityScore`, `comparedToQuestionId`, `thresholdUsed`). Test `OpenApiContractTest` chặn các trường
  này trong schema; `BlindReviewContractTest` quét mọi key của JSON thật ở list, detail và submit.
- **Phản hồi nộp production** giữ `questionStatus` và `bankItemId` (kết quả hành động của giáo viên); experiment không có `questionStatus`.
- **Vòng chấm:** danh sách production chỉ gồm câu `qc_passed` (câu chưa được chấm); danh sách experiment loại `duplicate`
  và `format_failed`, không lọc theo kết quả answer-check.
- **Sửa bài:** chỉ sửa được đề và 4 phương án; `editDistance` tính trên 5 trường đó. Request có `editedExplanation`
  khác null trả 400 với thông điệp trung tính.
  Danh sách không nhận `?status=` (tham số lạ bị bỏ qua) để không suy ra được kết quả QC.

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
| Lỗi | Ký tự điều khiển, số dấu `$` lẻ, ngoặc `{}` không cân bằng, lệnh nằm ngoài `$...$`, thiếu đối số `\frac`/`\sqrt`, `\left`/`\right` hoặc `\begin`/`\end` không khớp | Câu sinh ra thành `format_failed`; bản sửa của giáo viên bị từ chối (400) |
| Cảnh báo | Lệnh nằm ngoài danh sách cho phép, ví dụ `\widehat`, `\alpha`; môi trường ngoài danh sách (ví dụ `array`) khi cấu trúc cân bằng | Không ảnh hưởng trạng thái, không trả về API |

Gợi ý render phía frontend: tách chuỗi theo `$$...$$` và `$...$` (bỏ qua `\$`), render từng đoạn công thức bằng KaTeX
(`displayMode` cho `$$`), để `throwOnError: false` hoặc bắt lỗi từng đoạn và hiện chuỗi gốc khi không render được.
KaTeX ở chế độ `strict: "error"` có thể từ chối chữ tiếng Việt nằm trong công thức; backend không kiểm tra điều này.
