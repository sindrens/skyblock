package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.text.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Enchant names and effect texts from {@code messages/enchants.yml} ({@code enchant.<id>.name}, {@code .effect} with
 * {@code {key}} = the number of {@code values.<key>} / {@code params.<key>} at a level). Plain text (no tags) where it
 * ends up in lore that escapes it.
 */
final class EnchantText {

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private final Messages messages;
    private final String module;

    EnchantText(Messages messages, String module) {
        this.messages = messages;
        this.module = module;
    }

    /** Raw MiniMessage text of a key, or a visible "[missing: …]" marker. */
    String raw(String key) {
        String raw = messages.raw(module, key);
        return raw == null ? "<red>[missing: " + module + ":" + key + "]</red>" : raw;
    }

    /** The text of a key without tags (for lore parts that are escaped, e.g. names, descriptions). */
    String plain(String key) {
        return strip(raw(key));
    }

    static String strip(String text) {
        return text == null ? "" : text.replaceAll("<[^>]+>", "");
    }

    static String roman(int n) {
        return n >= 0 && n < ROMAN.length ? ROMAN[n] : String.valueOf(n);
    }

    /** {@code Vein Rend} (or the id when the name is missing). */
    String name(String id) {
        String raw = messages.raw(module, "enchant." + id + ".name");
        return raw == null ? id : strip(raw);
    }

    /** {@code Vein Rend II}. */
    String displayName(String id, int level) {
        return name(id) + " " + roman(level);
    }

    /** The effect sentence at a level, numbers filled in, no tags. */
    String effect(EnchantDef def, int level) {
        String raw = messages.raw(module, "enchant." + def.id() + ".effect");
        if (raw == null) {
            return def.id();
        }
        return strip(fill(raw, def, level));
    }

    /** Replaces {@code {key}} with the enchant's number at that level (trailing .0 removed). */
    static String fill(String text, EnchantDef def, int level) {
        String out = text;
        for (Map.Entry<String, List<Double>> e : def.values().entrySet()) {
            out = out.replace("{" + e.getKey() + "}", number(def.value(e.getKey(), level)));
        }
        for (Map.Entry<String, Double> e : def.params().entrySet()) {
            out = out.replace("{" + e.getKey() + "}", number(e.getValue()));
        }
        return out;
    }

    static String number(double value) {
        if (!Double.isFinite(value)) {
            return "?";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.format(Locale.ROOT, "%,d", (long) value);
        }
        String s = String.format(Locale.ROOT, "%.2f", value);
        while (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    /** {@code Pickaxe, Axe} or {@code All tools} / {@code All armor}. */
    String targets(EnchantDef def) {
        if (def.targets().size() == 5 && def.targets().stream().noneMatch(EnchantTarget::armor)) {
            return plain("target.all-tools");
        }
        if (def.targets().size() == 4 && def.targets().stream().allMatch(EnchantTarget::armor)) {
            return plain("target.all-armor");
        }
        List<String> names = new ArrayList<>();
        for (EnchantTarget t : EnchantTarget.values()) {
            if (def.targets().contains(t)) {
                names.add(plain("target." + t.id()));
            }
        }
        return String.join(", ", names);
    }

    /** Word-wraps plain text for menu lore (UI_STYLE: ~32 characters per line). */
    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        return out;
    }
}
