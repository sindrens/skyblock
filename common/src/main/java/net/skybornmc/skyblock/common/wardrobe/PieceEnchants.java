package net.skybornmc.skyblock.common.wardrobe;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.skybornmc.skyblock.common.tools.ToolPerk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The armor enchants of one set piece (E5): a list of {@link ToolPerk} (slot 1, 2 or {@link ArmorSlots#MYTHIC_SLOT},
 * enchant id, level, crystals invested; size always 1), stored as JSON in {@code wardrobe_pieces.enchants} in the same
 * format as tool perks. Broken input never throws: it reads as empty.
 */
public final class PieceEnchants {

    private static final Gson GSON = new Gson();

    private PieceEnchants() {
    }

    /** JSON for the database ({@code []} for none). */
    public static String encode(List<ToolPerk> enchants) {
        JsonArray array = new JsonArray();
        for (ToolPerk p : sorted(enchants)) {
            JsonObject o = new JsonObject();
            o.addProperty("slot", p.slot());
            o.addProperty("id", p.id());
            o.addProperty("level", p.level());
            if (p.invested() > 0) {
                o.addProperty("invested", p.invested());
            }
            array.add(o);
        }
        return GSON.toJson(array);
    }

    /** Reads {@link #encode} output; broken entries are skipped. */
    public static List<ToolPerk> decode(String json) {
        List<ToolPerk> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) {
                return out;
            }
            for (JsonElement e : root.getAsJsonArray()) {
                try {
                    JsonObject o = e.getAsJsonObject();
                    long invested = o.has("invested") ? Math.max(0, o.get("invested").getAsLong()) : 0;
                    out.add(new ToolPerk(o.get("slot").getAsInt(), o.get("id").getAsString(), o.get("level").getAsInt(), 1, invested));
                } catch (RuntimeException skip) {
                    // one broken entry is skipped, the rest still loads
                }
            }
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
        return sorted(out);
    }

    /** A copy sorted by slot. */
    public static List<ToolPerk> sorted(List<ToolPerk> enchants) {
        List<ToolPerk> out = new ArrayList<>(enchants == null ? List.of() : enchants);
        out.sort(Comparator.comparingInt(ToolPerk::slot));
        return List.copyOf(out);
    }

    /** The enchant in a slot. */
    public static Optional<ToolPerk> at(List<ToolPerk> enchants, int slot) {
        return enchants.stream().filter(p -> p.slot() == slot).findFirst();
    }

    /** The enchant with an id. */
    public static Optional<ToolPerk> find(List<ToolPerk> enchants, String id) {
        return enchants.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** Compact item tag text ({@code 1:vitality:3;2:fortitude:2}) for the lore renderer; ids and numbers only. */
    public static String tag(List<ToolPerk> enchants) {
        StringBuilder b = new StringBuilder();
        for (ToolPerk p : sorted(enchants)) {
            if (!b.isEmpty()) {
                b.append(';');
            }
            b.append(p.slot()).append(':').append(p.id()).append(':').append(p.level());
        }
        return b.toString();
    }

    /** Reads {@link #tag}; broken parts are skipped. */
    public static List<ToolPerk> fromTag(String tag) {
        List<ToolPerk> out = new ArrayList<>();
        if (tag == null || tag.isBlank()) {
            return out;
        }
        for (String part : tag.split(";")) {
            String[] f = part.split(":");
            if (f.length != 3) {
                continue;
            }
            try {
                out.add(new ToolPerk(Integer.parseInt(f[0]), f[1], Integer.parseInt(f[2])));
            } catch (RuntimeException skip) {
                // skipped
            }
        }
        return sorted(out);
    }
}
