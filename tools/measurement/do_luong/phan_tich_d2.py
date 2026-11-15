"""Phân tích D2 (QC so với nhãn người chấm) và D4 (hệ thống so với gọi thẳng Gemini) từ phiếu chấm mù hai pha.

Kế hoạch phân tích (định trước, commit trước khi mở khóa): docs/experiments/d2/ke-hoach-phan-tich.md.

Hai bước:
  1. phan-xu: đọc phiếu pha 2 (và pha 1 nếu có) của 2 người chấm, tính kappa, ghi phan-xu.xlsx gồm các ô bất đồng và các
     câu "tự giải ở pha 1 khác kết luận pha 2" (nghi neo vào đáp án in sẵn, L6a). Không có nguồn, cờ.
  2. bao-cao: đọc phiếu + phan-xu.xlsx đã điền + khoa.csv (+ lay-mau-log.json để có cỡ tầng), ra bảng Markdown/CSV.

Định nghĩa (khớp docs/experiments/d2/huong-dan-cham.md):
  loi_dap_an  = Đáp án (pha 2, sau phân xử) khác "Đúng"  -> định nghĩa CHÍNH (L8)
  loi_bat_ky  = loi_dap_an hoặc Vượt lớp = Có hoặc Mơ hồ = Có -> mô tả
Câu hệ thống: "dương tính" = CỜ CHÍNH (khoa.csv co_chinh = 1: lý do thuộc bước Solver/format/grounding). Cờ trùng và cờ
hạ tầng không vào số chính (L10); câu chỉ có các cờ đó không nằm trong phiếu (lay_mau, tầng chi_co_phu).
  precision = lỗi ∩ cờ chính / cờ chính; recall (trong mẫu) = lỗi ∩ cờ chính / lỗi; tỉ lệ lọt = lỗi ∩ không cờ / không cờ.
  Tầng cờ chính là tổng điều tra, tầng không cờ là mẫu ngẫu nhiên: recall và tỉ lệ lỗi tổng thể ước lượng bằng trọng số
  theo cỡ tầng (--lay-mau-log). Bootstrap phân tầng/theo cụm: xem kế hoạch phân tích.

Chạy (đứng ở tools/measurement):
  python -m do_luong.phan_tich_d2 phan-xu --khoa <dir>/khoa.csv --phieu <pha2-Quan.xlsx> <pha2-Dang.xlsx> \
      --pha1 <pha1-Quan.xlsx> <pha1-Dang.xlsx> --bo-cham <dir>/bo-cham.jsonl --out-dir <dir>
  python -m do_luong.phan_tich_d2 bao-cao --khoa <dir>/khoa.csv --phieu <...> <...> --phan-xu <dir>/phan-xu.xlsx \
      --pha1 <...> <...> --bo-cham <dir>/bo-cham.jsonl --lay-mau-log <dir>/lay-mau-log.json --out-dir <dir>/ket-qua-d2
"""
import argparse
import json
import os
import re
from fractions import Fraction

from . import cau as cau_mod
from . import co_qc
from . import lay_mau
from . import phieu as phieu_mod
from . import thong_ke as tk
from . import xuat_bang as xb

TIEU_CHI = ["dap_an", "y_sai", "muc_do", "vuot_lop", "mo_ho"]
TEN_TIEU_CHI = {"dap_an": "Đáp án", "y_sai": "Ý sai (Đ/S)", "muc_do": "Đúng mức độ", "vuot_lop": "Vượt lớp",
                "mo_ho": "Mơ hồ"}
HOP_LE = {"dap_an": set(phieu_mod.DAP_AN_MCQ), "muc_do": set(phieu_mod.MUC_DO), "vuot_lop": set(phieu_mod.CO_KHONG),
          "mo_ho": set(phieu_mod.CO_KHONG), "doan_nguon": set(phieu_mod.DOAN_NGUON)}
DINH_NGHIA_LOI = ("loi_dap_an", "loi_bat_ky")
TEN_NHOM = {"khong_co": "Hệ thống – không cờ (giáo viên nhận như câu sạch)",
            "co_chinh": "Hệ thống – cờ chính (Solver/format/grounding)",
            "baseline_2": "Baseline 2 (prompt tốt nhất, gọi thẳng)",
            "truoc_qc": "Hệ thống – trước QC (ước lượng, trọng số theo cỡ tầng)"}
