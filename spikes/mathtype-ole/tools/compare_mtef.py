"""So sánh cấu trúc MTEF giữa file tham chiếu (MathType sinh) và file spike, ghép theo đoạn văn thứ k
(mỗi đoạn không rỗng = một biểu thức). Bỏ qua COLOR/COLOR_DEF, FUTURE, header, các record định nghĩa.

Dùng: python -I compare_mtef.py <expected.docx> <actual.docx> [<exprs.tex.txt>]
"""
import difflib
import os
import re
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mtef_dump import dump_mtef, equation_native  # noqa: E402

SKIP = ("COLOR", "FUTURE", "MTEF v")


def per_paragraph(path):
    """Danh sách theo đoạn không rỗng: list cây MTEF (đã chuẩn hóa) hoặc None nếu đoạn không có đối tượng OLE."""
    with zipfile.ZipFile(path) as z:
        doc = z.read("word/document.xml").decode("utf-8")
        rels = z.read("word/_rels/document.xml.rels").decode("utf-8")
        target = {}
        for rel in re.findall(r"<Relationship [^>]*/>", rels):
            rid = re.search(r'Id="([^"]+)"', rel).group(1)
            target[rid] = "word/" + re.search(r'Target="([^"]+)"', rel).group(1)
        out = []
        for p in re.findall(r"<w:p[ >].*?</w:p>", doc, re.S):
            ole = re.search(r"<o:OLEObject [^>]*r:id=\"([^\"]+)\"", p)
            has_text = re.search(r"<w:t[ >][^<]*\S", p) or "<m:oMath" in p or ole
            if not has_text:
                continue
            if ole:
                lines = dump_mtef(equation_native(z.read(target[ole.group(1)]))).splitlines()
                out.append([l for l in lines if not l.strip().startswith(SKIP)])
            else:
                out.append(None)
        return out


def main(expected, actual, exprs_file=None):
    sys.stdout.reconfigure(encoding="utf-8")
    exprs = []
    if exprs_file:
        exprs = [l.strip() for l in open(exprs_file, encoding="utf-8") if l.strip()]
    exp, act = per_paragraph(expected), per_paragraph(actual)
    print(f"expected {len(exp)} đoạn, actual {len(act)} đoạn")
    stats = {"KHỚP": 0, "KHÁC": 0, "MathType không tạo": 0, "spike giữ OMML": 0}
    for i, (e, a) in enumerate(zip(exp, act), 1):
        label = exprs[i - 1] if i <= len(exprs) else ""
        if e is None and a is None:
            k = "MathType không tạo"
            print(f"#{i}: cả hai không có OLE  {label}")
        elif e is None:
            k = "MathType không tạo"
            print(f"#{i}: MathType Toggle TeX không tạo đối tượng (spike có)  {label}")
        elif a is None:
            k = "spike giữ OMML"
            print(f"#{i}: spike giữ OMML  {label}")
        elif e == a:
            k = "KHỚP"
            print(f"#{i}: KHỚP  {label}")
        else:
            k = "KHÁC"
            print(f"#{i}: KHÁC  {label}")
            for d in difflib.unified_diff(e, a, "MathType", "spike", lineterm="", n=1):
                if not d.startswith(("---", "+++")):
                    print("    " + d)
        stats[k] += 1
    print("Tổng:", stats)
    return 0 if stats["KHÁC"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:4]))
