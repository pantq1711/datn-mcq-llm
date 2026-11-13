"""Phiếu chấm không được lộ cờ QC, lý do, nguồn câu, phiên bản prompt, id câu hay lời giải."""
import os
import re
import shutil
import tempfile
import unittest
import zipfile

import _duong_dan  # noqa: F401
from do_luong import cau as cau_mod
from do_luong import lay_mau, phieu

LOI_GIAI_BI_MAT = "LỜI GIẢI BÍ MẬT không được lên phiếu"


def bo_mau():
    he_thong = cau_mod.tu_dong_xuat({
        "id": 4711, "dang": "mcq", "de": "Kết quả của phép tính $2x \\cdot 3x$ là", "loi_giai": LOI_GIAI_BI_MAT,
        "phuong_an": {"A": "$6x^2$", "B": "$5x$", "C": "$6x$", "D": "$5x^2$"}, "dap_an_mcq": "A",
        "prompt_version": "matrix-mcq-v8-draft", "ma_tran_mau": "giuaki1-toan8", "khoi": 8, "chu_de": "Đa thức",
        "bai": "Các phép tính đa thức", "muc_do_o": "easy", "da_hien_cho_gv": True, "de_thi_id": 99,
        "qc": {"dedup_ran": True, "dedup_passed": True, "answer_ran": True, "answer_passed": False,
               "judge_answer": "multiple", "solver_option_check": True, "format_warnings": []}})
    tf = cau_mod.tu_dong_xuat({
        "id": 4712, "dang": "tf", "de": "Cho đa thức $A = x^2 - 1$.", "loi_giai": LOI_GIAI_BI_MAT,
        "y": [{"vi_tri": k, "noi_dung": f"Ý {k}: $A$ có bậc {n}", "nhan": l, "muc_do": m}
              for k, n, l, m in (("a", 2, "T", "easy"), ("b", 1, "F", "easy"), ("c", 3, "F", "medium"),
                                 ("d", 0, "F", "hard"))],
        "prompt_version": "matrix-tf-v10-draft", "khoi": 8, "chu_de": "Đa thức", "bai": "Đơn thức, đa thức",
        "qc": {"dedup_ran": True, "dedup_passed": True, "answer_ran": True, "answer_passed": True,
               "grounding_verdict": "not_supported", "format_warnings": []}})
    baseline = {"id": "baseline_2-d1-c16", "nguon": "baseline_2", "dang": "sa", "de": "Tính $a*b$ với a_1 = 2 [ví dụ]",
                "dap_an": "-0,5", "loi_giai": LOI_GIAI_BI_MAT, "khoi": 8, "chu_de": "Đa thức", "bai": "x",
                "muc_do": "medium", "prompt_version": "baseline_2"}
    for c in (he_thong, tf, baseline):
        assert not cau_mod.kiem_tra(c), cau_mod.kiem_tra(c)
    return lay_mau.gan_ma([he_thong, tf, baseline], seed=11)


TU_KHOA_CAM = {"he_thong", "baseline", "Baseline", "co_qc", "solver", "Solver", "grounding", "matrix-", "prompt",
               "sys-", "giuaki1", "duplicate", "warning", "Hệ thống", "hệ thống", "Gemini", "QC", "cờ"}


def chuoi_cam_cu_the(ds):
    """Mọi giá trị trong file khóa (trừ mã, thứ tự, dạng) + lời giải: không được có ở BẤT KỲ đâu trong file (kể cả XML)."""
    cam = {LOI_GIAI_BI_MAT}
    for t, m, c in ds:
        row = lay_mau.dong_khoa(t, m, c)
        for k in ("id", "ly_do", "prompt_version", "ma_tran_mau"):
            for v in str(row[k]).split("|"):
                if v:
                    cam.add(v)
    return cam


def chu_trong_docx(path):
    """(chữ hiển thị của document.xml, toàn bộ XML)."""
    with zipfile.ZipFile(path) as z:
        doc = z.read("word/document.xml").decode("utf-8")
        xml = "".join(z.read(n).decode("utf-8") for n in z.namelist() if n.endswith(".xml"))
    return re.sub(r"<[^>]+>", "", doc), xml


def chu_trong_xlsx(path):
    """(giá trị ô + tên sheet + công thức danh sách chọn, toàn bộ XML)."""
    from openpyxl import load_workbook
    wb = load_workbook(path)
    out = []
    for ws in wb.worksheets:
        out.append(ws.title)
        for row in ws.iter_rows(values_only=True):
            out.extend(str(v) for v in row if v is not None)
        out.extend(dv.formula1 or "" for dv in ws.data_validations.dataValidation)
    with zipfile.ZipFile(path) as z:
        xml = "".join(z.read(n).decode("utf-8", errors="replace") for n in z.namelist() if n.endswith(".xml"))
    return "\n".join(out), xml


