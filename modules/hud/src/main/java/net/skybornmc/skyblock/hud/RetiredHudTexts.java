package net.skybornmc.skyblock.hud;

import java.util.HashMap;
import java.util.Map;

/**
 * Old bundled texts of {@code messages/hud.yml}: a live text that still equals one word for word (never edited by the
 * owner) is upgraded to the new bundled text on load ({@code Messages#retire}). Owner 2 Nov 2026: a new board title, a
 * Rank row and a fuller spawn board.
 */
final class RetiredHudTexts {

    private RetiredHudTexts() {
    }

    static Map<String, String> all() {
        Map<String, String> m = new HashMap<>();
        m.put("scoreboard.default.title", "<grad:phoenix><bold>PHOENIX SKYBLOCK</bold></grad>");
        m.put("scoreboard.default.lines", String.join("\n",
                "<soft-blank>",
                " <bold><c:header>| You</c:header></bold>",
                " <c:bar>|</c:bar> <c:text>Money:</c:text> <money>",
                "if:crystals_owned| <c:bar>|</c:bar> <c:text>Crystals:</c:text> <crystals>",
                "if:credits_owned| <c:bar>|</c:bar> <c:text>Credits:</c:text> <credits>",
                " <bold><c:header>| Island</c:header></bold>",
                "if:island_level| <c:bar>|</c:bar> <c:text>Level:</c:text> <island>",
                "if:objective| <c:bar>|</c:bar> <objective>",
                "if:island_level| <c:bar>|</c:bar> <progress:island_level_progress>",
                "if:board_timer| <c:bar>|</c:bar> <timer>",
                " <c:sky_ice>play.skybornmc.net"));
        m.put("scoreboard.island.title", "<grad:phoenix><bold>PHOENIX SKYBLOCK</bold></grad>");
        m.put("scoreboard.island.lines", String.join("\n",
                "<soft-blank>",
                " <bold><c:header>| You</c:header></bold>",
                " <c:bar>|</c:bar> <c:text>Money:</c:text> <money>",
                "if:crystals_owned| <c:bar>|</c:bar> <c:text>Crystals:</c:text> <crystals>",
                "if:credits_owned| <c:bar>|</c:bar> <c:text>Credits:</c:text> <credits>",
                " <bold><c:header>| Island</c:header></bold>",
                "if:island_level| <c:bar>|</c:bar> <c:text>Level:</c:text> <island>",
                "if:objective| <c:bar>|</c:bar> <objective>",
                "if:island_level| <c:bar>|</c:bar> <progress:island_level_progress>",
                "if:island_next_note| <c:bar>|</c:bar> <c:text>Note:</c:text> <c:value><ph:island_next_note>",
                "if:board_timer| <c:bar>|</c:bar> <timer>",
                " <c:sky_ice>play.skybornmc.net"));
        m.put("scoreboard.island-visit.title", "<grad:phoenix><bold>PHOENIX SKYBLOCK</bold></grad>");
        m.put("scoreboard.spawn.title", "<grad:phoenix><bold>PHOENIX SKYBLOCK</bold></grad>");
        m.put("scoreboard.spawn.lines", String.join("\n",
                "<soft-blank>",
                " <bold><c:header>| You</c:header></bold>",
                " <c:bar>|</c:bar> <c:text>Money:</c:text> <money>",
                "if:crystals_owned| <c:bar>|</c:bar> <c:text>Crystals:</c:text> <crystals>",
                "if:credits_owned| <c:bar>|</c:bar> <c:text>Credits:</c:text> <credits>",
                " <bold><c:header>| Spawn</c:header></bold>",
                "if:objective| <c:bar>|</c:bar> <objective>",
                "if:afk_pool| <c:bar>|</c:bar><ph:afk_line>",
                "if:board_timer| <c:bar>|</c:bar> <timer>",
                " <c:sky_ice>play.skybornmc.net"));
        m.put("scoreboard.depths.title", "<grad:phoenix><bold>PHOENIX SKYBLOCK</bold></grad>");
        m.put("scoreboard.depths-forest.title", "<grad:phoenix><bold>PHOENIX SKYBLOCK</bold></grad>");
        m.put("scoreboard.depths-combat.title", "<grad:phoenix><bold>PHOENIX SKYBLOCK</bold></grad>");
        m.put("scoreboard.depths-gate.title", "<grad:phoenix><bold>PHOENIX SKYBLOCK</bold></grad>");
        m.put("scoreboard.warzone.title", "<grad:phoenix><bold>PHOENIX SKYBLOCK</bold></grad>");
        return Map.copyOf(m);
    }
}