NHOM_PHIEU = ("khong_co", "co_chinh", "baseline_2")
DOAN_DUNG = {"he_thong": "Công cụ của nhóm", "baseline_2": "AI gọi thẳng", "baseline_1": "AI gọi thẳng"}
LY_DO_BAT_DONG = "hai người khác nhau"
LY_DO_PHA1 = "tự giải ở pha 1 khác kết luận pha 2"


def chuan_y_sai(v):
    """"b, d" / "D,b" -> "b,d"; rỗng -> ""."""
    if v is None:
        return ""
    letters = sorted({ch for ch in str(v).lower() if ch in "abcd"})
    return ",".join(letters)


def _gia_tri(v):
    return None if v in (None, "") else str(v).strip()


def doc_phieu(path):
    """Phiếu pha 2 .xlsx -> {mã: {tiêu chí: giá trị}}. Giá trị ngoài danh sách -> ValueError (không bỏ im lặng)."""
    from openpyxl import load_workbook
    ws = load_workbook(path, read_only=True, data_only=True)["Phiếu chấm"]
    out = {}
    for row in ws.iter_rows(min_row=2, values_only=True):
        if not row or row[1] is None:
            continue
        ma = str(row[1]).strip()
        nhan = {"dap_an": row[3], "y_sai": chuan_y_sai(row[4]), "muc_do": row[5], "vuot_lop": row[6],
                "mo_ho": row[7], "ghi_chu": row[8] or "", "doan_nguon": row[9] if len(row) > 9 else None}
        for k, ok in HOP_LE.items():
            v = nhan[k]
            nhan[k] = _gia_tri(v)
            if nhan[k] is not None and nhan[k] not in ok:
                raise ValueError(f"{os.path.basename(path)}: mã {ma}, cột {TEN_TIEU_CHI.get(k, k)}: giá trị lạ '{v}'")
        out[ma] = nhan
    return out


def doc_pha1(path):
    """Phiếu pha 1 .xlsx -> {mã: {"mcq", "tf": {a..d}, "sa", "ghi_chu"}}. Giá trị ngoài danh sách -> ValueError."""
    from openpyxl import load_workbook
    ws = load_workbook(path, read_only=True, data_only=True)[phieu_mod.SHEET_PHA1]
    out = {}
    for row in ws.iter_rows(min_row=2, values_only=True):
        if not row or row[1] is None:
            continue
        ma = str(row[1]).strip()
        tl = {"mcq": _gia_tri(row[3]), "tf": {k: _gia_tri(row[4 + i]) for i, k in enumerate("abcd")},
              "sa": _gia_tri(row[8]), "ghi_chu": row[9] or ""}
        if tl["mcq"] is not None and tl["mcq"] not in phieu_mod.TU_GIAI_MCQ:
            raise ValueError(f"{os.path.basename(path)}: mã {ma}, pha 1 trắc nghiệm: giá trị lạ '{tl['mcq']}'")
        for k, v in tl["tf"].items():
            if v is not None and v not in phieu_mod.TU_GIAI_TF:
                raise ValueError(f"{os.path.basename(path)}: mã {ma}, pha 1 ý {k}: giá trị lạ '{v}'")
        out[ma] = tl
    return out


def _so_sa(v):
    """Đáp số sa về dạng so được: Fraction nếu là số (0,5 = 1/2 = 0.5; − = -), không thì chuỗi bỏ khoảng trắng và $."""
    s = str(v).strip().strip("$").replace("−", "-").replace(" ", "").replace("{,}", ",")
    t = s.replace(",", ".")
    try:
        if re.fullmatch(r"-?\d+/\d+", t):
            return Fraction(t)
        if re.fullmatch(r"-?\d+(\.\d+)?", t):
            return Fraction(t)
    except (ValueError, ZeroDivisionError):
        pass
    return s


