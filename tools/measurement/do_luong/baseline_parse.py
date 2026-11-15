"""D4: parser chịu lỗi đưa đầu ra thô của Gemini (baseline_goi) về định dạng câu thống nhất (cau.py).

Hai kiểu đầu vào:
  - JSON (prompt 2): mảng câu; chịu được rào ```json, chữ thừa trước/sau, dấu phẩy thừa, "\\frac" chỉ có một "\\".
  - Chữ tự do (prompt 1): "PHẦN I/II/III", "Câu N", phương án "A." / "A)", ý "a)", đáp án ngay sau câu ("Đáp án: B") hoặc
    ở phần "ĐÁP ÁN" cuối đề (dòng "Câu N: ...", "1.A 2.B", bảng số/chữ).
Câu được gán khối/chủ đề/nội dung/mức độ theo thứ tự trong ma trận (câu mcq thứ k -> ô mcq thứ k).
Câu thiếu phần nào thì KHÔNG bỏ im lặng: ghi vào parse-loi.jsonl kèm lý do và khối chữ gốc, để An sửa tay phần ĐỊNH DẠNG
rồi đưa vào --sua-tay (cùng id thì bản sửa tay thay bản parse). Mỗi câu sửa tay có một dòng trong sua-tay-diff.jsonl
(lý do lỗi gốc + các trường đã đổi, trước/sau) để báo cáo và phân tích nhạy (L6c).
--ma-tran phải là đúng file đã truyền cho baseline_goi; meta của file thô có dấu vân tay ma trận khác thì dừng, không ghi.

Chạy (đứng ở tools/measurement):
  python -m do_luong.baseline_parse --tho-dir <dir>/baseline-tho --out-dir <dir> --ma-tran <ma-tran.json> \
      [--sua-tay <dir>/baseline-sua-tay.jsonl]
Ra: <dir>/baseline_1.jsonl, <dir>/baseline_2.jsonl, <dir>/parse-loi.jsonl [, <dir>/sua-tay-diff.jsonl]
"""
import argparse
import glob
import json
import os
import re

from . import cau as cau_mod
from . import ma_tran

# Lệnh LaTeX bắt đầu bằng b, f, n, r, t (trùng ký tự thoát JSON \b \f \n \r \t): gặp "\" + các chữ này thì coi là LaTeX.
_LATEX_BFNRT = {
    "frac", "dfrac", "tfrac", "forall", "flat", "beta", "bar", "binom", "boxed", "bot", "big", "bigl", "bigr", "Big",
    "bigg", "begin", "because", "bullet", "neq", "ne", "nabla", "neg", "not", "notin", "ni", "nu", "nmid", "nleq",
    "ngeq", "rho", "right", "rangle", "rceil", "rfloor", "rightarrow", "times", "tan", "text", "textbf", "textit",
    "theta", "tau", "triangle", "to", "top", "therefore", "tilde", "tag", "rm", "bf", "frown", "nexists", "newline",
}


def sua_json(raw):
    """Chuẩn bị chuỗi JSON của model: bỏ rào, cắt đúng khối [...] / {...}, nhân đôi "\\" của lệnh LaTeX, bỏ dấu phẩy thừa."""
    s = raw.strip()
    s = re.sub(r"^```(?:json)?\s*", "", s)
    s = re.sub(r"\s*```\s*$", "", s)
    starts = [i for i in (s.find("["), s.find("{")) if i >= 0]
    if starts:
        s = s[min(starts):]
        end = max(s.rfind("]"), s.rfind("}"))
        s = s[: end + 1]
    out, i, in_str = [], 0, False
    while i < len(s):
        c = s[i]
        if c == '"':
            in_str = not in_str
        if not in_str and c == ",":
            m = re.match(r",\s*[\]}]", s[i:])
            if m:  # dấu phẩy thừa trước ] hoặc } (chỉ ngoài chuỗi: "{,}" trong LaTeX giữ nguyên)
                i += 1
                continue
        if c != "\\":
            out.append(c)
            i += 1
            continue
        if not in_str:
            out.append(c)
            i += 1
            continue
        nxt = s[i + 1] if i + 1 < len(s) else ""
        if nxt == "\\" or nxt in "\"/":
            out.append(s[i:i + 2])
            i += 2
            continue
        if nxt == "u" and re.fullmatch(r"[0-9a-fA-F]{4}", s[i + 2:i + 6] or ""):
            out.append(s[i:i + 6])
            i += 6
            continue
        word = re.match(r"[A-Za-z]+", s[i + 1:])
        word = word.group(0) if word else ""
        if nxt in "bfnrt" and word not in _LATEX_BFNRT:
            out.append(s[i:i + 2])  # ký tự thoát JSON thật (\n xuống dòng ...)
            i += 2
            continue
        out.append("\\\\")  # LaTeX hoặc ký tự thoát không hợp lệ: giữ nguyên "\" trong chuỗi
        i += 1
    return "".join(out)


