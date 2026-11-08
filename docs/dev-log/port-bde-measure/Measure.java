import com.datn.mcq.bankimport.docx.DocxReader;
import com.datn.mcq.bankimport.dto.BankImportDtos.PreviewData;
import com.datn.mcq.bankimport.model.Question;
import com.datn.mcq.bankimport.parse.DocParser;
import com.datn.mcq.bankimport.parse.LessonName;
import com.datn.mcq.bankimport.parse.ParsedDoc;
import com.datn.mcq.bankimport.service.ImportPreviewBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Chương trình đo ngoài repo: chỉ in id câu, cờ và số đếm, KHÔNG in nội dung đề. args[0] = thư mục chứa các file .docx. */
public class Measure {
    public static void main(String[] args) throws Exception {
        List<Path> files;
        try (var s = Files.list(Path.of(args[0]))) {
            files = s.filter(p -> p.toString().endsWith(".docx")).sorted().toList();
        }
        for (Path f : files) {
            String name = f.getFileName().toString();
            String code = name.contains("CHƯƠNG 3 - BÀI 1") ? "k8c3b1" : name.contains("CHƯƠNG 3 - BÀI 2") ? "k8c3b2"
                    : name.contains("CHƯƠNG 4") ? "k8c4b1" : "k8c5b2";
            ParsedDoc doc = DocParser.parse(DocxReader.read(f, DocxReader.Limits.defaults()),
                    LessonName.parse(name, "0123456789abcdef"));
            PreviewData pv = ImportPreviewBuilder.build(doc, List.of());
            Map<String, Integer> flags = new TreeMap<>();
            for (Question q : doc.questions()) {
                for (String fl : q.flags) {
                    flags.merge(fl, 1, Integer::sum);
                }
                System.out.println("Q " + code + "-" + q.type + "-" + String.format("%03d", q.ordinal) + " hash=" + q.contentHash
                        + " img=" + q.images.size() + " tbl=" + (q.tables == null ? 0 : q.tables.size())
                        + " stemLen=" + (q.stem == null ? 0 : q.stem.length())
                        + " solLen=" + (q.solution == null ? 0 : q.solution.length()) + " flags=" + q.flags);
            }
            Map<String, Integer> skipped = new TreeMap<>();
            pv.skipped().forEach(sk -> skipped.merge(sk.reason(), 1, Integer::sum));
            long mcq = doc.questions().stream().filter(q -> q.type.equals("mcq")).count();
            long tf = doc.questions().stream().filter(q -> q.type.equals("tf")).count();
            long sa = doc.questions().stream().filter(q -> q.type.equals("sa")).count();
            System.out.println("S " + code + " total=" + doc.questions().size() + " mcq=" + mcq + " tf=" + tf + " sa=" + sa
                    + " preview=" + pv.items().size() + " skipped=" + pv.skipped().size() + " skippedByReason=" + skipped
                    + " blocking=" + pv.items().stream().filter(i -> i.blocking()).count() + " flags=" + flags);
        }
    }
}
