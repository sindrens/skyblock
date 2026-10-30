package net.skybornmc.skyblock.worlds.builds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** The order of {@code /skyblock builds paste-all}: by the first matching id prefix, then by id. Pure. */
public final class PasteOrder {

    private PasteOrder() {
    }

    public static List<String> sort(List<String> ids, List<String> prefixes) {
        List<String> out = new ArrayList<>(ids);
        out.sort(Comparator.comparingInt((String id) -> rank(id, prefixes)).thenComparing(id -> id));
        return out;
    }

    static int rank(String id, List<String> prefixes) {
        String lower = id.toLowerCase(Locale.ROOT);
        for (int i = 0; i < prefixes.size(); i++) {
            if (lower.startsWith(prefixes.get(i).toLowerCase(Locale.ROOT))) {
                return i;
            }
        }
        return prefixes.size();
    }
}