def so_khop(dang, tra_loi, dap_an_khoa):
    """Pha 1 của một người so với khóa: True/False; None nếu người chấm bỏ trống (không tính)."""
    if dang == "mcq":
        v = tra_loi.get("mcq")
        return None if v is None else v == dap_an_khoa
    if dang == "tf":
        t = tra_loi.get("tf") or {}
        if any(t.get(k) is None for k in "abcd"):
            return None
        return all(t[k] == (dap_an_khoa or {}).get(k) for k in "abcd")
    v = tra_loi.get("sa")
    return None if v is None else _so_sa(v) == _so_sa(dap_an_khoa)


def can_xem_lai(khoa, bo_cham, pha1_list, phieu_list):
    """Mã câu mà ít nhất một người có pha 1 không nhất quán với pha 2: tự giải khác khóa nhưng chấm Đáp án "Đúng" (nghi
    neo), hoặc tự giải trùng khóa nhưng chấm khác "Đúng"."""
    out = []
    for k in sorted(khoa, key=lambda r: int(r["thu_tu"])):
        m, c = k["ma"], bo_cham.get(k["ma"])
        if c is None:
            continue
        for pha1, p in zip(pha1_list, phieu_list):
            tl, d = pha1.get(m), p.get(m, {}).get("dap_an")
            sk = so_khop(c["dang"], tl, c.get("dap_an")) if tl else None
            if sk is not None and d is not None and sk != (d == "Đúng"):
                out.append(m)
                break
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


def bat_dong(khoa, p1, p2, xem_lai=()):
    """Danh sách (thứ tự, mã, tiêu chí, nhãn 1, nhãn 2, lý do xem) cần phân xử: ô hai người khác nhau, và ô Đáp án của câu
    trong xem_lai (pha 1 không nhất quán với pha 2) kể cả khi hai người đồng ý."""
    out, xem_lai = [], set(xem_lai)
    for k in sorted(khoa, key=lambda r: int(r["thu_tu"])):
        m = k["ma"]
        a, b = p1.get(m, {}), p2.get(m, {})
        for tc in TIEU_CHI:
            if tc == "y_sai" and k["dang"] != "tf":
                continue
            khac = a.get(tc) != b.get(tc)
            neo = tc == "dap_an" and m in xem_lai
            if khac or neo:
                ly_do = "; ".join(x for x, ok in ((LY_DO_BAT_DONG, khac), (LY_DO_PHA1, neo)) if ok)
                out.append((int(k["thu_tu"]), m, tc, a.get(tc) or "", b.get(tc) or "", ly_do))
    return out


def ghi_phan_xu(path, ds):
    from openpyxl import Workbook
    from openpyxl.worksheet.datavalidation import DataValidation
    wb = Workbook()
    ws = wb.active
    ws.title = "Phân xử"
    ws.append(["Thứ tự", "Mã", "Tiêu chí", "Người chấm 1", "Người chấm 2", "Kết luận", "Ghi chú", "Lý do xem"])
    lists = {"dap_an": phieu_mod.DAP_AN_MCQ, "muc_do": phieu_mod.MUC_DO, "vuot_lop": phieu_mod.CO_KHONG,
             "mo_ho": phieu_mod.CO_KHONG}
    dvs = {}
    for i, (tt, m, tc, a, b, ly_do) in enumerate(ds, start=2):
        ws.append([tt, m, TEN_TIEU_CHI[tc], a, b, None, None, ly_do])
        if tc in lists:
            if tc not in dvs:
                dvs[tc] = DataValidation(type="list", formula1='"' + ",".join(lists[tc]) + '"', allow_blank=True)
                ws.add_data_validation(dvs[tc])
            dvs[tc].add(f"F{i}")
    for col, w in {"A": 8, "B": 8, "C": 16, "D": 22, "E": 22, "F": 22, "G": 50, "H": 34}.items():
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
    """Nhãn sau phân xử: ô có kết luận phân xử thì lấy kết luận (kể cả ô hai người đồng ý nhưng được đưa ra xem lại vì
    pha 1); không thì hai người đồng ý mới lấy. Trả (nhãn, mã còn thiếu)."""
    out, thieu = {}, []
    for k in khoa:
        m = k["ma"]
        a, b = p1.get(m, {}), p2.get(m, {})
        nhan, ok = {}, True
        for tc in TIEU_CHI:
            if tc == "y_sai" and k["dang"] != "tf":
                nhan[tc] = ""
                continue
            if (m, tc) in px:
                nhan[tc] = px[(m, tc)]
            elif a.get(tc) == b.get(tc):
                nhan[tc] = a.get(tc)
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


