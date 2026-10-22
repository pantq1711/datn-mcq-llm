# API backend: tóm tắt cho frontend

Cập nhật 2026-10-22 (thêm câu Đúng/Sai `tf` và Trả lời ngắn `sa`). Nguồn chuẩn là code trong `back_end/` và các
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

- Base URL: `http://localhost:8080`. CORS cho phép mọi cổng của `localhost` và `127.0.0.1` trên `/api/**`.
- Chưa có xác thực. `reviewerId` lấy từ body; bỏ trống thì dùng giáo viên mặc định.
- Lỗi do backend chủ động trả có dạng `{"error": "thông báo tiếng Việt"}`:

| Mã | Khi nào |
|---|---|
| 400 | Dữ liệu không hợp lệ, ví dụ thiếu trường bắt buộc, gửi trường không áp dụng cho dạng câu, hoặc bản sửa còn lỗi LaTeX fatal |
| 404 | Không tìm thấy bản ghi, ví dụ `subjectId` hoặc `templateId` không tồn tại |
| 409 | Xung đột trạng thái, ví dụ nộp đánh giá cho câu `duplicate` hoặc nộp lại câu experiment |
| 429 | Gemini đang giới hạn tần suất gọi; chờ khoảng một phút rồi thử lại |
| 503 | Gemini tạm thời quá tải hoặc bảo trì |
| 500 | Lỗi khác chưa được phân loại, body theo định dạng mặc định của Spring |

Dạng câu (`questionType`): `mcq` (trắc nghiệm 4 phương án, mặc định khi bỏ trống), `tf` (Đúng/Sai: đề dẫn và đúng 4 ý
a-d), `sa` (Trả lời ngắn: đề và một đáp số). Câu `tf` và `sa` chỉ sinh cho `production`, chiến lược `zero-shot`, prompt
bản nháp (`tf-v1-draft`, `sa-v1-draft`; run trước ngày 22/10 là `v0`). Thực nghiệm C1/C2/C3 chỉ dùng `mcq`.
Khi sinh câu `tf`/`sa` (sync và job), prompt gửi model kèm tối đa 10 câu gần nhất cùng template và cùng dạng để model
không lặp lại bài toán hay số liệu; xem `enums.md` mục 3, "Độ đa dạng".

## 2. Endpoint

| Phương thức | Đường dẫn | Việc |
|---|---|---|
| GET | `/api/catalog/subjects` | Danh sách môn |
| GET | `/api/catalog/templates?subjectId=` | Danh sách template, lọc theo môn nếu có |
| POST | `/api/catalog/templates` | Tạo template cho môn đã tồn tại (không tạo môn) |
| POST | `/api/generation/generate-sync` | Sinh 1 câu (mọi dạng) và chạy QC ngay. Gọi Gemini, có thể tốn phí |
| POST | `/api/generation/jobs/enqueue` | Tạo job sinh câu bất đồng bộ (tối đa 50, mọi dạng) |
| POST | `/api/generation/jobs/process-next?runAnswerCheck=true` | Xử lý ngay 1 job đến hạn. Gọi Gemini |
| GET | `/api/generation/jobs` | Danh sách job |
| GET | `/api/reviews/questions` | Danh sách câu để chấm, mọi dạng (không có tham số lọc trạng thái) |
| GET | `/api/reviews/questions/{id}` | Form chấm một câu |
| POST | `/api/reviews/questions/{id}` | Nộp đánh giá |
| POST | `/api/reviews/demo/seed-math` | Tạo 5 câu `mcq` demo; đi qua format và dedup, không chạy answer_check, nên giữ `generated` |
| GET | `/api/bank-items?questionType=` | Ngân hàng câu hỏi đã duyệt, mới nhất trước; `questionType` (`mcq`, `tf`, `sa`) bỏ trống là mọi dạng |

Chạy lại QC cho câu `generated` (quy tắc QC-RERUN, quyết định 6 ngày 22/10) đã có ở tầng service (`QcRerunService`, cả ba
dạng) nhưng CHƯA có endpoint: backend chưa có xác thực và phân quyền (Q-6), nên không mở đường công khai có thể gọi Solver
tính phí cho câu tùy ý.

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
