import json
import os
import shutil
import tempfile
import unittest

import _duong_dan  # noqa: F401
from do_luong import baseline_goi, baseline_parse, ma_tran

MT_NHO = ma_tran.doc(os.path.join(_duong_dan.FIXTURES, "ma-tran-nho.json"))


def doc_jsonl(path):
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f]


def doc_fixture(ten):
    with open(os.path.join(_duong_dan.FIXTURES, ten), encoding="utf-8") as f:
        return f.read()


class ParseChuTuDoTest(unittest.TestCase):
    def setUp(self):
        self.tot, self.loi = baseline_parse.tu_van_ban(doc_fixture("baseline-chu-tu-do.txt"), "baseline_1", 1, MT_NHO)
        self.theo_stt = {c["id"]: c for c in self.tot}

    def test_doc_du_4_cau_khong_loi(self):
        self.assertEqual([], self.loi)
        self.assertEqual(["baseline_1-d1-c01", "baseline_1-d1-c02", "baseline_1-d1-c03", "baseline_1-d1-c04"],
                         [c["id"] for c in self.tot])

    def test_mcq_phuong_an_tung_dong_va_cung_dong_dap_an_tu_bang(self):
        c1, c2 = self.theo_stt["baseline_1-d1-c01"], self.theo_stt["baseline_1-d1-c02"]
        self.assertEqual("Biểu thức nào sau đây là đơn thức?", c1["de"])
        self.assertEqual("$3x^2y$", c1["phuong_an"]["B"])
        self.assertEqual("B", c1["dap_an"])
        self.assertEqual({"A": "$70^\\circ$", "B": "$110^\\circ$", "C": "$90^\\circ$", "D": "$20^\\circ$"},
                         c2["phuong_an"])
        self.assertEqual("A", c2["dap_an"])

    def test_tf_va_sa_dap_an_o_cuoi_de(self):
        tf, sa = self.theo_stt["baseline_1-d1-c03"], self.theo_stt["baseline_1-d1-c04"]
        self.assertEqual({"a": "Đ", "b": "S", "c": "Đ", "d": "Đ"}, tf["dap_an"])
        self.assertEqual("$P \\ge 0$ với mọi $x$.", tf["y"][3]["noi_dung"])
        self.assertEqual("-0,5", sa["dap_an"])
        self.assertEqual("Tính giá trị của $A = 2x - 3$ tại $x = 1{,}25$.", sa["de"])  # không dính đường kẻ "---"

    def test_gan_sieu_du_lieu_theo_ma_tran(self):
        c2, tf = self.theo_stt["baseline_1-d1-c02"], self.theo_stt["baseline_1-d1-c03"]
        self.assertEqual(("Tứ giác", "Hình thang cân", "medium", 8), (c2["chu_de"], c2["bai"], c2["muc_do"], c2["khoi"]))
        self.assertEqual(["easy", "easy", "medium", "hard"], tf["muc_do"])

    def test_dap_an_ngay_duoi_cau_khong_bi_hieu_la_tieu_de(self):
        raw = "Câu 1. Đơn thức nào?\nA. $x$\nB. $x+1$\nC. $1/x$\nD. $x-1$\nĐáp án: A\nCâu 2. Góc?\nA. 1\nB. 2\nC. 3\nD. 4\n" \
              "**Đáp án:** C\nLời giải: vì tổng bằng $360^\\circ$.\nCâu 3. Cho $x$.\na) Đúng a\nb) b\nc) c\nd) d\n" \
              "Đáp án: a) Đ b) S c) S d) Đ\nCâu 4. Tính.\nĐáp số: 12"
        tot, loi = baseline_parse.tu_van_ban(raw, "baseline_1", 2, MT_NHO)
        self.assertEqual([], loi)
        self.assertEqual(["A", "C"], [c["dap_an"] for c in tot if c["dang"] == "mcq"])
        self.assertEqual("vì tổng bằng $360^\\circ$.", tot[1]["loi_giai"])
        self.assertEqual("12", tot[3]["dap_an"])

    def test_cau_thieu_vao_danh_sach_loi_khong_bo_im_lang(self):
        raw = "PHẦN I\nCâu 1. Hỏi?\nA. 1\nB. 2\nC. 3\nĐáp án: A\n"
        tot, loi = baseline_parse.tu_van_ban(raw, "baseline_1", 3, MT_NHO)
        self.assertEqual([], tot)
        msgs = [m for x in loi for m in x["loi"]]
        self.assertIn("thiếu phương án D", msgs)
        self.assertTrue(any("thiếu 1 câu mcq" in m for m in msgs), msgs)
        self.assertTrue(any("thiếu 1 câu tf" in m for m in msgs), msgs)
        self.assertTrue(any("thiếu 1 câu sa" in m for m in msgs), msgs)
        self.assertIn("A. 1", next(x for x in loi if x["id"] == "baseline_1-d3-c01")["goc"])


