"""Sinh phiếu cho người chấm (D2+D4) và phiếu gán nhãn cặp (D6).

Phiếu chấm câu:
  de-cham.docx       đề hiển thị công thức (Markdown -> pandoc -> Word, cùng định dạng Markdown với đường xuất của app)
  phieu-<ten>.xlsx   mỗi người chấm một bản, cùng thứ tự, có danh sách chọn sẵn (data validation)
Phiếu CHỈ có: thứ tự, mã ngẫu nhiên, đề, phương án/ý, đáp án, khối/chủ đề/mức độ yêu cầu. KHÔNG có lời giải, nguồn, cờ QC,
phiên bản prompt, id câu (test_phieu quét nội dung để giữ điều này).

Phiếu gán nhãn cặp (D6): cap-gan-nhan.docx và cap-<ten>.xlsx, KHÔNG có điểm cosine, tầng lấy mẫu hay dự đoán của dedup.

Chạy (đứng ở tools/measurement):
  python -m do_luong.phieu cham --bo-cham <dir>/bo-cham.jsonl --nguoi-cham Quan Dang --out-dir <dir>
  python -m do_luong.phieu cap --cap <dir>/cap-vang-noi-dung.jsonl --nguoi-gan An Quan --out-dir <dir>
"""
import argparse
import os
import shutil
import subprocess

from . import cau as cau_mod

PANDOC_FORMAT = "markdown+tex_math_dollars-raw_html-raw_tex"  # PandocRunner.MARKDOWN_FORMAT

TEN_DANG = {"mcq": "Trắc nghiệm 4 phương án", "tf": "Đúng/Sai", "sa": "Trả lời ngắn"}

# Danh sách chọn (giá trị phải khớp huong-dan-cham.md và phan_tich_d2)
DAP_AN_MCQ = ["Đúng", "Sai", "Nhiều đáp án đúng", "Không có đáp án đúng"]
DAP_AN_TF = ["Đúng", "Sai"]
DAP_AN_SA = ["Đúng", "Sai"]
MUC_DO = ["Đúng", "Không đúng", "Không rõ"]
CO_KHONG = ["Không", "Có"]
NHAN_CAP = ["Trùng", "Không trùng", "Không chắc"]

COT_PHIEU = ["Thứ tự", "Mã", "Dạng", "Đáp án", "Ý sai (Đ/S)", "Đúng mức độ", "Vượt lớp", "Mơ hồ", "Ghi chú"]
COT_CAP = ["Cặp", "Dạng", "Câu thứ nhất", "Câu thứ hai", "Nhãn", "Ghi chú"]
SHEET_CAP = "Gán nhãn cặp"      # phan_tich_d6.doc_nhan chỉ đọc sheet này
SHEET_LUYEN = "Luyện tập"       # đứng đầu phiếu, KHÔNG tính vào kết quả
COT_LUYEN = COT_CAP + ["Đáp án mẫu (xem sau khi gán)"]


def _pa(a, b, c, d):
    return {"A": a, "B": b, "C": c, "D": d}


# 3 cặp luyện lấy từ ví dụ trong docs/experiments/d6/huong-dan-gan-nhan.md (không phải cặp thật; số hiệu "L1".."L3").
# Quy tắc minh họa: diễn đạt lại cùng số liệu = Trùng; đổi số liệu = Không trùng; sa: cùng đề và cùng giá trị đáp số = Trùng.
CAP_LUYEN = [
    {"cap": "L1", "dang": "mcq", "mau": "Trùng",
     "cau_1": {"dang": "mcq", "de": "Kết quả của phép nhân $2x \\cdot 3x^2$ là",
               "phuong_an": _pa("$6x^3$", "$5x^3$", "$6x^2$", "$5x^2$")},
     "cau_2": {"dang": "mcq", "de": "Tích của $2x$ và $3x^2$ bằng",
               "phuong_an": _pa("$5x^2$", "$6x^2$", "$5x^3$", "$6x^3$")}},
    {"cap": "L2", "dang": "mcq", "mau": "Không trùng",
     "cau_1": {"dang": "mcq", "de": "Kết quả của phép nhân $2x \\cdot 3x^2$ là",
               "phuong_an": _pa("$6x^3$", "$5x^3$", "$6x^2$", "$5x^2$")},
     "cau_2": {"dang": "mcq", "de": "Kết quả của phép nhân $3x \\cdot 4x^2$ là",
               "phuong_an": _pa("$12x^3$", "$7x^3$", "$12x^2$", "$7x^2$")}},
    {"cap": "L3", "dang": "sa", "mau": "Trùng",
     "cau_1": {"dang": "sa", "de": "Tính giá trị biểu thức $A=2x-3$ tại $x=1{,}25$.", "dap_an": "-0,5"},
     "cau_2": {"dang": "sa", "de": "Giá trị của biểu thức $2x-3$ khi $x=1{,}25$ là bao nhiêu?",
               "dap_an": "$-\\frac{1}{2}$"}},
]


