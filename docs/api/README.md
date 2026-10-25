# API backend: tóm tắt cho frontend

Cập nhật 2026-10-24 (W1-3: ngân hàng có lọc, phân trang, chi tiết kèm nguồn gốc; vết QC theo ô; chống trùng trong đề;
mục "Ngân hàng câu hỏi" và 7.7). Cùng ngày (W1-1: đăng nhập JWT, phân quyền, quyền sở hữu, `GET /api/exams`; mục 8). Trước đó 2026-10-22
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
- Lỗi do backend chủ động trả có dạng `{"error": "thông báo tiếng Việt"}`; lỗi xác thực và lỗi của Sửa câu / Giữ khi đang
  kiểm (W1-2, mục 7.5a) có thêm `"code"`, lỗi định dạng bản sửa có thêm `"errors"` (danh sách lỗi từng trường):

| Mã | Khi nào |
|---|---|
| 400 | Dữ liệu không hợp lệ, ví dụ thiếu trường bắt buộc, gửi trường không áp dụng cho dạng câu, hoặc bản sửa còn lỗi LaTeX fatal |
| 401 | Chưa đăng nhập (`UNAUTHENTICATED`), token hết hạn (`TOKEN_EXPIRED`), token sai (`TOKEN_INVALID`), đăng nhập sai (`INVALID_CREDENTIALS`), refresh không hợp lệ (`REFRESH_INVALID`) |
| 403 | Sai vai trò (`FORBIDDEN`), ví dụ giáo viên gọi API quản trị, admin gọi API của giáo viên |
| 404 | Không tìm thấy bản ghi, ví dụ `subjectId` hoặc `templateId` không tồn tại. Ma trận, đề của giáo viên khác cũng trả 404 (giống id không tồn tại) |
| 409 | Xung đột trạng thái, ví dụ nộp đánh giá cho câu `duplicate` hoặc nộp lại câu experiment. Có mã máy đọc được ở một số luồng: `MATRIX_IN_USE`, `BANK_EXHAUSTED`, `SLOT_*`, `VERSION_CONFLICT`, `QC_CHECKING`. W1-4 (K-5): hai thao tác đồng thời làm vi phạm khóa ngoại hoặc khóa duy nhất trả 409 `FOREIGN_KEY_CONFLICT` / `UNIQUE_CONFLICT` ("hãy tải lại rồi thử lại"), không lộ tên ràng buộc |
| 429 | Gemini đang giới hạn tần suất gọi; chờ khoảng một phút rồi thử lại. Hoặc đăng nhập sai quá nhiều lần (`TOO_MANY_ATTEMPTS`, header `Retry-After`, mục 8.2) |
| 503 | Gemini tạm thời quá tải hoặc bảo trì |
| 500 | Lỗi khác chưa được phân loại, body theo định dạng mặc định của Spring. Lỗi toàn vẹn dữ liệu khác (CHECK, NOT NULL: lỗi của chương trình) có `{"code": "DATA_INTEGRITY"}` |

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
| PATCH | `/api/admin/users/{id}` | Sửa tên, email, bật/khóa (đổi email hoặc khóa thì thu hồi phiên) | AD |
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
| POST | `/api/generation/jobs/process-next?runAnswerCheck=true` | Xử lý ngay 1 job đến hạn (khóa `FOR UPDATE SKIP LOCKED`, không nhận trùng với worker). Gọi Gemini. `runAnswerCheck=false` không nhận job `answer_check` |
| GET | `/api/generation/jobs` | Danh sách job; mỗi job có `jobType` (`generate` hoặc `answer_check`, V13) |
| GET | `/api/reviews/questions` | Danh sách câu để chấm, mọi dạng (không có tham số lọc trạng thái) |
| GET | `/api/reviews/questions/{id}` | Form chấm một câu |
| POST | `/api/reviews/questions/{id}` | Nộp đánh giá |
| POST | `/api/reviews/demo/seed-math` | Tạo 5 câu `mcq` demo; đi qua format và dedup, không chạy answer_check, nên giữ `generated` |
| GET | `/api/bank-items?...` | Ngân hàng câu hỏi đã duyệt: lọc, tìm, sắp xếp, phân trang; mỗi câu kèm số đề đã dùng (mục "Ngân hàng câu hỏi") |
| GET | `/api/bank-items/{id}` | Chi tiết một câu ngân hàng: đáp án, lời giải, nguồn gốc, đề đã dùng |
| GET | `/api/bank-items/topics?subjectId=` | Chủ đề có câu trong ngân hàng kèm số câu, để dựng bộ lọc |
| POST | `/api/exam-matrices` | Tạo ma trận đề bằng JSON (mục 7) |
| GET | `/api/exam-matrices`, `/api/exam-matrices/{id}` | Danh sách ma trận / chi tiết một ma trận |
| PUT, DELETE | `/api/exam-matrices/{id}` | Sửa / xóa ma trận; đã có đề thì chỉ sửa được tên, nguồn, ghi chú và không xóa được: 409 `MATRIX_IN_USE` (mục 7.1a) |
| POST | `/api/exam-matrices/{id}/duplicate` | Nhân bản ma trận thành ma trận mới độc lập (mục 7.1a) |
| GET | `/api/exam-matrices/samples` | Các mẫu ma trận có sẵn (hiện có `giuaki1-toan8`) |
| POST | `/api/exam-matrices/samples/{key}` | Tạo ma trận từ mẫu có sẵn |
| POST | `/api/exams/build` | Tạo đề theo ma trận một lần bấm (chế độ giáo viên). Sinh câu qua hàng đợi, có thể tốn phí |
| POST | `/api/exams/build-by-lesson` | Sinh theo bài (không cần ma trận): môn, khối, bài, số câu theo dạng và mức (mục 7.2a). Có thể tốn phí |
| GET | `/api/exams/{id}/progress` | Tiến độ theo ô |
| GET | `/api/exams/{id}` | Xem đề: câu theo phần I-III kèm đáp án, lời giải, cờ "cần xem kỹ" (KHÔNG chấm mù) |
| GET | `/api/exams/{id}/cells/{cellId}` | Các chỗ của một ô (tóm tắt) |
| GET | `/api/exams/{id}/slots/{slotId}/qc-trace` | Vết QC của một chỗ: các lần sinh, phiên bản, từng bước QC, thao tác (mục 7.7). Chỉ đọc |
| POST | `/api/exams/{id}/slots/{slotId}/keep` | Giữ câu (vào ngân hàng) |
| POST | `/api/exams/{id}/slots/{slotId}/replace` | Đổi câu khác: `source = generate` (mặc định) sinh lại đúng ô, có thể kèm yêu cầu của giáo viên; `source = bank` lấy câu ngân hàng khớp ô, hết thì 409 `BANK_EXHAUSTED` (mục 7.5) |
| PUT | `/api/exams/{id}/slots/{slotId}/question` | Sửa câu của chỗ chưa Giữ (mục 7.5a); QC chạy lại, Solver qua hàng đợi |
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
- Trả về danh sách job vừa tạo; mỗi job có `questionType`, `jobType` (`generate`) và với `tf` thêm
  `targetStatementDifficulties` dạng chuỗi `"easy,easy,medium,hard"` (job `tf` có `targetDifficulty = null`).
