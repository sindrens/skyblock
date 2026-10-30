package net.skybornmc.skyblock.achievements;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every message key the code uses exists, and menu lore lines fit 36 visible characters (Bedrock, UI_STYLE §4). */
class MessagesTest {

    private static final Pattern KEY = Pattern.compile("\"((?:unlock|menu|title-source|title-source-island|no-permission|season)(?:\\.[a-z][a-z-]*)*)\"");

    @Test
    void usedKeysExist() throws Exception {
        YamlConfiguration yml = Fixtures.resource("messages/achievements.yml");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/java/net/skybornmc/skyblock/achievements"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("AchievementsConfig.java")).toList()) {
                Matcher m = KEY.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (!yml.contains(key) && !yml.contains(key + ".name") && !yml.contains(key + ".title")) {
                        missing.add(key + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void loreLinesFitBedrock() {
        YamlConfiguration yml = Fixtures.resource("messages/achievements.yml");
        Set<String> tooLong = new TreeSet<>();
        for (String key : yml.getConfigurationSection("menu").getKeys(true)) {
            String path = "menu." + key;
            List<String> lines = yml.isList(path) ? yml.getStringList(path) : yml.isString(path) ? List.of(yml.getString(path)) : List.of();
            for (String line : lines) {
                String visible = AchievementRegistry.visible(line.replace("<sym:bullet>", "▸").replace("<sym:check>", "✔"));
                if (visible.length() > AchievementRegistry.MAX_LINE) {
                    tooLong.add(path + ": " + visible);
                }
            }
        }
        assertEquals(Set.of(), tooLong);
    }

    @Test
    void noUnescapedDoubleQuotesInsideMessages() throws Exception {
        for (String name : List.of("messages/achievements.yml", "achievements.yml")) {
            try (var in = getClass().getClassLoader().getResourceAsStream(name)) {
                String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                YamlConfiguration yml = new YamlConfiguration();
                yml.loadFromString(text); // throws on a broken quote
            }
        }
    }

    /** Keys built from enum ids at runtime (tabs, boost names, island boosts) exist too. */
    @Test
    void dynamicKeysExist() {
        YamlConfiguration yml = Fixtures.resource("messages/achievements.yml");
        Set<String> missing = new TreeSet<>();
        for (AchievementsMenu.Tab tab : AchievementsMenu.Tab.values()) {
            String id = tab.name().toLowerCase(java.util.Locale.ROOT);
            for (String key : List.of("menu.tab." + id, "menu.tab-hint." + id)) {
                if (!yml.isString(key)) {
                    missing.add(key);
                }
            }
        }
        for (PersonalBoost boost : PersonalBoost.values()) {
            if (!yml.isString("menu.boost.name." + boost.id())) {
                missing.add("menu.boost.name." + boost.id());
            }
        }
        for (net.skybornmc.skyblock.common.achievements.IslandBoost boost : net.skybornmc.skyblock.common.achievements.IslandBoost.values()) {
            if (!yml.isString("menu.island.boost.name." + boost.id())) {
                missing.add("menu.island.boost.name." + boost.id());
            }
            if (!yml.isList("menu.island.boost.lore." + boost.id())) {
                missing.add("menu.island.boost.lore." + boost.id());
            }
            if (boost != net.skybornmc.skyblock.common.achievements.IslandBoost.ISLAND_XP && !yml.isList("menu.island.boost.later." + boost.id())) {
                missing.add("menu.island.boost.later." + boost.id());
            }
        }
        assertEquals(Set.of(), missing);
    }
}
