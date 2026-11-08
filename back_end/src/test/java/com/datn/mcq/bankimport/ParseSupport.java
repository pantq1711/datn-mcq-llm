package com.datn.mcq.bankimport;

import com.datn.mcq.bankimport.docx.DocxReader;
import com.datn.mcq.bankimport.parse.DocParser;
import com.datn.mcq.bankimport.parse.LessonName;
import com.datn.mcq.bankimport.parse.ParsedDoc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Ghi tài liệu giả ra thư mục tạm rồi chạy bộ phân tích, dùng chung cho các test nhóm sửa lỗi trích xuất. Không phải test. */
final class ParseSupport {

    static ParsedDoc parse(DocxBuilder d, Path dir) throws IOException {
        Path f = dir.resolve("in-" + System.nanoTime() + ".docx");
        Files.write(f, d.bytes());
        return DocParser.parse(DocxReader.read(f, DocxReader.Limits.defaults()),
                LessonName.parse(FixtureDoc.FILE_NAME, "0123456789abcdef"));
    }

    private ParseSupport() {}
}
