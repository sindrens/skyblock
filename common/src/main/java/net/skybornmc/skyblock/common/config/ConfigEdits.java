package net.skybornmc.skyblock.common.config;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permissible;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One in-game config change, done the same way everywhere ({@code /admin}, the display editor): validate through
 * {@link ConfigManager#set}, save the YAML, write the audit log ({@code config/edit} with old and new value) and
 * reload the owning module so the change applies live (unless the setting needs a restart).
 */
public final class ConfigEdits {

    /** Permission for settings marked {@link Setting.Level#ADMIN}. */
    public static final String PERM_ADMIN = "skyblock.admin.config";
    /** Permission for settings marked {@link Setting.Level#OWNER} (economy, season…). */
    public static final String PERM_OWNER = "skyblock.owner.config";

    /** Files never shown in editors (secrets live there). */
    public static final Set<String> HIDDEN_FILES = Set.of("database.yml");

    /** Config files reloaded by "core" instead of a module of the same name. */
    private static final Set<String> CORE_FILES = Set.of("core.yml", "items.yml", "audit.yml", "menus.yml", "commands.yml");

    /** Outcome: {@code error} is a player-readable reason when not ok. */
    public record Result(boolean ok, String error, String oldValue, String newValue, boolean restart) {
    }

    private ConfigEdits() {
    }

    /** Whether {@code who} may edit a setting of this level. */
    public static boolean canEdit(Permissible who, Setting.Level level) {
        return level == Setting.Level.OWNER ? who.hasPermission(PERM_OWNER) : who.hasPermission(PERM_ADMIN) || who.hasPermission(PERM_OWNER);
    }

    /** The current info of one setting. */
    public static Optional<SettingInfo> info(Core core, ModuleConfig config, String path) {
        return core.configs().describe(config).stream().filter(i -> i.path().equals(path)).findFirst();
    }

    /**
     * Applies {@code rawValue} (text as typed, or a typed value like Boolean/Long/List) to {@code path}.
     * Checks the editor's permission level first; never throws for bad input.
     */
    public static Result apply(Core core, CommandSender editor, ModuleConfig config, String path, Object rawValue) {
        Optional<SettingInfo> before = info(core, config, path);
        if (before.isEmpty()) {
            return new Result(false, "unknown setting " + path, null, null, false);
        }
        SettingInfo info = before.get();
        if (!canEdit(editor, info.level())) {
            return new Result(false, "only the owner can change this setting", null, null, false);
        }
        String old = ConfigManager.display(info.value());
        try {
            core.configs().set(config, path, rawValue);
        } catch (IllegalArgumentException e) {
            return new Result(false, e.getMessage(), old, null, false);
        }
        String now = ConfigManager.display(info(core, config, path).map(SettingInfo::value).orElse(null));
        try {
            core.configs().save(config);
        } catch (IOException e) {
            return new Result(false, "could not save " + config.fileName() + ": " + e.getMessage(), old, now, false);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("file", config.fileName());
        details.put("path", path);
        details.put("old", old);
        details.put("new", now);
        core.audit().log(AuditCategory.CONFIG, "edit", Actor.of(editor), config.fileName() + ":" + path, null, null,
                editor instanceof org.bukkit.entity.Player p ? p.getLocation() : null, details);
        if (!info.restart()) {
            reload(config.fileName());
        }
        return new Result(true, null, old, now, info.restart());
    }

    /** Reloads whatever owns {@code fileName} through the normal reload command (console). */
    public static void reload(String fileName) {
        if (HIDDEN_FILES.contains(fileName)) {
            return;
        }
        String target = CORE_FILES.contains(fileName) ? "core"
                : fileName.equals("navigation.yml") ? "menus"
                : fileName.replace(".yml", "");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "skyblock reload " + target);
    }
}
