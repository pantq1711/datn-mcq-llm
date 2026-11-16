"""Phân tích D2/D4 và D6 trên dữ liệu giả nhỏ, so với số tính tay."""
import csv
import os
import shutil
import tempfile
import unittest

import _duong_dan  # noqa: F401
from do_luong import lay_mau, phan_tich_d2, phan_tich_d6, phieu

# id -> (nguồn, lý do QC, nhãn người chấm 1 ở pha 2: dap_an, muc_do, vuot_lop, mo_ho)
# Cờ chính (L10): sys-1, sys-2 (bước Solver), sys-3 (bước format). Mọi câu là mcq, khóa "A".
DU_LIEU = {
    "sys-1": ("he_thong", ["solver_inconsistent"], ("Sai", "Đúng", "Không", "Không")),
    "sys-2": ("he_thong", ["solver_multiple_correct"], ("Nhiều đáp án đúng", "Đúng", "Không", "Không")),
    "sys-3": ("he_thong", ["warning: explanation: số dấu $ bị lẻ", "dedup_not_run"], ("Đúng", "Đúng", "Không", "Không")),
    "sys-4": ("he_thong", [], ("Đúng", "Đúng", "Không", "Không")),
    "sys-5": ("he_thong", [], ("Đúng", "Không đúng", "Không", "Không")),
    "sys-6": ("he_thong", [], ("Đúng", "Đúng", "Không", "Có")),
    "baseline_2-d1-c01": ("baseline_2", None, ("Sai", "Đúng", "Có", "Không")),
    "baseline_2-d2-c02": ("baseline_2", None, ("Đúng", "Đúng", "Không", "Không")),
    "baseline_2-d3-c01": ("baseline_2", None, ("Đúng", "Đúng", "Không", "Không")),
    "baseline_2-d4-c02": ("baseline_2", None, ("Đúng", "Đúng", "Không", "Không")),
}
# Người chấm 2 khác người 1 ở: sys-3 Đáp án (Sai), sys-5 Mức độ (Đúng)
KHAC_2 = {("sys-3", 3): "Sai", ("sys-5", 5): "Đúng"}
# Đoán nguồn của người chấm 1 (cột cuối pha 2); còn lại để "Không đoán được"
DOAN_1 = {"sys-1": "Công cụ của nhóm", "sys-2": "Công cụ của nhóm", "sys-3": "Công cụ của nhóm",
          "sys-4": "AI gọi thẳng", "baseline_2-d1-c01": "AI gọi thẳng"}
# Pha 1 (tự giải) của người chấm 1: sys-1 chọn B (khớp với việc chấm Sai), sys-4 chọn C nhưng pha 2 lại chấm Đúng (neo)
PHA1_1 = {"sys-1": "B", "sys-2": "Nhiều phương án đúng", "sys-4": "C", "baseline_2-d1-c01": "D"}


def dung_bo(tmp):
    from do_luong import co_qc
    cau = []
    for i, (cid, (nguon, ly_do, _)) in enumerate(DU_LIEU.items()):
        c = {"id": cid, "nguon": nguon, "dang": "mcq", "dap_an": "A"}
        if ly_do is not None:
            c["qc"] = {"co": co_qc.has_content_flag(ly_do), "ly_do": ly_do, "co_chinh": co_qc.co_chinh(ly_do),
                       "ly_do_chinh": co_qc.ly_do_chinh(ly_do), "buoc": sorted({co_qc.buoc_cua(r) for r in ly_do})}
            c["tang"] = "co_chinh" if c["qc"]["co_chinh"] else "khong_co"
        else:
            c["tang"], c["de_baseline"] = "baseline_2", int(cid.split("-d")[1][0])
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
            nhan = DU_LIEU[c["id"]][2]
            for j, col in enumerate((4, 6, 7, 8)):
                v = nhan[j]
                if nguoi == 2 and col in (4, 6):
                    v = KHAC_2.get((c["id"], 3 if col == 4 else 5), v)
                ws.cell(row=r, column=col, value=v)
            if nguoi == 1:
                ws.cell(row=r, column=10, value=DOAN_1.get(c["id"], "Không đoán được"))
        wb.save(path)
        paths.append(path)
    return khoa_path, paths, ds


