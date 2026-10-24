# API backend: tóm tắt cho frontend

Cập nhật 2026-10-24 (W1-1: đăng nhập JWT, phân quyền, quyền sở hữu, `GET /api/exams`; mục 8). Trước đó 2026-10-22
(thêm câu Đúng/Sai `tf` và Trả lời ngắn `sa`). Nguồn chuẩn là code trong `back_end/` và các
migration Flyway; file này chỉ tóm tắt.
Giá trị enum trong JSON là chuỗi viết thường như cột "Giá trị trong DB" của [`docs/contract/enums.md`](../contract/enums.md)
(riêng `answerCheckStatus`, `dedupStatus` viết hoa như `CONSISTENT`, `NO_DUPLICATE`).

> **Tài liệu OpenAPI:** [`openapi.json`](openapi.json) được xuất từ springdoc qua test `OpenApiContractTest`. Sinh lại bằng:
>
> ```
> cd back_end
> ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.export.path=../docs/api/openapi.json
> ```
>
> Khi chạy app, springdoc phục vụ `/v3/api-docs` và Swagger UI (`/swagger-ui.html`). Đường Swagger UI chưa được kiểm chứng.

## 1. Quy ước chung

- Base URL: dev gọi qua Vite proxy `/api` → `http://localhost:8080` (cùng origin); triển khai: nginx phục vụ frontend và
  proxy `/api` cùng domain. CORS vẫn bật cho các origin trong `CORS_ALLOWED_ORIGINS` (mặc định `http://localhost:5173`,
  `http://127.0.0.1:5173`), có `allowCredentials`.
- **Cần đăng nhập** cho mọi `/api/**` trừ `POST /api/auth/login|refresh|logout` (mục 8). Gửi header
  `Authorization: Bearer <accessToken>`. Vai trò: `teacher`, `admin`. Form chấm `/api/reviews/**` vẫn lấy `reviewerId`
  từ body như cũ (chưa đổi, chờ Q-6).
- Lỗi do backend chủ động trả có dạng `{"error": "thông báo tiếng Việt"}`; lỗi xác thực có thêm `"code"`:

| Mã | Khi nào |
|---|---|
| 400 | Dữ liệu không hợp lệ, ví dụ thiếu trường bắt buộc, gửi trường không áp dụng cho dạng câu, hoặc bản sửa còn lỗi LaTeX fatal |
| 401 | Chưa đăng nhập (`UNAUTHENTICATED`), token hết hạn (`TOKEN_EXPIRED`), token sai (`TOKEN_INVALID`), đăng nhập sai (`INVALID_CREDENTIALS`), refresh không hợp lệ (`REFRESH_INVALID`) |
| 403 | Sai vai trò (`FORBIDDEN`), ví dụ giáo viên gọi API quản trị, admin gọi API của giáo viên |
| 404 | Không tìm thấy bản ghi, ví dụ `subjectId` hoặc `templateId` không tồn tại. Ma trận, đề của giáo viên khác cũng trả 404 (giống id không tồn tại) |
| 409 | Xung đột trạng thái, ví dụ nộp đánh giá cho câu `duplicate` hoặc nộp lại câu experiment |
| 429 | Gemini đang giới hạn tần suất gọi; chờ khoảng một phút rồi thử lại. Hoặc đăng nhập sai quá nhiều lần (`TOO_MANY_ATTEMPTS`, header `Retry-After`, mục 8.2) |
| 503 | Gemini tạm thời quá tải hoặc bảo trì |
| 500 | Lỗi khác chưa được phân loại, body theo định dạng mặc định của Spring |

Dạng câu (`questionType`): `mcq` (trắc nghiệm 4 phương án, mặc định khi bỏ trống), `tf` (Đúng/Sai: đề dẫn và đúng 4 ý
a-d), `sa` (Trả lời ngắn: đề và một đáp số). Câu `tf` và `sa` chỉ sinh cho `production`, chiến lược `zero-shot`, prompt
bản nháp (`tf-v2-draft`, `sa-v1-draft`; run cũ là `v0`, `tf-v1-draft`). Thực nghiệm C1/C2/C3 chỉ dùng `mcq`.
Khi sinh câu `tf`/`sa` (sync và job), prompt gửi model kèm tối đa 10 câu gần nhất cùng template và cùng dạng để model
không lặp lại bài toán hay số liệu; xem `enums.md` mục 3, "Độ đa dạng".

## 2. Endpoint

Cột "Quyền": `công khai`, `đăng nhập` (mọi vai trò), `GV` (giáo viên), `AD` (admin). Đường không có trong bảng bị chặn.

| Phương thức | Đường dẫn | Việc | Quyền |
|---|---|---|---|
| POST | `/api/auth/login` | Đăng nhập (mục 8) | công khai |
| POST | `/api/auth/refresh` | Đổi refresh token (cookie) lấy cặp token mới | công khai |
| POST | `/api/auth/logout` | Đăng xuất, xóa cookie | công khai |
| GET | `/api/auth/me` | Thông tin người đang đăng nhập | đăng nhập |
| POST | `/api/auth/password` | Đổi mật khẩu (thu hồi mọi phiên) | đăng nhập |
| GET | `/api/exams?page=&size=` | Đề của tôi, mới nhất trước, phân trang (mục 7.0) | GV |
| GET | `/api/admin/users?role=` | Danh sách tài khoản (mục 8.1) | AD |
| POST | `/api/admin/users` | Tạo tài khoản giáo viên | AD |
| PATCH | `/api/admin/users/{id}` | Sửa tên, bật/khóa (khóa thì thu hồi phiên) | AD |
| POST | `/api/admin/users/{id}/reset-password` | Đặt lại mật khẩu (thu hồi phiên) | AD |

Quyền của các dòng dưới: `catalog` GET và `bank-items` cho GV và AD; `POST /api/catalog/templates`, `/api/generation/**`,
`/api/reviews/demo/seed-math` chỉ AD; `/api/reviews/**` khác, `/api/exam-matrices/**`, `/api/exams/**` chỉ GV (ma trận,
đề chỉ của chính người gọi).

| Phương thức | Đường dẫn | Việc |
|---|---|---|
| GET | `/api/catalog/subjects` | Danh sách môn |
| GET | `/api/catalog/templates?subjectId=` | Danh sách template, lọc theo môn nếu có |
| POST | `/api/catalog/templates` | Tạo template cho môn đã tồn tại (không tạo môn) |
| POST | `/api/generation/generate-sync` | Sinh 1 câu (mọi dạng) và chạy QC ngay. Gọi Gemini, có thể tốn phí |
| POST | `/api/generation/jobs/enqueue` | Tạo job sinh câu bất đồng bộ (tối đa 50, mọi dạng) |
| POST | `/api/generation/jobs/process-next?runAnswerCheck=true` | Xử lý ngay 1 job đến hạn (khóa `FOR UPDATE SKIP LOCKED`, không nhận trùng với worker). Gọi Gemini |
| GET | `/api/generation/jobs` | Danh sách job |
| GET | `/api/reviews/questions` | Danh sách câu để chấm, mọi dạng (không có tham số lọc trạng thái) |
| GET | `/api/reviews/questions/{id}` | Form chấm một câu |
| POST | `/api/reviews/questions/{id}` | Nộp đánh giá |
| POST | `/api/reviews/demo/seed-math` | Tạo 5 câu `mcq` demo; đi qua format và dedup, không chạy answer_check, nên giữ `generated` |
| GET | `/api/bank-items?questionType=` | Ngân hàng câu hỏi đã duyệt, mới nhất trước; `questionType` (`mcq`, `tf`, `sa`) bỏ trống là mọi dạng |
| POST | `/api/exam-matrices` | Tạo ma trận đề bằng JSON (mục 7) |
| GET | `/api/exam-matrices`, `/api/exam-matrices/{id}` | Danh sách ma trận / chi tiết một ma trận |
| GET | `/api/exam-matrices/samples` | Các mẫu ma trận có sẵn (hiện có `giuaki1-toan8`) |
| POST | `/api/exam-matrices/samples/{key}` | Tạo ma trận từ mẫu có sẵn |
| POST | `/api/exams/build` | Tạo đề theo ma trận một lần bấm (chế độ giáo viên). Sinh câu qua hàng đợi, có thể tốn phí |
| GET | `/api/exams/{id}/progress` | Tiến độ theo ô |
| GET | `/api/exams/{id}` | Xem đề: câu theo phần I-III kèm đáp án, lời giải, cờ "cần xem kỹ" (KHÔNG chấm mù) |
| GET | `/api/exams/{id}/cells/{cellId}` | Các chỗ của một ô (tóm tắt) |
| POST | `/api/exams/{id}/slots/{slotId}/keep` | Giữ câu (vào ngân hàng) |
| POST | `/api/exams/{id}/slots/{slotId}/replace` | Đổi câu khác (sinh lại đúng ô), có thể kèm yêu cầu của giáo viên |
| GET | `/api/exams/{id}/export/docx` | Xuất đề ra Word (.docx): đề, ĐÁP ÁN, LỜI GIẢI; cần pandoc trên máy chủ |

Chạy lại QC cho câu `generated` (quy tắc QC-RERUN, quyết định 6 ngày 22/10) đã có ở tầng service (`QcRerunService`, cả ba
dạng) nhưng CHƯA có endpoint (chưa chốt ai được gọi Solver tính phí cho câu tùy ý).

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
- Môn chỉ được tạo qua seed hoặc trực tiếp trong DB; chưa có endpoint tạo môn. Một template dùng được cho cả ba dạng.

