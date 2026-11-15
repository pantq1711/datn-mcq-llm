# Phương án nhiễu kèm lỗi sai của học sinh (câu `mcq`)

> **BẢN THIẾT KẾ 11/11/2026 – AN ĐÃ DUYỆT 11/11 kèm quyết định H-1 đến H-5 (mục 10).** Khảo sát trên `main` tại `71b5b2b`.
> Tuân thủ `docs/ai-development-rules.md` v3.
>
> **Cập nhật khi code (13/11, nhánh `feature/misconception-distractors` cắt từ `main` sau #67, #70, #72):** số hiệu thực tế
> khác bản khảo sát vì PR độ sâu và PR B đã lấy các số trước:
> - prompt sinh câu: `matrix-mcq-v10-draft` (thay cho "v9" trong tài liệu này; v9 là của PR độ sâu); `sa`, `tf` không đổi;
> - Solver: **`solver-v7-distractor-check`** (15/11, sau hotfix hai lượt #76): lỗi KHÔNG còn ghép trong lượt giải mà ở lượt 2
>   dùng chung với `request_check` (mục 8.2 giải thích lý do). Bản một lượt `solver-v6-distractor-check` của nhánh này (13/11)
>   đã bỏ; trên `main`, v6 là `solver-v6-two-pass` của hotfix. Mục 5.1 bên dưới mô tả bản một lượt; cách làm hiện hành ở mục
>   5.1a;
> - schema sinh câu: `McqProductionSchema.GENERATION_RESPONSE_SCHEMA_JSON_V10` (thay cho "`_V9`"), chọn theo prompt đã gửi
>   (`MatrixPromptDraftV0.asksDistractorErrors`) thay vì theo `prompt_version`;
> - migration: **`V30__distractor_errors.sql`** (15/11, rebase lên main: V29 là `V29__request_verdict_unchecked.sql` của
>   hotfix; câu có hình I1 cũng dùng số tạm, PR nào merge sau đổi số khi rebase);
> - "không khớp" là cờ thông tin `distractor_mismatch: <chữ>` trong `infoReasons` (cờ thông tin của PR B), không vào
>   `reviewReasons`;
> - H-4 (An chấp nhận 14/11): kết quả tạm của bản sửa đang chờ tính bằng so trực tiếp bản sửa với bản ngay trước
>   (`question_text`, `correct_option`, từng `option_x`; `DistractorView`), không đọc `changed_fields` của nhật ký sửa; kết quả
>   như nhau;
> - H-3 đổi: PR này có thêm phần frontend tối thiểu (dòng "Lỗi sai: …" dưới phương án sai), theo yêu cầu giao việc 13/11;
> - công cụ đo: `SolverProbeRunner` nhận `--app.solver-probe.distractor-errors-file=…` (đối chiếu `correct_option` với DB, lệch
>   thì dừng trước khi gọi), CSV thêm cột cuối `error_checks` dạng `B:match|C:mismatch|D:unknown`.

## 1. Mục tiêu và phạm vi

Mỗi phương án SAI của câu `mcq` do hệ thống sinh có kèm một câu mô tả **lỗi sai của học sinh** dẫn tới phương án đó. Solver
kiểm xem lỗi đã nêu có thật sự cho ra phương án đó không. Giáo viên thấy lỗi kèm kết quả kiểm trên trang đề và trong phần
đáp án của file Word, dùng để chẩn đoán lỗi sai của lớp.

Ngoài phạm vi: câu `tf`, `sa`; câu nhập từ Word (`origin = 'import'`, không gọi LLM); câu `mcq` sinh lẻ của công cụ thí
nghiệm (`PromptBuilder.buildGenerationPrompt`, giữ 7 trường); cho giáo viên sửa chữ của lỗi; API ngân hàng
(`/api/bank-items`) chưa trả trường mới (dữ liệu vẫn được lưu ở `bank_item`).

## 2. Điều kiện trước và hai phát hiện cần An biết

1. **#55 chưa có trong `main`.** `git log main` có các PR #47–#54, #57, #58, #59; không có #55. Khối "PHẠM VI KIẾN THỨC" và
   `matrix-mcq-v8-draft` hiện chỉ nằm ở `origin/fix/grade-scope-prompt` (2 commit, chưa merge; kiểm lại sau `git fetch`
   ngày 11/11). An xác nhận đó là #55 (H-2): An chạy thật và merge #55 trước; nhánh `feature/misconception-distractors`
   chỉ cắt sau khi #55 đã vào `main`, khi đó mcq đi từ v8 lên **v9**.
2. **Yêu cầu bước 4 chạm vào tính độc lập của Solver.** Hợp đồng hiện tại (`enums.md` mục 4.6): "Solver chỉ thấy đề và 4
   phương án, không thấy đáp án và lời giải của câu". `distractor_errors` có `null` ở đúng phương án đúng, nên nếu gửi
   nguyên bản đồ "chữ → lỗi" cho Solver thì Solver biết luôn đáp án của người soạn, và bước `answer_check` mất tác dụng.
   Mục 5 dùng cách **ghép mù** để vẫn kiểm được trong cùng một lượt gọi mà không lộ đáp án (An duyệt, H-1).

## 3. Hợp đồng JSON sinh câu `mcq` (bước 1, `enums.md` mục 4.1)

Thêm trường thứ 8, đứng **cuối** (`propertyOrdering`: …, `explanation`, `correct_option`, `distractor_errors`):

```json
{
  "question_text": "...", "option_a": "...", "option_b": "...", "option_c": "...", "option_d": "...",
  "explanation": "...", "correct_option": "B",
  "distractor_errors": {"A": "Nhầm $(a+b)^2 = a^2 + b^2$", "B": null, "C": "Quên đổi dấu khi bỏ ngoặc", "D": "Cộng số mũ thay vì nhân"}
}
```

| Tình huống | Parser (`McqJsonParser`, chế độ production) |
|---|---|
| Đủ: `null` ở đáp án đúng, chuỗi ở 3 phương án sai | Lưu nguyên (trim, sửa escape LaTeX như trường văn bản khác) |
| Thiếu hẳn trường, hoặc không phải đối tượng | Không lỗi. Lưu `NULL`. Cảnh báo `json: đã sửa thiếu distractor_errors (không có phân tích phương án nhiễu)` |
| Thiếu hoặc rỗng ở một phương án sai | Phương án đó `null`. Cảnh báo `json: đã sửa distractor_errors thiếu lỗi của phương án C` |
| Có chuỗi ở phương án đúng | Bỏ chuỗi đó. Cảnh báo `json: đã sửa distractor_errors bỏ lỗi ghi ở phương án đúng B` |
| Từ 16 đến 30 từ (H-5) | Giữ nguyên chữ, cảnh báo `json: đã sửa distractor_errors phương án C dài N từ (quá 15)` |
| Trên 30 từ (H-5) | Bỏ (`null`), cảnh báo `json: đã sửa distractor_errors bỏ lỗi của phương án C vì dài N từ (quá 30)`. Không bao giờ cắt chữ giữa chừng |
| LaTeX hỏng (lẻ `$`, lệnh không hỗ trợ) | Bỏ lỗi của phương án đó như "thiếu" (chữ này sẽ in ra Word, không để làm hỏng pandoc) |

Số từ đếm theo khoảng trắng; một công thức `$...$` liền nhau tính là một từ. Mọi cảnh báo trên có tiền tố `json: đã sửa`, khớp `QcSummaryService.AUTO_FIXED_WARNING_PATTERNS` nên **không bật cờ**, không
làm câu `format_failed`, không sinh lại. Chế độ chặt (`repair = false`) đọc trường nếu có, không ghi cảnh báo.

Schema Gemini: thêm hằng mới `McqProductionSchema.GENERATION_RESPONSE_SCHEMA_JSON_V9` (8 trường, `distractor_errors` là
`OBJECT` có 4 thuộc tính `A`–`D` kiểu `STRING` `nullable`). Chỉ run có `prompt_version` bắt đầu bằng `matrix-mcq-` dùng
schema này (chọn ở `GenerationJobPoller` dòng 163 và `GenerationService` dòng 274); đường sinh lẻ giữ schema 7 trường.

Lý do đặt trường ở cuối: không đổi thứ tự 7 trường đang chạy ổn (lời giải trước `correct_option`), giảm rủi ro làm lệch
phân bố đáp án và tỉ lệ `format_failed`. Mặt trái: model có thể viết lỗi "hợp lý hóa sau" cho phương án đã chọn; chính
bước kiểm của Solver và phần chấm tay ở mục 8 đo việc này.

## 4. Prompt sinh câu (bước 2, `MatrixPromptDraftV0`, `matrix-mcq-v9-draft`)

Thêm hằng `MCQ_MISCONCEPTION_RULES`, đặt ngay sau `MCQ_OPTION_RULES`:

- Mỗi phương án sai phải là KẾT QUẢ của đúng MỘT lỗi sai thường gặp của học sinh lớp đó khi làm chính bài này (áp lỗi vào
  lời giải đúng thì ra đúng phương án đó).
- Hai phương án sai không được xuất phát từ cùng một lỗi.
- Lỗi phải nằm trong PHẠM VI KIẾN THỨC ở trên; không dùng lỗi của kiến thức lớp trên hay bài sau.
- Ghi lỗi vào `distractor_errors`: mỗi phương án sai một câu tiếng Việt tối đa 15 từ, mô tả lỗi của học sinh (ví dụ "Nhầm
  $(a+b)^2 = a^2 + b^2$", "Quên đổi dấu khi bỏ ngoặc"); phương án đúng ghi `null`. Không nhắc chữ cái phương án.
- Câu dẫn không được làm lộ đáp án (không gợi ý kết quả, không nêu lỗi cần tránh).
- (An bổ sung 11/11) Mỗi phương án sai phải KHÁC GIÁ TRỊ với đáp án đúng; không dùng cách viết khác (phân tích chưa triệt
  để, đổi thứ tự nhân tử, đặt nhân tử chung khác) của cùng một biểu thức. Lý do: đề 26 sinh trên #55 có 2/13 câu `mcq`
  (chỗ 401, 402) có phương án sai bằng đáp án đúng về giá trị. Quy tắc này nằm trong `MCQ_MISCONCEPTION_RULES`, có test
  prompt chứa câu này.

Câu yêu cầu đầu ra đổi thành "đủ 8 trường". Dòng "các phương án nhiễu hợp lý" hiện có được giữ. Cập nhật Javadoc lịch sử
phiên bản và `enums.md` (dòng `matrix-mcq-v9-draft`). `sa`, `tf` không đổi phiên bản.

## 5. Solver (bước 4)

### 5.1. Cách kiểm đề xuất: ghép mù trong cùng lượt gọi

Prompt mới `solver-v5-distractor-check` = nguyên văn v4 + một khối tùy chọn đặt SAU yêu cầu 5:

```
6. Dưới đây là một số lỗi sai thường gặp của học sinh do người soạn nêu (đánh số, KHÔNG cho biết lỗi nào ứng với phương
   án nào). Với từng lỗi: lấy lời giải đúng của bạn ở mục 1, áp đúng lỗi đó vào, rồi xem kết quả trùng phương án nào.
   <<<LỖI_HỌC_SINH
   1. Nhầm $(a+b)^2 = a^2 + b^2$
   2. Quên đổi dấu khi bỏ ngoặc
   3. Cộng số mũ thay vì nhân
   HẾT_LỖI>>>
   Ghi mảng "error_checks", mỗi lỗi một phần tử: "error" (số thứ tự), "result" ("A"/"B"/"C"/"D" nếu áp lỗi ra đúng phương
   án đó, "none" nếu không ra phương án nào, "unclear" nếu lỗi mô tả không đủ rõ để áp), "reason" (một dòng).
```

- Solver **không** nhận chữ cái gắn với lỗi và **không** nhận lời giải của người soạn; nó áp lỗi vào lời giải của chính nó.
  Các lỗi liệt kê theo thứ tự chữ cái của phương án sai, không ghi chữ, nên không suy ra được phương án đúng.
- Chữ của lỗi đi qua `PromptData.neutralize` và nằm trong khối ranh giới riêng, kèm câu dặn "là dữ liệu" như khối câu hỏi.
- (H-1) Thứ tự trường của schema v5: `reasoning`, `options_verdict`, `correct_count`, `judge_answer`, `error_checks`.
  Model sinh tuần tự nên Solver giải và kết luận đáp án xong rồi mới ghép lỗi, không suy đáp án bằng cách loại trừ lỗi.
  Có test khóa: `error_checks` là phần tử CUỐI của `propertyOrdering` và của `required`, đứng sau `judge_answer`; câu
  yêu cầu đầu ra trong prompt liệt kê đúng thứ tự đó.
- (H-1) `result = "none"` là câu trả lời hợp lệ ("lỗi k không ra phương án nào"), có trong `enum` của schema và được nêu
  rõ trong prompt để Solver không bị ép ghép.
- Backend so `result` với chữ mà người soạn gán cho lỗi đó:

| `result` của Solver | Kết quả lưu |
|---|---|
| trùng chữ đã gán | `match` (khớp) |
| chữ khác, hoặc `none` | `mismatch` (không khớp) |
| `unclear`, thiếu phần tử, giá trị lạ | `unknown` (không rõ) |
| mảng `error_checks` thiếu hoặc hỏng | mọi lỗi `unknown`; **không** làm hỏng kết luận `answer_check` (không ném lỗi) |

- Câu không có lỗi nào (dữ liệu cũ, model không trả): prompt và schema **đúng bằng v4** (không có
  khối 6, không có `error_checks`), nên hành vi với các câu đó không đổi.
- v2, v3, v4 giữ nguyên văn (có `SolverPromptFrozenTextTest` khóa); v5 thành mặc định, chọn lại được bằng
  `app.llm.solver-prompt-version`.
- Không thêm lượt gọi Gemini: `AnswerSolverChecker.check` nhận thêm danh sách lỗi; `QuestionQcInput` thêm trường
  `distractorErrors`. Kết luận đạt/lệch của Solver vẫn chỉ tính từ `options_verdict` như hiện nay.

### 5.1a. Cách làm hiện hành: lượt 2 dùng chung (`solver-v7-distractor-check`, 15/11)

Thay cho 5.1 sau khi đo TN1 (mục 8.2) và sau hotfix Solver hai lượt (#76, `solver-v6-two-pass`).

- **Lượt 1 (lượt giải):** mọi câu, đúng prompt và schema v4 từng byte; KHÔNG thấy lỗi, không thấy yêu cầu. Kết luận đạt/lệch
  chỉ từ lượt này.
- **Lượt 2 dùng chung:** chạy khi câu có yêu cầu của giáo viên HOẶC có ít nhất một lỗi ở phương án sai, VÀ lượt 1 không gắn
  cờ. Nhận đề, 4 phương án và lời giải của lượt 1 (không gửi đáp án người soạn), rồi khối lỗi (ghép mù như 5.1: đánh số, không
  chữ cái, không gửi lỗi ở phương án đúng, `result` có thể là `none`), rồi khối yêu cầu (nếu có). Trả `error_checks` rồi
  `request_check`. Chỉ có yêu cầu thì lượt 2 y như v6.
- **Không kiểm được:** lượt 1 có cờ (không gọi lượt 2) hoặc lượt 2 lỗi / phản hồi không đọc được: lỗi giữ "chưa kiểm" (không
  lưu `distractor_checks`), yêu cầu `unchecked`. JSON đọc được mà `error_checks` thiếu/hỏng: mọi lỗi `unknown` (bảng 5.1).
- So kết quả, lưu, hiển thị, H-4: như 5.1 và 5.2. Chi phí: +1 lượt cho mỗi câu `mcq` có lỗi mà lượt 1 đạt (dùng chung với lượt
  kiểm yêu cầu, không cộng thêm khi câu có cả hai).

### 5.2. Lưu và hiển thị kết quả

- `quality_check_result.distractor_checks` (jsonb, chỉ dòng `answer_check` của câu `mcq`):
  `{"A": "match", "C": "mismatch", "D": "unknown"}`. Chỉ có khóa của những lỗi đã được kiểm.
- `unchecked` (chưa kiểm) **không lưu**: suy ra khi phương án có chữ lỗi mà không có khóa trong `distractor_checks`
  (Solver chưa chạy, lỗi, bị tắt, hoặc bản giáo viên sửa đang chờ kiểm lại, xem mục 6).
- Ghi chú Solver (`solver_reasoning`) thêm khối `[Kiểm lỗi phương án nhiễu]` sau khối `[Xét từng phương án]`, mỗi lỗi một
  dòng, để vết QC đọc được lý do.
- "Không khớp" là cảnh báo **thông tin**: không vào `reviewReasons`, không đổi `needsReview`, `qcState`,
  `needsReviewCount`, `generated_question.status`, không sinh lại câu. Giáo viên thấy qua `distractorErrors[chữ].check`.
  Định nghĩa `qc_passed` (Q-2) không đổi.
- `copyAnswerCheck` (bản sửa chỉ đổi lời giải) chép cả `distractor_checks`.

## 6. Lưu trữ (bước 3)

Migration mới, **số kế tiếp của `origin/main` lúc tạo PR** (hiện là V27; chưa nhánh remote nào có V27 trở lên; kiểm lại
trước khi merge):

```sql
ALTER TABLE generated_question    ADD COLUMN distractor_errors JSONB;
ALTER TABLE bank_item             ADD COLUMN distractor_errors JSONB;
ALTER TABLE bank_item             ADD COLUMN distractor_checks JSONB;
ALTER TABLE quality_check_result  ADD COLUMN distractor_checks JSONB;
-- CHECK: chỉ câu mcq; mọi cột là đối tượng JSON hoặc NULL; distractor_checks của quality_check_result chỉ ở answer_check.
```

Không dùng bảng riêng: dữ liệu luôn đọc và ghi cùng câu, tối đa 3 mục, không cần truy vấn theo từng lỗi ngoài thống kê
(jsonb đủ dùng), và cùng kiểu với `warnings`/`errors` đã có. `bank_item` có hai cột vì ngân hàng là bản chụp lúc Giữ và
không nối về `quality_check_result` khi xem đề.

- **Sinh câu:** `GenerationService.saveParsedQuestion` ghi `generated_question.distractor_errors`.
- **Giữ câu** (`AssistedKeepService`, M-9): chép `distractor_errors` và kết quả kiểm hiện có sang `bank_item` trong cùng
  transaction, qua một nạp chồng mới của `BankService.createOrUpdateFromReview`. Kết quả kiểm lấy qua `GenerationService`
  (module `generation` đã phụ thuộc `qc`), **không** thêm chiều phụ thuộc `review → qc` (Q-8 không bị chạm).
- **Bản giáo viên sửa** (`QuestionEditService.saveVersion`): bản mới chép nguyên `distractor_errors` của bản trước (giữ
  chữ; giáo viên không sửa được chữ của lỗi). Theo H-4, "chưa kiểm" là trạng thái **tạm** cho tới khi job `answer_check`
  của bản sửa (Solver v5) chạy xong:
  - **Trong lúc chờ** (bản sửa chưa có dòng `answer_check`), kết quả hiển thị suy từ bản ngay trước, theo trường đã đổi
    (cùng danh sách `changed_fields` của lần sửa):
    - đổi câu dẫn (`question_text`, gồm cả số liệu dùng chung của bài) hoặc đổi `correct_option` → MỌI lỗi `unchecked`;
    - chỉ đổi `option_x` → lỗi của phương án x `unchecked`, các lỗi khác giữ kết quả của bản trước;
    - chỉ đổi lời giải → không có job; `copyAnswerCheck` chép nguyên kết quả cũ như hiện nay.
  - **Khi job chạy xong**: job gửi TẤT CẢ lỗi còn chữ của bản sửa (trừ lỗi ghi ở phương án nay là đáp án đúng) cho
    Solver v5 và ghi kết quả mới cho từng lỗi; kết quả tạm ở trên bị thay hoàn toàn.
  - Job `failed` (hết lượt): giữ kết quả tạm; Giữ câu chép đúng trạng thái đang hiển thị sang `bank_item`. Giữ khi job còn
    chờ vẫn bị chặn 409 `QC_CHECKING` như hiện nay.
  - Đáp án đổi sang X: lỗi ghi ở X không hiển thị và không gửi Solver (chữ vẫn nằm trong cột); phương án đúng cũ không có
    lỗi nên chỉ có 2 lỗi, câu không còn "đủ lỗi".
  - Test cho cả hai trường hợp (sửa một phương án; sửa câu dẫn hoặc đáp án), mỗi trường hợp kiểm trạng thái trong lúc chờ
    và sau khi job chạy xong, cộng trường hợp chỉ sửa lời giải.
  Trạng thái tạm tính lúc xem đề (đọc gộp thêm kết quả của bản trước, chỉ khi đề có bản sửa đang chờ), không thêm cột.

## 7. API và xuất Word (bước 5, 6)

**`ExamItemDto.distractorErrors`** (bỏ khỏi JSON khi `null`; chỉ câu `mcq` có ít nhất một lỗi):

```json
"distractorErrors": {
  "A": {"text": "Nhầm $(a+b)^2 = a^2 + b^2$", "check": "match"},
  "C": {"text": "Quên đổi dấu khi bỏ ngoặc", "check": "mismatch"},
  "D": {"text": "Cộng số mũ thay vì nhân", "check": "unchecked"}
}
```

`check` ∈ `match | mismatch | unknown | unchecked`. Chỉ có khóa của phương án sai có chữ lỗi. Câu hệ thống sinh đọc từ
`generated_question` + `quality_check_result` (đã nằm trong truy vấn gộp `summarizeAll`, không thêm truy vấn); câu ngân
hàng đọc từ `bank_item`. Các constructor cũ của `ExamItemDto` được giữ, thêm tham số ở constructor chính và
`withExtraReasons`. Cập nhật `docs/api/README.md`; kiểm bằng MockMvc (M-16). Đổi hợp đồng API này đã được An duyệt (M-35).
PR này chỉ gồm backend và tài liệu API; phần hiển thị ở `front_end` làm PR riêng sau khi UX-2a (trang đề mới) merge (H-3).

**Xuất Word** (`ExamMarkdownBuilder.solution`, nhánh `MCQ`): sau đoạn lời giải thêm một đoạn

> *Phân tích phương án nhiễu:* A – Nhầm (a+b)² = a² + b²; D – Cộng số mũ thay vì nhân.

- Chỉ in lỗi `match` hoặc `unchecked`; không in `mismatch` và `unknown`. Không còn lỗi nào thì không có dòng.
- Chỉ ở phần "LỜI GIẢI CHI TIẾT"; phần đề và bảng đáp án không đổi. Công thức trong lỗi đi qua `inline()` như lời giải.
- Tham số `includeDistractorNotes` của `GET /api/exams/{id}/export/docx`, mặc định `true`; `false` thì bỏ dòng; giá trị
  khác `true`/`false` trả 400 như các tham số khác.
- Test: `ExamMarkdownBuilderTest` (có dòng / không in `mismatch` / tắt bằng tham số / không lọt vào phần đề) và test xuất
  docx thật cho cả Equation và MathType với lỗi có công thức, kỳ vọng `X-Equation-Kept-Omml = 0`.

## 8. Đo cho chương 4 (bước 7)

`docs/thong-ke/08-phuong-an-nhieu.sql`: một câu `SELECT` (quy ước của thư mục), dạng dài `(chi_so, nhom, so_cau, ti_le)`,
bộ lọc chung và CTE `dev_users` như các file khác; chỉ câu LLM gốc `mcq` của run `matrix-mcq-v9-draft` trở đi.

| Chỉ số | Cách tính |
|---|---|
| Tỉ lệ câu có đủ lỗi | câu `mcq` có đủ chữ lỗi ở cả 3 phương án sai / tổng câu `mcq` |
| Tỉ lệ khớp / không khớp / không rõ / chưa kiểm | theo từng lỗi (mẫu số: số lỗi có chữ) và theo câu (cả 3 khớp) |
| Giữ / Đổi theo nhóm | kết cục của câu (cùng cách tính file 01) chia hai nhóm: có ít nhất một `mismatch` và không có |
| Đổi vì "phương án mơ hồ" | số dòng `replace` có `replace_reason = 'ambiguous_options'`, chia theo hai nhóm trên |

Test: thêm vào `StatisticsSqlTest` (dữ liệu mẫu, số đếm, "chỉ một câu lệnh đọc"); cập nhật `docs/thong-ke/README.md`.

Số từ SQL chỉ nói Solver **đồng ý** với lỗi đã nêu, không nói lỗi đó có hợp lý về sư phạm. Vì vậy chương 4 cần thêm:

1. **Đánh giá thủ công** (mục 8.1).
2. **Phương án sai bằng đáp án về giá trị** (An bổ sung 11/11): ở lần chạy thật, đếm số câu `mcq` có ít nhất một phương án
   sai bằng đáp án đúng về giá trị (nhóm tự kiểm bằng khai triển hoặc rút gọn, kèm đối chiếu `options_verdict` của
   Solver), so với mốc đề 26 trên #55: 2/13 câu (chỗ 401, 402).
3. **Không làm hỏng chỗ khác:** so với đề "Giữa kì I – Toán 8" gần nhất: phân bố chữ đáp án, số câu có cờ, số
   `format_failed`/sinh lại, số Solver lệch và Solver lỗi, token và độ trễ trung bình của lượt sinh và lượt Solver.
4. **Giới hạn phải ghi:** Solver cùng họ model với bộ sinh; "khớp" không phải bằng chứng lỗi thường gặp ở học sinh thật;
   giáo viên chưa dùng số liệu này trên lớp thật.

### 8.1. Đánh giá thủ công (An duyệt 11/11)

- **Mẫu:** 30 câu `mcq` chọn ngẫu nhiên (hạt giống cố định, ghi trong báo cáo) trong các câu LLM gốc có ít nhất một lỗi của
  đề chạy thật; đề không đủ 30 câu `mcq` thì lấy hết và ghi rõ số câu. Đơn vị chấm là **từng lỗi** (tối đa 90 lỗi).
- **Người chấm:** An và một giáo viên Toán, chấm độc lập, mỗi người một phiếu. Phiếu **không có** cột kết luận của Solver
  để người chấm không bị ảnh hưởng.
- **Phiếu:** file CSV mẫu `docs/experiments/phuong-an-nhieu/phieu-cham-mau.csv` (chỉ dòng tiêu đề và 1–2 dòng ví dụ tự
  soạn), các cột: `ma_cau`, `cau_dan`, `phuong_an_dung`, `chu_phuong_an`, `noi_dung_phuong_an`, `loi_hoc_sinh`,
  `danh_gia` (`hop_ly` / `khong_hop_ly`), `ghi_chu`. Kèm `README.md` nêu tiêu chí "hợp lý": (1) áp lỗi vào lời giải đúng
  ra đúng phương án đó; (2) là lỗi học sinh lớp đó thực sự hay mắc ở bài này; (3) trong phạm vi kiến thức. Thiếu một tiêu
  chí là "không hợp lý", ghi tiêu chí thiếu ở `ghi_chu`.
- **Dữ liệu chấm thật** (phiếu đã điền, bảng nối `ma_cau` với kết luận Solver) chỉ nằm ở `data/private/` (đã có trong
  `.gitignore`); không đưa vào repo, fixture hay log.
- **Chỉ số báo cáo:**
  - tỉ lệ lỗi "hợp lý" theo từng người chấm;
  - đồng thuận giữa hai người chấm: tỉ lệ trùng và Cohen's kappa;
  - đồng thuận người chấm với Solver, tính riêng cho từng người: bảng 2×2 (`match` so với `mismatch`; người: hợp lý so
    với không hợp lý), tỉ lệ trùng và kappa; lỗi `unknown`/`unchecked` báo riêng số lượng, không đưa vào bảng;
  - trong các lỗi Solver báo `match`, tỉ lệ người chấm cho là không hợp lý (phần Solver không bắt được: lỗi áp ra đúng
    phương án nhưng không phải lỗi thường gặp).
- Solver chỉ kiểm tiêu chí (1), nên đồng thuận thấp ở tiêu chí (2), (3) là điều dự kiến và phải ghi như một giới hạn.
- Không viết code tính kappa trong PR này: 90 dòng tính bằng bảng tính; công thức ghi trong `README.md` của thư mục.

### 8.2. Kiểm hồi quy Solver v5 trên bộ TN1 (An yêu cầu 11/11)

Danh sách lỗi nằm trong ĐẦU VÀO của Solver v5 (thứ tự trường ở 5.1 chỉ bảo vệ phần đầu ra), nên có thể ảnh hưởng lời giải
độc lập của Solver. Phải đo trước khi coi v5 là mặc định.

- **Dữ liệu:** bộ TN1 (`docs/experiments/tn1/`, `ids.txt`): 29 câu `mcq` của DB `datn_mcq_e2e`, 9 câu lỗi và 20 câu tốt.
- **Mốc so (v4, đã đo 31/10, hai lần):** bắt 7/9 và 5/9 câu lỗi; báo nhầm 0/19 và 0/20 câu tốt.
- **Cách chạy:** `SolverProbeRunner` với `--app.solver-probe.prompt-version=solver-v5-distractor-check`, mỗi câu gửi kèm
  danh sách lỗi của câu đó; ~29 lượt Gemini, không ghi DB, kết quả ghi ngoài repo rồi chép CSV vào `docs/experiments/tn1/`.
  Công cụ được mở rộng trong PR này: nhận lỗi kèm theo, thêm cột `error_checks` vào CSV (có test, không gọi mạng).
- **Tiêu chí (An chốt 11/11):** bắt ≥ 7/9 và báo nhầm 0 → đạt; bắt < 5/9 hoặc có báo nhầm → không đạt; bắt 5–6/9 → chạy
  lần hai (thêm ~29 lượt) rồi so cả hai lần với 7/9 và 5/9 của v4. Cách tính như TN1 (`verdict = inconsistent`). Mẫu số
  báo nhầm ghi đúng số câu chạy được (19 hoặc 20); câu lỗi 429 thì chạy lại riêng câu đó.
- **Lỗi kèm theo (An chốt 11/11):** `docs/experiments/tn1/distractor-errors.json`, agent soạn nháp, An duyệt trước khi đo.
  Cả 29 câu TN1 đều là `mcq` (đã kiểm trong `datn_mcq_e2e`), nên phép đo tính trên đủ 29 câu. Mỗi phương án sai một lỗi
  theo quy tắc prompt v9; với 9 câu lỗi, soạn theo đáp án người soạn câu đã gán, không sửa câu. Mỗi mục ghi kèm
  `correct_option` để công cụ đối chiếu với DB và từ chối chạy khi lệch.
- **Không đạt:** bỏ cách một lượt gọi, đổi sang **2 lượt gọi** cho câu `mcq` có lỗi kèm theo: lượt 1 là Solver v4 nguyên
  văn (giải và kết luận, không thấy lỗi), lượt 2 chỉ ghép lỗi. Ghi rõ trong thiết kế, `enums.md` và báo cáo chi phí tăng
  thêm: +1 lượt Gemini cho mỗi câu `mcq` có lỗi (đề "Giữa kì I – Toán 8" khoảng +12 lượt cho 12 câu Phần I, chưa kể câu
  sinh lại và Đổi câu), và ảnh hưởng tới hạn mức ngày (`GeminiRateLimiter`) và hạn mức 15 lượt/phút.
- **Thứ tự (An chốt 11/11):** code bước 1–7 (sau khi #55 merge) → An duyệt file lỗi → đo TN1 (~29 lượt, An cho phép lúc
  đó) → đạt mới chạy thật 1 đề (~70 lượt, xin phép riêng).
- **Kết quả đo bản một lượt (14/11, `solver-v6-distractor-check` của nhánh này):** bắt **3/9** câu lỗi (8, 37, 39), báo nhầm
  0/20, 29 lượt gọi → **KHÔNG ĐẠT** (dưới 5/9). `error_checks` của 39D: `match` (mong đợi `mismatch`/`none`), 53D: `mismatch`.
  Câu 7, 52, 53, 15 (v4 từng bắt) bị sót; ở các câu phân tích nhân tử mọi lỗi đều được báo `match`, kể cả lỗi gán cho phương án
  thực ra cũng đúng. File: `docs/experiments/tn1/tn1_20261114_pa1-onepass-errors_solver-v6-distractor-check.csv`.
- **Lý do đổi sang lượt 2 dùng chung (An quyết 14/11):** danh sách lỗi nằm trong ĐẦU VÀO của lượt giải nên có thể làm Solver
  coi các phương án đó là sai mà không xét lại; đặt `error_checks` cuối schema chỉ bảo vệ phần đầu ra. Đo PR B cùng ngày (v5 kèm
  chip `common_trap`) cũng chỉ 4/9, nên tách lượt cho cả hai (hotfix #76 cho yêu cầu, PA1 dùng chung lượt 2). Lưu ý: ba lần đo
  `solver-v6-two-pass` (lượt giải giống hệt v4) được 5/8, 5/9, 4/9, nên v4 tự dao động tới 4/9; con số 3/9 vì vậy là chỉ báo,
  còn lý do chính là cấu trúc: lượt giải không thấy lỗi thì không thể bị lỗi ảnh hưởng. Cách làm hiện hành: mục 5.1a. Chi phí
  như dự tính ở trên (+1 lượt cho câu `mcq` có lỗi mà lượt giải đạt; khoảng +12 lượt mỗi đề "Giữa kì I"), nhưng dùng chung với
  lượt kiểm yêu cầu.
- **Đo lại TN1 với v7:** chưa làm (chờ hạn mức Gemini); đo như trên, kèm độ chính xác ghép lỗi và 39D, 53D.

Ghi chú khi soạn file lỗi (để đọc kết quả đo cho đúng):

- Với 9 câu lỗi, phương án "sai" theo đáp án đã lưu thực ra cũng đúng (ví dụ câu 37: cả 4 phương án nhân lại bằng đa thức
  gốc), nên lỗi gán cho nó ("chưa lấy hết lũy thừa của x"...) chính là phép thử nặng nhất của hiệu ứng neo: Solver có còn
  coi phương án đó là đúng không.
- Ba nhóm ngoại lệ trong file lỗi (An duyệt 11/11, ghi ở `docs/experiments/tn1/README.md`); file có 83 lỗi:
  - câu 30 A, 60 B: lỗi kép (học sinh mắc 2 lỗi cùng lúc), giữ;
  - câu 15 D, 25 D, 41 D, 61 D: không có lỗi tự nhiên, để `null` (4 câu này chỉ gửi 2 lỗi cho Solver);
  - câu 39 D, 53 D: cố ý không khớp (lỗi mô tả không ra đúng biểu thức của phương án), giữ để kiểm Solver có báo không
    khớp. Báo cáo đo TN1 phải nêu riêng `error_checks` của 2 mục này; mong đợi `mismatch` (chữ khác hoặc `none`).
- Các ngoại lệ không ảnh hưởng tiêu chí đạt (chỉ tính `verdict` của `answer_check`).

## 9. Rủi ro

| Rủi ro | Giảm thiểu |
|---|---|
| Solver bị neo theo danh sách lỗi, bắt lỗi đáp án kém đi | Ghép mù (5.1); `error_checks` đứng sau `judge_answer`; kiểm hồi quy trên bộ TN1 (8.2), không đạt thì đổi sang 2 lượt gọi; so số Solver lệch trước/sau ở lần chạy thật |
| Prompt và đầu ra dài hơn: tốn token, chậm hơn, dễ bị cắt → Solver ERROR hoặc `format_failed` tăng | `error_checks` hỏng không làm hỏng `answer_check`; theo dõi token/độ trễ (V21) trước/sau |
| Thêm trường làm lệch chất lượng câu (phân bố đáp án, lời giải) | Trường đặt cuối; so phân bố đáp án và số cờ với đề trước |
| Lỗi "hợp lý hóa sau", nghe đúng nhưng không phải lỗi thật của học sinh | Chấm tay; ghi giới hạn trong báo cáo |
| Chữ lỗi làm lộ đáp án | Chỉ in ở phần lời giải của file Word và trang giáo viên; test khóa không lọt vào phần đề |
| LaTeX trong chữ lỗi làm hỏng pandoc/MathType | Parser bỏ lỗi có LaTeX hỏng; test xuất cả hai dạng |
| Trùng số migration với PR khác | Lấy số lúc tạo PR, kiểm lại trước khi merge |
| Dữ liệu cũ và câu nhập không có lỗi | Mọi đường đọc coi `NULL` là "không có phân tích": không dòng, không trường trong JSON |

## 10. Quyết định của An (11/11/2026)

- **H-1 (Solver).** Ghép mù. Trong JSON của Solver, phần đáp án Solver tự giải đứng TRƯỚC phần ghép lỗi; Solver được trả
  "lỗi k không ra phương án nào"; có test kiểm thứ tự trường trong schema (mục 5.1).
- **H-2 (#55).** #55 = `fix/grade-scope-prompt`. An chạy thật và merge trước; chỉ cắt nhánh sau khi #55 vào `main`.
- **H-3 (giao diện).** PR này chỉ backend + tài liệu API. Frontend làm PR riêng sau khi UX-2a merge.
- **H-4 (bản sửa).** Sửa câu dẫn, đổi đáp án đúng hoặc đổi số liệu dùng chung → mọi lỗi "chưa kiểm"; chỉ sửa một phương án
  → lỗi của phương án đó "chưa kiểm". Job `answer_check` của bản sửa (Solver v5) tính lại kết quả; "chưa kiểm" chỉ là
  trạng thái tạm. Test cho cả hai trường hợp (mục 6).
- **H-5 (độ dài).** ≤ 30 từ giữ, quá 15 từ thì cảnh báo tự sửa; > 30 từ bỏ (`null`) kèm cảnh báo tự sửa; không cắt chữ.
- **Chương 4.** Thêm đánh giá thủ công 30 câu, hai người chấm, đồng thuận với Solver (mục 8.1).
- **Quyền chạy.** Được chạy `back_end/run-tests.ps1` bất cứ lúc nào trong việc này. Chạy thật với Gemini chờ An cho phép
  riêng (hai lần xin riêng: đo TN1 ~29 lượt, chạy thật 1 đề ~70 lượt).
- **Ba diễn giải đã được An đồng ý (11/11):** "đổi số liệu dùng chung" = sửa `question_text`; job kiểm lại thất bại hết
  lượt thì giữ kết quả tạm và Giữ câu chép đúng trạng thái đang hiển thị; phiếu chấm không có cột kết luận Solver, kappa
  tính bằng bảng tính.
- **Quy tắc giá trị (11/11):** prompt v9 thêm quy tắc "phương án sai khác giá trị với đáp án đúng" (mục 4); lần chạy thật
  đếm số câu có phương án sai bằng đáp án về giá trị, mốc đề 26: 2/13 (mục 8).
- **Kiểm hồi quy Solver v5 trên TN1** (mục 8.2): đạt nếu số câu lỗi bắt được không giảm và báo nhầm vẫn 0; không đạt thì
  đổi sang 2 lượt gọi và ghi chi phí tăng. An đã chốt nguồn file lỗi và ngưỡng (mục 8.2); file lỗi nháp chờ An duyệt.

## 11. Kế hoạch sau khi duyệt

Thứ tự test-first theo từng bước 1–7, mỗi bước: viết test đỏ → code → chạy lại. File dự kiến sửa:

- `llm/prompt`: `MatrixPromptDraftV0`, `McqProductionSchema`, `PromptBuilder`
- `generation`: `McqJsonParser`, `ParsedMcqDto`, `GeneratedQuestion`, `GenerationService`, `GenerationJobPoller`,
  `QuestionEditService`, `StoredQcInput`
- `qc`: `AnswerSolverChecker`, `SolverVerdict`, `QuestionQcInput`, `QcOrchestrator`, `QualityCheckResult`,
  `QcSummaryService`
- `review`/`bank`: `AssistedKeepService`, `BankService`, `BankItem`
- `exam`: `ExamViewDtos`, `ExamViewService`, `ExamController`, `ExamExportService`, `ExamMarkdownBuilder`
- migration mới; `docs/contract/enums.md`, `docs/api/README.md`, `docs/thong-ke/08-phuong-an-nhieu.sql`,
  `docs/thong-ke/README.md`, `docs/experiments/phuong-an-nhieu/` (phiếu CSV mẫu + README)

Không sửa: prompt và parser `sa`/`tf`, `bankimport`, dedup, `QcStatusDecider`, các prompt Solver v2–v4, `front_end`.

Lệnh (M-27): `powershell -ExecutionPolicy Bypass -File back_end/run-tests.ps1` đã được An cho phép trong việc này (chạy
riêng qua `back_end/.test-selection`, cuối cùng chạy toàn bộ). Phần kiểm của bước chạy thật gồm hai việc, mỗi việc xin
phép riêng: (1) đo hồi quy Solver v5 trên TN1 (mục 8.2, ~29 lượt, `spring-boot:run` với `SolverProbeRunner`, DB
`datn_mcq_e2e`); (2) lần chạy thật 1 đề "Giữa kì I – Toán 8" (~70 lượt Gemini,
sau 15:00 nếu dùng chung khóa với VPS) chỉ khi An cho phép riêng.