def dung_pha1(tmp, ds):
    """Phiếu pha 1 của hai người: người 1 theo PHA1_1 (mặc định "A"); người 2 như người 1 nhưng sys-4 chọn "A" (nhất quán
    với việc chấm Đúng). Người 2 chấm sys-3 là Sai ở pha 2 dù tự giải ra "A" = khóa (ngược chiều, có chủ đích)."""
    from openpyxl import load_workbook
    paths = []
    for nguoi in (1, 2):
        path = os.path.join(tmp, f"pha1-{nguoi}.xlsx")
        phieu.xlsx_phieu_pha1(path, ds)
        wb = load_workbook(path)
        ws = wb[phieu.SHEET_PHA1]
        for r, (_, _, c) in enumerate(ds, start=2):
            chon = dict(PHA1_1, **({"sys-4": "A"} if nguoi == 2 else {}))
            ws.cell(row=r, column=4, value=chon.get(c["id"], "A"))
        wb.save(path)
        paths.append(path)
    return paths


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
                         {(m, tc, a, b) for _, m, tc, a, b, _ in ds})
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
        _, rows = phan_tich_d2.bang_co_nhan(self.khoa, self._nhan(), "loi_dap_an", loc=phan_tich_d2.la_co_solver)
        self.assertEqual(["tất cả", 6, "2/2", "0/4"], rows[-1][:4])

    def test_duong_tinh_la_co_chinh_khong_phai_co_giao_vien_thay(self):
        """L10: câu chỉ có cờ hạ tầng (dedup_not_run) không là dương tính, dù giáo viên thấy cờ."""
        khoa = [dict(k) for k in self.khoa]
        k3 = next(k for k in khoa if k["id"] == "sys-3")
        k3.update(co_chinh="0", ly_do_chinh="", tang="khong_co")  # giả sử chỉ còn cờ hạ tầng
        _, rows = phan_tich_d2.bang_co_nhan(khoa, self._nhan(), "loi_dap_an")
        self.assertEqual(["tất cả", 6, "2/2", "0/4"], rows[-1][:4])

    def test_bang_nhom_va_fisher(self):
        (h1, r1), (h2, r2) = phan_tich_d2.bang_nhom(self.khoa, self._nhan(), "loi_dap_an")
        by = {r[0]: r for r in r1}
        self.assertEqual("0/3", by[phan_tich_d2.TEN_NHOM["khong_co"]][4])
        self.assertEqual("2/3", by[phan_tich_d2.TEN_NHOM["co_chinh"]][4])
        self.assertEqual("1/4", by[phan_tich_d2.TEN_NHOM["baseline_2"]][4])
        self.assertFalse(any("cả mẫu" in r[0] for r in r1))  # L8a: không còn dòng "cả mẫu"
        # So sánh chính: không cờ vs baseline 2; Fisher [[0,3],[1,3]]: hai bảng cùng biên 4/7, 3/7 -> p = 1
        self.assertEqual(1, len(r2))
        self.assertIn("không cờ", r2[0][0])
        self.assertIn("Baseline 2", r2[0][0])
        self.assertEqual("1,0000", r2[0][3])

    def test_uoc_luong_truoc_qc_co_trong_so(self):
        """L9 (i): tỉ lệ lỗi hệ thống trước QC = trộn tầng cờ chính (tổng điều tra) và không cờ theo cỡ tầng."""
        n_tt = {("mcq", True): 3, ("mcq", False): 27}
        (_, r1), _ = phan_tich_d2.bang_nhom(self.khoa, self._nhan(), "loi_dap_an", n_tt)
        # (3·2/3 + 27·0/3) / 30 = 6,7%
        dong = next(r for r in r1 if r[0] == phan_tich_d2.TEN_NHOM["truoc_qc"])
        self.assertEqual("6,7%", dong[5])

    def test_tong_the_tu_lay_mau_log(self):
        path = os.path.join(self.tmp, "lay-mau-log.json")
        with open(path, "w", encoding="utf-8") as f:
            f.write('{"tong_the": {"mcq": {"co_chinh": 3, "khong_co": 20, "chi_co_phu": 1}, "tf": {"khong_co": 5}}}')
        self.assertEqual({("mcq", True): 3, ("mcq", False): 20, ("tf", False): 5},
                         phan_tich_d2.tong_the_tu_log(path))

    def test_doan_nguon(self):
        _, rows = phan_tich_d2.bang_doan_nguon(self.khoa, [self.p1, self.p2])
        # người 1: đoán 5 câu (4 hệ thống, 1 baseline), đúng 3 hệ thống + 1 baseline = 4/5; 5 câu "Không đoán được"
        self.assertEqual(["Người chấm 1", 5, 4, "80,0%"], rows[0][:4])
        self.assertEqual(5, rows[0][5])
        self.assertEqual(["Người chấm 2", 0, 0], rows[1][:3])

    def test_bao_cao_ghi_file(self):
        out = os.path.join(self.tmp, "kq")
        px = {(self.ma["sys-3"], "dap_an"): "Đúng", (self.ma["sys-5"], "muc_do"): "Không đúng"}
        phan_tich_d2.bao_cao(self.khoa, self.p1, self.p2, px, out, b=100)
        for f in ("ket-qua-d2.md", "bang1-kappa.csv", "bang2a-co-x-nhan-loi_dap_an.csv", "bang4-nhom.csv",
                  "bang5-so-sanh.csv", "bang8-doan-nguon.csv", "bang10-bootstrap-cum.csv", "bang11-theo-de-luot.csv",
                  "bang12-truoc-sau.csv", "cau-lot.csv"):
            self.assertTrue(os.path.exists(os.path.join(out, f)), f)
        self.assertFalse(os.path.exists(os.path.join(out, "bang9-bootstrap.csv")))  # cần cỡ tầng
        out2 = os.path.join(self.tmp, "kq2")
        qc = [{"de_thi_id": 1, "cau_sinh_ra": 31, "qua_format": 30, "trung": 0}]
        phan_tich_d2.bao_cao(self.khoa, self.p1, self.p2, px, out2, {("mcq", True): 3, ("mcq", False): 27},
                             qc_dem=qc, de_thi_ids=[1], n_quan_the=31, b=100)
        self.assertTrue(os.path.exists(os.path.join(out2, "bang9-bootstrap.csv")))
        with open(os.path.join(out2, "ket-qua-d2.md"), encoding="utf-8") as f:
            md = f.read()
        for s in ("Bảng 9.", "Bảng 10.", "Bảng 11.", "Bảng 12.", "B = 100", "không khớp"):
            self.assertIn(s, md)
        with open(os.path.join(out, "cau-lot.csv"), encoding="utf-8-sig") as f:
            lot = list(csv.DictReader(f))
        self.assertEqual(["sys-6"], [r["id"] for r in lot])


