#!/usr/bin/env python3
"""
Thử nghiệm bước answer_check (Solver Check - RQ2b) trên 24 câu đã kiểm chứng tay + SymPy:
- 13 câu sai khóa (6 câu lệch khóa, 7 câu không có phương án đúng trong A-D)
- 11 câu đối chứng (10 câu đúng duy nhất 1 phương án, 1 câu có phương án D chưa rút gọn bằng A)

Solver chỉ nhận question_text và option_a..d (KHÔNG thấy correct_option và explanation của model sinh),
giải từng bước ở trường reasoning trước rồi mới chốt judge_answer in {A, B, C, D, multiple, none}.
"""
import argparse
import json
import os
import sys
import time
import urllib.error

from llm_formula_probe import ESCAPE_HINT, call_gemini_with_retry

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

# Danh sách 24 câu đã kiểm chứng: (đường dẫn tương đối từ m35_lite, nhãn thực tế, loại câu)
# loại câu: "wrong_key" (lệch khóa), "none_correct" (không có đáp án đúng),
#           "valid" (đúng 1 đáp án), "ambiguous_unreduced" (schema_09: D chưa rút gọn bằng A)
BENCHMARK_24 = [
    # 6 câu lệch khóa
    ("out_json/raw_15.txt", "A", "wrong_key"),
    ("out_json/raw_16.txt", "B", "wrong_key"),
    ("out_plain/raw_15.txt", "B", "wrong_key"),
    ("out_schema/raw_07.txt", "B", "wrong_key"),
    ("out_schema/raw_15.txt", "B", "wrong_key"),
    ("out_schema/raw_16.txt", "A", "wrong_key"),
    # 7 câu không có đáp án đúng trong 4 phương án A-D
    ("out_json/raw_08.txt", "none", "none_correct"),
    ("out_plain/raw_06.txt", "none", "none_correct"),
    ("out_plain/raw_07.txt", "none", "none_correct"),
    ("out_plain/raw_08.txt", "none", "none_correct"),
    ("out_plain/raw_14.txt", "none", "none_correct"),
    ("out_plain/raw_16.txt", "none", "none_correct"),
    ("out_schema/raw_08.txt", "none", "none_correct"),
    # 11 câu nhóm đối chứng (6 câu dễ + 5 câu chủ đề khó: json_06, json_07, json_14, schema_06, schema_14)
    ("out_json/raw_01.txt", "A", "valid"),
    ("out_json/raw_03.txt", "A", "valid"),
    ("out_json/raw_06.txt", "A", "valid"),
    ("out_json/raw_07.txt", "B", "valid"),
    ("out_json/raw_14.txt", "A", "valid"),
    ("out_json/raw_19.txt", "A", "valid"),
    ("out_plain/raw_05.txt", "B", "valid"),
    ("out_schema/raw_06.txt", "A", "valid"),
    ("out_schema/raw_13.txt", "C", "valid"),
    ("out_schema/raw_14.txt", "B", "valid"),
    ("out_schema/raw_09.txt", "A", "ambiguous_unreduced"),
]

SOLVER_PROMPT = """Bạn là giáo viên Toán THCS chấm thẩm định độc lập một câu hỏi trắc nghiệm.
Dưới đây là đề bài và 4 phương án A, B, C, D (không kèm đáp án của người soạn):

Câu hỏi: {question_text}
A. {option_a}
B. {option_b}
C. {option_c}
D. {option_d}

Yêu cầu:
1. Tự giải bài toán từng bước từ đề bài ở trường "reasoning" (không đoán mò theo phương án).
2. Đối chiếu kết quả vừa giải được với cả 4 phương án A, B, C, D để điền trường "judge_answer":
   - Ghi "A", "B", "C", hoặc "D" nếu có ĐÚNG MỘT phương án đúng hoàn toàn.
   - Ghi "multiple" nếu có từ 2 phương án trở lên cùng đúng.
   - Ghi "none" nếu KHÔNG có phương án nào trong A, B, C, D khớp với kết quả đúng của đề bài.
""" + ESCAPE_HINT + """
Trả về DUY NHẤT một đối tượng JSON có 2 trường: "reasoning" và "judge_answer"."""


def load_question(path):
    raw = open(path, encoding="utf-8").read().strip()
    if raw.startswith("```"):
        raw = raw.strip("`")
        if raw.lower().startswith("json"):
            raw = raw[4:]
        raw = raw.strip()
    return json.loads(raw)


