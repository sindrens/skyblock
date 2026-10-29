package net.skybornmc.skyblock.cosmetics;

import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.item.Rarity;

import java.util.Objects;

/**
 * One title or tag from {@code tags-titles.yml}.
 *
 * @param display          how it looks: MiniMessage with style tags, or {@code &} codes
 * @param source           how to get it, shown on locked entries ("Defeat the first boss")
 * @param secret           titles only: shown as "???" until earned
 * @param seasonPersistent kept across season resets (owner default: yes for all)
 */
public record Cosmetic(Cosmetics.Kind kind, String id, String display, Rarity rarity, String source, boolean secret,
                       boolean seasonPersistent) {

    public Cosmetic {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(rarity, "rarity");
        if (!validId(id)) {
            throw new IllegalArgumentException("Invalid id '" + id + "' (use a-z, 0-9, _)");
        }
        display = display == null ? id : display;
        source = source == null ? "" : source;
    }

    public static boolean validId(String id) {
        return id != null && id.matches("[a-z0-9_]{1,64}");
    }

    public Cosmetic withDisplay(String value) {
        return new Cosmetic(kind, id, value, rarity, source, secret, seasonPersistent);
    }

    public Cosmetic withRarity(Rarity value) {
        return new Cosmetic(kind, id, display, value, source, secret, seasonPersistent);
    }

    public Cosmetic withSource(String value) {
        return new Cosmetic(kind, id, display, rarity, value, secret, seasonPersistent);
    }

    public Cosmetic withSecret(boolean value) {
        return new Cosmetic(kind, id, display, rarity, source, value, seasonPersistent);
    }

    public Cosmetic withSeasonPersistent(boolean value) {
        return new Cosmetic(kind, id, display, rarity, source, secret, value);
    }
}