- `jobType = "answer_check"` (V13, W1-2) là job chạy lại Solver cho bản giáo viên sửa (mục 7.5a): có `questionId` ngay
  khi tạo, không có `examSlotId`.

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

Ngân hàng là dữ liệu đã duyệt, không phải form chấm, nên có đáp án, đáp số và nhãn cuối. Ngân hàng dùng chung cho mọi
giáo viên (H1). Giáo viên và admin chỉ ĐỌC; sửa, xóa câu ngân hàng chưa làm (Q-4c).

**Danh sách** (W1-3: thay danh sách không phân trang; đổi hợp đồng so với trước, frontend chưa dùng):

```http
GET /api/bank-items?subjectId=1&gradeLevel=8&questionType=mcq&topic=Đa thức&difficulty=easy&q=bậc&sort=least_used&page=0&size=20
→ {"items": [ ...câu... ], "page": 0, "size": 20, "totalItems": 57, "totalPages": 3}
```

| Tham số | Ý nghĩa |
|---|---|
| `subjectId`, `gradeLevel` (6-9) | Môn, khối (của template gốc của câu) |
| `questionType` | `mcq`, `tf`, `sa` |
| `topic`, `content` | Chủ đề; nội dung của ô ma trận đã sinh câu. So đúng, bỏ khoảng trắng hai đầu, không phân biệt hoa thường |
| `difficulty` | `easy`, `medium`, `hard`. Câu `tf`: có ít nhất một ý ở mức đó |
| `levels` | Chỉ câu `tf`: đúng 4 mức a-d, ví dụ `easy,easy,medium,hard` (gửi kèm `questionType` khác `tf` thì 400) |
| `origin` | `ai` (câu AI đã được giáo viên duyệt, hiện là mọi câu). `import` để dành cho nhập từ Word: hiện trả rỗng |
| `q` | Tìm trong đề, 4 phương án, đáp số và các ý (không phân biệt hoa thường, phân biệt dấu; `%` và `_` là ký tự thường). Tối đa 100 ký tự |
| `usage` | `used` (có ít nhất một đề dùng) hoặc `unused` |
| `sort` | `newest` (mặc định, như trước), `oldest`, `least_used` (ít đề dùng trước, hòa thì dùng lần cuối cũ hơn trước), `recently_used` |
| `page`, `size` | `page` từ 0 (mặc định 0); `size` 1-50 (mặc định 20) |

Tham số sai (số trang, `gradeLevel`, enum lạ, `sort`, `usage`, `origin`, `levels`, `q` quá dài) trả 400 `{"error": "..."}`.
Chưa đăng nhập 401. Mỗi câu giữ nguyên các key cũ (luôn có mặt kể cả khi `null`: `id, sourceQuestionId, reviewId,
questionText, optionA..optionD, correctOption, explanation, difficulty, createdAt`, cộng `questionType`; câu `sa` thêm
`saAnswer` là đáp số cuối giáo viên chốt; câu `tf` thêm `statements` 4 ý theo thứ tự a-d và `difficulty` cấp câu là
`null`) và thêm môn, chủ đề, nguồn, mức dùng:

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
 ],
 "subjectId": 1, "subjectName": "Toán", "gradeLevel": 8, "topic": "Hằng đẳng thức", "content": "Khai triển",
 "origin": {"type": "ai", "reviewMode": "assisted", "edited": true},
 "usageCount": 3, "lastUsedAt": "2026-10-23T09:00:00"}
