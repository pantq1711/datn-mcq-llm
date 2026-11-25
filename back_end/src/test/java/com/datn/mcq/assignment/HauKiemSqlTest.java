package com.datn.mcq.assignment;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.exam.PsqlFile;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Yêu cầu 5 của PR B: {@code docs/thong-ke/11-hau-kiem.sql} tự tính lại n, k, p, D và cờ hậu kiểm từ bài làm gốc (không đọc
 * item_stat) và phải khớp kết quả service (B1) trên cùng dữ liệu: 22 lượt hợp lệ có nhiều lượt hòa điểm (tách theo id), một
 * cặp trùng số thứ tự và một lượt chưa nộp.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class HauKiemSqlTest extends AssignmentTestBase {

    private static final Path FILE = Path.of("..", "docs", "thong-ke", "11-hau-kiem.sql");

    /** Bài làm của số thứ tự {@code s} (1..22): số nhỏ làm tốt hơn; nhiều số làm giống hệt nhau (hòa điểm). */
    private String answers(int s) {
        String m1 = s <= 14 ? "B" : s <= 18 ? "C" : s <= 20 ? "A" : null;
        String m2 = s <= 3 ? "D" : "B";
        String tf = s <= 3 ? "{\"a\": \"T\", \"b\": \"F\", \"c\": \"F\", \"d\": \"T\"}"
                : s <= 15 ? "{\"a\": \"T\", \"b\": \"F\", \"c\": \"T\", \"d\": \"T\"}"
                : "{\"a\": \"F\", \"b\": \"T\", \"c\": \"F\"}";
        String sa = s <= 10 ? "2,5" : s <= 16 ? "25" : s <= 19 ? "2.5" : null;
        StringBuilder b = new StringBuilder("[");
        if (m1 != null) {
            b.append("{\"slotId\": ").append(mcqSlot1).append(", \"choice\": \"").append(m1).append("\"}, ");
        }
        b.append("{\"slotId\": ").append(mcqSlot2).append(", \"choice\": \"").append(m2).append("\"}, ");
        b.append("{\"slotId\": ").append(tfSlot).append(", \"tf\": ").append(tf).append("}");
        if (sa != null) {
            b.append(", {\"slotId\": ").append(saSlot).append(", \"text\": \"").append(sa).append("\"}");
        }
        return b.append("]").toString();
    }

    @Test
    void sqlTinhLaiTuBaiLamGocKhopService() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        for (int s = 1; s <= 22; s++) {
            submit(startToken(code, "8A1", s), answers(s)).andExpect(status().isOk());
        }
        submit(startToken(code, "8A2", 1), answers(1)).andExpect(status().isOk());   // cặp trùng
        submit(startConfirmedToken(code, "8A2", 1), answers(22)).andExpect(status().isOk());
        startToken(code, "8A2", 2);                                                     // chưa nộp
        closeAndPassGrace(id);

        JsonNode service = json(asTeacher(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(base() + "/" + id + "/item-stats")).andExpect(status().isOk()));
        assertEquals(22, service.get("n").asInt());
        assertEquals(6, service.get("groupSize").asInt(), "round(0,27 × 22) = round(5,94)");
        assertTrue(service.get("sampleSufficient").asBoolean());

        List<Map<String, Object>> sql = jdbc.queryForList(PsqlFile.load(FILE));
        List<Map<String, Object>> mine = sql.stream().filter(r -> ((Number) r.get("assignment_id")).intValue() == id)
                .toList();
        assertEquals(4, mine.size(), sql.toString());
        Map<Integer, Map<String, Object>> sqlBySlot = mine.stream()
                .collect(Collectors.toMap(r -> ((Number) r.get("slot_id")).intValue(), r -> r));

        List<Map<String, Object>> stored = jdbc.queryForList("SELECT slot_id, n, group_size, p, d, flags::text AS flags"
                + " FROM item_stat WHERE assignment_id = ?", id);
        assertEquals(4, stored.size());
        boolean anyFlag = false;
        for (Map<String, Object> st : stored) {
            int slot = ((Number) st.get("slot_id")).intValue();
            Map<String, Object> q = sqlBySlot.get(slot);
            String where = "chỗ " + slot + ": service " + st + " / SQL " + q;
            assertEquals(((Number) st.get("n")).intValue(), ((Number) q.get("n")).intValue(), where);
            assertEquals(((Number) st.get("group_size")).intValue(), ((Number) q.get("group_size")).intValue(), where);
            assertEquals(0, ((BigDecimal) st.get("p")).compareTo((BigDecimal) q.get("p")), where);
            assertEquals(0, ((BigDecimal) st.get("d")).compareTo((BigDecimal) q.get("d")), where);
            assertEquals(st.get("flags"), String.valueOf(q.get("flags")), where);
            anyFlag |= !"[]".equals(st.get("flags"));
        }
        assertTrue(anyFlag, "dữ liệu phải sinh ít nhất một cờ để việc so cờ có ý nghĩa: " + stored);
        // Câu 2 (tính tay): số 1-3 (1,75 điểm) và 4-6 (1,5 điểm, hòa với 7-10, tách theo id) là nhóm cao; số 1-3 chọn D.
        // c_H = 3, c_L = 6 ⇒ D = -0,5; A, C không ai chọn; D: cao 3 > thấp 0 và 3 ≥ max(2; 1,2).
        assertEquals("[\"low_discrimination\", \"unused_option: A\", \"unused_option: C\", \"key_suspect: D\"]",
                String.valueOf(sqlBySlot.get(mcqSlot2).get("flags")));
    }

    /**
     * Quyết định 25/11: bài giao chế độ họ tên có {@code seat_no} NULL; SQL phải nhận "trùng" theo lớp + họ tên (chữ
     * thường, giữ dấu) như service, không gộp mọi lượt NULL của một lớp thành một nhóm trùng.
     */
    @Test
    void cheDoHoTenSqlNhanTrungTheoTenKhopService() throws Exception {
        JsonNode a = assignMode(NAME, true, false);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        submit(json(startName(code, "8A1", "Nguyễn Văn An").andExpect(status().isCreated())).get("attemptToken")
                .asText(), answers(1)).andExpect(status().isOk());
        submit(json(startName(code, "8A1", "Nguyen Van An").andExpect(status().isCreated())).get("attemptToken")
                .asText(), answers(5)).andExpect(status().isOk());   // khác dấu: không trùng
        submit(json(startName(code, "8A1", "Trần Bình").andExpect(status().isCreated())).get("attemptToken")
                .asText(), answers(2)).andExpect(status().isOk());
        submit(json(startNameConfirmed(code, "8A1", "  trần   BÌNH ").andExpect(status().isCreated()))
                .get("attemptToken").asText(), answers(20)).andExpect(status().isOk());   // trùng với "Trần Bình"
        submit(json(startName(code, "8A2", "Trần Bình").andExpect(status().isCreated())).get("attemptToken")
                .asText(), answers(3)).andExpect(status().isOk());   // khác lớp: không trùng
        closeAndPassGrace(id);

        JsonNode service = json(asTeacher(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(base() + "/" + id + "/item-stats")).andExpect(status().isOk()));
        assertEquals(3, service.get("n").asInt(), service.toString());
        assertEquals(2, service.get("excluded").get("duplicate").asInt());

        List<Map<String, Object>> mine = jdbc.queryForList(PsqlFile.load(FILE)).stream()
                .filter(r -> ((Number) r.get("assignment_id")).intValue() == id).toList();
        assertEquals(4, mine.size(), mine.toString());
        for (Map<String, Object> r : mine) {
            assertEquals(3, ((Number) r.get("n")).intValue(), "SQL phải loại đúng cặp trùng tên: " + r);
        }
    }

    @Test
    void baiChuaHetAnHanHoacCuaTaiKhoanNhomKhongCoTrongKetQua() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        submit(startToken(code, "8A1", 1), answers(1)).andExpect(status().isOk());
        asTeacher(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base() + "/" + id + "/close"))
                .andExpect(status().isOk());
        assertEquals(List.of(), jdbc.queryForList(PsqlFile.load(FILE)), "còn trong ân hạn 2 phút");

        jdbc.update("UPDATE exam_assignment SET closed_at = closed_at - interval '3 minutes',"
                + " closes_at = closes_at - interval '3 minutes' WHERE id = ?", id);
        assertEquals(4, jdbc.queryForList(PsqlFile.load(FILE)).size(), "SQL không cần B1 đã được đọc");
        assertEquals(List.of(), jdbc.queryForList(PsqlFile.load(FILE, Map.of("dev_emails", "khac@x.vn, AN.ASSIGN@test"))),
                "bài giao của tài khoản nhóm bị loại (như psql -v dev_emails=...)");
    }
}