class Pha1Test(unittest.TestCase):
    """L6a: pha 1 tự giải, máy so với khóa; tự giải khác khóa mà pha 2 vẫn chấm Đúng thì đưa vào phân xử."""

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        _, self.paths, self.ds = dung_bo(self.tmp)
        self.pha1_paths = dung_pha1(self.tmp, self.ds)
        self.khoa = lay_mau.doc_khoa(os.path.join(self.tmp, "khoa.csv"))
        self.bo = {m: c for _, m, c in self.ds}
        self.ma = {c["id"]: m for _, m, c in self.ds}

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_so_khop_tung_dang(self):
        sk = phan_tich_d2.so_khop
        self.assertTrue(sk("mcq", {"mcq": "A"}, "A"))
        self.assertFalse(sk("mcq", {"mcq": "Nhiều phương án đúng"}, "A"))
        self.assertIsNone(sk("mcq", {"mcq": None}, "A"))
        self.assertTrue(sk("tf", {"tf": {"a": "Đ", "b": "S", "c": "Đ", "d": "Đ"}}, {"a": "Đ", "b": "S", "c": "Đ", "d": "Đ"}))
        self.assertFalse(sk("tf", {"tf": {"a": "Đ", "b": "Không kết luận được", "c": "Đ", "d": "Đ"}},
                            {"a": "Đ", "b": "S", "c": "Đ", "d": "Đ"}))
        self.assertIsNone(sk("tf", {"tf": {"a": "Đ", "b": None, "c": "Đ", "d": "Đ"}}, {"a": "Đ", "b": "S", "c": "Đ",
                                                                                        "d": "Đ"}))
        for a, b in (("0,5", "1/2"), ("-3", "−3"), ("$12$", "12"), ("x^3-8", "x^3 - 8"), ("0.25", "0,25")):
            self.assertTrue(sk("sa", {"sa": a}, b), (a, b))
        self.assertFalse(sk("sa", {"sa": "5"}, "6"))

    def test_doc_pha1_va_neo(self):
        pha1 = [phan_tich_d2.doc_pha1(p) for p in self.pha1_paths]
        self.assertEqual("C", pha1[0][self.ma["sys-4"]]["mcq"])
        p1, p2 = (phan_tich_d2.doc_phieu(p) for p in self.paths)
        neo = phan_tich_d2.can_xem_lai(self.khoa, self.bo, pha1, [p1, p2])
        # sys-4: người 1 tự giải C, khóa A, pha 2 chấm Đúng -> xem lại (nghi neo); sys-3: người 2 tự giải A (= khóa) nhưng
        # pha 2 chấm Sai -> xem lại (ngược chiều). sys-1 (B, chấm Sai), sys-2 (nhiều, chấm Nhiều), baseline c01 (D, chấm
        # Sai) nhất quán
        self.assertEqual({self.ma["sys-4"], self.ma["sys-3"]}, set(neo))
        ds = phan_tich_d2.bat_dong(self.khoa, p1, p2, neo)
        row = next(r for r in ds if r[1] == self.ma["sys-4"])
        self.assertEqual(("dap_an", "Đúng", "Đúng"), row[2:5])
        self.assertIn("tự giải", row[5])
        # có kết luận phân xử thì kết luận thắng cả khi hai người đồng ý
        nhan, _ = phan_tich_d2.nhan_cuoi(self.khoa, p1, p2, {(self.ma["sys-4"], "dap_an"): "Sai",
                                                            (self.ma["sys-3"], "dap_an"): "Đúng",
                                                            (self.ma["sys-5"], "muc_do"): "Đúng"})
        self.assertEqual("Sai", nhan[self.ma["sys-4"]]["dap_an"])

    def test_bang_pha1(self):
        pha1 = [phan_tich_d2.doc_pha1(p) for p in self.pha1_paths]
        _, rows = phan_tich_d2.bang_pha1(self.khoa, self.bo, pha1)
        by = {(r[0], r[1]): r for r in rows}
        # người 1, hệ thống không cờ: sys-4 lệch (C), sys-5, sys-6 khớp -> 2/3
        self.assertEqual("2/3", by[("Người chấm 1", phan_tich_d2.TEN_NHOM["khong_co"])][2])
        self.assertEqual("3/4", by[("Người chấm 1", phan_tich_d2.TEN_NHOM["baseline_2"])][2])
        # người 2, cờ chính: sys-1 (B), sys-2 (nhiều) lệch khóa A; sys-3 (A) khớp -> 1/3
        self.assertEqual("1/3", by[("Người chấm 2", phan_tich_d2.TEN_NHOM["co_chinh"])][2])

    def test_gia_tri_la_bi_tu_choi(self):
        from openpyxl import load_workbook
        wb = load_workbook(self.paths[0])
        wb["Phiếu chấm"].cell(row=2, column=7, value="có lẽ")
        wb.save(self.paths[0])
        with self.assertRaises(ValueError):
            phan_tich_d2.doc_phieu(self.paths[0])


