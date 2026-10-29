package net.skybornmc.skyblock.common.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The palette, symbols, gradients and prefix from {@code style.yml} (docs/UI_STYLE.md), exposed as MiniMessage tags:
 * <ul>
 *   <li>{@code <c:brand>}, {@code <rarity:epic>}, {@code <pillar:mining>}, {@code <cur:money>}: colors</li>
 *   <li>{@code <sym:money>}: a symbol</li>
 *   <li>{@code <grad:phoenix>text</grad>}: a named gradient ({@code {phase}} not supported here)</li>
 *   <li>{@code <prefix>}: the standard chat prefix</li>
 * </ul>
 * Bedrock viewers get each color's legacy fallback, and gradients become their first color's fallback.
 */
public final class Style {

    /** A palette color with its Bedrock fallback. */
    public record Color(String name, TextColor hex, NamedTextColor legacy) {
    }

    static final List<String> COLOR_SECTIONS = List.of("c", "rarity", "pillar", "cur");
    private static final Pattern GRAD_OPEN = Pattern.compile("<grad:([a-z0-9_-]+)>");

    private volatile Map<String, Map<String, Color>> colors = Map.of();
    private volatile Map<String, String> symbols = Map.of();
    private volatile Map<String, List<Color>> gradients = Map.of();
    private volatile String prefix = "";
    private volatile Set<Integer> bedrockSafe = Set.of();

    /**
     * Loads {@code style.yml}. On any problem the affected entry is skipped and reported; a broken file keeps
     * everything that was loaded before.
     */
    public List<String> load(File file) {
        List<String> problems = new ArrayList<>();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) {
            problems.add(file.getName() + ": not valid YAML, the previous style stays active. " + e.getMessage().lines().findFirst().orElse(""));
            return problems;
        }

        Map<String, Map<String, Color>> newColors = new LinkedHashMap<>();
        for (String section : COLOR_SECTIONS) {
            Map<String, Color> map = new LinkedHashMap<>();
            ConfigurationSection cs = yaml.getConfigurationSection(sectionKey(section));
            if (cs != null) {
                Map<String, Color> previous = colors.getOrDefault(section, Map.of());
                for (String name : cs.getKeys(false)) {
                    int before = problems.size();
                    java.util.Optional<Color> parsed = parseColor(cs.getString(name, ""), name, problems, sectionKey(section));
                    if (parsed.isPresent()) {
                        map.put(name, parsed.get());
                    } else if (previous.containsKey(name) && problems.size() > before) {
                        map.put(name, previous.get(name));
                        problems.set(problems.size() - 1, problems.getLast().replace("(skipped)", "(keeping the previous color)"));
                    }
                }
            }
            newColors.put(section, java.util.Collections.unmodifiableMap(map));
        }

        Map<String, String> newSymbols = new LinkedHashMap<>();
        ConfigurationSection sym = yaml.getConfigurationSection("symbols");
        if (sym != null) {
            for (String name : sym.getKeys(false)) {
                newSymbols.put(name, sym.getString(name, ""));
            }
        }

        Map<String, List<Color>> newGradients = new LinkedHashMap<>();
        ConfigurationSection grad = yaml.getConfigurationSection("gradients");
        if (grad != null) {
            for (String name : grad.getKeys(false)) {
                List<Color> stops = new ArrayList<>();
                for (String stop : grad.getStringList(name)) {
                    Color c = newColors.get("c").get(stop);
                    if (c == null && !stop.startsWith("#")) {
                        problems.add(file.getName() + " → gradients." + name + ": unknown color '" + stop + "' (skipped)");
                    } else if (c == null) {
                        parseColor(stop, name, problems, "gradients").ifPresent(stops::add);
                    } else {
                        stops.add(c);
                    }
                }
                if (stops.size() < 2) {
                    problems.add(file.getName() + " → gradients." + name + ": needs at least 2 colors (skipped)");
                } else {
                    newGradients.put(name, List.copyOf(stops));
                }
            }
        }

        Set<Integer> safe = new HashSet<>();
        yaml.getString("bedrock-safe-symbols", "").codePoints().filter(cp -> !Character.isWhitespace(cp)).forEach(safe::add);

