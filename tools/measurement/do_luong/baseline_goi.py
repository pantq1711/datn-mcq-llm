"""D4: gọi thẳng Gemini với 2 prompt baseline (docs/experiments/d4/prompts.md), mỗi prompt một số đề, ghi NGUYÊN đầu ra thô.

GỌI GEMINI TÍNH PHÍ: mỗi đề một lượt. Chỉ chạy khi An đã duyệt prompt và có khóa riêng.
- Khóa đọc từ biến môi trường GEMINI_API_KEY lúc chạy; không in, không ghi ra file.
- --dry-run: chỉ dựng prompt, in độ dài + SHA-256 và nơi sẽ ghi, KHÔNG cần khóa, KHÔNG gọi mạng.
- File thô đã có thì bỏ qua (chạy lại không tốn thêm lượt gọi); muốn gọi lại thì xóa file đó.
Mỗi lượt ghi <out>/<nguon>-d<k>.txt (đầu ra thô) và <out>/<nguon>-d<k>.meta.json (model, token, thời gian, SHA-256 prompt).

Chạy (đứng ở tools/measurement, Git Bash):
  python -m do_luong.baseline_goi --dry-run --out-dir ../../data/private/do-luong/baseline-tho
  export GEMINI_API_KEY=...   # gõ tay trong terminal, không dán vào file
  python -m do_luong.baseline_goi --model gemini-3.5-flash-lite --so-de 2 --out-dir ../../data/private/do-luong/baseline-tho
"""
import argparse
import hashlib
import json
import os
import re
import sys
import time
from datetime import datetime

from . import ma_tran

PROMPTS_MD = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "..", "..", "docs", "experiments", "d4",
                                           "prompts.md"))
LLM_PROBE = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "..", "..", "llm-probe"))
CHE_DO = {1: "plain", 2: "json"}  # mode của llm_formula_probe.call_gemini


def doc_prompt_mau(path=PROMPTS_MD):
    """{1: mẫu prompt 1, 2: mẫu prompt 2} từ khối ```text ngay sau dấu <!-- prompt-k -->."""
    with open(path, encoding="utf-8") as f:
        text = f.read()
    out = {}
    for k in (1, 2):
        m = re.search(r"<!--\s*prompt-%d\s*-->\s*```text\n(.*?)\n```" % k, text, re.DOTALL)
        if not m:
            raise ValueError(f"{path}: không thấy khối prompt-{k}")
        out[k] = m.group(1)
    return out


def dung_prompt(k, mau, mt):
    if "{MA_TRAN}" not in mau:
        raise ValueError(f"prompt-{k} thiếu chỗ {{MA_TRAN}}")
    return mau.replace("{MA_TRAN}", ma_tran.dang_bang(mt) if k == 1 else ma_tran.dang_danh_sach(mt))


def _goi(model, key, prompt, mode):
    if LLM_PROBE not in sys.path:
        sys.path.insert(0, LLM_PROBE)
    from llm_formula_probe import call_gemini_with_retry  # dùng lại hàm gọi + thử lại sẵn có
    return call_gemini_with_retry(model, key, prompt, mode)


def main(argv=None, goi=_goi):
    ap = argparse.ArgumentParser(description="D4: gọi thẳng Gemini với prompt baseline.")
    ap.add_argument("--prompt", type=int, nargs="+", default=[1, 2], choices=[1, 2])
    ap.add_argument("--so-de", type=int, default=2)
    ap.add_argument("--model", default="gemini-3.5-flash-lite", help="phải trùng LLM_MODEL của app khi sinh câu hệ thống")
    ap.add_argument("--ma-tran", default=ma_tran.MAU_MAC_DINH)
    ap.add_argument("--prompts-md", default=PROMPTS_MD)
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args(argv)

    mt = ma_tran.doc(a.ma_tran)
    mau = doc_prompt_mau(a.prompts_md)
    key = None
    if not a.dry_run:
        key = os.environ.get("GEMINI_API_KEY")
        if not key:
            sys.exit("Thiếu biến môi trường GEMINI_API_KEY (hoặc chạy --dry-run).")
    os.makedirs(a.out_dir, exist_ok=True)
    for k in a.prompt:
        prompt = dung_prompt(k, mau[k], mt)
        sha = hashlib.sha256(prompt.encode("utf-8")).hexdigest()
        for d in range(1, a.so_de + 1):
            base = os.path.join(a.out_dir, f"baseline_{k}-d{d}")
            if a.dry_run:
                print(f"[dry-run] prompt {k}, đề {d}: {len(prompt)} ký tự, sha256 {sha[:12]}…, sẽ ghi {base}.txt")
                if d == 1:
                    with open(base + ".prompt.txt", "w", encoding="utf-8", newline="\n") as f:
                        f.write(prompt)
                continue
            if os.path.exists(base + ".txt"):
                print(f"Đã có {base}.txt, bỏ qua (xóa file để gọi lại).")
                continue
            t0 = time.monotonic()
            text, tok_in, tok_out, tok_think = goi(a.model, key, prompt, CHE_DO[k])
            giay = time.monotonic() - t0
            with open(base + ".txt", "w", encoding="utf-8", newline="\n") as f:
                f.write(text)
            with open(base + ".meta.json", "w", encoding="utf-8") as f:
                json.dump({"nguon": f"baseline_{k}", "de": d, "model": a.model, "che_do": CHE_DO[k],
                           "prompt_sha256": sha, "token_vao": tok_in, "token_ra": tok_out, "token_nghi": tok_think,
                           "thoi_gian_goi_giay": round(giay, 2), "luc": datetime.now().isoformat(timespec="seconds")},
                          f, ensure_ascii=False, indent=2)
            print(f"prompt {k}, đề {d}: {len(text)} ký tự, {giay:.1f} s -> {base}.txt")


if __name__ == "__main__":
    main()
