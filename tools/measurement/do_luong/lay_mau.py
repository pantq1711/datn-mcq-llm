"""Lấy mẫu có seed cố định cho bộ chấm mù D2+D4, gán mã ngẫu nhiên, ghi file khóa (thiết kế sau phản biện 15/11, L4, L10).

Câu hệ thống: CHỈ câu của các đề thực nghiệm (--de-thi-id), chia ba tầng theo cờ:
  co_chinh    có ít nhất một lý do chính (bước Solver / format / grounding; co_qc.co_chinh): lấy HẾT (tổng điều tra).
  khong_co    không có cờ nào: rút ngẫu nhiên n câu mỗi dạng.
  chi_co_phu  chỉ có cờ trùng (duplicate:) hoặc cờ hạ tầng (job chưa chạy/thất bại): KHÔNG vào phiếu, ghi riêng vào log.
Không ưu tiên câu nào trong tầng, nên trọng số theo cỡ tầng (log "tong_the") dùng được cho ước lượng tổng thể.
Câu baseline: CHỈ baseline_2 (baseline_1 không vào phiếu mù, dùng cho phần định tính D4); n câu mỗi dạng, chia đều theo
lượt gọi (de_baseline); lượt thiếu câu thì bù từ lượt khác và ghi nhật ký.

Kết quả lặp lại được: đầu vào được sắp theo id trước khi rút, mọi lần rút dùng random.Random(seed).

Chạy:
  python -m do_luong.lay_mau --he-thong cau-he-thong.jsonl --de-thi-id 1 2 3 4 --baseline-2 baseline_2.jsonl \
      --seed 20261115 --out-dir ../../data/private/do-luong
"""
import argparse
import csv
import json
import os
import random

from . import cau as cau_mod

MA_ALPHABET = "ACDEFHJKMNPRTUVWXY3479"  # bỏ ký tự dễ nhầm (0/O, 1/I/L, 2/Z, 5/S, 6/G, 8/B)
MA_DO_DAI = 4
TEN_FILE_KHOA = "khoa.csv"
COT_KHOA = ["ma", "thu_tu", "id", "nguon", "dang", "tang", "co_qc", "co_chinh", "ly_do", "ly_do_chinh", "buoc",
            "prompt_version", "ma_tran_mau", "da_hien_cho_gv", "de_thi_id", "de_baseline"]
TANG = ("co_chinh", "khong_co", "chi_co_phu")


def _sap(rows):
    return sorted(rows, key=lambda r: str(r["id"]))


def _rut(rng, rows, k):
    """Rút k câu (không lặp) ngẫu nhiên. Trả (đã rút, còn lại)."""
    pool = _sap(rows)
    rng.shuffle(pool)
    return pool[:k], pool[k:]


def tang_cua(cau):
    qc = cau.get("qc") or {}
    if qc.get("co_chinh"):
        return "co_chinh"
    return "chi_co_phu" if qc.get("co") else "khong_co"


def _lay_baseline(rng, rows, n_moi_dang, nhat_ky):
    """n câu mỗi dạng, chia đều theo lượt (de_baseline); phần dư cho các lượt đầu; lượt thiếu thì bù từ lượt khác."""
    chon = []
    luot = sorted({r["de_baseline"] for r in rows})
    for dang in cau_mod.DANG:
        cua_dang = [r for r in rows if r["dang"] == dang]
        if not luot:
            break
        con_lai, thieu = [], 0
        for j, d in enumerate(luot):
            can = n_moi_dang // len(luot) + (1 if j < n_moi_dang % len(luot) else 0)
            lay, con = _rut(rng, [r for r in cua_dang if r["de_baseline"] == d], can)
            if len(lay) < can:
                nhat_ky.append(f"baseline_2 {dang}/lượt {d}: cần {can}, chỉ có {len(lay)}")
                thieu += can - len(lay)
            chon += lay
            con_lai += con
        if thieu:
            bu, _ = _rut(rng, con_lai, thieu)
            chon += bu
            nhat_ky.append(f"baseline_2 {dang}: bù {len(bu)} câu từ lượt khác" + ("" if len(bu) == thieu
                                                                                  else f" (KHÔNG ĐỦ, thiếu {thieu})"))
    return [dict(r, tang="baseline_2") for r in chon]


def lay_mau_d2(he_thong, baseline_2, de_thi_ids, n_khong_co=12, n_baseline=12, seed=0):
    """Trả (câu đã chọn, nhật ký, tổng thể {(dạng, tầng): số câu của các đề thực nghiệm})."""
    if any(r.get("nguon") != "baseline_2" for r in baseline_2):
        raise ValueError("Phiếu mù chỉ nhận baseline_2 (baseline_1 dùng cho phần định tính D4)")
    rng = random.Random(seed)
    nhat_ky = []
    ids = {int(x) for x in de_thi_ids}
    ngoai = [r for r in he_thong if r.get("de_thi_id") not in ids]
    if ngoai:
        nhat_ky.append(f"bỏ {len(ngoai)} câu ngoài 4 đề thực nghiệm (đề {sorted({r.get('de_thi_id') for r in ngoai})})")
    ht = [r for r in he_thong if r.get("de_thi_id") in ids]
    tong_the = {}
    for r in ht:
        key = (r["dang"], tang_cua(r))
        tong_the[key] = tong_the.get(key, 0) + 1
    chon = []
    for dang in cau_mod.DANG if ht else ():
        chon += [dict(r, tang="co_chinh") for r in _sap(ht) if r["dang"] == dang and tang_cua(r) == "co_chinh"]
        khong = [r for r in ht if r["dang"] == dang and tang_cua(r) == "khong_co"]
        lay, _ = _rut(rng, khong, n_khong_co)
        if len(lay) < n_khong_co:
            nhat_ky.append(f"{dang}/khong_co: cần {n_khong_co}, chỉ có {len(lay)} (lấy hết)")
        chon += [dict(r, tang="khong_co") for r in lay]
    chon += _lay_baseline(rng, baseline_2, n_baseline, nhat_ky)
    return chon, nhat_ky, tong_the


