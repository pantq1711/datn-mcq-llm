"""Liệt kê LaTeX của mọi công thức theo đúng thứ tự pandoc thấy (pandoc -t json), mỗi dòng một công thức.

Cùng bộ phân tích với lệnh xuất docx nên thứ tự khớp m:oMath trong docx; không tự dò $...$ bằng regex.
Dùng: python -I pandoc_math_list.py <in.md> <out.txt>
"""
import json
import subprocess
import sys


def walk(node, out):
    if isinstance(node, dict):
        if node.get("t") == "Math":
            kind, tex = node["c"]
            out.append(" ".join(tex.split()))  # một dòng / công thức
            return
        for v in node.values():
            walk(v, out)
    elif isinstance(node, list):
        for v in node:
            walk(v, out)


def main(md, txt):
    ast = json.loads(subprocess.run(["pandoc", md, "-f", "markdown+tex_math_dollars", "-t", "json"],
                                    check=True, capture_output=True).stdout.decode("utf-8"))
    out = []
    walk(ast["blocks"], out)
    with open(txt, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")
    print(f"{len(out)} công thức -> {txt}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main(sys.argv[1], sys.argv[2])
