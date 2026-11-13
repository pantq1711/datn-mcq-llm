"""Công thức thống kê dùng trong chương 4 (chỉ thư viện chuẩn). Mỗi hàm được test so với số tính tay."""
import math
from collections import Counter

Z95 = 1.959963984540054


def wilson(k, n, z=Z95):
    """Khoảng tin cậy Wilson cho tỉ lệ k/n. Trả (p, thấp, cao); n = 0 thì (None, None, None)."""
    if n == 0:
        return None, None, None
    p = k / n
    z2 = z * z
    den = 1 + z2 / n
    center = (p + z2 / (2 * n)) / den
    half = z * math.sqrt(p * (1 - p) / n + z2 / (4 * n * n)) / den
    return p, max(0.0, center - half), min(1.0, center + half)


def cohen_kappa(a, b):
    """Cohen's kappa của hai dãy nhãn cùng độ dài (bỏ cặp có giá trị rỗng). Trả (kappa, số cặp, tỉ lệ đồng ý).
    Mọi cặp cùng một nhãn duy nhất (p_e = 1) thì kappa = None (không xác định)."""
    pairs = [(x, y) for x, y in zip(a, b) if x not in (None, "") and y not in (None, "")]
    n = len(pairs)
    if n == 0:
        return None, 0, None
    po = sum(1 for x, y in pairs if x == y) / n
    ca = Counter(x for x, _ in pairs)
    cb = Counter(y for _, y in pairs)
    pe = sum(ca[c] * cb[c] for c in set(ca) | set(cb)) / (n * n)
    if pe == 1:
        return None, n, po
    return (po - pe) / (1 - pe), n, po


def _hypergeom_pmf(x, r1, c1, n):
    return math.comb(c1, x) * math.comb(n - c1, r1 - x) / math.comb(n, r1)


def fisher_exact(a, b, c, d):
    """Fisher exact hai phía cho bảng 2x2 [[a, b], [c, d]]: tổng xác suất các bảng cùng biên có xác suất <= bảng quan
    sát (cách của scipy.stats.fisher_exact). Trả (odds ratio, p)."""
    r1, c1, n = a + b, a + c, a + b + c + d
    lo, hi = max(0, r1 + c1 - n), min(r1, c1)
    p_obs = _hypergeom_pmf(a, r1, c1, n)
    p = sum(_hypergeom_pmf(x, r1, c1, n) for x in range(lo, hi + 1)
            if _hypergeom_pmf(x, r1, c1, n) <= p_obs * (1 + 1e-7))
    if b * c == 0:
        odds = math.inf if a * d > 0 else math.nan
    else:
        odds = a * d / (b * c)
    return odds, min(1.0, p)


def prf(tp, fp, fn):
    """Precision, recall, F1; mẫu số 0 thì None."""
    p = tp / (tp + fp) if tp + fp else None
    r = tp / (tp + fn) if tp + fn else None
    if p is None or r is None:
        return p, r, None
    return p, r, (2 * p * r / (p + r) if p + r > 0 else 0.0)


def recall_trong_so(p_loi_co_co, p_loi_khong_co, n_co, n_khong):
    """Recall ước lượng cho TỔNG THỂ khi mẫu phân tầng theo cờ (tỉ lệ lấy mẫu khác nhau giữa hai tầng):
    recall = N_cờ·p_cờ / (N_cờ·p_cờ + N_không·p_không), p = tỉ lệ câu lỗi trong tầng (đo trên mẫu), N = cỡ tầng."""
    if p_loi_co_co is None or p_loi_khong_co is None:
        return None
    loi_co = n_co * p_loi_co_co
    loi_khong = n_khong * p_loi_khong_co
    return loi_co / (loi_co + loi_khong) if loi_co + loi_khong > 0 else None


def chenh_lech(k1, n1, k2, n2):
    """Chênh lệch tỉ lệ p1 - p2 kèm khoảng Newcombe (hybrid Wilson, phương pháp 10) 95%."""
    p1, l1, u1 = wilson(k1, n1)
    p2, l2, u2 = wilson(k2, n2)
    if p1 is None or p2 is None:
        return None, None, None
    d = p1 - p2
    lo = d - math.sqrt((p1 - l1) ** 2 + (u2 - p2) ** 2)
    hi = d + math.sqrt((u1 - p1) ** 2 + (p2 - l2) ** 2)
    return d, lo, hi