### Sinh câu đồng bộ

| Trường | `mcq` | `sa` | `tf` |
|---|---|---|---|
| `templateId` | bắt buộc | bắt buộc | bắt buộc |
| `questionType` | bỏ trống hoặc `mcq` | `sa` | `tf` |
| `strategy` | `zero-shot`, `few-shot-structured`... | bỏ trống hoặc `zero-shot`; khác thì 400 | bỏ trống hoặc `zero-shot`; khác thì 400 |
| `targetDifficulty` | tùy chọn (mặc định `easy`) | tùy chọn (mặc định `easy`) | **không gửi** (400) |
| `targetStatementDifficulties` | không gửi (khác null thì 400) | không gửi (khác null thì 400) | **bắt buộc**, đúng 4 mức cho ý a, b, c, d |
| `modelName`, `temperature`, `runAnswerCheck`, `rawLlmOutputOverride` | tùy chọn | tùy chọn | tùy chọn |

- Thử offline không tốn phí: truyền `rawLlmOutputOverride` là chuỗi JSON theo hợp đồng ở mục 4 của
  [`enums.md`](../contract/enums.md) (7 trường `mcq`, 3 trường `sa`, 10 trường `tf`) VÀ `"runAnswerCheck": false`.
  Nếu không tắt answer-check, backend vẫn gọi Solver Gemini.
- Response: `{"question": {...}, "qcReport": {...}}`. Response này dành cho người nghiên cứu, không phải form chấm: nó có
  kết quả QC. `question` không có đáp án, đáp số hay nhãn của LLM ở cả ba dạng.
- `question`: `id, templateId, runId, questionText, optionA..optionD, parseSuccess, status, tokenCount, latencyMs,
  createdAt, questionType`; câu `tf` giải mã được có thêm `statements: [{key, text}]` (không có nhãn). `optionA..optionD`
  là `null` với `sa` và `tf`.
- `qcReport`: `resultingStatus, formatPassed, formatErrors, answerCheckPassed, judgeAnswer, judgeModel, solverReasoning,
  answerCheckStatus, dedupStatus, dedup`, cộng các trường chỉ có khi khác rỗng:
  - `answerWarnings` (`sa`, `tf`): cảnh báo không chặn, ví dụ `answer: phân số chưa tối giản`, `label: 4 ý cùng nhãn T`,
    `statement_a: đã bỏ tiền tố "a)" ở đầu ý` (quy tắc bỏ tiền tố ở mục 4.3 của `enums.md`; dạng `a.` chỉ bỏ khi ký tự
    ngay sau dấu chấm không phải chữ cái, nên `a.b = 0` giữ nguyên còn `a. Điều kiện` thì bỏ). Từ V5 cảnh báo được
    lưu ở `quality_check_result.warnings` (dòng `format`), cả luồng job, và `answerWarnings` đọc lại từ cột đó;
  - `saJudgeKind`, `saJudgeAnswer` (`sa`): loại đáp số Solver trả (`number`, `ambiguous`, `unparseable`) và chuỗi gốc;
  - `tfJudgements` (`tf`): `[{key, judgeLabel}]`, nhãn Solver cho 4 ý (`T`, `F`, `undecidable`).
- `dedupStatus`: `NOT_RUN` (format lỗi), `NO_DUPLICATE`, `DUPLICATE`, `ERROR`. `dedup` gồm `status, similarity,
  comparedToQuestionId, thresholdUsed, layer, detail`; `layer` là `NONE`, `FINGERPRINT`, `TRIGRAM` hoặc (chỉ `tf`)
  `TF_STATEMENTS` (đề dẫn giống và ít nhất 3/4 ý giống nhau). Dedup chỉ so câu cùng dạng. Solver chỉ được gọi khi
  `dedupStatus = NO_DUPLICATE`; câu trùng có `status = duplicate`, dedup lỗi giữ `generated`.
- `answerCheckStatus`: `CONSISTENT` (Solver khớp; với `tf` là khớp đủ 4 ý), `INCONSISTENT`, `ERROR`, `NOT_RUN`. Câu chỉ
  đạt `qc_passed` khi `CONSISTENT`.

Câu `mcq` (không đổi so với trước):

```http
POST /api/generation/generate-sync
{"templateId": 3, "strategy": "few-shot-structured", "targetDifficulty": "easy", "runAnswerCheck": true}
```

Câu `sa`:

```http
POST /api/generation/generate-sync
{"templateId": 3, "questionType": "sa", "targetDifficulty": "medium", "runAnswerCheck": true}
```

```json
{
  "question": {
    "id": 101, "templateId": 3, "runId": 12,
    "questionText": "Cho tam giác $ABC$ vuông cân tại $A$ có $AB = AC = 3$. Tính độ dài cạnh $BC$.",
    "optionA": null, "optionB": null, "optionC": null, "optionD": null,
    "parseSuccess": true, "status": "qc_passed", "tokenCount": 512, "latencyMs": 2140.0,
    "createdAt": "2026-10-22T16:00:00", "questionType": "sa"
  },
  "qcReport": {
    "resultingStatus": "qc_passed", "formatPassed": true, "formatErrors": [],
    "answerCheckPassed": true, "judgeAnswer": null, "judgeModel": "<model Solver>",
    "solverReasoning": "Tam giác vuông cân nên $BC = AB\\sqrt{2} = 3\\sqrt{2}$.",
    "answerCheckStatus": "CONSISTENT", "dedupStatus": "NO_DUPLICATE", "dedup": {"status": "NO_DUPLICATE", "...": "..."},
    "saJudgeKind": "number", "saJudgeAnswer": "$3\\sqrt{2}$"
  }
}
```

Câu `tf`:

```http
POST /api/generation/generate-sync
{"templateId": 3, "questionType": "tf", "targetStatementDifficulties": ["easy", "easy", "medium", "hard"],
 "runAnswerCheck": true}
```

```json
{
  "question": {
    "id": 205, "templateId": 3, "runId": 13,
    "questionText": "Cho biểu thức $P = \\frac{x^2 - 1}{x^2 - x}$.",
    "optionA": null, "optionB": null, "optionC": null, "optionD": null,
    "parseSuccess": true, "status": "qc_passed", "tokenCount": 830, "latencyMs": 3310.0,
    "createdAt": "2026-10-22T16:05:00", "questionType": "tf",
    "statements": [
      {"key": "a", "text": "Điều kiện xác định của $P$ là $x \\neq 0$ và $x \\neq 1$."},
      {"key": "b", "text": "Với điều kiện xác định, $P = \\frac{x+1}{x}$."},
      {"key": "c", "text": "Tại $x = 2$ thì $P = 2$."},
      {"key": "d", "text": "Có đúng hai giá trị nguyên của $x$ để $P$ nhận giá trị nguyên."}
    ]
  },
  "qcReport": {
    "resultingStatus": "qc_passed", "formatPassed": true, "formatErrors": [],
    "answerCheckPassed": true, "judgeAnswer": null, "judgeModel": "<model Solver>",
    "solverReasoning": "a) ... b) ... c) ... d) ...",
    "answerCheckStatus": "CONSISTENT", "dedupStatus": "NO_DUPLICATE", "dedup": {"status": "NO_DUPLICATE", "...": "..."},
    "tfJudgements": [
      {"key": "a", "judgeLabel": "T"}, {"key": "b", "judgeLabel": "T"},
      {"key": "c", "judgeLabel": "F"}, {"key": "d", "judgeLabel": "F"}
    ]
  }
}
```

### Tạo job sinh câu

```http
POST /api/generation/jobs/enqueue
{"templateId": 3, "questionType": "tf", "targetStatementDifficulties": ["easy", "easy", "medium", "hard"], "count": 5}
```

- Cùng quy tắc trường như sinh đồng bộ (`strategy`, `targetDifficulty`, `targetStatementDifficulties` theo dạng câu);
  `count` bỏ trống là 1; giá trị ngoài khoảng 1-50 được đưa về 1 hoặc 50. Câu `mcq`: `{"templateId": 3, "strategy": "zero-shot", "targetDifficulty": "easy",
  "count": 5}`; câu `sa`: `{"templateId": 3, "questionType": "sa", "targetDifficulty": "medium", "count": 5}`.
- Trả về danh sách job vừa tạo; mỗi job có `questionType` và với `tf` thêm `targetStatementDifficulties` dạng chuỗi
  `"easy,easy,medium,hard"` (job `tf` có `targetDifficulty = null`).

### Form chấm

`GET /api/reviews/questions` trả danh sách: `id, templateId, reviewPurpose, subjectName, gradeLevel, topic, questionText,
createdAt, questionType`. Danh sách không có phương án hay ý; mở chi tiết để chấm.

`GET /api/reviews/questions/{id}` trả form chấm; trường `null` bị bỏ khỏi JSON:

```json
{"id": 42, "templateId": 3, "reviewPurpose": "production", "subjectName": "Toán", "gradeLevel": 8,
 "topic": "Căn bậc hai", "questionText": "Kết quả của phép tính $\\sqrt{(-5)^2}$ là:",
 "optionA": "$-5$", "optionB": "$5$", "optionC": "$25$", "optionD": "$\\pm 5$",
 "createdAt": "2026-10-22T15:00:00", "questionType": "mcq"}
```

