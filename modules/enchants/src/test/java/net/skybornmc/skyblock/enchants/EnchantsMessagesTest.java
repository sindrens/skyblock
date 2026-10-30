package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.item.Rarity;
import org.bukkit.configuration.ConfigurationSection;
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

/** Every message key the code uses exists, and menu/lore lines fit 36 visible characters (Bedrock, UI_STYLE §4). */
class EnchantsMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");
    private static final Set<String> NOT_MESSAGES = Set.of("enchants.yml", "enchanter.yml");

    private static boolean has(YamlConfiguration yml, String key) {
        return yml.contains(key) || yml.contains(key + ".name");
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = Fixtures.resource("messages/enchants.yml");
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/enchants");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(src)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("EnchanterConfig.java")).toList()) {
                Matcher m = LITERAL.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (key.startsWith("skyblock.") || NOT_MESSAGES.contains(key)) {
                        continue;
                    }
                    if (!has(yml, key)) {
                        missing.add(key + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void dynamicKeysExist() {
        YamlConfiguration yml = Fixtures.resource("messages/enchants.yml");
        Set<String> missing = new TreeSet<>();
        for (EnchantRules.Problem p : EnchantRules.Problem.values()) {
            check(yml, "rule." + EnchantMenus.problemKey(p), missing);
            check(yml, "why." + EnchantMenus.problemKey(p), missing);
        }
        for (EnchantTarget t : EnchantTarget.values()) {
            check(yml, "target." + t.id(), missing);
        }
        for (Pool p : Pool.values()) {
            check(yml, "pool." + p.id(), missing);
        }
        for (Rarity r : Rarity.values()) {
            if (r != Rarity.SPECIAL) {
                check(yml, "rarity." + r.id(), missing);
            }
        }
        for (SlotMenu.Mode m : SlotMenu.Mode.values()) {
            check(yml, "menu.mode." + m.key(), missing);
            check(yml, "menu.tools.click." + m.key(), missing);
            check(yml, "menu.slots.info." + m.key(), missing);
            check(yml, "menu.slots.filled.click." + m.key(), missing);
        }
        check(yml, "menu.mode.combine", missing);
        for (RollType t : RollType.values()) {
            check(yml, "menu.shop.roll." + t.id(), missing);
        }
        for (GuideMenu.Tab t : GuideMenu.Tab.values()) {
            check(yml, "menu.guide.tab." + t.key(), missing);
        }
        for (String key : List.of("menu.slots.empty.apply", "menu.slots.empty.other", "menu.slots.prestige-empty.apply",
                "menu.slots.prestige-empty.other")) {
            check(yml, key, missing);
        }
        assertEquals(Set.of(), missing);
    }

    private static void check(YamlConfiguration yml, String key, Set<String> missing) {
        if (!has(yml, key)) {
            missing.add(key);
        }
    }

    @Test
    void menuAndLoreLinesFitBedrock() {
        YamlConfiguration yml = Fixtures.resource("messages/enchants.yml");
        Set<String> tooLong = new TreeSet<>();
        for (String root : List.of("menu", "why", "book", "scroll")) {
            ConfigurationSection section = yml.getConfigurationSection(root);
            if (section == null) {
                continue;
            }
            for (String key : section.getKeys(true)) {
                if (key.equals("description") || key.equals("unknown")) {
                    continue; // item descriptions are wrapped by the lore builder
                }
                Object value = section.get(key);
                List<?> lines = value instanceof List<?> list ? list : value instanceof String s ? List.of(s) : List.of();
                for (Object line : lines) {
                    String visible = String.valueOf(line).replaceAll("<sym:[a-z]+>", "*").replaceAll("<[^>]+>", "")
                            .replaceAll("\\{[a-z-]+}", "");
                    if (visible.length() > 36) {
                        tooLong.add(root + "." + key + ": " + visible);
                    }
                }
            }
        }
        assertEquals(Set.of(), tooLong);
    }
}