class ParseJsonTest(unittest.TestCase):
    def setUp(self):
        self.tot, self.loi = baseline_parse.tu_json(doc_fixture("baseline-json.txt"), "baseline_2", 1, MT_NHO)
        self.theo_id = {c["id"]: c for c in self.tot}

    def test_rao_chu_thua_dau_phay_thua_va_latex_mot_gach(self):
        self.assertEqual(["baseline_2-d1-c01", "baseline_2-d1-c03", "baseline_2-d1-c04"], [c["id"] for c in self.tot])
        self.assertEqual("Bậc là $3 + 1 = 4$.\nVậy chọn B.", self.theo_id["baseline_2-d1-c01"]["loi_giai"])  # \n thật giữ
        tf = self.theo_id["baseline_2-d1-c03"]
        self.assertEqual("$Q \\ge 0$", tf["y"][1]["noi_dung"])
        self.assertEqual({"a": "Đ", "b": "Đ", "c": "S", "d": "S"}, tf["dap_an"])
        self.assertEqual("$\\left(-1{,}5\\right)^2 = 2{,}25$", self.theo_id["baseline_2-d1-c04"]["loi_giai"])
        self.assertEqual("2,25", self.theo_id["baseline_2-d1-c04"]["dap_an"])

    def test_cau_loi_co_ly_do_va_ban_goc(self):
        self.assertEqual(1, len(self.loi))
        x = self.loi[0]
        self.assertEqual("baseline_2-d1-c02", x["id"])
        self.assertIn("thiếu phương án D", x["loi"])
        self.assertEqual("Tính $\\frac{1}{2} \\times 4$", x["cau"]["de"])  # \f, \t không bị đọc thành ký tự điều khiển
        self.assertEqual("$\\neq 2$", x["cau"]["phuong_an"]["B"])

    def test_json_hong_hoan_toan(self):
        tot, loi = baseline_parse.tu_json("không có JSON nào", "baseline_2", 2, MT_NHO)
        self.assertEqual([], tot)
        self.assertTrue(loi[0]["loi"][0].startswith("JSON không đọc được"))

    def test_sua_json_giu_escape_hop_le(self):
        self.assertEqual('["a\\nb", "\\u00e1", "\\\\frac", "\\"q\\""]',
                         baseline_parse.sua_json('["a\\nb", "\\u00e1", "\\\\frac", "\\"q\\""]'))
        self.assertEqual('["\\\\beta \\\\theta \\\\rho"]', baseline_parse.sua_json('["\\beta \\theta \\rho"]'))


