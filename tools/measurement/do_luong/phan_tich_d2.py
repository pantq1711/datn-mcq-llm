"""Phân tích D2 (QC so với nhãn người chấm) và D4 (hệ thống so với gọi thẳng Gemini) từ phiếu chấm mù.

Hai bước:
  1. phan-xu: đọc phiếu 2 người chấm, tính kappa, ghi phan-xu.xlsx gồm các ô bất đồng (không có nguồn, cờ).
  2. bao-cao: đọc phiếu + phan-xu.xlsx đã điền + khoa.csv, ra bảng Markdown/CSV cho chương 4.

Định nghĩa (khớp docs/experiments/d2/huong-dan-cham.md):
  loi_dap_an  = Đáp án khác "Đúng" (sai, nhiều đáp án đúng, không có đáp án đúng; Đ/S có ý sai nhãn; sa sai đáp số)
  loi_bat_ky  = loi_dap_an hoặc Vượt lớp = Có hoặc Mơ hồ = Có
  (Đúng mức độ báo riêng, không tính là lỗi.)
Câu hệ thống: "dương tính" = có cờ nội dung (khoa.csv co_qc = 1).
  precision = lỗi ∩ có cờ / có cờ; recall (trong mẫu) = lỗi ∩ có cờ / lỗi; tỉ lệ lọt = lỗi ∩ không cờ / không cờ.
  Mẫu phân tầng 1/2 có cờ nên recall trong mẫu lệch so với tổng thể; khi có --he-thong-xuat, báo thêm recall ước lượng
  cho tổng thể (thong_ke.recall_trong_so) theo cỡ hai tầng trong file xuất.

Chạy (đứng ở tools/measurement):
  python -m do_luong.phan_tich_d2 phan-xu --khoa <dir>/khoa.csv --phieu <dir>/phieu-Quan.xlsx <dir>/phieu-Dang.xlsx --out-dir <dir>
  python -m do_luong.phan_tich_d2 bao-cao --khoa <dir>/khoa.csv --phieu <...> <...> --phan-xu <dir>/phan-xu.xlsx \
      --he-thong-xuat <dir>/cau-he-thong.jsonl --out-dir <dir>/ket-qua-d2
"""
import argparse
import os

from . import cau as cau_mod
from . import lay_mau
from . import phieu as phieu_mod
from . import thong_ke as tk
from . import xuat_bang as xb

TIEU_CHI = ["dap_an", "y_sai", "muc_do", "vuot_lop", "mo_ho"]
TEN_TIEU_CHI = {"dap_an": "Đáp án", "y_sai": "Ý sai (Đ/S)", "muc_do": "Đúng mức độ", "vuot_lop": "Vượt lớp",
                "mo_ho": "Mơ hồ"}
HOP_LE = {"dap_an": set(phieu_mod.DAP_AN_MCQ), "muc_do": set(phieu_mod.MUC_DO), "vuot_lop": set(phieu_mod.CO_KHONG),
          "mo_ho": set(phieu_mod.CO_KHONG)}
DINH_NGHIA_LOI = ("loi_dap_an", "loi_bat_ky")
TEN_NGUON = {"he_thong": "Hệ thống", "baseline_1": "Baseline 1 (kiểu giáo viên)", "baseline_2": "Baseline 2 (prompt tốt nhất)"}


def chuan_y_sai(v):
    """"b, d" / "D,b" -> "b,d"; rỗng -> ""."""
    if v is None:
        return ""
    letters = sorted({ch for ch in str(v).lower() if ch in "abcd"})
    return ",".join(letters)


def doc_phieu(path):
    """Phiếu .xlsx -> {mã: {tiêu chí: giá trị}}. Giá trị ngoài danh sách -> ValueError (không bỏ im lặng)."""
    from openpyxl import load_workbook
    ws = load_workbook(path, read_only=True, data_only=True)["Phiếu chấm"]
    out = {}
    for row in ws.iter_rows(min_row=2, values_only=True):
        if not row or row[1] is None:
            continue
        ma = str(row[1]).strip()
        nhan = {"dap_an": row[3], "y_sai": chuan_y_sai(row[4]), "muc_do": row[5], "vuot_lop": row[6],
                "mo_ho": row[7], "ghi_chu": row[8] or ""}
        for k, ok in HOP_LE.items():
            v = nhan[k]
            nhan[k] = None if v in (None, "") else str(v).strip()
            if nhan[k] is not None and nhan[k] not in ok:
                raise ValueError(f"{os.path.basename(path)}: mã {ma}, cột {TEN_TIEU_CHI[k]}: giá trị lạ '{v}'")
        out[ma] = nhan
    return out


