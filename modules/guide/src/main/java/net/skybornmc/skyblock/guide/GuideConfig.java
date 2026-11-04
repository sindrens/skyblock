package net.skybornmc.skyblock.guide;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/** Settings of the island guide Kindle ({@code guide.yml}); texts in messages/guide.yml. */
public final class GuideConfig extends ModuleConfig {

    @Setting(path = "enabled", desc = "One guide NPC per island (owner 2 Nov 2026). Off = no NPC; /is guide still opens his menu.")
    public boolean enabled = true;

    @Setting(path = "skin", desc = "Player name whose skin the guide wears (empty = the default look).")
    public String skin = "";

    @Setting(path = "held-item", desc = "Item the guide holds (empty = nothing).")
    public String heldItem = "blaze_rod";

    @Setting(path = "offset.x", desc = "Default spot: blocks east of the island home.", min = -16, max = 16)
    public double offsetX = 2;

    @Setting(path = "offset.y", desc = "Default spot: blocks above the island home.", min = -8, max = 8)
    public double offsetY = 0;

    @Setting(path = "offset.z", desc = "Default spot: blocks south of the island home.", min = -16, max = 16)
    public double offsetZ = 1;

    @Setting(path = "label-height", desc = "Height of the name and status lines above his feet.", min = 1.5, max = 4)
    public double labelHeight = 2.3;

    @Setting(path = "view-range", desc = "How far the name line is seen (display view range factor).", min = 0.1, max = 4)
    public double viewRange = 0.5;

    @Setting(path = "check-seconds", desc = "How often guides near players are spawned (players standing on an island).", min = 2, max = 120)
    public int checkSeconds = 5;

    @Setting(path = "look-at-players", desc = "The guide turns to the nearest player within 6 blocks.")
    public boolean lookAtPlayers = true;

    @Setting(path = "announce-level-ups", desc = "On an island level-up, online members get \"Kindle has something new\" and his status line shows a star.")
    public boolean announceLevelUps = true;

    @Setting(path = "errands.hours", desc = "Kindle's errands and finds change this often (owner: every 3 hours, never shown as a timer).", min = 1, max = 48)
    public int errandHours = 3;

    @Setting(path = "errands.count", desc = "Errands each player gets per rotation.", min = 0, max = 7)
    public int errandCount = 2;

    @Setting(path = "trades.count", desc = "Kindle's finds (item trades) each player gets per rotation.", min = 0, max = 7)
    public int tradeCount = 3;

    @Setting(path = "talk.radius", desc = "Kindle says a line when a member comes this close (once per rotation per player).", min = 0, max = 16)
    public double talkRadius = 5;

    @Setting(path = "menu.progress-length", desc = "Dots of the island level progress bar in Kindle's menu.", min = 5, max = 20)
    public int progressLength = 10;

    @Setting(path = "menu.next-none-command", desc = "What Kindle's Next step button runs when there is no next step "
            + "(without /; empty = only a chat line).")
    public String nextNoneCommand = "menu";

    @Override
    public String fileName() {
        return "guide.yml";
    }

    @Override
    public String header() {
        return "Island guide Kindle (docs/planning/ISLAND_GUIDE.md). Texts: messages/guide.yml. Reload: /skyblock reload guide";
    }
}