        colors = java.util.Collections.unmodifiableMap(newColors);
        symbols = java.util.Collections.unmodifiableMap(newSymbols);
        gradients = java.util.Collections.unmodifiableMap(newGradients);
        prefix = yaml.getString("prefix", "");
        bedrockSafe = Set.copyOf(safe);
        return problems;
    }

    /** The YAML section name of a color tag ({@code c} → {@code colors}). */
    public static String sectionKey(String tag) {
        return switch (tag) {
            case "c" -> "colors";
            case "cur" -> "currencies";
            default -> tag;
        };
    }

    /** Parses {@code "#C21F2B dark_red"}. */
    private static java.util.Optional<Color> parseColor(String value, String name, List<String> problems, String section) {
        String[] parts = value.trim().split("\\s+");
        TextColor hex = parts.length > 0 ? TextColor.fromHexString(parts[0]) : null;
        NamedTextColor legacy = parts.length > 1 ? NamedTextColor.NAMES.value(parts[1].toLowerCase(Locale.ROOT)) : null;
        if (hex == null) {
            problems.add("style.yml → " + section + "." + name + ": '" + value + "' needs a hex color like #C21F2B (skipped)");
            return java.util.Optional.empty();
        }
        if (legacy == null) {
            legacy = NamedTextColor.nearestTo(hex);
            if (parts.length > 1) {
                problems.add("style.yml → " + section + "." + name + ": unknown legacy color '" + parts[1] + "', using " + legacy);
            }
        }
        return java.util.Optional.of(new Color(name, hex, legacy));
    }

    /** Tag resolver for one viewer type. */
    public TagResolver resolver(boolean bedrock) {
        TagResolver.Builder builder = TagResolver.builder();
        Map<String, Map<String, Color>> all = colors;
        for (String section : COLOR_SECTIONS) {
            Map<String, Color> map = all.getOrDefault(section, Map.of());
            builder.tag(section, (args, ctx) -> {
                String name = args.popOr("missing color name").value();
                Color c = map.get(name);
                if (c == null) {
                    return Tag.styling(NamedTextColor.WHITE);
                }
                return Tag.styling(bedrock ? c.legacy() : c.hex());
            });
        }
        Map<String, String> syms = symbols;
        builder.tag("sym", (args, ctx) -> Tag.selfClosingInserting(Component.text(syms.getOrDefault(args.popOr("missing symbol").value(), "?"))));
        return builder.build();
    }

    /** Resolver including {@code <prefix>}; the prefix itself is parsed with the same palette. */
    public TagResolver fullResolver(boolean bedrock, MiniMessage mm) {
        TagResolver base = resolver(bedrock);
        Component parsedPrefix = mm.deserialize(preprocess(prefix, bedrock), base);
        return TagResolver.resolver(base, TagResolver.resolver("prefix", Tag.selfClosingInserting(parsedPrefix)));
    }

    /** Expands {@code <grad:name>…</grad>} into a MiniMessage gradient (or the first color's fallback on Bedrock). */
    public String preprocess(String text, boolean bedrock) {
        if (text.indexOf("<grad:") < 0) {
            return text;
        }
        Matcher m = GRAD_OPEN.matcher(text);
        StringBuilder out = new StringBuilder();
        Map<String, List<Color>> grads = gradients;
        while (m.find()) {
            List<Color> stops = grads.get(m.group(1));
            String replacement;
            if (stops == null) {
                replacement = "<white>";
            } else if (bedrock) {
                replacement = "<color:" + stops.getFirst().legacy() + ">";
            } else {
                StringBuilder g = new StringBuilder("<gradient");
                stops.forEach(s -> g.append(':').append(s.hex().asHexString()));
                replacement = g.append('>').toString();
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        // Close tags: a gradient closes with </gradient>, a color with </color>; </reset>-free approach: close any open style.
        return out.toString().replace("</grad>", bedrock ? "</color>" : "</gradient>");
    }

    /** Characters above U+00FF in {@code text} that are not in the Bedrock-safe list. */
    public Set<String> unsafeGlyphs(String text) {
        Set<String> unsafe = new java.util.TreeSet<>();
        Set<Integer> safe = bedrockSafe;
        if (safe.isEmpty()) {
            return unsafe;
        }
        text.codePoints().filter(cp -> cp > 0xFF && !safe.contains(cp))
                .forEach(cp -> unsafe.add(new String(Character.toChars(cp))));
        return unsafe;
    }

    public Map<String, Map<String, Color>> colors() {
        return colors;
    }

    public Map<String, String> symbols() {
        return symbols;
    }

    public Map<String, List<Color>> gradients() {
        return gradients;
    }

    public String symbol(String name) {
        return symbols.getOrDefault(name, "?");
    }
}