def _duong(k):
    """Dương tính của D2 = cờ chính (L10)."""
    return k.get("co_chinh") == "1"


def la_co_solver(k):
    """Có lý do chính thuộc bước Solver (solver_* trừ cờ hạ tầng, statement_undecidable)."""
    return any(co_qc.buoc_cua(r) == "solver" for r in (k.get("ly_do_chinh") or "").split("|") if r)


def bang_co_nhan(khoa, nhan, dinh_nghia, n_tong_the=None, loc=None):
    """Bảng cờ chính × nhãn của câu hệ thống, tách theo dạng. loc(k) thu hẹp dương tính (ví dụ chỉ cờ của bước Solver)."""
    headers = ["Dạng", "n", "Cờ chính: lỗi/tổng", "Không cờ: lỗi/tổng", "Precision [KTC 95%]", "Recall trong mẫu [KTC 95%]",
               "Recall ước lượng tổng thể", "Tỉ lệ lọt [KTC 95%]"]
    rows = []
    for dang in list(cau_mod.DANG) + ["tất cả"]:
        ks = [k for k in khoa if k["nguon"] == "he_thong" and k["ma"] in nhan
              and (dang == "tất cả" or k["dang"] == dang)]
        duong = [k for k in ks if _duong(k) and (loc is None or loc(k))]
        am = [k for k in ks if k not in duong]
        tp = sum(1 for k in duong if _co_loi(nhan[k["ma"]], dinh_nghia))
        fp = len(duong) - tp
        fn = sum(1 for k in am if _co_loi(nhan[k["ma"]], dinh_nghia))
        tn = len(am) - fn
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


def _nhom_cua(k):
    return k.get("tang") or ("baseline_2" if k["nguon"] == "baseline_2" else "")


def bang_nhom(khoa, nhan, dinh_nghia, n_tong_the=None):
    """D4: tỉ lệ câu lỗi theo nhóm và dạng; so sánh CHÍNH duy nhất có kiểm định: hệ thống không cờ vs baseline 2 (L8).
    Có n_tong_the thì thêm dòng "trước QC" (L9 i): trộn tầng cờ chính và không cờ theo cỡ tầng (dạng × tầng), chỉ mô tả."""
    def dem(pred):
        ks = [k for k in khoa if k["ma"] in nhan and pred(k)]
        return sum(1 for k in ks if _co_loi(nhan[k["ma"]], dinh_nghia)), len(ks)

    h1 = ["Nhóm"] + [f"{d}: lỗi/n" for d in cau_mod.DANG] + ["Tất cả: lỗi/n", "Tỉ lệ lỗi [KTC 95%]"]
    r1 = []
    for g in NHOM_PHIEU:
        if not any(_nhom_cua(k) == g for k in khoa):
            continue
        row = [TEN_NHOM[g]]
        for d in cau_mod.DANG:
            k_, n_ = dem(lambda k, d=d, g=g: _nhom_cua(k) == g and k["dang"] == d)
            row.append(f"{k_}/{n_}")
        k_, n_ = dem(lambda k, g=g: _nhom_cua(k) == g)
        row += [f"{k_}/{n_}", xb.phan_tram(*tk.wilson(k_, n_))]
        r1.append(row)
    if n_tong_the:
        tu, mau = 0.0, 0
        for (d, co), n_tang in n_tong_the.items():
            g = "co_chinh" if co else "khong_co"
            k_, n_ = dem(lambda k, d=d, g=g: _nhom_cua(k) == g and k["dang"] == d)
            if n_ and n_tang:
                tu += n_tang * k_ / n_
                mau += n_tang
        if mau:
            r1.append([TEN_NHOM["truoc_qc"], "", "", "", "", xb.phan_tram(tu / mau)])
    h2 = ["So sánh chính", "Chênh lệch tỉ lệ lỗi [KTC 95% Newcombe]", "Odds ratio", "p (Fisher hai phía)"]
    r2 = []
    k1, n1 = dem(lambda k: _nhom_cua(k) == "khong_co")
    k2, n2 = dem(lambda k: _nhom_cua(k) == "baseline_2")
    if n1 and n2:
        d, lo, hi = tk.chenh_lech(k1, n1, k2, n2)
        odds, p = tk.fisher_exact(k1, n1 - k1, k2, n2 - k2)
        ct = f"{100 * d:.1f} điểm % [{100 * lo:.1f}; {100 * hi:.1f}]".replace(".", ",")
        r2.append([f"Hệ thống – không cờ vs {TEN_NHOM['baseline_2']}", ct, xb.so(odds), xb.so(p, 4)])
    return (h1, r1), (h2, r2)


