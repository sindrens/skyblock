package net.skybornmc.skyblock.common.frame;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Every pixel number of the frame layer (GUI-FONT 4.3), read from {@code frame/frame-metrics.json}, which
 * {@code tools/build_panels.py pack} measures from the final PNGs and Gradle bundles into common. Immutable and pure:
 * the Java side never hard-codes a pixel. Parsed with {@link YamlConfiguration} (JSON is valid YAML, the same trick as
 * the icon catalogue); the path separator is switched off because letter keys include {@code .}.
 */
public final class FrameMetrics {

    /** Classpath location of the bundled metrics. */
    public static final String RESOURCE = "frame/frame-metrics.json";

    /** A panel glyph: {@code rows}, size, advance, code point. */
    public record Panel(int rows, int width, int height, int advance, char cp) {
    }

    /** A plaque glyph: width, advance, inner text width, code point. */
    public record Plaque(int width, int advance, int inner, char cp) {
    }

    /** A logo glyph. */
    public record Logo(char cp, int advance, int ascent, int width, int height) {
    }

    /** A space glyph and its signed pixel step. */
    public record SpaceGlyph(char cp, int step) {
    }

    private final int titleOriginX;
    private final int titleBaseline;
    private final int plaqueExtend;
    private final int windowWidth;
    private final Map<PanelType, Panel> panels;
    private final Map<PlaqueSize, Plaque> plaques;
    private final int cellSize;
    private final int cellX0;
    private final int cellY0;
    private final int cellStep;
    private final int cellAdvance;
    private final List<Character> cells;
    private final Logo logoWide;
    private final Logo logoSmall;
    private final List<SpaceGlyph> negative;
    private final List<SpaceGlyph> positive;
    private final Map<Character, Integer> letters;
    private final int letterSpace;

    private FrameMetrics(Builder b) {
        this.titleOriginX = b.titleOriginX;
        this.titleBaseline = b.titleBaseline;
        this.plaqueExtend = b.plaqueExtend;
        this.windowWidth = b.windowWidth;
        this.panels = Collections.unmodifiableMap(new EnumMap<>(b.panels));
        this.plaques = Collections.unmodifiableMap(new EnumMap<>(b.plaques));
        this.cellSize = b.cellSize;
        this.cellX0 = b.cellX0;
        this.cellY0 = b.cellY0;
        this.cellStep = b.cellStep;
        this.cellAdvance = b.cellAdvance;
        this.cells = List.copyOf(b.cells);
        this.logoWide = b.logoWide;
        this.logoSmall = b.logoSmall;
        this.negative = List.copyOf(b.negative);
        this.positive = List.copyOf(b.positive);
        this.letters = Map.copyOf(b.letters);
        this.letterSpace = b.letterSpace;
    }

