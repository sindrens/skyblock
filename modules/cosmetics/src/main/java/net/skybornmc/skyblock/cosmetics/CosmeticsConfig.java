package net.skybornmc.skyblock.cosmetics;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/** Settings of titles &amp; tags ({@code cosmetics.yml}). The titles and tags themselves are in tags-titles.yml. */
public final class CosmeticsConfig extends ModuleConfig {

    @Setting(path = "points.playtime-minutes", desc = "Owner 7 Nov 2026: every this many online minutes give points.playtime-points cosmetic points.", min = 1, max = 1440)
    public int pointsPlaytimeMinutes = 60;

    @Setting(path = "points.playtime-points", desc = "Cosmetic points per points.playtime-minutes online (0 = none).", min = 0, max = 10000)
    public long pointsPlaytimePoints = 10;

    @Setting(path = "auto-title", desc = "Title every player gets on their first join (id from tags-titles.yml). Empty = none.")
    public String autoTitle = "newcomer";

    @Setting(path = "tag-item.max-amount", desc = "Most tag items one /tags item command can create.", min = 1, max = 64)
    public int tagItemMaxAmount = 16;

    @Setting(path = "tag.format", desc = "How an equipped tag looks in chat, tab and menus. <tag> = the tag's own display "
            + "(its colors). Default: the tag in bold, e.g. OWNER Pouched PHOENIX (owner 2 Nov 2026).")
    public String tagFormat = "<bold><tag></bold>";

    @Setting(path = "tag.strip-brackets", desc = "Remove [ ] around a tag's own display, so older tags written as [Name] "
            + "don't show as ([Name]).")
    public boolean tagStripBrackets = true;

    @Setting(path = "titles.hidden-unless-owned", desc = "Titles that can't be earned any more (their achievement is off): "
            + "players who have one keep it, everyone else doesn't see it in /titles (GUI pass, 7 Nov 2026).")
    public java.util.List<String> hiddenTitles = java.util.List.of("deep_diver", "tycoon", "phoenix_reborn");

    @Override
    public int version() {
        return 2;
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        return java.util.Map.of(2, java.util.Map.of("tag.format", ValueChange.of(
                "<c:muted>(</c:muted><tag><c:muted>)</c:muted>", "<bold><tag></bold>")));
    }

    @Override
    public String fileName() {
        return "cosmetics.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock titles & tags (/tags, /titles) and the display editor (/display).
                Titles and tags are defined in tags-titles.yml (or in-game: /tags -> Edit). Reload: /skyblock reload cosmetics""";
    }
}
