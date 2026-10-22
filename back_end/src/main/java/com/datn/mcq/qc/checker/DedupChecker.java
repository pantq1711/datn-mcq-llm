package com.datn.mcq.qc.checker;

import com.datn.mcq.common.util.ShortAnswerValue;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.qc.dto.DedupOutcome;
import com.datn.mcq.qc.dto.DedupStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * So trùng một câu với tập ứng viên (hàm thuần: không Spring, DB hay LLM).
 *
 * <p>Hai tầng (quyết định 1, 2026-10-21):
 * <ol>
 *   <li>Cùng dấu vân tay sau chuẩn hóa: trùng, điểm 1.0.</li>
 *   <li>Cosine 3-gram ký tự trên văn bản chuẩn {@code >= threshold}, với hai chốt chặn:
 *       số liệu và công thức trong thân câu giống nhau, và hai câu không lệch nhau về phủ định.</li>
 * </ol>
 * Ứng viên bị chốt chặn loại không được dùng làm "câu gần nhất", nên khi không trùng
 * điểm lưu lại luôn nhỏ hơn ngưỡng (khớp enums.md mục 3: trùng khi similarity >= tau).
 *
 * <p>V4: chỉ so với ứng viên cùng dạng câu. Văn bản chuẩn của câu sa là thân câu đã chuẩn hóa cộng khóa chuẩn của
 * đáp số ({@link #answerKey(String)}); hai chốt chặn vẫn chỉ xét thân câu. Văn bản chuẩn của câu tf là đề dẫn cộng
 * 4 ý đã chuẩn hóa và sắp xếp (không gồm nhãn); hai chốt chặn xét cả đề dẫn và 4 ý.
 */
public final class DedupChecker {

    /**
     * Ngưỡng tau TẠM THỜI, CHƯA hiệu chỉnh trên gold set (dedup_gold_pair). Chưa có precision hay recall.
     *
     * <p>Bằng chứng (2026-10-21, 78 câu gemini-3.5-flash-lite trong llm-probe, 3003 cặp, xem tay, không có nhãn độc lập):
     * cặp khác bài cao nhất qua cả hai chốt chặn là "Phương trình ... bậc nhất" với "Bất phương trình ... bậc nhất"
     * ở 0.8386; cặp cùng bài thấp nhất rõ ràng là "Khai triển (x+2)^2" ở 0.9128; vùng 0.8773-0.9054 là cùng thân câu
     * nhưng khác 2-3 phương án. Chọn 0.90 để ưu tiên tránh báo trùng nhầm, vì duplicate là trạng thái cuối.
     */
    public static final double PROVISIONAL_THRESHOLD = 0.90;

    private DedupChecker() {}

    public static DedupOutcome check(DedupCandidate subject, List<DedupCandidate> candidates, double threshold) {
        if (!(threshold > 0.0 && threshold <= 1.0)) {
            throw new IllegalArgumentException("Ngưỡng dedup phải nằm trong (0, 1], nhận " + threshold);
        }
        if (subject == null || !subject.hasContent()) {
            throw new IllegalArgumentException("Câu cần so trùng thiếu thân câu hoặc phương án");
        }
        String subjectText = canonical(subject);
        List<String> subjectSignature = signature(subject);
        String subjectNegation = negationKey(subject);

        List<DedupCandidate> ordered = new ArrayList<>();
        if (candidates != null) {
            for (DedupCandidate c : candidates) {
                // V4: chỉ so với câu cùng dạng (quyết định 11 ngày 22/10).
                if (c != null && c.questionType() == subject.questionType() && c.hasContent()
                        && !Objects.equals(c.questionId(), subject.questionId())) {
                    ordered.add(c);
                }
            }
        }
        ordered.sort(Comparator.comparing(DedupCandidate::questionId,
                Comparator.nullsLast(Comparator.naturalOrder())));

        Integer fingerprintMatch = null;
        Integer duplicateId = null;
        double duplicateScore = -1.0;
        Integer nearestId = null;
        double nearestScore = -1.0;

        for (DedupCandidate c : ordered) {
            String candidateText = canonical(c);
            if (candidateText.equals(subjectText)) {
                if (fingerprintMatch == null) {
                    fingerprintMatch = c.questionId();
                }
                continue;
            }
            boolean sameSignature = subjectSignature.equals(signature(c));
            boolean sameNegation = subjectNegation.equals(negationKey(c));
            if (!sameSignature || !sameNegation) {
                continue;
            }
            double score = McqTextNormalizer.trigramCosine(subjectText, candidateText);
            // Duyệt theo id tăng dần và chỉ thay khi điểm lớn hơn hẳn: hòa điểm thì giữ id nhỏ nhất.
            if (score >= threshold) {
                if (score > duplicateScore) {
                    duplicateScore = score;
                    duplicateId = c.questionId();
                }
            } else if (score > nearestScore) {
                nearestScore = score;
                nearestId = c.questionId();
            }
        }

        if (fingerprintMatch != null) {
            return new DedupOutcome(DedupStatus.DUPLICATE, 1.0, fingerprintMatch, threshold,
                    DedupOutcome.Layer.FINGERPRINT, "Trùng dấu vân tay với câu id=" + fingerprintMatch);
        }
        if (duplicateId != null) {
            return new DedupOutcome(DedupStatus.DUPLICATE, duplicateScore, duplicateId, threshold,
                    DedupOutcome.Layer.TRIGRAM, "Cosine 3-gram " + duplicateScore + " >= " + threshold
                    + " với câu id=" + duplicateId + ", cùng số liệu/công thức và cùng phủ định");
        }
        return new DedupOutcome(DedupStatus.NO_DUPLICATE,
                nearestId != null ? nearestScore : null, nearestId, threshold,
                DedupOutcome.Layer.NONE, "Không trùng với " + ordered.size() + " ứng viên");
    }

    private static String canonical(DedupCandidate c) {
        if (c.questionType() == QuestionType.SA) {
            return McqTextNormalizer.normalize(c.questionText()) + SA_ANSWER_SEPARATOR + answerKey(c.saAnswer());
        }
        if (c.questionType() == QuestionType.TF) {
            // Đề dẫn + 4 ý đã chuẩn hóa và sắp xếp: đảo thứ tự ý vẫn trùng. Nhãn Đúng/Sai không tham gia.
            List<String> statements = new ArrayList<>();
            for (String s : c.statements()) {
                statements.add(McqTextNormalizer.normalize(s));
            }
            statements.sort(null);
            return McqTextNormalizer.normalize(c.questionText()) + SA_ANSWER_SEPARATOR + String.join(" | ", statements);
        }
        return McqTextNormalizer.canonicalText(c.questionText(), c.optionA(), c.optionB(), c.optionC(), c.optionD());
    }

    /** Chốt chặn số liệu: mcq/sa xét thân câu; tf gộp số liệu và công thức của đề dẫn và 4 ý (đã sắp xếp). */
    private static List<String> signature(DedupCandidate c) {
        if (c.questionType() != QuestionType.TF) {
            return McqTextNormalizer.stemSignature(c.questionText());
        }
        List<String> all = new ArrayList<>(McqTextNormalizer.stemSignature(c.questionText()));
        for (String s : c.statements()) {
            all.addAll(McqTextNormalizer.stemSignature(s));
        }
        all.sort(null);
        return all;
    }

    /**
     * Chốt chặn phủ định: mcq/sa xét thân câu; tf so phủ định của đề dẫn và danh sách phủ định của 4 ý đã sắp xếp
     * (thiết kế bản sửa 1, mục 6).
     */
    private static String negationKey(DedupCandidate c) {
        String stem = String.valueOf(McqTextNormalizer.hasNegation(c.questionText()));
        if (c.questionType() != QuestionType.TF) {
            return stem;
        }
        List<Boolean> perStatement = new ArrayList<>();
        for (String s : c.statements()) {
            perStatement.add(McqTextNormalizer.hasNegation(s));
        }
        perStatement.sort(null);
        return stem + ":" + perStatement;
    }

    private static final String SA_ANSWER_SEPARATOR = " || ";

    /**
     * Phần đáp số trong văn bản chuẩn của câu sa (bản sửa 1 mục 6): khóa chuẩn của giá trị, nên "-0,75" và
     * "$-\frac{3}{4}$" như nhau. Đáp số không phân tích được (chỉ xảy ra với dữ liệu cũ khi bộ phân tích đổi) dùng
     * "raw:" + chuỗi đã bỏ khoảng trắng; tiền tố bảo đảm không trùng một khóa hợp lệ.
     */
    static String answerKey(String saAnswer) {
        ShortAnswerValue.ParseResult parsed = ShortAnswerValue.parse(saAnswer, ShortAnswerValue.Mode.LENIENT);
        if (parsed.ok()) {
            return parsed.value().canonicalKey();
        }
        return "raw:" + (saAnswer == null ? "" : saAnswer.replaceAll("\\s+", ""));
    }
}
