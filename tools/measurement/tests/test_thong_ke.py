"""Công thức thống kê so với số tính tay / giá trị công bố."""
import math
import unittest

import _duong_dan  # noqa: F401
from do_luong import thong_ke as tk


class WilsonTest(unittest.TestCase):
    def test_5_tren_10(self):
        # Tính tay: z²=3,8415; mẫu số 1,38415; tâm 0,5; nửa rộng 1,96·√(0,025+0,0096)/1,38415 = 0,26341
        p, lo, hi = tk.wilson(5, 10)
        self.assertEqual(0.5, p)
        self.assertAlmostEqual(0.2366, lo, places=4)
        self.assertAlmostEqual(0.7634, hi, places=4)

    def test_0_tren_10(self):
        p, lo, hi = tk.wilson(0, 10)
        self.assertEqual(0.0, lo)
        self.assertAlmostEqual(0.2775, hi, places=4)

    def test_n_bang_0(self):
        self.assertEqual((None, None, None), tk.wilson(0, 0))


class KappaTest(unittest.TestCase):
    def test_bang_2x2_tinh_tay(self):
        # [[20, 5], [10, 15]]: po = 35/50 = 0,7; pe = (25·30 + 25·20)/2500 = 0,5; κ = 0,4
        a = ["c"] * 25 + ["k"] * 25
        b = ["c"] * 20 + ["k"] * 5 + ["c"] * 10 + ["k"] * 15
        kappa, n, po = tk.cohen_kappa(a, b)
        self.assertEqual(50, n)
        self.assertAlmostEqual(0.7, po)
        self.assertAlmostEqual(0.4, kappa)

    def test_bo_cap_trong_va_mot_nhan(self):
        kappa, n, po = tk.cohen_kappa(["x", None, "x"], ["x", "x", ""])
        self.assertEqual(1, n)
        self.assertIsNone(kappa)  # p_e = 1
        self.assertEqual(1.0, po)

    def test_ba_nhan(self):
        a2 = ["x", "x", "y", "y", "z", "z"]
        b2 = ["x", "x", "x", "y", "z", "x"]
        kappa, n, po = tk.cohen_kappa(a2, b2)
        # đồng ý: 1,2,4,5 -> 4/6; biên b2: x4 y1 z1 -> pe = (2·4 + 2·1 + 2·1)/36 = 12/36
        self.assertAlmostEqual(4 / 6, po)
        self.assertAlmostEqual((4 / 6 - 1 / 3) / (2 / 3), kappa)


class FisherTest(unittest.TestCase):
    def test_nem_tra(self):
        # Bài "quý bà nếm trà" [[3,1],[1,3]]: p hai phía = 34/70
        odds, p = tk.fisher_exact(3, 1, 1, 3)
        self.assertAlmostEqual(9.0, odds)
        self.assertAlmostEqual(34 / 70, p, places=10)

    def test_gia_tri_scipy(self):
        # scipy.stats.fisher_exact([[1, 9], [11, 3]]) -> p = 0.0027594561852200836
        odds, p = tk.fisher_exact(1, 9, 11, 3)
        self.assertAlmostEqual(0.0027594561852200836, p, places=12)
        self.assertAlmostEqual(3 / 99, odds)

    def test_o_bang_0(self):
        odds, p = tk.fisher_exact(0, 5, 5, 0)
        self.assertTrue(math.isnan(odds) or odds == 0)
        self.assertAlmostEqual(2 / 252, p, places=10)  # 2 bảng cực trị, mỗi bảng 1/C(10,5)


class PrfTest(unittest.TestCase):
    def test_tinh_tay(self):
        p, r, f1 = tk.prf(8, 2, 4)
        self.assertAlmostEqual(0.8, p)
        self.assertAlmostEqual(8 / 12, r)
        self.assertAlmostEqual(2 * 0.8 * (8 / 12) / (0.8 + 8 / 12), f1)

    def test_mau_so_0(self):
        self.assertEqual((None, 0.0, None), tk.prf(0, 0, 3))
        self.assertEqual((0.0, 0.0, 0.0), tk.prf(0, 2, 3))

    def test_recall_trong_so(self):
        # 100 câu có cờ, 80% lỗi -> 80 lỗi; 900 không cờ, 10% lỗi -> 90 lỗi; recall = 80/170
        self.assertAlmostEqual(80 / 170, tk.recall_trong_so(0.8, 0.1, 100, 900))


class ChenhLechTest(unittest.TestCase):
    def test_vi_du_newcombe_1998(self):
        # Newcombe (1998), 56/70 - 48/80, phương pháp 10: 0,2 [0,0524; 0,3339]
        d, lo, hi = tk.chenh_lech(56, 70, 48, 80)
        self.assertAlmostEqual(0.2, d)
        self.assertAlmostEqual(0.0524, lo, places=4)
        self.assertAlmostEqual(0.3339, hi, places=4)


if __name__ == "__main__":
    unittest.main()
