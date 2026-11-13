"""D6: precision / recall / F1 của bộ dedup 3-gram theo ngưỡng (0,50 -> 0,95, bước 0,05) trên tập cặp gán nhãn.

Đầu vào:
  cap-vang.csv     do DedupGoldRunner (back_end, goldset/tool) ghi: mỗi cặp một dòng, dự đoán của DedupChecker.check ở
                   từng ngưỡng (cột du_doan_050 ... du_doan_095), điểm cosine, tầng lấy mẫu. File khóa, không đưa người gán.
  cap-<ten>.xlsx   phiếu gán nhãn đã điền (phieu.py cap). Người thứ nhất là nhãn chính; người thứ hai (nếu có) để tính kappa.
Nhãn "Không chắc" bị loại khỏi P/R (báo số lượng). Dương tính = "Trùng".
Ra: ket-qua-d6.md và CSV: P/R/F1 theo ngưỡng (đánh dấu 0,90 hiện tại và ngưỡng F1 cao nhất; tách theo dạng và theo lô),
số nhãn từng người, cosine thô so với cosine + 2 chốt chặn (cần các cột cung_so_lieu, cung_phu_dinh, cung_van_tay),
danh sách cặp đoán sai ở 0,90, dữ liệu vẽ đường cong, kappa và cặp lệch nhãn khi có hai người.

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


def co_nhan(c, nhan):
    return nhan.get(c["cap"]) in ("Trùng", "Không trùng")


def dem(ds, nhan, du_doan):
    """(TP, FP, FN, TN) của hàm dự đoán du_doan(c) -> bool trên các cặp ds (đã có nhãn Trùng/Không trùng)."""
    tp = sum(1 for c in ds if du_doan(c) and nhan[c["cap"]] == "Trùng")
    fp = sum(1 for c in ds if du_doan(c) and nhan[c["cap"]] == "Không trùng")
    fn = sum(1 for c in ds if not du_doan(c) and nhan[c["cap"]] == "Trùng")
    return tp, fp, fn, len(ds) - tp - fp - fn


def bang_nguong(caps, nhan, loc=None):
    """P/R/F1 theo ngưỡng. Ghi chú đánh dấu ngưỡng hiện tại và (các) ngưỡng có F1 cao nhất."""
    headers = ["Ngưỡng", "TP", "FP", "FN", "TN", "Precision [KTC 95%]", "Recall [KTC 95%]", "F1", "Ghi chú"]
    rows, curve = [], []
    ds = [c for c in caps if co_nhan(c, nhan) and (loc is None or loc(c))]
    kq = []
    for t in NGUONG:
        col = cot_du_doan(t)
        tp, fp, fn, tn = dem(ds, nhan, lambda c, col=col: c[col] == "1")
        kq.append((t, tp, fp, fn, tn) + tk.prf(tp, fp, fn))
    f1_max = max((k[7] for k in kq if k[7] is not None), default=None)
    for t, tp, fp, fn, tn, p, r, f1 in kq:
        ghi = []
        if abs(t - PROVISIONAL_THRESHOLD) < 1e-9:
            ghi.append("PROVISIONAL_THRESHOLD (hiện tại)")
        cao = f1 is not None and f1_max is not None and f1_max > 0 and abs(f1 - f1_max) < 1e-12
        if cao:
            ghi.append("F1 cao nhất")
        pw, rw = tk.wilson(tp, tp + fp), tk.wilson(tp, tp + fn)
        rows.append([xb.so(t, 2), tp, fp, fn, tn, xb.phan_tram(*pw), xb.phan_tram(*rw), xb.so(f1), "; ".join(ghi)])
        curve.append([f"{t:.2f}", tp, fp, fn, tn, "" if p is None else f"{p:.4f}", "" if r is None else f"{r:.4f}",
                      "" if f1 is None else f"{f1:.4f}", int(bool(cao)),
                      int(abs(t - PROVISIONAL_THRESHOLD) < 1e-9)])
    return headers, rows, curve, len(ds)


def co_cot_chot_chan(caps):
    return bool(caps) and all(k in caps[0] for k in ("cosine", "cung_so_lieu", "cung_phu_dinh", "cung_van_tay"))


def du_doan_tho(c, t):
    """Chỉ cosine 3-gram, không chốt chặn: cosine >= t (cùng dấu vân tay thì cosine = 1)."""
    return float(c["cosine"]) >= t - 1e-12


def du_doan_chot_chan(c, t):
    """Cosine + hai chốt chặn (số liệu, phủ định) như DedupChecker.check; trùng dấu vân tay luôn là trùng."""
    return c["cung_van_tay"] == "1" or (du_doan_tho(c, t) and c["cung_so_lieu"] == "1" and c["cung_phu_dinh"] == "1")


def bang_chot_chan(caps, nhan, loc=None):
    """So ba cách dự đoán theo ngưỡng: (a) cosine thô, (b) cosine + 2 chốt chặn, (c) DedupChecker.check đầy đủ (cột
    du_doan_XXX, gồm cả tầng theo ý của câu Đ/S). Trả None khi cap-vang.csv không có cột chẩn đoán.

    "Chốt chặn bỏ được FP" = FP(a) − FP(b); "Chốt chặn làm mất TP" = TP(a) − TP(b).
    """
    if not co_cot_chot_chan(caps):
        return None
    headers = ["Ngưỡng", "FP thô", "FP + chốt chặn", "Chốt chặn bỏ được FP", "TP thô", "TP + chốt chặn",
               "Chốt chặn làm mất TP", "P thô", "P + chốt chặn", "P đầy đủ", "R thô", "R + chốt chặn", "R đầy đủ",
               "F1 thô", "F1 + chốt chặn", "F1 đầy đủ"]
    ds = [c for c in caps if co_nhan(c, nhan) and (loc is None or loc(c))]
    rows = []
    for t in NGUONG:
        col = cot_du_doan(t)
        a = dem(ds, nhan, lambda c, t=t: du_doan_tho(c, t))
        b = dem(ds, nhan, lambda c, t=t: du_doan_chot_chan(c, t))
        d = dem(ds, nhan, lambda c, col=col: c[col] == "1")
        pa, pb, pd = tk.prf(*a[:3]), tk.prf(*b[:3]), tk.prf(*d[:3])
        rows.append([xb.so(t, 2), a[1], b[1], a[1] - b[1], a[0], b[0], a[0] - b[0],
                     xb.so(pa[0]), xb.so(pb[0]), xb.so(pd[0]), xb.so(pa[1]), xb.so(pb[1]), xb.so(pd[1]),
                     xb.so(pa[2]), xb.so(pb[2]), xb.so(pd[2])])
    return headers, rows, len(ds)


def cap_doan_sai(caps, nhan, t=PROVISIONAL_THRESHOLD):
    """Cặp hệ thống đoán sai ở ngưỡng t (bỏ Không chắc / chưa gán): FP = báo trùng nhầm, FN = bỏ sót. Để chọn ví dụ."""
    col = cot_du_doan(t)
    headers = ["cap", "q1", "q2", "dang", "tang", "lo", "nhom", "cosine", "cung_so_lieu", "cung_phu_dinh",
               "nhan", "he_thong_doan", "loai"]
    rows = []
    for c in caps:
        if not co_nhan(c, nhan):
            continue
        thuc, doan = nhan[c["cap"]] == "Trùng", c[col] == "1"
        if thuc == doan:
            continue
        rows.append([c["cap"]] + [c.get(k, "") for k in headers[1:10]]
                    + [nhan[c["cap"]], "Trùng" if doan else "Không trùng",
                       "FP (báo trùng nhầm)" if doan else "FN (bỏ sót)"])
    rows.sort(key=lambda r: (r[-1], -(float(r[7]) if r[7] else 0.0)))
    return headers, rows


def bang_nhan(caps, nhan_list, ten_list):
    """Số cặp theo nhãn của từng người gán (kể cả Không chắc và chưa gán)."""
    headers = ["Người gán", "Số cặp", "Trùng", "Không trùng", "Không chắc", "Chưa gán"]
    rows = []
    for ten, nhan in zip(ten_list, nhan_list):
        rows.append([ten, len(caps)] + [sum(1 for c in caps if nhan.get(c["cap"]) == v) for v in NHAN_HOP_LE]
                    + [sum(1 for c in caps if c["cap"] not in nhan)])
    return headers, rows


def bang_tang(caps, nhan):
    headers = ["Tầng / dạng", "Số cặp", "Trùng", "Không trùng", "Không chắc", "Chưa gán", "Dedup báo trùng ở 0,90"]
    rows = []
    col = cot_du_doan(PROVISIONAL_THRESHOLD)
    for key in [k for k in ("tang", "dang", "lo", "nhom") if caps and k in caps[0]]:
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
    # Dữ liệu vẽ đường P-R / P,R,F1 theo ngưỡng (không sinh PNG; vẽ sau từ CSV này).
    xb.ghi_csv(os.path.join(out_dir, "d6-duong-cong.csv"),
               ["nguong", "tp", "fp", "fn", "tn", "precision", "recall", "f1", "f1_cao_nhat", "provisional"], curve)
    hn, rn = bang_nhan(caps, nhan_list, ten_list)
    md += ["## Bảng D6-0. Số cặp theo nhãn (\"Không chắc\" và chưa gán bị loại khỏi P/R)", "", xb.markdown(hn, rn), ""]
    xb.ghi_csv(os.path.join(out_dir, "d6-so-nhan.csv"), hn, rn)
    ket = bang_chot_chan(caps, nhan)
    if ket:
        hc, rc, nc = ket
        md += [f"## Bảng D6-4. Cosine thô so với cosine + 2 chốt chặn ({nc} cặp có nhãn)", "",
               "Thô = cosine >= ngưỡng; + chốt chặn = thêm điều kiện cùng số liệu và cùng phủ định (trùng dấu vân tay "
               "luôn là trùng); đầy đủ = DedupChecker.check (cột du_doan_XXX).", "", xb.markdown(hc, rc), ""]
        xb.ghi_csv(os.path.join(out_dir, "d6-chot-chan.csv"), hc, rc)
    hs, rs = cap_doan_sai(caps, nhan)
    md += [f"## Bảng D6-5. Cặp hệ thống đoán sai ở {PROVISIONAL_THRESHOLD:.2f} ({len(rs)} cặp; chi tiết d6-cap-sai-090.csv)",
           "", xb.markdown(hs, rs), ""]
    xb.ghi_csv(os.path.join(out_dir, "d6-cap-sai-090.csv"), hs, rs)
    for key in [k for k in ("dang", "lo") if caps and k in caps[0]]:
        for v in sorted({c[key] for c in caps}):
            h2, rows2, _, n2 = bang_nguong(caps, nhan, loc=lambda c, k=key, v=v: c[k] == v)
            md += [f"## Bảng D6-1/{key}={v} ({n2} cặp)", "", xb.markdown(h2, rows2), ""]
            xb.ghi_csv(os.path.join(out_dir, f"d6-nguong-{key}-{v}.csv"), h2, rows2)
    h, rows = bang_tang(caps, nhan)
    md += ["## Bảng D6-2. Số cặp theo tầng lấy mẫu, dạng, lô và nhóm", "", xb.markdown(h, rows), ""]
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
           "của toàn bộ cặp trong ngân hàng. Lô 2 (nếu có) được chọn có chủ đích (cặp dedup từng chặn, cặp cùng số liệu): "
           "đọc P/R theo từng lô (bảng D6-1/lo=...) trước khi gộp. Đường P/R/F1 theo ngưỡng: d6-duong-cong.csv "
           "(không sinh ảnh).", ""]
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
