package net.skybornmc.skyblock.wardrobe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One saved loadout preset (WARDROBE §7.1, LD1) of a profile: per armor slot a wardrobe entry or "empty" (a missing slot
 * = keep), the pet (null = keep) and per hotbar slot (0-8) a unique item (a missing slot = keep). Immutable. The JSON
 * codec is pure and unit-tested; bad JSON reads as "keep everything" instead of failing.
 *
 * @param index  0-based place in the {@code /loadout} menu
 * @param icon   item id of the menu icon (lowercase, e.g. {@code iron_pickaxe})
 */
record Loadout(UUID id, UUID profile, int index, String name, String icon, Map<ArmorPiece, Armor> armor, String petId,
               Map<Integer, Tool> hotbar, long updatedAt) {

    static final int HOTBAR = 9;
    static final String EMPTY = "empty";

    /** One armor slot: a wardrobe entry, or empty (take a worn piece off). */
    record Armor(UUID entryId) {
        static final Armor NONE = new Armor(null);

        boolean empty() {
            return entryId == null;
        }
    }

    /**
     * One hotbar slot: a unique item (custom tool, …) by its id; name and material are shown in the menu (saved when the
     * preset was saved).
     */
    record Tool(UUID itemId, String name, String material) {
    }

    Loadout {
        armor = Collections.unmodifiableMap(armor.isEmpty() ? new EnumMap<>(ArmorPiece.class) : new EnumMap<>(armor));
        hotbar = Collections.unmodifiableMap(new TreeMap<>(hotbar));
    }

    /** Lowercase name for lookups ({@code /loadout mining}). */
    String nameKey() {
        return key(name);
    }

    static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
    }

    Loadout withName(String newName) {
        return new Loadout(id, profile, index, newName, icon, armor, petId, hotbar, updatedAt);
    }

    Loadout withIcon(String newIcon) {
        return new Loadout(id, profile, index, name, newIcon, armor, petId, hotbar, updatedAt);
    }

    /** Same id, place, name and icon with new contents (Save current). */
    Loadout withContents(Map<ArmorPiece, Armor> newArmor, String newPet, Map<Integer, Tool> newHotbar, long now) {
        return new Loadout(id, profile, index, name, icon, newArmor, newPet, newHotbar, now);
    }

    /** Wardrobe entries this preset wears. */
    Set<UUID> entries() {
        Set<UUID> out = new LinkedHashSet<>();
        for (Armor a : armor.values()) {
            if (!a.empty()) {
                out.add(a.entryId());
            }
        }
        return out;
    }

    /** Unique items this preset puts in the hotbar. */
    Set<UUID> items() {
        Set<UUID> out = new LinkedHashSet<>();
        for (Tool t : hotbar.values()) {
            out.add(t.itemId());
        }
        return out;
    }

    // ------------------------------------------------------------------ JSON codec

    static String armorJson(Map<ArmorPiece, Armor> armor) {
        JsonObject o = new JsonObject();
        for (Map.Entry<ArmorPiece, Armor> e : armor.entrySet()) {
            o.addProperty(e.getKey().key(), e.getValue().empty() ? EMPTY : e.getValue().entryId().toString());
        }
        return o.toString();
    }

    static Map<ArmorPiece, Armor> parseArmor(String json) {
        Map<ArmorPiece, Armor> out = new EnumMap<>(ArmorPiece.class);
        JsonObject o = object(json);
        if (o == null) {
            return out;
        }
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            ArmorPiece.byKey(e.getKey()).ifPresent(piece -> {
                if (!e.getValue().isJsonPrimitive()) {
                    return;
                }
                String v = e.getValue().getAsString();
                if (EMPTY.equalsIgnoreCase(v)) {
                    out.put(piece, Armor.NONE);
                } else {
                    UUID id = uuid(v);
                    if (id != null) {
                        out.put(piece, new Armor(id));
                    }
                }
            });
        }
        return out;
    }

    static String hotbarJson(Map<Integer, Tool> hotbar) {
        JsonObject o = new JsonObject();
        for (Map.Entry<Integer, Tool> e : new TreeMap<>(hotbar).entrySet()) {
            JsonObject t = new JsonObject();
            t.addProperty("id", e.getValue().itemId().toString());
            t.addProperty("name", WardrobeStore.clip(e.getValue().name() == null ? "" : e.getValue().name(), 48));
            t.addProperty("material", e.getValue().material() == null ? "" : e.getValue().material());
            o.add(String.valueOf(e.getKey()), t);
        }
        return o.toString();
    }

    /** Hotbar slots 0-8 with a valid item id; the same item twice keeps only the lowest slot. */
    static Map<Integer, Tool> parseHotbar(String json) {
        Map<Integer, Tool> out = new TreeMap<>();
        JsonObject o = object(json);
        if (o == null) {
            return out;
        }
        Map<Integer, Tool> raw = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            int slot;
            try {
                slot = Integer.parseInt(e.getKey().trim());
            } catch (NumberFormatException ex) {
                continue;
            }
            if (slot < 0 || slot >= HOTBAR || !e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject t = e.getValue().getAsJsonObject();
            UUID id = t.has("id") && t.get("id").isJsonPrimitive() ? uuid(t.get("id").getAsString()) : null;
            if (id == null) {
                continue;
            }
            String name = t.has("name") && t.get("name").isJsonPrimitive() ? t.get("name").getAsString() : "";
            String material = t.has("material") && t.get("material").isJsonPrimitive() ? t.get("material").getAsString() : "";
            raw.put(slot, new Tool(id, name, material));
        }
        Set<UUID> seen = new java.util.HashSet<>();
        for (Map.Entry<Integer, Tool> e : raw.entrySet()) {
            if (seen.add(e.getValue().itemId())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static JsonObject object(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonElement e = JsonParser.parseString(json);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static UUID uuid(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
