# tools/measurement – công cụ đo chương 4 (D2, D4, D6)

Công cụ **offline** cho chương thử nghiệm: không phải tính năng sản phẩm, không kết nối DB VPS, không ghi DB. Chỉ
`baseline_goi` gọi Gemini (tính phí) và chỉ khi An chạy với khóa riêng.

| Phần | Câu hỏi | Công cụ |
|---|---|---|
| D2 | Cờ QC của hệ thống có khớp nhãn người chấm không (precision, recall, tỉ lệ lọt)? | `sql/d2-xuat-cau.sql`, `lay_mau`, `phieu cham`, `phan_tich_d2` |
| D4 | Câu hệ thống so với gọi thẳng Gemini (2 prompt) | `baseline_goi`, `baseline_parse`, cùng bộ chấm mù D2; `docs/experiments/d4/` |
| D6 | Ngưỡng dedup 3-gram: P/R/F1 theo ngưỡng 0,50–0,95 | `DedupGoldRunner` (back_end), `phieu cap`, `phan_tich_d6` |

Dữ liệu thật và **file khóa** (mã ↔ id câu, nguồn, cờ) chỉ nằm trong `data/private/do-luong/` (gitignored). Phiếu đưa người
chấm (`de-cham.docx`, `phieu-*.xlsx`, `cap-*.xlsx`, `cap-gan-nhan.docx`) không có cờ, lý do, nguồn, phiên bản prompt, id câu,
lời giải hay điểm cosine; `tests/test_phieu.py` quét nội dung để giữ điều này.

## Cài một lần

Git Bash, đứng ở gốc repo:

```bash
cd tools/measurement
python -m venv .venv
.venv/Scripts/python -m pip install -r requirements.txt   # openpyxl; pandoc phải có sẵn trên PATH
cd ../..
tools/measurement/.venv/Scripts/python -m unittest discover tools/measurement/tests   # 102 test, không cần DB hay mạng
```

Các lệnh bên dưới chạy từ `tools/measurement` với `PY=.venv/Scripts/python` và `D=../../data/private/do-luong`.

## D2 + D4: bộ chấm mù ~100 câu

**1. Xuất câu hệ thống (An chạy trên VPS, chép file về `$D/cau-he-thong.jsonl`).**

```bash
psql -X -A -t -v ON_ERROR_STOP=1 -d <db> -f tools/measurement/sql/d2-xuat-cau.sql -o cau-he-thong.jsonl
# mặc định chỉ lấy prompt hiện tại (matrix-mcq-v9-draft, matrix-tf-v12-draft, matrix-sa-v12-draft; test_sql_xuat giữ
# khớp MatrixPromptDraftV0); đổi bằng -v prompt_versions=a,b,c
```

Thử cục bộ trên `datn_mcq_e2e` (docker): `docker exec -i datn-mcq-db sh -c 'psql -X -A -t -U "$POSTGRES_USER" -d datn_mcq_e2e' < tools/measurement/sql/d2-xuat-cau.sql > $D/cau-he-thong.jsonl`.
Cờ được tính ở Python (`do_luong/co_qc.py`) đúng như trang đề: `needsReview` và không chỉ có `similar_in_exam`
(hasContentFlag); cảnh báo tự sửa ("json: đã sửa …") không bật cờ. Khớp với backend được giữ bằng fixture chung
`tests/fixtures/co-qc-parity.json` (test Python `test_co_qc` và test Java `QcFlagParityTest`).

**2. Baseline (sau khi An duyệt `docs/experiments/d4/prompts.md`).**

```bash
MT=../../docs/experiments/d2/ma-tran-6-6-6.json                           # CÙNG ma trận với đề hệ thống (L2)
$PY -m do_luong.baseline_goi --dry-run --ma-tran $MT --out-dir $D/baseline-tho   # xem prompt, không cần khóa
export GEMINI_API_KEY=...                                                  # gõ trong terminal, không lưu file
$PY -m do_luong.baseline_goi --model gemini-3.5-flash-lite --so-de 4 --ma-tran $MT --temperature 0.7 \
    --out-dir $D/baseline-tho                                              # 2 prompt x 4 đề = 8 lượt gọi
$PY -m do_luong.baseline_parse --tho-dir $D/baseline-tho --ma-tran $MT --out-dir $D
```

