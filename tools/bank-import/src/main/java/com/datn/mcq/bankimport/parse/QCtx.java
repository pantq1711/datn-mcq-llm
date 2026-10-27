package com.datn.mcq.bankimport.parse;

import com.datn.mcq.bankimport.docx.RichText;
import com.datn.mcq.bankimport.docx.RichText.Obj;
import com.datn.mcq.bankimport.model.Question;
import com.datn.mcq.bankimport.model.Question.Formula;
import com.datn.mcq.bankimport.model.Question.ImageRef;
import com.datn.mcq.bankimport.mtef.FormulaResult;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ngữ cảnh kết xuất của một câu: đổi {@link RichText} thành chuỗi có {@code $latex$}, token {@code ⟦fN⟧} / {@code ⟦imgN⟧}
 * và chữ mũ gõ tay thành {@code $^{...}$}; đồng thời ghi công thức, ảnh và cờ vào câu. Id cấp theo thứ tự kết xuất.
 */
final class QCtx {

    private static final double FORMULA_IMAGE_MAX_CM = 0.9;      // ảnh thấp hơn mức này luôn là ảnh công thức
    private static final double WMF_FORMULA_IMAGE_MAX_CM = 2.0;  // WMF thấp hơn mức này cũng là ảnh công thức

    private final Question q;
    private final String docId;
    private final FormulaService formulas;
    private final Map<Obj, String> tokens = new IdentityHashMap<>();

    QCtx(Question q, String docId, FormulaService formulas) {
        this.q = q;
        this.docId = docId;
        this.formulas = formulas;
    }

    String render(RichText rt, String role) {
        return render(rt, role, -1, -1);
    }

    /** Kết xuất, bỏ các ký tự trong [omitFrom, omitTo) (nhãn mức, "Câu N.", nhãn phương án). */
    String render(RichText rt, String role, int omitFrom, int omitTo) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < rt.length()) {
            if (i >= omitFrom && i < omitTo) {
                i++;
                continue;
            }
            char ch = rt.s.charAt(i);
            if (rt.objIdx[i] >= 0) {
                Obj o = rt.objs.get(rt.objIdx[i]);
                String tok = token(o, role);
                boolean isFormula = o.equation() && tok.startsWith("$") && tok.endsWith("$");
                if (isFormula && precededByLetter(sb)) {
                    sb.append(' ');
                }
                sb.append(tok);
                if (isFormula && followedByLetterOrDigit(rt, i + 1, omitFrom, omitTo)) {
                    sb.append(' ');
                }
                i++;
            } else if (rt.script[i] != 0) {
                byte kind = rt.script[i];
                StringBuilder run = new StringBuilder();
                while (i < rt.length() && rt.script[i] == kind && rt.objIdx[i] < 0) {
                    run.append(rt.s.charAt(i++));
                }
                String body = run.toString().strip();
                if (!body.isEmpty()) {
                    sb.append(kind == 1 ? "$^{" : "$_{").append(body).append("}$");
                    q.addFlag("plain_math_vertalign");
                }
            } else {
                sb.append(ch);
                i++;
            }
        }
        return tidy(sb.toString());
    }

    private static boolean precededByLetter(StringBuilder sb) {
        return !sb.isEmpty() && Character.isLetter(sb.codePointBefore(sb.length()));
    }

    private static boolean followedByLetterOrDigit(RichText rt, int next, int omitFrom, int omitTo) {
        while (next < rt.length() && next >= omitFrom && next < omitTo) {
            next++;
        }
        if (next < rt.length() && rt.objIdx[next] < 0 && rt.script[next] == 0) {
            return Character.isLetterOrDigit(rt.s.codePointAt(next));
        }
        return false;
    }

    /** Chỉ đăng ký ảnh (không cần chuỗi), dùng cho ảnh nằm ngoài phương án, ví dụ ô ảnh của bảng bố cục. */
    void registerImage(Obj o, String role) {
        if (!o.equation()) {
            token(o, role);
        }
    }

    /** Khoảng trắng ngang gộp một, bỏ dòng trống, bỏ ký hiệu Wingdings/Webdings. */
    static String tidy(String s) {
        String t = s.replace(' ', ' ').replace("￼", "");
        t = t.replaceAll("[\\t\\u2002\\u2003\\u2009 ]+", " ");
        List<String> lines = new ArrayList<>();
        for (String line : t.split("\n")) {
            String l = line.strip();
            if (!l.isEmpty()) {
                lines.add(l);
            }
        }
        return String.join("\n", lines);
    }

    private String token(Obj o, String role) {
        String existing = tokens.get(o);
        if (existing != null) {
            return existing;
        }
        String tok = o.equation() ? formulaToken(o) : imageToken(o, role);
        tokens.put(o, tok);
        return tok;
    }

    private String formulaToken(Obj o) {
        FormulaResult r = formulas.convert(o.ref());
        if ("cong_thuc_rong".equals(r.reason())) {      // đối tượng công thức trống: không để lại token
            q.addFlag("empty_formula");
            return "";
        }
        Formula f = new Formula();
        f.id = "f" + (q.formulas.size() + 1);
        f.status = r.status().name().toLowerCase();
        f.latex = r.latex();
        f.reason = r.reason();
        f.sourceRef = o.ref();
        f.previewRef = o.previewRef();
        q.formulas.add(f);
        if (r.status() == FormulaResult.Status.UNSUPPORTED) {
            q.addFlag("formula_unsupported");
            return "⟦" + f.id + "⟧";
        }
        if (r.status() == FormulaResult.Status.PARTIAL) {
            q.addFlag("formula_partial");
        }
        return "$" + r.latex() + "$";
    }

    private String imageToken(Obj o, String role) {
        ImageRef im = new ImageRef();
        int n = q.images.size() + 1;
        im.id = "img" + n;
        im.kind = classify(o);
        im.role = role;
        im.sourceExt = o.ext();
        im.widthCm = round(o.widthCm());
        im.heightCm = round(o.heightCm());
        im.sourceRef = o.ref();
        im.file = "images/" + docId + "/" + q.id + "-" + n + "." + o.ext();
        q.images.add(im);
        q.hasImage = true;
        if (!im.kind.equals("figure")) {
            q.addFlag(im.kind);
        }
        return "⟦" + im.id + "⟧";
    }

    static String classify(Obj o) {
        if (o.progId() != null) {
            return "ole_drawing";
        }
        Double h = o.heightCm();
        if (h != null && (h < FORMULA_IMAGE_MAX_CM || ("wmf".equals(o.ext()) && h < WMF_FORMULA_IMAGE_MAX_CM))) {
            return "formula_image";
        }
        return "figure";
    }

    private static Double round(Double v) {
        return v == null ? null : Math.round(v * 100.0) / 100.0;
    }
}
