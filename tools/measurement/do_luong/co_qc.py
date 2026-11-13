"""Tính lý do "cần xem kỹ" và cờ nội dung của một câu hệ thống từ dữ liệu QC đã xuất (sql/d2-xuat-cau.sql).

Bản chép của hai chỗ trong backend, GIỮ KHỚP bằng fixture chung tests/fixtures/co-qc-parity.json:
  - QcSummaryService.QcSummary.reviewReasons (back_end/.../qc/service/QcSummaryService.java)
  - ExamViewService.toItem: thay solver_not_run bằng solver_pending/solver_failed, thêm grounding_pending/failed
Cờ nội dung = hasContentFlag (front_end/src/utils/exam.utils.ts): có lý do và không phải chỉ toàn similar_in_exam.
Lý do similar_in_exam tính lúc xem đề (so các câu trong cùng đề), không có trong dữ liệu xuất, nên không bao giờ xuất hiện
ở đây; hasContentFlag vì vậy bằng "có ít nhất một lý do".
"""
import re

SOLVER_NOT_RUN = "solver_not_run"
SOLVER_PENDING = "solver_pending"
SOLVER_FAILED = "solver_failed"
SOLVER_INCONSISTENT = "solver_inconsistent"
SOLVER_AMBIGUOUS = "solver_ambiguous"
SOLVER_UNPARSEABLE = "solver_unparseable"
SOLVER_MULTIPLE_CORRECT = "solver_multiple_correct"
SOLVER_NO_CORRECT = "solver_no_correct"
STATEMENT_UNDECIDABLE = "statement_undecidable"
DEDUP_NOT_RUN = "dedup_not_run"
WARNING_PREFIX = "warning: "
EXPLANATION_DRAFT_SUSPECT = "explanation_draft_suspect"
DUPLICATE_PREFIX = "duplicate: "
GROUNDING_NOT_SUPPORTED = "grounding_not_supported"
GROUNDING_UNKNOWN = "grounding_unknown"
GROUNDING_PENDING = "grounding_pending"
GROUNDING_FAILED = "grounding_failed"
SIMILAR_IN_EXAM_PREFIX = "similar_in_exam:"

# QcSummaryService.AUTO_FIXED_WARNING_PATTERNS (Java Pattern.matches = khớp toàn chuỗi -> re.fullmatch)
_AUTO_FIXED = [
    re.compile(r"json: đã sửa .*", re.DOTALL),
    re.compile(r"[a-z_]+: đã tự đóng \$"),
    re.compile(r"[a-z_]+: đã tự bọc \$ quanh .*", re.DOTALL),
    re.compile(r"statement_[a-d]: đã bỏ tiền tố .*", re.DOTALL),
]
# ExplanationDraftLeak.SUSPECT_WARNING
EXPLANATION_DRAFT_WARNING = "explanation: lời giải có thể còn phần nháp (tính lại, làm lại, sửa lại)"
# TfLabelPattern.isDeviationWarning, McqAnswerPlan.isDeviationWarning
_DEVIATION_PREFIXES = ("label: khác mẫu mục tiêu ", "label: khác mẫu bắt buộc ", "correct_option: khác chữ đã giao ")
# QuestionWordingCheck.isSymbolSuggestedWarning (String.matches = toàn chuỗi; "." không khớp xuống dòng)
_SYMBOL_SUGGESTED = re.compile(r"[a-z_]+: wording_symbol_suggested \(.*\)")

# Nhóm bước QC của từng lý do, để tách P/R theo bước (khoa.csv).
BUOC = {
    "solver": (SOLVER_NOT_RUN, SOLVER_PENDING, SOLVER_FAILED, SOLVER_INCONSISTENT, SOLVER_AMBIGUOUS,
               SOLVER_UNPARSEABLE, SOLVER_MULTIPLE_CORRECT, SOLVER_NO_CORRECT, STATEMENT_UNDECIDABLE),
    "grounding": (GROUNDING_NOT_SUPPORTED, GROUNDING_UNKNOWN, GROUNDING_PENDING, GROUNDING_FAILED),
    "dedup": (DEDUP_NOT_RUN,),
    "format": (EXPLANATION_DRAFT_SUSPECT,),
}


