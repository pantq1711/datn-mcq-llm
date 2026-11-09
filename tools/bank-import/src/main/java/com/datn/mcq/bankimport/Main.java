package com.datn.mcq.bankimport;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Dòng lệnh. Mặc định đọc data/private/bo-cau-hoi, ghi data/private/bank-json (đều bị .gitignore).
 * Console chỉ in số liệu và id, không in nội dung câu hỏi hay tên file gốc.
 */
public final class Main {

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        Path in = Path.of("data/private/bo-cau-hoi");
        Path dest = Path.of("data/private/bank-json");
        int[] expected = {20, 10, 10};
        boolean convert = false;
        boolean validate = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--in" -> in = Path.of(args[++i]);
                case "--out" -> dest = Path.of(args[++i]);
                case "--expect" -> {
                    String v = args[++i];
                    expected = v.equals("none") ? null : java.util.Arrays.stream(v.split(",")).mapToInt(Integer::parseInt).toArray();
                }
                case "--convert-images" -> convert = true;
                case "--no-validate" -> validate = false;
                default -> {
                    out.println("Cách dùng: --in <thư mục .docx> --out <thư mục đầu ra> [--expect mcq,tf,sa|none] "
                            + "[--convert-images] [--no-validate]");
                    System.exit(64);
                }
            }
        }
        try {
            BankImporter.Result r = BankImporter.run(new BankImporter.Options(in, dest, expected, convert, validate));
            print(out, r.report());
        } catch (BankImporter.LeakException e) {
            out.println("LỖI RÒ RỈ: " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            out.println("LỖI: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.exit(1);
        }
    }

    @SuppressWarnings("unchecked")
    private static void print(PrintStream out, Map<String, Object> report) {
        for (Map<String, Object> d : (List<Map<String, Object>>) report.get("docs")) {
            Map<String, Object> f = (Map<String, Object>) d.get("formulas");
            out.printf("%s: câu=%s theo dạng=%s | công thức ok=%s unsupported=%s | usable_text_only=%s | lệch số lượng=%s | bất thường cấu trúc=%d%n",
                    d.get("doc_id"), d.get("questions"), d.get("by_type"), f.get("ok"), f.get("unsupported"),
                    d.get("usable_text_only"), ((List<?>) d.get("count_mismatch")).size(),
                    ((List<?>) d.get("structure_problems")).size());
        }
        Map<String, Object> t = (Map<String, Object>) report.get("totals");
        Map<String, Object> f = (Map<String, Object>) t.get("formulas");
        out.printf("TỔNG: câu=%s theo dạng=%s mức=%s%n", t.get("questions"), t.get("by_type"), t.get("level_raw"));
        out.printf("      công thức dùng trong câu=%s ok=%s partial=%s unsupported=%s rỗng=%s (tỉ lệ ok %s%%)%n",
                f.get("used_in_questions"), f.get("ok"), f.get("partial"), f.get("unsupported"), f.get("empty"), f.get("ok_rate"));
        out.printf("      ảnh=%s | câu có ảnh=%s | usable_text_only=%s (cùng cấu trúc ok: %s)%n", t.get("images"),
                ((Map<String, Object>) t.get("images")).get("questions_with_image"), t.get("usable_text_only"),
                t.get("usable_text_only_and_structure_ok"));
        out.println("      chi tiết: report.json, catalog.csv, review.csv, formulas.html trong thư mục đầu ra");
    }

    private Main() {}
}
