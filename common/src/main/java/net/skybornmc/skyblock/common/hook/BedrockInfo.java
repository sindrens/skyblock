package net.skybornmc.skyblock.common.hook;

import net.skybornmc.skyblock.common.text.BedrockCheck;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Floodgate wrapper (softdepend, via reflection so no compile dependency): is-Bedrock (reuses {@link BedrockCheck}),
 * the Bedrock username prefix (usually {@code .}) and the player's device (e.g. {@code ANDROID}, {@code IOS},
 * {@code WINDOWS_10}, {@code XBOX}) for staff tools like {@code /whois}.
 */
public final class BedrockInfo {

    private final BedrockCheck check;
    private final Object api;
    private final Method getPlayer;
    private final String prefix;

    private BedrockInfo(BedrockCheck check, Object api, Method getPlayer, String prefix) {
        this.check = check;
        this.api = api;
        this.getPlayer = getPlayer;
        this.prefix = prefix;
    }

    public static BedrockInfo create(Plugin plugin, BedrockCheck check) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("floodgate")) {
            return new BedrockInfo(check, null, null, ".");
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Method getPlayer = apiClass.getMethod("getPlayer", UUID.class);
            String prefix = String.valueOf(apiClass.getMethod("getPlayerPrefix").invoke(api));
            return new BedrockInfo(check, api, getPlayer, prefix);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Floodgate API not usable for device info: " + e);
            return new BedrockInfo(check, null, null, ".");
        }
    }

    public boolean isBedrock(UUID uuid) {
        return check.isBedrock(uuid);
    }

    public BedrockCheck check() {
        return check;
    }

    /** Username prefix Floodgate gives Bedrock players (default {@code .}). */
    public String usernamePrefix() {
        return prefix;
    }

    /** Device OS name of an online Bedrock player, if known. */
    public Optional<String> device(UUID uuid) {
        if (api == null) {
            return Optional.empty();
        }
        try {
            Object player = getPlayer.invoke(api, uuid);
            if (player == null) {
                return Optional.empty();
            }
            Object os = player.getClass().getMethod("getDeviceOs").invoke(player);
            return Optional.ofNullable(os).map(String::valueOf);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
