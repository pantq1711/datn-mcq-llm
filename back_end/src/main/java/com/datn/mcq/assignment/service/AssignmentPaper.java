package com.datn.mcq.assignment.service;

import com.datn.mcq.assignment.dto.AssignmentDtos.PartScoreDto;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.AnswerInput;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.PublicPartDto;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.PublicQuestionDto;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.PublicStatementDto;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.ReviewItemDto;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.ReviewPartDto;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.ReviewStatementDto;
import com.datn.mcq.assignment.repository.AssignmentRepository.AnswerRow;
import com.datn.mcq.assignment.repository.AssignmentRepository.ItemRow;
import com.datn.mcq.bank.dto.BankItemDto;
import com.datn.mcq.bank.dto.BankItemDto.StatementDto;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.TruthValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đề của một bài giao như học sinh thấy: thành phần đề chốt lúc giao ({@code assignment_item}) cùng nội dung câu ngân hàng
 * và bảng điểm chụp lúc giao. Dựng câu cho học sinh làm (không đáp án), chấm, tính điểm theo Phần và dựng phần xem lại có
 * đáp án (chỉ dùng sau khi bài đóng). Không chạm DB.
 */
final class AssignmentPaper {

    static final List<String> PARTS = List.of("I", "II", "III");

    /** Một câu của đề: dòng {@code assignment_item} và câu ngân hàng tương ứng. */
    record Item(ItemRow row, BankItemDto bank) {
        QuestionType type() {
            return bank.questionType();
        }
    }

    private final List<Item> items;
    private final Map<Integer, Item> bySlot;
    private final Scoring scoring;

    AssignmentPaper(List<ItemRow> rows, Map<Integer, BankItemDto> bankItems, Scoring scoring) {
        List<Item> list = new ArrayList<>(rows.size());
        Map<Integer, Item> map = new LinkedHashMap<>();
        for (ItemRow row : rows) {
            Item item = new Item(row, bankItems.get(row.bankItemId()));
            list.add(item);
            map.put(row.slotId(), item);
        }
        this.items = List.copyOf(list);
        this.bySlot = map;
        this.scoring = scoring;
    }

    List<Item> items() {
        return items;
    }

    Item item(Integer slotId) {
        return bySlot.get(slotId);
    }

    int questionCount() {
        return items.size();
    }

    Scoring scoring() {
        return scoring;
    }

    BigDecimal maxScore() {
        BigDecimal max = BigDecimal.ZERO;
        for (Item item : items) {
            max = max.add(scoring.max(item.type()));
        }
        return max;
    }