`--so-de` bằng số đề hệ thống (L3). `--temperature` bỏ trống thì không gửi (mặc định của model); meta ghi temperature,
tên và dấu vân tay ma trận; `baseline_parse` dừng nếu `--ma-tran` khác ma trận lúc gọi.
Câu không parse được nằm trong `$D/parse-loi.jsonl` (lý do + khối chữ gốc + bản parse dở). Sửa **phần định dạng** (không
sửa nội dung toán), chép từng câu đã sửa (một dòng JSON, giữ `id`) vào `$D/baseline-sua-tay.jsonl`, chạy lại
`baseline_parse ... --sua-tay $D/baseline-sua-tay.jsonl`: mỗi câu sửa tay có một dòng trong `$D/sua-tay-diff.jsonl` (lý do
lỗi gốc, trường đã đổi trước/sau). Báo số câu phải sửa tay trong chương 4 và phân tích nhạy khi bỏ các câu đó (L6c).

**3. Lấy mẫu + gán mã + file khóa.**

```bash
$PY -m do_luong.lay_mau --he-thong $D/cau-he-thong.jsonl --de-thi-id 1 2 3 4 --baseline-2 $D/baseline_2.jsonl \
    --seed 20261115 --out-dir $D
```

CHỈ câu của các đề thực nghiệm (`--de-thi-id`, L4). Hệ thống chia ba tầng theo cờ: **cờ chính** (lý do bước Solver /
format / grounding) lấy HẾT; **không cờ** rút 12 câu mỗi dạng (`--n-khong-co`); **chỉ cờ phụ** (cờ trùng, cờ hạ tầng) không
vào phiếu, ghi riêng (L10). Không ưu tiên câu nào trong tầng. Baseline: CHỈ baseline 2, 12 câu mỗi dạng (`--n-baseline`)
chia đều theo lượt. Ra `$D/khoa.csv` (FILE KHÓA, có cột `tang`, `co_chinh`, `ly_do_chinh`), `$D/bo-cham.jsonl`,
`$D/lay-mau-log.json` (cỡ tầng `tong_the`, số câu mỗi tầng trong phiếu, câu chỉ có cờ phụ; đọc trước khi chấm).

**4. Phiếu hai pha (L6).**

```bash
$PY -m do_luong.phieu cham --pha 1 --bo-cham $D/bo-cham.jsonl --nguoi-cham Quan Dang --out-dir $D/phieu
# nhận lại phiếu pha 1 của cả hai rồi mới phát pha 2
$PY -m do_luong.phieu cham --pha 2 --bo-cham $D/bo-cham.jsonl --nguoi-cham Quan Dang --out-dir $D/phieu
```

Pha 1: `de-pha1.docx` (không có đáp án) + `phieu-pha1-<tên>.xlsx` (người chấm ghi đáp án của mình). Pha 2: `de-pha2.docx`
(có đáp án) + `phieu-pha2-<tên>.xlsx` (năm tiêu chí, cột cuối "Đoán nguồn"). Kèm `docs/experiments/d2/huong-dan-cham.md`.

**5. Phân xử rồi báo cáo.** `docs/experiments/d2/ke-hoach-phan-tich.md` phải được commit TRƯỚC bước này.