def is_auto_fixed_warning(w):
    return w is not None and any(p.fullmatch(w) for p in _AUTO_FIXED)


def _flags_warning(w):
    if w == EXPLANATION_DRAFT_WARNING:
        return EXPLANATION_DRAFT_SUSPECT
    if is_auto_fixed_warning(w) or w.startswith(_DEVIATION_PREFIXES) or _SYMBOL_SUGGESTED.fullmatch(w):
        return None
    return WARNING_PREFIX + w


def review_reasons(qc):
    """Lý do "cần xem kỹ" như giáo viên thấy ở trang đề. qc: đối tượng "qc" của một dòng xuất."""
    reasons = []
    duplicate_of = qc.get("dedup_compared_to") if qc.get("dedup_ran") and qc.get("dedup_passed") is False else None
    answer_passed = qc.get("answer_passed") if qc.get("answer_ran") else None
    judge = qc.get("judge_answer")
    option_check = bool(qc.get("solver_option_check"))
    if duplicate_of is not None:
        reasons.append(DUPLICATE_PREFIX + str(duplicate_of))
    elif answer_passed is None:
        reasons.append(SOLVER_NOT_RUN)
    elif not answer_passed:
        kind = qc.get("judge_sa_kind")
        if kind == "ambiguous":
            reasons.append(SOLVER_AMBIGUOUS)
        elif kind == "unparseable":
            reasons.append(SOLVER_UNPARSEABLE)
        elif option_check and judge == "multiple":
            reasons.append(SOLVER_MULTIPLE_CORRECT)
        elif option_check and judge == "none":
            reasons.append(SOLVER_NO_CORRECT)
        else:
            reasons.append(SOLVER_INCONSISTENT)
    if qc.get("answer_ran") and qc.get("has_undecidable"):
        reasons.append(STATEMENT_UNDECIDABLE)
    verdict = qc.get("grounding_verdict")
    if verdict == "not_supported":
        reasons.append(GROUNDING_NOT_SUPPORTED)
    elif verdict == "unknown":
        reasons.append(GROUNDING_UNKNOWN)
    if not qc.get("dedup_ran"):
        reasons.append(DEDUP_NOT_RUN)
    for w in qc.get("format_warnings") or []:
        r = _flags_warning(w)
        if r is not None:
            reasons.append(r)
    # ExamViewService.addGroundingJobReason: chỉ khi chưa có kết luận not_supported/unknown
    if GROUNDING_NOT_SUPPORTED not in reasons and GROUNDING_UNKNOWN not in reasons:
        job = qc.get("grounding_job_status")
        if job in ("pending", "running"):
            reasons.append(GROUNDING_PENDING)
        elif job == "failed":
            reasons.append(GROUNDING_FAILED)
    # ExamViewService.toItem: solver_not_run -> đang kiểm / thất bại
    if SOLVER_NOT_RUN in reasons:
        job = qc.get("answer_job_status")
        i = reasons.index(SOLVER_NOT_RUN)
        if job in ("pending", "running"):
            reasons[i] = SOLVER_PENDING
        elif job == "failed" or qc.get("solver_error"):
            reasons[i] = SOLVER_FAILED
    return reasons


def has_content_flag(reasons):
    """hasContentFlag (exam.utils.ts) với needsReview = (có lý do)."""
    if not reasons:
        return False
    return any(not r.startswith(SIMILAR_IN_EXAM_PREFIX) for r in reasons)


def buoc_cua(reason):
    """Bước QC sinh ra lý do: solver, grounding, dedup, format."""
    if reason.startswith(DUPLICATE_PREFIX):
        return "dedup"
    if reason.startswith(WARNING_PREFIX):
        return "format"
    for buoc, ds in BUOC.items():
        if reason in ds:
            return buoc
    return "khac"
