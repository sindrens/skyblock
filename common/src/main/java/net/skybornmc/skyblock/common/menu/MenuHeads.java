package net.skybornmc.skyblock.common.menu;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Textured heads for navigation buttons. A source is either a player name (e.g. {@code MHF_ArrowLeft}, Mojang's official
 * arrow heads, looked up once asynchronously) or a base64 texture value (minecraft-heads.com "Value"). Until a head is
 * ready, or if the lookup fails, the fallback item is used, so menus never wait for the network.
 */
final class MenuHeads {

    private final Logger log;
    private final Map<String, PlayerProfile> ready = new ConcurrentHashMap<>();

    MenuHeads(Logger log) {
        this.log = log;
    }

    /** Loads (or reloads) one head under {@code key}. */
    void load(String key, String source) {
        ready.remove(key);
        if (source == null || source.isBlank()) {
            return;
        }
        String value = source.trim();
        if (looksLikeTexture(value)) {
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)), "menu_head");
            profile.setProperty(new ProfileProperty("textures", value));
            ready.put(key, profile);
            return;
        }
        if (!isPlayerName(value)) {
            log.warning("menus.yml → heads." + key + ": '" + value + "' is neither a player name nor a texture value; using the arrow item.");
            return;
        }
        Bukkit.createProfile(value).update().whenComplete((profile, error) -> {
            if (error != null || !(profile instanceof PlayerProfile paper) || !paper.hasTextures()) {
                log.warning("menus.yml → heads." + key + ": could not load the head of '" + value + "'; using the arrow item.");
                return;
            }
            ready.put(key, paper);
        });
    }

    /** Base64 texture values are long and only use base64 characters. */
    static boolean looksLikeTexture(String value) {
        return value.length() > 60 && value.matches("[A-Za-z0-9+/=]+");
    }

    static boolean isPlayerName(String value) {
        return value.matches("[A-Za-z0-9_]{3,16}");
    }

    /** A head for {@code key}, or {@code fallback} if it isn't ready. */
    ItemStack item(String key, Material fallback) {
        PlayerProfile profile = ready.get(key);
        if (profile == null) {
            return new ItemStack(fallback);
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(profile));
        return head;
    }
}