```json
{"id": 101, "templateId": 3, "reviewPurpose": "production", "subjectName": "Toán", "gradeLevel": 8,
 "topic": "Định lí Pythagore",
 "questionText": "Cho tam giác $ABC$ vuông cân tại $A$ có $AB = AC = 3$. Tính độ dài cạnh $BC$.",
 "createdAt": "2026-10-22T16:00:00", "questionType": "sa"}
```

```json
{"id": 205, "templateId": 3, "reviewPurpose": "production", "subjectName": "Toán", "gradeLevel": 8,
 "topic": "Phân thức đại số", "questionText": "Cho biểu thức $P = \\frac{x^2 - 1}{x^2 - x}$.",
 "createdAt": "2026-10-22T16:05:00", "questionType": "tf",
 "statements": [
   {"key": "a", "text": "Điều kiện xác định của $P$ là $x \\neq 0$ và $x \\neq 1$."},
   {"key": "b", "text": "Với điều kiện xác định, $P = \\frac{x+1}{x}$."},
   {"key": "c", "text": "Tại $x = 2$ thì $P = 2$."},
   {"key": "d", "text": "Có đúng hai giá trị nguyên của $x$ để $P$ nhận giá trị nguyên."}
 ]}
```

### Nộp đánh giá

Quy tắc chung cho cả ba dạng:

- `decision`: `approved_as_is`, `approved_with_edit`, `rejected`. `formatOk` và `decision` bắt buộc.
- `rejected` cần ít nhất một `reasonCodes`; mã `other` cần `otherReasonDetail`. Mã dùng theo dạng câu: xem `ReasonCode` trong
  [`enums.md`](../contract/enums.md) (`statement_undecidable` chỉ `tf`, `answer_ambiguous` chỉ `sa`, mã về phương án chỉ `mcq`).
- Nên gửi `questionType`; khác dạng thật của câu thì 400 `Dữ liệu đánh giá không khớp dạng câu hỏi.`. Khi không gửi
  `questionType`, backend suy dạng từ body: có `statements` (khác null) là `tf`, có trường SA (khác null) là `sa`, còn lại là
  `mcq`; dạng suy ra khác dạng thật cũng trả 400 như trên.
- Trường thừa, một quy tắc cho cả ba dạng (kể cả khi body có `questionType`): trường thuộc dạng câu khác mà khác null
  trả 400 `Dữ liệu đánh giá không khớp dạng câu hỏi.`; trường đó mang giá trị `null` thì bị bỏ qua. Trường riêng của từng dạng:

  | Dạng | Trường riêng |
  |---|---|
  | `mcq` | `reviewerAnswer`, `finalOption`, `editedOptionA..D` |
  | `sa` | `reviewerSaKind`, `reviewerSaAnswer`, `finalSaAnswer` |
  | `tf` | `statements` (kể cả danh sách rỗng `[]`) |
  | `mcq` và `sa` | `difficultyLabel` cấp câu (câu `tf` chấm mức theo từng ý) |

  Ví dụ câu `mcq` gửi `"statements": []`, câu `sa` gửi `"finalOption": "A"`, câu `tf` gửi `"difficultyLabel": "easy"` đều trả
  400 như trên; gửi `"finalSaAnswer": null` cho câu `mcq` thì được chấp nhận.
- Request có `editedExplanation` khác null trả 400 với thông điệp trung tính (bước chấm không nhận sửa lời giải).
- Duyệt production chỉ nhận câu `qc_passed` hoặc `in_bank`; câu khác trả 400 với thông báo chung, không nêu trạng thái.
- Câu `duplicate` hoặc `format_failed` (cả hai purpose) trả 404 `{"error": "Không tìm thấy câu hỏi"}`, giống câu không tồn tại.
- Duyệt production thì câu vào ngân hàng (`in_bank`); lỗi ở bất kỳ bước nào làm rollback toàn bộ lần nộp.

Câu `mcq` (không đổi):

```http
POST /api/reviews/questions/42
{"reviewerId": 1, "reviewPurpose": "production", "reviewerAnswer": "B", "formatOk": true,
 "difficultyLabel": "easy", "decision": "approved_with_edit", "finalOption": "B",
 "editedQuestionText": "Kết quả của phép tính $\\sqrt{(-5)^2}$ là:"}
```

- `reviewerAnswer`: `A`..`D`, `multiple`, `none`. Duyệt thì bắt buộc `finalOption`; `rejected` thì `finalOption` phải trống.
- Response: `reviewId, questionId, reviewPurpose, reviewerAnswer, formatOk, difficultyLabel, decision, finalOption, edited,
  editDistance, questionStatus (chỉ production), bankItemId, reviewedAt`. Không có `answerCorrect`, `explanation`.

Câu `sa`:

```http
POST /api/reviews/questions/101
{"reviewerId": 1, "reviewPurpose": "production", "questionType": "sa", "formatOk": true,
 "difficultyLabel": "medium", "reviewerSaKind": "number", "reviewerSaAnswer": "√18",
 "decision": "approved_with_edit", "finalSaAnswer": "$3\\sqrt{2}$"}
```

```json
{"reviewId": 77, "questionId": 101, "reviewPurpose": "production", "reviewerAnswer": null, "formatOk": true,
 "difficultyLabel": "medium", "decision": "approved_with_edit", "finalOption": null, "edited": false,
 "editDistance": null, "questionStatus": "in_bank", "bankItemId": 31, "reviewedAt": "2026-10-22T16:20:00",
 "reviewerSaKind": "number", "reviewerSaAnswer": "√18", "finalSaAnswer": "$3\\sqrt{2}$"}
```

- Không gửi `reviewerAnswer`, `finalOption`, `editedOptionA..D`.
- `difficultyLabel` (cấp câu) bắt buộc. Sửa được đề qua `editedQuestionText`; `editDistance` chỉ tính trên đề, đổi đáp số
  không tính.
- Response chỉ lặp lại đáp số do giáo viên nhập; không có đáp số LLM, không có `answerCorrect`.

Câu `tf`:

```http
POST /api/reviews/questions/205
{"reviewerId": 1, "reviewPurpose": "production", "questionType": "tf", "formatOk": true,
 "decision": "approved_with_edit",
 "statements": [
   {"key": "a", "reviewerLabel": "T", "difficultyLabel": "easy", "finalLabel": "T"},
   {"key": "b", "reviewerLabel": "T", "difficultyLabel": "easy", "finalLabel": "T"},
   {"key": "c", "reviewerLabel": "F", "difficultyLabel": "medium", "finalLabel": "F"},
   {"key": "d", "reviewerLabel": "undecidable", "difficultyLabel": "hard", "finalLabel": "T",
    "editedText": "Có đúng một giá trị nguyên của $x$ để $P$ nhận giá trị nguyên."}
 ]}
```

```json
{"reviewId": 78, "questionId": 205, "reviewPurpose": "production", "reviewerAnswer": null, "formatOk": true,
 "difficultyLabel": null, "decision": "approved_with_edit", "finalOption": null, "edited": true, "editDistance": 3,
 "questionStatus": "in_bank", "bankItemId": 32, "reviewedAt": "2026-10-22T16:25:00",
 "statements": [
   {"key": "a", "reviewerLabel": "T", "difficultyLabel": "easy", "finalLabel": "T"},
   {"key": "b", "reviewerLabel": "T", "difficultyLabel": "easy", "finalLabel": "T"},
   {"key": "c", "reviewerLabel": "F", "difficultyLabel": "medium", "finalLabel": "F"},
   {"key": "d", "reviewerLabel": "undecidable", "difficultyLabel": "hard", "finalLabel": "T"}
 ]}
```

- Không gửi `reviewerAnswer`, `finalOption`, `difficultyLabel` (cấp câu), `editedOptionA..D`, các trường SA.
- `statements` phải có đủ 4 phần tử, mỗi `key` a-d đúng một lần (thứ tự gửi tùy ý; response luôn theo a-d).
- Sửa được đề dẫn (`editedQuestionText`) và nội dung từng ý (`editedText`, chỉ khi `approved_with_edit`); `editDistance`
  tính trên đề dẫn cộng 4 ý, đổi nhãn không tính.
- Response lặp lại nhãn, mức và nhãn cuối do giáo viên nhập; không có nhãn LLM và không có đúng/sai từng ý.

### Ngân hàng câu hỏi

`GET /api/bank-items` trả mỗi câu với 12 key cũ luôn có mặt (kể cả khi `null`): `id, sourceQuestionId, reviewId,
questionText, optionA..optionD, correctOption, explanation, difficulty, createdAt`, cộng `questionType`. Câu `sa` thêm
`saAnswer` (đáp số cuối giáo viên chốt); câu `tf` thêm `statements` (4 ý theo thứ tự a-d) và `difficulty` cấp câu là `null`:

```json
{"id": 32, "sourceQuestionId": 205, "reviewId": 78, "questionText": "Cho biểu thức $P = \\frac{x^2 - 1}{x^2 - x}$.",
 "optionA": null, "optionB": null, "optionC": null, "optionD": null, "correctOption": null,
 "explanation": "a) ... b) ... c) ... d) ...", "difficulty": null, "createdAt": "2026-10-22T16:25:00",
 "questionType": "tf",
 "statements": [
   {"key": "a", "text": "Điều kiện xác định của $P$ là $x \\neq 0$ và $x \\neq 1$.", "label": "T", "difficulty": "easy"},
   {"key": "b", "text": "Với điều kiện xác định, $P = \\frac{x+1}{x}$.", "label": "T", "difficulty": "easy"},
   {"key": "c", "text": "Tại $x = 2$ thì $P = 2$.", "label": "F", "difficulty": "medium"},
   {"key": "d", "text": "Có đúng một giá trị nguyên của $x$ để $P$ nhận giá trị nguyên.", "label": "T", "difficulty": "hard"}
 ]}
```