class PhieuKhongLoTest(unittest.TestCase):
    def setUp(self):
        self.ds = bo_mau()
        self.tmp = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def kiem(self, hien_thi, xml, ten):
        for s in TU_KHOA_CAM | chuoi_cam_cu_the(self.ds):
            self.assertNotIn(s, hien_thi, f"{ten} lộ '{s}'")
        for s in chuoi_cam_cu_the(self.ds):
            self.assertNotIn(s, xml, f"{ten} (XML) lộ '{s}'")

    def test_markdown_khong_lo(self):
        md = phieu.markdown_de(self.ds)
        self.kiem(md, md, "markdown")
        for _, ma, _ in self.ds:
            self.assertIn(f"Mã {ma}", md)
        self.assertIn("$6x^2$", md)
        self.assertIn("a) Đúng; b) Sai", md)

    def test_xlsx_khong_lo_va_co_danh_sach_chon(self):
        path = os.path.join(self.tmp, "phieu-Quan.xlsx")
        phieu.xlsx_phieu_cham(path, self.ds)
        self.kiem(*chu_trong_xlsx(path), "xlsx")
        from openpyxl import load_workbook
        ws = load_workbook(path)["Phiếu chấm"]
        self.assertEqual(phieu.COT_PHIEU, [c.value for c in ws[1]])
        lists = {dv.formula1: str(dv.sqref) for dv in ws.data_validations.dataValidation}
        self.assertIn('"Đúng,Sai,Nhiều đáp án đúng,Không có đáp án đúng"', lists)
        self.assertIn('"Đúng,Không đúng,Không rõ"', lists)
        self.assertIn('"Không,Có"', lists)
        # mỗi dòng có danh sách Đáp án đúng theo dạng
        for i, (_, _, c) in enumerate(self.ds, start=2):
            want = {"mcq": phieu.DAP_AN_MCQ, "tf": phieu.DAP_AN_TF, "sa": phieu.DAP_AN_SA}[c["dang"]]
            f = '"' + ",".join(want) + '"'
            self.assertIn(f"D{i}", lists[f].split())

    @unittest.skipUnless(shutil.which("pandoc"), "cần pandoc")
    def test_docx_khong_lo_va_cong_thuc_la_equation(self):
        path = os.path.join(self.tmp, "de-cham.docx")
        phieu.pandoc_docx(phieu.markdown_de(self.ds), path)
        text, xml = chu_trong_docx(path)
        self.kiem(text, xml, "docx")
        self.assertIn("<m:oMath", xml)  # công thức thành equation Word (OMML), không phải chữ thô
        for _, ma, _ in self.ds:
            self.assertIn(ma, text)

    def test_thoat_markdown_nhu_exam_markdown_builder(self):
        self.assertEqual("Tính $a*b$ với a\\_1 = 2 \\[ví dụ\\]", cau_mod.inline("Tính $a*b$ với a_1 = 2 [ví dụ]"))
        self.assertEqual("giá \\$5 và $x$", cau_mod.inline("giá \\$5 và $x$"))
        self.assertEqual("$x+1$", cau_mod.inline("$$x+1$$"))
        self.assertEqual("$\\frac{1}{2}$", cau_mod.inline("\\(\\frac{1}{2}\\)"))
        self.assertEqual(["Dòng 1 $a b$", "Dòng 2"], cau_mod.doan("Dòng 1 $a\nb$\n\nDòng 2"))


class PhieuCapTest(unittest.TestCase):
    def test_phieu_cap_khong_co_diem(self):
        caps = [{"cap": 1, "dang": "mcq",
                 "cau_1": {"dang": "mcq", "de": "Tính $1+1$", "phuong_an": {"A": "1", "B": "2", "C": "3", "D": "4"}},
                 "cau_2": {"dang": "mcq", "de": "Tính $1+2$", "phuong_an": {"A": "1", "B": "2", "C": "3", "D": "4"}},
                 "cosine": 0.8765, "tang": "same_run"}]
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "cap-An.xlsx")
            phieu.xlsx_phieu_cap(path, caps)
            text, xml = chu_trong_xlsx(path)
        md = phieu.markdown_cap(caps)
        for s in ("0.8765", "0,8765", "same_run", "cosine"):
            self.assertNotIn(s, text)
            self.assertNotIn(s, xml)
            self.assertNotIn(s, md)
        self.assertIn('"Trùng,Không trùng,Không chắc"', text)


if __name__ == "__main__":
    unittest.main()
