package com.datn.mcq.qc.checker;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.Normalizer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quyết định 3 (22/10), theo danh sách của quyết định 5 (21/10): cụm phủ định tường minh, có ranh giới từ,
 * chỉ xét phần văn bản ngoài công thức của thân câu. Mỗi cụm có một ca riêng; cụm dài được ưu tiên
 * (ví dụ "không phải" chứ không phải "không").
 */
class NegationMarkerTest {

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
            "Biểu thức sau không xác định khi $x$ bằng:              | không",
            "Phương trình nào chưa ở dạng tổng quát?                  | chưa",
            "Các số sau đều là số nguyên tố, ngoại trừ:               | ngoại trừ",
            "Số nào không phải là số chính phương?                    | không phải",
            "Khẳng định nào không đúng?                               | không đúng",
            "Giá trị nào không thuộc tập nghiệm của bất phương trình? | không thuộc",
            "Số nào không là ước của $12$?                            | không là",
            "Cặp số nào không thỏa mãn phương trình $2x + y = 5$?     | không thỏa mãn",
            "Biểu thức nào không thể rút gọn được nữa?                | không thể",
            "Chọn phát biểu sai:                                      | phát biểu sai",
            "Tìm nhận định sai trong các nhận định sau:               | nhận định sai",
            "Chọn khẳng định sai:                                     | khẳng định sai",
            "Khẳng định nào sau đây là sai?                           | sai"
    })
    void eachDecidedPhraseIsDetectedOnItsOwn(String stem, String expectedMarker) {
        assertEquals(List.of(expectedMarker), McqTextNormalizer.negationMarkers(stem.strip()));
        assertTrue(McqTextNormalizer.hasNegation(stem.strip()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Cặp số nào KHÔNG THỎA MÃN phương trình?",
            "Cặp số nào không thoả mãn phương trình?"
    })
    void caseAndOldStyleToneMarkAreAccepted(String stem) {
        assertEquals(List.of("không thỏa mãn"), McqTextNormalizer.negationMarkers(stem));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Bất phương trình nào sau đây là bất phương trình bậc nhất một ẩn?",
            "Tập nào chứa số $0$?",                       // "chứa" khác "chưa"
            "Với $x$ khác $0$, rút gọn biểu thức",
            "Nghiệm của phương trình $\\text{không} = 1$ là:", // nằm trong công thức
            "Tính khônggian và chưabiết",                 // dính chữ cái: không phải ranh giới từ
            "Giá trị saiz của biểu thức là:",             // "sai" dính chữ cái: không khớp
            "Chọn phát biểu đúng:",
            "Khẳng định nào đúng?"
    })
    void textWithoutDecidedPhrasesOrWithPhrasesOnlyInsideMathIsNotNegated(String stem) {
        assertTrue(McqTextNormalizer.negationMarkers(stem).isEmpty(), () -> stem);
        assertFalse(McqTextNormalizer.hasNegation(stem));
    }

    @org.junit.jupiter.api.Test
    void decomposedUnicodeInputIsNormalisedBeforeMatching() {
        String nfd = Normalizer.normalize("Số nào không phải là số nguyên tố?", Normalizer.Form.NFD);
        assertEquals(List.of("không phải"), McqTextNormalizer.negationMarkers(nfd));
    }

    @org.junit.jupiter.api.Test
    void severalMarkersAreAllReportedInOrder() {
        assertEquals(List.of("chưa", "không thể"),
                McqTextNormalizer.negationMarkers("Phương trình chưa giải được thì không thể kết luận:"));
    }
}