def kappa_bang(khoa, p1, p2):
    rows = []
    for tc in TIEU_CHI:
        ma_list = [k["ma"] for k in khoa if tc != "y_sai" or k["dang"] == "tf"]
        if tc == "y_sai":
            # chỉ so ý sai khi cả hai cùng chấm Sai (đã đồng ý câu có ý sai)
            ma_list = [m for m in ma_list if p1.get(m, {}).get("dap_an") == "Sai" == p2.get(m, {}).get("dap_an")]
        a = [p1.get(m, {}).get(tc) for m in ma_list]
        b = [p2.get(m, {}).get(tc) for m in ma_list]
        kappa, n, po = tk.cohen_kappa(a, b)
        rows.append([TEN_TIEU_CHI[tc], n, xb.phan_tram(po), xb.so(kappa)])
    # tiêu chí gộp: có lỗi bất kỳ
    a = [int(_co_loi(p1.get(k["ma"]), "loi_bat_ky")) if p1.get(k["ma"]) and p1[k["ma"]]["dap_an"] else None for k in khoa]
    b = [int(_co_loi(p2.get(k["ma"]), "loi_bat_ky")) if p2.get(k["ma"]) and p2[k["ma"]]["dap_an"] else None for k in khoa]
    kappa, n, po = tk.cohen_kappa(a, b)
    rows.append(["Có lỗi (gộp)", n, xb.phan_tram(po), xb.so(kappa)])
    return ["Tiêu chí", "Số câu", "Đồng ý", "Cohen's κ"], rows


def bat_dong(khoa, p1, p2):
    """Danh sách (thứ tự, mã, tiêu chí, nhãn 1, nhãn 2) cần phân xử."""
    out = []
    for k in sorted(khoa, key=lambda r: int(r["thu_tu"])):
        m = k["ma"]
        a, b = p1.get(m, {}), p2.get(m, {})
        for tc in TIEU_CHI:
            if tc == "y_sai" and k["dang"] != "tf":
                continue
            if a.get(tc) != b.get(tc):
                out.append((int(k["thu_tu"]), m, tc, a.get(tc) or "", b.get(tc) or ""))
    return out


def ghi_phan_xu(path, ds):
    from openpyxl import Workbook
    from openpyxl.worksheet.datavalidation import DataValidation
    wb = Workbook()
    ws = wb.active
    ws.title = "Phân xử"
    ws.append(["Thứ tự", "Mã", "Tiêu chí", "Người chấm 1", "Người chấm 2", "Kết luận", "Ghi chú"])
    lists = {"dap_an": phieu_mod.DAP_AN_MCQ, "muc_do": phieu_mod.MUC_DO, "vuot_lop": phieu_mod.CO_KHONG,
             "mo_ho": phieu_mod.CO_KHONG}
    dvs = {}
    for i, (tt, m, tc, a, b) in enumerate(ds, start=2):
        ws.append([tt, m, TEN_TIEU_CHI[tc], a, b, None, None])
        if tc in lists:
            key = tc
            if key not in dvs:
                dvs[key] = DataValidation(type="list", formula1='"' + ",".join(lists[tc]) + '"', allow_blank=True)
                ws.add_data_validation(dvs[key])
            dvs[key].add(f"F{i}")
    for col, w in {"A": 8, "B": 8, "C": 16, "D": 22, "E": 22, "F": 22, "G": 50}.items():
        ws.column_dimensions[col].width = w
    wb.save(path)


def doc_phan_xu(path):
    """{(mã, tiêu chí): kết luận}."""
    from openpyxl import load_workbook
    ws = load_workbook(path, read_only=True, data_only=True)["Phân xử"]
    rev = {v: k for k, v in TEN_TIEU_CHI.items()}
    out = {}
    for row in ws.iter_rows(min_row=2, values_only=True):
        if not row or row[1] is None:
            continue
        tc = rev[row[2]]
        v = row[5]
        if v in (None, ""):
            continue
        out[(str(row[1]).strip(), tc)] = chuan_y_sai(v) if tc == "y_sai" else str(v).strip()
    return out


def nhan_cuoi(khoa, p1, p2, px):
    """Nhãn sau phân xử: hai người đồng ý thì lấy, không thì lấy kết luận phân xử. Trả (nhãn, mã còn thiếu)."""
    out, thieu = {}, []
    for k in khoa:
        m = k["ma"]
        a, b = p1.get(m, {}), p2.get(m, {})
        nhan, ok = {}, True
        for tc in TIEU_CHI:
            if tc == "y_sai" and k["dang"] != "tf":
                nhan[tc] = ""
                continue
            if a.get(tc) == b.get(tc):
                nhan[tc] = a.get(tc)
            elif (m, tc) in px:
                nhan[tc] = px[(m, tc)]
            else:
                ok = False
        if ok and nhan.get("dap_an"):
            nhan["ghi_chu"] = " / ".join(x for x in (a.get("ghi_chu"), b.get("ghi_chu")) if x)
            out[m] = nhan
        else:
            thieu.append(m)
    return out, thieu


