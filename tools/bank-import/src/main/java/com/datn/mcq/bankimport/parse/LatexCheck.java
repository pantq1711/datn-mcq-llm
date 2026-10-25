package com.datn.mcq.bankimport.parse;

import org.scilab.forge.jlatexmath.TeXFormula;

/** Kiểm LaTeX có parse được bằng JLaTeXMath (chỉ cho công cụ kiểm tra này). */
public final class LatexCheck {

    public static boolean parses(String latex) {
        try {
            new TeXFormula(latex);
            return true;
        } catch (Throwable t) {       // ParseException hoặc lỗi nạp font: coi như không parse được
            return false;
        }
    }

    private LatexCheck() {}
}