def bang_muc_do(khoa, nhan):
    """Mức độ / vượt lớp / mơ hồ theo nhóm; câu hệ thống TÁCH tầng (L8e), không gộp không trọng số."""
    h = ["Nhóm", "Đúng mức độ/n đã chấm", "Tỉ lệ [KTC 95%]", "Không rõ", "Vượt lớp", "Mơ hồ"]
    rows = []
    for g in NHOM_PHIEU:
        ks = [k for k in khoa if _nhom_cua(k) == g and k["ma"] in nhan]
        if not ks:
            continue
        dung = sum(1 for k in ks if nhan[k["ma"]].get("muc_do") == "Đúng")
        co_nhan = sum(1 for k in ks if nhan[k["ma"]].get("muc_do") in ("Đúng", "Không đúng"))
        khong_ro = sum(1 for k in ks if nhan[k["ma"]].get("muc_do") == "Không rõ")
        vl = sum(1 for k in ks if nhan[k["ma"]].get("vuot_lop") == "Có")
        mh = sum(1 for k in ks if nhan[k["ma"]].get("mo_ho") == "Có")
        rows.append([TEN_NHOM[g], f"{dung}/{co_nhan}", xb.phan_tram(*tk.wilson(dung, co_nhan)), khong_ro,
                     f"{vl}/{len(ks)}", f"{mh}/{len(ks)}"])
    return h, rows


def bang_pha1(khoa, bo_cham, pha1_list):
    """L6a: tỉ lệ câu người chấm tự giải ra đúng khóa (pha 1), theo người và nhóm. Bỏ trống không tính."""
    h = ["Người chấm", "Nhóm", "Tự giải khớp khóa", "Tỉ lệ [KTC 95%]", "Bỏ trống"]
    rows = []
    for i, pha1 in enumerate(pha1_list, 1):
        for g in NHOM_PHIEU:
            ks = [k for k in khoa if _nhom_cua(k) == g and k["ma"] in bo_cham]
            if not ks:
                continue
            kq = [so_khop(bo_cham[k["ma"]]["dang"], pha1.get(k["ma"]) or {}, bo_cham[k["ma"]].get("dap_an")) for k in ks]
            khop, n = sum(1 for x in kq if x), sum(1 for x in kq if x is not None)
            rows.append([f"Người chấm {i}", TEN_NHOM[g], f"{khop}/{n}", xb.phan_tram(*tk.wilson(khop, n)),
                         sum(1 for x in kq if x is None)])
    return h, rows


def bang_doan_nguon(khoa, phieu_list):
    """L6b: tỉ lệ đoán đúng nguồn (trên các câu người chấm có đoán) và số câu "Không đoán được"."""
    h = ["Người chấm", "Số câu có đoán", "Đoán đúng", "Tỉ lệ đúng", "KTC 95%", "Không đoán được", "Bỏ trống"]
    nguon = {k["ma"]: k["nguon"] for k in khoa}
    rows = []
    for i, p in enumerate(phieu_list, 1):
        doan = [(m, p.get(m, {}).get("doan_nguon")) for m in nguon]
        co_doan = [(m, v) for m, v in doan if v not in (None, "Không đoán được")]
        dung = sum(1 for m, v in co_doan if DOAN_DUNG.get(nguon[m]) == v)
        w = tk.wilson(dung, len(co_doan))
        rows.append([f"Người chấm {i}", len(co_doan), dung, xb.phan_tram(w[0]), xb.phan_tram(*w),
                     sum(1 for _, v in doan if v == "Không đoán được"), sum(1 for _, v in doan if v is None)])
    return h, rows


