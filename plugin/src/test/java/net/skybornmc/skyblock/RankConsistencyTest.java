package net.skybornmc.skyblock;

import net.skybornmc.skyblock.common.rank.RankLooks;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ranks v2 (batch 10): every rank look in chat.yml ({@link RankLooks#DEFAULTS}) belongs to a LuckPerms group in
 * server/permissions/base.txt, with the same colour, and every prefix priority equals the group's weight.
 */
class RankConsistencyTest {

    private static final Pattern WEIGHT = Pattern.compile("^lp group (\\S+) setweight (\\d+)");
    private static final Pattern COLOR = Pattern.compile("^lp group (\\S+) meta set color \"(#[0-9A-Fa-f]{6})\"");
    private static final Pattern PREFIX = Pattern.compile("^lp group (\\S+) meta addprefix (\\d+) ");

    private static List<String> base() throws Exception {
        Path p = Path.of("..", "server", "permissions", "base.txt");
        if (!Files.exists(p)) {
            p = Path.of("server", "permissions", "base.txt");
        }
        return Files.readAllLines(p);
    }

    @Test
    void looksMatchTheLuckPermsGroups() throws Exception {
        Map<String, Integer> weights = new HashMap<>();
        Map<String, String> colors = new HashMap<>();
        List<String> problems = new ArrayList<>();
        List<String[]> prefixes = new ArrayList<>();
        for (String raw : base()) {
            String line = raw.strip();
            Matcher w = WEIGHT.matcher(line);
            if (w.find()) {
                weights.put(w.group(1), Integer.parseInt(w.group(2)));
            }
            Matcher c = COLOR.matcher(line);
            if (c.find()) {
                colors.put(c.group(1), c.group(2).toUpperCase(Locale.ROOT));
            }
            Matcher pre = PREFIX.matcher(line);
            if (pre.find()) {
                prefixes.add(new String[]{pre.group(1), pre.group(2)});
            }
        }
        RankLooks looks = RankLooks.parse(RankLooks.DEFAULTS, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(17, looks.groups().size());
        for (String group : looks.groups()) {
            assertTrue(weights.containsKey(group), group + " has a LuckPerms group with a weight");
            String hex = looks.look(group).colors().getFirst().asHexString().toUpperCase(Locale.ROOT);
            assertEquals(hex, colors.get(group), group + ": the chat look and the LuckPerms colour match");
        }
        for (String[] p : prefixes) {
            assertEquals(weights.get(p[0]), Integer.valueOf(p[1]), p[0] + ": prefix priority = weight");
        }
    }
}