def markdown_de(danh_sach, tieu_de="BỘ CÂU CHẤM ĐỘC LẬP"):
    """danh_sach: (thu_tu, ma, cau) theo thứ tự phiếu."""
    parts = [f"# {tieu_de}",
             "Chấm theo docs/experiments/d2/huong-dan-cham.md. Ghi nhãn vào phiếu .xlsx theo Thứ tự và Mã. "
             "Không trao đổi với người chấm khác cho tới khi nộp phiếu."]
    for thu_tu, ma, c in danh_sach:
        parts.append(cau_mod.markdown_cau(thu_tu, ma, c))
        parts.append("* * *")
    return "\n\n".join(parts) + "\n"


def _text_mot_dong(c):
    """Câu dạng chữ thuần cho ô Excel của phiếu cặp (LaTeX để nguyên trong $...$)."""
    lines = [c.get("de") or ""]
    if c.get("dang") == "mcq":
        lines += [f"{k}. {(c.get('phuong_an') or {}).get(k) or ''}" for k in "ABCD"]
    elif c.get("dang") == "tf":
        lines += [f"{y['vi_tri']}) {y['noi_dung']}" for y in c.get("y") or []]
    elif c.get("dang") == "sa":
        lines.append(f"Đáp số: {c.get('dap_an') or ''}")
    return "\n".join(lines)


def _markdown_mot_cap(p):
    parts = [f"## Cặp {p['cap']} ({TEN_DANG.get(p['dang'], p['dang'])})"]
    for nhan, c in (("Câu thứ nhất", p["cau_1"]), ("Câu thứ hai", p["cau_2"])):
        parts.append(f"**{nhan}.**")
        parts.extend(cau_mod.doan(c.get("de")))
        if p["dang"] == "mcq":
            parts.append("\n\n".join(f"**{k}.** {cau_mod.mot_dong((c.get('phuong_an') or {}).get(k))}"
                                     for k in "ABCD"))
        elif p["dang"] == "tf":
            parts.append("\n\n".join(f"**{y['vi_tri']})** {cau_mod.mot_dong(y['noi_dung'])}"
                                     for y in c.get("y") or []))
        elif p["dang"] == "sa":
            parts.append(f"Đáp số: {cau_mod.mot_dong(str(c.get('dap_an') or ''))}")
    parts.append("* * *")
    return parts


def markdown_cap(cap_list, luyen=True):
    """Mục luyện tập (3 cặp mẫu, không tính vào kết quả) đứng đầu, rồi các cặp thật."""
    parts = []
    if luyen:
        parts += ["# PHẦN LUYỆN TẬP (KHÔNG TÍNH VÀO KẾT QUẢ)",
                  f"Ba cặp dưới đây chỉ để làm quen. Gán thử ở sheet '{SHEET_LUYEN}' của phiếu .xlsx, đối chiếu cột "
                  "'Đáp án mẫu', rồi mới gán các cặp thật."]
        for p in CAP_LUYEN:
            parts += _markdown_mot_cap(p)
    parts += ["# CẶP CÂU CẦN GÁN NHÃN TRÙNG",
              "Gán nhãn theo docs/experiments/d6/huong-dan-gan-nhan.md, ghi vào phiếu .xlsx theo số Cặp."]
    for p in cap_list:
        parts += _markdown_mot_cap(p)
    return "\n\n".join(parts) + "\n"


def pandoc_docx(markdown, out_path, pandoc="pandoc"):
    exe = shutil.which(pandoc)
    if not exe:
        raise RuntimeError("Không tìm thấy pandoc trên PATH")
    subprocess.run([exe, "-f", PANDOC_FORMAT, "-t", "docx", "-o", out_path],
                   input=markdown.encode("utf-8"), check=True)


