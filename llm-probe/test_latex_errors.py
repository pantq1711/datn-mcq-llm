#!/usr/bin/env python3
"""Kiểm thử cho latex_errors: xuống dòng trong $...$, lệnh LaTeX ngoài $...$, và trường hợp hợp lệ."""
import json
import unittest

from llm_formula_probe import latex_errors


class LatexErrorsTest(unittest.TestCase):
    def test_unescaped_neq_in_json_creates_newline_inside_math_and_fails(self):
        raw_json = '{"question_text": "Điều kiện $x \\neq 0$"}'
        obj = json.loads(raw_json)
        errs = latex_errors(obj["question_text"])
        self.assertTrue(
            any("xuống dòng bên trong $...$" in e for e in errs),
            f"Phải bắt được lỗi \\n bên trong $...$, nhưng nhận được: {errs}",
        )

    def test_newline_outside_math_is_valid(self):
        raw_json = '{"explanation": "Bước 1: $x \\\\neq 0$.\\nBước 2: $x = 2$."}'
        obj = json.loads(raw_json)
        errs = latex_errors(obj["explanation"])
        self.assertEqual(errs, [], f"Xuống dòng ngoài $...$ phải hợp lệ, nhưng nhận được: {errs}")

    def test_latex_command_outside_math_is_reported_as_error(self):
        errs = latex_errors("A = \\frac{1}{x}")
        self.assertTrue(
            any("lệnh LaTeX nằm ngoài cặp $...$" in e for e in errs),
            f"Phải bắt được lỗi \\frac ngoài $...$, nhưng nhận được: {errs}",
        )

    def test_escaped_dollar_outside_math_is_valid(self):
        errs = latex_errors("Giá 5 \\$ và biểu thức $\\frac{1}{x}$")
        self.assertEqual(errs, [], f"Dấu \\$ ngoài công thức phải hợp lệ, nhưng nhận được: {errs}")


if __name__ == "__main__":
    unittest.main()