```

- `origin.reviewMode`: `assisted` (Giữ trong đề ở chế độ giáo viên) hoặc `blind` (duyệt ở form chấm mù);
  `origin.edited`: câu đã qua tay giáo viên sửa (bản sửa trong đề hoặc sửa lúc duyệt).
- `usageCount`: số đề khác nhau (của mọi giáo viên) đang có câu này; `lastUsedAt`: ngày tạo đề mới nhất trong số đó, `null`
  nếu chưa dùng. Câu bị Đổi ra khỏi một chỗ thì đề đó không còn tính.
- `content` có thể `null` (câu không sinh từ ô ma trận).

**Chi tiết một câu:**

```http
GET /api/bank-items/88
→ { ...mọi trường của một câu ở trên...,
    "provenance": {
      "llmQuestionId": 345, "runId": 12, "promptVersion": "matrix-mcq-v1-draft", "model": "gemini-3.5-flash-lite",
      "versions": [
        {"questionId": 345, "kind": "llm", "createdAt": "2026-10-24T09:00:00", "editedBy": null,
         "changedFields": null, "editDistance": null},
        {"questionId": 402, "kind": "teacher_edit", "createdAt": "2026-10-24T09:20:00",
         "editedBy": {"id": 7, "name": "Nguyễn Văn A"}, "changedFields": ["option_c", "correct_option"],
         "editDistance": 4}],
      "review": {"reviewId": 91, "mode": "assisted", "decision": "approved_as_is",
                 "reviewer": {"id": 7, "name": "Nguyễn Văn A"}, "reviewedAt": "2026-10-24T09:30:00"}},
    "usage": {"usageCount": 3, "lastUsedAt": "2026-10-24T10:00:00",
              "myExams": [{"examId": 12, "examCode": "MT261024090000123", "title": "Đề giữa kì I",
                           "createdAt": "2026-10-24T10:00:00"}]}}
404 {"error": "Không tìm thấy câu ngân hàng id=999"}
```

- `provenance.versions` đi từ câu LLM gốc đến bản được đưa vào ngân hàng; `bank_item.source_question_id` là phiên bản cuối.
  `editedBy` chỉ có khi có nhật ký thao tác Sửa trong đề. Không có `raw_llm_output`.
- `usage.myExams` chỉ gồm đề của chính người gọi (tối đa 50, mới nhất trước); đề của giáo viên khác chỉ được tính vào
  `usageCount`, không lộ mã đề. Admin luôn nhận danh sách rỗng.

**Chủ đề:** `GET /api/bank-items/topics?subjectId=` trả `[{"subjectId": 1, "subjectName": "Toán", "gradeLevel": 8,
"topic": "Đa thức", "count": 12}]` (sắp theo môn, khối, chủ đề; gộp chủ đề không phân biệt hoa thường).

Hiệu năng: với khoảng 3000 câu và 12000 lượt dùng, mọi tổ hợp lọc/tìm/sắp xếp trên chạy dưới 110 ms trên máy phát triển
(`BankSearchPerformanceTest`, tag `perf`, không chạy mặc định) nên chưa cần chỉ mục mới.

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
GET /api/exams?page=0&size=10[&kind=lesson|matrix]
→ {"items": [{"examId": 12, "examCode": "MT261022213000123", "title": "Đề giữa kì I", "mode": "teacher",
              "kind": "matrix", "status": "draft", "matrixId": 3, "createdAt": "2026-10-22T21:30:00", "totalSlots": 18,
              "readySlots": 10, "keptSlots": 5, "needsReviewSlots": 2, "generatingSlots": 2, "failedSlots": 1}],
    "page": 0, "size": 10, "totalItems": 1, "totalPages": 1}
```

- W1-4: `kind` là loại đề: `matrix` (tạo từ ma trận), `lesson` (sinh theo bài, `matrixId` luôn `null`) hoặc `manual` (đề
  cũ không có ma trận). Tham số `kind` tùy chọn lọc theo `lesson` hoặc `matrix`; giá trị khác trả 400 (`kind phải là lesson
  hoặc matrix`). Loại suy ra từ ma trận gốc của đề, không có cột riêng.
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
            "totalCommands": 7},
 "examCount": 0}
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

### 7.1a. Sửa, xóa, nhân bản ma trận (W1-4)

```http
PUT    /api/exam-matrices/3                 body như POST tạo ma trận (thay toàn bộ)  → 200 ma trận
DELETE /api/exam-matrices/3                                                          → 204
POST   /api/exam-matrices/3/duplicate       {"name": "Đề thử (cô Lan)"} (body tùy chọn) → 200 ma trận mới
```

`GET /api/exam-matrices` và `GET /api/exam-matrices/{id}` có thêm `examCount`: số đề đã tạo từ ma trận (UI dùng để báo
trước ma trận nào đang bị khóa). Quyền như mục 7: chỉ giáo viên, chỉ ma trận của mình.

- **Chưa có đề nào (`examCount = 0`):**
  - sửa: thay toàn bộ môn, khối, tên, nguồn, ghi chú và các ô (kiểm tra như tạo). Mọi ô được tạo lại nên **id ô và
    `templateId` đổi** sau mỗi lần sửa; template cũ không còn ô, câu sinh ra hay job nào tham chiếu thì bị xóa;
  - xóa: xóa ma trận, ô và các template không còn tham chiếu. Template còn câu sinh ra (vết nguồn gốc câu ngân hàng) ở lại.
- **Đã có đề (`examCount > 0`):** đề đã tạo đọc ô sống để Đổi/sinh lại và giữ khóa ngoại tới ma trận, nên:
  - sửa chỉ được **tên, nguồn, ghi chú**; gửi lại đúng cấu trúc cũ (môn, khối, mọi ô; khoảng trắng thừa không tính) thì
    200, id ô và template giữ nguyên; môn, khối hoặc ô khác thì **409** `MATRIX_IN_USE`, không đổi gì;
  - xóa trả **409** `MATRIX_IN_USE`;
  - muốn ma trận khác đi: tạo ma trận mới với nội dung đã sửa (`POST /api/exam-matrices`) hoặc nhân bản.
- **Nhân bản:** làm được cả với ma trận đã có đề. Ma trận mới độc lập (sửa hoặc xóa bản này không đụng bản kia): cùng nội
  dung ô, nguồn, ghi chú, mỗi ô có template riêng, `sampleKey` bỏ trống, `examCount = 0`, người sở hữu là người gọi. `name`
  bỏ trống thì "&lt;tên&gt; (bản sao)" (cắt cho vừa 255 ký tự); `name` quá 255 ký tự thì 400.
