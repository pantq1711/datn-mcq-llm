package com.datn.mcq.material.extract;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Cửa kiểm đầu vào docx dùng chung cho module khác (nhập ngân hàng câu từ Word, V26), để không chép lại các bước chống
 * file độc hại của tài liệu bài học. Chỉ mở ra đúng các bước kiểm; các lớp bên trong ({@link FileTypeSniffer},
 * {@link ZipSafetyScanner}, {@link DocxFormulaCounter.MarkupGuard}) vẫn để package-private.
 *
 * <p>Thứ tự trong {@link #writeCheckedDocx}: dung lượng, loại file theo nội dung (magic bytes; đuôi file và Content-Type
 * của client không được dùng), ghi vào thư mục tạm của nơi gọi với tên cố định, quét zip (số mục, dung lượng giải nén
 * thật, tỉ lệ nén, tên mục thoát thư mục), kiểm cấu trúc docx. Lỗi là {@code ApiErrorException} cùng mã với tài liệu bài
 * học: 400 EMPTY_FILE, 413 FILE_TOO_LARGE, 415 UNSUPPORTED_FILE_TYPE, 422 INVALID_FILE.
 */
@Component
public class DocxInputGuard {

    /** Tên file docx trong thư mục tạm (không dùng tên client gửi). */
    public static final String FILE_NAME = "in.docx";
    static final String ONLY_DOCX_MESSAGE = "Chỉ nhận file Word .docx.";

    private final ZipSafetyScanner scanner;

    public DocxInputGuard(ZipSafetyScanner scanner) {
        this.scanner = scanner;
    }

    /**
     * Kiểm {@code content} là docx an toàn, ghi vào {@code dir/in.docx} và trả đường dẫn đó.
     *
     * @param maxBytes dung lượng tối đa của file (byte)
     */
    public Path writeCheckedDocx(byte[] content, Path dir, long maxBytes) {
        if (content != null && content.length > maxBytes) {
            throw MaterialErrors.tooLarge(maxBytes);
        }
        if (FileTypeSniffer.sniff(content) != FileTypeSniffer.Kind.ZIP) {
            throw MaterialErrors.unsupported(ONLY_DOCX_MESSAGE);
        }
        Path file = dir.resolve(FILE_NAME);
        try {
            Files.write(file, content);
        } catch (IOException e) {
            throw MaterialErrors.failed("Lỗi ghi file tạm khi đọc file Word.");
        }
        List<String> entries = scanner.scan(file);
        if (!entries.contains("[Content_Types].xml") || !entries.contains(DocxFormulaCounter.DOCUMENT_XML)) {
            throw MaterialErrors.unsupported("File nén này không phải tài liệu Word (.docx). " + ONLY_DOCX_MESSAGE);
        }
        return file;
    }

    /**
     * Bọc luồng XML của một mục docx: từ chối khi vượt {@code maxBytes}, khi có {@code <!DOCTYPE}/{@code <!ENTITY} hoặc khi
     * không phải UTF-8 (ném IOException khi đọc).
     */
    public static InputStream guardedXml(InputStream in, long maxBytes) {
        return new DocxFormulaCounter.MarkupGuard(in, maxBytes);
    }

    /** Lỗi 422 INVALID_FILE của bước đọc docx, cùng mã với tài liệu bài học. */
    public static RuntimeException invalid(String message) {
        return MaterialErrors.invalid(message);
    }

    /** Lỗi 503 EXTRACTOR_BUSY khi hết chỗ xử lý file nặng. */
    public static RuntimeException busy() {
        return MaterialErrors.busy();
    }
}