def _co_loi(nhan, dinh_nghia):
    if nhan is None:
        return None
    loi = nhan.get("dap_an") not in (None, "Đúng")
    if dinh_nghia == "loi_bat_ky":
        loi = loi or nhan.get("vuot_lop") == "Có" or nhan.get("mo_ho") == "Có"
    return loi


def bang_co_nhan(khoa, nhan, dinh_nghia, n_tong_the=None, loc=None):
    """Bảng cờ × nhãn của câu hệ thống, tách theo dạng. loc(k) lọc dòng khóa (ví dụ chỉ câu bị bước Solver cờ)."""
    headers = ["Dạng", "n", "Có cờ: lỗi/tổng", "Không cờ: lỗi/tổng", "Precision [KTC 95%]", "Recall trong mẫu [KTC 95%]",
               "Recall ước lượng tổng thể", "Tỉ lệ lọt [KTC 95%]"]
    rows = []
    for dang in list(cau_mod.DANG) + ["tất cả"]:
        ks = [k for k in khoa if k["nguon"] == "he_thong" and k["ma"] in nhan
              and (dang == "tất cả" or k["dang"] == dang)]
        tp = sum(1 for k in ks if k["co_qc"] == "1" and (loc is None or loc(k)) and _co_loi(nhan[k["ma"]], dinh_nghia))
        fp = sum(1 for k in ks if k["co_qc"] == "1" and (loc is None or loc(k)) and not _co_loi(nhan[k["ma"]], dinh_nghia))
        fn = sum(1 for k in ks if not (k["co_qc"] == "1" and (loc is None or loc(k))) and _co_loi(nhan[k["ma"]], dinh_nghia))
        tn = len(ks) - tp - fp - fn
        p = tk.wilson(tp, tp + fp)
        r = tk.wilson(tp, tp + fn)
        lot = tk.wilson(fn, fn + tn)
        r_tt = None
        if n_tong_the and loc is None:
            n_co = sum(v for (d, co), v in n_tong_the.items() if co and (dang == "tất cả" or d == dang))
            n_khong = sum(v for (d, co), v in n_tong_the.items() if not co and (dang == "tất cả" or d == dang))
            r_tt = tk.recall_trong_so(p[0], lot[0], n_co, n_khong)
        rows.append([dang, len(ks), f"{tp}/{tp + fp}", f"{fn}/{fn + tn}", xb.phan_tram(*p), xb.phan_tram(*r),
                     xb.phan_tram(r_tt), xb.phan_tram(*lot)])
    return headers, rows