- Ma trận ẩn của sinh theo bài (7.2a), ma trận không có hoặc của giáo viên khác: 404 cho cả ba thao tác.
- Tạo đề và xóa/sửa ma trận đồng thời: tạo đề giữ khóa chia sẻ trên hàng ma trận, sửa/xóa khóa ghi hàng rồi mới đếm đề,
  nên lần xóa gặp tạo đề đang dở chờ rồi trả 409 `MATRIX_IN_USE`, còn tạo đề gặp ma trận vừa xóa trả 404 (không thành 500).

| Mã | `code` | Khi nào |
|---|---|---|
| 400 | — | Dữ liệu sai (`Ô 2: …`, `name dài tối đa 255 ký tự`…), không đổi gì |
| 404 | — | Ma trận không có, của giáo viên khác hoặc là ma trận ẩn; `subjectId` không tồn tại |
| 409 | `MATRIX_IN_USE` | Sửa môn/khối/ô hoặc xóa khi `examCount > 0`; thông báo nêu số đề |

### 7.2. Tạo đề một lần bấm

```http
POST /api/exams/build
{"matrixId": 3, "title": "Đề giữa kì I"}
→ {"examId": 12, "examCode": "MT261022213000123", "title": "Đề giữa kì I", "matrixId": 3, "slots": 18, "fromBank": 5, "jobsQueued": 13, "kind": "matrix"}
```

- `title` bỏ trống thì lấy tên ma trận. Người tạo đề là người đăng nhập (`teacherId` đã bỏ khỏi API từ W1-1; gửi lên
  thì bị bỏ qua). Ma trận phải của chính người gọi, không thì 404. Đề có `mode = teacher`, `status = draft`.
- Ngân hàng câu dùng chung cho mọi giáo viên (H1): câu ngân hàng khớp ô được lấy bất kể ai đã đưa vào.
- Mỗi ô lấy câu ngân hàng khớp ô trước: cùng môn, cùng dạng, cùng mức (`tf`: cùng 4 mức theo thứ tự), cùng chủ đề của
  template gốc (so sau trim, không phân biệt hoa thường); nếu câu ngân hàng được sinh từ một ô ma trận thì còn phải cùng
  nội dung. Một câu ngân hàng chỉ vào MỘT chỗ của đề (kể cả khi nhiều ô dùng chung template). Giữa các đề chỉ xếp hạng
  mềm, không cấm: câu có ít đề khác dùng hơn được chọn trước, hòa thì câu dùng lần cuối cũ hơn trước (chưa dùng lên đầu),
  hòa nữa thì theo id; đề của mọi giáo viên đều được tính (ngân hàng dùng chung). Hết câu chưa dùng thì dùng lại câu đã dùng.
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
- Dedup của câu sinh ra dùng phạm vi production hiện có (so với câu `qc_passed`, `in_bank` cùng môn), cộng (W1-3) các câu
  đang nằm ở chỗ khác CÙNG đề và cùng dạng: câu hiện tại của chỗ kể cả câu lệch Solver chưa qua `qc_passed`, văn bản câu
  ngân hàng đang dùng (kể cả bản đã sửa lúc duyệt), câu vừa sinh của chỗ đang sinh. Câu trùng thành `duplicate` rồi sinh
  lại như trên. Câu đã bị Đổi ra khỏi chỗ không tính. Hạn chế còn lại: hai job của cùng đề chạy đồng thời có thể cùng qua
  dedup (chưa khóa theo phạm vi).

### 7.2a. Sinh theo bài (W1-4)

Kiểm tra thường xuyên, ôn tập, làm bộ câu theo từng bài SGK không cần ma trận. Hệ thống tạo một "ma trận một bài" ẩn
(`exam_matrix.kind = 'lesson'`, V15) rồi dùng lại nguyên luồng 7.2: ngân hàng trước (xếp hạng mềm), phần thiếu sinh qua
hàng đợi; tiến độ, xem đề, Giữ/Đổi/Sửa, vết QC và xuất Word y như đề theo ma trận.

```http
POST /api/exams/build-by-lesson
{"subjectId": 1, "gradeLevel": 8, "lesson": "Bài 3. Hằng đẳng thức đáng nhớ",
 "content": "Bình phương của một tổng",
 "title": "Ôn tập bài 3",
 "learningOutcomes": {"hard": "Vận dụng hằng đẳng thức để rút gọn biểu thức nhiều bước."},
 "cells": [
  {"questionType": "mcq", "difficulty": "easy",   "questionCount": 3},
  {"questionType": "mcq", "difficulty": "medium", "questionCount": 2},
  {"questionType": "tf", "statementDifficulties": ["easy", "easy", "medium", "hard"], "questionCount": 1},
  {"questionType": "sa", "difficulty": "medium", "questionCount": 2}]}
→ {"examId": 31, "examCode": "MT261025090000123", "title": "Ôn tập bài 3", "matrixId": null, "slots": 8,
   "fromBank": 3, "jobsQueued": 5, "kind": "lesson"}
```

- Bắt buộc: `subjectId`, `lesson` (tên bài, tối đa 255 ký tự), `cells` (ít nhất 1). Tùy chọn: `gradeLevel` (6-9), `content`
  (phạm vi trong bài), `title` (trống thì lấy tên bài, tối đa 255), `learningOutcomes` (khóa `easy`, `medium`, `hard`).
