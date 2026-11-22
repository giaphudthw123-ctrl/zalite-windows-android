package org.ctdozlite.mobile;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class GifGalleryTest {
    @Test public void allHundredEntriesAreReachableWithoutDuplicates() {
        List<GifGallery.Entry> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++) rows.add(new GifGallery.Entry(String.valueOf(i), "GIF " + i, "", false));
        rows = GifGallery.filter(rows, "", false);
        Set<String> seen = new HashSet<>();
        for (int page = 0; page < GifGallery.pageCount(rows.size()); page++) {
            List<GifGallery.Entry> selected = GifGallery.page(rows, page);
            assertTrue(selected.size() <= 12);
            for (GifGallery.Entry row : selected) assertTrue(seen.add(row.id));
        }
        assertEquals(100, seen.size());
    }
    @Test public void filtersUnicodeTagsFavoritesAndStableTies() {
        List<GifGallery.Entry> rows = Arrays.asList(new GifGallery.Entry("b", "Mèo", "Vui Nhộn", true), new GifGallery.Entry("a", "Mèo", "Vui", true), new GifGallery.Entry("c", "Khác", "vui", false));
        List<GifGallery.Entry> filtered = GifGallery.filter(rows, " VUI ", true);
        assertEquals(2, filtered.size()); assertEquals("a", filtered.get(0).id);
        assertEquals(2, GifGallery.filter(rows, "MÈO", false).size());
        assertTrue(GifGallery.filter(rows, "missing", false).isEmpty());
    }
    @Test public void clampsPagesAfterDeleteOrFilter() {
        List<GifGallery.Entry> rows = Collections.singletonList(new GifGallery.Entry("a", "a", "", false));
        assertEquals("a", GifGallery.page(rows, 100).get(0).id);
        assertEquals("a", GifGallery.page(rows, -1).get(0).id);
        assertTrue(GifGallery.page(Collections.emptyList(), 100).isEmpty());
    }
}
