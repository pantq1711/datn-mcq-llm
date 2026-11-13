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
tools/measurement/.venv/Scripts/python -m unittest discover tools/measurement/tests   # 56 test, không cần DB hay mạng
```

Các lệnh bên dưới chạy từ `tools/measurement` với `PY=.venv/Scripts/python` và `D=../../data/private/do-luong`.

## D2 + D4: bộ chấm mù ~100 câu

**1. Xuất câu hệ thống (An chạy trên VPS, chép file về `$D/cau-he-thong.jsonl`).**

```bash
psql -X -A -t -v ON_ERROR_STOP=1 -d <db> -f tools/measurement/sql/d2-xuat-cau.sql -o cau-he-thong.jsonl
# mặc định chỉ lấy prompt hiện tại (matrix-mcq-v8-draft, matrix-tf-v10-draft, matrix-sa-v10-draft);
# đổi bằng -v prompt_versions=a,b,c
```

Thử cục bộ trên `datn_mcq_e2e` (docker): `docker exec -i datn-mcq-db sh -c 'psql -X -A -t -U "$POSTGRES_USER" -d datn_mcq_e2e' < tools/measurement/sql/d2-xuat-cau.sql > $D/cau-he-thong.jsonl`.
Cờ được tính ở Python (`do_luong/co_qc.py`) đúng như trang đề: `needsReview` và không chỉ có `similar_in_exam`
(hasContentFlag); cảnh báo tự sửa ("json: đã sửa …") không bật cờ. Khớp với backend được giữ bằng fixture chung
`tests/fixtures/co-qc-parity.json` (test Python `test_co_qc` và test Java `QcFlagParityTest`).

**2. Baseline (sau khi An duyệt `docs/experiments/d4/prompts.md`).**

```bash
$PY -m do_luong.baseline_goi --dry-run --out-dir $D/baseline-tho          # xem prompt, không cần khóa
export GEMINI_API_KEY=...                                                  # gõ trong terminal, không lưu file
$PY -m do_luong.baseline_goi --model gemini-3.5-flash-lite --so-de 2 --out-dir $D/baseline-tho   # 4 lượt gọi
$PY -m do_luong.baseline_parse --tho-dir $D/baseline-tho --out-dir $D
```

Câu không parse được nằm trong `$D/parse-loi.jsonl` (lý do + khối chữ gốc + bản parse dở). Sửa **phần định dạng** (không
sửa nội dung toán), chép từng câu đã sửa (một dòng JSON, giữ `id`) vào `$D/baseline-sua-tay.jsonl`, chạy lại
`baseline_parse ... --sua-tay $D/baseline-sua-tay.jsonl`. Báo số câu phải sửa tay trong chương 4.

**3. Lấy mẫu + gán mã + file khóa.**

```bash
$PY -m do_luong.lay_mau --he-thong $D/cau-he-thong.jsonl --baseline $D/baseline_1.jsonl $D/baseline_2.jsonl \
    --n-he-thong 60 --n-baseline 20 --seed 20261113 --out-dir $D
```

Hệ thống: 20 câu mỗi dạng, mỗi dạng 1/2 có cờ, ưu tiên câu của ma trận `giuaki1-toan8`; tầng thiếu thì bù và ghi vào
`$D/lay-mau-log.json` (đọc file này trước khi chấm). Baseline: 20 câu mỗi prompt chia theo tỉ lệ dạng. Ra
`$D/khoa.csv` (FILE KHÓA), `$D/bo-cham.jsonl`.

**4. Phiếu.**

```bash
$PY -m do_luong.phieu cham --bo-cham $D/bo-cham.jsonl --nguoi-cham Quan Dang --out-dir $D/phieu
```

Gửi `de-cham.docx` + `phieu-Quan.xlsx` cho Quân, `de-cham.docx` + `phieu-Dang.xlsx` cho Đang, kèm
`docs/experiments/d2/huong-dan-cham.md`.

**5. Phân xử rồi báo cáo.**

```bash
$PY -m do_luong.phan_tich_d2 phan-xu --khoa $D/khoa.csv --phieu $D/phieu/phieu-Quan.xlsx $D/phieu/phieu-Dang.xlsx --out-dir $D/phieu
# giáo viên điền cột "Kết luận" trong $D/phieu/phan-xu.xlsx
$PY -m do_luong.phan_tich_d2 bao-cao --khoa $D/khoa.csv --phieu $D/phieu/phieu-Quan.xlsx $D/phieu/phieu-Dang.xlsx \
    --phan-xu $D/phieu/phan-xu.xlsx --he-thong-xuat $D/cau-he-thong.jsonl --out-dir $D/ket-qua-d2
