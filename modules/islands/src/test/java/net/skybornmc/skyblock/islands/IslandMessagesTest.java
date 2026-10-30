package net.skybornmc.skyblock.islands;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Every message key the M2.7 code uses exists in messages/islands.yml (owner rule: all player-facing text in messages). */
class IslandMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");
    /** Files written for M2.7 (older files are covered by their own reviews). */
    private static final List<String> FILES = List.of("IslandLifecycle.java", "IslandAdmin.java", "SlotsMenu.java", "IslandClearer.java",
            // M2.4
            "VisitManager.java", "WarpManager.java", "VisitMenu.java", "WarpBrowserMenu.java", "WarpEditMenu.java", "IconPickerMenu.java",
            // ISL2
            "PersonalHomes.java", "IslandSettingsManager.java", "SettingsMenu.java", "InvitesMenu.java", "InviteActionsMenu.java",
            "IslandFly.java", "PermissionsMenu.java", "MemberActionsMenu.java", "MembersMenu.java", "MemberManager.java",
            // NOTES
            "IslandNotes.java", "NotesMenu.java", "NoteActionsMenu.java", "DeletedNotesMenu.java", "NoteBook.java", "NoteHolograms.java",
            // M2.5
            "IslandMenu.java", "ChatColorsMenu.java", "IslandChat.java", "IslandHub.java",
            // M2.6
            "IslandStats.java", "IslandStatsMenu.java", "IslandStatsMembersMenu.java", "IslandStatsListener.java");
    /** Keys of other files (commands.yml) and strings that are not message keys. */
    private static final Set<String> FOREIGN = Set.of("invalid-number", "player-unknown", "frame.next", "frame.previous", "title.page");

    private static YamlConfiguration messages() throws Exception {
        try (var in = IslandMessagesTest.class.getClassLoader().getResourceAsStream("messages/islands.yml")) {
            assertNotNull(in, "messages/islands.yml missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static boolean has(YamlConfiguration yml, String key) {
        return yml.contains(key) || yml.contains(key + ".name");
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = messages();
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/islands");
        Set<String> missing = new TreeSet<>();
        for (String name : FILES) {
            Matcher m = LITERAL.matcher(Files.readString(src.resolve(name)));
            while (m.find()) {
                String key = m.group(1);
                if (key.startsWith("skyblock.") || FOREIGN.contains(key) || key.startsWith("isadmin ")) {
                    continue;
                }
                if (!has(yml, key) && !yml.isConfigurationSection(key)) {
                    missing.add(key + " (" + name + ")");
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void dynamicKeysExist() throws Exception {
        YamlConfiguration yml = messages();
        List<String> keys = new ArrayList<>();
        for (SlotState state : SlotState.values()) {
            keys.add("admin.state." + state.key());
        }
        // confirm screens built from a prefix in IslandAdmin.confirm
        for (String prefix : List.of("admin.delete", "admin.reset", "admin.kick", "admin.setowner")) {
            keys.add(prefix + ".crumb");
            keys.add(prefix + ".shown.name");
            keys.add(prefix + ".details");
            keys.add(prefix + ".confirm");
        }
        for (PermRole role : PermRole.values()) {
            keys.add("role." + role.key());
        }
        // ISL2: matrix names/actions per permission, /is settings labels and values
        for (IslandPerm perm : IslandPerm.values()) {
            keys.add("perms.names." + perm.key());
            keys.add("protection.actions." + perm.key());
        }
        for (IslandFlag flag : IslandFlag.values()) {
            keys.add("flags." + flag.key() + ".label");
            keys.add("flags." + flag.key() + ".lore");
            for (String value : flag.choices()) {
                keys.add("flags.value." + value);
            }
        }
        for (String state : List.of("on", "off")) {
            keys.add("flags.state." + state);
            keys.add("flags.click." + state);
            keys.add("fly." + state);
        }
        keys.add("disband.confirm-word");
        // NOTES: deleted reasons and /settings toggles
        for (String reason : List.of(IslandNoteStore.REASON_DELETE, IslandNoteStore.REASON_CLEAR, IslandNoteStore.REASON_DISBAND)) {
            keys.add("notes.deleted.reason." + reason);
        }
        for (String toggle : List.of(IslandNotes.SETTING_ALERTS, IslandNotes.SETTING_SCOREBOARD)) {
            keys.add("settings." + toggle + ".name");
            keys.add("settings." + toggle + ".lore");
        }
        // M2.4: built from parts in VisitManager.voteDenied, the browser tabs, warp categories and /settings toggles
        for (String what : List.of("rate", "like")) {
            for (String part : List.of(".own", ".not-visited", ".already")) {
                keys.add(what + part);
            }
        }
        for (Showcase.Sort sort : Showcase.Sort.values()) {
            keys.add("browser.sort." + sort.key() + ".crumb");
            keys.add("browser.sort." + sort.key() + ".name");
            keys.add("browser.sort." + sort.key() + ".lore");
        }
        for (WarpCategory category : WarpCategory.values()) {
            keys.add("warps.category." + category.key());
        }
        for (String toggle : List.of(VisitManager.SETTING_ATTEMPTS, VisitManager.SETTING_ARRIVALS, VisitManager.SETTING_DEPARTURES)) {
            keys.add("settings." + toggle + ".name");
            keys.add("settings." + toggle + ".lore");
        }
        // M2.5: island chat spy toggle, role names in chat lines/colors, menu buttons built from a prefix
        keys.add("settings." + IslandChat.SETTING_SPY + ".name");
        keys.add("settings." + IslandChat.SETTING_SPY + ".lore");
        for (var role : IslandChatRules.COLOR_ROLES) {
            keys.add("role." + MemberStore.key(role));
        }
        // M2.6: category names, trend lines, the stats button when the service is off
        for (var stat : net.skybornmc.skyblock.common.island.IslandStat.values()) {
            keys.add("stats.category." + stat.key());
        }
        for (var kind : net.skybornmc.skyblock.islands.stats.StatsTrend.Kind.values()) {
            keys.add("stats.item.trend." + kind.name().toLowerCase(java.util.Locale.ROOT));
        }
        keys.add("stats.members.former");
        for (String button : List.of("home", "visit", "stats", "stats-off", "bank", "upgrades", "disband", "leave")) {
            keys.add("menu." + button + ".name");
            keys.add("menu." + button + ".lore");
        }
        Set<String> missing = new TreeSet<>();
        for (String key : keys) {
            if (!yml.contains(key)) {
                missing.add(key);
            }
        }
        assertEquals(Set.of(), missing);
    }
}
