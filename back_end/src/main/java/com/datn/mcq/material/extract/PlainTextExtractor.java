package com.datn.mcq.material.extract;

import com.datn.mcq.material.entity.LessonMaterial;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** File .txt: UTF-8 chặt (bỏ BOM), không đoán bảng mã khác để không làm hỏng tiếng Việt. */
@Component
public class PlainTextExtractor {

    static final String NO_TEXT_MESSAGE = "File văn bản không có nội dung.";

    private final MaterialProperties props;

    public PlainTextExtractor(MaterialProperties props) {
        this.props = props;
    }

    public ExtractedText extract(byte[] content) {
        int offset = content.length >= 3 && content[0] == (byte) 0xEF && content[1] == (byte) 0xBB
                && content[2] == (byte) 0xBF ? 3 : 0;
        String raw;
        try {
            raw = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content, offset, content.length - offset)).toString();
        } catch (CharacterCodingException e) {
            throw MaterialErrors.unsupported("File văn bản không phải UTF-8. Hãy lưu lại dạng UTF-8 (Notepad: Save As "
                    + "> Encoding: UTF-8) rồi tải lại.");
        }
        return new ExtractedText(TextNormalizer.checked(props, raw, NO_TEXT_MESSAGE), LessonMaterial.MIME_TXT,
                LessonMaterial.METHOD_PLAIN, null, List.of());
    }
}
