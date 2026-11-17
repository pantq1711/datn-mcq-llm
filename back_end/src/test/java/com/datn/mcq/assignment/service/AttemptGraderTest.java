package com.datn.mcq.assignment.service;

import com.datn.mcq.assignment.service.AttemptGrader.Graded;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.TruthValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Chấm ba dạng câu của bài làm trực tuyến (docs/design/lam-bai-truc-tuyen.md mục 4). Hàm thuần, không cần Spring.
 */
class AttemptGraderTest {

    private static final Scoring SCORING = new Scoring(new BigDecimal("0.25"), new BigDecimal("0.5"),
            List.of(new BigDecimal("0"), new BigDecimal("0.1"), new BigDecimal("0.25"), new BigDecimal("0.5"),
                    new BigDecimal("1.0")));

    private static final List<TruthValue> LABELS = List.of(TruthValue.TRUE, TruthValue.FALSE, TruthValue.FALSE,
            TruthValue.TRUE);

    private static void assertScore(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "điểm mong đợi " + expected + ", nhận " + actual);
    }

    // ---------- Trắc nghiệm ----------

    @Test
    void mcqCorrectWrongAndBlank() {
        Graded right = AttemptGrader.mcq(Option.B, "B", SCORING);
        assertEquals(1, right.correctCount());
        assertScore("0.25", right.score());
        assertEquals("B", right.mcqChoice());

        Graded wrong = AttemptGrader.mcq(Option.B, "C", SCORING);
        assertEquals(0, wrong.correctCount());
        assertScore("0", wrong.score());
        assertEquals("C", wrong.mcqChoice());

        Graded blank = AttemptGrader.mcq(Option.B, null, SCORING);
        assertEquals(0, blank.correctCount());
        assertScore("0", blank.score());
        assertNull(blank.mcqChoice());
    }

    // ---------- Đúng/Sai: điểm theo số ý đúng 0 / 0,1 / 0,25 / 0,5 / 1,0 ----------

    private static Map<String, String> tfAnswer(String a, String b, String c, String d) {
        Map<String, String> m = new LinkedHashMap<>();
        if (a != null) {
            m.put("a", a);
        }
        if (b != null) {
            m.put("b", b);
        }
        if (c != null) {
            m.put("c", c);
        }
        if (d != null) {
            m.put("d", d);
        }
        return m;
    }

    @ParameterizedTest(name = "tf {0} -> {1} ý đúng, {2} điểm")
    @CsvSource({
            // nhãn a-d = T,F,F,T; '-' là bỏ trống
            "TFFT, 4, 1.0",
            "TFFF, 3, 0.5",
            "TTF-, 2, 0.25",
            "T---, 1, 0.1",
            "FTTF, 0, 0",
            "----, 0, 0",
    })
    void tfScoreFollowsNumberOfCorrectStatements(String choices, int correct, String score) {
        String[] v = new String[4];
        for (int i = 0; i < 4; i++) {
            v[i] = choices.charAt(i) == '-' ? null : String.valueOf(choices.charAt(i));
        }
        Graded g = AttemptGrader.tf(LABELS, tfAnswer(v[0], v[1], v[2], v[3]), SCORING);
        assertEquals(correct, g.correctCount());
        assertScore(score, g.score());
        assertEquals(choices, g.tfChoices());
    }

    @Test
    void tfNullAnswerIsAllBlank() {
        Graded g = AttemptGrader.tf(LABELS, null, SCORING);
        assertEquals(0, g.correctCount());
        assertEquals("----", g.tfChoices());
    }

    // ---------- Trả lời ngắn: so theo giá trị sau chuẩn hóa ----------

    @ParameterizedTest(name = "sa khóa [{0}] gõ [{1}] -> {2}")
    @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
            "2,5|2,5|true",
            "2,5|2.5|true",
            "2,5|2,50|true",
            "2,5| 2,5 |true",
            "2,5|2,5 cm|false",
            "2,5|x = 2,5|false",
            "2,5|2,51|false",
            "-3|-3|true",
            "-3|−3|true",
            "-3|-3,0|true",
            "-3|3|false",
            "1234|1 234|true",
            "1234|1.234|false",
            "0,5|0.5|true",
            "0,5|0,50|true",
            "0,5|,5|false",
            "0,5|+0,5|false",
            "0,33|0,333|false",
            "5|5|true",
    })
    void saComparesByValue(String key, String typed, boolean expected) {
        Graded g = AttemptGrader.sa(key, typed, SCORING);
        assertEquals(expected ? 1 : 0, g.correctCount(), "khóa " + key + ", gõ [" + typed + "]");
        assertScore(expected ? "0.5" : "0", g.score());
        assertEquals(typed.strip(), g.saText());
    }

    @Test
    void saBlankIsWrongAndStoredAsNull() {
        for (String typed : new String[] {null, "", "   "}) {
            Graded g = AttemptGrader.sa("5", typed, SCORING);
            assertEquals(0, g.correctCount());
            assertScore("0", g.score());
            assertNull(g.saText());
        }
    }

    // ---------- Bảng điểm ----------

    @Test
    void scoringRoundTripsThroughJson() {
        Scoring back = Scoring.fromJson(SCORING.toJson());
        assertScore("0.25", back.mcq());
        assertScore("0.5", back.sa());
        assertEquals(5, back.tf().size());
        assertScore("1.0", back.tfScore(4));
        assertScore("0.1", back.tfScore(1));
    }
}