```

Ra `ket-qua-d2.md` (dán vào chương 4) và CSV: kappa từng tiêu chí; cờ × nhãn theo dạng (precision, recall trong mẫu,
recall ước lượng tổng thể, tỉ lệ lọt, KTC Wilson 95%) cho "lỗi đáp án" và "lỗi bất kỳ"; riêng bước Solver; D4 theo nhóm
với Fisher exact và chênh lệch tỉ lệ (KTC Newcombe); `cau-lot.csv` (câu lọt, cột `loai_loi` để phân loại).

D4 đo thêm thời gian và số công thức sửa tay: điền `docs/experiments/d4/bang-do-thoi-gian.md`.

## D6: ngưỡng dedup

**1. Chọn ~150 cặp (chỉ đọc DB, không gọi LLM).** Đứng ở `back_end`, DB trỏ tới `datn_mcq_e2e`, tắt backend khác:

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/datn_mcq_e2e'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=0 --app.worker.enabled=false --app.dedup-gold.seed=20261113 --app.dedup-gold.out-dir=../data/private/do-luong/d6"
```

Ra `cap-vang.csv` (FILE KHÓA: tầng, cosine, chốt chặn, dự đoán ở 10 ngưỡng), `cap-vang-noi-dung.jsonl`, `cap-vang-log.txt`.
Tính lại dự đoán cho đúng tập cặp cũ (ví dụ sau khi sửa DedupChecker): thay `--app.dedup-gold.seed=...` bằng
`--app.dedup-gold.pairs-file=../data/private/do-luong/d6/cap-vang.csv` (ghi ra thư mục khác).

**2. Phiếu gán nhãn, gán, phân tích.**

```bash
$PY -m do_luong.phieu cap --cap $D/d6/cap-vang-noi-dung.jsonl --nguoi-gan An Quan --out-dir $D/d6/phieu
# An (và Quân nếu kịp) gán theo docs/experiments/d6/huong-dan-gan-nhan.md
$PY -m do_luong.phan_tich_d6 --cap $D/d6/cap-vang.csv --nhan $D/d6/phieu/cap-An.xlsx [$D/d6/phieu/cap-Quan.xlsx] \
    --out-dir $D/d6/ket-qua
```

Ra `ket-qua-d6.md`, `d6-nguong.csv` (bảng, dòng 0,90 ghi `PROVISIONAL_THRESHOLD`), `d6-duong-cong.csv` (dữ liệu vẽ đường
P/R/F1 theo ngưỡng), bảng theo dạng và theo tầng, kappa nếu có người gán thứ hai.

## Tệp

| Tệp | Vai trò |
|---|---|
| `sql/d2-xuat-cau.sql` | Một câu SELECT xuất câu LLM của đề giáo viên + dữ liệu QC thô |
| `do_luong/co_qc.py` | Lý do "cần xem kỹ" và cờ nội dung (bản chép QcSummaryService + ExamViewService + hasContentFlag) |
| `do_luong/cau.py` | Định dạng câu thống nhất, Markdown giống ExamMarkdownBuilder (pandoc `markdown+tex_math_dollars`) |
| `do_luong/lay_mau.py` | Lấy mẫu phân tầng có seed, gán mã ngẫu nhiên, file khóa |
| `do_luong/phieu.py` | `.docx` (pandoc) và `.xlsx` có danh sách chọn |
| `do_luong/ma_tran.py` | Ma trận mẫu → danh sách câu và dạng chữ cho prompt baseline |
| `do_luong/baseline_goi.py`, `baseline_parse.py` | Gọi Gemini (dùng lại `llm-probe/llm_formula_probe.call_gemini_with_retry`) và parser chịu lỗi |
| `do_luong/thong_ke.py` | Wilson, Cohen's kappa, Fisher exact, P/R/F1, recall có trọng số, chênh lệch Newcombe |
| `do_luong/phan_tich_d2.py`, `phan_tich_d6.py`, `xuat_bang.py` | Bảng Markdown + CSV cho chương 4 |
