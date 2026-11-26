package com.datn.mcq.assignment.service;

import com.datn.mcq.assignment.repository.AssignmentRepository.AttemptRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Họ tên học sinh (quyết định 25/11/2026): chuẩn hóa khi lưu và khóa so trùng. Hàm thuần, không cần Spring.
 */
class StudentNamesTest {

    @Test
    void normalizeTrimsCollapsesSpacesAndComposesToNfc() {
        assertEquals("Nguyễn Văn An", StudentNames.normalize("  Nguyễn   Văn  An  "));
        assertEquals("Nguyễn Văn An", StudentNames.normalize("Nguyễn\tVăn 　An\n"));
        assertEquals("nguyễn VĂN an", StudentNames.normalize(" nguyễn VĂN an"), "giữ hoa thường như học sinh gõ");
        assertEquals("Ơn", StudentNames.normalize("Ơn"));
        assertEquals("Ữ".repeat(60), StudentNames.normalize("Ữ".repeat(60)));
        assertEquals("Ữ".repeat(60), StudentNames.normalize("Ữ".repeat(60)), "đếm ký tự sau khi ghép NFC");
    }

    @Test
    void normalizeRejectsTooShortTooLongAndControlCharacters() {
        for (String bad : new String[] {null, "", "   ", "A", " B ", "Ữ".repeat(61), "Lan\u0007Anh", "Lan\u0000",
                "Lan​Anh", "Lan‮Anh", "\u001b[31mAn"}) {
            assertNull(StudentNames.normalize(bad), "phải từ chối: " + bad);
        }
    }

    @Test
    void keyIgnoresCaseAndExtraSpacesButKeepsDiacritics() {
        String key = StudentNames.key(StudentNames.normalize("Nguyễn Văn An"));
        assertEquals(key, StudentNames.key(StudentNames.normalize("  nguyễn   VĂN an ")));
        assertEquals(key, StudentNames.key(StudentNames.normalize("NGUYỄN VĂN AN")));
        assertEquals(StudentNames.key("Đặng Hà"), StudentNames.key("đặng hà"));
        assertNotEquals(key, StudentNames.key(StudentNames.normalize("Nguyen Van An")), "giữ dấu");
        assertNotEquals(key, StudentNames.key(StudentNames.normalize("Nguyễn Văn Ân")));
    }

    private static AttemptRow row(int id, String cls, Integer seat, String name) {
        LocalDateTime t = LocalDateTime.of(2026, 11, 25, 8, 0);
        return new AttemptRow(id, 1, cls, seat, name, t, t.plusMinutes(45), null, null, null);
    }

    @Test
    void duplicateIdsUsesClassPlusNameOrClassPlusSeat() {
        List<AttemptRow> rows = new ArrayList<>();
        rows.add(row(1, "8A1", null, "Nguyễn Văn An"));
        rows.add(row(2, "8A1", null, "nguyễn văn an"));   // trùng 1
        rows.add(row(3, "8A1", null, "Nguyen Van An"));   // khác dấu: không trùng
        rows.add(row(4, "8A2", null, "Nguyễn Văn An"));   // khác lớp
        rows.add(row(5, "8A1", 7, null));
        rows.add(row(6, "8A1", 7, null));                  // trùng 5
        rows.add(row(7, "8A1", 8, null));
        assertEquals(Set.of(1, 2, 5, 6), AttemptRules.duplicateIds(rows));
    }
}
