# Bước 0 – Học sinh làm đề trực tuyến qua link (A) và phân tích câu sau khi làm bài (B)

Trạng thái: **An đã duyệt 14/11/2026** kèm các quyết định ở mục 12; **chốt thêm 16/11/2026** sau rà soát an ninh 07 (mục 13,
thắng các mục trên khi mâu thuẫn). Đang code theo thứ tự PR ở mục 10.
Nền: `origin/main` 199f5f6 (PR #82), migration của PR A là **V32** (main đã có V30, V31). Tuân thủ `docs/ai-development-rules.md` v3.

Vị trí trong đề tài: A là vế "nền tảng học tập trực tuyến" (có người học thật); B là lớp QC thứ ba
(tiền kiểm tự động → giáo viên duyệt bằng Giữ → **hậu kiểm bằng dữ liệu học sinh**).

## 0. Điều đã kiểm trong repo và hệ quả cho thiết kế

| # | Sự thật (nguồn) | Hệ quả |
|---|---|---|
| F1 | Chỗ đã Giữ vẫn Đổi được, khi đó `exam_slot.bank_item_id` đổi tại chỗ (`docs/api/README.md` 7.5, "Chỗ đã Giữ cũng đổi được") | Bài giao phải **chốt thành phần đề lúc giao** (bảng `assignment_item`), không đọc lại `exam_slot` khi chấm và khi thống kê |
| F2 | `bank_item` hiện không có API sửa hay xóa (`BankController` chỉ có GET; 7.5 "Chưa có: sửa câu đã nằm trong ngân hàng"); Q-4c còn **Chờ** | Không chép nội dung câu, chỉ giữ `bank_item_id`. Nếu Q-4c sau này cho sửa thì phải chặn sửa câu đã nằm trong bài giao (câu hỏi 8) |
| F3 | Chỗ lấy từ ngân hàng có `bank_item_id` ngay ở `state = ready`, chưa qua Giữ (V7 `chk_es_ready`) | "Mỗi slot có bank_item" và "mọi câu đã Giữ" **không trùng nhau** (câu hỏi 1) |
| F4 | Thang điểm đã có ở `ExamScoringProperties` (`app.exam.scoring`): mcq 0,25; sa 0,5; tf theo số ý đúng 0 / 0,1 / 0,25 / 0,5 / 1,0 | Dùng lại đúng thang này, không đặt thang mới. Tổng điểm đề thường **khác 10** (câu hỏi 4) |
| F5 | `ShortAnswerValue.parse(…, LENIENT)` + `sameValue` đã so đáp số theo giá trị (mục 4 của API) | Chấm Trả lời ngắn gọi lại hàm này, không viết bộ chuẩn hóa mới |
| F6 | `QuestionContent.tsx` luôn vẽ đáp án (`correctOption`, `label`, `saAnswer`); `MathText.tsx` chỉ vẽ chữ + KaTeX | Trang học sinh dùng lại **`MathText`**, viết component câu hỏi riêng; không dùng `QuestionContent` |
| F7 | `SecurityConfig`: đường chưa khai báo bị chặn; `/api/exams/**` đã là TEACHER | API giáo viên đặt dưới `/api/exams/{examId}/assignments`; thêm đúng một nhóm `permitAll` cho `/api/public/**` |
| F8 | `LoginIpLimiter` (cửa sổ trượt theo IP, IPv6 gom /64, trong bộ nhớ) | Dùng cùng thuật toán. Cả lớp thường chung **một IP** (wifi trường) nên hạn mức phải rộng (mục 3.3) |
| F9 | Câu có hình chưa có trên main (không migration nào có cột hình; `docs/design/cau-co-hinh.md` duyệt 11/11, ảnh trả bằng URL có chữ ký cho người đăng nhập) | Mục 6 |
| F10 | Frontend không có khung test (`package.json` chỉ có `dev`, `build`, `lint`); antd 6 có sẵn component `QRCode` | Không thêm thư viện QR. Test frontend là lint + build + danh sách kiểm tay (mục 9) |
| F11 | Không có scheduler cho nghiệp vụ đề; không có API xóa đề | "Hết hạn thì đóng" làm theo kiểu lười (mục 2.2) |

Chỗ **lệch so với phạm vi đã chốt**, cần An biết:

1. Bảng thống kê khóa theo `(bài giao, slot)` như yêu cầu, nhưng mang kèm `bank_item_id` chốt lúc giao (do F1).
2. Thêm hai API nhỏ ngoài danh sách: giáo viên **xóa một lượt làm** (cho học sinh vào lại khi mất máy, vì token chỉ nằm trên
   máy học sinh) và **xóa bài giao** (xóa dữ liệu học sinh). Không có hai API này thì "một lượt làm nộp một lần" không vận hành được trong lớp.
3. "Không lưu nháp" được giữ ở server. Trình duyệt vẫn giữ token và câu trả lời trong `localStorage` để tải lại trang không mất bài;
   học sinh tắt máy trước khi nộp thì server không có gì và lượt đó tính là không nộp.
4. Cờ hậu kiểm chỉ gắn khi đủ mẫu (mục 5.4); với một lớp 35–40 em cờ chỉ là gợi ý xem lại.

## 1. Mô hình dữ liệu

Một migration cho A, một cho B: **số kế tiếp của main lúc làm** (thực tế: **V32** cho A, **V33** cho B, vì V30, V31 của PR khác
merge trước). Kiểu thời gian theo quy ước hiện có (`TIMESTAMP`, giờ server).
Module backend mới `assignment` (`assignment ──► exam, bank, catalog`; `exam` không biết `assignment`), cập nhật
`docs/architecture/backend-structure.md`.

### 1.1. `exam_assignment` (PR A)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | SERIAL PK | |
| `exam_id` | INT NOT NULL → `exam(id)` | |
| `created_by` | INT NOT NULL → `users(id)` | phải là chủ đề |
| `public_code` | VARCHAR(12) NOT NULL UNIQUE | 10 ký tự ngẫu nhiên (bảng 32 ký tự, bỏ 0/O/1/I ≈ 50 bit, `SecureRandom`). Lưu thô vì giáo viên cần hiện lại link và QR; nó chỉ mở trang chào, không mở lượt làm của ai |
| `duration_minutes` | INT NOT NULL | CHECK 5..180 (cùng khoảng với xuất Word) |
| `closes_at` | TIMESTAMP NOT NULL | hạn đóng |
| `show_score` | BOOLEAN NOT NULL | |
| `status` | VARCHAR(10) NOT NULL DEFAULT `'open'` | `open`, `closed` |
| `closed_at` | TIMESTAMP | CHECK `(status = 'closed') = (closed_at IS NOT NULL)` |
| `scoring` | JSONB NOT NULL | ảnh chụp `app.exam.scoring` lúc giao: `{"mcq":0.25,"sa":0.5,"tf":[0,0.1,0.25,0.5,1.0]}`; đổi cấu hình sau này không đổi điểm cũ |
| `consent_confirmed_at` | TIMESTAMP NOT NULL | giáo viên tick xác nhận (mục 7) |
| `stats_computed_at` | TIMESTAMP | PR B |
| `created_at` | TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP | |

Index: `uq_ea_code (public_code)`, `idx_ea_exam (exam_id)`.

### 1.2. `assignment_item` (PR A) – thành phần đề chốt lúc giao

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `assignment_id` | INT NOT NULL → `exam_assignment` ON DELETE CASCADE | |
| `slot_id` | INT NOT NULL → `exam_slot(id)` | |
| `bank_item_id` | INT NOT NULL | câu học sinh thực sự thấy |
| `question_type` | VARCHAR(10) NOT NULL | FK kép `(bank_item_id, question_type)` → `bank_item(id, question_type)` (có sẵn `uq_bi_id_type`) |
| `part` | VARCHAR(3) NOT NULL | `I`, `II`, `III` |
| `number_in_part` | INT NOT NULL | số "Câu k" như trang đề và file Word |
| `position` | INT NOT NULL | thứ tự toàn đề |

PK `(assignment_id, slot_id)`; UNIQUE `(assignment_id, position)`. Thứ tự lấy từ `ExamViewService` (phần I, II, III; theo ô rồi số
thứ tự) để trùng với trang đề. Phương án A–D và ý a–d **không trộn**.

### 1.3. `assignment_attempt` (PR A)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | SERIAL PK | |
| `assignment_id` | INT NOT NULL → CASCADE | |
| `class_label` | VARCHAR(10) NOT NULL | học sinh gõ tự do; server chuẩn hóa: trim, bỏ mọi khoảng trắng, viết hoa (`8a1`, ` 8A 1` → `8A1`); chỉ chữ, số, `/`, `-`, `.`; 1–10 ký tự |
| `seat_no` | INT NOT NULL | CHECK 1..99 |
| `token_hash` | VARCHAR(64) NOT NULL UNIQUE | SHA-256 (hex) của token 32 byte ngẫu nhiên; token thô chỉ trả một lần lúc bắt đầu. Token đủ entropy nên không cần bcrypt |
| `started_at` | TIMESTAMP NOT NULL | |
| `deadline_at` | TIMESTAMP NOT NULL | `min(started_at + duration, closes_at)`, tính ở server lúc bắt đầu |
| `submitted_at` | TIMESTAMP | NULL = chưa nộp |
| `score`, `max_score` | NUMERIC(5,2) | có khi đã nộp |

UNIQUE `(assignment_id, class_label, seat_no)` (cũng là index theo bài giao). **Không có cột IP, user-agent, họ tên, biệt danh.**

### 1.4. `attempt_answer` (PR A) – ghi một lần lúc nộp, đủ mọi câu kể cả câu bỏ trống

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `attempt_id` | INT NOT NULL → CASCADE | |
| `slot_id` | INT NOT NULL | PK `(attempt_id, slot_id)` |
| `mcq_choice` | VARCHAR(1) | `A`..`D`, NULL = bỏ trống |
| `tf_choices` | VARCHAR(4) | 4 ký tự theo ý a–d, mỗi ký tự `T`, `F` hoặc `-` (bỏ trống), ví dụ `TF-T` |
| `sa_text` | VARCHAR(20) | nguyên văn học sinh gõ (đã trim) |
| `correct_count` | SMALLINT NOT NULL | mcq, sa: 0 hoặc 1; tf: 0..4 |
| `score` | NUMERIC(4,2) NOT NULL | |

### 1.5. `item_stat` (PR B)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `assignment_id`, `slot_id` | INT | PK; FK → `assignment_item` ON DELETE CASCADE |
| `bank_item_id`, `question_type` | | chép từ `assignment_item` để truy vấn `docs/thong-ke` không phải join lại |
| `n` | INT NOT NULL | số lượt đã nộp |
| `group_size` | INT NOT NULL | k = số lượt mỗi nhóm cao / thấp |
| `p` | NUMERIC(5,4) NOT NULL | |
| `d` | NUMERIC(5,4) | NULL khi k = 0 |
| `choice_counts` | JSONB NOT NULL | mục 5.3 |
| `flags` | JSONB NOT NULL | mảng mã cờ, `[]` khi không có |
| `computed_at` | TIMESTAMP NOT NULL | |

Index `idx_is_bank_item (bank_item_id)`. Không có cột cộng dồn trên `bank_item`, không lọc ngân hàng theo cờ (đúng phạm vi).

## 2. Quy tắc nghiệp vụ

### 2.1. Điều kiện giao bài (kiểm trong một transaction, trước mọi thay đổi)

| Điều kiện | Lỗi |
|---|---|
| Đề của người gọi | 404 |
| Đề có ít nhất một chỗ; mọi chỗ `state ∈ {kept, edited}` (đã chốt, câu hỏi 1) | 409 `ASSIGN_NOT_ALL_KEPT`, kèm `slots: [{slotId, part, number}]` |
| Mọi khóa `sa` ở dạng chặt (số nguyên / thập phân; câu nhập từ Word hoặc trước 27/10 có thể là phân số, căn mà điện thoại không gõ được) | 409 `ASSIGN_SA_KEY_UNSUPPORTED`, kèm danh sách câu |
| `closesAt` sau hiện tại ít nhất `durationMinutes`, không quá 30 ngày; `durationMinutes` 5..180; đã tick đồng ý | 400 `INVALID_REQUEST` |

Câu có hình: chưa có trên main nên **chưa chặn** (TODO ở mục 6).

Một đề giao được nhiều lần (mỗi lần một `public_code`). Đổi / Sửa câu trên đề sau khi giao **không ảnh hưởng** bài đã giao (F1).

### 2.2. Vòng đời

- `open` → `closed` khi giáo viên bấm Đóng, hoặc **lười**: lần đầu một API (giáo viên hay công khai) đọc bài giao sau `closes_at`
  thì chuyển `closed`, `closed_at = closes_at`. Không thêm scheduler (F11).
- Đóng tay khi còn lượt đang làm: `closes_at = now`; các lượt chưa nộp không nộp được nữa và **không vào thống kê**. Giao diện cảnh
  báo "Còn k học sinh đang làm" trước khi đóng (câu hỏi 5).
- Nộp được khi `now ≤ deadline_at + 30 giây` (bù trễ mạng; trình duyệt tự nộp khi đồng hồ về 0). Đồng hồ trình duyệt chỉ để hiển
  thị, lệch giờ máy được bù bằng `serverNow`.
- Nộp một lần: `UPDATE assignment_attempt SET submitted_at = … WHERE id = ? AND submitted_at IS NULL`; 0 dòng thì 409
  `ATTEMPT_ALREADY_SUBMITTED`. Ghi `attempt_answer` và điểm trong cùng transaction.
- Trần 300 lượt mỗi bài giao (409 `ASSIGNMENT_FULL`) để link lộ ra ngoài không làm đầy database.

## 3. API

Quy ước lỗi như mục 1 của `docs/api/README.md` (`{"error": "…tiếng Việt…", "code": "…"}`). Sẽ thêm mục 11 vào tài liệu API và
enum mới (`AssignmentStatus`, mã cờ hậu kiểm) vào `docs/contract/enums.md`.

### 3.1. Giáo viên (vai trò TEACHER, chỉ chủ đề; đề của người khác trả 404)

| # | Method, path | Request | Response | Lỗi |
|---|---|---|---|---|
| T1 | `POST /api/exams/{examId}/assignments` | `{durationMinutes, closesAt, showScore, consentConfirmed: true}` | 201 bài giao (như T3, chưa có lượt) | mục 2.1 |
| T2 | `GET /api/exams/{examId}/assignments` | | `[{assignmentId, publicCode, status, durationMinutes, closesAt, showScore, createdAt, startedCount, submittedCount, statsComputedAt}]` | 404 |
| T3 | `GET /api/exams/{examId}/assignments/{id}` | | bài giao + bảng điểm (dưới) | 404 |
| T4 | `POST …/{id}/close` | | 200 bài giao; đã đóng thì trả nguyên trạng (idempotent). ~~PR B: tính thống kê trong cùng lời gọi~~ (bỏ, mục 14 ý 1) | 404 |
| T5 | `DELETE …/{id}/attempts/{attemptId}` | | 204; chỉ khi bài còn mở | 409 `ASSIGNMENT_CLOSED`, 404 |
| T6 | `DELETE …/{id}` | | 204; xóa dây chuyền lượt làm, câu trả lời, thống kê | 404 |
| B1 | `GET …/{id}/item-stats` (PR B) | | mục 5.5 | 409 `ASSIGNMENT_OPEN`, 404 |
| B2 | `POST …/{id}/item-stats/recompute` (PR B) | | như B1 | 409 `ASSIGNMENT_OPEN`, 404 |

Bảng điểm trong T3:

```json
{"assignmentId": 7, "publicCode": "K7M2QX9RTB", "status": "open",
 "durationMinutes": 45, "closesAt": "2026-11-20T17:00:00", "showScore": true, "maxScore": 7.0, "questionCount": 18,
 "attempts": [{"attemptId": 31, "classLabel": "8A1", "seatNo": 12, "state": "submitted",
   "startedAt": "…", "submittedAt": "…", "score": 5.25, "score10": 7.5,
   "parts": [{"part": "I", "correct": 10, "total": 12, "score": 2.5}, {"part": "II", "correct": 5, "total": 8, "score": 0.75},
             {"part": "III", "correct": 4, "total": 4, "score": 2.0}]}]}
```

`state`: `in_progress`, `submitted`, `expired` (quá hạn mà chưa nộp). Phần II đếm theo **ý**. Sắp xếp theo lớp rồi số thứ tự.
Link học sinh do frontend ghép: `<origin>/lam-bai/<publicCode>` (backend không biết tên miền công khai).

### 3.2. Công khai (không đăng nhập; `Cache-Control: no-store`)

Token lượt làm gửi ở header `X-Attempt-Token`, **không** đặt trong URL (URL nằm trong log và lịch sử trình duyệt).

| # | Method, path | Request | Response | Lỗi |
|---|---|---|---|---|
| P1 | `GET /api/public/assignments/{code}` | | `{title, subject, gradeLevel, questionCount, durationMinutes, closesAt, status, showScore}`; **không có câu hỏi** | 404, 429 |
| P2 | `POST /api/public/assignments/{code}/attempts` | `{classLabel, seatNo}` | 201 `{attemptToken, startedAt, deadlineAt, serverNow, parts: […]}` | 400, 404, 409 `ASSIGNMENT_CLOSED` / `IDENTITY_TAKEN` / `ASSIGNMENT_FULL`, 429 |
| P3 | `GET /api/public/attempts/current` | header token | như P2 không có `attemptToken`, thêm `submitted` và (đã nộp + `showScore`) `result` | 401 `ATTEMPT_TOKEN_INVALID`, 429 |
| P4 | `POST /api/public/attempts/current/submit` | header token; `{answers: [{slotId, choice}, {slotId, tf: {"a":"T","b":"F"}}, {slotId, text}]}` | 200 `{submittedAt, result?}` | 400, 401, 409 `ATTEMPT_ALREADY_SUBMITTED` / `ATTEMPT_EXPIRED`, 429 |

Câu hỏi trong `parts` (DTO riêng `PublicQuestionDto`, dựng từ danh sách trường **cho phép**, không tái dùng DTO xem đề):

```json
{"part": "II", "items": [{"slotId": 205, "number": 1, "questionType": "tf", "questionText": "Cho tam giác $ABC$…",
  "statements": [{"key": "a", "text": "…"}, {"key": "b", "text": "…"}, {"key": "c", "text": "…"}, {"key": "d", "text": "…"}]}]}
```

`mcq` có `options: {"A": "…", …}`; `sa` chỉ có đề. **Không bao giờ có**: `correctOption`, `label`, `saAnswer`, `explanation`,
`difficulty`, `bankItemId`, `questionId`, cờ QC. `result` (chỉ khi `showScore`):
`{score, maxScore, score10, parts: [{part, correct, total, score}]}`, không có đáp án từng câu (câu hỏi 3).
`answers` thiếu câu nào thì câu đó là bỏ trống; `slotId` lạ, `choice` ngoài A–D, khóa ý ngoài a–d, `text` dài quá 20 → 400, không ghi gì.

### 3.3. Giới hạn tần suất theo IP

Lớp mới `SlidingWindowIpLimiter` ở `common.security`, cùng thuật toán với `LoginIpLimiter` (không sửa `LoginIpLimiter` trong PR
này; gộp hai lớp là việc dọn sau). Vượt thì 429 `TOO_MANY_REQUESTS` kèm `Retry-After`.

| Nhóm | Mặc định mỗi IP | Lý do |
|---|---|---|
| P1 | 120 / phút | cả lớp quét QR cùng lúc qua một IP wifi |
| P2 (tạo lượt) | 60 / phút | 40 em bấm Bắt đầu trong một phút vẫn qua; kèm trần 300 lượt mỗi bài |
| P3, P4 | 120 / phút | tải lại trang, nộp |

Cấu hình bằng biến môi trường (`PUBLIC_*`). Giới hạn đã biết, giống đăng nhập: bộ đếm trong bộ nhớ một tiến trình; sau nginx cần
`FORWARD_HEADERS_STRATEGY=native`. Mã 10 ký tự + 404 đồng nhất + hạn mức P1 làm việc dò mã không khả thi.

## 4. Chấm điểm

Điểm dùng `BigDecimal`, lấy từ `exam_assignment.scoring`. Điểm lượt = tổng điểm câu; `score10 = score / maxScore × 10`, làm tròn
2 chữ số (HALF_UP). `maxScore` = 0,25 × số câu mcq + 1,0 × số câu tf + 0,5 × số câu sa.

**Trắc nghiệm.** Đúng khi `choice = correct_option`. Bỏ trống hoặc sai: 0. Đúng: 0,25.

**Đúng/Sai.** Mỗi ý học sinh chọn Đ, S hoặc bỏ trống; ý đúng khi trùng `bank_item_statement.label`; bỏ trống là sai (không trừ
điểm). Điểm câu theo **số ý đúng**, đúng thang đang in trong file Word (`ExamMarkdownBuilder.answers`):

| Số ý đúng | 0 | 1 | 2 | 3 | 4 |
|---|---|---|---|---|---|
| Điểm | 0 | 0,1 | 0,25 | 0,5 | 1,0 |

Ví dụ nhãn a–d = Đ, S, S, Đ: trả lời `Đ, Đ, S, bỏ trống` → đúng ý a, c → 2 ý → 0,25. Trả lời `Đ, S, S, S` → 3 ý → 0,5.

**Trả lời ngắn.** `sa_text` rỗng → sai. Ngược lại `ShortAnswerValue.parse(text, LENIENT)`: không phân tích được → sai; phân
tích được → đúng khi `sameValue` với khóa. Đúng: 0,5. Ví dụ (các dòng này thành test tham số hóa ở PR A):

| Khóa | Học sinh gõ | Kết quả | Vì |
|---|---|---|---|
| `2,5` | `2,5` · `2.5` · `2,50` · ` 2,5 ` | đúng | cùng giá trị; dấu phẩy và dấu chấm đều là dấu thập phân |
| `2,5` | `2,5 cm` · `x = 2,5` | sai | không phải một số |
| `-3` | `-3` · `−3` (U+2212) · `-3,0` | đúng | chuẩn hóa dấu trừ |
| `-3` | `3` | sai | |
| `1234` | `1 234` | đúng | bỏ khoảng trắng |
| `1234` | `1.234` | sai | dấu chấm trước đúng 3 chữ số bị từ chối vì mơ hồ (quy tắc sẵn có) |
| `0,5` | `0.5` · `0,50` | đúng | |
| `0,5` | `,5` · `+0,5` | sai | không thuộc ngữ pháp |

Ô nhập trên điện thoại chỉ nhận chữ số, `,`, `.`, `-` (tối đa 10 ký tự) và báo ngay khi gặp dạng `1.234` ("viết liền, không dùng dấu
chấm ngăn hàng nghìn"), nên các dòng "sai vì dạng" hiếm khi tới server. Không làm tròn hộ học sinh: khóa `0,33` thì `0,333` sai.

## 5. B – Phân tích câu

### 5.1. Dữ liệu vào

Các lượt **đã nộp** của một bài giao (n). ~~Tính khi đóng (T4, kể cả đóng lười)~~ (thay bằng mục 14 ý 1: tính khi đọc lần đầu
sau lúc đóng + 2 phút ân hạn) và khi bấm "Tính lại" (B2): xóa các dòng `item_stat`
của bài giao rồi ghi lại trong một transaction, cập nhật `stats_computed_at`. Vài nghìn dòng, tính trong bộ nhớ, không cần job.

### 5.2. Công thức

- Xếp các lượt theo `score` giảm dần; hòa thì theo số câu đúng rồi `attempt.id` (để kết quả lặp lại được).
- k = round(0,27 × n). Nhóm cao H = k lượt đầu, nhóm thấp L = k lượt cuối.
- Với câu chấm đúng/sai (mcq, sa), `c` = số lượt đúng:
  **p = c / n**; **D = (c_H − c_L) / k**.
- Câu tf: tính cho **từng ý** như một câu đúng/sai (p_a, D_a, …); cấp câu lấy theo số ý:
  **p = (tổng số ý đúng) / (4n)**; **D = (tổng ý đúng của H − tổng ý đúng của L) / (4k)**. Không dùng điểm vì thang 0 / 0,1 / 0,25 /
  0,5 / 1 không tuyến tính.
- Bỏ trống tính là sai và được đếm riêng (`blank`).

Lưu ý phương pháp: D dùng tổng điểm có chứa chính câu đang xét (không hiệu chỉnh), nên D hơi cao với đề ít câu.

### 5.3. `choice_counts`

```json
mcq: {"A": {"all": 6, "high": 1, "low": 3}, "B": {…}, "C": {…}, "D": {…}, "blank": {"all": 1, "high": 0, "low": 1}}
tf:  {"a": {"T": 30, "F": 6, "blank": 2, "correct": 30, "p": 0.7895, "d": 0.4}, "b": {…}, "c": {…}, "d": {…}}
sa:  {"correct": 20, "blank": 5, "topWrong": [{"value": "2,5", "count": 6}, {"value": "25", "count": 3}]}
```

`topWrong`: tối đa 5 đáp số sai hay gặp nhất, gom theo giá trị (`canonicalKey`), đáp số không phân tích được gom theo chuỗi.

### 5.4. Cờ hậu kiểm

Chỉ gắn khi **n ≥ 20** (k ≥ 5); dưới ngưỡng `flags = ["insufficient_sample"]` và chỉ hiện p cùng số lượt chọn (câu hỏi 7).

| Mã | Điều kiện | Áp dụng | Chuỗi hiển thị đề xuất |
|---|---|---|---|
| `low_discrimination` | D < 0,2 | mọi dạng (tf: cấp câu) | "Câu phân biệt kém (D = …)" |
| `unused_option: <X>` | phương án sai X có `all / n` < 5% | mcq | "Phương án X gần như không ai chọn" |
| `key_suspect: <X>` | phương án **sai** X có `high > low` và `high ≥ max(2, 20% × k)` | mcq | "Nhóm điểm cao chọn X nhiều hơn nhóm điểm thấp, nên kiểm lại đáp án" |
| `key_suspect: <ý>` | D của ý < 0 và chênh ít nhất 2 lượt | tf | "Ý b: nhóm điểm cao sai nhiều hơn nhóm điểm thấp, nên kiểm lại nhãn" |
| `key_suspect` | D < 0 và chênh ít nhất 2 lượt | sa | "Nhóm điểm cao sai nhiều hơn, nên kiểm lại đáp số" |

Ngưỡng "ít nhất 2 lượt" là phần thêm so với phạm vi ("nhóm cao chọn một phương án sai nhiều hơn nhóm thấp"): với k ≈ 10, chênh
một lượt xảy ra thường xuyên do ngẫu nhiên.

### 5.5. API và giao diện

B1 trả `{assignmentId, n, groupSize, computedAt, items: [{slotId, part, number, questionType, bankItemId, p, d, choiceCounts, flags,
slotChanged}]}`; `slotChanged = true` khi chỗ đó trên đề đã Đổi sang câu khác sau lúc giao. Giao diện: một bảng trong mục "Bài đã
giao" của trang đề, cột Câu | Dạng | p | D | Lượt chọn (A/B/C/D, in đậm đáp án; tf: tỉ lệ đúng từng ý; sa: số đúng + đáp số sai hay
gặp) | Cờ; dòng trên bảng ghi n, k và câu nhắc giới hạn mẫu; nút "Tính lại". Không biểu đồ.

### 5.6. Bảng chéo tiền kiểm × hậu kiểm (SQL, không giao diện)

**Thay bằng mục 14 ý 4 (17/11):** file 11 tự tính lại thống kê từ bài làm gốc; bảng chéo làm bằng Python, không bằng SQL.
Đoạn dưới giữ để tra cứu.

File `docs/thong-ke/11-hau-kiem.sql`: một câu `SELECT`, mỗi dòng `item_stat` (đủ mẫu) xếp vào bảng 2 × 2
"có cờ tiền kiểm lúc Giữ" × "có cờ hậu kiểm", tách theo dạng câu; câu nhập từ Word (không qua QC) là hàng riêng. Đi từ
`item_stat.bank_item_id → bank_item.source_question_id → quality_check_result`. **"Cờ tiền kiểm" = định nghĩa của D2
(`hasContentFlag`)** (đã chốt); cách viết lại bằng SQL chốt ở bước inspect của PR B. Thêm vào
`StatisticsSqlTest` và bảng trong `docs/thong-ke/README.md`.

### 5.7. Giới hạn thống kê (ghi vào báo cáo và hiện ngay trên bảng)

| | Một lớp, n = 38 | Hai lớp, n = 76 |
|---|---|---|
| k (27%) | 10 | 21 |
| Một học sinh đổi câu trả lời làm D đổi | 0,10 | 0,05 |
| Sai số chuẩn lớn nhất của D, √(2 × 0,25 / k) | 0,22 (khoảng 95% ≈ ±0,44) | 0,15 (≈ ±0,30) |
| Sai số chuẩn lớn nhất của p, √(0,25 / n) | 0,08 (≈ ±0,16) | 0,06 (≈ ±0,11) |
| "< 5% chọn" nghĩa là | 0 hoặc 1 em | 0–3 em |

- Ngưỡng D < 0,2 nằm trong vùng nhiễu với một lớp: cờ là **gợi ý xem lại**, không phải kết luận câu kém.
- Phương án mà thật ra 10% học sinh sẽ chọn vẫn có khoảng 9,5% khả năng bị gắn `unused_option` khi n = 38
  (0,9^38 + 38 × 0,1 × 0,9^37).
- **Khuyến nghị: một bài giao cho 2 lớp cùng đề** (một link, mỗi em tự gõ lớp của mình) để n ≈ 70–80. Hai bài giao riêng của cùng đề
  không được gộp trong bản này.
- Học sinh không bị chọn ngẫu nhiên và không chống gian lận: số liệu mô tả đúng lớp đó, không khái quát cho câu hỏi nói chung.

## 6. Câu có hình

- Hiện tại main chưa có câu có hình nên **chưa chặn gì** (đã chốt 14/11).
- **TODO cho PR câu có hình:** thêm vào `AssignmentService.create` điều kiện "không câu nào trong đề có hình" → 409
  `ASSIGN_HAS_FIGURE` (nút Giao bài mờ kèm lý do), kèm test. Thiếu việc này thì học sinh nhận câu thiếu hình.
- Lý do không làm ngay: ảnh hiện thiết kế trả bằng URL có chữ ký cho người đã đăng nhập (H3). Mở cho học sinh cần một đường ảnh
  công khai gắn với token lượt làm, là một PR riêng sau này.

## 7. Quyền riêng tư

- **Thu thập:** lớp, số thứ tự, câu trả lời, giờ bắt đầu và nộp. **Không thu:** họ tên, email, số điện thoại, IP,
  user-agent, định danh thiết bị. IP chỉ nằm trong bộ đếm tần suất lúc tạo lượt, ở bộ nhớ, tối đa một phút. (16/11, sửa câu
  cũ "log truy cập của nginx vẫn có IP": máy chủ dùng Caddy và hiện không bật nhật ký truy cập, nên IP không được ghi; nếu sau
  này bật để điều tra thì che IP và xoay vòng 7 ngày, ghi vào `docs/deploy`.)
- Lớp + số thứ tự là dữ liệu giả danh: chỉ giáo viên của lớp nối được với một em cụ thể. Vẫn coi là dữ liệu cá nhân của trẻ em.
- **Giáo viên đồng ý:** hộp Giao bài có ô bắt buộc tick, lưu `consent_confirmed_at`: "Tôi xác nhận việc cho học sinh làm bài
  trên hệ thống này đã được nhà trường cho phép, phụ huynh đã được thông báo theo quy định của trường, và tôi sẽ không yêu cầu
  học sinh nhập họ tên." Báo cáo chỉ ghi "hệ thống không thu họ tên học sinh", không trích văn bản pháp lý (đã chốt 14/11).
- **Trang học sinh** ghi rõ trước khi bắt đầu: "Không nhập họ tên. Hệ thống chỉ lưu lớp, số thứ tự và bài làm của em để thầy cô
  xem điểm." Ô lớp chỉ nhận chữ, số, `/`, `-`, `.`, tối đa 10 ký tự nên không gõ được họ tên.
- **Chỉ chủ đề xem được** bảng điểm và thống kê; admin không có API xem. `item_stat` và các truy vấn `docs/thong-ke` chỉ có số
  gộp, không có danh tính.
- **Xóa:** T6 xóa toàn bộ dữ liệu của bài giao; T5 xóa một lượt. Thời hạn lưu: câu hỏi 6.
- Kết quả không hiện đáp án và lời giải cho học sinh (tránh lộ đề cho lớp sau; câu hỏi 3).

## 8. Luồng màn hình

**Giáo viên** (`features/assignment`, gắn vào `ExamDetailPage` bằng một component, sửa trang đề ở mức tối thiểu):

1. Trang đề: nút **Giao bài**. Còn câu chưa Giữ thì nút mờ, tooltip "Còn k câu chưa Giữ".
2. Hộp Giao bài: thời gian làm (phút), hạn đóng (ngày giờ), "Hiện điểm cho học sinh sau khi
   nộp", ô đồng ý → **Giao**.
3. Hộp kết quả: link (nút Sao chép), mã QR (antd `QRCode`, nút Tải ảnh để chiếu lên bảng).
4. Mục **Bài đã giao** dưới tiêu đề đề: mỗi bài một dòng (trạng thái, đã vào / đã nộp, hạn đóng) với Link/QR, **Bảng điểm**,
   **Đóng**, **Xóa**.
5. Bảng điểm (drawer): Lớp | STT | Trạng thái | Điểm phần I, II, III | Tổng | Thang 10 | Nộp lúc; nút xóa lượt khi bài
   còn mở; tự làm mới 15 giây một lần khi bài mở.
6. PR B: tab **Phân tích câu** trong drawer đó (mục 5.5).

**Học sinh** (đường `/lam-bai/:code`, ngoài `RequireAuth` và `AppLayout`, nạp lười; gọi API bằng một axios riêng không có
interceptor đăng nhập / refresh của `apiClient`):

1. **Chào:** tên đề, số câu, thời gian, hạn đóng, ghi chú quyền riêng tư; gõ lớp (ví dụ 8A1), nhập số thứ tự →
   **Bắt đầu**. Bài đã đóng: "Bài đã đóng". Số thứ tự đã có người dùng: "Số thứ tự này đã vào làm bài. Nếu là em, hãy mở lại bằng
   đúng máy đã dùng; nếu không, báo thầy cô."
2. **Làm bài:** thanh dính trên cùng có đồng hồ đếm ngược, "Đã làm x/y", nút Nộp. Một trang cuộn theo Phần I, II, III; bảng số câu
   để nhảy nhanh. mcq: 4 hàng chạm cả hàng; tf: mỗi ý hai nút Đ / S; sa: ô nhập chữ có nút `−` và `,` bên cạnh (bàn phím số của iOS
   không có dấu trừ). Công thức qua `MathText`; công thức riêng dòng rộng hơn màn hình thì cuộn ngang trong khung. Còn 5 phút đổi
   màu đồng hồ; về 0 tự nộp.
3. **Nộp:** hộp xác nhận nêu số câu chưa làm → kết quả: điểm theo phần + tổng + thang 10 (khi `showScore`) hoặc "Đã nộp bài".
4. Tải lại trang hoặc mở lại link trên cùng máy: đọc token trong `localStorage`, gọi P3, làm tiếp với thời gian còn lại; đã nộp thì
   về màn kết quả.

## 9. Danh sách test

Backend (chạy bằng `back_end/run-tests.ps1`, database `datn_mcq_test`, không gọi Gemini):

| Nhóm | Ca |
|---|---|
| Migration | ràng buộc CHECK và unique của 4 bảng A, 1 bảng B; xóa dây chuyền |
| Giao bài | đủ điều kiện → 201, `assignment_item` đúng thứ tự trang đề; còn chỗ `ready` / `failed` / `generating` → 409 kèm danh sách; khóa sa không ở dạng chặt → 409; đề người khác → 404; thiếu tick đồng ý, `closesAt` sai → 400; Đổi câu sau khi giao không đổi `assignment_item` |
| **Không lộ đáp án** | JSON của P2 và P3 không chứa khóa `correctOption`, `label`, `saAnswer`, `explanation`, `difficulty`, `bankItemId` (duyệt đệ quy cả ba dạng câu); P1 không có câu hỏi |
| Lượt làm | token thô không nằm trong DB (chỉ SHA-256); token sai → 401; trùng lớp + STT → 409, kể cả khi gõ lớp khác hoa thường hoặc có khoảng trắng; lớp có ký tự lạ → 400; `deadline_at = min(…)`; bài đóng → 409; trần 300 |
| Nộp | nộp hai lần (cả hai luồng đồng thời) → đúng một lần thành công; quá hạn + 30 giây → 409; trong 30 giây → 200; `slotId` lạ → 400 và không ghi gì; thiếu câu → bỏ trống |
| Chấm | bảng ví dụ mục 4 (tham số hóa); tf 0..4 ý đúng và ý bỏ trống; tổng điểm, `score10`; điểm dùng ảnh chụp `scoring` khi cấu hình đổi |
| Quyền | T1–T6, B1–B2 không token → 401, admin → 403, giáo viên khác → 404; `/api/public/**` không cần đăng nhập; các đường khác vẫn bị chặn như cũ |
| Tần suất | vượt hạn mức → 429 + `Retry-After`; lần bị chặn không tính; IPv6 gom /64 (đồng hồ giả) |
| Vòng đời | đóng tay idempotent; đóng lười sau `closes_at`; xóa lượt khi đã đóng → 409; xóa bài giao xóa hết |
| B – công thức | bộ dữ liệu tay 20 lượt: p, D, k, đếm phương án theo nhóm, hòa điểm ở ranh giới nhóm; tf theo ý; sa `topWrong`; n < 20 → `insufficient_sample`; từng cờ có ca bật và ca không bật; tính lại cho cùng kết quả; lượt chưa nộp không được tính |
| B – SQL | `StatisticsSqlTest` cho file 11: một câu lệnh, số đếm đúng trên dữ liệu mẫu |

Frontend: `npm run lint`, `npm run build`; kiểm tay theo danh sách trên điện thoại thật (Chrome Android, Safari iOS, màn 360 px):
công thức dài, đồng hồ, tự nộp, tải lại trang giữa chừng, mất mạng lúc nộp, bài đã đóng, quét QR. Không thêm khung test frontend
trong đợt này (F10).

## 10. Ước lượng công và chia PR

Ba PR, mỗi PR một nhánh, test-first; An commit / push xong PR trước mới sang PR sau. Ước lượng theo **giờ agent** và **thời gian
người duyệt** (đọc diff, chạy thử), không theo ngày công viết tay.

| PR | Nhánh | Nội dung | Giờ agent | Người duyệt |
|---|---|---|---|---|
| A-BE | `feature/online-attempt-be` từ `origin/main` | Migration V30; module `assignment`; T1–T6; P1–P4; token băm; chấm ba dạng; `SlidingWindowIpLimiter`; `permitAll` cho `/api/public/**`; DTO công khai; tài liệu API, enum, kiến trúc | 3–4 | 1–1,5 giờ |
| A-FE | `feature/online-attempt-fe` từ nhánh A-BE | Giáo viên: hộp Giao bài, link + QR, mục Bài đã giao, bảng điểm, đóng, xóa. Học sinh: `/lam-bai/:code`, đồng hồ, nộp, `localStorage`, điểm. `tsc -b` + lint | 2–3 | 1 giờ đọc + 30–45 phút thử trên điện thoại |
| B (backend) | `feature/online-item-stat` từ `origin/main` 3401fbb (sau khi A-BE, A-FE đã merge) | Migration **V33**; `item_stat` tính khi đọc lần đầu sau lúc đóng + 2 phút, và khi tính lại; B1, B2; vô hiệu khi xóa lượt; `docs/thong-ke/11-hau-kiem.sql` tự tính lại từ bài làm gốc + test khớp service (mục 14) | 2–3 | 1 giờ |
| B (frontend) | PR riêng sau B backend, trước 22/11 | Tab "Phân tích câu" trong drawer bảng điểm (mục 5.5), hiện `sampleNote` khi n < 20 | | |

Tổng: khoảng 7–10 giờ agent, 4–5 giờ người duyệt, chưa kể một buổi thử với lớp thật. A-FE và B cùng cắt từ A-BE và cùng sửa
trang đề ở frontend nên PR merge sau phải rebase.

## 11. Không làm (nhắc lại phạm vi)

Tài khoản học sinh, quản lý lớp, chống gian lận, trộn đề / trộn phương án theo học sinh, lưu nháp ở server, tự luận, câu có hình,
xuất bảng điểm ra file, gộp thống kê nhiều bài giao, cột cộng dồn trên `bank_item`, lọc ngân hàng theo cờ hậu kiểm, biểu đồ.

## 12. Câu hỏi cho An – ĐÃ CHỐT 14/11/2026

Câu 1, 3, 4, 5, 7, 8: theo đề xuất. **Câu 2: chỉ lớp (gõ tự do) + số thứ tự**; bỏ biệt danh và danh sách lớp do giáo viên nhập.
**Câu 6:** ô tick bắt buộc với lời văn ở mục 7; báo cáo ghi "không thu họ tên", không trích văn bản pháp lý. Bảng dưới giữ
nguyên đề xuất ban đầu để tra cứu.

| # | Điểm cần quyết | Đề xuất |
|---|---|---|
| 1 | Chỗ lấy từ ngân hàng đang `ready` (có `bank_item` nhưng giáo viên chưa bấm Giữ) có được giao không? | **Không.** Bắt buộc mọi chỗ `kept` / `edited`, đúng nghĩa "Giữ = thẩm định"; câu ngân hàng bấm Giữ không tạo đánh giá mới nên chỉ tốn một lần bấm |
| 2 | Danh tính học sinh: lớp + số thứ tự, hay biệt danh? | Giáo viên chọn theo từng bài giao, **mặc định lớp + số thứ tự** (bảng điểm đối chiếu được với sổ điểm, không có chữ tự do). Lớp chọn từ danh sách giáo viên nhập, không gõ tay |
| 3 | Bật "hiện điểm" thì học sinh thấy gì? | Chỉ điểm theo phần, tổng và thang 10. **Không** hiện đáp án, lời giải, câu nào sai (đề còn dùng cho lớp sau) |
| 4 | Thang điểm khi tổng đề khác 10 | Dùng nguyên `app.exam.scoring` (mcq 0,25; sa 0,5; tf 0 / 0,1 / 0,25 / 0,5 / 1,0), hiện cả điểm thô / tối đa và quy đổi thang 10 |
| 5 | Trùng danh tính, làm lại, đóng tay giữa giờ | Chặn trùng lớp + STT; giáo viên xóa lượt để em đó vào lại (chỉ khi bài còn mở). Đóng tay: lượt chưa nộp bị loại, có cảnh báo trước; nộp trễ tối đa 30 giây |
| 6 | Lời đồng ý và thời hạn giữ dữ liệu | Ô tick bắt buộc của giáo viên (lời văn mục 7); giữ dữ liệu tới khi giáo viên xóa bài giao; **không** tự xóa theo hạn. Cần An / GVHD xác nhận lời văn và căn cứ pháp lý |
| 7 | Ngưỡng cờ hậu kiểm với mẫu nhỏ | Chỉ gắn cờ khi n ≥ 20; `key_suspect` cần chênh ít nhất 2 lượt; gộp 2 lớp bằng **một bài giao nhiều lớp**, không gộp nhiều bài giao |
| 8 | Liên quan Q-4c: câu ngân hàng đã nằm trong bài giao có được sửa / gỡ về sau không? | Giữ hiện trạng (chưa có API sửa `bank_item`). Nếu sau này mở Q-4c thì chặn sửa câu đã có trong `assignment_item`, không chép nội dung câu vào bài giao ngay bây giờ |

## 13. Quyết định ngày 16/11/2026 (sau rà soát an ninh 07) – thắng các mục trên khi mâu thuẫn

An chốt sau báo cáo `.local/review/07-an-ninh-van-hanh.md` (AN-01..AN-04, VH-02). Đã code ở PR A-BE (V32).

1. **Kết quả chỉ trả sau khi bài đóng** (AN-01, thay câu hỏi 3 và P2–P4 ở mục 3.2). P4 chỉ trả `{submittedAt, resultsAt}`.
   P3 trả `result` (khi `showScore`) và `review` (khi `showSolutions`, ý 6) chỉ khi bài đã đóng **và đã qua ân hạn nộp tính
   từ lúc đóng** (`closed_at + 2 phút`): lúc đó không lượt nào còn nộp được, nên em nộp xong không chuyển được đáp án cho bạn
   đang trong ân hạn. Trước đó P3 có `resultsReleased = false` và `resultsAt` (dự kiến).
2. **Giáo viên khai lớp + sĩ số khi giao** (AN-02, đảo câu 2 ngày 14/11). T1 nhận `classes: [{classLabel, size}]` (1–10 lớp,
   nhãn chuẩn hóa như mục 1.3, sĩ số 1–99, không trùng), bảng mới `assignment_class`. P1 trả danh sách lớp, học sinh **chọn**
   lớp; P2 từ chối lớp ngoài danh sách và số thứ tự ngoài 1..sĩ số (400). **Trùng lớp + số thứ tự: giữ cả hai lượt** (bỏ
   `UNIQUE` và mã `IDENTITY_TAKEN`), cả hai bị đánh dấu `duplicate` ở bảng điểm, tính lúc đọc (không lưu cờ; xóa lượt sai thì
   lượt còn lại hết cờ); P2/P3 có `duplicateSeat`. Trần lượt = **2 × tổng sĩ số** (thay 300). Thêm API **xóa nhiều lượt**
   `POST …/{id}/attempts/bulk-delete` `{attemptIds}` → `{deleted}`. **PR B:** lượt có cờ trùng không tính vào `item_stat` và
   hậu kiểm.
3. **Xóa đề còn bài giao → 409 `EXAM_HAS_ASSIGNMENTS`** (AN-04), không đổi gì; xóa hết bài giao rồi mới xóa đề. `exam` khai
   interface `ExamDeletionGuard`, `assignment` cài đặt (không có chiều `exam ──► assignment`).
4. **Thời hạn giữ:** bài làm giữ tới khi giáo viên xóa (giữ câu 6). Vì vậy xóa một lượt (T5), xóa nhiều lượt và xóa bài
   giao (T6) làm được ở **mọi trạng thái**, kể cả sau khi đóng (thay "chỉ khi bài còn mở" của T5; việc cho PR B: thống kê
   đã tính phải được tính lại hoặc đánh dấu cũ khi lượt bị xóa sau lúc đóng). Bản sao lưu ở xa xoay vòng 14 ngày: chỉ ghi vào
   `docs/deploy/README.md` mục "Sao lưu và khôi phục".
5. **Giới hạn tần suất** (AN-03, thay mục 3.3): theo IP **chỉ khi tạo lượt** (P2, mặc định 200/phút mỗi IP); P1 không giới
   hạn; P3 và P4 đếm theo **lượt làm**, hai bộ đếm riêng (mặc định 30 và 20/phút mỗi lượt), tính sau khi token khớp (token sai:
   401, không tính). **Ân hạn nộp 2 phút** (thay 30 giây ở mục 2.2). Đóng lười giữ ân hạn cho lượt có hạn bằng hạn đóng; đóng
   tay chặn lượt mà hạn còn sau lúc đóng.
6. **Tùy chọn `showSolutions`** (mặc định false, cột `exam_assignment.show_solutions`): khi bật và kết quả đã mở (ý 1), P3 có
   `review` theo Phần: đề, phương án/ý, lựa chọn của em, đáp án đúng (`correctOption` / `label` từng ý / `saAnswer`), số đúng,
   điểm câu và lời giải. Lượt chưa nộp cũng xem được (phần của em để trống). Khi bài còn mở tuyệt đối không có các trường đó
   (test cho cả hai trạng thái). Câu 3 ngày 14/11 ("không hiện đáp án") chỉ còn đúng khi giáo viên không bật tùy chọn này.

## 14. Quyết định ngày 17/11/2026 (PR B backend, V33) – thắng các mục trên khi mâu thuẫn

An duyệt sau bước inspect của PR B.

1. **Lúc tính:** thống kê chỉ có khi bài đã đóng **và** đã qua ân hạn nộp 2 phút tính từ lúc đóng (`closed_at + 2 phút`), vì
   theo mục 13 ý 5 các lượt có hạn không muộn hơn lúc đóng vẫn nộp được trong ân hạn; tính ngay lúc đóng (T4) sẽ sót các lượt
   đó. T4 không tính nữa. B1 tính khi đọc lần đầu (dưới khóa hàng bài giao), B2 luôn tính lại. Bài mở: 409 `ASSIGNMENT_OPEN`;
   còn ân hạn: 409 `STATS_NOT_READY` kèm lúc có thống kê. Cột mới `exam_assignment.stats_computed_at`.
2. **Lượt được tính:** đã nộp và **không trùng**, dùng đúng một quy tắc "trùng" với bảng điểm T3 (`AttemptRules.duplicateIds`,
   xét mọi lượt kể cả lượt bỏ dở). Hệ quả chấp nhận được: lượt thật bị loại vì có lượt bỏ dở trùng số; giáo viên xóa lượt bỏ dở
   thì thống kê tự tính lại. B1 trả `excluded: {duplicate, notSubmitted}`.
3. **Xóa lượt sau khi đã tính** (mục 13 ý 4): **vô hiệu** trong cùng transaction với T5 hoặc xóa nhiều lượt (xóa dòng
   `item_stat`, `stats_computed_at = NULL`); lần đọc B1 sau tính lại. Chọn vì đơn giản nhất mà không bao giờ hiện số cũ: không cần
   cột hay trạng thái "cũ" trong API và giao diện, giáo viên không phải nhớ bấm Tính lại, đường xóa không phải tính nặng.
4. **Công thức** giữ mục 5.2–5.4; k = round(0,27 × n) tính bằng số nguyên `(27n + 50) / 100`; ngưỡng cờ so bằng số nguyên. n < 20:
   D vẫn tính và trả về, cờ duy nhất là `insufficient_sample`, B1 có `sampleSufficient = false`, `minSampleSize = 20` và
   `sampleNote` (giao diện phải hiện). Lớp thật 35–40 em (hai lớp 70–80) sẽ vượt ngưỡng.
5. **`docs/thong-ke/11-hau-kiem.sql`** tự tính lại n, k, p, D và cờ từ bài làm gốc (không đọc `item_stat`), test
   `HauKiemSqlTest` bắt khớp service. **Không làm file 12 / bảng chéo bằng SQL:** bảng chéo tiền kiểm × hậu kiểm cho chương 4 làm
   bằng Python từ `tools/measurement/do_luong/co_qc.py` (ghép qua `bank_item_id`), để không có bản sao thứ ba của logic cờ.
6. **Chia PR:** nhánh `feature/online-item-stat` chỉ backend; tab "Phân tích câu" là PR frontend riêng ngay sau, trước 22/11.
