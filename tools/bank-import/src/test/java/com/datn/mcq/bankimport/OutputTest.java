package com.datn.mcq.bankimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.datn.mcq.bankimport.DocxBuilder.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Đầu ra của công cụ: JSON, ảnh, danh mục CSV, review.csv, formulas.html, báo cáo và chốt chặn rò rỉ. */
class OutputTest {

    @TempDir
    static Path root;
    static Path out;
    static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void run() throws IOException {
        Path in = Files.createDirectory(root.resolve("in"));
        out = root.resolve("out");
        FixtureDoc.write(in);
        BankImporter.run(new BankImporter.Options(in, out, new int[]{20, 10, 10}, false, true));
    }

    private static List<Map<String, String>> csv(String name) throws IOException {
        List<String> lines = Files.readAllLines(out.resolve(name), StandardCharsets.UTF_8);
        List<String> header = parseCsvLine(lines.get(0));
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            List<String> cells = parseCsvLine(line);
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < header.size(); i++) {
                row.put(header.get(i), i < cells.size() ? cells.get(i) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (q) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    q = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                q = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static Map<String, String> row(List<Map<String, String>> rows, String id) {
        return rows.stream().filter(r -> r.get("id").equals(id)).findFirst().orElseThrow(() -> new AssertionError("không có " + id));
    }

    // ---------- JSON và ảnh ----------

    @Test
    void jsonMotFileMoiBoCauHoiTenSnakeCase() throws IOException {
        JsonNode doc = JSON.readTree(out.resolve("k8c9b9.json").toFile());
        assertEquals("1.0", doc.get("schema_version").asText());
        assertEquals("k8c9b9", doc.get("source").get("doc_id").asText());
        assertEquals(8, doc.get("source").get("grade").asInt());
        assertEquals(22, doc.get("questions").size());
        JsonNode q1 = doc.get("questions").get(0);
        assertEquals("k8c9b9-mcq-001", q1.get("id").asText());
        assertEquals("NB", q1.get("level_raw").asText());
        assertEquals("easy", q1.get("level").asText());
        assertEquals("C", q1.get("answer").get("value").asText());
        assertFalse(q1.get("has_image").asBoolean());
        assertNotNull(q1.get("content_hash"));
        assertEquals("f1", q1.get("formulas").get(0).get("id").asText());
        assertTrue(doc.get("questions").get(8).get("level_raw").isNull(), "mức thiếu ghi null rõ ràng, không bỏ khóa");
    }

    @Test
    void anhDuocCheVaoThuMucRiengVaDuongDanTonTai() throws IOException {
        JsonNode doc = JSON.readTree(out.resolve("k8c9b9.json").toFile());
        int n = 0;
        for (JsonNode q : doc.get("questions")) {
            for (JsonNode im : q.get("images")) {
                Path f = out.resolve(im.get("file").asText());
                assertTrue(Files.isRegularFile(f), im.get("file").asText());
                assertTrue(Files.size(f) > 0);
                assertTrue(im.get("file").asText().startsWith("images/k8c9b9/"), im.get("file").asText());
                n++;
            }
        }
        assertTrue(n >= 5, "n=" + n);
    }

    @Test
    void khongChuoiCamNaoLotVaoBatKyOutputNao() throws IOException {
        try (Stream<Path> files = Files.walk(out)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                if (f.toString().endsWith(".png") || f.toString().endsWith(".wmf") || f.toString().endsWith(".emf")
                        || f.toString().endsWith(".bin")) {
                    continue;
                }
                String text = Files.readString(f, StandardCharsets.UTF_8);
                for (String bad : FixtureDoc.FORBIDDEN) {
                    assertFalse(text.contains(bad), "lọt '" + bad + "' vào " + out.relativize(f));
                }
            }
        }
        try (Stream<Path> files = Files.walk(out)) {
            files.forEach(f -> assertFalse(f.getFileName().toString().contains("GIẢ"), f.toString()));
        }
    }

    // ---------- danh mục CSV ----------

    @Test
    void danhMucCoDayDuCotVaTrangThaiCongThuc() throws IOException {
        List<Map<String, String>> rows = csv("catalog.csv");
        assertEquals(22, rows.size());
        for (String col : List.of("id", "doc_id", "type", "level_raw", "level", "answer", "has_image", "formula_status",
                "usable_text_only", "structure_ok", "flags")) {
            assertTrue(rows.get(0).containsKey(col), "thiếu cột " + col);
        }
        Map<String, String> q1 = row(rows, "k8c9b9-mcq-001");
        assertEquals("all_ok", q1.get("formula_status"));
        assertEquals("false", q1.get("has_image"));
        assertEquals("true", q1.get("usable_text_only"));
        assertEquals("true", q1.get("structure_ok"));
        assertEquals("C", q1.get("answer"));

        Map<String, String> q3 = row(rows, "k8c9b9-mcq-003");
        assertEquals("true", q3.get("has_image"));
        assertEquals("false", q3.get("usable_text_only"));

        assertEquals("has_formula_image", row(rows, "k8c9b9-mcq-006").get("formula_status"));
        assertEquals("false", row(rows, "k8c9b9-mcq-006").get("usable_text_only"));

        Map<String, String> sa4 = row(rows, "k8c9b9-sa-004");
        assertEquals("has_unsupported", sa4.get("formula_status"));
        assertEquals("false", sa4.get("usable_text_only"));

        Map<String, String> q10 = row(rows, "k8c9b9-mcq-010");
        assertEquals("false", q10.get("structure_ok"), "thiếu đáp án là lỗi cấu trúc");
        assertEquals("true", q10.get("usable_text_only"), "định nghĩa usable_text_only chỉ xét hình và công thức");

        assertEquals("TFFT", row(rows, "k8c9b9-tf-001").get("answer"));
        assertEquals("????", row(rows, "k8c9b9-tf-006").get("answer"));
        assertEquals("7,5", row(rows, "k8c9b9-sa-001").get("answer"));
    }

    // ---------- review.csv ----------

    @Test
    void reviewCoMoiCauDungSaiMoiCauTraLoiNganVaCauCoCo() throws IOException {
        List<Map<String, String>> rows = csv("review.csv");
        List<String> ids = rows.stream().map(r -> r.get("id")).toList();
        for (int i = 1; i <= 6; i++) {
            assertTrue(ids.contains("k8c9b9-tf-00" + i), "thiếu tf " + i);
        }
        for (int i = 1; i <= 4; i++) {
            assertTrue(ids.contains("k8c9b9-sa-00" + i), "thiếu sa " + i);
        }
        assertTrue(ids.contains("k8c9b9-mcq-010"), "câu bị cờ phải được xem");
        assertTrue(ids.contains("k8c9b9-mcq-011"));
        assertEquals("Phần II, câu thứ 1", row(rows, "k8c9b9-tf-001").get("locator"));
        for (String col : List.of("checked", "verdict", "note", "sample_reason")) {
            assertTrue(rows.get(0).containsKey(col), col);
        }
        assertEquals(rows.size(), ids.stream().distinct().count());
    }

    // ---------- báo cáo ----------

    @Test
    void baoCaoDemCauCongThucVaLechSoLuongKyVong() throws IOException {
        JsonNode r = JSON.readTree(out.resolve("report.json").toFile());
        JsonNode t = r.get("totals");
        assertEquals(22, t.get("questions").asInt());
        assertEquals(12, t.get("by_type").get("mcq").asInt());
        assertEquals(6, t.get("by_type").get("tf").asInt());
        assertEquals(4, t.get("by_type").get("sa").asInt());
        assertEquals(2, t.get("formulas").get("ok").asInt());
        assertEquals(0, t.get("formulas").get("partial").asInt());
        assertEquals(1, t.get("formulas").get("unsupported").asInt());
        assertEquals(1, t.get("formulas").get("empty").asInt());
        JsonNode doc = r.get("docs").get(0);
        assertEquals("k8c9b9", doc.get("doc_id").asText());
        assertEquals(3, doc.get("count_mismatch").size(), "12/6/4 khác kỳ vọng 20/10/10");
        // mcq-010 thiếu đáp án, mcq-011 hai đáp án, tf-006 thiếu đáp án
        assertEquals(3, doc.get("structure_problems").size(), doc.get("structure_problems").toString());
        assertTrue(t.get("usable_text_only").asInt() > 0);
        assertTrue(t.get("usable_text_only_and_structure_ok").asInt() < t.get("usable_text_only").asInt());
    }

    // ---------- formulas.html ----------

    @Test
    void bangDoiChieuCongThucCoLatexVaAnh() throws IOException {
        String html = Files.readString(out.resolve("formulas.html"), StandardCharsets.UTF_8);
        assertTrue(html.contains("\\frac{3}{4}"), "thiếu LaTeX mẫu");
        assertTrue(html.contains("<img"), "thiếu ảnh");
        assertTrue(Files.isDirectory(out.resolve("formulas")));
    }

    // ---------- chốt chặn rò rỉ ----------

    @Test
    void phatHienRoRiTenGiaoVienThiDungVaXoaOutput() throws IOException {
        Path in = Files.createDirectory(root.resolve("leak-in"));
        Path leakOut = root.resolve("leak-out");
        DocxBuilder d = new DocxBuilder();
        d.p("PHẦN I. CÂU TRẮC NGHIỆM NHIỀU PHƯƠNG ÁN LỰA CHỌN");
        d.numbered(cy("[NB]"), t(" Thầy NGUYỄN VĂN GIẢ ra đề."));
        d.p("A. a").p("B. b").p(t("C. "), y("c")).p("D. d");
        d.write(in, "KHỐI 8 - CHƯƠNG 1 - BÀI 1 - TÊN BÀI - GV NGUYỄN VĂN GIẢ.docx");

        BankImporter.LeakException e = assertThrows(BankImporter.LeakException.class,
                () -> BankImporter.run(new BankImporter.Options(in, leakOut, null, false, true)));
        assertFalse(e.getMessage().contains("NGUYỄN"), "thông báo lỗi không được chứa chính chuỗi bị lộ");
        if (Files.exists(leakOut)) {
            try (Stream<Path> files = Files.walk(leakOut)) {
                assertEquals(0, files.filter(Files::isRegularFile).count(), "output phải bị xóa khi phát hiện rò rỉ");
            }
        }
    }
}
