import json
import os
import random
import tempfile
import unittest

import _duong_dan  # noqa: F401
from do_luong import co_qc, lay_mau


def cau(i, dang, ly_do=(), de=1):
    ly_do = list(ly_do)
    return {"id": f"sys-{i}", "nguon": "he_thong", "dang": dang, "de_thi_id": de, "da_hien_cho_gv": True,
            "qc": {"co": co_qc.has_content_flag(ly_do), "co_chinh": co_qc.co_chinh(ly_do), "ly_do": ly_do,
                   "ly_do_chinh": co_qc.ly_do_chinh(ly_do), "buoc": sorted({co_qc.buoc_cua(r) for r in ly_do})}}


def kho(de_ids=(1, 2, 3, 4)):
    """Mỗi đề: mỗi dạng 5 câu không cờ, 1 câu cờ chính; đề 1 thêm 1 câu chỉ có cờ trùng, đề 2 thêm 1 câu cờ hạ tầng."""
    rows, i = [], 0
    for de in de_ids:
        for dang in ("mcq", "tf", "sa"):
            for _ in range(5):
                i += 1
                rows.append(cau(i, dang, (), de))
            i += 1
            rows.append(cau(i, dang, ["solver_inconsistent"], de))
        i += 1
        if de == 1:
            rows.append(cau(i, "mcq", ["duplicate: 3"], de))
        elif de == 2:
            rows.append(cau(i, "tf", ["solver_failed"], de))
    return rows


