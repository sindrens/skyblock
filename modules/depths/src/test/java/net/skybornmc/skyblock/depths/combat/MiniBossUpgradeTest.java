package net.skybornmc.skyblock.depths.combat;

import net.skybornmc.skyblock.common.mob.AbilityId;
import net.skybornmc.skyblock.common.mob.MobDef;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniBossUpgradeTest {

    static String bundled() throws Exception {
        try (var in = MiniBossUpgradeTest.class.getClassLoader().getResourceAsStream(MobSections.FILE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The bundled line of a mini-boss without its abilities (what it was before M8.2). */
    static String withoutAbilities(String line) {
        int start = line.indexOf(", abilities: [");
        if (start < 0) {
            return line;
        }
        int depth = 0;
        int i = line.indexOf('[', start);
        for (; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']' && --depth == 0) {
                break;
            }
        }
        return line.substring(0, start) + line.substring(i + 1);
    }

    /** A live file as M4.8/M8.1 left it: mini-boss lines without abilities, no marker. */
    static String liveBeforeM82() throws Exception {
        StringBuilder out = new StringBuilder();
        for (String line : bundled().split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith(MiniBossUpgrade.MARKER_KEY + ":") || t.startsWith("# One-time upgrades")) {
                continue;
            }
            String id = t.contains(":") ? t.substring(0, t.indexOf(':')) : "";
            if (MiniBossUpgrade.M48_LINES.containsKey(id)) {
                line = line.substring(0, line.indexOf(t)) + MiniBossUpgrade.M48_LINES.get(id);
            }
            out.append(line).append('\n');
        }
        out.setLength(out.length() - 1);
        return out.toString();
    }

    static MobCatalog parse(String text, List<String> problems) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return MobCatalog.parse(y, y.getConfigurationSection(MobSections.ITEMS).getKeys(false), MobCatalogTest.TYPES, problems::add);
    }

    private static String lineOf(String text, String id) {
        for (String line : text.split("\n")) {
            if (line.trim().startsWith(id + ":")) {
                return line;
            }
        }
        return null;
    }

    @Test
    void theOldLinesAreExactlyTheNewOnesWithoutAbilities() throws Exception {
        String b = bundled();
        for (Map.Entry<String, String> e : MiniBossUpgrade.M48_LINES.entrySet()) {
            String line = lineOf(b, e.getKey());
            assertNotNull(line, e.getKey());
            assertTrue(line.contains("abilities:"), e.getKey() + " ships abilities");
            assertEquals(e.getValue(), withoutAbilities(line.trim()), "the jar's " + e.getKey() + " only adds abilities");
        }
        assertTrue(MiniBossUpgrade.applied(b), "new installs already have the marker");
        assertFalse(MiniBossUpgrade.apply(b, b).ran(), "nothing runs on a new install");
    }

    @Test
    void unchangedFileGetsEveryMiniBossAndNothingElseChanges() throws Exception {
        String live = liveBeforeM82();
        assertFalse(MiniBossUpgrade.applied(live));
        MiniBossUpgrade.Result r = MiniBossUpgrade.apply(live, bundled());
        assertTrue(r.ran());
        assertEquals(List.copyOf(MiniBossUpgrade.M48_LINES.keySet()), r.upgraded());
        assertTrue(r.skipped().isEmpty(), r.skipped().toString());
        assertTrue(MiniBossUpgrade.applied(r.text()));
        // Every other line is byte for byte the same.
        String[] before = live.split("\n", -1);
        String[] after = r.text().split("\n", -1);
        for (int i = 0; i < before.length; i++) {
            String id = before[i].trim().contains(":") ? before[i].trim().substring(0, before[i].trim().indexOf(':')) : "";
            if (!MiniBossUpgrade.M48_LINES.containsKey(id)) {
                assertEquals(before[i], after[i], "line " + (i + 1));
            }
        }
        List<String> problems = new ArrayList<>();
        MobCatalog c = parse(r.text(), problems);
        assertTrue(problems.isEmpty(), problems.toString());
        for (String id : MiniBossUpgrade.M48_LINES.keySet()) {
            assertEquals(3, c.mob(id).stats().abilities().size(), id);
        }
    }

    @Test
    void anEditedMiniBossIsLeftAlone() throws Exception {
        String live = liveBeforeM82().replace("grave_warden:     {type: zombie, health: 300,", "grave_warden:     {type: zombie, health: 350,");
        String ownAbilities = MiniBossUpgrade.M48_LINES.get("forge_lord").replace("}]}", "}], abilities: [{id: slam}]}");
        live = live.replace(MiniBossUpgrade.M48_LINES.get("forge_lord"), ownAbilities);
        MiniBossUpgrade.Result r = MiniBossUpgrade.apply(live, bundled());
        assertEquals("changed by the owner", r.skipped().get("grave_warden"));
        assertEquals("changed by the owner", r.skipped().get("forge_lord"));
        assertEquals(8, r.upgraded().size());
        assertTrue(lineOf(r.text(), "grave_warden").contains("health: 350") && !lineOf(r.text(), "grave_warden").contains("abilities"));
        assertEquals(ownAbilities, lineOf(r.text(), "forge_lord").trim());
        MobCatalog c = parse(r.text(), new ArrayList<>());
        MobDef lord = c.mob("forge_lord");
        assertEquals(1, lord.stats().abilities().size());
        assertEquals(AbilityId.SLAM, lord.stats().abilities().getFirst().id());
    }

    @Test
    void runsOnceEvenIfTheOwnerRemovesTheAbilitiesAgain() throws Exception {
        MiniBossUpgrade.Result first = MiniBossUpgrade.apply(liveBeforeM82(), bundled());
        assertFalse(MiniBossUpgrade.apply(first.text(), bundled()).ran());
        String removed = first.text().replace(lineOf(first.text(), "warlord").trim(), MiniBossUpgrade.M48_LINES.get("warlord"));
        MiniBossUpgrade.Result again = MiniBossUpgrade.apply(removed, bundled());
        assertFalse(again.ran());
        assertEquals(removed, again.text());
    }

    @Test
    void spacingAndWindowsLineEndingsDoNotCountAsEdits() throws Exception {
        String live = liveBeforeM82().replace("grave_warden:     {type: zombie,", "grave_warden: {type:  zombie,").replace("\n", "\r\n");
        MiniBossUpgrade.Result r = MiniBossUpgrade.apply(live, bundled());
        assertTrue(r.upgraded().contains("grave_warden"), r.skipped().toString());
        assertTrue(r.text().contains("\r\n"));
        assertTrue(lineOf(r.text(), "grave_warden").endsWith("\r"), "line ending kept");
        assertTrue(parse(r.text().replace("\r", ""), new ArrayList<>()).mob("grave_warden").stats().hasAbilities());
    }

    @Test
    void missingMiniBossIsReportedAndOtherUpgradesAreKept() throws Exception {
        String live = liveBeforeM82().replace(MiniBossUpgrade.M48_LINES.get("bone_king"), "# removed")
                + "\napplied-upgrades: [something-else]\n";
        MiniBossUpgrade.Result r = MiniBossUpgrade.apply(live, bundled());
        assertEquals("not in the file", r.skipped().get("bone_king"));
        assertTrue(r.text().contains("applied-upgrades: [something-else, " + MiniBossUpgrade.ID + "]"));
        assertEquals(1, r.text().split(MiniBossUpgrade.MARKER_KEY + ":", -1).length - 1, "one marker line");
    }

    @Test
    void fileUpgradeKeepsABackupAndWritesOnce(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(MobSections.FILE);
        assertNull(MiniBossUpgrade.upgrade(file, bundled()), "no file: nothing (the jar's copy comes with the abilities)");
        String live = liveBeforeM82();
        Files.writeString(file, live);
        MiniBossUpgrade.Result r = MiniBossUpgrade.upgrade(file, bundled());
        assertNotNull(r);
        assertEquals(10, r.upgraded().size());
        assertEquals(live, Files.readString(dir.resolve(MiniBossUpgrade.BACKUP)));
        assertEquals(r.text(), Files.readString(file));
        assertNull(MiniBossUpgrade.upgrade(file, bundled()), "second start: nothing");
    }
}
