package com.datn.mcq.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NV-31 (16/11): app.bank.keep-visibility chỉ nhận shared | private (không phân biệt hoa thường); trống là shared. */
class BankPropertiesTest {

    @Test
    void macDinhVaChuanHoa() {
        assertEquals("shared", new BankProperties(null).keepVisibility());
        assertEquals("shared", new BankProperties("  ").keepVisibility());
        assertFalse(new BankProperties("shared").keepPrivate());
        BankProperties p = new BankProperties(" PRIVATE ");
        assertEquals("private", p.keepVisibility());
        assertTrue(p.keepPrivate());
    }

    @Test
    void giaTriLaLamUngDungKhongKhoiDong() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new BankProperties("rieng"));
        assertTrue(e.getMessage().contains("BANK_KEEP_VISIBILITY"), e.getMessage());
    }
}
