package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Proc feedback (ENCHANTS §11): one action bar line and a short sound, at most once per
 * {@code gathering.proc-message-interval-ms} per player, so fast mining never spams. No titles. Main thread.
 */
final class ProcBar {

    private final EnchantsModule module;
    private final Map<UUID, Long> last = new HashMap<>();

    ProcBar(EnchantsModule module) {
        this.module = module;
    }

    void show(Player player, String key, String sound, TagResolver... tags) {
        long now = System.currentTimeMillis();
        Long before = last.get(player.getUniqueId());
        if (before != null && now - before < module.config().gatherMessageIntervalMs) {
            return;
        }
        last.put(player.getUniqueId(), now);
        module.msg().actionBar(player, key, tags);
        if (sound != null && !sound.isBlank()) {
            module.core.sounds().play(player, sound);
        }
    }

    void forget(UUID player) {
        last.remove(player);
    }
}
