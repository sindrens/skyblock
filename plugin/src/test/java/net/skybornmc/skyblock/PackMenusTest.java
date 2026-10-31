package net.skybornmc.skyblock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.menu.MenuIcons;
import net.skybornmc.skyblock.common.menu.PackTitles;
import net.skybornmc.skyblock.common.pack.PackIds;
import net.skybornmc.skyblock.menus.MenuModels;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GUI-6 / RP3, pure (no server): every menu class in the code has a pack background, every pack icon id the code uses
 * exists in pack-ids.yml, and the pack title is built from the right space sequence.
 */
class PackMenusTest {

    static final Path ROOT = Path.of("..");
    static PackIds ids;

    @BeforeAll
    static void load() throws IOException {
        try (FileReader r = new FileReader(ROOT.resolve("plugin/src/main/resources/pack/pack-ids.yml").toFile(), StandardCharsets.UTF_8)) {
            ids = PackIds.parse(r);
        }
    }

    // ------------------------------------------------------------------ source scan

    /** {@code module name → its main java source files}; the framework counts as module {@code common}. */
    static Map<String, List<Path>> sources() throws IOException {
        Map<String, List<Path>> out = new HashMap<>();
        out.put("common", java(ROOT.resolve("common/src/main/java")));
        try (Stream<Path> modules = Files.list(ROOT.resolve("modules"))) {
            for (Path m : modules.toList()) {
                Path src = m.resolve("src/main/java");
                if (Files.isDirectory(src)) {
                    out.put(m.getFileName().toString(), java(src));
                }
            }
        }
        return out;
    }

