"""D6: precision / recall / F1 của bộ dedup 3-gram theo ngưỡng (0,50 -> 0,95, bước 0,05) trên tập cặp gán nhãn.

Đầu vào:
  cap-vang.csv     do DedupGoldRunner (back_end, goldset/tool) ghi: mỗi cặp một dòng, dự đoán của DedupChecker.check ở
                   từng ngưỡng (cột du_doan_050 ... du_doan_095), điểm cosine, tầng lấy mẫu. File khóa, không đưa người gán.
  cap-<ten>.xlsx   phiếu gán nhãn đã điền (phieu.py cap). Người thứ nhất là nhãn chính; người thứ hai (nếu có) để tính kappa.
Nhãn "Không chắc" bị loại khỏi P/R (báo số lượng). Dương tính = "Trùng".

Chạy (đứng ở tools/measurement):
  python -m do_luong.phan_tich_d6 --cap <dir>/cap-vang.csv --nhan <dir>/cap-An.xlsx [<dir>/cap-Quan.xlsx] \
      --out-dir <dir>/ket-qua-d6
"""
import argparse
import csv
import os

from . import thong_ke as tk
from . import xuat_bang as xb

PROVISIONAL_THRESHOLD = 0.90  # DedupChecker.PROVISIONAL_THRESHOLD
NGUONG = [round(0.50 + 0.05 * i, 2) for i in range(10)]
NHAN_HOP_LE = ("Trùng", "Không trùng", "Không chắc")


def cot_du_doan(t):
    return f"du_doan_{int(round(t * 100)):03d}"


