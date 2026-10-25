package com.datn.mcq.bankimport.docx;

import java.util.List;

/** Khối ở mức thân tài liệu: đoạn văn hoặc bảng. */
public sealed interface Block {

    /** Một đoạn; {@code numbered}: có numPr (đánh số tự động). */
    record Para(RichText text, boolean numbered) implements Block {}

    record Cell(int span, List<Para> paras) {}

    record Tbl(List<List<Cell>> rows) implements Block {}
}
