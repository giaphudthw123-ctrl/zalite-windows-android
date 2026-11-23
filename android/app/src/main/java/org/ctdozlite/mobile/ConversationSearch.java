package org.ctdozlite.mobile;

import java.text.Normalizer;
import java.util.Locale;

/** Accent-insensitive local search; never sends a query to a service. */
final class ConversationSearch {
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replace('đ', 'd').trim();
    }
    static boolean matches(String name, String preview, String query) {
        String words = normalize(query);
        String haystack = normalize(name + " " + preview);
        for (String word : words.split("\\s+")) {
            if (!haystack.contains(word)) return false;
        }
        return true;
    }
}
