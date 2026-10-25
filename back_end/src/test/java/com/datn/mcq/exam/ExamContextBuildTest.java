package com.datn.mcq.exam;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.exam.service.ExamContextService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W2-3 bước 4: gắn tài liệu bài học và "yêu cầu thêm cho AI" vào lần tạo đề (sinh theo bài và theo ma trận): kiểm tra
 * đầu vào (quyền sở hữu, tối đa 5 tài liệu, tối đa 1000 ký tự), lưu theo từng đề, ô có tài liệu hoặc yêu cầu thì bỏ qua
 * ngân hàng, yêu cầu vào prompt như DỮ LIỆU, và chặn xóa tài liệu đang dùng (409 MATERIAL_IN_USE).
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamContextBuildTest extends ExamFlowTestBase {

    private static final String LESSON = "Bài 3. Hằng đẳng thức đáng nhớ";
    private static final String MCQ2 = "{\"questionType\": \"mcq\", \"difficulty\": \"easy\", \"questionCount\": 2}";

    @Autowired
    ExamContextService contextService;

    private int sha;

    private int material(int owner, String tag) {
        return insert("INSERT INTO lesson_material (owner_id, file_name, mime_type, size_bytes, sha256, extract_method,"
                + " extracted_text, char_count) VALUES (?, ?, 'text/plain', 10, ?, 'plain', 'Noi dung bai hoc', 16)",
                owner, tag + ".txt", String.format("%064x", ++sha));
    }

    private static String ids(int... ids) {
        return IntStream.of(ids).mapToObj(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    }

    private String lessonBody(String extra) {
        return "{\"subjectId\": " + subject + ", \"gradeLevel\": 8, \"lesson\": \"" + LESSON + "\""
                + (extra == null ? "" : ", " + extra) + ", \"cells\": [" + MCQ2 + "]}";
    }

    private ResultActions lesson(String extra) throws Exception {
        return postJson("/api/exams/build-by-lesson", lessonBody(extra));
    }

    private void assertNothingBuilt() {
        assertEquals(0, count("SELECT count(*) FROM exam"), "đầu vào sai thì không tạo đề");
        assertEquals(0, count("SELECT count(*) FROM generation_job"), "và không xếp job nào");
        assertEquals(0, count("SELECT count(*) FROM exam_generation_context"));
    }

    private void assertError(ResultActions r, int status, String code) throws Exception {
        r.andExpect(status().is(status));
        assertEquals(code, json(r).path("code").asText());
    }

    // ---------- sinh theo bài ----------

    @Test
    void sinhTheoBaiLuuNguCanhCaDeVaBoQuaNganHang() throws Exception {
        bankMcq(LESSON, "easy", "Câu ngân hàng khớp bài");
        int m1 = material(teacher, "bai3");
        JsonNode built = json(lesson("\"materialIds\": [" + m1 + "], \"extraInstruction\": \"  Dùng ngữ cảnh mua sắm.  \"")
                .andExpect(status().isOk()));
        assertEquals(0, built.get("fromBank").asInt(), "ô có tài liệu bỏ qua ngân hàng dù có câu khớp");
        assertEquals(2, built.get("jobsQueued").asInt());

        int examId = built.get("examId").asInt();
        var ctx = jdbc.queryForMap("SELECT id, cell_id, extra_instruction FROM exam_generation_context WHERE exam_id = ?",
                examId);
        assertNull(ctx.get("cell_id"), "bài học: ngữ cảnh áp cho cả đề");
        assertEquals("Dùng ngữ cảnh mua sắm.", ctx.get("extra_instruction"), "đã trim");
        assertEquals(List.of(m1), jdbc.queryForList("SELECT material_id FROM exam_context_material WHERE context_id = ?",
                Integer.class, ctx.get("id")));

        // Cùng bài nhưng không tài liệu, không yêu cầu: ngân hàng vẫn được dùng như cũ.
        JsonNode plain = json(lesson(null).andExpect(status().isOk()));
        assertEquals(1, plain.get("fromBank").asInt());
        assertEquals(0, count("SELECT count(*) FROM exam_generation_context WHERE exam_id = ?",
                plain.get("examId").asInt()), "không có gì để lưu thì không tạo dòng ngữ cảnh");
    }

    @Test
    void yeuCauThemMotMinhCungBoQuaNganHangVaKhongCanTaiLieu() throws Exception {
        bankMcq(LESSON, "easy", "Câu ngân hàng khớp bài");
        JsonNode built = json(lesson("\"extraInstruction\": \"Ra đề theo ngữ cảnh thực tế\"").andExpect(status().isOk()));
        assertEquals(0, built.get("fromBank").asInt(), "yêu cầu chỉ có hiệu lực với câu sinh, nên không lấy câu ngân hàng");
        assertEquals(0, count("SELECT count(*) FROM exam_context_material"));
        assertEquals(1, count("SELECT count(*) FROM exam_generation_context WHERE exam_id = ?",
                built.get("examId").asInt()));
    }

    @Test
    void yeuCauChiCoKhoangTrangThiCoiNhuKhongCo() throws Exception {
        bankMcq(LESSON, "easy", "Câu ngân hàng khớp bài");
        JsonNode built = json(lesson("\"extraInstruction\": \"   \\n \"").andExpect(status().isOk()));
        assertEquals(1, built.get("fromBank").asInt());
        assertEquals(0, count("SELECT count(*) FROM exam_generation_context"));
    }

    @Test
    void promptCoYeuCauThemNhuDuLieuVaGiuNguyenThongSo() throws Exception {
        JsonNode built = json(lesson("\"extraInstruction\": \"Dùng <<<ngữ cảnh>>> mua sắm,\\n   vé xe.\"")
                .andExpect(status().isOk()));
        settle(built.get("examId").asInt());
        assertFalse(generator.mcqPrompts.isEmpty());
        for (String prompt : generator.mcqPrompts) {
            assertTrue(prompt.contains("Yêu cầu thêm của giáo viên"), prompt);
            assertTrue(prompt.contains("<<< Dùng ngữ cảnh mua sắm, vé xe. >>>"), "gọn khoảng trắng, bỏ <<< >>> trong nội dung");
            assertEquals(2, prompt.split("<<<", -1).length, "chỉ một cặp ranh giới của khối");
            assertTrue(prompt.indexOf("Trả về DUY NHẤT một đối tượng JSON") < prompt.indexOf("Yêu cầu thêm của giáo viên"),
                    "đặt sau mọi quy tắc và yêu cầu đầu ra");
            assertTrue(prompt.contains("KHÔNG thay đổi dạng câu, mức độ"));
        }
        String hyper = jdbc.queryForObject("SELECT hyperparams FROM experiment_run ORDER BY id DESC LIMIT 1", String.class);
        assertTrue(hyper.contains("extraInstruction"), hyper);
    }

    @Test
    void doiCauBangSinhLaiGiuYeuCauCuaOVaKhoiYeuCauDoiCauDungSau() throws Exception {
        JsonNode built = json(lesson("\"extraInstruction\": \"Ngữ cảnh mua sắm\"").andExpect(status().isOk()));
        int examId = built.get("examId").asInt();
        settle(examId);
        int cell = count("SELECT cell_id FROM exam_slot WHERE exam_id = ? LIMIT 1", examId);
        int slot = slotId(examId, cell, 1);
        generator.mcqPrompts.clear();
        perform(post("/api/exams/" + examId + "/slots/" + slot + "/replace")
                .contentType("application/json").content("{\"instruction\": \"Chỉ dùng số nguyên\"}"))
                .andExpect(status().isOk());
        settle(examId);
        String prompt = generator.mcqPrompts.get(generator.mcqPrompts.size() - 1);
        assertTrue(prompt.contains("<<< Ngữ cảnh mua sắm >>>"), prompt);
        assertTrue(prompt.contains("<<< Chỉ dùng số nguyên >>>"), prompt);
        assertTrue(prompt.indexOf("Ngữ cảnh mua sắm") < prompt.indexOf("Chỉ dùng số nguyên"),
                "yêu cầu của ô trước, yêu cầu của lượt Đổi câu sau cùng");
    }

    // ---------- kiểm tra đầu vào ----------

    @Test
    void yeuCauQua1000KyTuBi400DungDuoc1000() throws Exception {
        assertError(lesson("\"extraInstruction\": \"" + "ă".repeat(1001) + "\""), 400, "INSTRUCTION_TOO_LONG");
        assertNothingBuilt();
        lesson("\"extraInstruction\": \"" + "ă".repeat(1000) + "\"").andExpect(status().isOk());
        assertEquals("ă".repeat(1000), jdbc.queryForObject("SELECT extra_instruction FROM exam_generation_context",
                String.class));
    }

    @Test
    void toiDa5TaiLieuMoiLanSinhTrungIdTinhMot() throws Exception {
        int[] six = IntStream.rangeClosed(1, 6).map(i -> material(teacher, "t" + i)).toArray();
        assertError(lesson("\"materialIds\": [" + ids(six) + "]"), 400, "TOO_MANY_MATERIALS");
        assertNothingBuilt();
        lesson("\"materialIds\": [" + ids(six[0], six[0], six[0], six[0], six[0], six[0]) + "]")
                .andExpect(status().isOk());
        assertEquals(1, count("SELECT count(*) FROM exam_context_material"), "trùng id gộp thành một");
    }

    @Test
    void taiLieuCuaNguoiKhacHoacKhongCoLa404KhongTaoDe() throws Exception {
        int theirs = material(otherTeacher, "cua-nguoi-khac");
        int mine = material(teacher, "cua-toi");
        lesson("\"materialIds\": [" + mine + "," + theirs + "]").andExpect(status().isNotFound());
        lesson("\"materialIds\": [999999]").andExpect(status().isNotFound());
        assertNothingBuilt();
    }

    @Test
    void phanTuRongTrongDanhSachTaiLieuBi400() throws Exception {
        assertError(lesson("\"materialIds\": [null]"), 400, "INVALID_REQUEST");
        assertNothingBuilt();
    }

    // ---------- theo ma trận ----------

    @Test
    void maTranTheoOChiOCoNguCanhMoiBoQuaNganHang() throws Exception {
        int matrix = createMatrix(mcqCell("Đa thức", null, 1), mcqCell("Phân thức", null, 2));
        int cellA = cellId(matrix, 1);
        int cellB = cellId(matrix, 2);
        bankMcq("Đa thức", "easy", "Câu ngân hàng ô A");
        bankMcq("Phân thức", "easy", "Câu ngân hàng ô B");
        int m2 = material(teacher, "phan-thuc");

        JsonNode built = json(postJson("/api/exams/build", "{\"matrixId\": " + matrix + ", \"cells\": [{\"cellId\": "
                + cellB + ", \"materialIds\": [" + m2 + "], \"extraInstruction\": \"Ngữ cảnh thực tế\"}]}")
                .andExpect(status().isOk()));
        int examId = built.get("examId").asInt();
        assertEquals(1, built.get("fromBank").asInt(), "ô A không có ngữ cảnh: lấy câu ngân hàng");
        assertEquals(2, built.get("jobsQueued").asInt(), "ô B bỏ qua ngân hàng: sinh cả 2 câu");
        assertEquals(1, count("SELECT count(*) FROM exam_generation_context WHERE exam_id = ?", examId));
        assertEquals(cellB, count("SELECT cell_id FROM exam_generation_context WHERE exam_id = ?", examId));
        settle(examId);
        assertTrue(generator.mcqPrompts.stream().allMatch(p -> p.contains("Ngữ cảnh thực tế")),
                "chỉ job của ô B được sinh và đều mang yêu cầu của ô B");
        assertTrue(generator.mcqPrompts.size() >= 2, "ô B sinh đủ 2 câu");
        assertEquals(Set.of(m2), contextService.materialsFor(examId, cellB));
        assertEquals(Set.of(), contextService.materialsFor(examId, cellA));
        assertEquals("Ngữ cảnh thực tế", contextService.instructionFor(examId, cellB));
        assertNull(contextService.instructionFor(examId, cellA));
    }

    @Test
    void taiLieuCuaCaMaTranGopVoiTaiLieuCuaOVaDeDeLai() throws Exception {
        int matrix = createMatrix(mcqCell("Đa thức", null, 1), mcqCell("Phân thức", null, 1));
        int cellA = cellId(matrix, 1);
        int cellB = cellId(matrix, 2);
        int m1 = material(teacher, "ca-de");
        int m2 = material(teacher, "o-b");
        JsonNode built = json(postJson("/api/exams/build", "{\"matrixId\": " + matrix + ", \"materialIds\": [" + m1
                + "], \"cells\": [{\"cellId\": " + cellB + ", \"materialIds\": [" + m2 + ", " + m1 + "]}]}")
                .andExpect(status().isOk()));
        int examId = built.get("examId").asInt();
        assertEquals(Set.of(m1), contextService.materialsFor(examId, cellA), "ô A chỉ có tài liệu của cả ma trận");
        assertEquals(Set.of(m1, m2), contextService.materialsFor(examId, cellB), "ô B: hợp, trùng gộp một");
        assertEquals(2, count("SELECT count(*) FROM exam_generation_context WHERE exam_id = ?", examId));
    }

    @Test
    void maTranOKhongThuocMaTranHoacLapBi400VaTongQua5Bi400() throws Exception {
        int matrix = createMatrix(mcqCell("Đa thức", null, 1));
        int other = createMatrix(mcqCell("Phân thức", null, 1));
        int cell = cellId(matrix, 1);
        int foreignCell = cellId(other, 1);
        assertError(postJson("/api/exams/build", "{\"matrixId\": " + matrix + ", \"cells\": [{\"cellId\": " + foreignCell
                + ", \"extraInstruction\": \"x\"}]}"), 400, "INVALID_REQUEST");
        assertError(postJson("/api/exams/build", "{\"matrixId\": " + matrix + ", \"cells\": [{\"cellId\": " + cell
                + ", \"extraInstruction\": \"x\"}, {\"cellId\": " + cell + ", \"extraInstruction\": \"y\"}]}"), 400,
                "INVALID_REQUEST");
        int[] six = IntStream.rangeClosed(1, 6).map(i -> material(teacher, "m" + i)).toArray();
        assertError(postJson("/api/exams/build", "{\"matrixId\": " + matrix + ", \"materialIds\": [" + ids(six[0], six[1],
                six[2]) + "], \"cells\": [{\"cellId\": " + cell + ", \"materialIds\": [" + ids(six[3], six[4], six[5])
                + "]}]}"), 400, "TOO_MANY_MATERIALS");
        assertEquals(0, count("SELECT count(*) FROM exam"));
        assertEquals(0, count("SELECT count(*) FROM generation_job"));
    }

    @Test
    void taoDeTheoMaTranKhongGuiNguCanhVanChayNhuCu() throws Exception {
        int matrix = createMatrix(mcqCell("Đa thức", null, 1));
        bankMcq("Đa thức", "easy", "Câu ngân hàng");
        JsonNode built = build(matrix);
        assertEquals(1, built.get("fromBank").asInt());
        assertEquals(0, count("SELECT count(*) FROM exam_generation_context"));
    }

    // ---------- xóa tài liệu đang dùng ----------

    @Test
    void xoaTaiLieuDaGanVaoDeBi409MaChuaGanThiXoaDuoc() throws Exception {
        int used = material(teacher, "da-dung");
        int free = material(teacher, "chua-dung");
        json(lesson("\"materialIds\": [" + used + "]").andExpect(status().isOk()));
        ResultActions r = perform(delete("/api/materials/" + used));
        assertError(r, 409, "MATERIAL_IN_USE");
        assertEquals(1, count("SELECT count(*) FROM lesson_material WHERE id = ?", used), "không mất");
        perform(delete("/api/materials/" + free)).andExpect(status().isNoContent());
        // Người khác không xóa được và không biết có tài liệu: 404 như thường.
        performAs(otherTeacher, delete("/api/materials/" + used)).andExpect(status().isNotFound());
        assertEquals(1, json(perform(get("/api/materials?page=0&size=10")).andExpect(status().isOk())).get("totalItems")
                .asInt());
    }

    // ---------- riêng tư của ngân hàng ----------

    @Test
    void giuCauSinhTuTaiLieuThiNganHangChiChuSoHuuThayVaDungLai() throws Exception {
        int m1 = material(teacher, "bai3");
        JsonNode built = json(lesson("\"materialIds\": [" + m1 + "]").andExpect(status().isOk()));
        int examId = built.get("examId").asInt();
        settle(examId);
        int cell = count("SELECT cell_id FROM exam_slot WHERE exam_id = ? LIMIT 1", examId);
        int slot = slotId(examId, cell, 1);
        perform(post("/api/exams/" + examId + "/slots/" + slot + "/keep")).andExpect(status().isOk());
        int itemId = count("SELECT bank_item_id FROM exam_slot WHERE id = ?", slot);
        assertEquals(teacher, count("SELECT visible_to_user_id FROM bank_item WHERE id = ?", itemId),
                "câu sinh từ tài liệu: chỉ chủ sở hữu thấy");

        // Chủ sở hữu thấy trong danh sách, chi tiết và chủ đề.
        assertEquals(1, json(perform(get("/api/bank-items")).andExpect(status().isOk())).get("totalItems").asInt());
        perform(get("/api/bank-items/" + itemId)).andExpect(status().isOk());
        assertEquals(1, json(perform(get("/api/bank-items/topics")).andExpect(status().isOk())).size());

        // Giáo viên khác và admin: không thấy trong tìm kiếm, chi tiết là 404, không có trong chủ đề.
        int admin = AuthTestSupport.insertUser(jdbc, "Admin", "admin.priv@test.vn", "admin", "matkhau-admin-12", true);
        assertEquals(0, json(performAs(otherTeacher, get("/api/bank-items")).andExpect(status().isOk()))
                .get("totalItems").asInt());
        performAs(otherTeacher, get("/api/bank-items/" + itemId)).andExpect(status().isNotFound());
        assertEquals(0, json(performAs(otherTeacher, get("/api/bank-items/topics")).andExpect(status().isOk())).size());
        assertEquals(0, json(mockMvc.perform(get("/api/bank-items").with(AuthTestSupport.admin(admin)))
                .andExpect(status().isOk())).get("totalItems").asInt());
        mockMvc.perform(get("/api/bank-items/" + itemId).with(AuthTestSupport.admin(admin)))
                .andExpect(status().isNotFound());

        // Ứng viên khi tạo đề: giáo viên khác không nhận, chủ sở hữu dùng lại được.
        String plainLesson = lessonBody(null);
        JsonNode theirs = json(performAs(otherTeacher, post("/api/exams/build-by-lesson")
                .contentType("application/json").content(plainLesson)).andExpect(status().isOk()));
        assertEquals(0, theirs.get("fromBank").asInt(), "câu riêng tư không thành ứng viên của người khác");
        JsonNode mine = json(lesson(null).andExpect(status().isOk()));
        assertEquals(1, mine.get("fromBank").asInt(), "chủ sở hữu dùng lại câu của mình");

        // Đổi bằng câu ngân hàng: giáo viên khác hết câu (409), không bị lộ câu riêng tư.
        int theirExam = theirs.get("examId").asInt();
        int theirCell = count("SELECT cell_id FROM exam_slot WHERE exam_id = ? LIMIT 1", theirExam);
        for (int round = 0; round < 5; round++) {
            processAll();
            performAs(otherTeacher, get("/api/exams/" + theirExam + "/progress")).andExpect(status().isOk());
        }
        int theirSlot = slotId(theirExam, theirCell, 1);
        ResultActions replace = performAs(otherTeacher, post("/api/exams/" + theirExam + "/slots/" + theirSlot + "/replace")
                .contentType("application/json").content("{\"source\": \"bank\"}"));
        assertError(replace, 409, "BANK_EXHAUSTED");
    }

    @Test
    void yeuCauThemMotMinhKhongHanCheNganHangViKhongCoNoiDungTaiLieu() throws Exception {
        JsonNode built = json(lesson("\"extraInstruction\": \"Ngữ cảnh mua sắm\"").andExpect(status().isOk()));
        int examId = built.get("examId").asInt();
        settle(examId);
        int cell = count("SELECT cell_id FROM exam_slot WHERE exam_id = ? LIMIT 1", examId);
        int slot = slotId(examId, cell, 1);
        perform(post("/api/exams/" + examId + "/slots/" + slot + "/keep")).andExpect(status().isOk());
        int itemId = count("SELECT bank_item_id FROM exam_slot WHERE id = ?", slot);
        assertNull(jdbc.queryForObject("SELECT visible_to_user_id FROM bank_item WHERE id = ?", Integer.class, itemId),
                "câu không dựa trên tài liệu nào thì dùng chung");
    }

    @Test
    void giuCauKhongCoTaiLieuThiNganHangDungChungNhuCu() throws Exception {
        JsonNode built = json(lesson(null).andExpect(status().isOk()));
        int examId = built.get("examId").asInt();
        settle(examId);
        int cell = count("SELECT cell_id FROM exam_slot WHERE exam_id = ? LIMIT 1", examId);
        int slot = slotId(examId, cell, 1);
        perform(post("/api/exams/" + examId + "/slots/" + slot + "/keep")).andExpect(status().isOk());
        int itemId = count("SELECT bank_item_id FROM exam_slot WHERE id = ?", slot);
        assertNull(jdbc.queryForObject("SELECT visible_to_user_id FROM bank_item WHERE id = ?", Integer.class, itemId));
        assertEquals(1, json(performAs(otherTeacher, get("/api/bank-items")).andExpect(status().isOk()))
                .get("totalItems").asInt(), "câu dùng chung: giáo viên khác vẫn thấy");
    }
}
