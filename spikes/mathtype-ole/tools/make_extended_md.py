"""Sinh samples/KIEM_TRA_mo_rong.md từ thcs50.tex.txt (bỏ 10 câu đầu) + chars68.tex.txt + phần C viết tay.

Dùng: python -I make_extended_md.py <thư mục samples>
"""
import os
import sys

PART_C = [
    r"**Phần C. Trường hợp đặc biệt**",
    r"",
    r"C1. Chữ tiếng Việt trong công thức: $\text{Vậy } x=2$ và $S=12\text{ cm}^2$ và $\text{Số học sinh}=35$.",
    r"",
    r"C2. Công thức hiển thị riêng dòng (căn giữa):",
    r"",
    r"$$x_{1,2}=\frac{-b\pm\sqrt{\Delta}}{2a}$$",
    r"",
    r"C3. Phương án trắc nghiệm trên một dòng: A. $x=1$  B. $x=-1$  C. $x=\frac{1}{2}$  D. $x=2$",
    r"",
    r"C4. Công thức chưa hỗ trợ, cố ý giữ dạng Equation của Word: $\lfloor x\rfloor$.",
    r"",
    r"C5. Tích phân có lũy thừa trong thân: $\int_0^1 x^2\,dx$.",
    r"",
]


def main(samples):
    def read(name):
        with open(os.path.join(samples, name), encoding="utf-8") as f:
            return [l.strip() for l in f if l.strip()]

    lines = [r"**Spike: công thức MathType (OLE), bộ mở rộng**", "",
             r"Phần A, B: mỗi dòng một công thức. Phần C: các trường hợp đặc biệt.", "",
             r"**Phần A. 40 biểu thức Toán THCS**", ""]
    for i, t in enumerate(read("thcs50.tex.txt")[10:], 11):
        lines += [f"A{i}. ${t}$", ""]
    lines += [r"**Phần B. Ký hiệu và cấu trúc**", ""]
    for i, t in enumerate(read("chars68.tex.txt"), 1):
        lines += [f"B{i}. ${t}$", ""]
    lines += PART_C
    with open(os.path.join(samples, "KIEM_TRA_mo_rong.md"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))
    bad = [l for l in lines if any(ord(c) < 32 for c in l)]
    print(f"{len(lines)} dòng, dòng có ký tự điều khiển: {len(bad)}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main(sys.argv[1])