Ngân hàng là dữ liệu đã duyệt, không phải form chấm, nên có đáp án, đáp số và nhãn cuối.

## 3. Trường bị giấu khỏi form chấm

Form chấm gồm `GET /api/reviews/questions`, `GET /api/reviews/questions/{id}` và response sau khi nộp, cả production lẫn
experiment. Test `OpenApiContractTest` chặn các trường này trong schema (quét cả schema lồng nhau, kể cả trong
`statements`); `BlindReviewContractTest` và các test luồng `sa`/`tf` quét mọi key của JSON thật.

| Dạng | Không bao giờ có |
|---|---|
| Mọi dạng | `explanation` (lời giải), `llmDifficulty`, `answerCorrect`, chiến lược sinh (`strategy`), `status`, `latexValid`, `latexErrors`, `qcReport` và mọi kết quả QC: answer-check (`judgeAnswer`, `judgeModel`, `answerCheckStatus`), dedup (`dedupStatus`, `similarityScore`, `comparedToQuestionId`, `thresholdUsed`) |
| `mcq` | `correctOption` (đáp án LLM) |
| `sa` | `saAnswer` (đáp số LLM), kết quả Solver (`judgeSaKind`, `judgeSaAnswer`), `answerWarnings` |
| `tf` | Nhãn LLM của từng ý (`label`, `llmLabel`), mức của từng ý (`difficulty`, `llmDifficulty`, `finalDifficulty`), nhãn Solver (`judgeLabel`, `tfJudgements`), đúng/sai từng ý do server tính (`labelCorrect`). Mỗi phần tử `statements` chỉ có `key` và `text` |

- **Phản hồi nộp production** giữ `questionStatus` và `bankItemId` (kết quả hành động của giáo viên); experiment không có
  `questionStatus`.
- **Vòng chấm:** danh sách production chỉ gồm câu `qc_passed` (câu chưa được chấm); danh sách experiment loại `duplicate`
  và `format_failed`, không lọc theo kết quả answer-check. Danh sách không nhận `?status=` (tham số lạ bị bỏ qua) để không
  suy ra được kết quả QC.
- **Sửa bài:** `mcq` sửa đề và 4 phương án (`editDistance` trên 5 trường đó); `sa` sửa đề (`editDistance` trên đề); `tf` sửa đề
  dẫn và 4 ý (`editDistance` trên 5 trường đó). Không dạng nào nhận sửa lời giải.

Trạng thái câu (`status`): `generated`, `format_failed`, `duplicate`, `qc_passed`, `rejected`, `in_bank`.
Câu sinh thật chỉ đạt `qc_passed` khi format đạt, dedup không trùng và answer_check (Solver Gemini) chạy thành công và khớp
(`mcq`: đáp án; `sa`: đáp số theo giá trị; `tf`: cả 4 nhãn). Dedup chưa khóa theo phạm vi, nên tránh sinh song song cùng môn.

## 4. Đáp số câu Trả lời ngắn

Đáp số là MỘT giá trị số. Backend phân tích chuỗi thành giá trị chính xác dạng k·√n (k là phân số tối giản, n không chứa
thừa số chính phương) và so theo giá trị, không so chuỗi và không làm tròn.

Trước khi phân tích: trim; tối đa 100 ký tự; bỏ cặp `$...$` hoặc `$$...$$` bao cả chuỗi; đổi `−` (U+2212) thành `-`,
`{,}` thành `,`, `\cdot` thành `·`, `\dfrac`/`\tfrac` thành `\frac`; bỏ mọi khoảng trắng.

| Dạng | Ví dụ được nhận | Ghi chú |
|---|---|---|
| Số nguyên | `5`, `-12`, `350 000` | Khoảng trắng bị bỏ, `350 000` là 350000 |
| Thập phân dấu phẩy | `-0,75`, `0,50`, `1,500` | Dấu phẩy luôn là dấu thập phân: `1,500` là 1,5 |
| Thập phân dấu chấm | `1.5`, `1.25`, `1.2345`, `0.125`, `-0.125` | Xem quy tắc dấu chấm hàng nghìn bên dưới |
| Phân số | `1/2`, `\frac{1}{2}`, `-\frac{3}{4}`, `\frac{-3}{4}`, `2/4` | Mẫu số 0 bị từ chối. Phân số chưa tối giản hợp lệ |
| Căn | `\sqrt{12}`, `√12`, `√{12}`, `-√3` | Số dưới căn nguyên dương, tối đa 10^12; `\sqrt{12}` là `2\sqrt{3}` |
| Số nhân căn | `2\sqrt{3}`, `2·√3`, `2\cdot\sqrt{3}`, `0,5\sqrt{3}` | |
| Phân số có căn | `\frac{\sqrt{3}}{2}`, `\frac{1}{2}\sqrt{3}`, `\frac{2}{\sqrt{3}}`, `\frac{\sqrt{8}}{\sqrt{2}}` | `\frac{2}{\sqrt{3}}` bằng `\frac{2\sqrt{3}}{3}` |

Không nhận (không phân tích được): `x = 2`, `2 cm`, `\pi`, `2^3`, `1 + \sqrt{2}` (tổng), `+5`, `--3`, `,5`, `5,`, `1,5,6`,
`1/2√3` (mơ hồ), `\sqrt3` (thiếu ngoặc), `\sqrt{2.5}`, `\sqrt{0}`, `\sqrt{-3}`.

So theo giá trị: `0,5` = `1/2` = `$\frac{1}{2}$`; `\sqrt{4}` = `2`; nhưng `0,33` ≠ `1/3` và `1,41` ≠ `\sqrt{2}`.

**Dấu chấm hàng nghìn.** Dấu chấm theo sau đúng 3 chữ số có thể là phân cách hàng nghìn kiểu Việt Nam (`1.500` là 1500 hay
1,5?), nên bị từ chối với thông báo `dấu chấm có thể là phân cách hàng nghìn`: `1.500`, `-12.000`, `350.000`, `1.500.000`,
`1.125`, `10.125`, `1.000,5`. Miễn khi phần nguyên là `0` (kể cả `-0`): `0.125` là 1/8, `-0.125` là -1/8. Muốn viết 1500
thì nhập `1500` hoặc `1 500`; muốn viết 1,125 thì nhập `1,125`. Dấu chấm theo sau số chữ số khác 3 là dấu thập phân
(`1.5`, `1.25`, `1.2345`).

**Hai chế độ:**

| Chế độ | Áp dụng cho | Chặn thêm |
|---|---|---|
| Chặt (STRICT) | `answer` do LLM sinh (bước format); đáp số cuối vào ngân hàng: `finalSaAnswer`, hoặc `reviewerSaAnswer` khi chọn `approved_as_is` | Chữ số 0 thừa ở đầu (`05`, `-007,5`, `\frac{05}{2}`, `\sqrt{012}`) và số 0 mang dấu âm (`-0`, `-0,0`, `-\frac{0}{3}`) |
| Rộng (LENIENT) | `reviewerSaAnswer` khi chấm mù; `judge_answer` của Solver | Chỉ cần phân tích được |

- Khi đáp số cuối có chữ số 0 thừa, thông báo nêu cách sửa, tính từ chính chuỗi giáo viên nhập: `Đáp số có chữ số 0 thừa ở
  đầu, hãy nhập 5`. Lỗi chế độ chặt khác có dạng `Đáp số cuối không hợp lệ: <lý do>`.
- `reviewerSaAnswer` không phân tích được trả 400 `Đáp số giáo viên nhập không đọc được thành một số.`
- Server so `reviewerSaAnswer` với đáp số LLM theo giá trị để ra `answerCorrect` (không trả về). `reviewerSaKind = ambiguous`
  luôn tính là lệch.

## 5. Hướng dẫn frontend cho form chấm `sa` và `tf`

Chung: dựng form theo `questionType` của chi tiết câu; gửi lại `questionType` khi nộp. Form là chấm mù: không có và không
được tìm cách hiện đáp án, đáp số, nhãn hay mức của LLM, kể cả sau khi nộp. Hiện nguyên thông điệp `error` khi nhận 400.
Mức độ dùng `easy`, `medium`, `hard` (`Difficulty`).

### Câu Trả lời ngắn (`sa`)

1. Hiện `questionText` (render LaTeX như mục 6). Không có phương án.
2. Chọn `reviewerSaKind`: "Một đáp số" (`number`) kèm ô nhập `reviewerSaAnswer`, hoặc "Đề có nhiều cách hiểu" (`ambiguous`),
   khi đó ô đáp số phải để trống (gửi `null`).
3. Ô nhập đáp số nhận các dạng ở mục 4. Nên gợi ý dùng dấu phẩy thập phân (`-0,75`) để tránh lỗi dấu chấm hàng nghìn.
   Frontend có thể kiểm sơ bộ, nhưng backend là nguồn chuẩn.
