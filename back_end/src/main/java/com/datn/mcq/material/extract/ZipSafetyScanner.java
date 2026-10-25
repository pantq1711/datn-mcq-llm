package com.datn.mcq.material.extract;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Quét file zip (docx) TRƯỚC khi đưa cho pandoc, chống zip bomb và tên mục thoát thư mục. Đọc danh mục từ central
 * directory (cùng cách pandoc đọc, nên không bị lừa bởi local header khác) và đếm byte giải nén THẬT của từng mục, không
 * tin kích thước khai báo. Giới hạn: số mục, dung lượng giải nén mỗi mục và tổng, tỉ lệ nén (với mục lớn hơn 1 MiB).
 */
@Component
public class ZipSafetyScanner {

    static final long RATIO_MIN_BYTES = 1L << 20;
    static final int MAX_NAME_LENGTH = 512;

    private final MaterialProperties props;

    public ZipSafetyScanner(MaterialProperties props) {
        this.props = props;
    }

    /** @return tên các mục; ném INVALID_FILE (422) khi file hỏng hoặc vượt giới hạn */
    List<String> scan(Path zipFile) {
        List<String> names = new ArrayList<>();
        long total = 0;
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (names.size() >= props.getZipMaxEntries()) {
                    throw MaterialErrors.invalid("File có quá nhiều thành phần bên trong nên bị từ chối.");
                }
                String name = entry.getName();
                requireSafeName(name);
                names.add(name);
                if (entry.isDirectory()) {
                    continue;
                }
                long limit = Math.min(props.getZipMaxEntryBytes(), props.getZipMaxTotalBytes() - total);
                long actual = countDecompressed(zip, entry, limit);
                long compressed = Math.max(1, entry.getCompressedSize());
                if (actual > RATIO_MIN_BYTES && actual / compressed > props.getZipMaxRatio()) {
                    throw MaterialErrors.invalid("File có thành phần nén bất thường (nghi zip bomb) nên bị từ chối.");
                }
                total += actual;
            }
        } catch (IOException e) {
            throw MaterialErrors.invalid("File bị hỏng hoặc không phải tài liệu hợp lệ.");
        }
        return names;
    }

    /** Giải nén một mục tới khi hết hoặc quá {@code limit} byte (khi đó từ chối); trả số byte thật. */
    private static long countDecompressed(ZipFile zip, ZipEntry entry, long limit) throws IOException {
        long count = 0;
        byte[] buf = new byte[64 * 1024];
        try (InputStream in = zip.getInputStream(entry)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                count += n;
                if (count > limit) {
                    throw MaterialErrors.invalid("File có thành phần quá lớn khi giải nén nên bị từ chối.");
                }
            }
        }
        return count;
    }

    private static void requireSafeName(String name) {
        boolean bad = name == null || name.isEmpty() || name.length() > MAX_NAME_LENGTH || name.indexOf('\\') >= 0
                || name.startsWith("/") || name.indexOf(':') >= 0 || name.indexOf('\0') >= 0;
        if (!bad) {
            for (String segment : name.split("/", -1)) {
                if (segment.equals("..")) {
                    bad = true;
                    break;
                }
            }
        }
        if (bad) {
            throw MaterialErrors.invalid("File có tên thành phần không an toàn nên bị từ chối.");
        }
    }
}