- `cells`: mỗi phần tử là một nhóm câu cùng dạng và mức: `mcq`/`sa` cần `difficulty`; `tf` dùng `statementDifficulties`
  (4 mức ý a-d) và KHÔNG có `difficulty`; `tf` bỏ trống `statementDifficulties` thì mặc định dễ, dễ, vừa, khó. Phần I/II/III
  suy từ dạng câu. `questionCount` 1-50 mỗi nhóm. **Tổng số câu tối đa 50 mỗi lần**; vượt thì 400 (`Một lần sinh theo bài
  tối đa 50 câu (hiện N)`).
- Yêu cầu cần đạt: giáo viên không phải nhập. Mỗi mức ô dùng có mặc định theo tên bài ("Nhận biết…", "Hiểu và vận dụng
  trực tiếp…", "Vận dụng…"); `learningOutcomes` ghi đè theo mức.
- Ngân hàng: khớp theo môn, dạng, mức và **chủ đề = tên bài** (so sau trim, không phân biệt hoa thường). Khi không nhập
  `content`, không lọc theo nội dung: câu ngân hàng sinh từ ô ma trận cùng chủ đề dùng lại được. Có `content` thì giữ quy
  tắc 7.2 (cùng nội dung). Frontend nên gợi ý tên bài từ chủ đề đã có (`GET /api/bank/topics`) để tăng khả năng dùng lại.
- Ma trận ẩn không hiện ở `GET /api/exam-matrices`; mọi đường dẫn theo id của nó (xem, `POST /api/exams/build`, sửa,
  xóa, nhân bản; mục 7.1a) trả 404. Vì vậy `matrixId` của đề theo bài luôn `null` trong API.
- Lỗi: 400 `{"error": …}` (dữ liệu sai; không tạo gì), 404 (`subjectId` không tồn tại), 403 (admin), 401. Mọi lỗi đều
  không để lại ma trận, template hay đề (một transaction).
- Chỉ lọc và thống kê theo loại đề: `kind` có trong danh sách (7.0), xem đề (7.4) và kết quả tạo đề; `GET /api/exams?kind=lesson`.
- **Việc kế tiếp (chưa làm): danh mục bài `lesson_catalog`** (môn, khối, chương, số bài, tên bài) và tài liệu bài học.
  Đề xuất: bảng `lesson_catalog(id, subject_id, grade_level, chapter, lesson_no, title)`; `build-by-lesson` nhận thêm
  `lessonId` TÙY CHỌN, khi có thì `lesson`/`gradeLevel`/`subjectId` lấy từ danh mục (không gửi cũng được). Các trường hiện có
  giữ nguyên nghĩa nên API cũ không vỡ; chỗ lấy tên bài là `MatrixService.resolveLesson`. Tên bài vẫn là chủ đề khớp ngân
  hàng, nên câu cũ sinh theo chuỗi vẫn dùng lại được nếu tên trùng danh mục. Tài liệu bài học (nội dung SGK) sẽ thêm vào
  `MatrixCellSpec` (JSON ở `experiment_run.hyperparams`, khóa lạ được bỏ qua nên tương thích ngược).

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
 "kind": "matrix", "matrixId": 3, "createdAt": "2026-10-22T21:30:00", "buildStatus": "ready", "needsReviewCount": 1,
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
  do (vẫn lưu trong `warnings`). `needsReview` đúng khi danh sách khác rỗng. Câu giáo viên đã sửa (mục 7.5a) có thêm
  `solver_pending` (Solver đang kiểm lại, thay `solver_not_run`), `solver_failed` (kiểm lại thất bại) và
  `duplicate: <id>` (bản sửa trùng câu khác).
- W1-2: câu hệ thống sinh chưa Giữ có `qcState`: `checking` (Solver đang kiểm lại bản sửa), `passed` (không còn lý do cần
  xem kỹ), `needs_review` (có lý do). Chỗ khác không có trường này. Mọi câu có `edited` (câu hiện tại là bản giáo viên
  sửa, kể cả khi đã Giữ) và, khi `edited`, `originalQuestionId` (bản gốc LLM).

### 7.5. Giữ / Đổi câu khác

```http
POST /api/exams/12/slots/101/keep                    → câu (như 7.4) với "state": "kept", "bankItemId": 88
POST /api/exams/12/slots/104/replace                 → câu với "state": "generating" (chưa có nội dung)
POST /api/exams/12/slots/104/replace  {"instruction": "Dùng ngữ cảnh tính tiền mua vở"}
POST /api/exams/12/slots/104/replace  {"source": "bank"}      → câu ngân hàng khớp ô ("state": "ready", "source": "bank")
```

- Không cần body (Đổi có thể kèm `instruction`). Người thao tác (`exam_slot_action.actor_id`, `teacher_review.reviewer_id`
  khi Giữ) là người đăng nhập.
- **Giữ:**
  - câu hệ thống sinh được chép vào ngân hàng: ghi `teacher_review` production với `review_mode = 'assisted'` (duyệt
    nguyên trạng theo đáp án/nhãn của LLM) và `bank_item` trong cùng transaction; câu chuyển `in_bank`;
  - câu lệch Solver cũng Giữ được, vì giáo viên đã thấy cờ;
  - câu lấy từ ngân hàng thì chỉ đánh dấu `kept`, không tạo đánh giá mới;
  - Giữ lần hai trả 409 `Câu hỏi này đã được giữ.`; chỗ chưa có câu trả 409;
  - W1-2: câu giáo viên đã sửa được chép đúng bản sửa vào ngân hàng và chỗ thành `edited` (thay vì `kept`); khi Solver
    còn đang kiểm lại bản sửa (`qcState = checking`) trả 409 `{"code": "QC_CHECKING"}`; kiểm lại thất bại
    (`solver_failed`) thì Giữ được, kèm cờ đã thấy.
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
- Mỗi thao tác ghi `exam_slot_action` (`keep`, `replace`, `edit`) để thống kê giữ/sửa/đổi. Đánh giá `assisted` bị loại
  khỏi chỉ số chấm mù (`answer_correct`, duyệt không sửa).
- **Chưa có:** sửa câu đã nằm trong ngân hàng, ráp đề, trộn mã đề, đề dự phòng, xuất PDF.
- **Đổi bằng câu ngân hàng (W1-4, D5-B):** `POST .../replace` nhận `{"source": "bank" | "generate", "instruction"?}`. Bỏ
  trống `source` (hoặc không gửi body) là `generate`: giữ nguyên hành vi cũ (sinh mới qua hàng đợi), nên client cũ không vỡ.
  - `bank`: lấy ngay câu ngân hàng khớp ô, cùng tiêu chí và xếp hạng mềm như lúc tạo đề (mục 7.2; đề theo bài: 7.2a).
    Không có job, không gọi LLM. Trả câu như 7.4 với `state = ready`, `source = bank`, `bankItemId`, không có cờ. Chỗ
    đã Giữ cũng đổi được (câu đã Giữ vẫn ở ngân hàng).
  - Loại trừ **trong phạm vi cả đề**: (1) câu ngân hàng đang nằm ở chỗ nào của đề; (2) câu ngân hàng từng bị Đổi ra khỏi
    BẤT KỲ chỗ nào của đề (đọc từ `exam_slot_action`); (3) bản sao ngân hàng của một câu sinh ra đang nằm trong đề hoặc đã
    bị Đổi ra (câu có thể vào ngân hàng qua duyệt chấm mù), kể cả bản gốc của một câu giáo viên đã sửa.
  - Hết câu: **409** `{"code": "BANK_EXHAUSTED", "error": "Ngân hàng không còn câu phù hợp cho chỗ này …"}`. Chỗ giữ
    nguyên, không ghi nhật ký, không tự sinh ngầm; frontend đề nghị giáo viên "Sinh câu mới" (gọi lại với
    `source = generate`).
  - Lỗi khác: 400 `INVALID_REQUEST` khi `source` ngoài `bank`/`generate` hoặc `source = bank` kèm `instruction`; 409 (không
    có `code`) khi chỗ đang sinh; 404 đề của người khác.
  - Nhật ký `exam_slot_action` của `replace` có thêm `replace_source` (`bank` hoặc `generate`, V16; dòng cũ là `generate`)
    để thống kê Đổi sang ngân hàng / sinh mới. `generated_question_id`/`bank_item_id` của dòng là câu BỊ đổi ra.
  - Ghi chú số liệu: `fromBank` ở tiến độ và `source = bank` đếm cả chỗ Đổi bằng câu ngân hàng, không chỉ chỗ lấy lúc tạo đề.

### 7.5a. Sửa câu (W1-2)

```http
PUT /api/exams/12/slots/101/question
```

```json
// mcq
{"baseQuestionId": 345, "questionText": "…", "optionA": "…", "optionB": "…", "optionC": "…", "optionD": "…",
 "correctOption": "B", "explanation": "…"}
// sa
{"baseQuestionId": 346, "questionText": "…", "saAnswer": "3,5", "explanation": "…"}
// tf
{"baseQuestionId": 347, "questionText": "…", "explanation": "…",
 "statements": [{"key": "a", "text": "…", "label": "T"}, {"key": "b", "text": "…", "label": "F"},
                {"key": "c", "text": "…", "label": "F"}, {"key": "d", "text": "…", "label": "T"}]}
```

- Chỉ chỗ `ready` có câu hệ thống sinh (chưa Giữ). Gửi đủ nội dung theo dạng câu (thay thế toàn bộ, không sửa mức độ);
  trường của dạng khác để trống. `baseQuestionId` là `questionId` đang hiển thị lúc bắt đầu sửa.
- Mỗi lần sửa tạo một bản mới (`questionId` mới), bản gốc LLM giữ nguyên. Nội dung giống hệt câu hiện tại (sau trim) thì
  trả câu hiện tại, không ghi gì.
- QC chạy ngay: trim, tự đóng `$` quên ở cuối trường (ghi `warning: <trường>: đã tự đóng $`), kiểm định dạng như câu
  sinh ra (mục 6), dedup (không so với chính câu này và các bản trước của nó).
- Đổi đề, phương án/ý hoặc đáp án/đáp số/nhãn: Solver chạy lại qua hàng đợi, request không chờ (`qcState = checking`,
  `reviewReasons` có `solver_pending`). Theo dõi bằng `GET /api/exams/{id}`. Chỉ đổi lời giải: không gọi Solver, giữ
  kết quả Solver của bản trước.
- Solver lệch: `solver_inconsistent`, đáp án giáo viên giữ nguyên, Giữ được. Solver lỗi hoặc hết lượt thử:
  `solver_failed`, không bao giờ coi là đạt, Giữ được kèm cờ. Kết quả Solver của bản cũ về muộn không ảnh hưởng bản mới.
- Bản sửa trùng câu khác: `qcState = needs_review`, `reviewReasons: ["duplicate: <id>"]`, không Giữ được (sửa tiếp hoặc
  Đổi câu khác).
- Response 200: câu như 7.4, ví dụ:

```json
{"slotId": 101, "state": "ready", "source": "generated", "questionId": 402, "questionText": "…", "correctOption": "B",
 "explanation": "…", "needsReview": true, "reviewReasons": ["solver_pending", "warning: option_c: đã tự đóng $"],
 "qcState": "checking", "edited": true, "originalQuestionId": 345}
```

| Mã | `code` | Khi nào |
|---|---|---|
| 400 | `INVALID_REQUEST` | Thiếu trường (kể cả `baseQuestionId`), gửi trường của dạng khác, `correctOption` ngoài A-D, nhãn ý ngoài T/F, `tf` không đủ 4 ý a-d |
| 400 | `FORMAT_INVALID` | Bản sửa không qua kiểm tra định dạng; `errors` liệt kê từng lỗi, ví dụ `["option_a: nội dung trống"]` |
| 404 | — | Đề không có hoặc của giáo viên khác; chỗ không thuộc đề |
| 409 | `SLOT_KEPT` | Chỗ đã Giữ (sửa câu ngân hàng chưa hỗ trợ) |
| 409 | `SLOT_NOT_READY` | Chỗ đang sinh hoặc hỏng |
| 409 | `SLOT_FROM_BANK` | Chỗ lấy câu từ ngân hàng |
| 409 | `VERSION_CONFLICT` | `baseQuestionId` khác câu hiện tại (đã có người sửa hoặc Đổi, hoặc trang đã cũ): tải lại rồi sửa tiếp |

- Mỗi lần sửa ghi `exam_slot_action` `edit`: bản mới, người sửa, thời điểm, `changed_fields`, `edit_distance` (so với bản
  trước; không tính lời giải). Thống kê về đầu ra LLM phải lọc `generated_question.edited_from_question_id IS NULL`
  (`enums.md` mục 3).

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

### 7.7. Vết QC của một chỗ (W1-3)

```http
GET /api/exams/12/slots/101/qc-trace
```

Cho giáo viên (chỉ vai trò `teacher`, chỉ đề của mình) thấy câu trong chỗ đã đi qua những bước nào. **Chỉ đọc**: không
đối soát job như xem đề và tiến độ, nên gọi API này không đổi trạng thái chỗ (chỗ còn `generating` vẫn `generating`). Đề
không có hoặc của giáo viên khác, hoặc chỗ không thuộc đề: 404 `{"error": ...}`; admin 403; chưa đăng nhập 401.

```json
{"examId": 12, "slotId": 101, "cellId": 21, "slotNo": 2, "part": "I", "topic": "Đa thức", "questionType": "mcq",
 "state": "kept", "source": "generated", "currentQuestionId": 450, "bankItemId": 88, "attempts": 3, "solverRetries": 0,
 "lineages": [
  {"origin": "generated",
   "job": {"jobId": 501, "createdAt": "2026-10-24T09:00:00", "status": "done", "attempt": 1, "error": null},
   "versions": [
    {"questionId": 340, "kind": "llm", "createdAt": "2026-10-24T09:00:20", "status": "format_failed", "current": false,
     "editedBy": null, "changedFields": null, "editDistance": null,
     "answer": {"correctOption": "A", "saAnswer": null, "labels": null},
     "qc": {"format": {"passed": false, "errors": ["option_c: số dấu $ lẻ"], "warnings": [], "autoRepairs": [],
                       "checkedAt": "2026-10-24T09:00:20"},
            "dedup": null,
            "answerCheck": {"state": "not_run", "reason": "format_failed", "judgeModel": null, "judgeAnswer": null,
                            "judgeSaKind": null, "judgeSaAnswer": null, "judgeLabels": null, "solverReasoning": null,
                            "error": null, "checkedAt": null, "job": null}},
     "reviewReasons": []}]},
  {"origin": "generated",
   "job": {"jobId": 507, "createdAt": "2026-10-24T09:05:00", "status": "done", "attempt": 1, "error": null},
   "versions": [
    {"questionId": 345, "kind": "llm", "status": "qc_passed", "current": false, "...": "...",
     "answer": {"correctOption": "B"},
     "qc": {"format": {"passed": true, "errors": [], "warnings": [],
                       "autoRepairs": ["option_a: đã tự đóng $"], "checkedAt": "..."},
            "dedup": {"passed": true, "similarity": 0.41, "threshold": 0.9, "checkedAt": "...",
                      "comparedTo": {"questionId": 210, "inThisExam": false, "inBank": true}},
            "answerCheck": {"state": "consistent", "judgeModel": "gemini-3.5-flash-lite", "judgeAnswer": "B",
                            "solverReasoning": "Số mũ cao nhất của x là 3 ...", "error": null, "job": null, "...": "..."}},
     "reviewReasons": ["warning: option_a: đã tự đóng $"]},
    {"questionId": 402, "kind": "teacher_edit", "createdAt": "2026-10-24T09:20:00", "status": "generated",
     "current": false, "editedBy": {"id": 7, "name": "Nguyễn Văn A"},
     "changedFields": ["option_c", "correct_option"], "editDistance": 4, "answer": {"correctOption": "C"},
     "qc": {"format": {"passed": true, "...": "..."}, "dedup": {"passed": true, "...": "..."},
            "answerCheck": {"state": "inconsistent", "judgeAnswer": "B", "solverReasoning": "...",
                            "job": {"jobId": 530, "status": "done", "attempt": 1, "error": null, "...": "..."}}},
     "reviewReasons": ["solver_inconsistent"]}]},
  {"origin": "generated", "job": {"jobId": 533, "...": "..."},
   "versions": [{"questionId": 450, "kind": "llm", "status": "in_bank", "current": true, "...": "...",
                 "reviewReasons": []}]}],
 "actions": [
  {"id": 80, "action": "edit", "at": "2026-10-24T09:20:00", "actor": {"id": 7, "name": "Nguyễn Văn A"},
   "questionId": 402, "bankItemId": null, "changedFields": ["option_c", "correct_option"], "editDistance": 4,
   "instruction": null},
  {"id": 81, "action": "replace", "at": "...", "actor": {"id": 7, "name": "..."}, "questionId": 402, "bankItemId": null,
   "changedFields": null, "editDistance": null, "instruction": "Dùng ngữ cảnh mua vở"},
  {"id": 82, "action": "keep", "at": "...", "actor": {"id": 7, "name": "..."}, "questionId": 450, "bankItemId": 88,
   "changedFields": null, "editDistance": null, "instruction": null}]}
```

- `lineages`: mỗi phần tử là **một lần sinh** (job sinh câu của chỗ, theo thứ tự tạo; các lần sinh lại sau lỗi định dạng,
  trùng, lệch Solver hoặc Đổi câu đều là lineage riêng) và `versions` là câu LLM gốc rồi các bản giáo viên sửa theo thứ tự.
  Chỗ lấy câu từ ngân hàng có một lineage `origin = "bank"` (`job = null`) gồm chuỗi phiên bản của câu nguồn; W1-4: lineage
  này cũng xuất hiện (đứng cuối) khi chỗ từng được sinh rồi Đổi bằng câu ngân hàng.
- `current`: phiên bản đang nằm trong chỗ (`currentQuestionId`). `kind`: `llm` hoặc `teacher_edit`.
- `qc.format.autoRepairs`: các lần hệ thống tự sửa (`json: đã sửa 1 escape LaTeX (\circ)`, `<trường>: đã tự đóng $`);
  `warnings` là cảnh báo khác. `errors` là lý do không qua format. Mỗi bước là `null` khi câu chưa có dòng QC đó (ví dụ
  câu dựng tay).
- `qc.dedup.threshold`: ngưỡng đã dùng lúc kiểm, đọc từ `quality_check_result.threshold_used` (hiện 0.90); `similarity`
  là điểm cao nhất với câu `comparedTo` (có thể `null` khi không có ứng viên đáng kể). `comparedTo.inThisExam`: câu đó
  đang nằm trong đề này; `inBank`: câu đó đã có trong ngân hàng.
- `qc.answerCheck.state` phân biệt bốn tình huống: `consistent` (Solver khớp), `inconsistent` (lệch: kèm `judgeAnswer`
  hoặc `judgeSaKind`/`judgeSaAnswer` hoặc `judgeLabels` theo dạng câu, và `solverReasoning`), `error` (Solver lỗi, không có
  kết quả: `error` là lý do; với bản sửa còn `job.status = "failed"`), `pending` (job `answer_check` đang chờ hoặc chạy),
  `not_run` (chưa chạy, `reason` là `format_failed`, `duplicate`, `dedup_not_concluded`, `solver_not_run`, `superseded`
  hoặc `not_recorded`). `consistent`/`inconsistent` đọc từ dòng `answer_check` đã lưu.
- `reviewReasons`: lý do "cần xem kỹ" của riêng phiên bản đó, cùng tên với API xem đề (7.4), rỗng với bản đã vào ngân hàng.
- `actions`: Sửa / Đổi / Giữ của chỗ theo thứ tự thời gian; `Đổi` kèm `instruction`; `Sửa` kèm `changedFields`,
  `editDistance`. `questionId` là câu bị thao tác (Đổi: câu bị đổi ra khỏi chỗ).
- Không có `raw_llm_output`, prompt, `prompt_note`, token, độ trễ hay chi phí. Dữ liệu cũ (trước V14) có
  `solverReasoning = null`.

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
PATCH /api/admin/users/7       {"email": "gv.moi@truong.edu.vn"}

POST /api/admin/users/7/reset-password   {"newPassword": "********"}   → 204
```

- Danh sách theo id tăng dần; `role` (`teacher`, `admin`, `researcher`) bỏ trống là tất cả. Không bao giờ trả mật khẩu
  hay mật khẩu băm; `hasPassword = false` là tài khoản cũ chưa đặt mật khẩu.
- Tạo: `name` không trống (tối đa 255 ký tự), `email` hợp lệ (lưu chữ thường), mật khẩu từ 8 ký tự, tối đa 72 byte;
  sai thì 400. Email đã dùng (không phân biệt hoa thường) trả 409.
- Sửa: chỉ đổi trường được gửi (`name`, `email`, `enabled`); body rỗng, `name` trống hoặc `email` không hợp lệ trả
  400; id không tồn tại 404. Mọi trường được kiểm tra trước khi ghi: một trường lỗi thì không đổi gì.
- **Đổi email** (`email`): chuẩn hóa chữ thường, bỏ khoảng trắng hai đầu; email đã thuộc tài khoản khác (không phân biệt
  hoa thường) trả 409 `Email đã được dùng: ...`. Đổi thật thì thu hồi mọi refresh token của người đó (lý do
  `email_changed`, V12), người đó đăng nhập lại bằng email mới; gửi lại đúng email hiện tại thì không thu hồi gì. Dùng
  để thêm email cho tài khoản cũ trước khi đặt mật khẩu.
- **Khóa** (`enabled: false`)
  thu hồi mọi refresh token của người đó; access token cũ còn hiệu lực tối đa 15 phút (H10). Tự khóa mình trả 409
  `Không thể tự khóa tài khoản của mình`; khóa admin đang hoạt động cuối cùng trả 409
  `Không thể khóa admin đang hoạt động cuối cùng`. Mở khóa không cấp lại phiên, người đó đăng nhập lại.
- Đặt lại mật khẩu: dùng được cho cả tài khoản cũ chưa có mật khẩu (để giáo viên mặc định/dữ liệu cũ đăng nhập); thu hồi
  mọi phiên của người đó. Tài khoản chưa có email trả 400 `Tài khoản chưa có email nên không đặt mật khẩu được`: thêm
  email bằng PATCH trước rồi đặt mật khẩu.

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