def _nhan_tf(v):
    v = (str(v or "")).strip().lower()
    if v in ("đ", "đúng", "t", "true", "d", "dung", "1"):
        return "Đ"
    if v in ("s", "sai", "f", "false", "0"):
        return "S"
    return None


def _dap_an_sa(v):
    s = str(v or "").strip()
    s = re.sub(r"^(đáp số|đáp án|kết quả)\s*[:：]?\s*", "", s, flags=re.IGNORECASE)
    s = re.split(r"\s+\(", s, maxsplit=1)[0]  # "12 (thay vào)." -> "12": bỏ phần giải thích trong ngoặc phía sau
    s = s.strip().strip("$").strip().rstrip(".")
    return s


def tu_json(raw, nguon, de, mt=None):
    """Trả (câu, lỗi). Mỗi lỗi: {"id", "loi": [...], "cau": ..., "goc": ...}. mt: ma trận (mặc định ma trận mẫu)."""
    try:
        data = json.loads(sua_json(raw))
    except json.JSONDecodeError as e:
        return [], [{"id": f"{nguon}-d{de}", "loai": LOAI_DINH_DANG, "loi": [f"JSON không đọc được: {e}"], "cau": None,
                     "goc": raw[:4000]}]
    if isinstance(data, dict):
        data = next((v for v in data.values() if isinstance(v, list)), [data])
    items = []
    for i, it in enumerate(data, 1):
        if not isinstance(it, dict):
            items.append(({"stt": i, "dang": None}, f"phần tử {i} không phải đối tượng"))
            continue
        dang = str(it.get("dang") or "").strip().lower() or None
        c = {"stt": it.get("cau") or i, "dang": dang, "de": it.get("de") or it.get("question_text") or "",
             "loi_giai": it.get("loi_giai") or it.get("explanation")}
        if dang == "mcq":
            pa = it.get("phuong_an") or {}
            c["phuong_an"] = {k: (pa.get(k) or pa.get(k.lower()) or "") for k in "ABCD"}
            c["dap_an"] = str(it.get("dap_an") or "").strip().upper()[:1] or None
        elif dang == "tf":
            ys = it.get("y") or []
            c["y"] = [{"vi_tri": str(y.get("vi_tri") or "abcd"[j]).strip().lower()[:1],
                       "noi_dung": y.get("noi_dung") or ""} for j, y in enumerate(ys) if isinstance(y, dict)]
            c["dap_an"] = {str(y.get("vi_tri") or "abcd"[j]).strip().lower()[:1]: _nhan_tf(y.get("nhan"))
                           for j, y in enumerate(ys) if isinstance(y, dict)}
        elif dang == "sa":
            c["dap_an"] = _dap_an_sa(it.get("dap_an"))
        items.append((c, json.dumps(it, ensure_ascii=False)))
    return _hoan_thien(items, nguon, de, mt)


# ------------------------------------------------------------------ chữ tự do

_RE_PHAN = re.compile(r"^PH[ẦA]N\s+(III|II|I|3|2|1)\b(.*)$", re.IGNORECASE)
_RE_CAU = re.compile(r"^C[âa]u\s*(\d+)\s*[.:)\-–]?\s*(.*)$", re.IGNORECASE)
_RE_PA = re.compile(r"^([A-D])\s*[.)]\s*(.*)$")
_RE_Y = re.compile(r"^([a-d])\s*[.)]\s*(.*)$")
_RE_DAP_AN_DONG = re.compile(r"^(đáp án|đáp số|chọn|trả lời)\s*(?:đúng)?\s*[:：]\s*(.*)$", re.IGNORECASE)
_RE_LOI_GIAI = re.compile(r"^(lời giải|hướng dẫn giải|giải)\s*[:：]?\s*(.*)$", re.IGNORECASE)
_RE_TIEU_DE_DAP_AN = re.compile(r"^(đáp án|hướng dẫn chấm|hướng dẫn giải|đáp án và hướng dẫn|bảng đáp án|lời giải)\b",
                                re.IGNORECASE)
