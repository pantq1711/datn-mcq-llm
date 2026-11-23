package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.exam.export.PandocTestSupport;
import com.datn.mcq.questionimage.RecordingStatementInspector;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O2 (mục 4.2 của cau-co-hinh.md) ở mức HTTP: {@code GET /api/exams/{id}/export/docx} của đề có chỗ lấy từ ngân hàng mà câu
 * nguồn có hình. Ảnh đọc từ DB (không qua URL), nhúng data URI, đếm lại trong docx; token không có ảnh được phép xem → 500
 * {@code EXPORT_IMAGE_MISSING}; tổng ảnh vượt trần → 422 {@code EXPORT_IMAGES_TOO_LARGE} trước khi chạy pandoc.
 *
 * <p>Đường tạo đề hiện không chọn câu có hình ({@code ExamSlotRepository} còn điều kiện loại, chỉ bỏ ở X1), nên test gán
 * thẳng {@code bank_item_id} vào chỗ như {@link ExamViewImagesTest}. Ảnh tự vẽ; trần hạ xuống 1 MB cho test.
 */
@SpringBootTest(properties = {
        "app.question-images.url-signing-key=dGVzdC1vbmx5LXF1ZXN0aW9uLWltYWdlLXVybC1zaWduaW5nLWtleS1ub3QtcHJvZA==",
        "app.question-images.url-ttl=1h",
        "app.export.max-image-bytes=1048576",
        RecordingStatementInspector.PROPERTY
})
@Import(TestSafetyConfiguration.class)
class ExamFigureExportApiTest extends ExamFlowTestBase {

    @DynamicPropertySource
    static void pandoc(DynamicPropertyRegistry registry) {
        registry.add("app.export.pandoc-path", () -> PandocTestSupport.find().orElse("pandoc"));
    }

    private int batch;

    @BeforeEach
    void requirePandoc() {
        assumeTrue(PandocTestSupport.find().isPresent(), "máy không có pandoc");
    }

    static byte[] png(int side, boolean noise) throws Exception {
        BufferedImage img = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        if (noise) {
            Random r = new Random(side);
            for (int y = 0; y < side; y++) {
                for (int x = 0; x < side; x++) {
                    img.setRGB(x, y, r.nextInt(0x1000000));
                }
            }
        } else {
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, side, side);
            g.setColor(Color.BLACK);
            g.drawOval(2, 2, side - 4, side - 4);
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** Một dòng question_image gắn vào câu nguồn của {@code bankItemId}; trả id ảnh. */
    private int image(int bankItemId, String role, int ordinal, byte[] data) throws Exception {
        int gqId = count("SELECT source_question_id FROM bank_item WHERE id = ?", bankItemId);
        if (batch == 0) {
            batch = insert("INSERT INTO import_batch (owner_id, display_name, file_sha256, subject_id, grade_level,"
                    + " topic, status, question_count, imported_count, created_at, expires_at, confirmed_at)"
                    + " VALUES (?, 'Lô có hình', ?, ?, 8, 'Đa thức', 'confirmed', 1, 1,"
                    + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '24 hours', CURRENT_TIMESTAMP)",
                    teacher, "a".repeat(64), subject);
        }
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        return insert("INSERT INTO question_image (owner_id, import_batch_id, item_key, generated_question_id, role,"
                + " ordinal, mime, data, byte_size, width_px, height_px, display_width_mm, sha256, source_format)"
                + " VALUES (?, ?, ?, ?, ?, ?, 'image/png', ?, ?, 10, 10, 50, ?, 'png')",
                teacher, batch, "mcq-" + bankItemId, gqId, role, ordinal, data, data.length, sha);
    }

    /** Đề 1 chỗ mcq; chỗ được gán câu ngân hàng {@code bank}. Trả examId. */
    private int examWithBankSlot(int bank) throws Exception {
        int matrix = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = build(matrix).get("examId").asInt();
        settle(examId);
        int slot = slotId(examId, cellId(matrix, 1), 1);
        jdbc.update("UPDATE exam_slot SET bank_item_id = ?, generated_question_id = NULL WHERE id = ?", bank, slot);
        return examId;
    }

    /** Câu ngân hàng có hình ở câu dẫn, phương án C và lời giải. Trả {examId, ảnh câu dẫn, ảnh phương án, ảnh lời giải}. */
    private int[] figureExam(boolean noise, int side) throws Exception {
        int bank = bankMcq("Đa thức", "easy", "Cho hình.");
        int stem = image(bank, "stem", 1, png(side, noise));
        int option = image(bank, "option_c", 2, png(40, false));
        int solution = image(bank, "solution", 3, png(50, false));
        jdbc.update("UPDATE bank_item SET question_text = ?, option_c = ?, explanation = ? WHERE id = ?",
                "Cho tam giác $ABC$ như hình vẽ.\n⟦img:" + stem + "⟧\nTính $BC$.", "⟦img:" + option + "⟧",
                "Xem hình.\n⟦img:" + solution + "⟧", bank);
        return new int[] {examWithBankSlot(bank), stem, option, solution};
    }

