"""L1: phiên bản prompt mặc định của sql/d2-xuat-cau.sql phải trùng phiên bản main đang sinh (MatrixPromptDraftV0)."""
import os
import re
import unittest

import _duong_dan

SQL = os.path.join(_duong_dan.GOC, "sql", "d2-xuat-cau.sql")
JAVA = os.path.normpath(os.path.join(_duong_dan.GOC, "..", "..", "back_end", "src", "main", "java", "com", "datn", "mcq",
                                     "llm", "prompt", "MatrixPromptDraftV0.java"))


def doc(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def phien_ban_java():
    """{"mcq": "matrix-mcq-v9-draft", ...} từ switch của MatrixPromptDraftV0.promptVersion."""
    than = re.search(r"public static String promptVersion\(QuestionType type\)\s*\{(.*?)\n    \}", doc(JAVA), re.DOTALL)
    out = {}
    for dang, hau_to in re.findall(r"case (MCQ|TF|SA) -> \"([^\"]+)\"", than.group(1)):
        out[dang.lower()] = "matrix-" + dang.lower() + hau_to
    return out


def phien_ban_sql(path=SQL):
    m = re.search(r"\\set prompt_versions '([^']+)'", doc(path))
    return sorted(m.group(1).split(","))


SQL_TRUOC_SAU = os.path.join(_duong_dan.GOC, "sql", "d2-truoc-sau.sql")


class SqlPhienBanTest(unittest.TestCase):
    def test_mac_dinh_sql_trung_phien_ban_main(self):
        java = phien_ban_java()
        self.assertEqual({"mcq", "tf", "sa"}, set(java))
        self.assertEqual(sorted(java.values()), phien_ban_sql())

    def test_sql_truoc_sau_cung_phien_ban_va_cung_pham_vi(self):
        """Bảng mục 6: số qua format phải cùng tập câu với d2-xuat-cau.sql (cùng phiên bản, cùng điều kiện câu LLM gốc)."""
        self.assertEqual(phien_ban_sql(), phien_ban_sql(SQL_TRUOC_SAU))
        sql = doc(SQL_TRUOC_SAU)
        for dieu_kien in ("g.origin = 'llm'", "g.edited_from_question_id IS NULL", "e.mode = 'teacher'",
                          "j.job_type = 'generate'"):
            self.assertIn(dieu_kien, sql)
        self.assertNotIn("\\", sql.split("\\endif", 1)[1])  # không có "\" trong phần câu SELECT


if __name__ == "__main__":
    unittest.main()
