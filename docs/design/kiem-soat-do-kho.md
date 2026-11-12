# Kiểm soát độ khó đề Toán THCS và yêu cầu của giáo viên

## TIẾN ĐỘ (cập nhật 12/11/2026)

- **PR A** (`feature/difficulty-control`, PR #67): đã code xong và rebase lên `origin/main` 069d350 (sau #65, #61).
  Toàn bộ test qua, 1841 test, chạy trên Postgres tạm. Migration `V27__exam_challenge.sql`.
- **PR B** (`feature/teacher-requests`): **chưa làm**. Chỉ cắt nhánh từ PR A **sau khi** PR A đã merge. Đặc tả ở mục 4.
- **Chạy thật Gemini** (a)–(d): **chưa chạy**, đang chờ khóa Gemini riêng. Xem mục 5.
- Tài liệu này không chép nội dung đề thật, đề thương mại, tên trường hay tên giáo viên. Đề thật chỉ được dùng để
  rút ra cấu trúc ở mục 1.

## 1. Bối cảnh

**Góp ý dùng thử 11/11:** "đề theo ma trận đơn giản, chưa có chiều sâu kiến thức" và "chưa thấy câu nâng cao".

**Cấu trúc đề thật** (đề giữa kì I Toán 8 của một trường; chỉ đối chiếu, không lưu vào repo):

| Phần | Đề thật | Hệ thống trước 12/11 |
|---|---|---|
| I. Trắc nghiệm | Chủ yếu Nhận biết | Giống |
| II. Đúng/Sai | Mỗi câu **một ngữ cảnh chung**. 4 ý khó dần, ý sau dựa vào ý trước, có ý gắn bối cảnh thực tế (ví dụ hình chữ nhật có chu vi không đổi) | 4 ý rời nhau. Chỉ ý d được dặn "suy luận thêm" |
| III. Trả lời ngắn | Câu mức Vận dụng: "cho điều kiện giữa các biến, tính giá trị biểu thức"; "tính góc từ quan hệ giữa các góc" | Phần lớn thay số vào một công thức |
| IV. Tự luận | Có | Ngoài phạm vi hệ thống |

Phần chiều sâu nằm ở Phần II và III. Ở mức Vận dụng, prompt cũ chỉ cho "phối hợp nhiều bước của chính bài này",
nên câu Vận dụng không được kết hợp kiến thức các bài trước và hay chỉ cần một bước.

**Mục tiêu:**
1. Câu mức Vận dụng có chiều sâu như đề thật: kết hợp kiến thức đã học, nhiều bước, có bối cảnh.
2. Giáo viên chọn được mức thử thách của cả đề (Chuẩn / Nâng cao).
3. Đổi câu theo lý do "Quá dễ" / "Quá khó" thì câu mới thật sự khó hơn / dễ hơn.
4. (PR B) Giáo viên yêu cầu được đặc điểm câu (bối cảnh thực tế, nhiều bước…), và hệ thống kiểm xem câu ra có đúng
   yêu cầu không. Việc kiểm **không tốn thêm lượt gọi Gemini**.

## 2. Câu hỏi thiết kế và quyết định đã chốt

Các câu 1–7 nêu trong báo cáo bước 0 (inspect), An chốt ngày 12/11. Các mục A1–A3 An duyệt sau khi xem PR A.

| # | Câu hỏi | Quyết định | Lý do |
|---|---|---|---|
| 1 | Đề bài ghi ô tự do "≤ 300 ký tự như hiện có", nhưng code hiện là 500 (Đổi câu) và 1000 (đề, ô). Giữ hay hạ? | **Không hạ.** Chốt 12/11: giữ 1000 cho đề và ô. Sau khi duyệt tài liệu này, An đổi: **tăng Đổi câu lên 1000** (PR B làm, xem 4.2) | Hạ là phá API đang chạy. Tăng vì hộp Đổi câu tự điền sẵn văn bản thừa hưởng của ô hoặc đề (tối đa 1000), nên giới hạn phải bằng nhau |
| 2 | Thứ tự ưu tiên Đổi câu > ô > đề: mức cụ thể hơn **thay hẳn** hay **gộp** với mức dưới? | **Thay hẳn**, áp cho cả chip lẫn văn bản, giống `instructionFor` | Đơn giản, dễ giải thích cho giáo viên, và đã có tiền lệ (ô ghi đè đề) |
| 3 | Yêu cầu theo ô "lưu cùng ô" có nghĩa là thêm cột vào `exam_matrix_cell`? | **Không thêm cột vào `exam_matrix_cell`.** Lưu theo đề ở `exam_generation_context`, thêm `request_codes` | Văn bản tự do theo ô cũng đang lưu theo đề (V19). Một ma trận dùng cho nhiều đề, nên không gắn yêu cầu vào ma trận |
| 4 | Đổi hợp đồng API (M-35)? | **Đồng ý.** Thêm `infoReasons` và `requests` vào `ExamItemDto`. Thêm `challenge` (PR A) và `requestCodes` (PR B) vào request tạo đề và Đổi câu. `infoReasons` thiết kế **chung**: sau này dùng thêm cho "Có hình" (câu có hình) và lỗi phương án nhiễu không khớp (PA1) | Cần một loại cờ chỉ để thông tin, không bật "cần xem kỹ" |
| 5 | Solver đang "mù" yêu cầu. Để kiểm `request_check` thì Solver phải thấy yêu cầu: đồng ý không? | **Đồng ý, kèm điều kiện:** khối yêu cầu đặt SAU đề bài, dặn "chỉ xét sau khi đã giải"; `request_check` đứng **cuối** schema; câu không có yêu cầu dùng prompt Solver **y như cũ**. Tên `solver-v5-request-check`; ghi `enums.md` rằng PA1 sẽ là v6 | Không để yêu cầu làm lệch cách Solver giải và xét đáp án |
| 6 | Nhánh `fix/quota-retry-edit-normalize` cũng sửa `ExamBuildService`, `ExamBuildDtos`. Làm trên đâu? | Làm trên `origin/main`. Nhánh đó (#65) merge trước, PR A rebase sau | Đã làm 12/11: hai xung đột được giải bằng cách giữ cả hai phía |
| 7 | Frontend: đã báo Quân chưa? | **Chưa.** PR A chỉ có backend và 1 ô "Mức thử thách" ở 2 form tạo đề (An báo Quân). Giao diện PR B làm sau | Tránh sửa giao diện khi người phụ trách chưa biết |
| — | Chia việc | **PR A** = bước 1–4 và 6, migration chỉ `exam.challenge`. **PR B** = bước 7. **Bỏ bước 5** | Repo không có file ma trận và đặc tả của tổ |
| A1 | Đề Nâng cao: ô Vận dụng có lấy câu từ ngân hàng không? | **Không**, luôn soạn mới | Câu trong ngân hàng được soạn theo mức Chuẩn |
| A2 | Đúng/Sai: mức của từng ý lấy theo đề bài (a Nhận biết … d Vận dụng) hay theo ô ma trận? | **Theo ô ma trận**, không sửa mẫu cũ (mẫu cũ là Biết, Biết, Hiểu, Vận dụng) | Ma trận là hợp đồng với giáo viên. Prompt chỉ ghi "thường a…d" |
| A3 | Form theo bài học, dòng gợi ý "lấy từ ngân hàng hay soạn mới" | Khi chọn Nâng cao thì ghi rõ ô Vận dụng luôn soạn mới | Hệ quả của A1 |

**Số migration:** PR A dùng V27, số kế tiếp của main (V26) lúc rebase. Flyway không chạy migration lệch thứ tự, nên
không giữ chỗ số cho nhánh nào. Mỗi PR lấy số **kế tiếp của main ngay lúc rebase trước khi merge**; PR nào merge trước
lấy số trước (chi tiết ở 4.5).

## 3. PR A (đã làm, PR #67)

### 3.1 Prompt sinh câu theo ma trận (`MatrixPromptDraftV0`)

Phiên bản mới: **`matrix-mcq-v9-draft`, `matrix-sa-v11-draft`, `matrix-tf-v11-draft`** (cũ: mcq v8, sa v10, tf v10).
Run cũ giữ phiên bản cũ. Solver **không đổi** ở PR A.

| Khối | Hằng | Khi nào có |
|---|---|---|
| Dòng phạm vi mức Vận dụng: "được kết hợp kiến thức các bài ĐÃ HỌC trước đó cùng lớp (vẫn trong PHẠM VI KIẾN THỨC này), không đưa kiến thức chưa học" | `APPLY_SCOPE_LINE` (thay dòng "phối hợp nhiều bước của chính bài này") | Mọi ô |
| "YÊU CẦU MỨC VẬN DỤNG": kết hợp kiến thức; lời giải ≥ 2 bước biến đổi có ý nghĩa, không thay số trực tiếp vào một công thức; ưu tiên bối cảnh thực tế, giá trị lớn nhất/nhỏ nhất, nhận định cần suy luận | `APPLY_LEVEL_HEADER`, `APPLY_LEVEL_RULES` | Ô mcq/sa mức `hard`, hoặc ô tf có ≥ 1 ý `hard` |
| "Mức NÂNG CAO": ít nhất 3 bước, có thể có bẫy là lỗi sai thường gặp, kết hợp ít nhất 2 kiến thức | `ADVANCED_RULE` | Như dòng trên, và đề có `challenge = advanced` |
| Câu mẫu "VÍ DỤ ĐỘ SÂU MONG MUỐN" | `depthExamplesBlock` | Có file câu mẫu và có câu khớp ô (xem 3.4) |
| tf ngữ cảnh chung: 4 ý cùng một ngữ cảnh; độ khó tăng dần a→d theo mức của từng ý; ý sau có thể dùng kết quả ý trước; ≥ 1 ý gắn bối cảnh thực tế hoặc hình học | `TF_SHARED_CONTEXT_RULES` | Mọi ô tf. Quy tắc nhãn và phân bố nhãn giữ nguyên |
| sa Vận dụng: ưu tiên dạng "cho điều kiện giữa các biến, tính giá trị biểu thức", "tính nhanh bằng hằng đẳng thức", "tính góc từ quan hệ giữa các góc" | `SA_APPLY_FORMS_RULE` | Ô sa mức `hard` |

Mức Nhận biết và Thông hiểu giữ nguyên prompt.

### 3.2 Mức thử thách của đề (`ExamChallenge`, migration V27)

- Enum `standard | advanced` (`enums/ExamChallenge.java`). Cột `exam.challenge VARCHAR(10) NOT NULL DEFAULT 'standard'`,
  CHECK `chk_exam_challenge`.
- **API:**
  - Nhận `challenge` ở `BuildExamRequest` (tạo đề theo ma trận) và `CreateLessonRequest` (theo bài học).
  - Bỏ trống thì là `standard`. Không phân biệt hoa thường.
  - Giá trị lạ trả 400 `INVALID_REQUEST`, kiểm **trước mọi ghi**.
  - Trả về `challenge` trong `BuildResultDto` và `ExamViewDto`. Danh sách đề chưa có trường này.
- Mức thử thách đi cùng run: `MatrixCellSpec.challenge` nằm trong `experiment_run.hyperparams`, khóa chỉ có khi khác
  `standard`, nên JSON của ô chuẩn giữ nguyên như cũ.
- **Đổi câu giữ mức:** worker dựng lại prompt từ spec của run. Chỗ chưa có run thì lấy mức từ `exam.challenge`.
- **Đề Nâng cao:** ô có mức Vận dụng không lấy câu từ ngân hàng (quyết định A1).

### 3.3 Đổi câu theo lý do `too_easy` / `too_hard`

- Lý do khác `too_easy`/`too_hard`, hoặc không chọn lý do: prompt như cũ.
- Hai lý do này thêm một dòng vào `generation_job.prompt_note`:
  - `too_easy`: "Lý do giáo viên Đổi câu trước: QUÁ DỄ. Câu mới phải cùng ô, cùng mức nhưng khó hơn rõ rệt (thêm bước,
    kết hợp kiến thức, bối cảnh)."
  - `too_hard`: "… QUÁ KHÓ. Câu mới phải cùng ô, cùng mức nhưng dễ hơn, ít bước hơn."
  - Kèm tối đa 200 ký tự đề dẫn của câu cũ, trong `<<< >>>` và đã qua `PromptData.neutralize`.
  - Hàm `replaceReasonBlock`, `reasonBlockOf`.
- Dòng này nằm sau các dòng hệ thống của `prompt_note` và trước khối yêu cầu bổ sung của giáo viên. Prompt đặt cả hai ở cuối.
- Lần sinh lại tự động trong cùng lượt Đổi (`enqueueRetry`) giữ dòng này.
- Câu cũ được đọc **trước** khi bỏ khỏi chỗ (`ExamActionService.currentQuestionText`): câu sinh lấy từ
  `generated_question`, câu ngân hàng lấy từ `bank_item`. Chỉ áp khi Đổi bằng sinh mới.

### 3.4 Câu mẫu độ sâu (`DepthExampleCatalog`)

- File JSON đặt **ngoài repo**, cấu hình `app.prompt.depth-examples-file` (biến môi trường `DEPTH_EXAMPLES_FILE`).
  Nạp một lần lúc khởi động.
- Định dạng: `{"examples":[{"subject","grade","level","questionType","text"}]}`.
  - `level` và `text` bắt buộc.
  - Bỏ trống `subject`/`grade`/`questionType` nghĩa là áp cho mọi giá trị.
  - Phần tử sai bị bỏ qua.
  - Thiếu file, file rỗng hay hỏng: không có câu mẫu. Log chỉ cảnh báo, không in nội dung.
- Mỗi ô lấy tối đa 2 câu cho mỗi mức ô dùng, lưu vào `hyperparams.depthExamples`.
- Mỗi câu mẫu qua `neutralize`, gộp khoảng trắng, cắt 600 ký tự. Prompt dặn "học độ sâu và cách hỏi, KHÔNG chép số liệu hay câu chữ".
- Hiện **chưa có** `data/private/cau-mau-do-sau.json`, nên khi chạy thật sẽ không có khối này.

### 3.5 Frontend (tối thiểu)

- Ô chọn "Mức thử thách" (Chuẩn / Nâng cao (câu Vận dụng khó hơn)) ở `GenerateExamPage` và `BuildByLessonForm`.
- `BuildByLessonForm`: khi chọn Nâng cao mà có câu mức Vận dụng hoặc có câu Đúng/Sai, hiện cảnh báo "câu mức Vận dụng
  … luôn được soạn mới, không lấy từ ngân hàng" (quyết định A3).

### 3.6 Test của PR A

- **Mới:**
  - `MatrixPromptDepthTest` (14): có/không khối Vận dụng, khối nâng cao, tf ngữ cảnh chung, dạng sa Vận dụng, khối lý do
    đã qua neutralize, khối câu mẫu.
  - `DepthExampleCatalogTest` (2): chọn theo môn, lớp, dạng câu, tối đa 2 câu mỗi mức; thiếu file, đường dẫn trống hoặc
    file hỏng thì không có câu mẫu.
  - `ExamDepthControlTest` (9): API `challenge` (nâng cao, trống là chuẩn, giá trị lạ → 400 không tạo đề, theo bài học),
    migration (mặc định và CHECK), ô Vận dụng của đề nâng cao bỏ ngân hàng, Đổi câu giữ mức, lý do quá dễ kèm câu cũ và giữ
    qua lần sinh lại, lý do quá khó và lý do khác.
- **Sửa:** `ExamBuildTest`, `MatrixPromptDraftV0Test` (phiên bản, dòng phạm vi), `exam-view-batch/golden.json` (thêm `challenge`).

### 3.7 Chỗ code khác đặc tả bước 0

| Đặc tả bước 0 | Code | Vì sao |
|---|---|---|
| Bước 5: mẫu ma trận mới theo đặc tả tổ | Không làm | Không có file đặc tả (đã chốt) |
| Bước 2: a Nhận biết, b Thông hiểu, c Thông hiểu/Vận dụng, d Vận dụng | Mức từng ý theo ô ma trận; prompt ghi "thường …" | Quyết định A2 |
| Bước 1: áp quy tắc Vận dụng cho mcq, tf, sa | Dòng phạm vi đổi cho mọi ô; khối quy tắc chi tiết chỉ thêm khi ô có mức Vận dụng | Không làm dài prompt của ô Biết/Hiểu |
| Bước 6: `advanced` chỉ áp lên ô Vận dụng | Như đặc tả, thêm: ô đó bỏ ngân hàng | Quyết định A1 |
| Bước 4: khối lý do đặt trong prompt | Đặt trong `prompt_note` (cuối prompt), giữ qua lần sinh lại | Dùng lại cơ chế khối yêu cầu Đổi câu đã có; không phải sửa spec của run |
| Bước 8: tăng cả phiên bản Solver | Solver không đổi ở PR A | Solver chỉ đổi khi có `request_check` (PR B) |
| Migration: nhiều cột (bản kế hoạch ở bước 0) | Chỉ `exam.challenge` | Chia PR; phần còn lại thuộc PR B |
| Số migration lấy lúc code | V27 | Lấy số kế tiếp của main lúc rebase trước khi merge (main dừng ở V26) |

## 4. PR B: yêu cầu của giáo viên (chưa làm)

**Điều kiện bắt đầu:** PR A đã merge. Cắt nhánh `feature/teacher-requests` từ `origin/main` sau merge, làm trong
worktree riêng, test-first. Tuân thủ `docs/ai-development-rules.md` và `AGENTS.md`.

### 4.1 Danh mục yêu cầu (`TeacherRequest`)

Thêm enum mới, ghi vào `docs/contract/enums.md`. Mỗi mã ứng với **một đoạn prompt cố định**. Đoạn dưới đây là bản đề xuất;
nếu sửa chữ thì sửa ở hằng và ở test, không viết rải rác.

| Mã | Nhãn hiện cho giáo viên | Đoạn prompt sinh câu | Dạng câu |
|---|---|---|---|
| `real_context` | Bối cảnh thực tế | Câu hỏi đặt trong một bối cảnh thực tế quen thuộc với học sinh (đo đạc, mua bán, chuyển động, diện tích…); số liệu hợp lý với bối cảnh. | mcq, tf, sa |
| `multi_step` | Nhiều bước biến đổi | Lời giải cần ít nhất 2 bước biến đổi có ý nghĩa; không giải được bằng cách thay số trực tiếp vào một công thức. | mcq, tf, sa |
| `common_trap` | Có bẫy lỗi hay gặp | Có ít nhất một bẫy là lỗi sai học sinh hay mắc (sai dấu khi phá ngoặc, nhầm hằng đẳng thức…); với trắc nghiệm, ít nhất một phương án nhiễu ứng với lỗi đó. | mcq, tf, sa |
| `extremum` | Tìm giá trị lớn nhất/nhỏ nhất | Câu hỏi yêu cầu tìm giá trị lớn nhất hoặc nhỏ nhất, giải được bằng kiến thức trong lớp. | mcq, tf, sa |
| `geometry_link` | Liên hệ hình học | Câu hỏi gắn với một hình (độ dài, chu vi, diện tích, góc) và dùng kiến thức của bài để giải. | mcq, tf, sa |
| `tf_shared_context` | Đúng/Sai cùng một tình huống | Bốn ý cùng xoay quanh một tình huống của đề dẫn; ý sau dùng kết quả ý trước. | **chỉ tf** |

- **Ghi chú `tf_shared_context`:** từ PR A, mọi ô tf đã có `TF_SHARED_CONTEXT_RULES`. Mã này vẫn giữ để thống kê và
  nhấn mạnh.
- **Mã không hợp dạng câu** (ví dụ `tf_shared_context` cho câu mcq), tùy mức:
  - **Mức đề và mức ô:** API nhận, vì một đề hay một ô có thể gồm nhiều dạng câu. Với câu không hợp dạng, mã đó **không**
    vào prompt, **không** lưu vào câu và **không** được kiểm. Các câu hợp dạng vẫn dùng mã đó bình thường.
  - **Mức Đổi câu:** dạng câu đã biết (dạng của chỗ), nên mã không hợp dạng trả **400 `INVALID_REQUEST`**, kiểm trước mọi
    ghi, kèm thông báo tiếng Việt: "Yêu cầu \"<nhãn chip>\" không áp dụng cho câu <tên dạng câu>." (ví dụ "Yêu cầu \"Đúng/Sai
    cùng một tình huống\" không áp dụng cho câu Trắc nghiệm.").
  - Frontend chỉ hiện chip hợp dạng câu.
- **Khối trong prompt sinh câu:** tiêu đề "YÊU CẦU CỦA GIÁO VIÊN (bắt buộc, không được trái các quy tắc ở trên):", mỗi mã
  một dòng `- <đoạn cố định>`. Văn bản tự do vẫn dùng khối hiện có (`extraBlock` / `teacherBlock`, qua `neutralize`).

### 4.2 Ba chỗ nhận yêu cầu và thứ tự ưu tiên

| Chỗ | API | Lưu ở |
|---|---|---|
| Cả đề, theo ma trận | `BuildExamRequest.requestCodes: string[]` (cạnh `extraInstruction`) | `exam_generation_context` dòng `cell_id IS NULL`, cột mới `request_codes` |
| Cả đề, theo bài học | `CreateLessonRequest.requestCodes` | như trên |
| Từng ô | `CellContextInput.requestCodes` | `exam_generation_context` dòng của ô, `request_codes` |
| Đổi câu | `ReplaceRequest.requestCodes` (`POST /api/exams/{examId}/slots/{slotId}/replace`) | `exam_slot_action.request_codes` (nhật ký) |

**Kiểm đầu vào** (trả 400 `INVALID_REQUEST`, kiểm **trước mọi ghi**):
- Mã lạ trả 400. Mã không phân biệt hoa thường, mã trùng gộp lại.
- Ở mức đề và ô, mảng rỗng hoặc `null` nghĩa là không có yêu cầu.
- Ở **Đổi câu**, `null` và mảng rỗng **khác nhau** (vì hộp Đổi câu chọn sẵn chip thừa hưởng, xem 4.7):
  - `requestCodes` **không gửi hoặc `null`:** thừa hưởng yêu cầu của ô, không có thì của đề.
  - `requestCodes` **có gửi** (kể cả `[]`): mức Đổi câu được coi là **tự đặt**. Chỉ dùng mã và `instruction` của lượt Đổi,
    bỏ hẳn yêu cầu của ô và đề. Gửi `[]` và không có `instruction` thì câu mới **không có yêu cầu nào**: đây là cách giáo
    viên bỏ chọn hết.
- Đổi câu có mã không hợp dạng của chỗ trả 400 kèm thông báo tiếng Việt (4.1).
- **Giới hạn văn bản: 1000 ký tự ở cả ba chỗ** (đề, ô, Đổi câu); ký tự tính theo điểm mã Unicode, sau trim, như hiện nay.
  Đổi câu đang là 500, nên PR B phải sửa đủ các chỗ sau:

  | Chỗ | Hiện tại | PR B sửa thành |
  |---|---|---|
  | `back_end/.../llm/prompt/MatrixPromptDraftV0.java`, hằng `TEACHER_INSTRUCTION_MAX` | `500` (javadoc "ngày 23/10, đợt 3") | `1000`, gán bằng `CellTextLimits.EXTRA_INSTRUCTION_MAX_CHARS` để ba chỗ không lệch nhau; sửa javadoc |
  | `back_end/.../exam/service/ExamActionService.java`, `normalizeInstruction` và javadoc của `replace` (dòng `{@value …TEACHER_INSTRUCTION_MAX}`) | Dùng hằng trên, thông báo "instruction dài tối đa N ký tự (hiện M)" | Không sửa logic, chỉ đổi theo hằng |
  | DB: CHECK `chk_esa_instruction` của `exam_slot_action` (tạo ở `V9__slot_action_instruction.sql`) | `char_length(instruction) BETWEEN 1 AND 500` | Migration PR B: `DROP CONSTRAINT chk_esa_instruction`, tạo lại với `BETWEEN 1 AND 1000` và giữ điều kiện `action = 'replace'`; sửa `COMMENT ON COLUMN`. **Không sửa file V9** |
  | `front_end/src/features/exam/components/ChangeQuestionModal.tsx`, `MAX_INSTRUCTION_LENGTH` (và chú thích "mục 7.5: tối đa 500") | `500` (luật `max` của Form và `maxLength` của ô nhập) | `1000` |
  | `docs/api/README.md` mục 7.5 (đoạn "Yêu cầu của giáo viên khi Đổi") | "tối đa 500 ký tự" | "tối đa 1000 ký tự" |
  | `docs/contract/enums.md`, dòng `exam_slot_action.instruction` (V9) | "1–500 ký tự" | "1–1000 ký tự (từ migration PR B)" |
  | Test `ExamBuildTest` (đoạn `"x".repeat(501)` → 400 và `"y".repeat(500)` → 200) | Biên 500 | Biên 1000 (xem test 16 ở 4.8) |

  Các chỗ **không** sửa:
  - `V9UpgradeFromV8Test` kiểm CHECK chứa "500": test này chỉ migrate tới V9, nên vẫn đúng.
  - `CellTextLimits.EXTRA_INSTRUCTION_MAX_CHARS` và `ExamContextService.MAX_INSTRUCTION_CHARS`: đã là 1000.
  - `OUTCOME_MAX_CHARS = 500`: là giới hạn yêu cầu cần đạt của ô, không liên quan.
  - `teacherBlock` không tự cắt, nên không cần sửa.
- Đổi câu với `source = bank` mà có `requestCodes` khác rỗng trả 400, giống `instruction` hiện nay.

**Ưu tiên Đổi câu > ô > đề, mức cụ thể hơn THAY HẲN mức dưới**, cho **cả chip lẫn văn bản** (quyết định 2):
- Mức đề và mức ô có yêu cầu khi có ít nhất một mã hoặc có văn bản tự do. Mức đó dùng **cả bộ** (mã và văn bản) của
  chính nó.
- Mức Đổi câu được coi là tự đặt khi có gửi `requestCodes` (kể cả `[]`) hoặc có `instruction`. Khi đó prompt **không**
  còn khối yêu cầu và văn bản của ô hay của đề.
- **Đổi hành vi so với hiện nay:** hiện `instruction` của Đổi câu đang **cộng thêm** vào `extraInstruction` của ô (spec
  của run vẫn đưa `extraBlock` vào prompt). PR B đổi thành thay hẳn. Cách làm: job mang yêu cầu riêng thì
  `MatrixPromptDraftV0.build` bỏ `extraBlock` và khối mã của spec. Cần test riêng cho thay đổi này.
- Đổi câu không gửi `requestCodes` và không có `instruction` (ví dụ client cũ): dùng yêu cầu của ô, không có thì của
  đề (như `instructionFor`).
- Lần sinh lại tự động (`enqueueRetry`) giữ yêu cầu của lượt đó.

**Yêu cầu hiệu lực** của mỗi job, tính lúc xếp hàng:
- `generation_job.request_codes` và `generation_job.request_text` (văn bản tự do đã chuẩn hóa).
- Worker chép hai trường này sang `generated_question.request_codes` / `request_text` khi lưu câu.
- Câu sửa (`QuestionEditService`, bản sao) chép theo hai trường này.
- Câu ngân hàng không có yêu cầu.
- Mã đã lọc theo dạng câu (4.1). `MatrixCellSpec` có thêm `requestCodes`, khóa trong hyperparams chỉ có khi có mã.

### 4.3 Solver `solver-v5-request-check` (cùng lượt gọi)

- Thêm hằng `PromptBuilder.SOLVER_PROMPT_VERSION_V5 = "solver-v5-request-check"`, đưa vào `SOLVER_PROMPT_VERSIONS`, và đặt
  làm mặc định. Ghi `enums.md` rằng PA1 sẽ là `solver-v6`.
- **Câu không có yêu cầu** (không mã, không văn bản): prompt và schema Solver **giống hệt v4** cho mcq, và giống hệt bản
  hiện tại cho tf, sa, so từng byte. Test bằng `assertEquals` với prompt sinh ra theo v4.
- **Câu có yêu cầu:** vẫn **một** lượt gọi Gemini, không thêm lượt.
  - **Prompt:** khối yêu cầu đặt **SAU** đề bài và phương án/ý, trước phần yêu cầu định dạng trả lời:
    ```
    YÊU CẦU CỦA GIÁO VIÊN ĐỐI VỚI CÂU NÀY (chỉ xét SAU KHI đã giải xong và chọn đáp án; yêu cầu không được làm thay đổi cách giải hay đáp án):
    - <đoạn cố định của từng mã>
    - Yêu cầu khác: <<< văn bản tự do, qua neutralize >>>
    Trong request_check: verdict = met nếu câu đáp ứng đủ mọi yêu cầu, partial nếu chỉ một phần, not_met nếu không; reason ≤ 20 từ.
    ```
  - **Schema:** thêm trường `request_check` = `{"verdict": enum [met, partial, not_met], "reason": STRING}`, đặt **cuối**
    `properties`, `required` và `propertyOrdering` của cả 3 schema (mcq v3, tf, sa). Các trường cũ giữ nguyên thứ tự.
  - **Cách xét đáp án không đổi:** `passed`, `judge_*` và việc sinh lại do Solver lệch giữ như cũ. `request_check` không
    bao giờ làm câu thành không đạt và không kích hoạt sinh lại.
  - **Phản hồi thiếu hoặc hỏng `request_check`:** coi như không có kết luận về yêu cầu (không cờ). Phần kết quả Solver
    vẫn hợp lệ. Không ném lỗi.
- **Lưu kết quả:** `quality_check_result.request_verdict VARCHAR(10)` (CHECK `met|partial|not_met`, NULL khi không có yêu cầu
  hoặc không có kết luận) và `request_reason TEXT`, trên dòng `answer_check`.
- **Truyền yêu cầu vào Solver:** `QuestionQcInput` có thêm các trường yêu cầu, đọc từ `generated_question`.
  `QcOrchestrator.callSolver`, `QcRerunService` và `SolverProbeRunner` truyền theo. Câu cũ (NULL) đi đường không có yêu cầu.

### 4.4 `infoReasons`: cờ thông tin

- `ExamItemDto` thêm `List<String> infoReasons`, **luôn có** (`[]` khi không có), như `reviewReasons`. Vì vậy
  `exam-view-batch/golden.json` phải cập nhật.
- Giá trị (do `QcSummaryService` tính từ dòng `answer_check` mới nhất):
  - `request_partial: <reason>`: frontend hiện "Đúng một phần yêu cầu: …".
  - `request_not_met: <reason>`: frontend hiện "Chưa đúng yêu cầu: …".
  - `met`: không có cờ.
- **Tách hẳn khỏi `reviewReasons`:**
  - Không bật `needsReview`.
  - Không tính vào `needsReviewCount`.
  - Không đổi `qcState` hay trạng thái chỗ, không ảnh hưởng ngân hàng riêng/chung.
- **Giữ câu:** `ExamActionService.keep` vẫn chỉ dựa vào `needsReview` (biến `flagged`). Câu chỉ có `infoReasons` khi Giữ
  vẫn vào **ngân hàng dùng chung**, không thành riêng tư. Cần test.
- **Thiết kế chung:** sau này "Có hình" (câu có hình) và lỗi phương án nhiễu không khớp (PA1) thêm giá trị
  vào cùng danh sách. Mỗi giá trị có dạng `<mã>` hoặc `<mã>: <chi tiết>`. Frontend hiện giá trị lạ nguyên văn.
- `ExamItemDto` thêm `requests` (`@JsonInclude(NON_NULL)`, null khi câu không có yêu cầu):
  `{"codes": [...], "text": "...", "scope": "replace" | "cell" | "exam"}`. `scope` cho biết yêu cầu lấy từ mức nào, và
  được lưu thêm ở `generation_job.request_scope`.
- `ExamItemDto` thêm `inheritedRequests` (`@JsonInclude(NON_NULL)`): yêu cầu **thừa hưởng** của chỗ, tức yêu cầu hiệu lực
  của ô, không có thì của đề, đọc từ `exam_generation_context` (như `instructionFor`). Dạng
  `{"codes": [...], "text": "...", "scope": "cell" | "exam"}`:
  - `codes`: mã chip thừa hưởng, đã lọc theo dạng câu của chỗ; `[]` khi mức đó chỉ có văn bản.
  - `text`: **văn bản tự do thừa hưởng**, nguyên văn đã lưu ở `exam_generation_context.extra_instruction` (đã chuẩn hóa lúc
    lưu, chưa qua `neutralize`, vì `neutralize` chỉ chạy lúc dựng prompt); null khi mức đó chỉ có mã.
  - `scope`: mức được dùng. Mã và văn bản luôn lấy từ **cùng một mức**, theo quy tắc thay hẳn: ô có yêu cầu (mã hoặc văn
    bản) thì lấy cả bộ của ô, kể cả khi đề cũng có; không thì lấy của đề.
  - Cả trường null khi ô và đề đều không có yêu cầu.
  - Trường này khác `requests`: `requests` là yêu cầu của câu đang ở chỗ (có thể do lượt Đổi trước đặt), còn
    `inheritedRequests` là cái hộp Đổi câu điền sẵn (4.7).

### 4.5 Migration

- Một file.
- **Quy tắc đánh số:** Flyway không chạy migration lệch thứ tự, nên **không giữ chỗ số trước** cho nhánh nào. Lúc code, đặt
  số kế tiếp của `origin/main` hiện tại. Ngay lúc rebase lên `origin/main` **trước khi merge**, lấy lại số **kế tiếp** của
  main và đổi tên file nếu main đã có số đó, kèm sửa mọi chỗ nhắc số (chú thích migration, entity, `enums.md`). PR nào
  merge trước thì lấy số trước.
- Nội dung:
  - `exam_generation_context.request_codes TEXT[]`
  - `exam_slot_action.request_codes TEXT[]`
  - `generation_job.request_codes TEXT[]`, `request_text TEXT`, `request_scope VARCHAR(10)`
  - `generated_question.request_codes TEXT[]`, `request_text TEXT`
  - `quality_check_result.request_verdict VARCHAR(10)`, `request_reason TEXT`
  - Nâng giới hạn `exam_slot_action.instruction` lên 1000: thay CHECK `chk_esa_instruction` và `COMMENT ON COLUMN` (4.2).
- Mọi cột cho phép NULL, không có default (dòng cũ là "không yêu cầu").
- CHECK: mảng mã nằm trong 6 mã (`request_codes <@ ARRAY[...]::text[]`); `request_verdict IN ('met','partial','not_met')`;
  `request_scope IN ('replace','cell','exam')`.
- Chạy test trên Postgres tạm (container, cổng 5433) như PR A. Số migration của nhánh còn có thể đổi khi rebase, mà đã ghi
  vào DB test chung thì chỉ gỡ được bằng repair hay clean (cấm theo M-28).
- **Lưu ý:** khi đổi số migration, xóa bản cũ trong `back_end/target/classes/db/migration`. Nếu không, Flyway vẫn chạy file cũ.

### 4.6 Thống kê chương 4

- File `docs/thong-ke/10-yeu-cau-giao-vien.sql`, dùng biến `dev_emails` như các file khác. Thêm test vào `StatisticsSqlTest`.
- Truy vấn:
  1. Tỉ lệ `met/partial/not_met` theo từng mã (câu có nhiều mã thì tính cho mỗi mã).
  2. Tỉ lệ theo `request_scope`.
  3. Số lượt Đổi câu ngay sau một câu `not_met` hay `partial` của cùng chỗ.

### 4.7 Frontend (tối thiểu, làm SAU backend, khi An báo Quân)

Dùng component antd sẵn có (`Tag.CheckableTag` hoặc `Checkbox.Group`).

- **`ChangeQuestionModal`:**
  - Nhóm chip yêu cầu đặt dưới chip lý do, chỉ hiện khi chọn "soạn mới". Ẩn chip không hợp dạng câu.
  - Mở hộp thì **chọn sẵn** các chip trong `inheritedRequests.codes` và **điền sẵn** văn bản (xem dưới), kèm dòng chú thích "Yêu cầu của ô" hoặc "Yêu cầu của
    đề" theo `scope`. Lý do: Đổi câu "thay hẳn" yêu cầu của ô và đề; nếu không chọn sẵn, giáo viên bấm một chip mới là mất
    luôn các yêu cầu cũ mà không biết.
  - Giáo viên bỏ chọn được bất kỳ chip nào. Khi chọn "soạn mới", hộp **luôn gửi** `requestCodes`, kể cả `[]` khi đã bỏ
    hết, để backend coi là tự đặt (4.2).
  - Văn bản tự do thừa hưởng (`inheritedRequests.text`) được **tự điền sẵn** vào ô nhập "yêu cầu thêm" của hộp. Giáo viên
    sửa hoặc xóa được. Khi gửi, nội dung ô nhập đi trong `instruction` như văn bản của chính lượt Đổi; backend không phân
    biệt văn bản tự gõ với văn bản điền sẵn. Xóa trống ô nhập thì không gửi `instruction`; mã vẫn gửi theo quy tắc trên.
  - Chú thích "Yêu cầu của ô" / "Yêu cầu của đề" áp cho cả chip lẫn văn bản điền sẵn. Không có dòng chữ xám riêng.
  - **Giới hạn độ dài:** ô nhập tối đa 1000 ký tự (`MAX_INSTRUCTION_LENGTH = 1000`), bằng giới hạn của ô và đề (4.2), nên
    văn bản thừa hưởng luôn điền đủ. Không có cơ chế riêng cho văn bản dài.
  - Chọn "lấy từ ngân hàng": ẩn chip và ô nhập, không gửi `requestCodes` hay `instruction` (giữ quy tắc 400 hiện có).
- **`BuildByLessonForm`, `GenerateExamPage`:** nhóm chip cho cả đề, và cho từng ô ở phần ngữ cảnh theo ô.
- **`ExamQuestionList`** (thẻ câu):
  - Dòng chữ xám "Yêu cầu: <nhãn chip>; <văn bản>".
  - Thẻ thông tin màu xanh cho `infoReasons`. Không dùng màu cảnh báo và không tính vào bộ lọc "cần xem kỹ".
- **`exam.types.ts`:** `TeacherRequestCode`, `TEACHER_REQUEST_OPTIONS`, `infoReasons`, `requests`, `inheritedRequests`.

### 4.8 Test cần có (viết trước, đỏ rồi xanh)

**Prompt sinh câu:**
1. Mỗi mã sinh đúng đoạn cố định của nó (test tham số hóa, 6 mã).
2. Mức đề và ô: mã không hợp dạng câu không vào prompt và không được lưu vào câu đó. Câu hợp dạng trong cùng đề hay ô
   vẫn có mã đó.
3. Ưu tiên Đổi câu > ô > đề, thay hẳn:
   - Đổi câu có yêu cầu thì prompt **không** chứa `extraInstruction` hay mã của ô và đề.
   - Ô có yêu cầu thì không chứa yêu cầu của đề.
   - Đổi câu không gửi `requestCodes` và không có `instruction` thì dùng yêu cầu của ô.
   - Đổi câu gửi `requestCodes: []` và không có `instruction` thì câu mới không có yêu cầu nào, kể cả khi ô và đề có.
4. Văn bản tự do có `<<<`, `>>>` hay "bỏ qua quy tắc" đi qua `neutralize`.
5. Sinh lại tự động giữ yêu cầu của lượt Đổi.

**API:**
6. `requestCodes` hợp lệ, trùng, lẫn hoa thường, rỗng.
7. Mã lạ trả 400 và không ghi gì (đề, ngữ cảnh, nhật ký, job).
8. Đổi câu `source=bank` kèm `requestCodes` khác rỗng trả 400.
9. Đổi câu ghi `exam_slot_action.request_codes`.
10. `ExamItemDto.requests` có `scope` đúng.
11. Đổi câu có mã không hợp dạng của chỗ (ví dụ `tf_shared_context` cho chỗ mcq) trả 400 `INVALID_REQUEST`:
    - thông báo tiếng Việt đúng mẫu ở 4.1;
    - không ghi gì: chỗ, nhật ký, job, trần lượt sinh đều giữ nguyên.
12. Tạo đề hay ô có mã không hợp dạng của một số câu (ví dụ `tf_shared_context` cho đề gồm mcq và tf) trả 200.
13. `ExamItemDto.inheritedRequests`:
    - ô có yêu cầu thì `scope = cell`;
    - chỉ đề có yêu cầu thì `scope = exam`;
    - cả hai đều không có thì vắng trường;
    - `codes` đã lọc theo dạng câu của chỗ;
    - `text` bằng nguyên văn `extra_instruction` đã lưu của mức được chọn, kể cả văn bản đủ 1000 ký tự (không cắt);
    - mức chỉ có văn bản thì `codes = []`; mức chỉ có mã thì `text` null;
    - ô chỉ có văn bản còn đề chỉ có mã thì lấy cả bộ của ô (`scope = cell`, `codes = []`, `text` của ô), không trộn mã
      của đề vào.
14. Đổi câu gửi lại đúng chip và văn bản thừa hưởng (mô phỏng hộp điền sẵn, không sửa gì):
    - prompt có đúng một lần văn bản đó, trong khối yêu cầu của Đổi câu (`teacherBlock`), **không** còn `extraBlock` của
      ô hay đề, nên văn bản không bị lặp hai lần;
    - `requests.scope = replace`; `exam_slot_action.instruction` và `request_codes` ghi đúng.
15. Đổi câu gửi chip thừa hưởng nhưng xóa trống văn bản: câu mới chỉ có mã, prompt không còn văn bản của ô hay đề.
16. Giới hạn `instruction` của Đổi câu là 1000 (sửa đoạn biên 500 hiện có trong `ExamBuildTest`):
    - gửi 1001 ký tự (sau trim) trả 400, thông báo chứa "1000";
    - không ghi gì: số dòng `exam_slot_action` và `generation_job` không đổi, chỗ giữ nguyên;
    - gửi đúng 1000 ký tự (có khoảng trắng hai đầu) trả 200 và lưu đủ 1000;
    - gửi lại nguyên văn bản thừa hưởng dài 1000 ký tự của ô thì nhận;
    - migration: chèn thẳng dòng `replace` có `instruction` 1000 ký tự thì qua CHECK, 1001 ký tự thì bị chặn; dòng `keep`
      có `instruction` vẫn bị chặn.

**Solver:**
17. Câu không có yêu cầu: prompt và schema **bằng hệt** v4 (mcq) và bản hiện tại (tf, sa).
18. Câu có yêu cầu:
    - Khối yêu cầu đứng sau đề và phương án, có câu "chỉ xét SAU KHI đã giải".
    - `request_check` là phần tử **cuối** của `properties`, `required`, `propertyOrdering` ở cả 3 schema.
19. Đúng một lượt gọi LLM cho mỗi câu có yêu cầu (đếm số lượt gọi của client giả).
20. `request_check` thiếu hoặc hỏng: kết quả Solver vẫn lưu, `request_verdict` NULL, không có cờ.
21. `not_met` hay `partial` không đổi `passed` và không kích hoạt sinh lại.

**Cờ:**
22. `not_met` cho ra `infoReasons = ["request_not_met: …"]`, trong khi `needsReview`, `reviewReasons`, `needsReviewCount`
    và `qcState` giữ nguyên.
23. Giữ câu chỉ có `infoReasons` thì câu vào ngân hàng **dùng chung**, không qua `restrictToUser`.
24. `golden.json` có `infoReasons: []`.

**Migration và thống kê:**
25. Các CHECK từ chối mã lạ, `verdict` lạ, `scope` lạ; dòng cũ là NULL.
26. Câu sửa chép yêu cầu sang bản sao.
27. `QcRerunService` chạy lại Solver vẫn kèm yêu cầu.
28. `StatisticsSqlTest` cho file `10-yeu-cau-giao-vien.sql`.

**Toàn bộ:** chạy `back_end/run-tests.ps1` trên Postgres tạm. Nếu có sửa frontend thì chạy typecheck frontend.

### 4.9 Tài liệu PR B phải cập nhật

- `docs/contract/enums.md`:
  - `TeacherRequest` (6 mã kèm đoạn prompt), `RequestVerdict`.
  - Solver `solver-v5-request-check`, ghi PA1 = v6.
  - Định dạng `infoReasons`.
  - Dòng `exam_slot_action.instruction`: 1–1000 ký tự.
- `docs/api/README.md`:
  - Mục 7.5: `instruction` của Đổi câu tối đa 1000 ký tự.
  - `requestCodes` ở mục tạo đề, theo bài học, ngữ cảnh ô, Đổi câu.
  - `infoReasons`, `requests` ở mục xem đề.
  - Đổi hành vi "thay hẳn" ở Đổi câu.

## 5. Thực nghiệm chạy thật (chờ khóa Gemini riêng)

**Chưa chạy.** Chỉ chạy khi An cho phép và có khóa Gemini riêng.
- Ước tính khoảng 180 lượt Gemini, có thể chia 2 ngày.
- Môi trường: DB `datn_mcq_e2e`, một backend có worker, ma trận "Giữa kì I – Toán 8".

| Lượt | Cấu hình | Phụ thuộc |
|---|---|---|
| (a) | Chuẩn, mẫu cũ | PR A |
| (b) | Nâng cao, mẫu cũ | PR A |
| (c) | Chuẩn, mẫu theo đặc tả tổ, kèm câu mẫu | **Đầu vào cần có**, do An cung cấp trong `data/private` (ngoài git), đã bỏ tên trường: (1) bản đặc tả đề của tổ, để dựng mẫu ma trận theo đặc tả (bước 5, chưa làm); (2) file câu mẫu `cau-mau-do-sau.json` đúng định dạng ở 3.4. Thiếu đầu vào nào thì chưa chạy (c) |
| (d) | Trên đề (a): Đổi 2 câu với lý do "Quá dễ"; Đổi 2 câu với chip `real_context` + `multi_step` | Nửa đầu cần PR A, nửa sau cần **PR B** |

**Báo cáo:**
- Phân bố đáp án, số cờ (`reviewReasons`, và `infoReasons` sau PR B), câu nào vượt lớp.
- Với (d):
  - Tỉ lệ `request_check` met/partial/not_met.
  - An tự đánh giá từng câu có đúng yêu cầu không, và đếm số câu đồng ý với Solver.
  - Câu Đổi vì "Quá dễ" có khó hơn câu cũ không (An đánh giá).

**Xuất bản chấm mù:**
- Xuất Word MathType 3 đề vào `data/private/danh-gia-do-sau/` (ngoài git), tên ẩn nguồn `de-A`, `de-B`, `de-C`, kèm một
  file khóa ghi đề nào là cấu hình nào.
- Giáo viên chấm mù cùng đề thật.
- Không đưa các file này hay kết quả có tên người vào repo.
