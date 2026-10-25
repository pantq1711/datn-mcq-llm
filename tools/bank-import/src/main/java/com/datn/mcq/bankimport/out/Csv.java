package com.datn.mcq.bankimport.out;

import java.util.List;

/** CSV RFC 4180 tối giản. Xuống dòng trong ô được đổi thành dấu cách để mỗi bản ghi nằm trên một dòng. */
final class Csv {

    static String line(List<?> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cell(cells.get(i) == null ? "" : String.valueOf(cells.get(i))));
        }
        return sb.toString();
    }

    private static String cell(String s) {
        String t = s.replace('\r', ' ').replace('\n', ' ');
        if (t.contains(",") || t.contains("\"")) {
            return '"' + t.replace("\"", "\"\"") + '"';
        }
        return t;
    }

    private Csv() {}
}
