import os
import random
import tempfile
import unittest

import _duong_dan  # noqa: F401
from do_luong import lay_mau


def cau(i, dang, co, mau="giuaki1-toan8"):
    return {"id": f"sys-{i}", "nguon": "he_thong", "dang": dang, "qc": {"co": co, "ly_do": ["solver_inconsistent"] if co
            else [], "buoc": ["solver"] if co else []}, "ma_tran_mau": mau, "da_hien_cho_gv": True}


def kho(so_moi_tang=15):
    rows, i = [], 0
    for dang in ("mcq", "tf", "sa"):
        for co in (True, False):
            for _ in range(so_moi_tang):
                i += 1
                rows.append(cau(i, dang, co, "giuaki1-toan8" if i % 3 else None))
    return rows


class LayMauHeThongTest(unittest.TestCase):
    def test_phan_tang_dung(self):
        chon, nhat_ky = lay_mau.lay_mau_he_thong(kho(), n=60, seed=7)
        self.assertEqual(60, len(chon))
        self.assertEqual([], nhat_ky)
        for dang in ("mcq", "tf", "sa"):
            for co in (True, False):
                self.assertEqual(10, sum(1 for c in chon if c["dang"] == dang and c["qc"]["co"] == co), (dang, co))
        self.assertEqual(60, len({c["id"] for c in chon}))

    def test_seed_on_dinh_va_khong_phu_thuoc_thu_tu_dau_vao(self):
        rows = kho()
        a, _ = lay_mau.lay_mau_he_thong(rows, n=60, seed=20261113)
        b, _ = lay_mau.lay_mau_he_thong(list(reversed(rows)), n=60, seed=20261113)
        shuffled = rows[:]
        random.Random(1).shuffle(shuffled)
        c, _ = lay_mau.lay_mau_he_thong(shuffled, n=60, seed=20261113)
        self.assertEqual([x["id"] for x in a], [x["id"] for x in b])
        self.assertEqual([x["id"] for x in a], [x["id"] for x in c])
        d, _ = lay_mau.lay_mau_he_thong(rows, n=60, seed=20261114)
        self.assertNotEqual([x["id"] for x in a], [x["id"] for x in d])

    def test_uu_tien_ma_tran_mau(self):
        chon, _ = lay_mau.lay_mau_he_thong(kho(), n=60, seed=3)
        # mỗi tầng có 10 câu của ma trận mẫu (i % 3 != 0) nên đủ 10 câu cần lấy
        self.assertTrue(all(c["ma_tran_mau"] == "giuaki1-toan8" for c in chon))

    def test_tang_thieu_thi_bu_va_ghi_nhat_ky(self):
        rows = [r for r in kho() if not (r["dang"] == "tf" and r["qc"]["co"])][:]  # tf không có câu có cờ
        rows += [cau(900 + i, "tf", True) for i in range(3)]  # chỉ 3 câu tf có cờ
        chon, nhat_ky = lay_mau.lay_mau_he_thong(rows, n=60, seed=5)
        self.assertEqual(60, len(chon))
        self.assertEqual(3, sum(1 for c in chon if c["dang"] == "tf" and c["qc"]["co"]))
        # tf không cờ chỉ có 15 câu: 10 theo tầng + 5 bù; 2 câu còn thiếu bù từ dạng khác
        self.assertEqual(15, sum(1 for c in chon if c["dang"] == "tf" and not c["qc"]["co"]))
        self.assertTrue(any("tf/có cờ: cần 10, chỉ có 3" in x for x in nhat_ky), nhat_ky)
        self.assertTrue(any("bù 2 câu từ dạng khác" in x for x in nhat_ky), nhat_ky)

    def test_khong_du_cau_bao_ro(self):
        rows = [cau(i, "mcq", i % 2 == 0) for i in range(10)]
        chon, nhat_ky = lay_mau.lay_mau_he_thong(rows, n=60, seed=1)
        self.assertEqual(10, len(chon))
        self.assertTrue(any("KHÔNG ĐỦ CÂU" in x for x in nhat_ky))

    def test_chi_da_hien(self):
        rows = kho()
        for r in rows[::2]:
            r["da_hien_cho_gv"] = False
        chon, _ = lay_mau.lay_mau_he_thong(rows, n=30, seed=1, chi_da_hien=True)
        self.assertTrue(all(c["da_hien_cho_gv"] for c in chon))


class LayMauBaselineTest(unittest.TestCase):
    def test_chia_ti_le_tinh_tay(self):
        # 24 mcq, 4 tf, 8 sa, lấy 20: mỗi dạng 1, còn 17 chia 11,33 / 1,89 / 3,78 -> 12, 2, 4, dư 2 cho tf (,89) và sa (,78)
        self.assertEqual({"mcq": 12, "tf": 3, "sa": 5}, lay_mau.chia_ti_le({"mcq": 24, "tf": 4, "sa": 8}, 20))

    def test_chia_ti_le_khong_vuot_so_co(self):
        self.assertEqual({"mcq": 2, "tf": 1, "sa": 0}, lay_mau.chia_ti_le({"mcq": 2, "tf": 1, "sa": 0}, 20))

    def test_lay_mau_baseline_on_dinh(self):
        rows = [{"id": f"baseline_1-d{d}-c{i:02d}", "nguon": "baseline_1",
                 "dang": "mcq" if i <= 12 else "tf" if i <= 14 else "sa"} for d in (1, 2) for i in range(1, 19)]
        a, nk = lay_mau.lay_mau_baseline(rows, 20, seed=9)
        b, _ = lay_mau.lay_mau_baseline(list(reversed(rows)), 20, seed=9)
        self.assertEqual([x["id"] for x in a], [x["id"] for x in b])
        self.assertEqual(20, len(a))
        self.assertEqual({"mcq": 12, "tf": 3, "sa": 5}, {d: sum(1 for x in a if x["dang"] == d) for d in ("mcq", "tf", "sa")})
        self.assertEqual([], nk)


class GanMaTest(unittest.TestCase):
    def test_ma_ngau_nhien_khong_trung_va_on_dinh(self):
        rows = kho(10)
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
        ds = lay_mau.gan_ma([cau(1, "mcq", True), {"id": "baseline_2-d1-c03", "nguon": "baseline_2", "dang": "sa"}], seed=1)
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "khoa.csv")
            lay_mau.ghi_khoa(path, ds)
            rows = {r["id"]: r for r in lay_mau.doc_khoa(path)}
        self.assertEqual("1", rows["sys-1"]["co_qc"])
        self.assertEqual("solver_inconsistent", rows["sys-1"]["ly_do"])
        self.assertEqual("", rows["baseline_2-d1-c03"]["co_qc"])
        self.assertEqual("baseline_2", rows["baseline_2-d1-c03"]["nguon"])


if __name__ == "__main__":
    unittest.main()
