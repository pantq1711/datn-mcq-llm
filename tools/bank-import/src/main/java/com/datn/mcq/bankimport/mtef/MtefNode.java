package com.datn.mcq.bankimport.mtef;

import java.util.List;

/** Cây MTEF v5 rút gọn: chỉ giữ cấu trúc cần cho việc dịch sang LaTeX (bỏ kích thước, màu, nudge). */
sealed interface MtefNode {

    /** LINE (một ô). {@code isNull}: ô trống (LINE(null)). */
    record Line(List<MtefNode> items, boolean isNull) implements MtefNode {}

    /**
     * CHAR. {@code face}: typeface (đã trừ 128), đã quy kiểu người dùng/User 1 về TEXT khi phân giải được.
     * {@code unresolved}: lý do không phân giải được typeface (null nếu bình thường).
     */
    record Chr(int face, int code, List<Integer> embells, String unresolved) implements MtefNode {}

    /** TMPL: {@code children} gồm các ô (LINE) rồi các ký tự phụ (ngoặc, dấu mũ...). */
    record Tmpl(int selector, int variation, List<MtefNode> children) implements MtefNode {}

    /** PILE: các dòng xếp chồng. {@code halign}: 1 trái, 2 giữa, 3 phải. */
    record Pile(int halign, List<MtefNode> children) implements MtefNode {}

    record Matrix(int rows, int cols, List<MtefNode> children) implements MtefNode {}
}
