"""Định dạng câu thống nhất cho bộ chấm mù (câu hệ thống và câu baseline dùng chung) và cách dựng Markdown để hiển thị.

Một câu (dict, một dòng JSONL):
  id            mã nội bộ, chỉ nằm trong file khóa: "sys-<generated_question.id>" hoặc "<nguon>-d<đề>-c<số>"
  nguon         he_thong | baseline_1 | baseline_2
  dang          mcq | tf | sa
  de            đề bài (LaTeX trong $...$)
  phuong_an     mcq: {"A": ..., "B": ..., "C": ..., "D": ...}; dạng khác: None
  y             tf: [{"vi_tri": "a", "noi_dung": ...}, ...]; dạng khác: None
  dap_an        mcq: "A"; tf: {"a": "Đ", "b": "S", ...}; sa: chuỗi đáp số
  loi_giai      giữ để tra cứu, KHÔNG hiển thị trên phiếu (Q3: bỏ lời giải ở mọi câu)
  khoi, chu_de, bai, muc_do (mcq/sa: easy|medium|hard; tf: danh sách 4 mức theo ý)
  qc            chỉ câu hệ thống: {"co": bool, "ly_do": [...], "buoc": [...]} (đi vào file khóa, không vào phiếu)
  prompt_version, ma_tran_mau, da_hien_cho_gv, de_thi_id: siêu dữ liệu cho file khóa
"""
import json
import re

from . import co_qc

DANG = ("mcq", "tf", "sa")
NGUON = ("he_thong", "baseline_1", "baseline_2")
MUC_DO_VI = {"easy": "Nhận biết", "medium": "Thông hiểu", "hard": "Vận dụng"}
NHAN_TF = {"T": "Đ", "F": "S"}


def doc_jsonl(path):
    rows = []
    with open(path, encoding="utf-8-sig") as f:
        for i, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError as e:
                raise ValueError(f"{path}: dòng {i} không phải JSON: {e}") from None
    return rows


def ghi_jsonl(path, rows):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")


def tu_dong_xuat(row):
    """Một dòng của sql/d2-xuat-cau.sql -> câu thống nhất (kèm cờ QC tính bằng co_qc)."""
    dang = row["dang"]
    ly_do = co_qc.review_reasons(row.get("qc") or {})
    cau = {
        "id": f"sys-{row['id']}",
        "nguon": "he_thong",
        "dang": dang,
        "de": row.get("de") or "",
        "phuong_an": row.get("phuong_an") if dang == "mcq" else None,
        "y": None,
        "dap_an": None,
        "loi_giai": row.get("loi_giai"),
        "khoi": row.get("khoi"),
        "chu_de": row.get("chu_de"),
        "bai": row.get("bai"),
        # mức ma trận yêu cầu (ô ma trận); baseline lấy cùng nguồn này nên phiếu không lộ nguồn qua cách ghi mức độ
        "muc_do": row.get("muc_do_o") or row.get("muc_do"),
        "qc": {"co": co_qc.has_content_flag(ly_do), "ly_do": ly_do,
               "buoc": sorted({co_qc.buoc_cua(r) for r in ly_do})},
        "prompt_version": row.get("prompt_version"),
        "ma_tran_mau": row.get("ma_tran_mau"),
        "da_hien_cho_gv": row.get("da_hien_cho_gv"),
        "de_thi_id": row.get("de_thi_id"),
    }
    if dang == "mcq":
        cau["dap_an"] = row.get("dap_an_mcq")
    elif dang == "sa":
        cau["dap_an"] = row.get("dap_an_sa")
    else:
        ys = sorted(row.get("y") or [], key=lambda s: s["vi_tri"])
        cau["y"] = [{"vi_tri": s["vi_tri"], "noi_dung": s.get("noi_dung") or ""} for s in ys]
        cau["dap_an"] = {s["vi_tri"]: NHAN_TF.get(s.get("nhan"), "?") for s in ys}
        cau["muc_do"] = [s.get("muc_do") for s in ys]
    return cau


def kiem_tra(cau):
    """Danh sách lỗi cấu trúc (rỗng = hợp lệ) của một câu thống nhất."""
    loi = []
    if cau.get("dang") not in DANG:
        loi.append(f"dang không hợp lệ: {cau.get('dang')}")
        return loi
    if not (cau.get("de") or "").strip():
        loi.append("thiếu đề bài")
    if cau["dang"] == "mcq":
        pa = cau.get("phuong_an") or {}
        for k in "ABCD":
            if not (pa.get(k) or "").strip():
                loi.append(f"thiếu phương án {k}")
        if cau.get("dap_an") not in tuple("ABCD"):
            loi.append(f"đáp án mcq không hợp lệ: {cau.get('dap_an')}")
    elif cau["dang"] == "tf":
        ys = cau.get("y") or []
        if [y.get("vi_tri") for y in ys] != list("abcd") or any(not (y.get("noi_dung") or "").strip() for y in ys):
            loi.append("câu đúng/sai phải có đủ 4 ý a-d")
        da = cau.get("dap_an") or {}
        if sorted(da) != list("abcd") or any(v not in ("Đ", "S") for v in da.values()):
            loi.append(f"đáp án đúng/sai không hợp lệ: {da}")
    else:
        if not str(cau.get("dap_an") or "").strip():
            loi.append("thiếu đáp số")
    return loi