CAU_GIA = [  # (nhóm, dạng, cụm = đề hoặc lượt baseline, lỗi đáp án)
    ("co_chinh", "mcq", "1", 1), ("co_chinh", "mcq", "2", 1), ("co_chinh", "mcq", "3", 0), ("co_chinh", "mcq", "4", 0),
    ("khong_co", "mcq", "1", 1), ("khong_co", "mcq", "2", 0), ("khong_co", "mcq", "3", 0), ("khong_co", "mcq", "4", 0),
    ("co_chinh", "tf", "1", 1), ("co_chinh", "tf", "3", 1),
    ("khong_co", "tf", "1", 0), ("khong_co", "tf", "2", 0), ("khong_co", "tf", "3", 0), ("khong_co", "tf", "4", 0),
    ("baseline_2", "mcq", "1", 1), ("baseline_2", "mcq", "2", 1), ("baseline_2", "mcq", "3", 0),
    ("baseline_2", "mcq", "4", 0),
    ("baseline_2", "tf", "1", 1), ("baseline_2", "tf", "2", 0), ("baseline_2", "tf", "3", 0), ("baseline_2", "tf", "4", 0),
]
# Cỡ tầng: mcq cờ 4 / không cờ 20; tf cờ 2 / không cờ 12 (tầng cờ chính là tổng điều tra)
N_TT_GIA = {("mcq", True): 4, ("mcq", False): 20, ("tf", True): 2, ("tf", False): 12}
# Tính tay: p tầng = mcq cờ 2/4, mcq không 1/4, tf cờ 2/2, tf không 0/4
#   trước QC = (4·0,5 + 20·0,25 + 2·1 + 12·0) / 38 = 9/38; recall = (2 + 2) / 9 = 4/9
#   sau QC = 1/8; baseline 2 = 3/8; (i) = 9/38 − 3/8; (ii) = 9/38 − 1/8
TRUOC_GIA, RECALL_GIA = 9 / 38, 4 / 9


def khoa_gia(spec=CAU_GIA):
    khoa, nhan = [], {}
    for i, (g, d, c, loi) in enumerate(spec, 1):
        he = g != "baseline_2"
        ma = f"M{i:02d}"
        khoa.append({"ma": ma, "thu_tu": str(i), "id": ma, "nguon": "he_thong" if he else "baseline_2", "dang": d,
                     "tang": g, "co_chinh": ("1" if g == "co_chinh" else "0") if he else "", "ly_do_chinh": "",
                     "de_thi_id": c if he else "", "de_baseline": "" if he else c})
        nhan[ma] = {"dap_an": "Sai" if loi else "Đúng", "y_sai": "", "muc_do": "Đúng", "vuot_lop": "Không",
                    "mo_ho": "Không"}
    return khoa, nhan