def bang_nhom(khoa, nhan, dinh_nghia, n_tong_the=None):
    """D4: tỉ lệ câu lỗi theo nhóm và theo dạng; Fisher + chênh lệch (Newcombe) giữa hệ thống và từng baseline."""
    def dem(pred):
        ks = [k for k in khoa if k["ma"] in nhan and pred(k)]
        return sum(1 for k in ks if _co_loi(nhan[k["ma"]], dinh_nghia)), len(ks)

    nhom = [("Hệ thống – không cờ (giáo viên nhận như câu sạch)", lambda k: k["nguon"] == "he_thong" and k["co_qc"] != "1"),
            ("Hệ thống – có cờ", lambda k: k["nguon"] == "he_thong" and k["co_qc"] == "1"),
            ("Hệ thống – cả mẫu (lệch do lấy 1/2 có cờ)", lambda k: k["nguon"] == "he_thong"),
            (TEN_NGUON["baseline_1"], lambda k: k["nguon"] == "baseline_1"),
            (TEN_NGUON["baseline_2"], lambda k: k["nguon"] == "baseline_2")]
    h1 = ["Nhóm"] + [f"{d}: lỗi/n" for d in cau_mod.DANG] + ["Tất cả: lỗi/n", "Tỉ lệ lỗi [KTC 95%]"]
    r1 = []
    for ten, pred in nhom:
        row = [ten]
        for d in cau_mod.DANG:
            k_, n_ = dem(lambda k, d=d, pred=pred: pred(k) and k["dang"] == d)
            row.append(f"{k_}/{n_}")
        k_, n_ = dem(pred)
        row += [f"{k_}/{n_}", xb.phan_tram(*tk.wilson(k_, n_))]
        r1.append(row)
    if n_tong_the:
        # tỉ lệ lỗi hệ thống ước lượng cho tổng thể: trộn hai tầng theo cỡ tầng thật
        kc, nc = dem(nhom[1][1])
        kk, nk = dem(nhom[0][1])
        n_co = sum(v for (_, co), v in n_tong_the.items() if co)
        n_khong = sum(v for (_, co), v in n_tong_the.items() if not co)
        if nc and nk and n_co + n_khong:
            p = (n_co * kc / nc + n_khong * kk / nk) / (n_co + n_khong)
            r1.append(["Hệ thống – ước lượng tổng thể (trọng số theo cỡ tầng)", "", "", "", "", xb.phan_tram(p)])
    h2 = ["So sánh", "Chênh lệch tỉ lệ lỗi [KTC 95% Newcombe]", "Odds ratio", "p (Fisher hai phía)"]
    r2 = []
    for ten_ht, pred_ht in (nhom[0], nhom[2]):
        for b in ("baseline_1", "baseline_2"):
            k1, n1 = dem(pred_ht)
            k2, n2 = dem(lambda k, b=b: k["nguon"] == b)
            if n1 == 0 or n2 == 0:
                continue
            d, lo, hi = tk.chenh_lech(k1, n1, k2, n2)
            odds, p = tk.fisher_exact(k1, n1 - k1, k2, n2 - k2)
            ct = f"{100 * d:.1f} điểm % [{100 * lo:.1f}; {100 * hi:.1f}]".replace(".", ",")
            r2.append([f"{ten_ht.split(' (')[0]} vs {TEN_NGUON[b]}", ct, xb.so(odds), xb.so(p, 4)])
    return (h1, r1), (h2, r2)


def bang_muc_do(khoa, nhan):
    h = ["Nhóm", "Đúng mức độ/n đã chấm", "Tỉ lệ [KTC 95%]", "Vượt lớp", "Mơ hồ"]
    rows = []
    for ten, n in TEN_NGUON.items():
        ks = [k for k in khoa if k["nguon"] == n and k["ma"] in nhan]
        dung = sum(1 for k in ks if nhan[k["ma"]].get("muc_do") == "Đúng")
        co_nhan = sum(1 for k in ks if nhan[k["ma"]].get("muc_do") in ("Đúng", "Không đúng"))
        vl = sum(1 for k in ks if nhan[k["ma"]].get("vuot_lop") == "Có")
        mh = sum(1 for k in ks if nhan[k["ma"]].get("mo_ho") == "Có")
        rows.append([ten, f"{dung}/{co_nhan}", xb.phan_tram(*tk.wilson(dung, co_nhan)), f"{vl}/{len(ks)}", f"{mh}/{len(ks)}"])
    return h, rows


def cau_lot(khoa, nhan):
    h = ["ma", "thu_tu", "id", "dang", "dap_an", "y_sai", "vuot_lop", "mo_ho", "ghi_chu", "loai_loi"]
    rows = []
    for k in sorted(khoa, key=lambda r: int(r["thu_tu"])):
        n = nhan.get(k["ma"])
        if k["nguon"] == "he_thong" and k["co_qc"] != "1" and n and _co_loi(n, "loi_bat_ky"):
            rows.append([k["ma"], k["thu_tu"], k["id"], k["dang"], n["dap_an"], n.get("y_sai", ""), n["vuot_lop"],
                         n["mo_ho"], n.get("ghi_chu", ""), ""])
    return h, rows


def tong_the_tu_xuat(path):
    """Cỡ tầng (dạng, có cờ) của toàn bộ câu hệ thống trong file xuất."""
    out = {}
    for row in cau_mod.doc_jsonl(path):
        c = cau_mod.tu_dong_xuat(row)
        if cau_mod.kiem_tra(c):
            continue
        key = (c["dang"], bool(c["qc"]["co"]))
        out[key] = out.get(key, 0) + 1
    return out


