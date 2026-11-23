package org.ctdozlite.mobile;

/** Transport acknowledgement is never presented as delivered or read. */
final class SendStatus {
    static boolean canClearDraft(String draft,String sent,String phase) { return phase.equals("accepted")&&!sent.isEmpty()&&draft.equals(sent); }
    static String previewName(String name) {
        StringBuilder clean=new StringBuilder();
        name.codePoints().limit(160).forEach(code->{int type=Character.getType(code);clean.appendCodePoint(type==Character.CONTROL||type==Character.FORMAT||type==Character.LINE_SEPARATOR||type==Character.PARAGRAPH_SEPARATOR?' ':code);});
        return clean.toString().trim().replaceAll("\\s+"," ");
    }
    static String describe(String phase) {
        switch(phase) {
            case "prepared": return "Đã xem trước • chưa gửi";
            case "sending": return "Đang chờ phản hồi máy chủ";
            case "accepted": return "Máy chủ phản hồi thành công • chờ đồng bộ";
            case "rejected": return "Lượt gửi bị từ chối";
            case "unknown": return "Chưa xác định kết quả • không tự gửi lại; hãy kiểm tra lịch sử";
            case "local_demo": return "Dữ liệu thử • không gửi qua Zalo";
            case "requires_login": return "Cần phiên đăng nhập trước khi gửi";
            case "cancelled": return "Đã hủy trước khi gửi";
            case "none": return "Chưa có lượt gửi trong bộ nhớ";
            default: return "Không hoàn tất lượt gửi";
        }
    }
}
