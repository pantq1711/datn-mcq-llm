"""Phân tích D2/D4 và D6 trên dữ liệu giả nhỏ, so với số tính tay."""
import csv
import os
import shutil
import tempfile
import unittest

import _duong_dan  # noqa: F401
from do_luong import lay_mau, phan_tich_d2, phan_tich_d6, phieu

# id -> (nguồn, cờ QC, bước, nhãn người chấm 1: dap_an, muc_do, vuot_lop, mo_ho)
DU_LIEU = {
    "sys-1": ("he_thong", True, "solver", ("Sai", "Đúng", "Không", "Không")),
    "sys-2": ("he_thong", True, "solver", ("Nhiều đáp án đúng", "Đúng", "Không", "Không")),
    "sys-3": ("he_thong", True, "dedup", ("Đúng", "Đúng", "Không", "Không")),
    "sys-4": ("he_thong", False, "", ("Đúng", "Đúng", "Không", "Không")),
    "sys-5": ("he_thong", False, "", ("Đúng", "Không đúng", "Không", "Không")),
    "sys-6": ("he_thong", False, "", ("Đúng", "Đúng", "Không", "Có")),
    "baseline_1-d1-c01": ("baseline_1", None, "", ("Sai", "Đúng", "Có", "Không")),
    "baseline_1-d1-c02": ("baseline_1", None, "", ("Đúng", "Đúng", "Không", "Không")),
    "baseline_2-d1-c01": ("baseline_2", None, "", ("Đúng", "Đúng", "Không", "Không")),
    "baseline_2-d1-c02": ("baseline_2", None, "", ("Đúng", "Đúng", "Không", "Không")),
}
# Người chấm 2 khác người 1 ở: sys-3 Đáp án (Sai), sys-5 Mức độ (Đúng)
KHAC_2 = {("sys-3", 3): "Sai", ("sys-5", 5): "Đúng"}


def dung_bo(tmp):
    cau = []
    for i, (cid, (nguon, co, buoc, _)) in enumerate(DU_LIEU.items()):
        c = {"id": cid, "nguon": nguon, "dang": "mcq"}
        if co is not None:
            c["qc"] = {"co": co, "ly_do": ["x"] if co else [], "buoc": [buoc] if buoc else []}
        cau.append(c)
    ds = lay_mau.gan_ma(cau, seed=2)
    khoa_path = os.path.join(tmp, "khoa.csv")
    lay_mau.ghi_khoa(khoa_path, ds)
    from openpyxl import load_workbook
    paths = []
    for nguoi in (1, 2):
        path = os.path.join(tmp, f"phieu-{nguoi}.xlsx")
        phieu.xlsx_phieu_cham(path, ds)
        wb = load_workbook(path)
        ws = wb["Phiếu chấm"]
        for r, (_, _, c) in enumerate(ds, start=2):
            nhan = DU_LIEU[c["id"]][3]
            for j, col in enumerate((4, 6, 7, 8)):
                v = nhan[j]
                if nguoi == 2 and col in (4, 6):
                    v = KHAC_2.get((c["id"], 3 if col == 4 else 5), v)
                ws.cell(row=r, column=col, value=v)
        wb.save(path)
        paths.append(path)
    return khoa_path, paths, ds