4. Chọn `difficultyLabel` (cấp câu), `formatOk`, `decision`.
5. Theo `decision`:
   - `approved_as_is`: không cho chọn khi `reviewerSaKind = ambiguous`. `finalSaAnswer` bỏ trống hoặc trùng `reviewerSaAnswer`;
     đáp số này phải qua chế độ chặt (nếu giáo viên nhập `05`, backend trả 400 kèm gợi ý nhập `5`).
   - `approved_with_edit`: bắt buộc ô `finalSaAnswer` (chế độ chặt); có thể sửa đề (`editedQuestionText`).
   - `rejected`: `finalSaAnswer` để trống; bắt buộc chọn lý do (có `answer_ambiguous`; không có các mã về phương án).
6. Không gửi `reviewerAnswer`, `finalOption`, `editedOptionA..D`, `statements`, `editedExplanation`.

### Câu Đúng/Sai (`tf`)

1. Hiện `questionText` là đề dẫn, rồi 4 ý theo `statements` (key `a`-`d`). Frontend tự thêm nhãn "a)", "b)"... khi hiển thị;
   `text` không chứa tiền tố này.
2. Mỗi ý có hai lựa chọn bắt buộc:
   - `reviewerLabel`: Đúng (`T`), Sai (`F`), Không kết luận được (`undecidable`);
   - `difficultyLabel`: mức độ của riêng ý đó.
   Không có mức độ cấp câu: không gửi `difficultyLabel` ở cấp ngoài.
3. Chọn `formatOk`, `decision`.
4. Theo `decision`:
   - `approved_as_is`: không cho chọn khi có ý `undecidable`. `finalLabel` bỏ trống (server lấy theo `reviewerLabel`) hoặc
     trùng `reviewerLabel`.
   - `approved_with_edit`: bắt buộc `finalLabel` (`T`/`F`) cho cả 4 ý, kể cả ý không sửa; có thể sửa đề dẫn
     (`editedQuestionText`) và từng ý (`editedText`).
   - `rejected`: `finalLabel` của mọi ý để trống; bắt buộc chọn lý do (có `statement_undecidable`; không có các mã về phương
     án và `answer_ambiguous`).
5. Luôn gửi đủ 4 phần tử `statements`, mỗi `key` một lần. Thiếu ý, trùng ý hoặc thiếu `reviewerLabel`/`difficultyLabel` của
   một ý đều trả 400 nêu ý đó, ví dụ `Ý c: phải chọn difficultyLabel`.
6. Không gửi `reviewerAnswer`, `finalOption`, `difficultyLabel` cấp câu, `editedOptionA..D`, `reviewerSaKind`,
   `reviewerSaAnswer`, `finalSaAnswer`, `editedExplanation`.

## 6. Định dạng công thức toán