_PHAN_SO = {"I": "I", "1": "I", "II": "II", "2": "II", "III": "III", "3": "III"}
_PHAN_DANG = {"I": "mcq", "II": "tf", "III": "sa"}

# Đáp án mâu thuẫn (An chốt 15/11): model tự sửa giữa chừng thì KHÔNG chọn đáp án nào, xếp loại riêng "dap_an_mau_thuan".
LOAI_MAU_THUAN = "dap_an_mau_thuan"
LOAI_DINH_DANG = "dinh_dang"
_RE_TU_SUA = re.compile(r"xin lỗi|khoan|sửa lại|correction|nhầm|tính lại|đổi thành|kiểm tra lại đề|thử lại",
                        re.IGNORECASE)
_RE_CHON = re.compile(r"[Cc]họn\s*(?:đáp án\s*|phương án\s*)?([A-D])\b")
_RE_Y_NHAN = re.compile(r"\b([a-d])\)\s*(Đúng|Sai)\b|\bý\s*([a-d])\s*(?:là\s*)?(Đúng|Sai)\b")


def _mau_thuan(dang, lines, dap_an):
    """Lý do (chuỗi) nếu phần đáp án của câu tự mâu thuẫn, None nếu không: có dấu hiệu tự sửa ("xin lỗi", "sửa lại",
    "khoan"...), mcq có hơn một chữ được chọn, tf có ý vừa Đúng vừa Sai."""
    text = "\n".join(lines)
    m = _RE_TU_SUA.search(text)
    if m:
        return f"model tự sửa giữa chừng (\"{m.group(0)}\")"
    if dang == "mcq":
        chu = set(_RE_CHON.findall(text)) | ({dap_an} if dap_an else set())
        if len(chu) > 1:
            return "nhiều phương án được chọn: " + ", ".join(sorted(chu))
    elif dang == "tf":
        nhan = {}
        for a, b, c, d in _RE_Y_NHAN.findall(text):
            nhan.setdefault(a or c, set()).add(b or d)
        lech = sorted(k for k, v in nhan.items() if len(v) > 1)
        if lech:
            return "ý vừa Đúng vừa Sai: " + ", ".join(lech)
    return None


def _sach(line):
    """Bỏ dấu Markdown bao ngoài để nhận dạng: #, **, __, >, gạch đầu dòng (-, •, *)."""
    s = line.strip()
    if re.fullmatch(r"[-*_=\s]{3,}", s):  # đường kẻ ngang Markdown
        return ""
    s = re.sub(r"^#+\s*", "", s)
    s = re.sub(r"^>\s*", "", s)
    s = s.replace("**", "").replace("__", "")
    s = re.sub(r"^[-•*]\s+(?=C[âa]u|[A-Da-d]\s*[.)])", "", s.strip())
    return s.strip()


def _la_tieu_de_dap_an(raw_line, s):
    """Tiêu đề phần đáp án cuối đề: dòng tiêu đề (#, in đậm cả dòng hoặc chữ hoa) bắt đầu bằng "Đáp án"/"Hướng dẫn chấm"/...
    và không có nội dung sau dấu hai chấm (để "Đáp án: B" ngay dưới câu không bị hiểu nhầm)."""
    if not _RE_TIEU_DE_DAP_AN.match(s) or len(s) > 80 or re.search(r"[:：]\s*\S", s):
        return False
    r = raw_line.strip()
    return r.startswith("#") or (r.startswith("**") and r.rstrip(":：").endswith("**")) or s == s.upper()


