"""Ma trận mẫu của app (back_end/src/main/resources/samples/*.json) -> danh sách câu theo thứ tự đề, và dạng chữ cho prompt
baseline D4. Câu baseline nhận khối/chủ đề/nội dung/mức độ từ đúng chỗ này để phiếu chấm ghi giống câu hệ thống."""
import json
import os

MAU_MAC_DINH = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "..", "..", "back_end", "src", "main",
                                             "resources", "samples", "matrix_giuaki1_toan8.json"))
MUC_DO_VI = {"easy": "Nhận biết", "medium": "Thông hiểu", "hard": "Vận dụng"}
TEN_PHAN = {"I": "PHẦN I. Câu trắc nghiệm nhiều phương án lựa chọn",
            "II": "PHẦN II. Câu trắc nghiệm đúng sai",
            "III": "PHẦN III. Câu trắc nghiệm trả lời ngắn"}


def doc(path=MAU_MAC_DINH):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def cac_cau(mt):
    """Mỗi phần tử một câu: stt (1..N theo thứ tự ô), dang, phan, khoi, chu_de, bai, muc_do (tf: danh sách), yccd."""
    out = []
    for cell in mt["cells"]:
        for _ in range(cell["questionCount"]):
            muc = cell.get("statementDifficulties") if cell["questionType"] == "tf" else cell.get("difficulty")
            out.append({"stt": len(out) + 1, "dang": cell["questionType"], "phan": cell["part"],
                        "khoi": mt.get("gradeLevel"), "chu_de": cell["topic"], "bai": cell.get("content"),
                        "muc_do": muc, "yccd": cell.get("learningOutcomes") or {}})
    return out


def _muc(m):
    return ", ".join(MUC_DO_VI[x] for x in m) if isinstance(m, list) else MUC_DO_VI.get(m, m)


def dang_bang(mt):
    """Ma trận dạng chữ như chép từ bản đặc tả (prompt 1)."""
    lines = [f"{mt['name']} (khối {mt.get('gradeLevel')})"]
    phan = None
    for cell in mt["cells"]:
        if cell["part"] != phan:
            phan = cell["part"]
            n = sum(c["questionCount"] for c in mt["cells"] if c["part"] == phan)
            lines.append(f"{TEN_PHAN[phan]} ({n} câu)")
        muc = cell.get("statementDifficulties") if cell["questionType"] == "tf" else cell.get("difficulty")
        yc = "; ".join(f"{MUC_DO_VI[k]}: {v}" for k, v in (cell.get("learningOutcomes") or {}).items())
        lines.append(f"- {cell['topic']} | {cell.get('content') or ''} | Mức độ: {_muc(muc)} | Số câu: "
                     f"{cell['questionCount']} | Yêu cầu cần đạt: {yc}")
    for note in mt.get("notes") or []:
        lines.append(f"Ghi chú: {note}")
    return "\n".join(lines)


def dang_danh_sach(mt):
    """Ma trận dạng danh sách đánh số từng câu (prompt 2)."""
    lines = []
    for c in cac_cau(mt):
        if c["dang"] == "tf":
            muc = "mức độ 4 ý a-d: " + _muc(c["muc_do"])
            yc = "; ".join(f"{MUC_DO_VI[k]}: {v}" for k, v in c["yccd"].items())
        else:
            muc = "mức độ: " + _muc(c["muc_do"])
            yc = c["yccd"].get(c["muc_do"]) or "; ".join(c["yccd"].values())
        lines.append(f"Câu {c['stt']} (dang = \"{c['dang']}\", {muc}) – Chủ đề: {c['chu_de']}; Nội dung: {c['bai']}; "
                     f"Yêu cầu cần đạt: {yc}")
    for note in mt.get("notes") or []:
        lines.append(f"Ghi chú: {note}")
    return "\n".join(lines)
