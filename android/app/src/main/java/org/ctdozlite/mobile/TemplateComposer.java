package org.ctdozlite.mobile;

/** Pure insertion rules: keep edits made after opening a picker and never truncate a template. */
final class TemplateComposer {
    static String insert(String current,String expected,String expanded,int start,int end) {
        if(!current.equals(expected))throw new IllegalArgumentException("Bản nháp đã thay đổi. Hãy chọn lại mẫu.");
        if(start<0||end<start||end>current.length()||splitsPair(current,start)||splitsPair(current,end))throw new IllegalArgumentException("Vị trí chèn không hợp lệ.");
        String merged=current.substring(0,start)+expanded+current.substring(end);
        if(merged.length()>4000)throw new IllegalArgumentException("Nội dung sau khi chèn vượt 4.000 ký tự UTF-16.");
        return merged;
    }
    private static boolean splitsPair(String text,int at) { return at>0&&at<text.length()&&Character.isHighSurrogate(text.charAt(at-1))&&Character.isLowSurrogate(text.charAt(at)); }
}