```bash
P=$D/phieu
$PY -m do_luong.phan_tich_d2 phan-xu --khoa $D/khoa.csv --phieu $P/phieu-pha2-Quan.xlsx $P/phieu-pha2-Dang.xlsx \
    --pha1 $P/phieu-pha1-Quan.xlsx $P/phieu-pha1-Dang.xlsx --bo-cham $D/bo-cham.jsonl --out-dir $P
# giáo viên điền cột "Kết luận" trong $P/phan-xu.xlsx
# số câu sinh ra / qua format / trùng theo đề (bảng trước/sau, kế hoạch mục 6); chỉ đọc, chạy trên datn_mcq_d2:
docker exec -i datn-mcq-db sh -c 'psql -X -A -t -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d datn_mcq_d2' \
    < tools/measurement/sql/d2-truoc-sau.sql > $D/d2-qc-dem.jsonl
$PY -m do_luong.phan_tich_d2 bao-cao --khoa $D/khoa.csv --phieu $P/phieu-pha2-Quan.xlsx $P/phieu-pha2-Dang.xlsx \
    --pha1 $P/phieu-pha1-Quan.xlsx $P/phieu-pha1-Dang.xlsx --bo-cham $D/bo-cham.jsonl \
    --phan-xu $P/phan-xu.xlsx --lay-mau-log $D/lay-mau-log.json --qc-dem $D/d2-qc-dem.jsonl --out-dir $D/ket-qua-d2
```

`phan-xu.xlsx` gồm ô hai người khác nhau và ô Đáp án của câu mà pha 1 không nhất quán với pha 2 (cột "Lý do xem"). Ra
`ket-qua-d2.md` và CSV: kappa; cờ chính × nhãn theo dạng (precision, recall trong mẫu, recall ước lượng tổng thể, tỉ lệ
lọt, KTC Wilson 95%) cho "lỗi đáp án" (chính) và "lỗi bất kỳ"; riêng bước Solver; D4 theo nhóm (không cờ, cờ chính,
baseline 2, trước QC có trọng số) và một so sánh chính có kiểm định (không cờ vs baseline 2: Fisher, Newcombe); mức độ tách
tầng; pha 1 khớp khóa; đoán nguồn; `cau-lot.csv`. Thêm theo kế hoạch mục 5–6: Bảng 9 KTC bootstrap phân tầng (trước QC,
recall tổng thể, so sánh L9 i–ii; cần `--lay-mau-log`), Bảng 10 bootstrap theo cụm đề/lượt cho so sánh chính (phân tích
nhạy), Bảng 11 tỉ lệ lỗi theo từng đề/lượt, Bảng 12 trước/sau theo tiêu chí (hai dòng đầu cần `--qc-dem`; cảnh báo nếu số
câu qua format khác `lay-mau-log`). B = 10 000 và seed 20261115 cố định trong code, không có tham số dòng lệnh.

D4 đo thêm thời gian và số công thức sửa tay: điền `docs/experiments/d4/bang-do-thoi-gian.md`.

## D6: ngưỡng dedup

**1. Chọn ~150 cặp (chỉ đọc DB, không gọi LLM).** Đứng ở `back_end`, DB trỏ tới `datn_mcq_e2e`, tắt backend khác:

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/datn_mcq_e2e'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=0 --app.worker.enabled=false --app.dedup-gold.seed=20261113 --app.dedup-gold.out-dir=../data/private/do-luong/d6"
```

Ra `cap-vang.csv` (FILE KHÓA: tầng, cosine, chốt chặn, dự đoán ở 10 ngưỡng, cột `lo`, `nhom`, `da_chan`),
`cap-vang-noi-dung.jsonl`, `cap-vang-log.txt`.
Tính lại dự đoán cho đúng tập cặp cũ (ví dụ sau khi sửa DedupChecker): thay `--app.dedup-gold.seed=...` bằng
`--app.dedup-gold.pairs-file=../data/private/do-luong/d6/cap-vang.csv` (ghi ra thư mục khác).

**Lô 2 (bù cho lô 1, vốn dồn vào cặp đổi số liệu).** Giữ lô 1 làm pairs-file và thêm `--app.dedup-gold.batch2-seed=<seed khác>`:
chọn ≤ 60 cặp không trùng lô 1 gồm (1) mọi cặp dedup từng chặn trong DB (`quality_check_result`: câu bị loại, câu nó khớp),
(2) cặp cùng số liệu cosine ≥ 0,90 (≤ 15), (3) cặp cùng số liệu cosine 0,60–0,90 cho đủ; rồi xáo hai lô thành MỘT tập,
đánh số lại 1..N. Cột `lo` (1/2), `nhom` (`lo1`, `da_chan`, `cung_so_lieu_tu_0.90`, `cung_so_lieu_0.60-0.90`) và `da_chan`
chỉ nằm trong file khóa, không lên phiếu.

**2. Phiếu gán nhãn, gán, phân tích.**

```bash
$PY -m do_luong.phieu cap --cap $D/d6/cap-vang-noi-dung.jsonl --nguoi-gan An Quan --out-dir $D/d6/phieu
# An (và Quân nếu kịp) gán theo docs/experiments/d6/huong-dan-gan-nhan.md
$PY -m do_luong.phan_tich_d6 --cap $D/d6/cap-vang.csv --nhan $D/d6/phieu/cap-An.xlsx [$D/d6/phieu/cap-Quan.xlsx] \
    --out-dir $D/d6/ket-qua