# ------------------------------------------------------------------ Markdown (cùng cách thoát với ExamMarkdownBuilder)

_ESCAPED = "\\*_[]<>#`~^|@"


def chuan_hoa_cong_thuc(text):
    """Đưa mọi kiểu đánh dấu công thức về $...$ như dữ liệu đã lưu của app: $$..$$, \\(..\\), \\[..\\]."""
    if text is None:
        return ""
    s = re.sub(r"\$\$(.+?)\$\$", lambda m: "$" + m.group(1) + "$", text, flags=re.DOTALL)
    s = re.sub(r"\\\((.+?)\\\)", lambda m: "$" + m.group(1) + "$", s, flags=re.DOTALL)
    s = re.sub(r"\\\[(.+?)\\\]", lambda m: "$" + m.group(1) + "$", s, flags=re.DOTALL)
    return s


def _closing_dollar(text, start):
    i = start
    while i < len(text):
        if text[i] == "\\" and i + 1 < len(text):
            i += 2
            continue
        if text[i] == "$":
            return i
        i += 1
    return -1


def inline(text):
    """Một đoạn Markdown: giữ $...$ (xuống dòng trong công thức thành khoảng trắng), thoát ký tự Markdown ngoài công thức.
    Bản chép ExamMarkdownBuilder.inline/pieces."""
    text = chuan_hoa_cong_thuc(text)
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == "\\" and i + 1 < n and text[i + 1] == "$":
            out.append("\\$")
            i += 2
            continue
        if c == "$":
            close = _closing_dollar(text, i + 1)
            inner = text[i + 1:close].strip() if close >= 0 else ""
            if not inner:
                out.append("\\$")
                i += 1
            else:
                out.append("$" + re.sub(r"\s*\n\s*", " ", inner) + "$")
                i = close + 1
            continue
        if c in _ESCAPED:
            out.append("\\")
        out.append(c)
        i += 1
    return "".join(out)


def doan(text):
    """Mỗi dòng không trống thành một đoạn Markdown."""
    text = chuan_hoa_cong_thuc(text or "")
    # Không cắt dòng bên trong công thức: gộp xuống dòng trong $...$ trước.
    text = re.sub(r"\$[^$]+\$", lambda m: re.sub(r"\s*\n\s*", " ", m.group(0)), text)
    return [inline(line.strip()) for line in text.splitlines() if line.strip()]


def mot_dong(text):
    return " ".join(doan(text))


def nhan_muc_do(muc_do):
    if isinstance(muc_do, list):
        return ", ".join(f"{k}) {MUC_DO_VI.get(m, '—')}" for k, m in zip("abcd", muc_do))
    return MUC_DO_VI.get(muc_do, "—")


def markdown_cau(stt, ma, cau):
    """Khối Markdown của một câu trên phiếu: CHỈ đề, phương án/ý, đáp án và yêu cầu ma trận (khối, chủ đề, mức độ).
    Không có lời giải, nguồn, cờ, phiên bản prompt (Q3)."""
    blocks = [f"## Câu {stt} — Mã {ma}"]
    meta = f"*Khối {cau.get('khoi') or '—'} · Chủ đề: {inline(cau.get('chu_de') or '—')}"
    if cau.get("bai"):
        meta += f" · Nội dung: {inline(cau['bai'])}"
    meta += f" · Mức độ yêu cầu: {nhan_muc_do(cau.get('muc_do'))}*"
    blocks.append(meta)
    blocks.extend(doan(cau.get("de")))
    if cau["dang"] == "mcq":
        blocks.append("\n\n".join(f"**{k}.** {mot_dong(cau['phuong_an'].get(k))}" for k in "ABCD"))
        blocks.append(f"**Đáp án:** {cau['dap_an']}")
    elif cau["dang"] == "tf":
        blocks.append("\n\n".join(f"**{y['vi_tri']})** {mot_dong(y['noi_dung'])}" for y in cau["y"]))
        da = cau["dap_an"]
        blocks.append("**Đáp án:** " + "; ".join(f"{k}) {'Đúng' if da.get(k) == 'Đ' else 'Sai'}" for k in "abcd"))
    else:
        blocks.append(f"**Đáp số:** {mot_dong(str(cau['dap_an']))}")
    return "\n\n".join(blocks)
