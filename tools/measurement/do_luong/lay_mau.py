"""Lấy mẫu phân tầng có seed cố định cho bộ chấm mù D2+D4, gán mã ngẫu nhiên, ghi file khóa.

Câu hệ thống: tầng (dạng × có/không cờ), mỗi dạng n/3 câu chia đôi có cờ / không cờ. Trong mỗi tầng ưu tiên câu của ma trận
mẫu (mặc định giuaki1-toan8) rồi mới đến câu khác. Tầng thiếu câu thì lấy bù từ tầng cùng dạng còn lại, rồi từ dạng khác,
và ghi lại trong nhật ký (để báo cáo đúng số thực tế, không bỏ im lặng).
Câu baseline: mỗi nguồn lấy n câu chia theo tỉ lệ dạng trong đầu ra (phần dư lớn nhất), tối thiểu 1 câu mỗi dạng nếu có.

Kết quả lặp lại được: đầu vào được sắp theo id trước khi rút, mọi lần rút dùng random.Random(seed).

Chạy:
  python -m do_luong.lay_mau --he-thong cau-he-thong.jsonl --baseline b1.jsonl b2.jsonl \
      --seed 20261113 --out-dir ../../data/private/do-luong
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
COT_KHOA = ["ma", "thu_tu", "id", "nguon", "dang", "co_qc", "ly_do", "buoc", "prompt_version", "ma_tran_mau",
            "da_hien_cho_gv", "de_thi_id"]


def _sap(rows):
    return sorted(rows, key=lambda r: str(r["id"]))


def _rut(rng, rows, k, uu_tien=None):
    """Rút k câu (không lặp); câu thỏa uu_tien được rút trước. Trả (đã rút, còn lại)."""
    rows = _sap(rows)
    first = [r for r in rows if uu_tien and uu_tien(r)]
    rest = [r for r in rows if not (uu_tien and uu_tien(r))]
    rng.shuffle(first)
    rng.shuffle(rest)
    pool = first + rest
    return pool[:k], pool[k:]


def lay_mau_he_thong(rows, n=60, seed=0, ma_tran_mau="giuaki1-toan8", chi_da_hien=False):
    """Trả (câu đã chọn, nhật ký). rows: câu thống nhất nguồn he_thong."""
    rng = random.Random(seed)
    nhat_ky = []
    if chi_da_hien:
        rows = [r for r in rows if r.get("da_hien_cho_gv")]
    per_dang = {d: n // len(cau_mod.DANG) for d in cau_mod.DANG}
    for d in cau_mod.DANG[: n % len(cau_mod.DANG)]:
        per_dang[d] += 1
    uu_tien = (lambda r: r.get("ma_tran_mau") == ma_tran_mau) if ma_tran_mau else None
    chon, con_lai, thieu_tong = [], [], 0
    for d in cau_mod.DANG:
        k = per_dang[d]
        can_co, can_khong = k - k // 2, k // 2
        lay_co, con_co = _rut(rng, [r for r in rows if r["dang"] == d and r["qc"]["co"]], can_co, uu_tien)
        lay_khong, con_khong = _rut(rng, [r for r in rows if r["dang"] == d and not r["qc"]["co"]], can_khong, uu_tien)
        chon_d = lay_co + lay_khong
        if len(lay_co) < can_co:
            nhat_ky.append(f"{d}/có cờ: cần {can_co}, chỉ có {len(lay_co)}")
            bu, con_khong = _rut(rng, con_khong, can_co - len(lay_co), uu_tien)
            chon_d += bu
            nhat_ky.append(f"{d}: bù {len(bu)} câu không cờ" if bu else f"{d}: không còn câu không cờ để bù")
        if len(lay_khong) < can_khong:
            nhat_ky.append(f"{d}/không cờ: cần {can_khong}, chỉ có {len(lay_khong)}")
            bu, con_co = _rut(rng, con_co, can_khong - len(lay_khong), uu_tien)
            chon_d += bu
            nhat_ky.append(f"{d}: bù {len(bu)} câu có cờ" if bu else f"{d}: không còn câu có cờ để bù")
        chon += chon_d
        thieu_tong += k - len(chon_d)
        con_lai += con_co + con_khong
    if thieu_tong > 0:
        bu, _ = _rut(rng, con_lai, thieu_tong, uu_tien)
        chon += bu
        nhat_ky.append(f"bù {len(bu)} câu từ dạng khác (thiếu {thieu_tong})")
        if len(bu) < thieu_tong:
            nhat_ky.append(f"KHÔNG ĐỦ CÂU: chỉ chọn được {len(chon)}/{n}")
    return chon, nhat_ky


def chia_ti_le(dem, n):
    """Chia n theo tỉ lệ dem (dict dạng -> số câu có), phần dư lớn nhất, mỗi dạng có câu được ít nhất 1, không vượt số có."""
    tong = sum(dem.values())
    if tong == 0:
        return {d: 0 for d in dem}
    n = min(n, tong)
    co_cau = [d for d in sorted(dem) if dem[d] > 0]
    phan = {d: (1 if dem[d] > 0 and n >= len(co_cau) else 0) for d in dem}
    con_lai = n - sum(phan.values())
    ty = {d: dem[d] * con_lai / tong for d in dem}
    for d in dem:
        phan[d] += min(int(ty[d]), dem[d] - phan[d])
    while sum(phan.values()) < n:
        ung = [d for d in sorted(dem) if phan[d] < dem[d]]
        d = max(ung, key=lambda x: (ty[x] - int(ty[x]), dem[x], x))
        phan[d] += 1
        ty[d] = int(ty[d])  # đã dùng phần dư
    return phan


def lay_mau_baseline(rows, n=20, seed=0):
    """rows: câu thống nhất của MỘT nguồn baseline (đã parse). Trả (câu đã chọn, nhật ký)."""
    rng = random.Random(seed)
    dem = {d: sum(1 for r in rows if r["dang"] == d) for d in cau_mod.DANG}
    phan = chia_ti_le(dem, n)
    chon, nhat_ky = [], []
    for d in cau_mod.DANG:
        lay, _ = _rut(rng, [r for r in rows if r["dang"] == d], phan[d])
        chon.extend(lay)
    if len(chon) < n:
        nhat_ky.append(f"{rows[0]['nguon'] if rows else '?'}: chỉ có {len(chon)}/{n} câu hợp lệ")
    return chon, nhat_ky


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
    return {
        "ma": ma, "thu_tu": thu_tu, "id": cau["id"], "nguon": cau["nguon"], "dang": cau["dang"],
        "co_qc": "" if cau["nguon"] != "he_thong" else int(bool(qc.get("co"))),
        "ly_do": "|".join(qc.get("ly_do") or []), "buoc": "|".join(qc.get("buoc") or []),
        "prompt_version": cau.get("prompt_version") or "", "ma_tran_mau": cau.get("ma_tran_mau") or "",
        "da_hien_cho_gv": "" if cau.get("da_hien_cho_gv") is None else int(bool(cau["da_hien_cho_gv"])),
        "de_thi_id": cau.get("de_thi_id") or "",
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
    ap.add_argument("--baseline", nargs="*", default=[], help="JSONL câu baseline đã parse (baseline_parse)")
    ap.add_argument("--n-he-thong", type=int, default=60)
    ap.add_argument("--n-baseline", type=int, default=20, help="số câu mỗi nguồn baseline")
    ap.add_argument("--seed", type=int, required=True)
    ap.add_argument("--ma-tran-mau", default="giuaki1-toan8", help="ưu tiên câu của ma trận mẫu này ('' = không ưu tiên)")
    ap.add_argument("--chi-da-hien", action="store_true", help="chỉ lấy câu từng hiện cho giáo viên")
    ap.add_argument("--out-dir", required=True)
    a = ap.parse_args(argv)

    he_thong = []
    loi = []
    for row in cau_mod.doc_jsonl(a.he_thong):
        c = cau_mod.tu_dong_xuat(row)
        e = cau_mod.kiem_tra(c)
        if e:
            loi.append((c["id"], e))
        else:
            he_thong.append(c)
    chon, nhat_ky = lay_mau_he_thong(he_thong, a.n_he_thong, a.seed, a.ma_tran_mau or None, a.chi_da_hien)
    for path in a.baseline:
        rows = [r for r in cau_mod.doc_jsonl(path) if not cau_mod.kiem_tra(r)]
        lay, nk = lay_mau_baseline(rows, a.n_baseline, a.seed)
        chon.extend(lay)
        nhat_ky.extend(nk)
    ds = gan_ma(chon, a.seed)
    os.makedirs(a.out_dir, exist_ok=True)
    ghi_khoa(os.path.join(a.out_dir, TEN_FILE_KHOA), ds)
    cau_mod.ghi_jsonl(os.path.join(a.out_dir, "bo-cham.jsonl"),
                      [{"thu_tu": t, "ma": m, **c} for t, m, c in ds])
    with open(os.path.join(a.out_dir, "lay-mau-log.json"), "w", encoding="utf-8") as f:
        json.dump({"seed": a.seed, "so_cau": len(ds), "nhat_ky": nhat_ky,
                   "cau_he_thong_loi_cau_truc": loi,
                   "dem": {f"{n}/{d}": sum(1 for _, _, c in ds if c["nguon"] == n and c["dang"] == d)
                           for n in cau_mod.NGUON for d in cau_mod.DANG}}, f, ensure_ascii=False, indent=2)
    print(f"Đã chọn {len(ds)} câu. Nhật ký: {len(nhat_ky)} dòng. Ghi vào {a.out_dir}")


if __name__ == "__main__":
    main()
