package net.skybornmc.skyblock.common.config;

/** Describes one setting for tools like the in-game /admin editor. */
public record SettingInfo(
        String path,
        String description,
        Class<?> type,
        Object value,
        Object defaultValue,
        double min,
        double max,
        boolean restart,
        Setting.Level level
) {
}