def baseline(luot=(1, 2, 3, 4), so=6):
    return [{"id": f"baseline_2-d{d}-c{k:02d}", "nguon": "baseline_2", "de_baseline": d,
             "dang": ("mcq", "tf", "sa")[(k - 1) // so]} for d in luot for k in range(1, 3 * so + 1)]


class LayMauD2Test(unittest.TestCase):
    def test_loc_theo_4_de_va_ba_tang(self):
        rows = kho((1, 2, 3, 4, 9))  # đề 9 là đề thử, không thuộc thực nghiệm
        chon, nhat_ky, tong_the = lay_mau.lay_mau_d2(rows, [], [1, 2, 3, 4], n_khong_co=12, seed=7)
        self.assertNotIn(9, {c["de_thi_id"] for c in chon})
        he = [c for c in chon if c["nguon"] == "he_thong"]
        # tầng cờ chính: lấy HẾT (4 đề x 3 dạng = 12 câu); tầng không cờ: 12 mỗi dạng
        self.assertEqual(12, sum(1 for c in he if c["tang"] == "co_chinh"))
        for dang in ("mcq", "tf", "sa"):
            self.assertEqual(12, sum(1 for c in he if c["tang"] == "khong_co" and c["dang"] == dang), dang)
            self.assertEqual(4, sum(1 for c in he if c["tang"] == "co_chinh" and c["dang"] == dang), dang)
        # câu chỉ có cờ trùng / cờ hạ tầng không vào phiếu, nhưng được đếm
        self.assertFalse(any(c["qc"]["co"] and not c["qc"]["co_chinh"] for c in chon))
        self.assertEqual(1, tong_the[("mcq", "chi_co_phu")])
        self.assertEqual(1, tong_the[("tf", "chi_co_phu")])
        self.assertEqual(20, tong_the[("sa", "khong_co")])
        self.assertEqual(4, tong_the[("sa", "co_chinh")])
        self.assertTrue(any("ngoài 4 đề" in x for x in nhat_ky), nhat_ky)
        self.assertEqual(len(he), len({c["id"] for c in he}))

    def test_seed_on_dinh_va_khong_phu_thuoc_thu_tu(self):
        rows, b = kho(), baseline()
        a, _, _ = lay_mau.lay_mau_d2(rows, b, [1, 2, 3, 4], seed=20261115)
        shuffled = rows[:]
        random.Random(1).shuffle(shuffled)
        c, _, _ = lay_mau.lay_mau_d2(shuffled, list(reversed(b)), [1, 2, 3, 4], seed=20261115)
        self.assertEqual([x["id"] for x in a], [x["id"] for x in c])
        d, _, _ = lay_mau.lay_mau_d2(rows, b, [1, 2, 3, 4], seed=20261116)
        self.assertNotEqual([x["id"] for x in a], [x["id"] for x in d])

    def test_khong_co_thieu_thi_lay_het_va_ghi_ro(self):
        rows = [r for r in kho() if not (r["dang"] == "tf" and not r["qc"]["co"])][:]
        rows += [cau(900 + i, "tf", (), 1) for i in range(5)]
        chon, nhat_ky, _ = lay_mau.lay_mau_d2(rows, [], [1, 2, 3, 4], n_khong_co=12, seed=1)
        self.assertEqual(5, sum(1 for c in chon if c["dang"] == "tf" and c["tang"] == "khong_co"))
        self.assertTrue(any("tf/khong_co: cần 12, chỉ có 5" in x for x in nhat_ky), nhat_ky)

    def test_baseline_deu_theo_luot(self):
        chon, nhat_ky, _ = lay_mau.lay_mau_d2([], baseline(), [1, 2, 3, 4], n_baseline=12, seed=3)
        self.assertEqual([], nhat_ky)
        self.assertTrue(all(c["tang"] == "baseline_2" for c in chon))
        for dang in ("mcq", "tf", "sa"):
            for d in (1, 2, 3, 4):
                self.assertEqual(3, sum(1 for c in chon if c["dang"] == dang and c["de_baseline"] == d), (dang, d))

    def test_baseline_luot_thieu_thi_bu_tu_luot_khac(self):
        b = [r for r in baseline() if not (r["de_baseline"] == 2 and r["dang"] == "tf" and r["id"] > "baseline_2-d2-c07")]
        chon, nhat_ky, _ = lay_mau.lay_mau_d2([], b, [1, 2, 3, 4], n_baseline=12, seed=3)
        self.assertEqual(12, sum(1 for c in chon if c["dang"] == "tf"))
        self.assertEqual(1, sum(1 for c in chon if c["dang"] == "tf" and c["de_baseline"] == 2))
        self.assertTrue(any("tf/lượt 2: cần 3, chỉ có 1" in x for x in nhat_ky), nhat_ky)

    def test_chi_nhan_baseline_2(self):
        with self.assertRaises(ValueError):
            lay_mau.lay_mau_d2([], [{"id": "baseline_1-d1-c01", "nguon": "baseline_1", "dang": "mcq", "de_baseline": 1}],
                               [1], seed=1)


def dong_xuat(i, dang, ly_do_qc, de):
    """Một dòng giống sql/d2-xuat-cau.sql (đủ trường để cau.tu_dong_xuat + kiem_tra qua)."""
    row = {"id": i, "dang": dang, "de": f"Đề {i}", "phuong_an": None, "dap_an_mcq": None, "dap_an_sa": None, "y": None,
           "loi_giai": "g", "khoi": 8, "chu_de": "Đa thức", "bai": "b", "muc_do_o": "easy", "de_thi_id": de,
           "prompt_version": f"matrix-{dang}-vX", "ma_tran_mau": None, "da_hien_cho_gv": True, "qc": ly_do_qc}
    if dang == "mcq":
        row["phuong_an"], row["dap_an_mcq"] = {"A": "1", "B": "2", "C": "3", "D": "4"}, "A"
    elif dang == "sa":
        row["dap_an_sa"] = "5"
    else:
        row["y"] = [{"vi_tri": v, "noi_dung": v, "nhan": "T", "muc_do": "easy"} for v in "abcd"]
    return row


QC_SACH = {"format_passed": True, "format_warnings": [], "dedup_ran": True, "dedup_passed": True, "answer_ran": True,
           "answer_passed": True}
QC_SOLVER = dict(QC_SACH, answer_passed=False)
QC_TRUNG = dict(QC_SACH, dedup_passed=False, dedup_compared_to=1)


class MainTest(unittest.TestCase):
    def test_main_ghi_khoa_bo_cham_va_log_tong_the(self):
        rows, i = [], 0
        for de in (1, 2, 5):
            for dang in ("mcq", "tf", "sa"):
                for qc in [QC_SACH] * 3 + [QC_SOLVER]:
                    i += 1
                    rows.append(dong_xuat(i, dang, qc, de))
        rows.append(dong_xuat(999, "mcq", QC_TRUNG, 1))
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "xuat.jsonl"), "w", encoding="utf-8") as f:
                f.write("\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n")
            with open(os.path.join(d, "b2.jsonl"), "w", encoding="utf-8") as f:
                for r in baseline((1, 2), so=2):
                    r.update({"de": "x", "phuong_an": {"A": "1", "B": "2", "C": "3", "D": "4"} if r["dang"] == "mcq"
                              else None, "y": [{"vi_tri": v, "noi_dung": v} for v in "abcd"] if r["dang"] == "tf"
                              else None, "dap_an": "A" if r["dang"] == "mcq" else {v: "Đ" for v in "abcd"}
                              if r["dang"] == "tf" else "3"})
                    f.write(json.dumps(r, ensure_ascii=False) + "\n")
            out = os.path.join(d, "out")
            lay_mau.main(["--he-thong", os.path.join(d, "xuat.jsonl"), "--de-thi-id", "1", "2",
                          "--baseline-2", os.path.join(d, "b2.jsonl"), "--n-khong-co", "2", "--n-baseline", "2",
                          "--seed", "5", "--out-dir", out])
            khoa = lay_mau.doc_khoa(os.path.join(out, "khoa.csv"))
            with open(os.path.join(out, "lay-mau-log.json"), encoding="utf-8") as f:
                log = json.load(f)
        he = [k for k in khoa if k["nguon"] == "he_thong"]
        self.assertEqual(6 + 6, len(he))  # 2 đề x 3 dạng cờ chính + 2 không cờ mỗi dạng
        self.assertEqual({"co_chinh", "khong_co"}, {k["tang"] for k in he})
        self.assertTrue(all(k["de_thi_id"] in ("1", "2") for k in he))
        self.assertNotIn("sys-999", {k["id"] for k in khoa})
        self.assertEqual("1", next(k for k in he if k["tang"] == "co_chinh")["co_chinh"])
        self.assertEqual(6, sum(1 for k in khoa if k["nguon"] == "baseline_2"))
        self.assertEqual({"mcq": {"co_chinh": 2, "khong_co": 6, "chi_co_phu": 1}, "tf": {"co_chinh": 2, "khong_co": 6},
                          "sa": {"co_chinh": 2, "khong_co": 6}}, log["tong_the"])
        self.assertEqual([1, 2], log["de_thi_id"])
        self.assertEqual(["sys-999"], [x["id"] for x in log["chi_co_phu"]])


class GanMaTest(unittest.TestCase):
    def test_ma_ngau_nhien_khong_trung_va_on_dinh(self):
        rows = kho()
        a = lay_mau.gan_ma(rows, seed=4)
        b = lay_mau.gan_ma(list(reversed(rows)), seed=4)
        self.assertEqual([(t, m, c["id"]) for t, m, c in a], [(t, m, c["id"]) for t, m, c in b])
        mas = [m for _, m, _ in a]
        self.assertEqual(len(mas), len(set(mas)))
        self.assertTrue(all(len(m) == 4 and set(m) <= set(lay_mau.MA_ALPHABET) for m in mas))
        self.assertEqual(list(range(1, len(rows) + 1)), [t for t, _, _ in a])
        # thứ tự đã xáo: không còn nhóm theo dạng / cờ như đầu vào
        self.assertNotEqual([c["id"] for c in lay_mau._sap(rows)], [c["id"] for _, _, c in a])

    def test_file_khoa(self):
        c1 = dict(cau(1, "mcq", ["solver_inconsistent", "dedup_not_run"]), tang="co_chinh")
        b = {"id": "baseline_2-d1-c03", "nguon": "baseline_2", "dang": "sa", "de_baseline": 1, "tang": "baseline_2"}
        ds = lay_mau.gan_ma([c1, b], seed=1)
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "khoa.csv")
            lay_mau.ghi_khoa(path, ds)
            rows = {r["id"]: r for r in lay_mau.doc_khoa(path)}
        self.assertEqual("1", rows["sys-1"]["co_qc"])
        self.assertEqual("1", rows["sys-1"]["co_chinh"])
        self.assertEqual("solver_inconsistent|dedup_not_run", rows["sys-1"]["ly_do"])
        self.assertEqual("solver_inconsistent", rows["sys-1"]["ly_do_chinh"])
        self.assertEqual("co_chinh", rows["sys-1"]["tang"])
        self.assertEqual("", rows["baseline_2-d1-c03"]["co_qc"])
        self.assertEqual("baseline_2", rows["baseline_2-d1-c03"]["tang"])
        self.assertEqual("1", rows["baseline_2-d1-c03"]["de_baseline"])


if __name__ == "__main__":
    unittest.main()
