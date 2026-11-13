package com.datn.mcq.generation.service;

import com.datn.mcq.enums.DistractorCheck;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lỗi đang hiển thị và kết quả tạm của bản sửa (13/11, PA1, H-4). Hàm thuần. */
class DistractorViewTest {

    private static final String ERRORS = "{\"A\":\"Lỗi A\",\"B\":null,\"C\":\"Lỗi C\",\"D\":\"Lỗi D\"}";

    private static GeneratedQuestion q(int id, Integer from, String text, Option correct, String a, String c) {
        GeneratedQuestion g = new GeneratedQuestion();
        g.setId(id);
        g.setQuestionType(QuestionType.MCQ);
        g.setEditedFromQuestionId(from);
        g.setQuestionText(text);
        g.setOptionA(a);
        g.setOptionB("b");
        g.setOptionC(c);
        g.setOptionD("d");
        g.setCorrectOption(correct);
        g.setDistractorErrors(ERRORS);
        return g;
    }

    private static final Map<String, DistractorCheck> ORIGINAL = Map.of("A", DistractorCheck.MATCH,
            "C", DistractorCheck.MISMATCH, "D", DistractorCheck.UNKNOWN);

    private static Map<String, DistractorCheck> resolved(GeneratedQuestion shown, GeneratedQuestion prev,
                                                         Map<Integer, Map<String, DistractorCheck>> stored) {
        Map<Integer, GeneratedQuestion> byId = new HashMap<>();
        byId.put(prev.getId(), prev);
        Map<String, DistractorCheck> out = new java.util.LinkedHashMap<>();
        DistractorView.resolve(shown, byId::get, stored::get).forEach((k, v) -> out.put(k, v.check()));
        return out;
    }

    @Test
    void cauCoDongAnswerCheckDungKetQuaDaLuuThieuKhoaLaChuaKiem() {
        GeneratedQuestion g = q(1, null, "Đề", Option.B, "a", "c");
        Map<Integer, Map<String, DistractorCheck>> stored = Map.of(1, Map.of("A", DistractorCheck.MATCH));
        Map<String, DistractorView.Item> v = DistractorView.resolve(g, id -> null, stored::get);
        assertEquals(java.util.List.of("A", "C", "D"), java.util.List.copyOf(v.keySet()), "không có phương án đúng");
        assertEquals(DistractorCheck.MATCH, v.get("A").check());
        assertEquals(DistractorCheck.UNCHECKED, v.get("C").check());
        assertEquals("Lỗi C", v.get("C").text());
        // chưa có dòng (Solver chưa chạy hoặc lỗi): mọi lỗi chưa kiểm
        DistractorView.resolve(g, id -> null, id -> null).values()
                .forEach(i -> assertEquals(DistractorCheck.UNCHECKED, i.check()));
    }

    @Test
    void banSuaChiDoiMotPhuongAnTrongLucChoThiChiLoiDoChuaKiem() {
        GeneratedQuestion prev = q(1, null, "Đề", Option.B, "a", "c");
        GeneratedQuestion edited = q(2, 1, "Đề", Option.B, "a", "c mới");
        Map<Integer, Map<String, DistractorCheck>> stored = new HashMap<>(Map.of(1, ORIGINAL));
        assertEquals(Map.of("A", DistractorCheck.MATCH, "C", DistractorCheck.UNCHECKED, "D", DistractorCheck.UNKNOWN),
                resolved(edited, prev, stored), "trong lúc chờ: chỉ C về chưa kiểm");
        // job answer_check của bản sửa xong: kết quả mới thay hẳn
        stored.put(2, Map.of("A", DistractorCheck.MISMATCH, "C", DistractorCheck.MATCH, "D", DistractorCheck.MATCH));
        assertEquals(Map.of("A", DistractorCheck.MISMATCH, "C", DistractorCheck.MATCH, "D", DistractorCheck.MATCH),
                resolved(edited, prev, stored));
    }

    @Test
    void banSuaDoiCauDanHoacDapAnThiMoiLoiChuaKiem() {
        GeneratedQuestion prev = q(1, null, "Đề", Option.B, "a", "c");
        Map<Integer, Map<String, DistractorCheck>> stored = new HashMap<>(Map.of(1, ORIGINAL));
        GeneratedQuestion stem = q(2, 1, "Đề mới", Option.B, "a", "c");
        resolved(stem, prev, stored).values().forEach(c -> assertEquals(DistractorCheck.UNCHECKED, c));
        // đổi đáp án sang C: lỗi ghi ở C không hiển thị; lỗi ở B (phương án đúng cũ) không có chữ → chỉ còn A, D
        GeneratedQuestion answer = q(3, 1, "Đề", Option.C, "a", "c");
        Map<String, DistractorCheck> r = resolved(answer, prev, stored);
        assertEquals(java.util.List.of("A", "D"), java.util.List.copyOf(r.keySet()));
        r.values().forEach(c -> assertEquals(DistractorCheck.UNCHECKED, c));
        // sau job: kết quả mới
        stored.put(2, Map.of("A", DistractorCheck.MATCH));
        assertEquals(DistractorCheck.MATCH, resolved(stem, prev, stored).get("A"));
    }

    @Test
    void chuoiHaiBanSuaDangChoThiSuyTuBanCoKetQuaGanNhat() {
        GeneratedQuestion v1 = q(1, null, "Đề", Option.B, "a", "c");
        GeneratedQuestion v2 = q(2, 1, "Đề", Option.B, "a2", "c");
        GeneratedQuestion v3 = q(3, 2, "Đề", Option.B, "a2", "c3");
        Map<Integer, GeneratedQuestion> byId = Map.of(1, v1, 2, v2);
        Map<Integer, Map<String, DistractorCheck>> stored = Map.of(1, ORIGINAL);
        Map<String, DistractorView.Item> r = DistractorView.resolve(v3, byId::get, stored::get);
        assertEquals(DistractorCheck.UNCHECKED, r.get("A").check(), "A đổi ở v2");
        assertEquals(DistractorCheck.UNCHECKED, r.get("C").check(), "C đổi ở v3");
        assertEquals(DistractorCheck.UNKNOWN, r.get("D").check(), "D giữ kết quả v1");
    }

    @Test
    void cauKhongCoLoiThiRong() {
        GeneratedQuestion g = q(1, null, "Đề", Option.B, "a", "c");
        g.setDistractorErrors(null);
        assertTrue(DistractorView.resolve(g, id -> null, id -> null).isEmpty());
        g.setDistractorErrors("{\"B\":\"chỉ ở đáp án đúng\"}");
        assertTrue(DistractorView.resolve(g, id -> null, id -> null).isEmpty());
    }
}