def _tach_pa_mot_dong(text):
    """"A. 1  B. 2  C. 3  D. 4" -> [("A", "1"), ...]; không tách được thì None."""
    parts = re.split(r"\s+(?=[B-D]\s*[.)]\s)", text)
    if len(parts) < 2:
        return None
    out = []
    for p in parts:
        m = _RE_PA.match(p.strip())
        if not m:
            return None
        out.append((m.group(1), m.group(2).strip()))
    return out


def _dap_an_tu_chuoi(text):
    """Đọc đáp án từ phần chữ sau "Câu N:" ở phần đáp án: chữ A-D, nhãn từng ý Đ/S, hoặc đáp số."""
    t = text.strip()
    tf = re.findall(r"\b([a-d])\s*\)?\s*[:\-–.]?\s*(Đúng|Sai|Đ|S)\b", t, flags=re.IGNORECASE)
    if len(tf) >= 2:
        return {"tf": {k.lower(): _nhan_tf(v) for k, v in tf}}
    m = re.match(r"^(?:chọn\s*)?(?:đáp án\s*)?[:：]?\s*([A-D])\b(?![a-zà-ỹ])", t, flags=re.IGNORECASE)
    if m and m.group(1).isupper():
        return {"mcq": m.group(1)}
    if t:
        return {"sa": _dap_an_sa(t.split("\n")[0])}
    return {}


def _doc_bang_dap_an(lines):
    """Bảng Markdown: một hàng số câu, hàng ngay sau là đáp án."""
    out = {}
    rows = [[c.strip().replace("**", "") for c in ln.strip().strip("|").split("|")] for ln in lines if ln.count("|") >= 2]
    rows = [r for r in rows if not all(re.fullmatch(r":?-+:?", c or "-") for c in r)]
    for r1, r2 in zip(rows, rows[1:]):
        nums = [re.sub(r"(?i)^c[âa]u\s*", "", c) for c in r1]
        if all(re.fullmatch(r"\d+", n) for n in nums[1:] if n) and sum(1 for n in nums if re.fullmatch(r"\d+", n)) >= 2:
            for n, v in zip(nums, r2):
                if re.fullmatch(r"\d+", n) and v:
                    out[int(n)] = _dap_an_tu_chuoi(v)
    return out


