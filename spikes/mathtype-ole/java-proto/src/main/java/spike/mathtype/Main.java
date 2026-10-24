package spike.mathtype;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Dùng: java spike.mathtype.Main &lt;pandoc.docx&gt; &lt;latex.txt (mỗi dòng một công thức, đúng thứ tự)&gt; &lt;out.docx&gt;
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        if (args.length != 3) {
            System.err.println("Dùng: Main <pandoc.docx> <latex.txt> <out.docx>");
            System.exit(2);
        }
        byte[] in = Files.readAllBytes(Path.of(args[0]));
        List<String> latex = Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8).stream()
                .filter(s -> !s.isBlank()).toList();
        long t0 = System.nanoTime();
        DocxMathTypeConverter.Result r = DocxMathTypeConverter.convert(in, latex);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        Files.write(Path.of(args[2]), r.docx());
        r.log().forEach(System.out::println);
        System.out.printf("Đã chuyển %d, giữ OMML %d, %d ms -> %s%n", r.converted(), r.kept(), ms, args[2]);
    }
}
