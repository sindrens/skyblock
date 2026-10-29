package net.skybornmc.skyblock.common.module;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Per-module permission files (F2): every module declares its own permissions in
 * {@code modules/<id>/src/main/resources/permissions/<id>.yml} instead of the shared {@code plugin.yml}.
 *
 * <p>The plugin registers a module's file with Bukkit right before the module enables and removes it again
 * after the module disables (or fails to enable), so modules need no code for this. The file format is the
 * {@code permissions:} section of a {@code plugin.yml}, without the {@code permissions:} key:</p>
 * <pre>
 * skyblock.player.spawn:
 *   description: Teleport to spawn (/spawn)
 *   default: true        # true | false | op | not op; missing = op (same as plugin.yml)
 *   children:            # optional
 *     skyblock.player.tpa: true
 * </pre>
 */
public final class ModulePermissions {

    private static final Pattern NODE = Pattern.compile("[a-z0-9_\\-]+(\\.[a-z0-9_\\-*]+)*");

    private final PluginManager pluginManager;
    private final Function<String, InputStream> resources;
    private final Logger logger;
    private final Map<String, List<String>> registered = new HashMap<>();

    /**
     * @param resources opens a resource of the plugin jar by path (e.g. {@code plugin::getResource}); returns null if missing
     */
    public ModulePermissions(PluginManager pluginManager, Function<String, InputStream> resources, Logger logger) {
        this.pluginManager = Objects.requireNonNull(pluginManager, "pluginManager");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Jar path of a module's permission file. */
    public static String resourcePath(String moduleId) {
        return "permissions/" + moduleId + ".yml";
    }

    /**
     * Registers the permissions of one module. A missing file is fine (module without permissions); a broken file
     * is logged and skipped so the module still enables. Nodes that are already registered (plugin.yml or another
     * module) are left alone and logged: every node must have exactly one owner.
     *
     * @return the number of permissions this call registered
     */
    public synchronized int register(String moduleId) {
        unregister(moduleId);
        String path = resourcePath(moduleId);
        List<Permission> permissions;
        try (InputStream in = resources.apply(path)) {
            if (in == null) {
                return 0;
            }
            permissions = parse(path, new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException e) {
            logger.log(Level.SEVERE, "Permission file " + path + " is invalid; module '" + moduleId
                    + "' runs without its permission defaults.", e);
            return 0;
        }
        List<String> added = new ArrayList<>();
        for (Permission permission : permissions) {
            if (pluginManager.getPermission(permission.getName()) != null) {
                logger.warning("Permission " + permission.getName() + " in " + path
                        + " is already registered elsewhere (plugin.yml or another module); kept the existing one.");
                continue;
            }
            try {
                pluginManager.addPermission(permission);
                added.add(permission.getName());
            } catch (IllegalArgumentException e) {
                logger.warning("Permission " + permission.getName() + " in " + path + " could not be registered: "
                        + e.getMessage());
            }
        }
        registered.put(moduleId, added);
        return added.size();
    }

    /** Removes every permission {@link #register} added for this module. Safe to call more than once. */
    public synchronized void unregister(String moduleId) {
        List<String> names = registered.remove(moduleId);
        if (names == null) {
            return;
        }
        for (String name : names) {
            try {
                pluginManager.removePermission(name);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not remove permission " + name + " of module '" + moduleId + "'.", e);
            }
        }
    }

    /** Nodes currently registered for a module (empty if none). */
    public synchronized Set<String> registered(String moduleId) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(registered.getOrDefault(moduleId, List.of())));
    }

    /**
     * Parses a permission file.
     *
     * @param source name used in error messages
     * @throws IllegalArgumentException on an invalid node name, entry or default
     */
    public static List<Permission> parse(String source, Reader reader) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        // Node names contain dots; never split them into nested sections.
        yaml.options().pathSeparator('\u0000');
        yaml.load(reader);
        List<Permission> out = new ArrayList<>();
        for (String node : yaml.getKeys(false)) {
            if (!NODE.matcher(node).matches()) {
                throw new IllegalArgumentException(source + ": invalid permission node '" + node + "'");
            }
            ConfigurationSection entry = yaml.getConfigurationSection(node);
            if (entry == null) {
                throw new IllegalArgumentException(source + ": '" + node + "' needs a description (and optional default)");
            }
            String description = entry.getString("description", "");
            String defaultName = entry.getString("default");
            PermissionDefault def = PermissionDefault.OP;
            if (defaultName != null) {
                def = PermissionDefault.getByName(defaultName);
                if (def == null) {
                    throw new IllegalArgumentException(source + ": '" + node + "' has an invalid default '" + defaultName
                            + "' (use true, false, op or not op)");
                }
            }
            Map<String, Boolean> children = new LinkedHashMap<>();
            ConfigurationSection childSection = entry.getConfigurationSection("children");
            if (childSection != null) {
                for (String child : childSection.getKeys(false)) {
                    if (!NODE.matcher(child).matches() || !childSection.isBoolean(child)) {
                        throw new IllegalArgumentException(source + ": invalid child '" + child + "' of '" + node + "'");
                    }
                    children.put(child, childSection.getBoolean(child));
                }
            }
            out.add(new Permission(node, description, def, children));
        }
        return out;
    }
}