class PhanTichD2Test(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.khoa_path, self.paths, self.ds = dung_bo(self.tmp)
        self.khoa = lay_mau.doc_khoa(self.khoa_path)
        self.p1 = phan_tich_d2.doc_phieu(self.paths[0])
        self.p2 = phan_tich_d2.doc_phieu(self.paths[1])
        self.ma = {c["id"]: m for _, m, c in self.ds}

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_kappa_dap_an_tinh_tay(self):
        # người 1: Đúng 7, Sai 2, Nhiều 1; người 2: Đúng 6, Sai 3, Nhiều 1; po = 9/10; pe = (42+6+1)/100; κ = 0,41/0,51
        _, rows = phan_tich_d2.kappa_bang(self.khoa, self.p1, self.p2)
        dap_an = rows[0]
        self.assertEqual(["Đáp án", 10, "90,0%"], dap_an[:3])
        self.assertEqual(f"{0.41 / 0.51:.3f}".replace(".", ","), dap_an[3])

    def test_bat_dong_va_phan_xu(self):
        ds = phan_tich_d2.bat_dong(self.khoa, self.p1, self.p2)
        self.assertEqual({(self.ma["sys-3"], "dap_an", "Đúng", "Sai"), (self.ma["sys-5"], "muc_do", "Không đúng", "Đúng")},
                         {(m, tc, a, b) for _, m, tc, a, b in ds})
        nhan, thieu = phan_tich_d2.nhan_cuoi(self.khoa, self.p1, self.p2, {})
        self.assertEqual({self.ma["sys-3"], self.ma["sys-5"]}, set(thieu))
        path = os.path.join(self.tmp, "phan-xu.xlsx")
        phan_tich_d2.ghi_phan_xu(path, ds)
        from openpyxl import load_workbook
        wb = load_workbook(path)
        ws = wb["Phân xử"]
        for r in range(2, ws.max_row + 1):
            ws.cell(row=r, column=6, value="Đúng" if ws.cell(row=r, column=3).value == "Đáp án" else "Không đúng")
        wb.save(path)
        px = phan_tich_d2.doc_phan_xu(path)
        nhan, thieu = phan_tich_d2.nhan_cuoi(self.khoa, self.p1, self.p2, px)
        self.assertEqual([], thieu)
        self.assertEqual("Đúng", nhan[self.ma["sys-3"]]["dap_an"])

    def _nhan(self):
        px = {(self.ma["sys-3"], "dap_an"): "Đúng", (self.ma["sys-5"], "muc_do"): "Không đúng"}
        nhan, _ = phan_tich_d2.nhan_cuoi(self.khoa, self.p1, self.p2, px)
        return nhan

    def test_bang_co_x_nhan(self):
        nhan = self._nhan()
        # lỗi đáp án: TP 2 (sys-1, sys-2), FP 1 (sys-3), FN 0, TN 3
        _, rows = phan_tich_d2.bang_co_nhan(self.khoa, nhan, "loi_dap_an")
        tat_ca = rows[-1]
        self.assertEqual(["tất cả", 6, "2/3", "0/3"], tat_ca[:4])
        self.assertTrue(tat_ca[4].startswith("66,7%"))
        self.assertTrue(tat_ca[5].startswith("100,0%"))
        self.assertTrue(tat_ca[7].startswith("0,0%"))
        # lỗi bất kỳ: thêm sys-6 (mơ hồ) -> FN 1, recall 2/3, lọt 1/3
        _, rows = phan_tich_d2.bang_co_nhan(self.khoa, nhan, "loi_bat_ky", {("mcq", True): 100, ("mcq", False): 900})
        tat_ca = rows[-1]
        self.assertEqual(["tất cả", 6, "2/3", "1/3"], tat_ca[:4])
        self.assertTrue(tat_ca[5].startswith("66,7%"))
        # recall tổng thể = 100·(2/3) / (100·(2/3) + 900·(1/3)) = 66,67/366,67 = 18,2%
        self.assertEqual("18,2%", tat_ca[6])
        self.assertTrue(tat_ca[7].startswith("33,3%"))

    def test_bang_solver_chi_tinh_co_cua_solver(self):
        _, rows = phan_tich_d2.bang_co_nhan(self.khoa, self._nhan(), "loi_dap_an",
                                            loc=lambda k: "solver" in k["buoc"].split("|"))
        self.assertEqual(["tất cả", 6, "2/2", "0/4"], rows[-1][:4])

    def test_bang_nhom_va_fisher(self):
        (h1, r1), (h2, r2) = phan_tich_d2.bang_nhom(self.khoa, self._nhan(), "loi_bat_ky")
        by = {r[0]: r for r in r1}
        self.assertEqual("1/3", by["Hệ thống – không cờ (giáo viên nhận như câu sạch)"][4])
        self.assertEqual("1/2", by[phan_tich_d2.TEN_NGUON["baseline_1"]][4])
        self.assertEqual("0/2", by[phan_tich_d2.TEN_NGUON["baseline_2"]][4])
        # Fisher [[1,2],[1,1]]: mọi bảng cùng biên có xác suất 0,1 / 0,6 / 0,3 -> p = 1
        first = r2[0]
        self.assertIn("Baseline 1", first[0])
        self.assertEqual("1,0000", first[3])

    def test_bao_cao_ghi_file(self):
        out = os.path.join(self.tmp, "kq")
        px = {(self.ma["sys-3"], "dap_an"): "Đúng", (self.ma["sys-5"], "muc_do"): "Không đúng"}
        phan_tich_d2.bao_cao(self.khoa, self.p1, self.p2, px, out)
        for f in ("ket-qua-d2.md", "bang1-kappa.csv", "bang2a-co-x-nhan-loi_dap_an.csv", "bang4-nhom.csv",
                  "bang5-so-sanh.csv", "cau-lot.csv"):
            self.assertTrue(os.path.exists(os.path.join(out, f)), f)
        with open(os.path.join(out, "cau-lot.csv"), encoding="utf-8-sig") as f:
            lot = list(csv.DictReader(f))
        self.assertEqual(["sys-6"], [r["id"] for r in lot])

    def test_gia_tri_la_bi_tu_choi(self):
        from openpyxl import load_workbook
        wb = load_workbook(self.paths[0])
        wb["Phiếu chấm"].cell(row=2, column=7, value="có lẽ")
        wb.save(self.paths[0])
        with self.assertRaises(ValueError):
            phan_tich_d2.doc_phieu(self.paths[0])


CAP = [  # cap, cosine, cùng số liệu, nhãn An, nhãn Quân
    ("1", 0.97, True, "Trùng", "Trùng"),
    ("2", 0.92, True, "Trùng", "Trùng"),
    ("3", 0.88, False, "Không trùng", "Không trùng"),
    ("4", 0.86, True, "Không trùng", "Trùng"),
    ("5", 0.70, True, "Trùng", "Trùng"),
    ("6", 0.60, True, "Không chắc", "Không trùng"),
    ("7", 0.55, True, None, None),
]


class PhanTichD6Test(unittest.TestCase):
    def setUp(self):
        self.caps = []
        for cap, cos, cung, _, _ in CAP:
            row = {"cap": cap, "dang": "mcq", "tang": "same_run" if int(cap) % 2 else "random", "cosine": str(cos)}
            for t in phan_tich_d6.NGUONG:
                row[phan_tich_d6.cot_du_doan(t)] = "1" if cung and cos >= t else "0"
            self.caps.append(row)
        self.an = {c[0]: c[3] for c in CAP if c[3]}
        self.quan = {c[0]: c[4] for c in CAP if c[4]}

    def test_nguong_tinh_tay(self):
        _, rows, curve, n = phan_tich_d6.bang_nguong(self.caps, self.an)
        self.assertEqual(5, n)  # bỏ "Không chắc" và chưa gán
        by = {r[0]: r for r in rows}
        # 0,90: TP 2 (1, 2), FP 0, FN 1 (5), TN 2 (3, 4); P = 1, R = 2/3, F1 = 0,8
        self.assertEqual([2, 0, 1, 2], by["0,90"][1:5])
        self.assertEqual("0,800", by["0,90"][7])
        self.assertEqual("PROVISIONAL_THRESHOLD (hiện tại)", by["0,90"][8])
        # 0,70: TP 3, FP 1 (4), FN 0, TN 1; F1 = 2·0,75·1/1,75
        self.assertEqual([3, 1, 0, 1], by["0,70"][1:5])
        self.assertEqual(f"{2 * 0.75 / 1.75:.3f}".replace(".", ","), by["0,70"][7])
        # 0,95: chỉ cặp 1
        self.assertEqual([1, 0, 2, 2], by["0,95"][1:5])
        prov = [c for c in curve if c[-1] == 1]
        self.assertEqual(["0.90"], [c[0] for c in prov])
        self.assertEqual(10, len(curve))
        self.assertEqual("0.50", curve[0][0])

    def test_bao_cao_va_phieu_cap(self):
        tmp = tempfile.mkdtemp()
        try:
            paths = []
            for ten, nhan in (("An", self.an), ("Quan", self.quan)):
                path = os.path.join(tmp, f"cap-{ten}.xlsx")
                phieu.xlsx_phieu_cap(path, [{"cap": c["cap"], "dang": "mcq", "cau_1": {"de": "x"}, "cau_2": {"de": "y"}}
                                            for c in self.caps])
                from openpyxl import load_workbook
                wb = load_workbook(path)
                ws = wb["Gán nhãn cặp"]
                for r in range(2, ws.max_row + 1):
                    ws.cell(row=r, column=5, value=nhan.get(str(ws.cell(row=r, column=1).value)))
                wb.save(path)
                paths.append(path)
            labels = [phan_tich_d6.doc_nhan(p) for p in paths]
            self.assertEqual(self.an, labels[0])
            out = os.path.join(tmp, "kq")
            phan_tich_d6.bao_cao(self.caps, labels, ["An", "Quan"], out)
            with open(os.path.join(out, "ket-qua-d6.md"), encoding="utf-8") as f:
                md = f.read()
            self.assertIn("Cohen's κ", md)
            self.assertIn("PROVISIONAL_THRESHOLD", md)
            with open(os.path.join(out, "d6-cap-lech-nhan.csv"), encoding="utf-8-sig") as f:
                lech = f.read()
            self.assertIn("4,Không trùng,Trùng", lech)
            self.assertIn("6,Không chắc,Không trùng", lech)
            self.assertTrue(os.path.exists(os.path.join(out, "d6-duong-cong.csv")))
        finally:
            shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
