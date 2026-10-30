package net.skybornmc.skyblock.npcs;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * messages/npcs.yml parses, every key the code uses exists, only Bedrock-safe symbols are used, and every NPC,
 * hologram and leaderboard of the delivered builds (server/builds/*.yml) has its texts.
 */
class MessagesTest {

    private static final Pattern KEY = Pattern.compile("\"((?:npc|help|leaderboard|admin)(?:\\.[a-z0-9][a-z0-9-]*)+|hologram-fallback)\"");
    private static final String SAFE = "✦✧◆●▸▶★✔✘⚔⛏❤⌛»«│·•";

    private static String text() throws Exception {
        try (var in = MessagesTest.class.getClassLoader().getResourceAsStream("messages/npcs.yml")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static YamlConfiguration yml() throws Exception {
        YamlConfiguration yml = new YamlConfiguration();
        yml.loadFromString(text()); // throws on a broken quote
        return yml;
    }

    private static boolean present(YamlConfiguration yml, String key) {
        return yml.isString(key) || yml.isList(key);
    }

    @Test
    void usedKeysExist() throws Exception {
        YamlConfiguration yml = yml();
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/java/net/skybornmc/skyblock/npcs"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("NpcsConfig.java")).toList()) {
                Matcher m = KEY.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (!present(yml, key) && !yml.isConfigurationSection(key)) {
                        missing.add(key + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertEquals(Set.of(), missing);
        for (String key : List.of("help.menu.open.name", "admin.menu.entry.name", "admin.menu.entry.lore", "admin.menu.empty.name",
                "admin.menu.info", "admin.kind.npc", "admin.kind.hologram", "admin.kind.board", "leaderboard.empty-place")) {
            assertTrue(present(yml, key), key);
        }
    }

    @Test
    void onlyBedrockSafeSymbols() throws Exception {
        Set<String> bad = new TreeSet<>();
        text().codePoints().filter(cp -> cp > 127 && SAFE.indexOf(cp) < 0).forEach(cp -> bad.add(new String(Character.toChars(cp))));
        assertEquals(Set.of(), bad);
    }

    @Test
    void everyConfiguredRoleHasNameAndHelp() throws Exception {
        YamlConfiguration yml = yml();
        NpcsConfig c = new NpcsConfig();
        RoleRules rules = RoleRules.parse(c.npcRoles, c.roleActions);
        Set<String> roles = new TreeSet<>(rules.actions().keySet());
        for (String line : c.npcRoles) {
            roles.add(line.substring(line.indexOf(':') + 1).trim());
        }
        List<String> missing = new ArrayList<>();
        for (String role : roles) {
            if (!yml.isString("roles." + role + ".name")) {
                missing.add(role + ".name");
            }
            if (!present(yml, "roles." + role + ".help")) {
                missing.add(role + ".help");
            }
        }
        assertEquals(List.of(), missing);
    }

    /** The delivered builds: every NPC has a named role with help, every hologram a text, every board a source + title. */
    @Test
    void deliveredBuildsHaveTexts() throws Exception {
        File dir = new File("../../server/builds");
        File[] metas = dir.listFiles((d, n) -> n.endsWith(".yml") && !n.equals("depths_layout.yml"));
        assertNotNull(metas, "server/builds not found");
        YamlConfiguration yml = yml();
        NpcsConfig c = new NpcsConfig();
        RoleRules rules = RoleRules.parse(c.npcRoles, c.roleActions);
        Map<String, BoardSource> sources = BoardSource.parseAll(c.sources, new ArrayList<>());
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (File f : metas) {
            YamlConfiguration meta = YamlConfiguration.loadConfiguration(f);
            checked += meta.getMapList("npcs").size() + meta.getMapList("holograms").size() + meta.getMapList("leaderboards").size();
            for (Map<?, ?> e : meta.getMapList("npcs")) {
                String id = String.valueOf(e.get("id")).toLowerCase();
                String role = rules.roleOf(id, e.get("role") == null ? null : String.valueOf(e.get("role")));
                if (!present(yml, "roles." + role + ".help")) {
                    missing.add("npc " + id + " -> role " + role + " has no help (" + f.getName() + ")");
                }
            }
            for (Map<?, ?> e : meta.getMapList("holograms")) {
                String id = String.valueOf(e.get("id")).toLowerCase();
                if (!c.hologramSkip.contains(id) && !present(yml, "holograms." + id) && e.get("text") == null) {
                    missing.add("hologram " + id + " has no text (" + f.getName() + ")");
                }
            }
            for (Map<?, ?> e : meta.getMapList("leaderboards")) {
                String id = String.valueOf(e.get("id")).toLowerCase();
                if (!sources.containsKey(id)) {
                    missing.add("leaderboard " + id + " has no source");
                }
                if (!yml.isString("leaderboard.titles." + id)) {
                    missing.add("leaderboard " + id + " has no title");
                }
            }
        }
        assertEquals(List.of(), missing);
        assertTrue(checked >= 60, "the build metadata was read (" + checked + " points)");
    }
}