class UocLuongBootstrapTest(unittest.TestCase):
    """Kế hoạch phân tích mục 3.2, 3.3, 5: ước lượng theo tầng dạng × tầng và bootstrap phân tầng / theo cụm."""

    def setUp(self):
        self.khoa, self.nhan = khoa_gia()
        self.dv = phan_tich_d2.don_vi(self.khoa, self.nhan, "loi_dap_an")

    def test_diem_uoc_luong_tinh_tay(self):
        u = phan_tich_d2.uoc_luong(self.dv, N_TT_GIA)
        self.assertAlmostEqual(TRUOC_GIA, u["truoc_qc"])
        self.assertAlmostEqual(RECALL_GIA, u["recall"])
        self.assertAlmostEqual(1 / 8, u["sau_qc"])
        self.assertAlmostEqual(3 / 8, u["baseline_2"])
        self.assertAlmostEqual(TRUOC_GIA - 3 / 8, u["i"])
        self.assertAlmostEqual(TRUOC_GIA - 1 / 8, u["ii"])

    def test_don_vi_lay_cum_theo_nguon(self):
        cums = {(g, c) for g, _, c, _ in self.dv}
        self.assertIn(("khong_co", "4"), cums)
        self.assertIn(("baseline_2", "4"), cums)
        # câu không có nhãn cuối không vào
        nhan = dict(self.nhan)
        del nhan["M01"]
        self.assertEqual(len(self.dv) - 1, len(phan_tich_d2.don_vi(self.khoa, nhan, "loi_dap_an")))

    def test_recall_tong_the_cua_bang2_dung_trong_so_dang_x_tang(self):
        # gộp trong tầng (cách cũ) sẽ ra (6·4/6) / (6·4/6 + 32·1/8) = 50%; theo dạng × tầng là 4/9 = 44,4%
        _, rows = phan_tich_d2.bang_co_nhan(self.khoa, self.nhan, "loi_dap_an", N_TT_GIA)
        by = {r[0]: r for r in rows}
        self.assertEqual("44,4%", by["tất cả"][6])
        # một dạng: trùng công thức recall_trong_so: mcq = 4·0,5 / (4·0,5 + 20·0,25) = 2/7
        self.assertEqual("28,6%", by["mcq"][6])

    def test_bootstrap_phan_tang_lap_lai_duoc_va_chua_diem(self):
        a = phan_tich_d2.bootstrap_phan_tang(self.dv, N_TT_GIA, b=500, seed=7)
        b = phan_tich_d2.bootstrap_phan_tang(self.dv, N_TT_GIA, b=500, seed=7)
        self.assertEqual(a, b)
        diem, lo, hi, kxd = a["truoc_qc"]
        self.assertAlmostEqual(TRUOC_GIA, diem)
        self.assertLessEqual(lo, diem)
        self.assertGreaterEqual(hi, diem)
        self.assertEqual(0, kxd)
        # recall: tầng cờ tf luôn có lỗi nên tử số > 0 ở mọi lần lặp, không có lần không xác định
        self.assertEqual(0, a["recall"][3])
        self.assertLess(a["i"][1], a["i"][2])

    def test_bootstrap_rut_lai_trong_tang_khong_tron_tang(self):
        """Mọi tầng đồng nhất (cả tầng cùng lỗi hoặc cùng không lỗi) thì rút lại trong tầng không đổi gì: KTC co về điểm.
        Nếu rút lại trộn tầng thì KTC sẽ rộng ra."""
        spec = [("co_chinh", "mcq", "1", 1), ("co_chinh", "mcq", "2", 1), ("khong_co", "mcq", "1", 0),
                ("khong_co", "mcq", "2", 0), ("khong_co", "tf", "1", 1), ("baseline_2", "mcq", "1", 1),
                ("baseline_2", "tf", "2", 0)]
        khoa, nhan = khoa_gia(spec)
        kq = phan_tich_d2.bootstrap_phan_tang(phan_tich_d2.don_vi(khoa, nhan, "loi_dap_an"),
                                              {("mcq", True): 2, ("mcq", False): 8, ("tf", False): 10}, b=300, seed=1)
        for k in ("truoc_qc", "recall", "i", "ii"):
            diem, lo, hi, _ = kq[k]
            self.assertAlmostEqual(diem, lo, msg=k)
            self.assertAlmostEqual(diem, hi, msg=k)
        # baseline 2 rút trong từng dạng: mcq toàn lỗi, tf toàn không lỗi -> luôn 1/2
        self.assertEqual((0.5, 0.5, 0.5, 0), kq["baseline_2"])

    def test_bootstrap_khong_xac_dinh_khi_khong_co_loi(self):
        spec = [(g, d, c, 0) for g, d, c, _ in CAU_GIA]
        khoa, nhan = khoa_gia(spec)
        kq = phan_tich_d2.bootstrap_phan_tang(phan_tich_d2.don_vi(khoa, nhan, "loi_dap_an"), N_TT_GIA, b=50)
        self.assertEqual((None, None, None, 50), kq["recall"])
        self.assertEqual((0.0, 0.0, 0.0, 0), kq["truoc_qc"])

    def test_bootstrap_cum_tinh_tay(self):
        diem, lo, hi, kxd, n_he, n_ba = phan_tich_d2.bootstrap_cum(self.dv, b=500, seed=3)
        self.assertAlmostEqual(1 / 8 - 3 / 8, diem)
        self.assertEqual((4, 4, 0), (n_he, n_ba, kxd))
        self.assertLessEqual(lo, diem)
        self.assertGreaterEqual(hi, diem)
        # hai đề hệ thống (đề 1: 1/1 lỗi, đề 2: 0/1), một lượt baseline 0/2: chênh lệch chỉ nhận 0; 0,5; 1
        spec = [("khong_co", "mcq", "1", 1), ("khong_co", "mcq", "2", 0), ("co_chinh", "mcq", "1", 1),
                ("baseline_2", "mcq", "1", 0), ("baseline_2", "tf", "1", 0)]
        khoa, nhan = khoa_gia(spec)
        kq = phan_tich_d2.bootstrap_cum(phan_tich_d2.don_vi(khoa, nhan, "loi_dap_an"), b=2000, seed=3)
        self.assertEqual((0.5, 0.0, 1.0, 0, 2, 1), kq)  # câu cờ chính không vào

    def test_bang_bootstrap_va_bang_cum(self):
        _, rows = phan_tich_d2.bang_bootstrap(self.khoa, self.nhan, N_TT_GIA, b=200, seed=1)
        self.assertEqual(2 * len(phan_tich_d2.TEN_CHI_SO), len(rows))
        truoc = next(r for r in rows if r[0] == "loi_dap_an" and r[1] == phan_tich_d2.TEN_CHI_SO["truoc_qc"])
        self.assertEqual("23,7%", truoc[2])
        chenh_i = next(r for r in rows if r[0] == "loi_dap_an" and r[1] == phan_tich_d2.TEN_CHI_SO["i"])
        self.assertEqual("-13,8 điểm %", chenh_i[2])
        _, rows = phan_tich_d2.bang_bootstrap_cum(self.khoa, self.nhan, b=200, seed=1)
        self.assertEqual(["-25,0 điểm %", "4 / 4"], [rows[0][1], rows[0][3]])

    def test_bang_theo_de_va_luot(self):
        _, rows = phan_tich_d2.bang_theo_cum(self.khoa, self.nhan)
        by = {(r[0], r[1]): r[2:] for r in rows}
        T = phan_tich_d2.TEN_NHOM
        self.assertEqual(["1/1", "1/1", "0/0", "2/2", "100,0%"], by[(T["co_chinh"], "Đề 1")])
        self.assertEqual(["0/1", "0/0", "0/0", "0/1", "0,0%"], by[(T["co_chinh"], "Đề 4")])
        self.assertEqual(["1/1", "0/1", "0/0", "1/2", "50,0%"], by[(T["khong_co"], "Đề 1")])
        self.assertEqual(["1/1", "0/1", "0/0", "1/2", "50,0%"], by[(T["baseline_2"], "Lượt 2")])
        self.assertEqual(4 + 4 + 4, len(rows))  # cờ chính có câu ở 4 đề, không cờ 4 đề, baseline 4 lượt