def doc_cap(path):
    with open(path, encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def doc_nhan(path):
    """Phiếu cặp .xlsx -> {số cặp (str): nhãn}. Nhãn lạ -> ValueError."""
    from openpyxl import load_workbook
    ws = load_workbook(path, read_only=True, data_only=True)["Gán nhãn cặp"]
    out = {}
    for row in ws.iter_rows(min_row=2, values_only=True):
        if not row or row[0] is None or row[4] in (None, ""):
            continue
        v = str(row[4]).strip()
        if v not in NHAN_HOP_LE:
            raise ValueError(f"{os.path.basename(path)}: cặp {row[0]}: nhãn lạ '{v}'")
        out[str(row[0]).strip()] = v
    return out


def bang_nguong(caps, nhan, loc=None):
    headers = ["Ngưỡng", "TP", "FP", "FN", "TN", "Precision [KTC 95%]", "Recall [KTC 95%]", "F1", "Ghi chú"]
    rows, curve = [], []
    ds = [c for c in caps if nhan.get(c["cap"]) in ("Trùng", "Không trùng") and (loc is None or loc(c))]
    for t in NGUONG:
        col = cot_du_doan(t)
        tp = sum(1 for c in ds if c[col] == "1" and nhan[c["cap"]] == "Trùng")
        fp = sum(1 for c in ds if c[col] == "1" and nhan[c["cap"]] == "Không trùng")
        fn = sum(1 for c in ds if c[col] != "1" and nhan[c["cap"]] == "Trùng")
        tn = len(ds) - tp - fp - fn
        p, r, f1 = tk.prf(tp, fp, fn)
        pw, rw = tk.wilson(tp, tp + fp), tk.wilson(tp, tp + fn)
        note = "PROVISIONAL_THRESHOLD (hiện tại)" if abs(t - PROVISIONAL_THRESHOLD) < 1e-9 else ""
        rows.append([xb.so(t, 2), tp, fp, fn, tn, xb.phan_tram(*pw), xb.phan_tram(*rw), xb.so(f1), note])
        curve.append([f"{t:.2f}", tp, fp, fn, tn, "" if p is None else f"{p:.4f}", "" if r is None else f"{r:.4f}",
                      "" if f1 is None else f"{f1:.4f}", int(bool(note))])
    return headers, rows, curve, len(ds)


def bang_tang(caps, nhan):
    headers = ["Tầng / dạng", "Số cặp", "Trùng", "Không trùng", "Không chắc", "Chưa gán", "Dedup báo trùng ở 0,90"]
    rows = []
    col = cot_du_doan(PROVISIONAL_THRESHOLD)
    for key in ("tang", "dang"):
        for v in sorted({c[key] for c in caps}):
            ds = [c for c in caps if c[key] == v]
            rows.append([f"{key}={v}", len(ds),
                         sum(1 for c in ds if nhan.get(c["cap"]) == "Trùng"),
                         sum(1 for c in ds if nhan.get(c["cap"]) == "Không trùng"),
                         sum(1 for c in ds if nhan.get(c["cap"]) == "Không chắc"),
                         sum(1 for c in ds if c["cap"] not in nhan),
                         sum(1 for c in ds if c[col] == "1")])
    return headers, rows


def bao_cao(caps, nhan_list, ten_list, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    nhan = nhan_list[0]
    md = ["# Kết quả D6: ngưỡng dedup 3-gram (tự sinh bởi do_luong.phan_tich_d6)", "",
          f"Tập cặp: {len(caps)} cặp. Nhãn chính: {ten_list[0]}"
          + (f"; người gán thứ hai: {', '.join(ten_list[1:])}." if len(ten_list) > 1 else
             ". Một người gán (ghi ở phần hạn chế)."), ""]
    h, rows, curve, n = bang_nguong(caps, nhan)
    md += [f"## Bảng D6-1. P/R/F1 theo ngưỡng ({n} cặp có nhãn Trùng/Không trùng)", "", xb.markdown(h, rows), ""]
    xb.ghi_csv(os.path.join(out_dir, "d6-nguong.csv"), h, rows)
    xb.ghi_csv(os.path.join(out_dir, "d6-duong-cong.csv"),
               ["nguong", "tp", "fp", "fn", "tn", "precision", "recall", "f1", "provisional"], curve)
    for dang in sorted({c["dang"] for c in caps}):
        h2, rows2, _, n2 = bang_nguong(caps, nhan, loc=lambda c, d=dang: c["dang"] == d)
        md += [f"## Bảng D6-1/{dang} ({n2} cặp)", "", xb.markdown(h2, rows2), ""]
        xb.ghi_csv(os.path.join(out_dir, f"d6-nguong-{dang}.csv"), h2, rows2)
    h, rows = bang_tang(caps, nhan)
    md += ["## Bảng D6-2. Số cặp theo tầng lấy mẫu và dạng", "", xb.markdown(h, rows), ""]
    xb.ghi_csv(os.path.join(out_dir, "d6-tang.csv"), h, rows)
    if len(nhan_list) > 1:
        ma = [c["cap"] for c in caps]
        kappa, n, po = tk.cohen_kappa([nhan_list[0].get(m) for m in ma], [nhan_list[1].get(m) for m in ma])
        md += ["## Bảng D6-3. Đồng thuận gán nhãn", "",
               xb.markdown(["Người gán", "Số cặp", "Đồng ý", "Cohen's κ"],
                           [[" – ".join(ten_list[:2]), n, xb.phan_tram(po), xb.so(kappa)]]), ""]
        lech = [[m, nhan_list[0].get(m, ""), nhan_list[1].get(m, "")] for m in ma
                if nhan_list[0].get(m) and nhan_list[1].get(m) and nhan_list[0][m] != nhan_list[1][m]]
        xb.ghi_csv(os.path.join(out_dir, "d6-cap-lech-nhan.csv"), ["cap", ten_list[0], ten_list[1]], lech)
    md += ["Ghi chú: tập cặp được lấy mẫu dồn vào vùng cosine 0,6–0,95 nên P/R là trên tập hiệu chỉnh, không phải tỉ lệ "
           "của toàn bộ cặp trong ngân hàng.", ""]
    with open(os.path.join(out_dir, "ket-qua-d6.md"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(md))


def main(argv=None):
    ap = argparse.ArgumentParser(description="D6: P/R/F1 của dedup theo ngưỡng.")
    ap.add_argument("--cap", required=True, help="cap-vang.csv")
    ap.add_argument("--nhan", nargs="+", required=True, help="phiếu cặp đã gán; người đầu là nhãn chính")
    ap.add_argument("--ten", nargs="*", help="tên người gán theo thứ tự --nhan")
    ap.add_argument("--out-dir", required=True)
    a = ap.parse_args(argv)
    ten = a.ten or [os.path.splitext(os.path.basename(p))[0].removeprefix("cap-") for p in a.nhan]
    bao_cao(doc_cap(a.cap), [doc_nhan(p) for p in a.nhan], ten, a.out_dir)
    print(f"Đã ghi {a.out_dir}")


if __name__ == "__main__":
    main()