def tu_van_ban(raw, nguon, de, mt=None):
    lines = raw.splitlines()
    phan, dap_an_mode = None, False
    cau_list, cur = [], None
    dap_an = {}  # (phan, so) và so -> dict
    khoi = {}    # (phan, so) và so -> các dòng phần đáp án của câu đó (để phát hiện đáp án mâu thuẫn)
    vung_dap_an = []
    last_key = None

    def dong_cau():
        nonlocal cur
        if cur is not None:
            cau_list.append(cur)
        cur = None

    for line in lines:
        s = _sach(line)
        if not s:
            continue
        m = _RE_PHAN.match(s)
        if m:
            dong_cau()
            phan = _PHAN_SO[m.group(1).upper()]
            last_key = None
            continue
        if _la_tieu_de_dap_an(line, s):
            dong_cau()
            dap_an_mode = True
            phan = None
            last_key = None
            continue
        if dap_an_mode:
            vung_dap_an.append((phan, line))
            m = _RE_CAU.match(s)
            if m:
                key = int(m.group(1))
                v = _dap_an_tu_chuoi(m.group(2))
                dap_an[(phan, key)] = v
                dap_an.setdefault(key, v)
                last_key = (phan, key)
                khoi[last_key] = [s]
                khoi.setdefault(key, khoi[last_key])
                continue
            if last_key is not None:
                khoi[last_key].append(s)
            pairs = re.findall(r"(\d+)\s*[.:\-–)]\s*([A-D])\b", s)
            if len(pairs) >= 2:
                for n, v in pairs:
                    dap_an[(phan, int(n))] = {"mcq": v}
                    dap_an.setdefault(int(n), {"mcq": v})
                continue
            tf = re.findall(r"\b([a-d])\s*\)?\s*[:\-–.]?\s*(Đúng|Sai|Đ|S)\b", s, flags=re.IGNORECASE)
            if tf and last_key is not None:
                d = dap_an.get(last_key, {})
                d.setdefault("tf", {}).update({k.lower(): _nhan_tf(v) for k, v in tf})
                dap_an[last_key] = d
            continue
        m = _RE_CAU.match(s)
        if m:
            dong_cau()
            cur = {"so": int(m.group(1)), "phan": phan, "de": [m.group(2)] if m.group(2) else [], "pa": {}, "y": {},
                   "dap_an": None, "loi_giai": [], "goc": [line], "che_do": "de", "sau": []}
            continue
        if cur is None:
            continue
        cur["goc"].append(line)
        if cur["che_do"] != "de":
            cur["sau"].append(s)
        m = _RE_DAP_AN_DONG.match(s)
        if m:
            cur["dap_an"] = _dap_an_tu_chuoi(m.group(2))
            cur["che_do"] = "sau_dap_an"
            cur["sau"].append(s)
            continue
        m = _RE_LOI_GIAI.match(s)
        if m and cur["che_do"] != "loi_giai" and (cur["pa"] or cur["y"] or cur["dap_an"]):
            cur["che_do"] = "loi_giai"
            if m.group(2):
                cur["loi_giai"].append(m.group(2))
            continue
        if cur["che_do"] == "loi_giai":
            cur["loi_giai"].append(s)
            continue
        many = _tach_pa_mot_dong(s)
        if many and many[0][0] == "A":
            cur["pa"].update(dict(many))
            continue
        m = _RE_PA.match(s)
        if m and cur["che_do"] == "de":
            cur["pa"][m.group(1)] = m.group(2).strip()
            continue
        m = _RE_Y.match(s)
        if m and cur["che_do"] == "de":
            cur["y"][m.group(1)] = m.group(2).strip()
            continue
        if cur["che_do"] == "de" and not cur["pa"] and not cur["y"]:
            cur["de"].append(s)
    dong_cau()
    # Bảng đáp án đọc theo từng phần: "Câu 1" của bảng phần I không bị "Câu 1" của phần II (đánh số lại) che.
    for p in dict.fromkeys(p for p, _ in vung_dap_an):
        for k, v in _doc_bang_dap_an([ln for q, ln in vung_dap_an if q == p]).items():
            dap_an.setdefault((p, k), v)
            dap_an.setdefault(k, v)

    items = []
    for i, c in enumerate(cau_list, 1):
        dang = _PHAN_DANG.get(c["phan"])
        if dang is None:
            dang = "mcq" if len(c["pa"]) >= 2 else "tf" if len(c["y"]) >= 2 else "sa"
        da = c["dap_an"] or dap_an.get((c["phan"], c["so"])) or dap_an.get(c["so"]) or {}
        # stt theo thứ tự xuất hiện: đề chữ tự do hay đánh số lại "Câu 1" ở mỗi phần (trùng id nếu dùng số trong đề)
        out = {"stt": i, "dang": dang, "de": "\n".join(c["de"]).strip(),
               "loi_giai": "\n".join(c["loi_giai"]).strip() or None}
        if dang == "mcq":
            out["phuong_an"] = {k: c["pa"].get(k, "") for k in "ABCD"}
            out["dap_an"] = da.get("mcq")
        elif dang == "tf":
            out["y"] = [{"vi_tri": k, "noi_dung": c["y"][k]} for k in "abcd" if k in c["y"]]
            out["dap_an"] = da.get("tf") or {}
        else:
            out["dap_an"] = da.get("sa") or (da.get("mcq") if da.get("mcq") else None)
        dong_da = (khoi.get((c["phan"], c["so"])) or (khoi.get(c["so"]) if c["phan"] is None else None) or []) \
            + c["sau"]
        ly_do = _mau_thuan(dang, dong_da, out["dap_an"] if dang == "mcq" else None)
        if ly_do:
            out["dap_an"] = {} if dang == "tf" else None  # không chọn đáp án đầu hay cuối
            out["mau_thuan"] = ly_do
        items.append((out, "\n".join(c["goc"] + (["--- phần đáp án ---"] + dong_da if dong_da else []))))
    return _hoan_thien(items, nguon, de, mt)


# ------------------------------------------------------------------ chung