def _xlsx(path, sheet_title, headers, rows, validations, widths, huong_dan):
    from openpyxl import Workbook
    from openpyxl.styles import Alignment, Font
    from openpyxl.worksheet.datavalidation import DataValidation

    wb = Workbook()
    ws = wb.active
    ws.title = sheet_title
    ws.append(headers)
    for cell in ws[1]:
        cell.font = Font(bold=True)
    for r in rows:
        ws.append(r)
    for col, width in widths.items():
        ws.column_dimensions[col].width = width
    for row in ws.iter_rows(min_row=2):
        for cell in row:
            cell.alignment = Alignment(wrap_text=True, vertical="top")
    ws.freeze_panes = "C2"
    dvs = {}
    for cell_ref, values in validations:
        key = tuple(values)
        if key not in dvs:
            dv = DataValidation(type="list", formula1='"' + ",".join(values) + '"', allow_blank=True,
                                showErrorMessage=True, errorTitle="Giá trị không hợp lệ",
                                error="Chọn một giá trị trong danh sách.")
            ws.add_data_validation(dv)
            dvs[key] = dv
        dvs[key].add(cell_ref)
    hd = wb.create_sheet("Hướng dẫn")
    for line in huong_dan:
        hd.append([line])
    hd.column_dimensions["A"].width = 120
    wb.save(path)


def xlsx_phieu_cham(path, danh_sach):
    rows, validations = [], []
    for i, (thu_tu, ma, c) in enumerate(danh_sach, start=2):
        rows.append([thu_tu, ma, TEN_DANG[c["dang"]], None, None, None, None, None, None])
        validations.append((f"D{i}", {"mcq": DAP_AN_MCQ, "tf": DAP_AN_TF, "sa": DAP_AN_SA}[c["dang"]]))
        validations.append((f"F{i}", MUC_DO))
        validations.append((f"G{i}", CO_KHONG))
        validations.append((f"H{i}", CO_KHONG))
    _xlsx(path, "Phiếu chấm", COT_PHIEU, rows, validations,
          {"A": 8, "B": 8, "C": 22, "D": 22, "E": 12, "F": 14, "G": 10, "H": 10, "I": 50},
          ["Chấm theo docs/experiments/d2/huong-dan-cham.md (định nghĩa từng nhãn và ví dụ).",
           "Mỗi dòng là một câu trong file de-cham.docx, khớp theo Thứ tự và Mã.",
           "Đáp án: chọn trong danh sách. Câu Đúng/Sai chọn 'Sai' khi có ít nhất một ý sai nhãn, rồi ghi các ý sai vào "
           "cột 'Ý sai (Đ/S)', ví dụ: b,d.",
           "Đúng mức độ, Vượt lớp, Mơ hồ: chọn trong danh sách. Ghi chú: tự do (nên ghi lý do khi chọn Sai / Có).",
           "Tự giải từng câu; không trao đổi với người chấm khác cho tới khi nộp phiếu."])


def xlsx_phieu_cap(path, cap_list):
    rows, validations = [], []
    for i, p in enumerate(cap_list, start=2):
        rows.append([p["cap"], TEN_DANG.get(p["dang"], p["dang"]), _text_mot_dong(p["cau_1"]),
                     _text_mot_dong(p["cau_2"]), None, None])
        validations.append((f"E{i}", NHAN_CAP))
    _xlsx(path, SHEET_CAP, COT_CAP, rows, validations,
          {"A": 7, "B": 18, "C": 60, "D": 60, "E": 14, "F": 40},
          ["Gán nhãn theo docs/experiments/d6/huong-dan-gan-nhan.md.",
           "Trùng = cùng nội dung VÀ cùng số liệu, chỉ khác cách diễn đạt/thứ tự phương án. Đổi số liệu = Không trùng.",
           "Công thức ở dạng LaTeX; bản dễ đọc nằm trong cap-gan-nhan.docx (cùng số Cặp).",
           f"Sheet '{SHEET_LUYEN}' (3 cặp mẫu L1-L3) đứng đầu: gán thử trước; không tính vào kết quả."])
    _them_sheet_luyen(path)