def parse_solver_output(raw):
    text = raw.strip()
    if text.startswith("```"):
        text = text.strip("`")
        if text.lower().startswith("json"):
            text = text[4:]
        text = text.strip()
    obj = json.loads(text)
    ans = str(obj.get("judge_answer", "")).strip()
    return ans, str(obj.get("reasoning", "")).strip()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True, help="Tên model làm Solver (ví dụ gemini-3.5-flash-lite)")
    ap.add_argument("--sleep", type=float, default=13.0)
    args = ap.parse_args()

    key = os.environ.get("GEMINI_API_KEY")
    if not key:
        sys.exit("Thiếu biến môi trường GEMINI_API_KEY")

    out_dir = f"out_solver_{args.model.replace('/', '_')}"
    os.makedirs(out_dir, exist_ok=True)

    n_total = len(BENCHMARK_24)
    bad_expected = sum(1 for _, _, c in BENCHMARK_24 if c in ("wrong_key", "none_correct"))
    good_expected = n_total - bad_expected

    bad_caught = 0          # Câu sai khóa mà judge_answer != generator_correct_option
    bad_exact_match = 0     # Câu sai khóa mà judge_answer == true_answer (bắt đúng cả loại lỗi)
    bad_evaluated = 0
    bad_unparsed = 0
    good_passed = 0         # Câu đúng khóa mà judge_answer == generator_correct_option
    good_false_alarm = 0    # Câu đúng khóa mà bị Solver báo lệch
    good_evaluated = 0
    good_unparsed = 0

    for idx, (rel_path, true_ans, category) in enumerate(BENCHMARK_24, start=1):
        q = load_question(rel_path)
        gen_key = str(q["correct_option"]).strip()
        cache_name = rel_path.replace("/", "__")
        cache_path = os.path.join(out_dir, cache_name)

        cached = False
        raw = None
        if os.path.exists(cache_path) and os.path.getsize(cache_path) > 0:
            candidate_raw = open(cache_path, encoding="utf-8").read()
            try:
                parse_solver_output(candidate_raw)
                raw = candidate_raw
                cached = True
            except Exception:  # noqa: BLE001
                # File cache cũ bị hỏng JSON (do lần chạy trước thiếu ESCAPE_HINT) -> gọi lại API
                raw = None

        if raw is None:
            prompt = SOLVER_PROMPT.format(
                question_text=q["question_text"],
                option_a=q["option_a"],
                option_b=q["option_b"],
                option_c=q["option_c"],
                option_d=q["option_d"],
            )
            try:
                raw, _, _, _ = call_gemini_with_retry(args.model, key, prompt, "json")
            except (urllib.error.URLError, RuntimeError, KeyError, IndexError) as e:
                print(f"[{idx:02d}/{n_total}] LỖI GỌI SOLVER ({rel_path}): {e}")
                if category in ("wrong_key", "none_correct"):
                    bad_unparsed += 1
                else:
                    good_unparsed += 1
                continue
            with open(cache_path, "w", encoding="utf-8") as fh:
                fh.write(raw)

        try:
            judge_ans, _ = parse_solver_output(raw)
        except Exception as e:  # noqa: BLE001
            print(f"[{idx:02d}/{n_total}] LỖI PARSE SOLVER ({rel_path}): {e}")
            if category in ("wrong_key", "none_correct"):
                bad_unparsed += 1
            else:
                good_unparsed += 1
            continue

        # Logic answer_check trong pipeline: passed nếu judge_ans khớp gen_key (và thuộc A-D)
        qc_passed = (judge_ans == gen_key) and (judge_ans in ("A", "B", "C", "D"))
        if category in ("wrong_key", "none_correct"):
            bad_evaluated += 1
            if not qc_passed:
                bad_caught += 1
            if judge_ans == true_ans:
                bad_exact_match += 1
            verdict = "BẮT ĐƯỢC LỖI" if not qc_passed else "BỎ LỌT LỖI"
        else:
            good_evaluated += 1
            if qc_passed:
                good_passed += 1
            else:
                good_false_alarm += 1
            verdict = "KHỚP (ĐÚNG)" if qc_passed else "BÁO NHẦM"

        tag_cache = " (cache)" if cached else ""
        print(
            f"[{idx:02d}/{n_total}] {rel_path:22s} | loại={category:19s} | "
            f"key_sinh={gen_key} | chuẩn={true_ans:4s} | solver={judge_ans:8s} -> {verdict}{tag_cache}"
        )
        if not cached and idx < n_total:
            time.sleep(args.sleep)

    total_evaluated = bad_evaluated + good_evaluated
    total_unparsed = bad_unparsed + good_unparsed
    print(f"\n=== TỔNG KẾT SOLVER ({args.model}) TRÊN {n_total} CÂU ĐÃ KIỂM CHỨNG ===")
    print(f"  0. Tỉ lệ phản hồi Solver parse được JSON:")
    print(f"     - Đánh giá được : {total_evaluated}/{n_total} (lỗi parse/gọi API: {total_unparsed}/{n_total})")
    print(f"  1. Trên nhóm {bad_expected} câu sai khóa (6 lệch khóa + 7 không có đáp án đúng):")
    print(f"     - Đánh giá được                                        : {bad_evaluated}/{bad_expected} (lỗi parse: {bad_unparsed})")
    print(f"     - Phát hiện có bất đồng (judge_answer != correct_option): {bad_caught}/{bad_evaluated} câu đánh giá được ({bad_caught}/{bad_expected} toàn bộ)")
    print(f"     - Chỉ định đúng nhãn chuẩn (A/B/C/D hoặc none)        : {bad_exact_match}/{bad_evaluated} câu đánh giá được ({bad_exact_match}/{bad_expected} toàn bộ)")
    print(f"  2. Trên nhóm {good_expected} câu đối chứng (10 đúng đơn + 1 phương án chưa rút gọn):")
    print(f"     - Đánh giá được                                        : {good_evaluated}/{good_expected} (lỗi parse: {good_unparsed})")
    print(f"     - Đồng thuận đúng với khóa sinh                        : {good_passed}/{good_evaluated} câu đánh giá được ({good_passed}/{good_expected} toàn bộ)")
    print(f"     - Báo động giả (false alarm)                           : {good_false_alarm}/{good_evaluated} câu đánh giá được")


if __name__ == "__main__":
    main()