class TruocSauTest(unittest.TestCase):
    """Kế hoạch phân tích mục 6: bảng trước/sau theo tiêu chí."""

    QC_DEM = [{"de_thi_id": 1, "cau_sinh_ra": 22, "qua_format": 20, "trung": 1, "dedup_loi": 0, "dedup_chua_chay": 0},
              {"de_thi_id": 2, "cau_sinh_ra": 18, "qua_format": 18, "trung": 1, "dedup_loi": 1, "dedup_chua_chay": 0},
              {"de_thi_id": 9, "cau_sinh_ra": 99, "qua_format": 1, "trung": 50, "dedup_loi": 0, "dedup_chua_chay": 0}]

    def setUp(self):
        self.khoa, self.nhan = khoa_gia()
        # một câu không cờ mcq "Không rõ" mức độ, một câu "Không đúng"; một câu không cờ tf mơ hồ
        self.nhan["M05"]["muc_do"] = "Không rõ"
        self.nhan["M06"]["muc_do"] = "Không đúng"
        self.nhan["M11"]["mo_ho"] = "Có"

    def test_dong_db_chi_lay_de_thuc_nghiem(self):
        _, rows, cb = phan_tich_d2.bang_truoc_sau(self.khoa, self.nhan, N_TT_GIA, self.QC_DEM, [1, 2], 38)
        by = {r[0]: r for r in rows}
        self.assertEqual("38/40 = 95,0%", by["Định dạng hợp lệ"][1])
        self.assertEqual("36/38 = 94,7%", by["Không trùng"][1])
        self.assertIn("dedup lỗi/chưa kết luận: 1", by["Không trùng"][3])
        self.assertEqual([], cb)

    def test_canh_bao_khi_hai_nguon_khong_khop(self):
        _, _, cb = phan_tich_d2.bang_truoc_sau(self.khoa, self.nhan, N_TT_GIA, self.QC_DEM, [1, 2, 3], 85)
        self.assertEqual(2, len(cb))
        self.assertIn("thiếu đề: [3]", cb[0])
        self.assertIn("không khớp", cb[1])
        _, rows, _ = phan_tich_d2.bang_truoc_sau(self.khoa, self.nhan, N_TT_GIA)
        self.assertIn("--qc-dem", rows[0][1])

    def test_dong_nhan_tinh_tay(self):
        _, rows, _ = phan_tich_d2.bang_truoc_sau(self.khoa, self.nhan, N_TT_GIA, self.QC_DEM, [1, 2])
        by = {r[0]: r for r in rows}
        # đáp án đúng: trước = 1 − 9/38 = 76,3%; sau = 7/8
        self.assertEqual("76,3%", by["Đáp án đúng"][1])
        self.assertTrue(by["Đáp án đúng"][2].startswith("7/8 = 87,5%"))
        # mơ hồ: tầng tf không cờ 1/4 mơ hồ -> trước = 1 − 12·0,25/38 = 35/38 = 92,1%; sau = 7/8
        self.assertEqual("92,1%", by["Không mơ hồ"][1])
        self.assertTrue(by["Không mơ hồ"][2].startswith("7/8"))
        # mức độ: tầng mcq không cờ đạt 2/4, vào mẫu số 3/4; tầng khác đạt hết
        #   trước = (4 + 20·0,5 + 2 + 12) / (4 + 20·0,75 + 2 + 12) = 28/33 = 84,8%; sau = 6/7 (bỏ "Không rõ")
        self.assertEqual("84,8%", by["Đúng mức độ"][1])
        self.assertTrue(by["Đúng mức độ"][2].startswith("6/7 = 85,7%"))
        self.assertIn("'Không rõ': 1 câu", by["Đúng mức độ"][3])
        self.assertEqual("100,0%", by["Không vượt lớp"][1])


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