    static BigDecimal score10(BigDecimal score, BigDecimal maxScore) {
        if (maxScore == null || maxScore.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return score.multiply(BigDecimal.TEN).divide(maxScore, 2, RoundingMode.HALF_UP);
    }

    // ---------- cho học sinh làm ----------

    /** Các Phần có câu, mỗi câu chỉ có đề, phương án hoặc ý (key, text). Không đáp án, nhãn, đáp số, lời giải. */
    List<PublicPartDto> publicParts() {
        List<PublicPartDto> out = new ArrayList<>();
        for (String part : PARTS) {
            List<PublicQuestionDto> qs = new ArrayList<>();
            for (Item item : items) {
                if (!part.equals(item.row().part())) {
                    continue;
                }
                BankItemDto b = item.bank();
                Map<String, String> options = item.type() == QuestionType.MCQ ? options(b) : null;
                List<PublicStatementDto> statements = item.type() == QuestionType.TF
                        ? statements(b).stream().map(s -> new PublicStatementDto(s.key().getDbValue(), s.text())).toList()
                        : null;
                qs.add(new PublicQuestionDto(item.row().slotId(), item.row().numberInPart(), item.type().getDbValue(),
                        b.questionText(), options, statements));
            }
            if (!qs.isEmpty()) {
                out.add(new PublicPartDto(part, qs));
            }
        }
        return out;
    }

    private static Map<String, String> options(BankItemDto b) {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("A", b.optionA());
        options.put("B", b.optionB());
        options.put("C", b.optionC());
        options.put("D", b.optionD());
        return options;
    }

    private static List<StatementDto> statements(BankItemDto b) {
        List<StatementDto> list = new ArrayList<>(b.statements() == null ? List.of() : b.statements());
        list.sort(Comparator.comparing(s -> s.key().getDbValue()));
        return list;
    }

    // ---------- chấm ----------

    /** Chấm đủ mọi câu (câu không có trong {@code answers} là bỏ trống). {@code answers} đã kiểm dạng. */
    List<AnswerRow> grade(Integer attemptId, Map<Integer, AnswerInput> answers) {
        List<AnswerRow> out = new ArrayList<>(items.size());
        for (Item item : items) {
            AnswerInput a = answers.get(item.row().slotId());
            BankItemDto b = item.bank();
            AttemptGrader.Graded g = switch (item.type()) {
                case MCQ -> AttemptGrader.mcq(b.correctOption(), a == null ? null : a.choice(), scoring);
                case TF -> AttemptGrader.tf(statements(b).stream().map(StatementDto::label).toList(),
                        a == null ? null : a.tf(), scoring);
                case SA -> AttemptGrader.sa(b.saAnswer(), a == null ? null : a.text(), scoring);
            };
            out.add(new AnswerRow(attemptId, item.row().slotId(), g.mcqChoice(), g.tfChoices(), g.saText(),
                    g.correctCount(), g.score()));
        }
        return out;
    }

    static BigDecimal total(List<AnswerRow> answers) {
        BigDecimal sum = BigDecimal.ZERO;
        for (AnswerRow a : answers) {
            sum = sum.add(a.score());
        }
        return sum;
    }

    /** Điểm theo Phần của một lượt đã nộp. Phần II đếm theo ý. */
    List<PartScoreDto> partScores(Map<Integer, AnswerRow> answersBySlot) {
        List<PartScoreDto> out = new ArrayList<>();
        for (String part : PARTS) {
            int correct = 0;
            int total = 0;
            BigDecimal score = BigDecimal.ZERO;
            boolean any = false;
            for (Item item : items) {
                if (!part.equals(item.row().part())) {
                    continue;
                }
                any = true;
                total += item.type() == QuestionType.TF ? 4 : 1;
                AnswerRow a = answersBySlot.get(item.row().slotId());
                if (a != null) {
                    correct += a.correctCount();
                    score = score.add(a.score());
                }
            }
            if (any) {
                out.add(new PartScoreDto(part, correct, total, score));
            }
        }
        return out;
    }

    // ---------- xem lại sau khi bài đóng ----------

    /**
     * Đề kèm lựa chọn của em, đáp án đúng, điểm câu và lời giải. CHỈ gọi khi bài đã đóng, hết ân hạn và giáo viên bật
     * {@code showSolutions}. {@code answersBySlot} rỗng khi lượt chưa nộp (mọi câu hiện là bỏ trống, 0 điểm).
     */
    List<ReviewPartDto> review(Map<Integer, AnswerRow> answersBySlot) {
        List<ReviewPartDto> out = new ArrayList<>();
        for (String part : PARTS) {
            List<ReviewItemDto> list = new ArrayList<>();
            for (Item item : items) {
                if (!part.equals(item.row().part())) {
                    continue;
                }
                list.add(reviewItem(item, answersBySlot.get(item.row().slotId())));
            }
            if (!list.isEmpty()) {
                out.add(new ReviewPartDto(part, list));
            }
        }
        return out;
    }

    private ReviewItemDto reviewItem(Item item, AnswerRow a) {
        BankItemDto b = item.bank();
        int correctCount = a == null ? 0 : a.correctCount();
        BigDecimal score = a == null ? BigDecimal.ZERO : a.score();
        Integer slotId = item.row().slotId();
        int number = item.row().numberInPart();
        String type = item.type().getDbValue();
        return switch (item.type()) {
            case MCQ -> new ReviewItemDto(slotId, number, type, b.questionText(), options(b),
                    a == null ? null : a.mcqChoice(), b.correctOption() == null ? null : b.correctOption().getDbValue(),
                    null, null, null, correctCount, score, b.explanation());
            case TF -> {
                List<StatementDto> st = statements(b);
                String choices = a == null || a.tfChoices() == null ? "----" : a.tfChoices();
                List<ReviewStatementDto> rs = new ArrayList<>(st.size());
                for (int i = 0; i < st.size(); i++) {
                    TruthValue label = st.get(i).label();
                    char c = i < choices.length() ? choices.charAt(i) : AttemptGrader.BLANK;
                    String yours = c == AttemptGrader.BLANK ? null : String.valueOf(c);
                    rs.add(new ReviewStatementDto(st.get(i).key().getDbValue(), st.get(i).text(),
                            label == null ? null : label.getDbValue(), yours,
                            yours != null && label != null && label.getDbValue().equals(yours)));
                }
                yield new ReviewItemDto(slotId, number, type, b.questionText(), null, null, null, rs, null, null,
                        correctCount, score, b.explanation());
            }
            case SA -> new ReviewItemDto(slotId, number, type, b.questionText(), null, null, null, null,
                    a == null ? null : a.saText(), b.saAnswer(), correctCount, score, b.explanation());
        };
    }
}