```

Phiếu `cap-<tên>.xlsx` có sheet **Luyện tập** (3 cặp mẫu L1–L3, đứng đầu, không tính vào kết quả) và `cap-gan-nhan.docx`
có mục luyện tập ở đầu; `phan_tich_d6` chỉ đọc sheet "Gán nhãn cặp".

Ra `ket-qua-d6.md`, `d6-nguong.csv` (bảng; dòng 0,90 ghi `PROVISIONAL_THRESHOLD`, ngưỡng F1 cao nhất ghi "F1 cao nhất"),
`d6-duong-cong.csv` (dữ liệu vẽ đường P/R/F1 theo ngưỡng; không sinh PNG), `d6-so-nhan.csv` (Trùng / Không trùng / Không
chắc / chưa gán của từng người), `d6-chot-chan.csv` (cosine thô so với cosine + 2 chốt chặn so với DedupChecker đầy đủ;
chốt chặn bỏ được bao nhiêu báo nhầm), `d6-cap-sai-090.csv` (cặp hệ thống đoán sai ở 0,90, để chọn ví dụ),
`d6-nguong-dang-*.csv`, `d6-nguong-lo-*.csv`, `d6-tang.csv`, kappa và `d6-cap-lech-nhan.csv` nếu có người gán thứ hai.

## Tệp

| Tệp | Vai trò |
|---|---|
| `sql/d2-xuat-cau.sql` | Một câu SELECT xuất câu LLM của đề giáo viên + dữ liệu QC thô |
| `sql/d2-truoc-sau.sql` | Một câu SELECT: số câu sinh ra / qua format / trùng theo đề (bảng trước/sau QC) |
| `do_luong/co_qc.py` | Lý do "cần xem kỹ" và cờ nội dung (bản chép QcSummaryService + ExamViewService + hasContentFlag) |
| `do_luong/cau.py` | Định dạng câu thống nhất, Markdown giống ExamMarkdownBuilder (pandoc `markdown+tex_math_dollars`) |
| `do_luong/lay_mau.py` | Lấy mẫu phân tầng có seed, gán mã ngẫu nhiên, file khóa |
| `do_luong/phieu.py` | `.docx` (pandoc) và `.xlsx` có danh sách chọn |
| `do_luong/ma_tran.py` | Ma trận mẫu → danh sách câu và dạng chữ cho prompt baseline |
| `do_luong/baseline_goi.py`, `baseline_parse.py` | Gọi Gemini (dùng lại `llm-probe/llm_formula_probe.call_gemini_with_retry`) và parser chịu lỗi |
| `do_luong/thong_ke.py` | Wilson, Cohen's kappa, Fisher exact, P/R/F1, recall có trọng số, chênh lệch Newcombe |
| `do_luong/phan_tich_d2.py`, `phan_tich_d6.py`, `xuat_bang.py` | Bảng Markdown + CSV cho chương 4 |