def _hoan_thien(items, nguon, de, mt=None):
    """Gán siêu dữ liệu theo ma trận (câu dạng X thứ k -> ô dạng X thứ k), kiểm tra, tách câu lỗi."""
    mt = mt or ma_tran.doc()
    o_theo_dang = {d: [c for c in ma_tran.cac_cau(mt) if c["dang"] == d] for d in cau_mod.DANG}
    dem = {d: 0 for d in cau_mod.DANG}
    tot, loi = [], []
    for c, goc in items:
        dang = c.get("dang")
        cid = f"{nguon}-d{de}-c{int(c.get('stt') or 0):02d}"
        if dang not in cau_mod.DANG:
            loi.append({"id": cid, "loai": LOAI_DINH_DANG, "loi": [f"không xác định được dạng câu: {dang}"], "cau": c,
                        "goc": goc})
            continue
        dem[dang] += 1
        o = o_theo_dang[dang][dem[dang] - 1] if dem[dang] <= len(o_theo_dang[dang]) else None
        full = {"id": cid, "nguon": nguon, "dang": dang, "de": c.get("de") or "",
                "phuong_an": c.get("phuong_an") if dang == "mcq" else None,
                "y": c.get("y") if dang == "tf" else None, "dap_an": c.get("dap_an"),
                "loi_giai": c.get("loi_giai"),
                "khoi": o["khoi"] if o else None, "chu_de": o["chu_de"] if o else None,
                "bai": o["bai"] if o else None, "muc_do": o["muc_do"] if o else None,
                "prompt_version": nguon, "de_baseline": de}
        e = cau_mod.kiem_tra(full)
        if o is None:
            e.append(f"thừa câu {dang} so với ma trận (câu {dang} thứ {dem[dang]})")
        if c.get("mau_thuan"):
            e.insert(0, f"đáp án mâu thuẫn: {c['mau_thuan']}")
        if e:
            loi.append({"id": cid, "loai": LOAI_MAU_THUAN if c.get("mau_thuan") else LOAI_DINH_DANG, "loi": e,
                        "cau": full, "goc": goc})
        else:
            tot.append(full)
    for d in cau_mod.DANG:
        if dem[d] < len(o_theo_dang[d]):
            loi.append({"id": f"{nguon}-d{de}-thieu-{d}", "loai": LOAI_DINH_DANG, "loi": [f"thiếu {len(o_theo_dang[d]) - dem[d]} câu {d} so với ma trận"],
                        "cau": None, "goc": ""})
    return tot, loi


def parse_file(path, mt=None):
    """<nguon>-d<k>.txt -> (câu, lỗi). nguon baseline_2 đọc JSON, còn lại đọc chữ (JSON hỏng thì thử chữ).
    mt: ma trận đã dùng khi gọi (mặc định ma trận mẫu)."""
    m = re.match(r"(baseline_\d+)-d(\d+)\.txt$", os.path.basename(path))
    if not m:
        raise ValueError(f"Tên file thô không đúng mẫu <nguon>-d<k>.txt: {path}")
    nguon, de = m.group(1), int(m.group(2))
    with open(path, encoding="utf-8") as f:
        raw = f.read()
    looks_json = raw.lstrip().startswith(("[", "{", "```json")) or nguon == "baseline_2"
    if looks_json:
        tot, loi = tu_json(raw, nguon, de, mt)
        if tot or not loi or not loi[0]["loi"][0].startswith("JSON"):
            return tot, loi
    return tu_van_ban(raw, nguon, de, mt)


def kiem_ma_tran(path, mt):
    """Lỗi (chuỗi) nếu meta của file thô ghi dấu vân tay ma trận khác mt; None nếu khớp hoặc meta cũ không ghi."""
    meta_path = path[: -len(".txt")] + ".meta.json"
    if not os.path.exists(meta_path):
        return None
    with open(meta_path, encoding="utf-8") as f:
        meta = json.load(f)
    sha = meta.get("ma_tran_sha256")
    if sha and sha != ma_tran.dau_van_tay(mt):
        return (f"{os.path.basename(path)} được gọi với ma trận \"{meta.get('ma_tran_ten')}\", khác ma trận đang dùng để "
                f"parse \"{mt.get('name')}\": truyền đúng --ma-tran")
    return None


_TRUONG_DIFF = ("dang", "de", "phuong_an", "y", "dap_an", "loi_giai")