    private byte[] export(int examId, String query, int expectedStatus) throws Exception {
        return perform(get("/api/exams/" + examId + "/export/docx" + query)).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private static int count(String s, String regex) {
        Matcher m = Pattern.compile(regex).matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    @Test
    void xuatWordNhungAnhCauDanPhuongAnVaLoiGiaiDocTuDb() throws Exception {
        int[] e = figureExam(false, 80);

        for (String q : new String[] {"", "?equation=mathtype"}) {
            String xml = PandocTestSupport.documentXml(export(e[0], q, 200));
            assertEquals(3, count(xml, "<w:drawing>"), q);
            for (int id : new int[] {e[1], e[2], e[3]}) {
                assertEquals(1, count(xml, "descr=\"qimg:" + id + "\""), q + " ảnh " + id);
            }
            assertFalse(xml.contains("⟦img"), "token không ra Word dạng chữ");
        }
    }

    @Test
    void chiDeKhongCoAnhLoiGiai() throws Exception {
        int[] e = figureExam(false, 80);

        String xml = PandocTestSupport.documentXml(export(e[0], "?content=exam", 200));
        assertEquals(2, count(xml, "<w:drawing>"));
        assertFalse(xml.contains("qimg:" + e[3] + "\""), "ảnh lời giải không ra ở file chỉ đề");
    }

    @Test
    void banDacTaKhongInChuCauHoiNenKhongCoTokenVaKhongNapAnh() throws Exception {
        int[] e = figureExam(false, 80);

        RecordingStatementInspector.clear();
        String xml = PandocTestSupport.documentXml(export(e[0], "?content=spec", 200));
        assertFalse(xml.contains("⟦img"));
        assertEquals(0, count(xml, "<w:drawing>"));
        for (String sql : RecordingStatementInspector.imageQueries()) {
            assertFalse(RecordingStatementInspector.selectsDataColumn(sql), "bản đặc tả không nạp byte ảnh: " + sql);
        }
    }

    @Test
    void tokenTroToiAnhKhongThuocCauDuocXemThi500ExportImageMissing() throws Exception {
        int other = bankMcq("Đa thức", "easy", "Câu khác.");
        int foreign = image(other, "stem", 1, png(30, false));
        int bank = bankMcq("Đa thức", "easy", "Cho hình.");
        jdbc.update("UPDATE bank_item SET question_text = ? WHERE id = ?", "Cho hình.\n⟦img:" + foreign + "⟧", bank);
        int examId = examWithBankSlot(bank);

        JsonNode body = objectMapper.readTree(export(examId, "", 500));
        assertEquals("EXPORT_IMAGE_MISSING", body.get("code").asText());
    }

    @Test
    void tongAnhVuotTranThi422TruocKhiChayPandoc() throws Exception {
        // 3 ảnh nhiễu ~ 400 KB (không nén được) > trần 1 MB của test
        int bank = bankMcq("Đa thức", "easy", "Cho hình.");
        StringBuilder text = new StringBuilder("Cho các hình.");
        for (int k = 1; k <= 3; k++) {
            text.append("\n⟦img:").append(image(bank, "stem", k, png(365 + k, true))).append("⟧");
        }
        jdbc.update("UPDATE bank_item SET question_text = ? WHERE id = ?", text.toString(), bank);
        int total = count("SELECT sum(byte_size) FROM question_image WHERE import_batch_id = ?", batch);
        assertTrue(total > 1048576, "tổng " + total);
        int examId = examWithBankSlot(bank);

        RecordingStatementInspector.clear();
        JsonNode body = objectMapper.readTree(export(examId, "", 422));
        assertEquals("EXPORT_IMAGES_TOO_LARGE", body.get("code").asText());
        String mb = String.format(java.util.Locale.ROOT, "%.1f", Math.ceil(total * 10.0 / 1048576) / 10).replace('.', ',');
        assertEquals("Tổng hình trong đề quá lớn để xuất Word (" + mb + " MB, tối đa 1 MB). Hãy bỏ bớt câu có hình.",
                body.get("error").asText());
        for (String sql : RecordingStatementInspector.imageQueries()) {
            assertFalse(RecordingStatementInspector.selectsDataColumn(sql), "vượt trần: không nạp byte ảnh: " + sql);
        }
    }

    @Test
    void deKhongCoHinhXuatNhuCuKhongTruyVanByteAnh() throws Exception {
        int matrix = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = build(matrix).get("examId").asInt();
        settle(examId);

        RecordingStatementInspector.clear();
        String xml = PandocTestSupport.documentXml(export(examId, "", 200));
        assertEquals(0, count(xml, "<w:drawing>"));
        assertEquals(java.util.List.of(), RecordingStatementInspector.imageQueries());
    }
}
