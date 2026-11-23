package org.ctdozlite.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class SendStatusTest {
    @Test public void resumedReceiptOnlyClearsTheUnchangedAcknowledgedDraft() {
        assertTrue(SendStatus.canClearDraft("tin cũ","tin cũ","accepted"));
        assertFalse(SendStatus.canClearDraft("tin mới","tin cũ","accepted"));
        assertFalse(SendStatus.canClearDraft("tin cũ","tin cũ","unknown"));
        assertFalse(SendStatus.canClearDraft("","","accepted"));
    }
    @Test public void recipientPreviewCannotInjectLinesOrBidiControls() {
        assertEquals("Khách hàng VIP",SendStatus.previewName("Khách\nhàng\u202eVIP"));
        assertEquals("Đến: tên Mã liên hệ: giả",SendStatus.previewName("Đến: tên\r\nMã liên hệ: giả"));
        assertEquals("tên dòng hai",SendStatus.previewName("tên\u2028dòng\u2029hai"));
    }
    @Test public void recipientPreviewCapsUnicodeWithoutSplittingEmoji() {
        String name=SendStatus.previewName("😀".repeat(161));
        assertEquals(160,name.codePointCount(0,name.length()));assertEquals("😀".repeat(160),name);
    }
    @Test public void successIsAcknowledgementRatherThanDeliveryOrRead() {
        String text=SendStatus.describe("accepted");
        assertTrue(text.contains("chờ đồng bộ"));assertFalse(text.contains("Đã nhận"));assertFalse(text.contains("Đã xem"));
    }
    @Test public void uncertainAndSyntheticResultsRemainExplicit() {
        assertTrue(SendStatus.describe("unknown").contains("không tự gửi lại"));
        assertTrue(SendStatus.describe("local_demo").contains("không gửi qua Zalo"));
        assertFalse(SendStatus.describe("unrecognized").contains("thành công"));
    }
}