def diff_sua_tay(c_sua, ban_truoc):
    """L6c: một dòng cho một câu sửa tay. ban_truoc: mục parse-loi (có "loi", "cau"), câu đã parse được, hoặc None."""
    la_loi = bool(ban_truoc) and "loi" in ban_truoc
    cau_truoc = ban_truoc.get("cau") if la_loi else ban_truoc
    doi = {}
    for k in _TRUONG_DIFF:
        truoc = (cau_truoc or {}).get(k)
        if truoc != c_sua.get(k):
            doi[k] = {"truoc": truoc, "sau": c_sua.get(k)}
    return {"id": c_sua["id"], "loi_goc": ban_truoc["loi"] if la_loi else [], "co_ban_parse": cau_truoc is not None,
            "truong_doi": doi}


def main(argv=None):
    ap = argparse.ArgumentParser(description="D4: parse đầu ra thô baseline về định dạng câu thống nhất.")
    ap.add_argument("--tho-dir", required=True)
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--ma-tran", default=ma_tran.MAU_MAC_DINH,
                    help="đúng file ma trận đã truyền cho baseline_goi (meta có dấu vân tay thì phải khớp)")
    ap.add_argument("--sua-tay", help="JSONL câu đã sửa tay (cùng id thay bản parse)")
    a = ap.parse_args(argv)
    mt = ma_tran.doc(a.ma_tran)
    paths = [p for p in sorted(glob.glob(os.path.join(a.tho_dir, "baseline_*-d*.txt"))) if not p.endswith(".prompt.txt")]
    lech = [e for e in (kiem_ma_tran(p, mt) for p in paths) if e]
    if lech:
        raise SystemExit("Sai ma trận, không ghi gì:\n" + "\n".join(lech))
    theo_nguon, loi_all, ban_truoc = {}, [], {}
    for path in paths:
        tot, loi = parse_file(path, mt)
        for c in tot:
            theo_nguon.setdefault(c["nguon"], {})[c["id"]] = c
            ban_truoc[c["id"]] = c
        for x in loi:
            ban_truoc[x["id"]] = x
        loi_all.extend(loi)
    sua = cau_mod.doc_jsonl(a.sua_tay) if a.sua_tay else []
    da_sua, diffs = set(), []
    for c in sua:
        truoc = ban_truoc.get(c.get("id")) or {}
        if truoc.get("loai") == LOAI_MAU_THUAN:
            raise ValueError(f"{c.get('id')} là câu đáp án mâu thuẫn: không sửa tay (An chốt 15/11), chỉ đếm cho D4")
        e = cau_mod.kiem_tra(c)
        if e:
            raise ValueError(f"sửa tay {c.get('id')} vẫn lỗi: {e}")
        theo_nguon.setdefault(c["nguon"], {})[c["id"]] = c
        da_sua.add(c["id"])
        diffs.append(diff_sua_tay(c, ban_truoc.get(c["id"])))
    loi_con = [x for x in loi_all if x["id"] not in da_sua]
    os.makedirs(a.out_dir, exist_ok=True)
    for nguon, d in sorted(theo_nguon.items()):
        cau_mod.ghi_jsonl(os.path.join(a.out_dir, f"{nguon}.jsonl"), [d[k] for k in sorted(d)])
        print(f"{nguon}: {len(d)} câu hợp lệ")
    cau_mod.ghi_jsonl(os.path.join(a.out_dir, "parse-loi.jsonl"), loi_con)
    dem = {}
    for x in loi_con:
        nguon = x["id"].split("-d")[0]
        dem.setdefault(nguon, {})
        dem[nguon][x.get("loai", LOAI_DINH_DANG)] = dem[nguon].get(x.get("loai", LOAI_DINH_DANG), 0) + 1
    with open(os.path.join(a.out_dir, "parse-loi-dem.json"), "w", encoding="utf-8") as f:
        json.dump(dem, f, ensure_ascii=False, indent=2)
    if a.sua_tay:
        cau_mod.ghi_jsonl(os.path.join(a.out_dir, "sua-tay-diff.jsonl"), diffs)
    print(f"{len(loi_con)} mục lỗi chưa sửa -> parse-loi.jsonl ({len(da_sua)} câu đã sửa tay"
          + (", diff -> sua-tay-diff.jsonl)" if a.sua_tay else ")"))


if __name__ == "__main__":
    main()
