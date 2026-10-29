package net.skybornmc.skyblock.common.text;

import org.bukkit.Bukkit;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;

/**
 * Tells whether a player joined through Geyser/Floodgate (Bedrock). Uses Floodgate through reflection so the plugin
 * runs without it (softdepend). Bedrock players get the legacy color fallbacks from {@code style.yml}.
 */
public interface BedrockCheck {

    boolean isBedrock(UUID uuid);

    /** Never Bedrock (tests, servers without Floodgate). */
    BedrockCheck NONE = uuid -> false;

    /** Floodgate when it is installed and enabled, otherwise {@link #NONE}. */
    static BedrockCheck floodgate() {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            return NONE;
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            MethodHandle isFloodgate = MethodHandles.publicLookup()
                    .findVirtual(apiClass, "isFloodgatePlayer", MethodType.methodType(boolean.class, UUID.class))
                    .bindTo(api);
            return uuid -> {
                try {
                    return (boolean) isFloodgate.invoke(uuid);
                } catch (Throwable t) {
                    return false;
                }
            };
        } catch (ReflectiveOperationException | RuntimeException e) {
            Bukkit.getLogger().warning("[PhoenixSkyblock] Floodgate found but its API could not be used: " + e.getMessage());
            return NONE;
        }
    }
}
