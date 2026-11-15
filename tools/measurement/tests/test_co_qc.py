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

    def test_co_chinh_loai_co_trung_va_co_ha_tang(self):
        """L10: cờ chính = phán đoán của bước Solver / format / grounding; cờ trùng và cờ hạ tầng báo riêng."""
        for ly_do in (["duplicate: 6"], ["solver_pending"], ["solver_failed"], ["solver_not_run"], ["dedup_not_run"],
                      ["grounding_pending"], ["grounding_failed"], ["duplicate: 6", "dedup_not_run"], []):
            with self.subTest(ly_do):
                self.assertFalse(co_qc.co_chinh(ly_do))
                self.assertEqual([], co_qc.ly_do_chinh(ly_do))
        for ly_do in (["solver_inconsistent"], ["solver_no_correct"], ["solver_multiple_correct"], ["solver_ambiguous"],
                      ["solver_unparseable"], ["statement_undecidable"], ["warning: label: 4 ý cùng nhãn T"],
                      ["explanation_draft_suspect"], ["grounding_not_supported"], ["grounding_unknown"]):
            with self.subTest(ly_do):
                self.assertTrue(co_qc.co_chinh(ly_do))
        self.assertEqual(["statement_undecidable"], co_qc.ly_do_chinh(["solver_failed", "statement_undecidable",
                                                                      "dedup_not_run"]))


if __name__ == "__main__":
    unittest.main()
