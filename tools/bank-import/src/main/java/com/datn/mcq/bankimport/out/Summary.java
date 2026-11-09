package com.datn.mcq.bankimport.out;

import com.datn.mcq.bankimport.model.Question;

/** Các thuộc tính tổng hợp của một câu dùng chung cho danh mục CSV và báo cáo. */
public final class Summary {

    public final int formulaOk;
    public final int formulaPartial;
    public final int formulaUnsupported;
    public final int figure;
    public final int formulaImage;
    public final int oleDrawing;
    public final boolean structureOk;

    public Summary(Question q) {
        int ok = 0;
        int partial = 0;
        int unsupported = 0;
        for (Question.Formula f : q.formulas) {
            switch (f.status) {
                case "ok" -> ok++;
                case "partial" -> partial++;
                default -> unsupported++;
            }
        }
        int fig = 0;
        int fi = 0;
        int ole = 0;
        for (Question.ImageRef im : q.images) {
            switch (im.kind) {
                case "formula_image" -> fi++;
                case "ole_drawing" -> ole++;
                default -> fig++;
            }
        }
        this.formulaOk = ok;
        this.formulaPartial = partial;
        this.formulaUnsupported = unsupported;
        this.figure = fig;
        this.formulaImage = fi;
        this.oleDrawing = ole;
        this.structureOk = q.flags.stream().noneMatch(com.datn.mcq.bankimport.parse.DocParser::isStructural);
    }

    /** Ưu tiên: has_unsupported > has_partial > has_formula_image > all_ok. */
    public String formulaStatus() {
        if (formulaUnsupported > 0) {
            return "has_unsupported";
        }
        if (formulaPartial > 0) {
            return "has_partial";
        }
        if (formulaImage > 0) {
            return "has_formula_image";
        }
        return "all_ok";
    }

    public boolean hasImage() {
        return figure + formulaImage + oleDrawing > 0;
    }

    /** Không có hình (kể cả ảnh công thức, đối tượng vẽ) và mọi công thức đều ok. */
    public boolean usableTextOnly() {
        return !hasImage() && formulaPartial == 0 && formulaUnsupported == 0;
    }

    public static String answerText(Question q) {
        if (q.type.equals("mcq")) {
            return q.answer == null || q.answer.value == null ? "" : q.answer.value;
        }
        if (q.type.equals("tf")) {
            StringBuilder sb = new StringBuilder();
            for (Question.Statement s : q.statements) {
                sb.append(s.truth == null ? '?' : s.truth ? 'T' : 'F');
            }
            return sb.toString();
        }
        return q.answer == null || q.answer.text == null ? "" : q.answer.text;
    }
}
