package net.skybornmc.skyblock;

import net.skybornmc.skyblock.common.module.ModulePermissions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F2 guard rails for the per-module permission files: every node has exactly one owner (plugin.yml or one module),
 * files are named after their module, and every skyblock.* node granted in server/permissions/*.txt is declared.
 */
class PermissionFilesTest {

    /** Nodes checked with a dynamic suffix in code (e.g. one node per audit category); not declared one by one. */
    private static final List<String> DYNAMIC_PREFIXES = List.of("skyblock.staff.logs.category.");

    private static Path root() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("settings.gradle.kts"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("Project root not found");
        }
        return dir;
    }

    /** node -> owner ("plugin.yml" or module id). */
    private static Map<String, String> declaredNodes(List<String> duplicates) throws Exception {
        Map<String, String> owners = new HashMap<>();
        YamlConfiguration pluginYml = new YamlConfiguration();
        pluginYml.options().pathSeparator('\u0000');
        pluginYml.load(root().resolve("plugin/src/main/resources/plugin.yml").toFile());
        ConfigurationSection section = pluginYml.getConfigurationSection("permissions");
        if (section != null) {
            for (String node : section.getKeys(false)) {
                owners.put(node, "plugin.yml");
            }
        }
        try (Stream<Path> modules = Files.list(root().resolve("modules"))) {
            for (Path module : modules.filter(Files::isDirectory).sorted().toList()) {
                Path file = module.resolve("src/main/resources/permissions/" + module.getFileName() + ".yml");
                if (!Files.exists(file)) {
                    continue;
                }
                try (Reader reader = Files.newBufferedReader(file)) {
                    for (Permission p : ModulePermissions.parse(file.toString(), reader)) {
                        String previous = owners.put(p.getName(), module.getFileName().toString());
                        if (previous != null) {
                            duplicates.add(p.getName() + " (" + previous + " and " + module.getFileName() + ")");
                        }
                    }
                }
            }
        }
        return owners;
    }

    @Test
    void everyNodeHasExactlyOneOwner() throws Exception {
        List<String> duplicates = new ArrayList<>();
        Map<String, String> owners = declaredNodes(duplicates);
        assertTrue(duplicates.isEmpty(), "Declared twice: " + duplicates);
        assertTrue(owners.size() > 100, "permission files not found? " + owners.size());
    }

    @Test
    void permissionFilesAreNamedAfterTheirModule() throws IOException {
        List<String> wrong = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root().resolve("modules"))) {
            files.filter(f -> f.getParent().getFileName().toString().equals("permissions")
                            && f.toString().contains("src/main/resources/permissions"))
                    .forEach(f -> {
                        String module = root().resolve("modules").relativize(f).getName(0).toString();
                        if (!f.getFileName().toString().equals(module + ".yml")) {
                            wrong.add(f.toString());
                        }
                    });
        }
        assertTrue(wrong.isEmpty(), "Permission files must be permissions/<module>.yml: " + wrong);
    }

    @Test
    void grantFilesBelongToModulesAndOnlyGrantDeclaredNodes() throws Exception {
        Map<String, String> owners = declaredNodes(new ArrayList<>());
        Path dir = root().resolve("server/permissions");
        assertTrue(Files.exists(dir.resolve("base.txt")), "server/permissions/base.txt is applied first");
        assertFalse(Files.exists(dir.resolve("luckperms.txt")), "luckperms.txt was split into base.txt + module files");
        Set<String> undeclared = new TreeSet<>();
        List<String> orphans = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".txt")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".txt", "");
                if (!name.equals("base") && !Files.isDirectory(root().resolve("modules").resolve(name))) {
                    orphans.add(file.getFileName().toString());
                }
                for (String line : Files.readAllLines(file)) {
                    String[] parts = line.trim().split("\\s+");
                    // lp group <group> permission set <node> <value>
                    if (parts.length >= 6 && parts[0].equals("lp") && parts[3].equals("permission") && parts[4].equals("set")) {
                        String node = parts[5];
                        boolean ok = !node.startsWith("skyblock.") || node.endsWith("*") || owners.containsKey(node)
                                || DYNAMIC_PREFIXES.stream().anyMatch(node::startsWith);
                        if (!ok) {
                            undeclared.add(node + " in " + file.getFileName());
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), orphans, "server/permissions/<name>.txt must match a module (or be base.txt)");
        assertEquals(Set.of(), undeclared, "Granted but not declared in plugin.yml or a permissions/<module>.yml");
    }
}
