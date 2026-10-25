package com.datn.mcq.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DbEnumTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final List<DbEnumConverter> CONVERTERS = List.of(
                new Strategy.Conv(),
                new Purpose.Conv(),
                new Difficulty.Conv(),
                new Option.Conv(),
                new AnswerChoice.Conv(),
                new QuestionStatus.Conv(),
                new CheckType.Conv(),
                new ReviewDecision.Conv(),
                new ReasonCode.Conv(),
                new SamplingStratum.Conv(),
                new GoldSplit.Conv(),
                new ExamStatus.Conv(),
                new JobStatus.Conv(),
                new SourceType.Conv(),
                new UserRole.Conv(),
                new QuestionType.Conv(),
                new StatementKey.Conv(),
                new TruthValue.Conv(),
                new TruthChoice.Conv(),
                new SaAnswerKind.Conv(),
                new SaJudgeKind.Conv());

    private static final List<Class<? extends Enum<?>>> ENUMS = List.of(
                Strategy.class,
                Purpose.class,
                Difficulty.class,
                Option.class,
                AnswerChoice.class,
                QuestionStatus.class,
                CheckType.class,
                ReviewDecision.class,
                ReasonCode.class,
                SamplingStratum.class,
                GoldSplit.class,
                ExamStatus.class,
                JobStatus.class,
                SourceType.class,
                UserRole.class,
                QuestionType.class,
                StatementKey.class,
                TruthValue.class,
                TruthChoice.class,
                SaAnswerKind.class,
                SaJudgeKind.class);

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void mỗiGiáTrịChuyểnQuaLạiĐúng() {
        for (DbEnumConverter c : CONVERTERS) {
            for (Object o : ((Class<?>) typeOf(c)).getEnumConstants()) {
                Enum e = (Enum) o;
                String db = c.convertToDatabaseColumn(e);
                assertEquals(e, c.convertToEntityAttribute(db));
            }
        }
    }

    @Test
    void giáTrịDbKhôngTrùngTrongCùngMộtEnum() {
        for (Class<? extends Enum<?>> type : ENUMS) {
            Set<String> seen = new HashSet<>();
            for (Enum<?> e : type.getEnumConstants()) {
                assertTrue(seen.add(((DbEnum) e).getDbValue()),
                        "Trùng giá trị DB trong " + type.getSimpleName());
            }
        }
    }

    @Test
    void chuỗiHiệnTrongHợpĐồngEnums() {
        assertEquals("few-shot-structured", Strategy.FEW_SHOT_STRUCTURED.getDbValue());
        assertEquals(Strategy.ZERO_SHOT, new Strategy.Conv().convertToEntityAttribute("zero-shot"));
        assertEquals(AnswerChoice.MULTIPLE, new AnswerChoice.Conv().convertToEntityAttribute("multiple"));
        assertEquals(Option.B, new Option.Conv().convertToEntityAttribute("B "));
        assertEquals("answer_check", CheckType.ANSWER_CHECK.getDbValue());
    }

    /** V4: chuỗi DB của các enum mới khớp docs/contract/enums.md và CHECK của V4. */
    @Test
    void enumV4KhớpHợpĐồng() {
        assertEquals(List.of("mcq", "tf", "sa"), dbValues(QuestionType.class));
        assertEquals(List.of("a", "b", "c", "d"), dbValues(StatementKey.class));
        assertEquals(List.of("T", "F"), dbValues(TruthValue.class));
        assertEquals(List.of("T", "F", "undecidable"), dbValues(TruthChoice.class));
        assertEquals(List.of("number", "ambiguous"), dbValues(SaAnswerKind.class));
        assertEquals(List.of("number", "ambiguous", "unparseable"), dbValues(SaJudgeKind.class));
        assertEquals("statement_undecidable", ReasonCode.STATEMENT_UNDECIDABLE.getDbValue());
        assertEquals("answer_ambiguous", ReasonCode.ANSWER_AMBIGUOUS.getDbValue());
    }

    /** V4: mã lý do theo dạng câu (quyết định 3 ngày 22/10). */
    @Test
    void mãLýDoTheoDạngCâu() {
        for (ReasonCode code : ReasonCode.values()) {
            boolean newCode = code == ReasonCode.STATEMENT_UNDECIDABLE || code == ReasonCode.ANSWER_AMBIGUOUS;
            assertEquals(!newCode, code.allowedFor(QuestionType.MCQ), "mcq: " + code);
        }
        assertTrue(ReasonCode.STATEMENT_UNDECIDABLE.allowedFor(QuestionType.TF));
        assertFalse(ReasonCode.STATEMENT_UNDECIDABLE.allowedFor(QuestionType.SA));
        assertTrue(ReasonCode.ANSWER_AMBIGUOUS.allowedFor(QuestionType.SA));
        assertFalse(ReasonCode.ANSWER_AMBIGUOUS.allowedFor(QuestionType.TF));
        for (ReasonCode code : ReasonCode.MCQ_ONLY) {
            assertFalse(code.allowedFor(QuestionType.TF), "tf: " + code);
            assertFalse(code.allowedFor(QuestionType.SA), "sa: " + code);
        }
        assertTrue(ReasonCode.CONTENT_INACCURATE.allowedFor(QuestionType.TF));
        assertTrue(ReasonCode.OTHER.allowedFor(QuestionType.SA));
    }

    private static List<String> dbValues(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(e -> ((DbEnum) e).getDbValue()).toList();
    }

    @Test
    void nullVàGiáTrịLạ() {
        assertNull(new Difficulty.Conv().convertToEntityAttribute(null));
        assertNull(new Difficulty.Conv().convertToDatabaseColumn(null));
        assertThrows(IllegalArgumentException.class,
                () -> new Difficulty.Conv().convertToEntityAttribute("very_hard"));
    }

    private static Class<?> typeOf(DbEnumConverter<?> c) {
        // lấy lớp enum từ hằng đầu tiên mà converter chấp nhận
        for (Class<? extends Enum<?>> type : ENUMS) {
            String outer = c.getClass().getEnclosingClass().getSimpleName();
            if (type.getSimpleName().equals(outer)) {
                return type;
            }
        }
        throw new IllegalStateException("Không tìm thấy enum của " + c.getClass());
    }
}
