package net.skybornmc.skyblock.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.skybornmc.skyblock.common.tools.ToolPerk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JSON for perks, stats and owner history (item data and the {@code tools} table use the same format). Broken input
 * never throws: it reads as empty, so a damaged item still works and can be repaired by staff.
 */
final class ToolCodec {

    private static final Gson GSON = new Gson();

    private ToolCodec() {
    }

    static String perks(List<ToolPerk> perks) {
        JsonArray array = new JsonArray();
        for (ToolPerk p : perks) {
            JsonObject o = new JsonObject();
            o.addProperty("slot", p.slot());
            o.addProperty("id", p.id());
            o.addProperty("level", p.level());
            if (p.size() != 1) {
                o.addProperty("size", p.size());
            }
            if (p.invested() > 0) {
                o.addProperty("invested", p.invested());
            }
            array.add(o);
        }
        return GSON.toJson(array);
    }

    static List<ToolPerk> perks(String json) {
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
                    int size = o.has("size") ? o.get("size").getAsInt() : 1;
                    long invested = o.has("invested") ? Math.max(0, o.get("invested").getAsLong()) : 0;
                    out.add(new ToolPerk(o.get("slot").getAsInt(), o.get("id").getAsString(), o.get("level").getAsInt(), size, invested));
                } catch (RuntimeException skip) {
                    // one broken entry is skipped, the rest still loads
                }
            }
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
        return out;
    }

    static String stats(Map<String, Long> stats) {
        JsonObject o = new JsonObject();
        stats.forEach(o::addProperty);
        return GSON.toJson(o);
    }

    static Map<String, Long> stats(String json) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return out;
            }
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                try {
                    out.put(e.getKey(), e.getValue().getAsLong());
                } catch (RuntimeException skip) {
                    // ignore non-numbers
                }
            }
        } catch (RuntimeException e) {
            return new LinkedHashMap<>();
        }
        return out;
    }

    /** One previous or current holder of a tool. */
    record Owner(UUID uuid, String name, long since) {
    }

    static String owners(List<Owner> owners) {
        JsonArray array = new JsonArray();
        for (Owner owner : owners) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", owner.uuid().toString());
            o.addProperty("name", owner.name());
            o.addProperty("since", owner.since());
            array.add(o);
        }
        return GSON.toJson(array);
    }

    static List<Owner> owners(String json) {
        List<Owner> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
                try {
                    JsonObject o = e.getAsJsonObject();
                    out.add(new Owner(UUID.fromString(o.get("uuid").getAsString()), o.get("name").getAsString(), o.get("since").getAsLong()));
                } catch (RuntimeException skip) {
                    // skip broken entry
                }
            }
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
        return out;
    }
}
