package net.skybornmc.skyblock.common.npc;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.key.Key;
import org.bukkit.profile.PlayerTextures;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The skin of a mannequin NPC (Kindle, Rowan and the spawn NPCs; owner 4 Nov 2026: real skins, not Steve), from one
 * config text. Pure parsing ({@link #parse}, unit-tested) plus {@link #profile} for the entity.
 * <ul>
 *   <li>a vanilla default skin: {@code alex}, {@code steve}, {@code ari}, {@code efe}, {@code kai}, {@code makena},
 *       {@code noor}, {@code sunny}, {@code zuri} (optionally {@code wide:kai} / {@code slim:kai} for the arm model);
 *       no lookup, works offline;</li>
 *   <li>a Mojang texture: {@code texture:<value>} or {@code texture:<value>:<signature>} (from a skin site);</li>
 *   <li>a player's skin: {@code player:<name>} or just a name that is not a default skin (looked up by the client).</li>
 * </ul>
 * Empty = the plain default look.
 */
public record NpcSkin(Kind kind, String value, String signature, boolean slim) {

    public enum Kind { DEFAULT, TEXTURE, PLAYER }

    /** The vanilla default skins and their usual arm model (true = slim). */
    public static final Map<String, Boolean> DEFAULTS = Map.of(
            "steve", false, "alex", true, "ari", false, "efe", true, "kai", false,
            "makena", true, "noor", true, "sunny", false, "zuri", false);

    /** Reads a skin text; empty for a blank or broken one (a player name must be 1-16 of {@code [A-Za-z0-9_]}). */
    public static Optional<NpcSkin> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String text = raw.strip();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("texture:")) {
            String rest = text.substring("texture:".length()).strip();
            int colon = rest.indexOf(':');
            String value = colon < 0 ? rest : rest.substring(0, colon).strip();
            String signature = colon < 0 ? null : rest.substring(colon + 1).strip();
            if (value.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new NpcSkin(Kind.TEXTURE, value, signature == null || signature.isEmpty() ? null : signature, false));
        }
        if (lower.startsWith("player:")) {
            return player(text.substring("player:".length()).strip());
        }
        Boolean forced = null;
        String name = lower;
        if (lower.startsWith("slim:") || lower.startsWith("wide:")) {
            forced = lower.startsWith("slim:");
            name = lower.substring(5).strip();
        } else if (lower.startsWith("default:")) {
            name = lower.substring("default:".length()).strip();
        }
        Boolean slim = DEFAULTS.get(name);
        if (slim != null) {
            return Optional.of(new NpcSkin(Kind.DEFAULT, name, null, forced != null ? forced : slim));
        }
        return forced == null && !lower.startsWith("default:") ? player(text) : Optional.empty();
    }

    private static Optional<NpcSkin> player(String name) {
        return name.matches("[A-Za-z0-9_]{1,16}") ? Optional.of(new NpcSkin(Kind.PLAYER, name, null, false)) : Optional.empty();
    }

    /** The texture key of a default skin: {@code minecraft:entity/player/slim/alex}. */
    public Key textureKey() {
        return Key.key(Key.MINECRAFT_NAMESPACE, "entity/player/" + (slim ? "slim" : "wide") + "/" + value);
    }

    /** The profile for {@code Mannequin#setProfile}. */
    public ResolvableProfile profile() {
        return switch (kind) {
            case DEFAULT -> ResolvableProfile.resolvableProfile()
                    .skinPatch(patch -> patch.body(textureKey()).model(slim ? PlayerTextures.SkinModel.SLIM : PlayerTextures.SkinModel.CLASSIC))
                    .build();
            case TEXTURE -> ResolvableProfile.resolvableProfile()
                    .addProperty(new ProfileProperty("textures", value, signature))
                    .build();
            case PLAYER -> ResolvableProfile.resolvableProfile().name(value).build();
        };
    }

    /** The profile of a skin text, if it is a valid one. */
    public static Optional<ResolvableProfile> profileOf(String raw) {
        return parse(raw).map(NpcSkin::profile);
    }
}
