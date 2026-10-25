#!/usr/bin/env python3
"""
Thử nhanh: Gemini trả công thức LaTeX ra sao khi sinh câu hỏi Toán?
Chỉ dùng thư viện chuẩn của Python (không cần pip install).

Cách chạy (Git Bash, khóa API đặt trong biến môi trường, KHÔNG dán vào file hay chat):
    export GEMINI_API_KEY="khóa của bạn"
    python llm_formula_probe.py --model <tên-model> --n 20 --mode plain
    python llm_formula_probe.py --model <tên-model> --n 20 --mode json
    python llm_formula_probe.py --model <tên-model> --n 20 --mode schema
    python llm_formula_probe.py --model <tên-model> --n 20 --mode json --no-escape-hint

Mỗi lần chạy lưu phản hồi thô vào thư mục out_<mode>/ để tự mở ra xem.
Nếu chạy lại, các file raw_XX.txt đã có sẵn sẽ được đọc lại từ đĩa (không tốn thêm quota API).
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

TOPICS = [
    "rút gọn phân số",
    "căn bậc hai của số chính phương",
    "phương trình bậc nhất một ẩn",
    "bất phương trình bậc nhất một ẩn",
    "hằng đẳng thức đáng nhớ",
    "hệ hai phương trình bậc nhất hai ẩn",
    "tính giá trị biểu thức chứa lũy thừa",
    "rút gọn biểu thức đại số có phân thức",
]

FIELDS = ["question_text", "option_a", "option_b", "option_c", "option_d", "correct_option", "explanation"]

BASE_PROMPT = """Bạn là giáo viên Toán lớp 8. Hãy soạn đúng 1 câu hỏi trắc nghiệm 4 phương án về chủ đề: {topic}.
Chỉ có 1 đáp án đúng. Trả về DUY NHẤT một đối tượng JSON gồm 7 trường:
question_text, option_a, option_b, option_c, option_d, correct_option (một trong A, B, C, D), explanation (lời giải từng bước).
Mọi công thức toán đặt trong cặp dấu $...$ (LaTeX)."""

ESCAPE_HINT = """
Lưu ý: vì kết quả là JSON, mọi dấu gạch chéo ngược trong LaTeX phải viết thành hai dấu, ví dụ "$\\\\frac{1}{2}$"."""

SCHEMA = {
    "type": "OBJECT",
    "properties": {f: {"type": "STRING"} for f in FIELDS},
    "required": FIELDS,
}


def call_gemini(model, key, prompt, mode):
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    body = {"contents": [{"parts": [{"text": prompt}]}]}
    if mode == "json":
        body["generationConfig"] = {"responseMimeType": "application/json"}
    elif mode == "schema":
        body["generationConfig"] = {"responseMimeType": "application/json", "responseSchema": SCHEMA}
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json", "x-goog-api-key": key},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            data = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")[:600]
        raise RuntimeError(f"HTTP {e.code}: {detail}") from None
    text = data["candidates"][0]["content"]["parts"][0].get("text", "")
    usage = data.get("usageMetadata", {})
    return text, usage.get("promptTokenCount", 0), usage.get("candidatesTokenCount", 0), usage.get("thoughtsTokenCount", 0)


def call_gemini_with_retry(model, key, prompt, mode, max_retries=3):
    """Tự động đợi khi gặp 503 (quá tải) hoặc 429 theo phút; dừng ngay nếu hết quota ngày."""
    for attempt in range(1, max_retries + 1):
        try:
            return call_gemini(model, key, prompt, mode)
        except RuntimeError as e:
            msg = str(e)
            # Kiểm tra hết quota theo ngày (ví dụ: Please retry in 14h48m...)
            if "HTTP 429" in msg and re.search(r"retry in \d+h", msg):
                sys.exit(
                    f"\n[DỪNG NGAY] Đã hết hạn mức Free Tier trong ngày (20 request/ngày cho model {model}).\n"
                    f"Chi tiết: {msg}"
                )
            # Nếu 429 theo phút (RPM = 5), đọc số giây cần chờ
            if "HTTP 429" in msg and attempt < max_retries:
                m = re.search(r"retry in ([0-9.]+)s", msg)
                wait_s = (float(m.group(1)) + 2.0) if m else 15.0
                print(f"  -> Chạm giới hạn 5 req/phút (429), tự động chờ {wait_s:.1f}s rồi thử lại ({attempt}/{max_retries})...")
                time.sleep(wait_s)
                continue
            # Nếu 503 quá tải tạm thời
            if "HTTP 503" in msg and attempt < max_retries:
                wait_s = 15.0 * attempt
                print(f"  -> Server Google đang quá tải (503), chờ {wait_s:.0f}s rồi thử lại ({attempt}/{max_retries})...")
                time.sleep(wait_s)
                continue
            raise


def latex_errors(text):
    """Bản Python của LatexFormatValidator (cùng quy tắc)."""
    errors = []
    if text is None or not str(text).strip():
        return ["Nội dung trống"]
    for i, c in enumerate(text):
        if c in "\f\b\t\r" or (ord(c) < 0x20 and c != "\n"):
            errors.append(f"ký tự điều khiển tại vị trí {i} (thường do \\ chưa thoát trong JSON)")
            break
    in_math, cmd_outside_reported, depth, i, n = False, False, 0, 0, len(text)
    while i < n:
        c = text[i]
        if c == "\\":
            if not in_math and not cmd_outside_reported and i + 1 < n and text[i + 1].isalpha():
                errors.append("Có lệnh LaTeX nằm ngoài cặp $...$ (thiếu dấu $ bao quanh công thức)")
                cmd_outside_reported = True
            i += 2
            continue
        if c == "$":
            if i + 1 < n and text[i + 1] == "$":
                i += 1
            if in_math and depth != 0:
                errors.append("ngoặc { } không cân bằng")
                depth = 0
            in_math = not in_math
            i += 1
            continue
        if in_math:
            if c == "\n":
                errors.append(
                    f"ký tự điều khiển xuống dòng bên trong $...$ tại vị trí {i} (thường do \\n như \\neq chưa thoát trong JSON)"
                )
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth < 0:
                    errors.append("dấu } thừa")
                    depth = 0
        i += 1
    if in_math:
        errors.append("số dấu $ bị lẻ")
    return errors


def analyze(raw):
    """Trả về dict các cờ mô tả phản hồi thô."""
    r = {"fence": False, "json_ok": False, "fields_ok": False, "alt_delim": False,
         "control_char": False, "latex_ok": False, "notes": []}
    text = raw.strip()
    if text.startswith("```"):
        r["fence"] = True
        text = text.strip("`")
        if text.lower().startswith("json"):
            text = text[4:]
        text = text.strip()
    try:
        obj = json.loads(text)
    except Exception as e:  # noqa: BLE001
        r["notes"].append(f"không parse được JSON: {e}")
        return r
    r["json_ok"] = isinstance(obj, dict)
    if not r["json_ok"]:
        return r
    missing = [f for f in FIELDS if f not in obj]
    r["fields_ok"] = not missing and str(obj.get("correct_option", "")).strip() in list("ABCD")
    if missing:
        r["notes"].append(f"thiếu trường: {missing}")
    all_errors = []
    for f in FIELDS:
        v = obj.get(f)
        if isinstance(v, str):
            if "\\(" in v or "\\[" in v:
                r["alt_delim"] = True
            errs = latex_errors(v)
            if any("ký tự điều khiển" in e for e in errs):
                r["control_char"] = True
            all_errors += [f"{f}: {e}" for e in errs]
    r["latex_ok"] = not all_errors
    r["notes"] += all_errors[:3]
    return r


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--n", type=int, default=20)
    ap.add_argument("--mode", choices=["plain", "json", "schema"], default="plain")
    ap.add_argument("--no-escape-hint", action="store_true", help="bỏ lời dặn thoát dấu \\")
    ap.add_argument("--sleep", type=float, default=13.0, help="nghỉ giữa 2 câu (mặc định 13s để không vượt 5 req/phút của Free Tier)")
    args = ap.parse_args()

    key = os.environ.get("GEMINI_API_KEY")
    if not key:
        sys.exit("Thiếu biến môi trường GEMINI_API_KEY")

    tag = args.mode + ("_nohint" if args.no_escape_hint else "")
    out_dir = f"out_{tag}"
    os.makedirs(out_dir, exist_ok=True)

    stats = {k: 0 for k in ["call_failed", "fence", "json_ok", "fields_ok", "alt_delim", "control_char", "latex_ok"]}
    tok_in = tok_out = tok_think = 0
    consecutive_fail = 0
    examples = []
    for i in range(args.n):
        topic = TOPICS[i % len(TOPICS)]
        raw_path = os.path.join(out_dir, f"raw_{i + 1:02d}.txt")
        if os.path.exists(raw_path) and os.path.getsize(raw_path) > 0:
            with open(raw_path, "r", encoding="utf-8") as fh:
                raw = fh.read()
            cached = True
        else:
            cached = False
            prompt = BASE_PROMPT.format(topic=topic) + ("" if args.no_escape_hint else ESCAPE_HINT)
            try:
                raw, t_in, t_out, t_think = call_gemini_with_retry(args.model, key, prompt, args.mode)
            except (urllib.error.URLError, RuntimeError, KeyError, IndexError) as e:
                stats["call_failed"] += 1
                print(f"[{i + 1}/{args.n}] LỖI GỌI API: {e}")
                consecutive_fail += 1
                if consecutive_fail >= 3:
                    sys.exit("3 lần gọi liên tiếp thất bại, dừng để khỏi tốn lượt. Đọc thông báo lỗi ở trên.")
                time.sleep(args.sleep)
                continue
            consecutive_fail = 0
            tok_in += t_in
            tok_out += t_out
            tok_think += t_think
            with open(raw_path, "w", encoding="utf-8") as fh:
                fh.write(raw)

        r = analyze(raw)
        for k in ["fence", "json_ok", "fields_ok", "alt_delim", "control_char", "latex_ok"]:
            stats[k] += 1 if r[k] else 0
        flag = "OK " if (r["json_ok"] and r["fields_ok"] and r["latex_ok"]) else "LỖI"
        suffix = " (đọc lại từ đĩa)" if cached else ""
        print(f"[{i + 1}/{args.n}] {flag} {topic}{suffix}")
        if flag == "LỖI" and len(examples) < 5:
            examples.append((i + 1, r["notes"]))
        if not cached and i + 1 < args.n:
            time.sleep(args.sleep)

    done = args.n - stats["call_failed"]
    print(f"\n=== Tổng kết: chế độ {tag}, model {args.model}, {done}/{args.n} lần gọi thành công ===")
    for k, label in [("json_ok", "parse được JSON"), ("fields_ok", "đủ 7 trường và đáp án A-D"),
                     ("latex_ok", "LaTeX đạt kiểm tra"), ("control_char", "có ký tự điều khiển (\\ thoát sai)"),
                     ("alt_delim", "dùng \\( \\) hoặc \\[ \\] thay vì $"), ("fence", "bọc trong ```")]:
        print(f"  {label:42s}: {stats[k]}/{done}")
    print(f"  token vào/ra/suy luận: {tok_in}/{tok_out}/{tok_think}  (token suy luận thường tính phí như token ra)")
    if done:
        print(f"  trung bình mỗi câu: vào {tok_in // done}, ra {tok_out // done}, suy luận {tok_think // done}")
    if examples:
        print("\nVí dụ lỗi (mở file raw_XX.txt tương ứng để xem nguyên văn):")
        for idx, notes in examples:
            print(f"  câu {idx}: {notes}")


if __name__ == "__main__":
    main()
