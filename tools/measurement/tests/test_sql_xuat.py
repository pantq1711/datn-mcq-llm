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


def phien_ban_sql():
    m = re.search(r"\\set prompt_versions '([^']+)'", doc(SQL))
    return sorted(m.group(1).split(","))


class SqlPhienBanTest(unittest.TestCase):
    def test_mac_dinh_sql_trung_phien_ban_main(self):
        java = phien_ban_java()
        self.assertEqual({"mcq", "tf", "sa"}, set(java))
        self.assertEqual(sorted(java.values()), phien_ban_sql())


if __name__ == "__main__":
    unittest.main()