def bao_cao(khoa, p1, p2, px, out_dir, n_tong_the=None):
    os.makedirs(out_dir, exist_ok=True)
    nhan, thieu = nhan_cuoi(khoa, p1, p2, px)
    md = ["# Kết quả D2/D4 (tự sinh bởi do_luong.phan_tich_d2)", ""]
    if thieu:
        md += [f"**Cảnh báo:** {len(thieu)} câu chưa đủ nhãn (chưa phân xử hoặc bỏ trống), không tính: "
               + ", ".join(thieu), ""]

    def them(ten, tep, headers, rows, ghi_chu=""):
        md.extend([f"## {ten}", ""] + ([ghi_chu, ""] if ghi_chu else []) + [xb.markdown(headers, rows), ""])
        xb.ghi_csv(os.path.join(out_dir, tep), headers, rows)

    them("Bảng 1. Độ đồng thuận giữa hai người chấm", "bang1-kappa.csv", *kappa_bang(khoa, p1, p2),
         "Ý sai (Đ/S) chỉ so trên câu Đ/S cả hai cùng chấm Sai.")
    for i, dn in enumerate(DINH_NGHIA_LOI):
        ten_dn = "lỗi đáp án" if dn == "loi_dap_an" else "lỗi bất kỳ (đáp án, vượt lớp, mơ hồ)"
        them(f"Bảng 2{'ab'[i]}. Cờ QC × nhãn người chấm ({ten_dn})", f"bang2{'ab'[i]}-co-x-nhan-{dn}.csv",
             *bang_co_nhan(khoa, nhan, dn, n_tong_the))
    them("Bảng 3. Riêng bước Solver (lỗi đáp án)", "bang3-solver.csv",
         *bang_co_nhan(khoa, nhan, "loi_dap_an", None, loc=lambda k: "solver" in k["buoc"].split("|")),
         "Dương tính = câu có lý do thuộc bước Solver (solver_*, statement_undecidable).")
    (h1, r1), (h2, r2) = bang_nhom(khoa, nhan, "loi_bat_ky", n_tong_the)
    them("Bảng 4. D4: tỉ lệ câu lỗi theo nhóm (lỗi bất kỳ)", "bang4-nhom.csv", h1, r1)
    them("Bảng 5. D4: so sánh hệ thống với gọi thẳng Gemini", "bang5-so-sanh.csv", h2, r2,
         "So sánh chính: câu hệ thống KHÔNG cờ (giáo viên nhận như câu sạch) với câu baseline (không có QC). "
         "Dòng 'cả mẫu' chỉ để tham khảo vì mẫu hệ thống lấy 1/2 câu có cờ.")
    them("Bảng 6. Mức độ, vượt lớp, mơ hồ theo nhóm", "bang6-muc-do.csv", *bang_muc_do(khoa, nhan))
    h, rows = cau_lot(khoa, nhan)
    xb.ghi_csv(os.path.join(out_dir, "cau-lot.csv"), h, rows)
    md += [f"Danh sách {len(rows)} câu hệ thống lọt (không cờ nhưng có lỗi) ở cau-lot.csv, cột loai_loi để phân loại.", ""]
    with open(os.path.join(out_dir, "ket-qua-d2.md"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(md))
    return nhan, thieu


def main(argv=None):
    ap = argparse.ArgumentParser(description="Phân tích D2/D4 từ phiếu chấm mù.")
    sub = ap.add_subparsers(dest="lenh", required=True)
    for ten in ("phan-xu", "bao-cao"):
        s = sub.add_parser(ten)
        s.add_argument("--khoa", required=True)
        s.add_argument("--phieu", nargs=2, required=True, help="phiếu của hai người chấm")
        s.add_argument("--out-dir", required=True)
        if ten == "bao-cao":
            s.add_argument("--phan-xu", help="phan-xu.xlsx đã điền cột Kết luận")
            s.add_argument("--he-thong-xuat", help="JSONL xuất đầy đủ, để ước lượng recall tổng thể")
    a = ap.parse_args(argv)
    khoa = lay_mau.doc_khoa(a.khoa)
    p1, p2 = doc_phieu(a.phieu[0]), doc_phieu(a.phieu[1])
    if a.lenh == "phan-xu":
        os.makedirs(a.out_dir, exist_ok=True)
        ds = bat_dong(khoa, p1, p2)
        ghi_phan_xu(os.path.join(a.out_dir, "phan-xu.xlsx"), ds)
        h, rows = kappa_bang(khoa, p1, p2)
        print(xb.markdown(h, rows))
        print(f"{len(ds)} ô bất đồng -> {os.path.join(a.out_dir, 'phan-xu.xlsx')}")
    else:
        px = doc_phan_xu(a.phan_xu) if a.phan_xu else {}
        n_tt = tong_the_tu_xuat(a.he_thong_xuat) if a.he_thong_xuat else None
        nhan, thieu = bao_cao(khoa, p1, p2, px, a.out_dir, n_tt)
        print(f"Đã ghi báo cáo vào {a.out_dir}: {len(nhan)} câu có nhãn cuối, {len(thieu)} câu thiếu nhãn.")


if __name__ == "__main__":
    main()