class ParseFileVaSuaTayTest(unittest.TestCase):
    def test_main_ghi_loi_va_nhan_sua_tay(self):
        with tempfile.TemporaryDirectory() as d:
            tho = os.path.join(d, "tho")
            os.makedirs(tho)
            shutil.copy(os.path.join(_duong_dan.FIXTURES, "baseline-json.txt"), os.path.join(tho, "baseline_2-d1.txt"))
            out = os.path.join(d, "out")
            # ma trận mặc định có 12 mcq nên đầu ra thử thiếu câu -> có mục "thiếu" trong parse-loi
            baseline_parse.main(["--tho-dir", tho, "--out-dir", out])
            loi = doc_jsonl(os.path.join(out, "parse-loi.jsonl"))
            self.assertIn("baseline_2-d1-c02", [x["id"] for x in loi])
            sua = dict(next(x for x in loi if x["id"] == "baseline_2-d1-c02")["cau"])
            sua["phuong_an"] = dict(sua["phuong_an"], D="$4$")
            with open(os.path.join(d, "sua.jsonl"), "w", encoding="utf-8") as f:
                f.write(json.dumps(sua, ensure_ascii=False) + "\n")
            baseline_parse.main(["--tho-dir", tho, "--out-dir", out, "--sua-tay", os.path.join(d, "sua.jsonl")])
            ids = [x["id"] for x in doc_jsonl(os.path.join(out, "baseline_2.jsonl"))]
            loi2 = [x["id"] for x in doc_jsonl(os.path.join(out, "parse-loi.jsonl"))]
        self.assertIn("baseline_2-d1-c02", ids)
        self.assertNotIn("baseline_2-d1-c02", loi2)


class BaselineGoiTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.calls = []

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)
        os.environ.pop("GEMINI_API_KEY", None)

    def goi_gia(self, model, key, prompt, mode):
        self.calls.append((model, key, mode, prompt))
        return "[]", 10, 20, 0

    def test_doc_prompt_that_va_dung_ma_tran(self):
        mau = baseline_goi.doc_prompt_mau()
        p1 = baseline_goi.dung_prompt(1, mau[1], ma_tran.doc())
        p2 = baseline_goi.dung_prompt(2, mau[2], ma_tran.doc())
        self.assertNotIn("{MA_TRAN}", p1 + p2)
        self.assertIn("Giữa kì I – Toán 8", p1)
        self.assertIn("Câu 18 (dang = \"sa\"", p2)
        self.assertIn("Câu 13 (dang = \"tf\", mức độ 4 ý a-d: Nhận biết, Nhận biết, Thông hiểu, Vận dụng)", p2)
        self.assertEqual(18, len(ma_tran.cac_cau(ma_tran.doc())))

    def test_dry_run_khong_can_khoa_khong_goi(self):
        os.environ.pop("GEMINI_API_KEY", None)
        baseline_goi.main(["--dry-run", "--out-dir", self.tmp], goi=self.goi_gia)
        self.assertEqual([], self.calls)
        self.assertTrue(os.path.exists(os.path.join(self.tmp, "baseline_1-d1.prompt.txt")))
        self.assertFalse(os.path.exists(os.path.join(self.tmp, "baseline_1-d1.txt")))

    def test_thieu_khoa_thi_dung(self):
        os.environ.pop("GEMINI_API_KEY", None)
        with self.assertRaises(SystemExit):
            baseline_goi.main(["--out-dir", self.tmp], goi=self.goi_gia)
        self.assertEqual([], self.calls)

    def test_goi_ghi_tho_meta_khong_ghi_khoa_va_bo_qua_file_da_co(self):
        os.environ["GEMINI_API_KEY"] = "KHOA-GIA-0123456789"
        baseline_goi.main(["--out-dir", self.tmp, "--so-de", "2"], goi=self.goi_gia)
        self.assertEqual(4, len(self.calls))
        self.assertEqual(["plain", "plain", "json", "json"], [c[2] for c in self.calls])
        with open(os.path.join(self.tmp, "baseline_2-d2.meta.json"), encoding="utf-8") as f:
            meta = json.load(f)
        self.assertEqual("gemini-3.5-flash-lite", meta["model"])
        for name in os.listdir(self.tmp):
            with open(os.path.join(self.tmp, name), encoding="utf-8") as f:
                self.assertNotIn("KHOA-GIA", f.read(), name)
        baseline_goi.main(["--out-dir", self.tmp, "--so-de", "2"], goi=self.goi_gia)
        self.assertEqual(4, len(self.calls))  # đã có file thô: không gọi lại


if __name__ == "__main__":
    unittest.main()
