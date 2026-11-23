package org.ctdozlite.mobile;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationSearchTest {
    @Test public void vietnameseAccentsAndCaseAreOptional() {
        assertTrue(ConversationSearch.matches("Đặng Thị Ánh", "Báo giá", "DANG anh"));
        assertTrue(ConversationSearch.matches("Dạng NFD", "a\u0301nh", "ÁNH"));
    }
    @Test public void tokensMayMatchNameAndPreview() {
        assertTrue(ConversationSearch.matches("Khách hàng", "Gửi báo giá mới", "  khach   gia  "));
        assertFalse(ConversationSearch.matches("Khách hàng", "Báo giá", "khach kho"));
    }
    @Test public void emptyQueryAndNonLatinNamesAreSafe() {
        assertTrue(ConversationSearch.matches("😀 東京", "", "東京"));
        assertTrue(ConversationSearch.matches("", "", "  "));
        assertFalse(ConversationSearch.matches("", "", "x"));
    }
}
