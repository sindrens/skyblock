package net.skybornmc.skyblock.wardrobe;

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
class MessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)*)\"");

    private static boolean has(YamlConfiguration yml, String key) {
        return yml.contains(key) || yml.contains(key + ".name");
    }

    /** Message keys passed to send/fail/text/lines/item/actionBar (a literal right after the call). */
    private static final Pattern CALL = Pattern.compile(
            "(?:fail|send|actionBar|text|lines|raw|plain|component)\\((?:[a-zA-Z().]+, )?(?:WardrobeModule\\.ID, )?\"([a-z][a-z.-]*)\"");
    private static final Pattern ITEM = Pattern.compile("WardrobeModule\\.ID, \"([a-z][a-z.-]*)\"");

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = Fixtures.resource("messages/wardrobe.yml");
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/wardrobe");
        Set<String> missing = new TreeSet<>();
        Set<String> skip = Set.of("wardrobe", "sets", "staff", "success", "error", "click", "unlock", "piece-name.", "bonus-text.");
        try (Stream<Path> files = Files.list(src)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("WardrobeConfig.java")
                    && !f.endsWith("WardrobeOps.java") && !f.endsWith("WardrobeStore.java")).toList()) {
                String code = Files.readString(file);
                for (Pattern p : List.of(CALL, ITEM)) {
                    Matcher m = p.matcher(code);
                    while (m.find()) {
                        String key = m.group(1);
                        if (skip.contains(key) || key.endsWith(".") || key.startsWith("skyblock.")) {
                            continue;
                        }
                        if (!has(yml, key)) {
                            missing.add(key + " (" + file.getFileName() + ")");
                        }
                    }
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void dynamicKeysExist() {
        YamlConfiguration yml = Fixtures.resource("messages/wardrobe.yml");
        Set<String> missing = new TreeSet<>();
        for (String f : WardrobeMenu.FILTERS) {
            check(yml, "filter." + f, missing);
        }
        for (String a : SetRegistry.ACTIVITIES) {
            check(yml, "activity-name." + a, missing);
        }
        for (String w : SetRegistry.WINGS) {
            check(yml, "wing-name." + w, missing);
        }
        for (var p : net.skybornmc.skyblock.common.wardrobe.ArmorPiece.values()) {
            check(yml, "piece-name." + p.key(), missing);
        }
        for (String k : List.of("gate.tier", "gate.tier-any", "gate.any-skill", "gate.skill", "gate.none", "status.owned", "status.disabled",
                "status.arrives", "status.locked", "status.claim", "status.claimed", "status.buy", "status.crate", "status.also-from",
                "menu.main.info", "menu.set.info", "menu.chest.info", "menu.store.info", "menu.staff.info", "menu.main.empty", "menu.chest.empty",
                "menu.store.empty", "menu.staff.empty", "confirm.give-set.crumb", "confirm.give-set.details", "confirm.give-token.crumb",
                "confirm.give-token.details", "menu.set.level", "menu.set.level-off")) {
            check(yml, k, missing);
        }
        for (MergeRules.Refusal r : MergeRules.Refusal.values()) {
            check(yml, "merge.why." + r.key(), missing);
        }
        for (String a : SetXp.ACTIVITIES) {
            check(yml, "xp-activity." + a, missing);
        }
        for (String k : List.of(Tomes.NORMAL, Tomes.GREATER)) {
            check(yml, "tome.name." + k, missing);
            check(yml, "menu.staff.give-tome." + k, missing);
        }
        for (String k : List.of("xp-activity.none", "level-title.title", "level-title.subtitle", "menu.set.trail-on", "menu.set.trail-off",
                "status.owned-fixed", "status.merged", "settings.trail", "merged-name", "merge.free")) {
            check(yml, k, missing);
        }
        // Loadouts (W3): keys picked at runtime.
        for (LoadoutRules.NameProblem p : LoadoutRules.NameProblem.values()) {
            check(yml, "loadout.name." + p.key, missing);
        }
        for (LoadoutPlanner.Reason r : LoadoutPlanner.Reason.values()) {
            check(yml, "loadout.skip." + r.key, missing);
        }
        for (String k : List.of("loading", "busy", "frozen", "in-combat", "cooldown", "loadout.blocked-world", "menu.loadout.pet",
                "menu.loadout.edit-crumb", "confirm.redeem.crumb", "confirm.redeem.details", "menu.loadout.apply", "menu.loadout.apply-locked", "menu.loadout.preset.click",
                "menu.loadout.preset.locked", "menu.loadout.line.pet", "menu.loadout.line.pet-keep", "menu.loadout.line.tool",
                "menu.loadout.line.tool-missing", "menu.loadout.line.set", "menu.loadout.line.gone", "menu.loadout.info",
                "menu.loadout.edit-info", "menu.loadout.icon-info", "menu.loadout.icon-empty", "confirm.loadout-save.crumb",
                "confirm.loadout-save.details", "confirm.loadout-delete.crumb", "confirm.loadout-delete.details", "loadout.default-name",
                "loadout.slot-name", "chest.full", "chest.stale", "chest.kept")) {
            check(yml, k, missing);
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
        YamlConfiguration yml = Fixtures.resource("messages/wardrobe.yml");
        Set<String> tooLong = new TreeSet<>();
        for (String root : List.of("menu", "status", "confirm", "piece", "token", "tome", "bonus", "bonus-text", "gate", "settings")) {
            ConfigurationSection section = yml.getConfigurationSection(root);
            if (section == null) {
                continue;
            }
            for (String key : section.getKeys(true)) {
                if (key.equals("description") || key.equals("unknown")) {
                    continue; // wrapped by the lore builder
                }
                Object value = section.get(key);
                List<?> lines = value instanceof List<?> list ? list : value instanceof String s ? List.of(s) : List.of();
                for (Object line : lines) {
                    String visible = String.valueOf(line).replaceAll("<sym:[a-z]+>", "*").replaceAll("<requirement>", "Mining tier 12 ▸ 12/12")
                            .replaceAll("<[^>]+>", "").replaceAll("\\{value}", "99.9").replaceAll("\\{[a-z-]+}", "12");
                    if (visible.length() > 36) {
                        tooLong.add(root + "." + key + ": " + visible);
                    }
                }
            }
        }
        assertEquals(Set.of(), tooLong);
    }
}