CAP_CHAN = [  # cap, cosine, cùng số liệu, cùng phủ định, cùng dấu vân tay, lô, nhóm, nhãn
    ("1", 0.97, 1, 1, 0, "1", "lo1", "Trùng"),
    ("2", 0.95, 0, 1, 0, "1", "lo1", "Không trùng"),         # thô báo trùng, chốt chặn số liệu chặn đúng
    ("3", 0.93, 1, 0, 0, "1", "lo1", "Không trùng"),         # chốt chặn phủ định chặn đúng
    ("4", 0.92, 1, 1, 0, "2", "da_chan", "Không trùng"),     # báo nhầm ở 0,90 (FP)
    ("5", 0.85, 1, 1, 0, "2", "cung_so_lieu_0.60-0.90", "Trùng"),  # bỏ sót ở 0,90 (FN)
    ("6", 0.60, 1, 1, 0, "2", "cung_so_lieu_0.60-0.90", "Không chắc"),
    ("7", 1.00, 1, 1, 1, "1", "lo1", "Trùng"),
    ("8", 0.70, 0, 1, 0, "1", "lo1", "Không trùng"),
    ("9", 0.50, 1, 1, 0, "1", "lo1", None),                  # chưa gán
]


class PhanTichD6MoRongTest(unittest.TestCase):
    def setUp(self):
        self.caps, self.nhan = [], {}
        for cap, cos, ss, sn, vt, lo, nhom, nh in CAP_CHAN:
            row = {"cap": cap, "q1": str(int(cap) * 10), "q2": str(int(cap) * 10 + 1), "dang": "mcq",
                   "tang": "same_run", "cosine": str(cos), "cung_so_lieu": str(ss), "cung_phu_dinh": str(sn),
                   "cung_van_tay": str(vt), "lo": lo, "nhom": nhom}
            for t in phan_tich_d6.NGUONG:
                row[phan_tich_d6.cot_du_doan(t)] = "1" if vt or (ss and sn and cos >= t) else "0"
            self.caps.append(row)
            if nh:
                self.nhan[cap] = nh

    def test_chot_chan_so_voi_cosine_tho(self):
        h, rows, n = phan_tich_d6.bang_chot_chan(self.caps, self.nhan)
        self.assertEqual(7, n)  # bỏ cặp 6 (Không chắc) và 9 (chưa gán)
        by = {r[0]: dict(zip(h, r)) for r in rows}
        # 0,90: thô báo cặp 1,2,3,4,7 (TP 2: 1,7; FP 3: 2,3,4); chốt chặn bỏ 2 và 3
        r = by["0,90"]
        self.assertEqual([3, 1, 2, 2, 2, 0], [r["FP thô"], r["FP + chốt chặn"], r["Chốt chặn bỏ được FP"],
                                              r["TP thô"], r["TP + chốt chặn"], r["Chốt chặn làm mất TP"]])
        self.assertEqual("0,400", r["P thô"])          # 2/5
        self.assertEqual("0,667", r["P + chốt chặn"])  # 2/3
        self.assertEqual(r["P + chốt chặn"], r["P đầy đủ"])
        self.assertEqual("0,667", r["R + chốt chặn"])  # TP 2, FN 1 (cặp 5)
        # 0,50: thô thêm cặp 8 (cosine 0,70, khác số liệu) vào FP; chốt chặn vẫn bỏ được 3 FP (2, 3, 8)
        self.assertEqual([4, 1, 3], [by["0,50"]["FP thô"], by["0,50"]["FP + chốt chặn"],
                                     by["0,50"]["Chốt chặn bỏ được FP"]])
        self.assertIsNone(phan_tich_d6.bang_chot_chan([{"cap": "1"}], {"1": "Trùng"}))

    def test_f1_cao_nhat_va_nguong_hien_tai(self):
        _, rows, curve, n = phan_tich_d6.bang_nguong(self.caps, self.nhan)
        self.assertEqual(7, n)
        by = {r[0]: r for r in rows}
        # 0,50-0,85: TP 3 (1,5,7), FP 1 (4), F1 = 0,857 (cao nhất); 0,90: F1 0,667; 0,95: 0,8
        self.assertEqual("0,857", by["0,85"][7])
        self.assertEqual("F1 cao nhất", by["0,85"][8])
        self.assertEqual("0,667", by["0,90"][7])
        self.assertEqual("PROVISIONAL_THRESHOLD (hiện tại)", by["0,90"][8])
        self.assertEqual("0,800", by["0,95"][7])
        self.assertEqual(8, sum(1 for c in curve if c[-2] == 1))
        self.assertEqual(["0.90"], [c[0] for c in curve if c[-1] == 1])
        # trùng ngưỡng hiện tại và cao nhất thì ghi cả hai
        _, rows2, _, _ = phan_tich_d6.bang_nguong(self.caps, {"1": "Trùng", "7": "Trùng", "4": "Trùng"})
        self.assertEqual("PROVISIONAL_THRESHOLD (hiện tại); F1 cao nhất", {r[0]: r for r in rows2}["0,90"][8])

    def test_cap_doan_sai_o_090(self):
        h, rows = phan_tich_d6.cap_doan_sai(self.caps, self.nhan)
        got = {r[0]: dict(zip(h, r)) for r in rows}
        self.assertEqual({"4", "5"}, set(got))
        self.assertEqual("FP (báo trùng nhầm)", got["4"]["loai"])
        self.assertEqual("FN (bỏ sót)", got["5"]["loai"])
        self.assertEqual(("40", "41", "da_chan", "Không trùng", "Trùng"),
                         (got["4"]["q1"], got["4"]["q2"], got["4"]["nhom"], got["4"]["nhan"], got["4"]["he_thong_doan"]))

    def test_bao_cao_ghi_du_file_va_chiu_duoc_thieu_cot(self):
        tmp = tempfile.mkdtemp()
        try:
            phan_tich_d6.bao_cao(self.caps, [self.nhan], ["An"], tmp)
            for f in ("ket-qua-d6.md", "d6-chot-chan.csv", "d6-cap-sai-090.csv", "d6-so-nhan.csv",
                      "d6-duong-cong.csv", "d6-nguong-lo-1.csv", "d6-nguong-lo-2.csv", "d6-tang.csv"):
                self.assertTrue(os.path.exists(os.path.join(tmp, f)), f)
            with open(os.path.join(tmp, "d6-so-nhan.csv"), encoding="utf-8-sig") as f:
                self.assertEqual(["An", "9", "3", "4", "1", "1"], list(csv.reader(f))[1])  # 1 Không chắc, 1 chưa gán
            with open(os.path.join(tmp, "ket-qua-d6.md"), encoding="utf-8") as f:
                md = f.read()
            for s in ("F1 cao nhất", "Bảng D6-4", "Bảng D6-5", "lo=2", "nhom=da_chan", "không sinh ảnh"):
                self.assertIn(s, md)
            # cap-vang.csv cũ thiếu cột chẩn đoán: không có bảng chốt chặn, vẫn chạy
            cu = [{k: v for k, v in c.items() if k not in ("cung_so_lieu", "cung_phu_dinh", "cung_van_tay")}
                  for c in self.caps]
            out2 = os.path.join(tmp, "cu")
            phan_tich_d6.bao_cao(cu, [self.nhan], ["An"], out2)
            self.assertFalse(os.path.exists(os.path.join(out2, "d6-chot-chan.csv")))
            self.assertTrue(os.path.exists(os.path.join(out2, "d6-cap-sai-090.csv")))
        finally:
            shutil.rmtree(tmp, ignore_errors=True)

    def test_phieu_luyen_tap_khong_vao_ket_qua(self):
        tmp = tempfile.mkdtemp()
        try:
            path = os.path.join(tmp, "cap-An.xlsx")
            phieu.xlsx_phieu_cap(path, [{"cap": 1, "dang": "mcq", "cau_1": {"dang": "mcq", "de": "x"},
                                         "cau_2": {"dang": "mcq", "de": "y"}}])
            from openpyxl import load_workbook
            wb = load_workbook(path)
            ws = wb[phieu.SHEET_LUYEN]
            for r in range(2, 2 + len(phieu.CAP_LUYEN)):
                ws.cell(row=r, column=5, value="Trùng")  # gán đủ 3 cặp luyện
            wb[phieu.SHEET_CAP].cell(row=2, column=5, value="Không trùng")
            wb.save(path)
            self.assertEqual({"1": "Không trùng"}, phan_tich_d6.doc_nhan(path))
        finally:
            shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