def cau_lot(khoa, nhan):
    h = ["ma", "thu_tu", "id", "dang", "dap_an", "y_sai", "vuot_lop", "mo_ho", "ghi_chu", "loai_loi"]
    rows = []
    for k in sorted(khoa, key=lambda r: int(r["thu_tu"])):
        n = nhan.get(k["ma"])
        if k["nguon"] == "he_thong" and not _duong(k) and n and _co_loi(n, "loi_bat_ky"):
            rows.append([k["ma"], k["thu_tu"], k["id"], k["dang"], n["dap_an"], n.get("y_sai", ""), n["vuot_lop"],
                         n["mo_ho"], n.get("ghi_chu", ""), ""])
    return h, rows


def tong_the_tu_log(path):
    """Cỡ tầng {(dạng, có cờ chính): số câu} của các đề thực nghiệm, đọc từ lay-mau-log.json (cùng tập đã lấy mẫu, L4).
    Tầng chi_co_phu (chỉ cờ trùng/hạ tầng) không có trong phiếu nên không vào ước lượng."""
    with open(path, encoding="utf-8") as f:
        tt = json.load(f)["tong_the"]
    out = {}
    for dang, tang in tt.items():
        for t, n in tang.items():
            if t in ("co_chinh", "khong_co"):
                out[(dang, t == "co_chinh")] = n
    return out