def _them_sheet_luyen(path):
    """Chèn sheet 'Luyện tập' làm sheet đầu (và sheet mở sẵn). phan_tich_d6 chỉ đọc sheet 'Gán nhãn cặp'."""
    from openpyxl import load_workbook
    from openpyxl.styles import Alignment, Font
    from openpyxl.worksheet.datavalidation import DataValidation

    wb = load_workbook(path)
    ws = wb.create_sheet(SHEET_LUYEN, 0)
    ws.append(COT_LUYEN)
    for cell in ws[1]:
        cell.font = Font(bold=True)
    for p in CAP_LUYEN:
        ws.append([p["cap"], TEN_DANG[p["dang"]], _text_mot_dong(p["cau_1"]), _text_mot_dong(p["cau_2"]),
                   None, None, p["mau"]])
    for col, width in {"A": 7, "B": 18, "C": 50, "D": 50, "E": 14, "F": 30, "G": 22}.items():
        ws.column_dimensions[col].width = width
    for row in ws.iter_rows(min_row=2):
        for cell in row:
            cell.alignment = Alignment(wrap_text=True, vertical="top")
    dv = DataValidation(type="list", formula1='"' + ",".join(NHAN_CAP) + '"', allow_blank=True,
                        showErrorMessage=True, errorTitle="Giá trị không hợp lệ",
                        error="Chọn một giá trị trong danh sách.")
    ws.add_data_validation(dv)
    dv.add(f"E2:E{1 + len(CAP_LUYEN)}")
    note_row = len(CAP_LUYEN) + 3
    ws.cell(row=note_row, column=1, value=(
        "3 cặp luyện này KHÔNG tính vào kết quả (không phải cặp thật). Gán thử rồi đối chiếu cột G. Quy tắc: diễn đạt "
        "lại cùng số liệu = Trùng; đổi số liệu = Không trùng. Xong sang sheet 'Gán nhãn cặp'."))
    ws.merge_cells(start_row=note_row, start_column=1, end_row=note_row, end_column=7)
    ws.cell(row=note_row, column=1).alignment = Alignment(wrap_text=True, vertical="top")
    ws.row_dimensions[note_row].height = 48
    wb.active = 0
    wb.save(path)


def ten_an_toan(ten):
    return "".join(ch for ch in ten if ch.isalnum() or ch in "-_") or "nguoi"


def main(argv=None):
    ap = argparse.ArgumentParser(description="Sinh phiếu chấm D2+D4 / phiếu gán nhãn cặp D6.")
    sub = ap.add_subparsers(dest="lenh", required=True)
    c = sub.add_parser("cham")
    c.add_argument("--bo-cham", required=True, help="bo-cham.jsonl do lay_mau ghi")
    c.add_argument("--nguoi-cham", nargs="+", required=True)
    c.add_argument("--out-dir", required=True)
    c.add_argument("--khong-docx", action="store_true", help="bỏ bước pandoc")
    p = sub.add_parser("cap")
    p.add_argument("--cap", required=True, help="cap-vang-noi-dung.jsonl do DedupGoldRunner ghi")
    p.add_argument("--nguoi-gan", nargs="+", required=True)
    p.add_argument("--out-dir", required=True)
    p.add_argument("--khong-docx", action="store_true")
    a = ap.parse_args(argv)
    os.makedirs(a.out_dir, exist_ok=True)
    if a.lenh == "cham":
        rows = sorted(cau_mod.doc_jsonl(a.bo_cham), key=lambda r: r["thu_tu"])
        ds = [(r["thu_tu"], r["ma"], r) for r in rows]
        if not a.khong_docx:
            pandoc_docx(markdown_de(ds), os.path.join(a.out_dir, "de-cham.docx"))
        for ten in a.nguoi_cham:
            xlsx_phieu_cham(os.path.join(a.out_dir, f"phieu-{ten_an_toan(ten)}.xlsx"), ds)
        print(f"Đã sinh phiếu cho {len(a.nguoi_cham)} người chấm, {len(ds)} câu, tại {a.out_dir}")
    else:
        caps = sorted(cau_mod.doc_jsonl(a.cap), key=lambda r: r["cap"])
        if not a.khong_docx:
            pandoc_docx(markdown_cap(caps), os.path.join(a.out_dir, "cap-gan-nhan.docx"))
        for ten in a.nguoi_gan:
            xlsx_phieu_cap(os.path.join(a.out_dir, f"cap-{ten_an_toan(ten)}.xlsx"), caps)
        print(f"Đã sinh phiếu gán nhãn {len(caps)} cặp cho {len(a.nguoi_gan)} người, tại {a.out_dir}")


if __name__ == "__main__":
    main()