def gan_ma(rows, seed=0):
    """Xáo trộn toàn bộ câu và gán mã ngẫu nhiên không trùng; trả danh sách (thu_tu, ma, cau) theo thứ tự phiếu."""
    rng = random.Random(f"ma-{seed}")
    rows = _sap(rows)
    rng.shuffle(rows)
    used, out = set(), []
    for i, r in enumerate(rows, 1):
        while True:
            ma = "".join(rng.choice(MA_ALPHABET) for _ in range(MA_DO_DAI))
            if ma not in used:
                used.add(ma)
                break
        out.append((i, ma, r))
    return out


def dong_khoa(thu_tu, ma, cau):
    qc = cau.get("qc") or {}
    he = cau["nguon"] == "he_thong"
    return {
        "ma": ma, "thu_tu": thu_tu, "id": cau["id"], "nguon": cau["nguon"], "dang": cau["dang"],
        "tang": cau.get("tang") or "",
        "co_qc": int(bool(qc.get("co"))) if he else "", "co_chinh": int(bool(qc.get("co_chinh"))) if he else "",
        "ly_do": "|".join(qc.get("ly_do") or []), "ly_do_chinh": "|".join(qc.get("ly_do_chinh") or []),
        "buoc": "|".join(qc.get("buoc") or []),
        "prompt_version": cau.get("prompt_version") or "", "ma_tran_mau": cau.get("ma_tran_mau") or "",
        "da_hien_cho_gv": "" if cau.get("da_hien_cho_gv") is None else int(bool(cau["da_hien_cho_gv"])),
        "de_thi_id": cau.get("de_thi_id") or "", "de_baseline": cau.get("de_baseline") or "",
    }


def ghi_khoa(path, danh_sach):
    with open(path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=COT_KHOA)
        w.writeheader()
        for thu_tu, ma, c in danh_sach:
            w.writerow(dong_khoa(thu_tu, ma, c))


def doc_khoa(path):
    with open(path, encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def main(argv=None):
    ap = argparse.ArgumentParser(description="Lấy mẫu bộ chấm mù D2+D4 và ghi file khóa.")
    ap.add_argument("--he-thong", required=True, help="JSONL xuất bằng sql/d2-xuat-cau.sql")
    ap.add_argument("--de-thi-id", type=int, nargs="+", required=True, help="id các đề thực nghiệm (L4)")
    ap.add_argument("--baseline-2", help="JSONL câu baseline_2 đã parse (baseline_parse)")
    ap.add_argument("--n-khong-co", type=int, default=12, help="số câu không cờ mỗi dạng")
    ap.add_argument("--n-baseline", type=int, default=12, help="số câu baseline_2 mỗi dạng")
    ap.add_argument("--seed", type=int, required=True)
    ap.add_argument("--out-dir", required=True)
    a = ap.parse_args(argv)

    he_thong, loi = [], []
    for row in cau_mod.doc_jsonl(a.he_thong):
        c = cau_mod.tu_dong_xuat(row)
        e = cau_mod.kiem_tra(c)
        if e:
            loi.append((c["id"], e))
        else:
            he_thong.append(c)
    b2 = [r for r in cau_mod.doc_jsonl(a.baseline_2) if not cau_mod.kiem_tra(r)] if a.baseline_2 else []
    chon, nhat_ky, tong_the = lay_mau_d2(he_thong, b2, a.de_thi_id, a.n_khong_co, a.n_baseline, a.seed)
    ds = gan_ma(chon, a.seed)
    os.makedirs(a.out_dir, exist_ok=True)
    ghi_khoa(os.path.join(a.out_dir, TEN_FILE_KHOA), ds)
    cau_mod.ghi_jsonl(os.path.join(a.out_dir, "bo-cham.jsonl"), [{"thu_tu": t, "ma": m, **c} for t, m, c in ds])
    ids = set(a.de_thi_id)
    dem = {}
    for _, _, c in ds:
        key = f"{c['nguon']}/{c['tang']}/{c['dang']}"
        dem[key] = dem.get(key, 0) + 1
    with open(os.path.join(a.out_dir, "lay-mau-log.json"), "w", encoding="utf-8") as f:
        json.dump({"seed": a.seed, "de_thi_id": sorted(ids), "so_cau": len(ds), "nhat_ky": nhat_ky,
                   "tong_the": {d: {t: tong_the[(d, t)] for t in TANG if tong_the.get((d, t))} for d in cau_mod.DANG},
                   "dem_trong_phieu": dict(sorted(dem.items())),
                   "chi_co_phu": [{"id": c["id"], "dang": c["dang"], "ly_do": c["qc"]["ly_do"]}
                                  for c in _sap(he_thong) if c.get("de_thi_id") in ids and tang_cua(c) == "chi_co_phu"],
                   "cau_he_thong_loi_cau_truc": loi}, f, ensure_ascii=False, indent=2)
    print(f"Đã chọn {len(ds)} câu. Nhật ký: {len(nhat_ky)} dòng. Ghi vào {a.out_dir}")


if __name__ == "__main__":
    main()