def bao_cao(khoa, p1, p2, px, out_dir, n_tong_the=None, pha1=None, bo_cham=None):
    os.makedirs(out_dir, exist_ok=True)
    nhan, thieu = nhan_cuoi(khoa, p1, p2, px)
    md = ["# Kết quả D2/D4 (tự sinh bởi do_luong.phan_tich_d2)", "",
          "Theo kế hoạch định trước: docs/experiments/d2/ke-hoach-phan-tich.md. Dương tính = cờ chính (L10).", ""]
    if thieu:
        md += [f"**Cảnh báo:** {len(thieu)} câu chưa đủ nhãn (chưa phân xử hoặc bỏ trống), không tính: "
               + ", ".join(thieu), ""]

    def them(ten, tep, headers, rows, ghi_chu=""):
        md.extend([f"## {ten}", ""] + ([ghi_chu, ""] if ghi_chu else []) + [xb.markdown(headers, rows), ""])
        xb.ghi_csv(os.path.join(out_dir, tep), headers, rows)

    them("Bảng 1. Độ đồng thuận giữa hai người chấm (pha 2)", "bang1-kappa.csv", *kappa_bang(khoa, p1, p2),
         "Ý sai (Đ/S) chỉ so trên câu Đ/S cả hai cùng chấm Sai.")
    for i, dn in enumerate(DINH_NGHIA_LOI):
        ten_dn = "lỗi đáp án – CHÍNH" if dn == "loi_dap_an" else "lỗi bất kỳ (đáp án, vượt lớp, mơ hồ) – mô tả"
        them(f"Bảng 2{'ab'[i]}. Cờ chính × nhãn người chấm ({ten_dn})", f"bang2{'ab'[i]}-co-x-nhan-{dn}.csv",
             *bang_co_nhan(khoa, nhan, dn, n_tong_the))
    them("Bảng 3. Riêng bước Solver (lỗi đáp án)", "bang3-solver.csv",
         *bang_co_nhan(khoa, nhan, "loi_dap_an", None, loc=la_co_solver),
         "Dương tính = câu có lý do chính thuộc bước Solver (solver_* trừ cờ hạ tầng, statement_undecidable).")
    (h1, r1), (h2, r2) = bang_nhom(khoa, nhan, "loi_dap_an", n_tong_the)
    them("Bảng 4. D4: tỉ lệ câu lỗi đáp án theo nhóm (CHÍNH)", "bang4-nhom.csv", h1, r1)
    them("Bảng 5. D4: so sánh chính (lỗi đáp án)", "bang5-so-sanh.csv", h2, r2,
         "So sánh chính duy nhất có kiểm định: câu hệ thống KHÔNG cờ (giáo viên nhận như câu sạch) với baseline 2. "
         "Các dòng khác ở Bảng 4/4b chỉ mô tả.")
    (h1b, r1b), _ = bang_nhom(khoa, nhan, "loi_bat_ky", n_tong_the)
    them("Bảng 4b. D4: tỉ lệ câu lỗi bất kỳ theo nhóm (mô tả)", "bang4b-nhom-loi-bat-ky.csv", h1b, r1b)
    them("Bảng 6. Mức độ, vượt lớp, mơ hồ theo nhóm", "bang6-muc-do.csv", *bang_muc_do(khoa, nhan),
         "Câu hệ thống tách theo tầng; 'Không rõ' không vào mẫu số và được báo riêng.")
    if pha1 and bo_cham:
        them("Bảng 7. Pha 1: người chấm tự giải khớp khóa", "bang7-pha1.csv", *bang_pha1(khoa, bo_cham, pha1))
    them("Bảng 8. Đoán nguồn (cột cuối pha 2)", "bang8-doan-nguon.csv", *bang_doan_nguon(khoa, [p1, p2]),
         "Tỉ lệ đúng tính trên các câu có đoán; tỉ lệ gần 50% nghĩa là không phân biệt được nguồn.")
    h, rows = cau_lot(khoa, nhan)
    xb.ghi_csv(os.path.join(out_dir, "cau-lot.csv"), h, rows)
    md += [f"Danh sách {len(rows)} câu hệ thống lọt (không cờ nhưng có lỗi) ở cau-lot.csv, cột loai_loi để phân loại.", ""]
    with open(os.path.join(out_dir, "ket-qua-d2.md"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(md))
    return nhan, thieu


def main(argv=None):
    ap = argparse.ArgumentParser(description="Phân tích D2/D4 từ phiếu chấm mù hai pha.")
    sub = ap.add_subparsers(dest="lenh", required=True)
    for ten in ("phan-xu", "bao-cao"):
        s = sub.add_parser(ten)
        s.add_argument("--khoa", required=True)
        s.add_argument("--phieu", nargs=2, required=True, help="phiếu PHA 2 của hai người chấm")
        s.add_argument("--pha1", nargs=2, help="phiếu PHA 1 của hai người chấm (cùng thứ tự với --phieu)")
        s.add_argument("--bo-cham", help="bo-cham.jsonl (khóa đáp án để so pha 1); bắt buộc khi có --pha1")
        s.add_argument("--out-dir", required=True)
        if ten == "bao-cao":
            s.add_argument("--phan-xu", help="phan-xu.xlsx đã điền cột Kết luận")
            s.add_argument("--lay-mau-log", help="lay-mau-log.json (cỡ tầng) để ước lượng tổng thể")
    a = ap.parse_args(argv)
    if a.pha1 and not a.bo_cham:
        ap.error("--pha1 cần --bo-cham")
    khoa = lay_mau.doc_khoa(a.khoa)
    p1, p2 = doc_phieu(a.phieu[0]), doc_phieu(a.phieu[1])
    pha1 = [doc_pha1(p) for p in a.pha1] if a.pha1 else None
    bo = {r["ma"]: r for r in cau_mod.doc_jsonl(a.bo_cham)} if a.bo_cham else None
    if a.lenh == "phan-xu":
        os.makedirs(a.out_dir, exist_ok=True)
        xem = can_xem_lai(khoa, bo, pha1, [p1, p2]) if pha1 else []
        ds = bat_dong(khoa, p1, p2, xem)
        ghi_phan_xu(os.path.join(a.out_dir, "phan-xu.xlsx"), ds)
        h, rows = kappa_bang(khoa, p1, p2)
        print(xb.markdown(h, rows))
        print(f"{len(ds)} ô cần phân xử ({len(xem)} câu do pha 1 không nhất quán) -> "
              f"{os.path.join(a.out_dir, 'phan-xu.xlsx')}")
    else:
        px = doc_phan_xu(a.phan_xu) if a.phan_xu else {}
        n_tt = tong_the_tu_log(a.lay_mau_log) if a.lay_mau_log else None
        nhan, thieu = bao_cao(khoa, p1, p2, px, a.out_dir, n_tt, pha1, bo)
        print(f"Đã ghi báo cáo vào {a.out_dir}: {len(nhan)} câu có nhãn cuối, {len(thieu)} câu thiếu nhãn.")


if __name__ == "__main__":
    main()
