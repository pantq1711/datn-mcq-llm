import json
import os
import unittest

import _duong_dan  # noqa: F401
from do_luong import co_qc


class CoQcParityTest(unittest.TestCase):
    """Cùng fixture với back_end QcFlagParityTest: Python phải ra đúng lý do và cờ như trang đề."""

    def test_fixture_chung(self):
        with open(os.path.join(_duong_dan.FIXTURES, "co-qc-parity.json"), encoding="utf-8") as f:
            cases = json.load(f)["cases"]
        self.assertGreaterEqual(len(cases), 15)
        for c in cases:
            with self.subTest(c["ten"]):
                ly_do = co_qc.review_reasons(c["qc"])
                self.assertEqual(c["ly_do"], ly_do)
                self.assertEqual(c["co"], co_qc.has_content_flag(ly_do))

    def test_similar_in_exam_khong_tinh_la_co_noi_dung(self):
        self.assertFalse(co_qc.has_content_flag(["similar_in_exam: I.3"]))
        self.assertTrue(co_qc.has_content_flag(["similar_in_exam: I.3", "solver_inconsistent"]))
        self.assertFalse(co_qc.has_content_flag([]))

    def test_buoc_cua_ly_do(self):
        self.assertEqual("solver", co_qc.buoc_cua("solver_multiple_correct"))
        self.assertEqual("solver", co_qc.buoc_cua("statement_undecidable"))
        self.assertEqual("dedup", co_qc.buoc_cua("duplicate: 7"))
        self.assertEqual("dedup", co_qc.buoc_cua("dedup_not_run"))
        self.assertEqual("grounding", co_qc.buoc_cua("grounding_failed"))
        self.assertEqual("format", co_qc.buoc_cua("warning: x: lạ"))
        self.assertEqual("format", co_qc.buoc_cua("explanation_draft_suspect"))


if __name__ == "__main__":
    unittest.main()
