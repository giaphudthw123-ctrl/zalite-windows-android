package org.ctdozlite.mobile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** In-memory gallery selection; no disk access or image decoding. */
final class GifGallery {
    static final int PAGE_SIZE = 12;
    static final class Entry {
        final String id, name, tags;
        final boolean favorite;
        Entry(String id, String name, String tags, boolean favorite) {
            this.id = id; this.name = name; this.tags = tags; this.favorite = favorite;
        }
    }
    static List<Entry> filter(List<Entry> entries, String query, boolean favorites) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        ArrayList<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            if ((!favorites || entry.favorite) && (entry.name + " " + entry.tags).toLowerCase(Locale.ROOT).contains(needle)) result.add(entry);
        }
        result.sort(Comparator.comparing((Entry entry) -> entry.name.toLowerCase(Locale.ROOT)).thenComparing(entry -> entry.id));
        return result;
    }
    static int pageCount(int size) { return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE); }
    static List<Entry> page(List<Entry> entries, int requested) {
        int index = Math.max(0, Math.min(requested, pageCount(entries.size()) - 1));
        int start = index * PAGE_SIZE;
        return new ArrayList<>(entries.subList(start, Math.min(start + PAGE_SIZE, entries.size())));
    }
}