    /** Parses the metrics JSON; throws {@link IllegalArgumentException} with the reason when something is missing. */
    public static FrameMetrics parse(String json) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().pathSeparator('\u001F');
        try {
            yaml.loadFromString(json == null ? "" : json);
        } catch (InvalidConfigurationException e) {
            throw new IllegalArgumentException("frame-metrics.json is not valid JSON: " + e.getMessage(), e);
        }
        Builder b = new Builder();
        b.titleOriginX = integer(yaml, "title_origin_x");
        b.titleBaseline = integer(yaml, "title_baseline");
        b.plaqueExtend = integer(yaml, "plaque_extend");
        b.windowWidth = integer(yaml, "window_width");
        ConfigurationSection panels = section(yaml, "panels");
        for (PanelType type : PanelType.values()) {
            ConfigurationSection p = section(panels, type.key());
            b.panels.put(type, new Panel(integer(p, "rows"), integer(p, "width"), integer(p, "height"), integer(p, "advance"), cp(p, "cp")));
        }
        ConfigurationSection plaques = section(yaml, "plaques");
        for (PlaqueSize size : PlaqueSize.values()) {
            ConfigurationSection p = section(plaques, size.key());
            b.plaques.put(size, new Plaque(integer(p, "width"), integer(p, "advance"), integer(p, "inner"), cp(p, "cp")));
        }
        ConfigurationSection cell = section(yaml, "cell");
        b.cellSize = integer(cell, "size");
        b.cellX0 = integer(cell, "x0");
        b.cellY0 = integer(cell, "y0");
        b.cellStep = integer(cell, "step");
        b.cellAdvance = integer(cell, "advance");
        for (String hex : cell.getStringList("cp")) {
            b.cells.add(cp(hex));
        }
        if (b.cells.size() != 6) {
            throw new IllegalArgumentException("cell.cp must list 6 code points (one per row)");
        }
        ConfigurationSection logo = section(yaml, "logo");
        b.logoWide = logo(section(logo, "wide"));
        b.logoSmall = logo(section(logo, "small"));
        ConfigurationSection space = section(yaml, "space");
        b.negative.addAll(spaces(space, "neg"));
        b.positive.addAll(spaces(space, "pos"));
        ConfigurationSection letters = section(yaml, "letters");
        ConfigurationSection advance = section(letters, "advance");
        for (String key : advance.getKeys(false)) {
            if (key.length() == 1) {
                b.letters.put(key.charAt(0), advance.getInt(key));
            }
        }
        b.letterSpace = integer(letters, "space");
        return new FrameMetrics(b);
    }

    /** The metrics bundled in the jar; empty (reported) when missing or broken, then nobody gets frame glyphs. */
    public static Optional<FrameMetrics> bundled(ClassLoader loader, Consumer<String> problem) {
        try (InputStream in = loader.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                problem.accept(RESOURCE + " is missing from the jar; menus stay plain");
                return Optional.empty();
            }
            return Optional.of(parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException | IllegalArgumentException e) {
            problem.accept("could not read " + RESOURCE + ": " + e.getMessage() + "; menus stay plain");
            return Optional.empty();
        }
    }

    private static ConfigurationSection section(ConfigurationSection parent, String key) {
        ConfigurationSection s = parent.getConfigurationSection(key);
        if (s == null) {
            throw new IllegalArgumentException("missing section '" + key + "'");
        }
        return s;
    }

    private static int integer(ConfigurationSection s, String key) {
        if (!s.isInt(key)) {
            throw new IllegalArgumentException("missing number '" + key + "' in " + s.getName());
        }
        return s.getInt(key);
    }

    private static char cp(ConfigurationSection s, String key) {
        String hex = s.getString(key);
        if (hex == null) {
            throw new IllegalArgumentException("missing code point '" + key + "' in " + s.getName());
        }
        return cp(hex);
    }

    private static char cp(String hex) {
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad code point '" + hex + "'", e);
        }
    }

    private static Logo logo(ConfigurationSection s) {
        return new Logo(cp(s, "cp"), integer(s, "advance"), integer(s, "ascent"), integer(s, "width"), integer(s, "height"));
    }

    private static List<SpaceGlyph> spaces(ConfigurationSection space, String key) {
        List<SpaceGlyph> out = new ArrayList<>();
        for (Map<?, ?> raw : space.getMapList(key)) {
            Object cp = raw.get("cp");
            Object step = raw.get("step");
            if (!(cp instanceof String hex) || !(step instanceof Number n)) {
                throw new IllegalArgumentException("space." + key + " entries need cp and step");
            }
            out.add(new SpaceGlyph(cp(hex), n.intValue()));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("space." + key + " is empty");
        }
        return out;
    }

    public int titleOriginX() {
        return titleOriginX;
    }

    public int titleBaseline() {
        return titleBaseline;
    }

    public int plaqueExtend() {
        return plaqueExtend;
    }

    public int windowWidth() {
        return windowWidth;
    }

    public Panel panel(PanelType type) {
        return panels.get(type);
    }

    public Plaque plaque(PlaqueSize size) {
        return plaques.get(size);
    }

    public int cellSize() {
        return cellSize;
    }

    public int cellX0() {
        return cellX0;
    }

    public int cellY0() {
        return cellY0;
    }

    public int cellStep() {
        return cellStep;
    }

    public int cellAdvance() {
        return cellAdvance;
    }

    /** The cell glyph of container row {@code row} (0-5). */
    public char cell(int row) {
        return cells.get(row);
    }

    public Logo logoWide() {
        return logoWide;
    }

    public Logo logoSmall() {
        return logoSmall;
    }

    /** Negative space glyphs (steps -1, -2, -4 ... -128). */
    public List<SpaceGlyph> negative() {
        return negative;
    }

    /** Positive space glyphs (steps 1, 2, 4 ... 128). */
    public List<SpaceGlyph> positive() {
        return positive;
    }

    /** Advance of a lettering character (visible width + pad), or -1 when the font has no such letter. */
    public int letter(char c) {
        if (c == ' ') {
            return letterSpace;
        }
        Integer a = letters.get(c);
        return a == null ? -1 : a;
    }

    /** Characters the lettering font has (plus space). */
    public java.util.Set<Character> letters() {
        return letters.keySet();
    }

    /** Every code point the frame font uses (panels, plaques, cells, logo, spaces). */
    public List<Character> codepoints() {
        List<Character> out = new ArrayList<>();
        panels.values().forEach(p -> out.add(p.cp()));
        plaques.values().forEach(p -> out.add(p.cp()));
        out.addAll(cells);
        out.add(logoWide.cp());
        out.add(logoSmall.cp());
        negative.forEach(s -> out.add(s.cp()));
        positive.forEach(s -> out.add(s.cp()));
        return out;
    }

    private static final class Builder {
        int titleOriginX;
        int titleBaseline;
        int plaqueExtend;
        int windowWidth;
        final Map<PanelType, Panel> panels = new HashMap<>();
        final Map<PlaqueSize, Plaque> plaques = new HashMap<>();
        int cellSize;
        int cellX0;
        int cellY0;
        int cellStep;
        int cellAdvance;
        final List<Character> cells = new ArrayList<>();
        Logo logoWide;
        Logo logoSmall;
        final List<SpaceGlyph> negative = new ArrayList<>();
        final List<SpaceGlyph> positive = new ArrayList<>();
        final Map<Character, Integer> letters = new HashMap<>();
        int letterSpace;
    }
}
