#!/usr/bin/env python3
"""
Chấm lại TẤT CẢ phản hồi thô đã lưu bằng bộ kiểm tra hiện tại trong llm_formula_probe.py.
Không gọi API. Chạy trong thư mục chứa các out_*/ (ví dụ llm-probe/m35_lite):
    python ../recheck.py
"""
import glob
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import llm_formula_probe as p  # noqa: E402

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def wilson(k, n, z=1.959964):
    if n == 0:
        return (0.0, 0.0)
    ph = k / n
    d = 1 + z * z / n
    c = (ph + z * z / (2 * n)) / d
    h = z * math.sqrt(ph * (1 - ph) / n + z * z / (4 * n * n)) / d
    return (max(0.0, 100 * (c - h)), min(100.0, 100 * (c + h)))


def fmt(k, n):
    lo, hi = wilson(k, n)
    return f"{k}/{n} ({100 * k / n:.0f}%, Wilson 95% [{lo:.1f}, {hi:.1f}])" if n else "0/0"


dirs = sorted(
    d for d in glob.glob("out_*")
    if os.path.isdir(d) and glob.glob(os.path.join(d, "raw_*.txt"))
)
if not dirs:
    sys.exit("Không thấy thư mục out_*/ ở đây. Hãy cd vào thư mục chứa chúng (ví dụ llm-probe/m35_lite).")

for d in dirs:
    files = sorted(glob.glob(os.path.join(d, "raw_*.txt")))
    n = len(files)
    counts = {k: 0 for k in ["json_ok", "fields_ok", "latex_ok", "control_char", "fence", "alt_delim", "tat_ca_dat"]}
    bad = []
    for f in files:
        raw = open(f, encoding="utf-8").read()
        r = p.analyze(raw)
        for k in ["json_ok", "fields_ok", "latex_ok", "control_char", "fence", "alt_delim"]:
            counts[k] += 1 if r.get(k) else 0
        if r.get("json_ok") and r.get("fields_ok") and r.get("latex_ok"):
            counts["tat_ca_dat"] += 1
        else:
            bad.append((os.path.basename(f), r.get("notes", [])[:2]))
    print(f"\n== {d} (n={n})")
    for k, label in [("json_ok", "parse được JSON"), ("fields_ok", "đủ 7 trường, đáp án A-D"),
                     ("latex_ok", "LaTeX đạt"), ("tat_ca_dat", "đạt cả ba"),
                     ("control_char", "có ký tự điều khiển"), ("fence", "bọc ```"),
                     ("alt_delim", "dùng \\( \\) hoặc \\[ \\]")]:
        print(f"  {label:26s}: {fmt(counts[k], n)}")
    for name, notes in bad:
        print(f"    - không đạt: {name} {notes}")