    private static List<Path> java(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Decl(String module, String name, String parent, boolean isAbstract) {
    }

    private static final Pattern CLASS = Pattern.compile(
            "(?m)^\\s*(?:public |protected |private |static |final |sealed )*(abstract )?class (\\w+)(?:<[^{(]*?>)?\\s+extends\\s+(\\w+)");

    /** Concrete classes that (transitively) extend Menu or PagedMenu. */
    static Set<String> menuClasses() throws IOException {
        List<Decl> decls = new ArrayList<>();
        for (Map.Entry<String, List<Path>> e : sources().entrySet()) {
            for (Path p : e.getValue()) {
                Matcher m = CLASS.matcher(read(p));
                while (m.find()) {
                    decls.add(new Decl(e.getKey(), m.group(2), m.group(3), m.group(1) != null));
                }
            }
        }
        Set<String> menus = new TreeSet<>(Set.of("Menu", "PagedMenu"));
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Decl d : decls) {
                if (menus.contains(d.parent()) && menus.add(d.name())) {
                    grew = true;
                }
            }
        }
        Set<String> out = new TreeSet<>();
        for (Decl d : decls) {
            if (menus.contains(d.parent()) && !d.isAbstract()) {
                out.add(d.module() + "/" + d.name());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ (a) every menu has a background

    @Test
    void everyMenuClassHasABackground() throws IOException {
        Set<String> menus = menuClasses();
        assertTrue(menus.size() >= 150, "found only " + menus.size() + " menu classes: the scan is broken");
        Set<String> missing = new TreeSet<>();
        for (String menu : menus) {
            if (!ids.menu().classes().containsKey(menu) && !PackTitles.CODE_BACKGROUNDS.containsKey(menu)
                    && !PackTitles.CORE_FRAME_BY_ROWS.contains(menu)) {
                missing.add(menu);
            }
        }
        assertEquals(Set.of(), missing, "menu classes without a background (add them to PackTitles.CODE_BACKGROUNDS or the pack art)");
    }

    @Test
    void codeBackgroundsAreRealAndNotStale() throws IOException {
        Set<String> menus = menuClasses();
        PackTitles.CODE_BACKGROUNDS.forEach((cls, bg) -> {
            assertTrue(ids.menu().backgrounds().containsKey(bg), cls + " -> " + bg);
            assertTrue(menus.contains(cls), cls + " is no menu class any more");
            assertFalse(ids.menu().classes().containsKey(cls), cls + " is in pack-ids.yml now: drop the code mapping");
        });
        for (String cls : PackTitles.CORE_FRAME_BY_ROWS) {
            assertTrue(menus.contains(cls), cls);
        }
    }

    @Test
    void everyMappedBackgroundExists() {
        ids.menu().classes().forEach((cls, bg) -> assertTrue(ids.menu().backgrounds().containsKey(bg), cls + " -> " + bg));
    }

    // ------------------------------------------------------------------ (b) every icon id used exists

    private static final Pattern LITERAL = Pattern.compile("\"((?:menu|currency)/[a-z0-9_/]*[a-z0-9_])\"");
    private static final Pattern FEATURE = Pattern.compile("MenuIcons\\.feature\\(\"([a-z0-9_]+)\"\\)");
    private static final Pattern TAB = Pattern.compile("MenuIcons\\.tab\\(\"([a-z0-9_]+)\"\\)");
    private static final Pattern CURRENCY = Pattern.compile("MenuIcons\\.currency\\(\"([a-z0-9_]+)\"\\)");

    @Test
    void everyIconIdTheCodeUsesExists() throws IOException {
        Set<String> used = new TreeSet<>();
        for (List<Path> files : sources().values()) {
            for (Path p : files) {
                String text = read(p);
                collect(LITERAL, text, "", used);
                collect(FEATURE, text, "menu/icon/", used);
                collect(TAB, text, "menu/tab/", used);
                collect(CURRENCY, text, "currency/", used);
            }
        }
        // Constants of MenuIcons and the default maps of the hub, You and Travel.
        for (Field f : MenuIcons.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class && Modifier.isPublic(f.getModifiers())) {
                try {
                    used.add((String) f.get(null));
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
        used.addAll(MenuModels.HUB.values());
        used.addAll(MenuModels.YOU.values());
        used.addAll(MenuModels.TRAVEL.values());
        used.add(MenuIcons.currencyOf("gems"));
        used.add(MenuIcons.currencyOf("money"));
        used.add(MenuIcons.currencyOf("crystals"));
        used.add(MenuIcons.currencyOf("shards"));
        assertTrue(used.size() > 60, "scan found only " + used.size() + " ids");
        Set<String> unknown = new TreeSet<>(used);
        unknown.removeAll(ids.icons());
        assertTrue(unknown.isEmpty(), "icon ids used in code but missing from pack-ids.yml: " + unknown);
    }

    private static void collect(Pattern pattern, String text, String prefix, Set<String> into) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            into.add(prefix + m.group(1));
        }
    }

    @Test
    void shopTabsMatchTabIcons() {
        for (String tab : List.of("essentials", "farming", "wood", "stone", "special", "colors", "decoration", "redstone", "spawners")) {
            assertTrue(ids.icons().contains(MenuIcons.tab(tab)), tab);
        }
    }

    @Test
    void stepIconsGrowWithTheIndex() {
        assertEquals("menu/minus_small", MenuIcons.minus(0));
        assertEquals("menu/minus_mid", MenuIcons.minus(1));
        assertEquals("menu/minus_big", MenuIcons.minus(2));
        assertEquals("menu/plus_small", MenuIcons.plus(0));
        assertEquals("menu/plus_big", MenuIcons.plus(2));
    }

    // ------------------------------------------------------------------ (c) the title

    private static String text(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void titleHasTheSpaceSequenceGlyphAndParchmentText() {
        PackIds.Background bg = ids.menu().backgrounds().get("hub");
        Component title = Component.text("Main menu", net.kyori.adventure.text.format.NamedTextColor.GRAY);
        Component built = PackTitles.build(ids, bg, title);

        List<Component> parts = built.children();
        assertEquals(2, parts.size(), "prefix + title");
        List<Component> prefix = parts.get(0).children();
        assertEquals(4, prefix.size());
        // space(-8) · glyph · space(-(width+1)) · space(+offset)
        assertEquals(ids.space(-8), ((net.kyori.adventure.text.TextComponent) prefix.get(0)).content());
        assertEquals(PackTitles.SPACE_FONT, prefix.get(0).font());
        assertEquals(bg.glyph(), ((net.kyori.adventure.text.TextComponent) prefix.get(1)).content());
        assertEquals(PackTitles.MENU_FONT, prefix.get(1).font());
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.WHITE, prefix.get(1).color());
        assertEquals(net.kyori.adventure.text.format.ShadowColor.none(), prefix.get(1).shadowColor());
        assertEquals(ids.space(-(ids.menu().width() + 1)), ((net.kyori.adventure.text.TextComponent) prefix.get(2)).content());
        assertEquals(ids.space(ids.menu().titleOffset()), ((net.kyori.adventure.text.TextComponent) prefix.get(3)).content());
        assertEquals(PackTitles.SPACE_FONT, prefix.get(3).font());

        assertEquals(TextColor.fromHexString("#E8DCC0"), parts.get(1).color());
        assertEquals("Main menu", text(parts.get(1)));
    }

    @Test
    void spaceSequencesAddUpToTheWidths() {
        assertEquals(-177, advance(ids.space(-(ids.menu().width() + 1))));
        assertEquals(176, ids.menu().width());
        assertEquals(-8, advance(ids.space(-8)));
        assertEquals(8, advance(ids.space(ids.menu().titleOffset())));
        assertEquals(13, ids.menu().ascent());
    }

    private static int advance(String spaces) {
        Map<String, Integer> byChar = new HashMap<>();
        ids.spaces().forEach((px, ch) -> byChar.put(ch, px));
        int sum = 0;
        for (int i = 0; i < spaces.length(); i++) {
            sum += byChar.get(String.valueOf(spaces.charAt(i)));
        }
        return sum;
    }

    @Test
    void titleRecolorsNestedColors() {
        Component nested = Component.text("▸ ", net.kyori.adventure.text.format.NamedTextColor.RED)
                .append(Component.text("Island", net.kyori.adventure.text.format.NamedTextColor.GREEN));
        Component built = PackTitles.build(ids, ids.menu().backgrounds().get("frame_core_3"), nested);
        Component title = built.children().get(1);
        assertEquals(TextColor.fromHexString("#E8DCC0"), title.color());
        assertEquals(TextColor.fromHexString("#E8DCC0"), title.children().get(0).color());
    }

    @Test
    void moduleFromThePackageAndFallbackFrames() {
        assertEquals("islands", PackTitles.moduleOf("net.skybornmc.skyblock.islands.IslandMenu"));
        assertEquals("common", PackTitles.moduleOf("net.skybornmc.skyblock.common.menu.ConfirmMenu"));
        assertEquals("menus", PackTitles.moduleOf("net.skybornmc.skyblock.menus.nested.Deep"));
        // known class
        assertEquals("island", PackTitles.background(ids, "islands", "IslandMenu", 6).orElseThrow().id());
        // unknown class: frame_core_<rows>, else the 6-row core frame
        assertEquals("shop_buy", PackTitles.background(ids, "shop", "BuyMenu", 6).orElseThrow().id());
        assertEquals("frame_core_3", PackTitles.background(ids, "nope", "NopeMenu", 3).orElseThrow().id());
        assertEquals("frame_core_4", PackTitles.background(ids, "nope", "NopeMenu", 4).orElseThrow().id());
        assertEquals("frame_core_6", PackTitles.background(ids, "nope", "NopeMenu", 5).orElseThrow().id());
    }

    @Test
    void hubModelsCoverTheDefaultButtons() {
        assertEquals("menu/icon/home", MenuModels.TRAVEL.get("my-island"));
        assertEquals("menu/icon/shop", MenuModels.TRAVEL.get("market"));
        assertEquals("menu/icon/rewards", MenuModels.HUB.get("rewards"));
        assertNotNull(MenuIcons.currencyOf("gems"));
        assertNull(MenuIcons.currencyOf("tokens"));
    }
}
