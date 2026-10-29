package net.skybornmc.skyblock.cosmetics;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/** Settings of titles &amp; tags ({@code cosmetics.yml}). The titles and tags themselves are in tags-titles.yml. */
public final class CosmeticsConfig extends ModuleConfig {

    @Setting(path = "auto-title", desc = "Title every player gets on their first join (id from tags-titles.yml). Empty = none.")
    public String autoTitle = "newcomer";

    @Setting(path = "tag-item.max-amount", desc = "Most tag items one /tags item command can create.", min = 1, max = 64)
    public int tagItemMaxAmount = 16;

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
