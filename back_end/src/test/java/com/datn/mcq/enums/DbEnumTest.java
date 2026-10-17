package com.datn.mcq.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                new UserRole.Conv());

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
                UserRole.class);

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