**Sửa đầu ra LLM ở run production (ngày 23/10).** Trước khi giải mã JSON, dấu `\` của lệnh LaTeX chưa nhân đôi trong
chuỗi được nhân đôi (ví dụ `"$75^\circ$"` một dấu gạch); sau khi giải mã, trường có `$` lẻ mà phần sau `$` cuối trông
như công thức được tự đóng `$` ở cuối trường, trước dấu câu. Mỗi lần sửa ghi cảnh báo vào `answerWarnings`/`warnings`
(`json: đã sửa 1 escape LaTeX (\circ)`, `statement_b: đã tự đóng $`). `$` lẻ ở giữa câu vẫn là lỗi format. MCQ
production gọi Gemini kèm schema JSON như `sa`/`tf`. Lý do format không qua (kể cả lỗi giải mã JSON) lưu ở
`quality_check_result.errors` (V8). Run `experiment` không áp dụng; chi tiết ở `enums.md` mục 3.

Các trường văn bản (`questionText`, `optionA`..`optionD`, `statements[].text`, đáp số `sa`, `explanation`) là văn bản tiếng
Việt xen công thức LaTeX:

- `$...$` là công thức trong dòng; `$$...$$` là công thức hiển thị riêng dòng.
- `\$` ngoài công thức là dấu đô la thường, không mở công thức.
- Môi trường được kiểm tra: `matrix`, `cases`, `aligned`, đặt trong công thức; `\\` là xuống hàng trong môi trường.
  Ví dụ hệ phương trình: `$\left\{\begin{matrix} 2x + y = 5 \\ x - y = 1 \end{matrix}\right.$`.
- Ký tự xuống dòng thật chỉ hợp lệ ngoài công thức, trong `$$...$$` hoặc trong môi trường.
- Trong JSON, mỗi dấu `\` của LaTeX được viết thành `\\`, ví dụ `"$\\frac{1}{2}$"`.
- JSON có khóa trùng trong cùng một đối tượng (ví dụ hai khóa `option_c` hay hai khóa `label_a`) bị coi là lỗi định dạng:
  câu thành `format_failed`, thông báo nêu tên khóa trùng; backend không lấy giá trị đầu hay giá trị sau. Phản hồi Solver
  có khóa trùng (ví dụ hai khóa `judge_answer`) cho `answerCheckStatus = ERROR`, câu giữ `generated`.

Backend kiểm tra cú pháp bề mặt, không bảo đảm KaTeX render được:

| Mức | Trường hợp | Hệ quả |
|---|---|---|
| Lỗi | Ký tự điều khiển, số dấu `$` lẻ, ngoặc `{}` không cân bằng, lệnh nằm ngoài `$...$`, thiếu đối số `\frac`/`\sqrt`, `\left`/`\right` hoặc `\begin`/`\end` không khớp, ở đề, 4 phương án (`mcq`), 4 ý (`tf`) hoặc đáp số (`sa`) | Câu sinh ra thành `format_failed`; bản sửa của giáo viên bị từ chối (400) |
| Cảnh báo | Lệnh nằm ngoài danh sách cho phép, ví dụ `\widehat`, `\alpha`; môi trường ngoài danh sách (ví dụ `array`) khi cấu trúc cân bằng; mọi lỗi định dạng trong `explanation` | Không ảnh hưởng trạng thái, không trả về API. Lời giải còn lỗi nghiêm trọng được lưu `explanation = null` khi vào ngân hàng |

Trước khi kiểm tra, backend đổi chuỗi `\n` theo nghĩa đen (dấu `\` rồi chữ `n`, do model viết `\\n` trong JSON)
thành ký tự xuống dòng ở các trường văn bản (đề, phương án, ý, lời giải; đáp số `sa` chỉ trim), chỉ khi nằm ngoài
`$...$`/`$$...$$`. Trong công thức giữ nguyên (`\neq`, `\nu`...);
ngoài công thức, `\neq`, `\ne`, `\notin`, `\not`, `\nu`, `\nabla`, `\neg`, `\ni` cũng giữ nguyên để vẫn bị báo "lệnh nằm ngoài $...$".
`raw_llm_output` lưu nguyên bản gốc.

Gợi ý render phía frontend: tách chuỗi theo `$$...$$` và `$...$` (bỏ qua `\$`), render từng đoạn công thức bằng KaTeX
(`displayMode` cho `$$`), để `throwOnError: false` hoặc bắt lỗi từng đoạn và hiện chuỗi gốc khi không render được.
KaTeX ở chế độ `strict: "error"` có thể từ chối chữ tiếng Việt nằm trong công thức; backend không kiểm tra điều này.

## 7. Tạo đề theo ma trận (chế độ giáo viên, V6-V7)

Luồng dành cho giáo viên ra đề, production. **Không chấm mù**: API `/api/exams/**` trả đáp án, lời giải và cờ "cần xem
kỹ" (quyết định 22/10, `docs/ai-development-rules.md`). Form chấm mù `/api/reviews/**` và chế độ experiment không đổi.

Cách dùng: chọn hoặc tạo ma trận → `POST /api/exams/build` → gọi `GET /api/exams/{id}/progress` vài giây một lần
cho tới khi `status` khác `generating` → `GET /api/exams/{id}` để xem đề → Giữ hoặc Đổi từng câu.

**Quyền (W1-1):** chỉ vai trò `teacher`. Ma trận và đề thuộc người tạo (lấy từ token, không gửi `teacherId`); ma trận,
đề của người khác trả 404 giống id không tồn tại. Admin gọi các API này nhận 403.

### 7.0. Đề của tôi

```http
GET /api/exams?page=0&size=10
→ {"items": [{"examId": 12, "examCode": "MT261022213000123", "title": "Đề giữa kì I", "mode": "teacher",
              "status": "draft", "matrixId": 3, "createdAt": "2026-10-22T21:30:00", "totalSlots": 18,
              "readySlots": 10, "keptSlots": 5, "needsReviewSlots": 2, "generatingSlots": 2, "failedSlots": 1}],
    "page": 0, "size": 10, "totalItems": 1, "totalPages": 1}
```

- Chỉ đề do người gọi tạo, mới nhất trước. `page` từ 0 (mặc định 0), `size` từ 1 đến 50 (mặc định 10); sai thì 400.
- `readySlots`: chỗ đã có câu, chưa Giữ (`ready`); `keptSlots`: đã Giữ (`kept`, `edited`); `needsReviewSlots`: trong số
  `readySlots`, câu hệ thống sinh có cờ "cần xem kỹ" (cùng cách tính `needsReview` ở 7.4); `generatingSlots`: `pending`,
  `generating`; `failedSlots`: `failed`.
- Danh sách chỉ đọc, KHÔNG đối soát job (khác 7.3/7.4): số liệu theo lần đối soát gần nhất. Muốn số mới nhất của một đề
  thì gọi `GET /api/exams/{id}/progress`.
- **Việc còn nợ (chưa làm):** `needsReviewSlots` đang tính N+1: mỗi chỗ `ready` có câu hệ thống sinh gọi
  `QcSummaryService.summarize` (vài truy vấn `quality_check_result`, `answer_check_statement` mỗi câu). Sẽ thay bằng một
  truy vấn gộp theo danh sách câu ở task thống kê; trước đó giữ `size` nhỏ (mặc định 10, tối đa 50).

### 7.1. Ma trận

```http
POST /api/exam-matrices
{"name": "Đề thử", "subjectId": 1, "gradeLevel": 8, "notes": ["ghi chú"], "cells": [
  {"part": "I", "questionType": "mcq", "difficulty": "easy", "questionCount": 2, "topic": "Đa thức",
   "content": "Đơn thức, đa thức", "learningOutcomes": {"easy": "Nhận biết được đơn thức, đa thức."}},
  {"part": "II", "questionType": "tf", "statementDifficulties": ["easy", "easy", "medium", "hard"], "questionCount": 1,
   "topic": "Hằng đẳng thức", "learningOutcomes": {"easy": "Nhận biết.", "medium": "Khai triển.", "hard": "Vận dụng."}},
  {"part": "III", "questionType": "sa", "difficulty": "medium", "questionCount": 1, "topic": "Tứ giác",
   "learningOutcomes": {"medium": "Tính được góc trong tứ giác."}}]}
```

```json
{"id": 3, "name": "Đề thử", "subjectId": 1, "gradeLevel": 8, "source": null, "notes": ["ghi chú"],
 "createdAt": "2026-10-22T21:30:00",
 "cells": [{"id": 11, "position": 1, "part": "I", "questionType": "mcq", "topic": "Đa thức", "content": "Đơn thức, đa thức",
            "learningOutcomes": {"easy": "Nhận biết được đơn thức, đa thức."}, "difficulty": "easy",
            "questionCount": 2, "templateId": 41}],
 "totals": {"questionsByPart": {"I": 2, "II": 1, "III": 1}, "commandsByLevel": {"easy": 4, "medium": 2, "hard": 1},
            "totalCommands": 7}}
```

- Phần I chỉ chứa `mcq`, phần II chỉ `tf`, phần III chỉ `sa` (DB cũng kiểm). `mcq`/`sa` cần `difficulty`; `tf` cần
  `statementDifficulties` gồm đúng 4 mức (ý a-d) và không có `difficulty`. `questionCount` từ 1 đến 50.
- `learningOutcomes` chỉ nhận khóa `easy`, `medium`, `hard` và phải có yêu cầu cần đạt cho mọi mức ô dùng.
- Mỗi ô tạo một `question_template` (`templateId`) để dùng lại pipeline sinh câu.
- `totals.commandsByLevel` đếm lệnh hỏi: mỗi câu `mcq`/`sa` một lệnh hỏi, mỗi câu `tf` 4 lệnh hỏi theo mức của 4 ý.
- Lỗi dữ liệu trả 400 nêu số ô, ví dụ `Ô 2: câu đúng/sai phải có statementDifficulties gồm đúng 4 mức`; `subjectId`
  không tồn tại trả 404. Lỗi thì không tạo gì (kể cả template).
- Mẫu có sẵn: `GET /api/exam-matrices/samples`; `POST /api/exam-matrices/samples/giuaki1-toan8` với body `{}` (dùng môn
  "Toán") hoặc `{"subjectId": 1}`. Mẫu "Giữa kì I – Toán 8" đọc từ `docs/sample/matrix_giuaki1_toan8.json` (bản chép
  trong `back_end/src/main/resources/samples/`, có test so khớp), giữ nguyên `notes`; tổng I=12, II=2, III=4.

### 7.2. Tạo đề một lần bấm

```http
POST /api/exams/build
{"matrixId": 3, "title": "Đề giữa kì I"}
→ {"examId": 12, "examCode": "MT261022213000123", "title": "Đề giữa kì I", "matrixId": 3, "slots": 18, "fromBank": 5, "jobsQueued": 13}
```

- `title` bỏ trống thì lấy tên ma trận. Người tạo đề là người đăng nhập (`teacherId` đã bỏ khỏi API từ W1-1; gửi lên
  thì bị bỏ qua). Ma trận phải của chính người gọi, không thì 404. Đề có `mode = teacher`, `status = draft`.
- Ngân hàng câu dùng chung cho mọi giáo viên (H1): câu ngân hàng khớp ô được lấy bất kể ai đã đưa vào.
- Mỗi ô lấy câu ngân hàng khớp ô trước: cùng môn, cùng dạng, cùng mức (`tf`: cùng 4 mức theo thứ tự), cùng chủ đề của
  template gốc (so sau trim, không phân biệt hoa thường); nếu câu ngân hàng được sinh từ một ô ma trận thì còn phải cùng
  nội dung. Câu chưa nằm trong đề nào được ưu tiên; không lấy trùng câu trong cùng một đề.
- Phần thiếu: mỗi ô một run production, zero-shot, `prompt_version` `matrix-mcq-v1-draft` / `matrix-tf-v2-draft` /
  `matrix-sa-v1-draft` (run trước ngày 23/10 là `v0`, `tf` có thêm `v1`; prompt nháp `MatrixPromptDraftV0`: sinh từ chủ đề, nội dung, yêu cầu cần đạt, không có câu
  mẫu; quy tắc đa dạng và danh sách tối đa 10 câu đã sinh của ô; chủ đề hình học như Tứ giác dặn câu phải làm được
  không cần hình vẽ). Mỗi chỗ một job, giãn cách 13 giây. Job chạy bằng worker (`app.worker.enabled=true`) hoặc
  `POST /api/generation/jobs/process-next`. Một đề 18 câu khi ngân hàng còn trống mất khoảng 7-8 phút trên Free Tier.
- Mỗi job của một chỗ có ghi chú riêng trong prompt (`generation_job.prompt_note`, V8): "Đây là câu thứ k/n của ô
  này" (dạng hỏi khác các câu còn lại, phủ các ý khác nhau của yêu cầu cần đạt) khi ô có từ 2 câu; khi sinh lại sau
  một lần `format_failed`, thêm "Lần sinh trước bị loại vì: <lỗi>. Tránh lặp lại lỗi này." (tối đa 300 ký tự). Prompt
  dựng lúc worker nhận job nên có cả câu đã sinh của các chỗ trước trong cùng ô.
- Câu `tf`: ghi chú có thêm mẫu nhãn bắt buộc, ví dụ "Mẫu nhãn bắt buộc (T,F,F,T): ý a phải Đúng, ý b phải Sai, ...",
  chọn ngẫu nhiên trong các mẫu có 1–3 ý Đúng và khác mẫu của câu `tf` gần nhất cùng đề (cả khi sinh lại). Prompt bỏ
  dòng "không bắt buộc số ý Đúng" và, khi ý d ở mức Vận dụng, dặn ý d phải cần suy luận thêm, không chỉ là hệ quả trực
  tiếp của ý c. Nhãn trả về khác mẫu thì câu vẫn dùng được, kèm cảnh báo `label: khác mẫu bắt buộc ...` và cờ "cần xem kỹ".
- Mỗi lần gọi tiến độ, xem đề hoặc thao tác, backend đối soát các job đã xong:
  - câu `qc_passed`: chỗ đó có câu;
  - câu lệch Solver (trạng thái `generated`): sinh lại 1 lần; vẫn lệch thì dùng câu đó, kèm cờ "cần xem kỹ";
  - câu trùng, lỗi định dạng hoặc job lỗi: sinh lại, tối đa 3 lần mỗi chỗ; hết lượt thì dùng câu lệch Solver đã có (nếu
    có), không thì chỗ đó `failed`.
- Dedup của câu sinh ra dùng phạm vi production hiện có (so với câu `qc_passed`, `in_bank` cùng môn). Văn bản đã sửa
  trong `bank_item` chưa được đưa vào dedup.

### 7.3. Tiến độ

```json
{"examId": 12, "examCode": "MT261022213000123", "title": "Đề giữa kì I", "status": "generating", "total": 18,
 "ready": 9, "generating": 9, "failed": 0, "fromBank": 5,
 "cells": [{"cellId": 11, "position": 1, "part": "I", "questionType": "mcq", "topic": "Đa thức", "content": "Đơn thức, đa thức",
            "needed": 4, "fromBank": 2, "ready": 3, "generating": 1, "failed": 0}]}
```

`status`: `generating` (còn chỗ đang sinh), `ready` (đủ câu), `partial` (hết việc nhưng có chỗ `failed`, dùng nút Đổi câu
khác để sinh lại).

### 7.4. Xem đề

```json
{"examId": 12, "examCode": "MT261022213000123", "title": "Đề giữa kì I", "mode": "teacher", "status": "draft",
 "matrixId": 3, "createdAt": "2026-10-22T21:30:00", "buildStatus": "ready", "needsReviewCount": 1,
 "parts": [{"part": "III", "required": 4, "ready": 4, "items": [
   {"slotId": 101, "cellId": 21, "slotNo": 1, "part": "III", "topic": "Tứ giác", "questionType": "sa", "state": "ready",
    "source": "generated", "questionId": 345, "questionText": "Tứ giác $ABCD$ có ba góc…", "saAnswer": "90",
    "explanation": "Tổng bốn góc của tứ giác là 360 độ…", "difficulty": "medium",
    "needsReview": true, "reviewReasons": ["solver_inconsistent"]}]}]}
```

- Phần luôn theo thứ tự I, II, III; trong phần theo thứ tự ô rồi số thứ tự câu. `required`/`ready` để kiểm đủ câu.
- Mỗi câu có nội dung đầy đủ theo dạng: `mcq` có `optionA..optionD`, `correctOption`; `sa` có `saAnswer`; `tf` có
  `statements: [{key, text, label, difficulty}]`; cùng `explanation`, `difficulty` (`tf` để trống, mức ở từng ý).
  Chỗ chưa có câu chỉ có phần định danh và `state` (`generating`, `failed`).
- `source`: `bank` (lấy từ ngân hàng) hoặc `generated`. Câu từ ngân hàng và câu đã Giữ hiển thị bản trong ngân hàng,
  không gắn cờ.
- `reviewReasons`: `solver_inconsistent` (Solver lệch), `solver_ambiguous`, `solver_unparseable` (`sa`),
  `statement_undecidable` (`tf`), `solver_not_run` (Solver chưa chạy hoặc lỗi), `dedup_not_run`, và
  `warning: <cảnh báo format>` (ví dụ `warning: label: 4 ý cùng nhãn T`, `warning: option_a: đã tự đóng $`,
  `warning: label: khác mẫu bắt buộc T,F,F,T (nhận T,T,F,F)`). Cảnh báo `json: đã sửa ... escape LaTeX` không phải lý
  do (vẫn lưu trong `warnings`). `needsReview` đúng khi danh sách khác rỗng.

### 7.5. Giữ / Đổi câu khác

```http
POST /api/exams/12/slots/101/keep                    → câu (như 7.4) với "state": "kept", "bankItemId": 88
POST /api/exams/12/slots/104/replace                 → câu với "state": "generating" (chưa có nội dung)
POST /api/exams/12/slots/104/replace  {"instruction": "Dùng ngữ cảnh tính tiền mua vở"}
```

- Không cần body (Đổi có thể kèm `instruction`). Người thao tác (`exam_slot_action.actor_id`, `teacher_review.reviewer_id`
  khi Giữ) là người đăng nhập.
- **Giữ:**
  - câu hệ thống sinh được chép vào ngân hàng: ghi `teacher_review` production với `review_mode = 'assisted'` (duyệt
    nguyên trạng theo đáp án/nhãn của LLM) và `bank_item` trong cùng transaction; câu chuyển `in_bank`;
  - câu lệch Solver cũng Giữ được, vì giáo viên đã thấy cờ;
  - câu lấy từ ngân hàng thì chỉ đánh dấu `kept`, không tạo đánh giá mới;
  - Giữ lần hai trả 409 `Câu hỏi này đã được giữ.`; chỗ chưa có câu trả 409.
- **Đổi câu khác** (dùng được cả cho chỗ `failed`, khi đó đặt lại số lần thử): bỏ câu hiện tại khỏi chỗ (câu cũ giữ nguyên trạng thái; câu đã Giữ vẫn ở ngân hàng) và sinh lại đúng
  ô qua hàng đợi; prompt liệt kê các câu đã sinh của ô, kể cả câu vừa bị đổi. Chỗ đang sinh trả 409.
- **Yêu cầu của giáo viên khi Đổi** (`instruction`, tùy chọn, ngày 23/10): trim, tối đa 500 ký tự (dài hơn trả 400
  `instruction dài tối đa 500 ký tự (hiện N)`), trống thì như không gửi. Yêu cầu được ghép vào cuối
  `generation_job.prompt_note` của job sinh lại (sau dòng câu thứ k/n, mẫu nhãn `tf`, lý do bị loại lần trước) và đặt ở
  cuối prompt thành một khối riêng: "Yêu cầu bổ sung của giáo viên (chỉ dùng để chọn nội dung, ngữ cảnh, cách hỏi;
  KHÔNG thay đổi dạng câu, mức độ, số ý, mẫu nhãn, định dạng JSON và các quy tắc ở trên): <<< ... >>>". Trong khối,
  `<<<`/`>>>` bị bỏ và xuống dòng gộp thành dấu cách; mẫu nhãn chỉ đọc từ phần ghi chú hệ thống. Yêu cầu chỉ dùng cho
  lượt Đổi đó (gồm các lần thử tự động khi lần sinh ấy bị loại), không thành ghi chú cố định của ô; lượt Đổi sau không
  mang theo. Lưu ở `exam_slot_action.instruction` (V9).
- Chỗ không thuộc đề trong đường dẫn trả 404.
- Mỗi thao tác ghi `exam_slot_action` (`keep`, `replace`) để thống kê giữ/sửa/đổi. Đánh giá `assisted` bị loại khỏi chỉ số
  chấm mù (`answer_correct`, duyệt không sửa).
- **Chưa có:** Sửa câu (nội dung, đáp án, lời giải), ráp đề, trộn mã đề, đề dự phòng, xuất PDF.

### 7.6. Xuất đề ra Word

```http
GET /api/exams/12/export/docx   → 200, file MT2610231200001.docx (Content-Disposition: attachment)
```

- Bản cơ bản: tiêu đề và mã đề; PHẦN I/II/III với tên phần đầy đủ (PHẦN I. Câu trắc nghiệm nhiều phương án lựa chọn,
  PHẦN II. Câu trắc nghiệm đúng sai, PHẦN III. Câu trắc nghiệm trả lời ngắn); "Câu k." đánh số lại từ 1 trong từng
  phần; `mcq` mỗi phương án một dòng "A. ..."; `tf` các ý "a) ..."; `sa` chỉ có đề. Sau một ngắt trang là "ĐÁP ÁN"
  (`mcq` chữ cái, `tf` chuỗi Đ/S theo thứ tự a–d như `ĐSĐS`, `sa` đáp số) rồi "LỜI GIẢI" từng câu.
- Đáp án đúng trong phần đề CHƯA đánh dấu (tùy chọn gạch chân cho phần mềm trộn đề làm sau).
- Chỉ xuất nội dung các chỗ đã có câu (`ready`, `kept`, `edited`); chỗ `failed` ghi "[Chưa có câu – cần sinh lại]",
  chỗ đang sinh ghi "[Chưa có câu – đang sinh]"; ở ĐÁP ÁN và LỜI GIẢI các chỗ này ghi "—". Xuất đề cũng đối soát như
  xem đề (7.4).
- Cách làm: dựng Markdown từ dữ liệu xem đề rồi gọi `pandoc -f markdown+tex_math_dollars -t docx
  --reference-doc=reference.docx` (thư mục tạm riêng, tối đa 30 giây, xóa khi xong). Công thức `$...$` thành Equation
  gốc của Word (OMML), sửa được trong Word.
- File mẫu `back_end/src/main/resources/export/reference.docx` (chép ra thư mục tạm mỗi lần xuất): là `reference.docx`
  mặc định của pandoc (`pandoc -o reference.docx --print-default-data-file reference.docx`), thêm vào `word/settings.xml`
  khai báo `<w:compat><w:compatSetting w:name="compatibilityMode" w:uri="http://schemas.microsoft.com/office/word"
  w:val="15"/></w:compat>` (trước `<w:rsids>`) để Word không mở file ở Compatibility Mode (lệnh Convert Equations của
  MathType mới chạy), và đặt font mặc định cùng kiểu Normal là Times New Roman 13 trong `word/styles.xml`. Tiêu đề
  (Heading) vẫn dùng font theo theme của pandoc.
- Lỗi: đề không tồn tại 404; máy chủ không có pandoc hoặc pandoc chạy quá 30 giây trả 503 kèm `error`; pandoc báo lỗi
  trả 500.
- **Cài pandoc** (backend không tự cài):
  - Windows: tải bộ cài `.msi` ở <https://pandoc.org/installing.html> (hoặc `winget install --id JohnMacFarlane.Pandoc`),
    rồi mở terminal mới và khởi động lại backend để PATH có pandoc. Bộ cài mặc định đặt ở
    `%LOCALAPPDATA%\Pandoc\pandoc.exe`.
  - Ubuntu/Debian: `sudo apt install pandoc` (bản trong kho có thể cũ; cần pandoc 2.x trở lên).
  - macOS: `brew install pandoc`.
  - Kiểm tra: `pandoc --version`. Nếu pandoc không nằm trên PATH, đặt biến môi trường `PANDOC_PATH` (cấu hình
    `app.export.pandoc-path`) là đường dẫn đầy đủ tới file chạy.
  - Test gọi pandoc thật (`PandocRunnerTest`, `ExamBuildTest`) tìm pandoc theo `PANDOC_PATH`, PATH, rồi
    `%LOCALAPPDATA%\Pandoc`; không thấy thì bỏ qua phần gọi pandoc thật.

## 8. Đăng nhập và phân quyền (W1-1)

Không có đăng ký tự do: admin tạo tài khoản giáo viên (mục 8.1). Admin đầu tiên tạo khi backend khởi động từ biến môi
trường `ADMIN_EMAIL`, `ADMIN_PASSWORD` (từ 12 ký tự), `ADMIN_NAME`; chỉ tạo khi chưa có admin, không ghi đè mật khẩu.
Tài khoản cũ trong DB chưa có mật khẩu nên chưa đăng nhập được tới khi admin đặt mật khẩu (`reset-password`, mục 8.1).

- **Access token**: JWT HS256, sống 15 phút, trả trong JSON. Frontend giữ trong bộ nhớ (store), **không** lưu
  `localStorage`; gửi `Authorization: Bearer <accessToken>`.
- **Refresh token**: chuỗi ngẫu nhiên trong cookie `refresh_token` (HttpOnly, Secure theo `app.security.cookie-secure`,
  SameSite=Strict, Path=/api/auth), sống 7 ngày, gia hạn khi dùng, phiên dài tối đa 30 ngày. JavaScript không đọc được
  cookie này; trình duyệt tự gửi khi gọi `/api/auth/*` cùng origin (Vite proxy hoặc nginx). Mỗi lần refresh cấp token
  mới; gửi lại token cũ bị coi là dùng lại và cả phiên bị thu hồi.

```http
POST /api/auth/login
Content-Type: application/json
{"email": "gv.toan@truong.edu.vn", "password": "********"}

200 OK
Set-Cookie: refresh_token=<opaque>; Path=/api/auth; Max-Age=604800; Secure; HttpOnly; SameSite=Strict
{"accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 900,
 "user": {"id": 7, "name": "Nguyễn Văn A", "email": "gv.toan@truong.edu.vn", "role": "teacher"}}

401 {"error": "Email hoặc mật khẩu không đúng", "code": "INVALID_CREDENTIALS"}
400 {"error": "email và password không được để trống"}
```

```http
POST /api/auth/refresh        (không body; trình duyệt gửi cookie)
200 → cùng dạng như login, kèm cookie mới
401 → {"error": "Phiên đăng nhập không hợp lệ hoặc đã hết hạn, hãy đăng nhập lại", "code": "REFRESH_INVALID"}, xóa cookie

POST /api/auth/logout         → 204, xóa cookie (gọi lại hoặc không có cookie vẫn 204)
GET  /api/auth/me             → {"id": 7, "name": "...", "email": "...", "role": "teacher"}
POST /api/auth/password       {"currentPassword": "...", "newPassword": "..."}
     → 204 và mọi phiên bị thu hồi (phải đăng nhập lại); sai mật khẩu hiện tại → 400
       {"error": "Mật khẩu hiện tại không đúng"}; mật khẩu mới phải từ 8 ký tự, tối đa 72 byte → 400
```

Lỗi xác thực từ filter: `401 {"error": "Chưa đăng nhập", "code": "UNAUTHENTICATED"}`,
`401 {"error": "Phiên đăng nhập đã hết hạn", "code": "TOKEN_EXPIRED"}`,
`401 {"error": "Token không hợp lệ", "code": "TOKEN_INVALID"}` (kèm header `WWW-Authenticate: Bearer ...`),
`403 {"error": "Bạn không có quyền thực hiện thao tác này", "code": "FORBIDDEN"}`.

**Hướng dẫn frontend:**

1. Axios `baseURL` để trống (gọi `/api/...` cùng origin qua Vite proxy `/api` → `http://localhost:8080`, hoặc nginx khi
   triển khai). Cookie tự gửi khi cùng origin; nếu gọi khác origin thì cần `withCredentials: true`.
2. Interceptor request: gắn `Authorization: Bearer <accessToken>` nếu có.
3. Interceptor response, khi **401** (trừ phản hồi của `/api/auth/login` và `/api/auth/refresh`): gọi
   `POST /api/auth/refresh` **một lần dùng chung** cho mọi request đang chờ (single-flight, ví dụ giữ một Promise), rồi
   gửi lại request gốc đúng một lần với token mới. Refresh lỗi → xóa store, chuyển về trang đăng nhập. Không gộp như vậy
   thì hai lần refresh song song sẽ bị coi là dùng lại token và cả phiên bị đăng xuất.
4. **403** → báo "không có quyền", không gọi refresh. **404** với đề, ma trận → "không tìm thấy" (có thể là của người khác).
5. Khi tải lại trang: gọi `/api/auth/refresh` để lấy access token mới (cookie còn hạn thì vẫn đăng nhập).
6. **Tải file Word**: không dùng `window.open`/`<a href>` (không gắn được header). Dùng
   `apiClient.get(url, {responseType: 'blob'})`, lấy tên file từ header `Content-Disposition` (đã cho phép đọc qua CORS).
7. Không gửi `teacherId` nữa. Bỏ trang đăng ký. Phân trang theo `user.role` (`teacher`, `admin`).

### 8.1. Quản trị tài khoản (chỉ admin)

```http
GET  /api/admin/users?role=teacher
→ [{"id": 7, "name": "Nguyễn Văn A", "email": "gv.toan@truong.edu.vn", "role": "teacher", "enabled": true,
    "hasPassword": true, "createdAt": "2026-10-24T11:00:00", "lastLoginAt": "2026-10-24T11:05:00"}]

POST /api/admin/users          {"name": "Nguyễn Văn A", "email": "gv.toan@truong.edu.vn", "password": "********"}
→ 201, tài khoản như trên (vai trò luôn là teacher)

PATCH /api/admin/users/7       {"enabled": false}            → 200, tài khoản sau khi sửa
PATCH /api/admin/users/7       {"name": "Nguyễn Văn B"}

POST /api/admin/users/7/reset-password   {"newPassword": "********"}   → 204
```

- Danh sách theo id tăng dần; `role` (`teacher`, `admin`, `researcher`) bỏ trống là tất cả. Không bao giờ trả mật khẩu
  hay mật khẩu băm; `hasPassword = false` là tài khoản cũ chưa đặt mật khẩu.
- Tạo: `name` không trống (tối đa 255 ký tự), `email` hợp lệ (lưu chữ thường), mật khẩu từ 8 ký tự, tối đa 72 byte;
  sai thì 400. Email đã dùng (không phân biệt hoa thường) trả 409.
- Sửa: chỉ đổi trường được gửi; body rỗng hoặc `name` trống trả 400; id không tồn tại 404. **Khóa** (`enabled: false`)
  thu hồi mọi refresh token của người đó; access token cũ còn hiệu lực tối đa 15 phút (H10). Tự khóa mình trả 409
  `Không thể tự khóa tài khoản của mình`; khóa admin đang hoạt động cuối cùng trả 409
  `Không thể khóa admin đang hoạt động cuối cùng`. Mở khóa không cấp lại phiên, người đó đăng nhập lại.
- Đặt lại mật khẩu: dùng được cho cả tài khoản cũ chưa có mật khẩu (để giáo viên mặc định/dữ liệu cũ đăng nhập); thu hồi
  mọi phiên của người đó. Tài khoản chưa có email trả 400 `Tài khoản chưa có email nên không đặt mật khẩu được` (API
  chưa có chỗ sửa email).

### 8.2. Giới hạn đăng nhập sai

Sai 5 lần trong 15 phút với cùng email và IP thì khóa đăng nhập 15 phút cho cặp đó, kể cả khi nhập đúng mật khẩu:

```http
429 Too Many Requests
Retry-After: 873
{"error": "Đăng nhập sai quá nhiều lần. Thử lại sau 15 phút.", "code": "TOO_MANY_ATTEMPTS"}
```

- Email không tồn tại cũng bị đếm như nhau (không lộ email nào có tài khoản). Đăng nhập đúng thì xóa bộ đếm.
- Đếm theo cả IP để người khác không khóa được tài khoản của giáo viên chỉ bằng cách cố tình nhập sai.
- Bộ đếm nằm trong bộ nhớ của backend: khởi động lại thì mất; chạy nhiều bản backend thì mỗi bản đếm riêng.
- Sau nginx phải đặt `FORWARD_HEADERS_STRATEGY=native` (nginx gửi `X-Forwarded-For`), nếu không mọi người dùng chung IP
  của nginx và giới hạn thành theo email.
- Cấu hình: `LOGIN_MAX_FAILURES` (5), `LOGIN_FAILURE_WINDOW` (`15m`), `LOGIN_LOCK_DURATION` (`15m`).
- Frontend: 429 thì hiện thông báo trong `error`, không gọi refresh.

**Ghi chú cho sau này (chưa làm):** nếu thêm SSE để đẩy tiến độ tạo đề, KHÔNG dùng `EventSource` vì không gửi được header
`Authorization`; dùng `fetch` đọc stream (gắn header được) hoặc cấp vé ngắn hạn dùng một lần cho kết nối SSE.

**Cấu hình backend** (biến môi trường, xem `.env.example`): `JWT_SECRET` (bắt buộc, base64 từ 32 byte, ví dụ
`openssl rand -base64 48`; thiếu thì backend không khởi động), `JWT_ACCESS_TTL` (mặc định `15m`), `REFRESH_TTL` (`7d`),
`REFRESH_ABSOLUTE_TTL` (`30d`), `COOKIE_SECURE` (mặc định `true`; Chrome chấp nhận cookie Secure trên
`http://localhost`, nên chỉ đặt `false` khi dev qua http với host khác localhost), `CORS_ALLOWED_ORIGINS`,
`ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_NAME`. Swagger UI (`/swagger-ui.html`) có nút **Authorize**: đăng nhập bằng
`POST /api/auth/login`, dán `accessToken`. `GET /actuator/health` công khai nhưng chỉ trả chi tiết cho người đã đăng nhập.
