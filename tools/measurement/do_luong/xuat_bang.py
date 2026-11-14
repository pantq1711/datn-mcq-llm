"""Ghi bảng ra Markdown (dán thẳng vào chương 4) và CSV (UTF-8 có BOM để Excel đọc đúng tiếng Việt)."""
import csv
import math


def so(x, chu_so=3):
    if x is None or (isinstance(x, float) and math.isnan(x)):
        return "—"
    if isinstance(x, float):
        if math.isinf(x):
            return "∞"
        return f"{x:.{chu_so}f}".replace(".", ",")
    return str(x)


def phan_tram(p, lo=None, hi=None):
    """"42,1% [30,2–55,0]" theo cách viết số tiếng Việt."""
    if p is None:
        return "—"
    s = f"{100 * p:.1f}%".replace(".", ",")
    if lo is not None and hi is not None:
        s += f" [{100 * lo:.1f}–{100 * hi:.1f}]".replace(".", ",")
    return s


def markdown(headers, rows):
    def cell(v):
        return str(v).replace("|", "\\|").replace("\n", " ")
    out = ["| " + " | ".join(headers) + " |", "|" + "|".join("---" for _ in headers) + "|"]
    out += ["| " + " | ".join(cell(v) for v in r) + " |" for r in rows]
    return "\n".join(out)


def ghi_csv(path, headers, rows):
    with open(path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(headers)
        w.writerows(rows)
