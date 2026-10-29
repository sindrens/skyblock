package net.skybornmc.skyblock.common.module;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModulePermissionsTest {

    private static final String FILE = """
            # comment
            skyblock.player.spawn:
              description: Teleport to spawn (/spawn)
              default: true
            skyblock.staff.tp:
              description: Teleport to players
            skyblock.chat.color:
              description: Colors
              default: false
            skyblock.staff.logs.category.*:
              description: Every category
              default: op
              children:
                skyblock.staff.logs.category.chat: true
            """;

    /** Minimal PluginManager: only the permission methods ModulePermissions uses. */
    private final Map<String, Permission> perms = new HashMap<>();
    private final PluginManager pluginManager = (PluginManager) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{PluginManager.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getPermission" -> perms.get((String) args[0]);
                case "addPermission" -> {
                    Permission p = (Permission) args[0];
                    if (perms.putIfAbsent(p.getName(), p) != null) {
                        throw new IllegalArgumentException("already defined");
                    }
                    yield null;
                }
                case "removePermission" -> {
                    perms.remove(args[0] instanceof Permission p ? p.getName() : (String) args[0]);
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });

    private ModulePermissions withFiles(Map<String, String> files) {
        return new ModulePermissions(pluginManager, path -> files.containsKey(path)
                ? new ByteArrayInputStream(files.get(path).getBytes(StandardCharsets.UTF_8)) : null, Logger.getLogger("test"));
    }

    @Test
    void parsesDottedNodesDefaultsAndChildren() throws Exception {
        List<Permission> list = ModulePermissions.parse("test", new StringReader(FILE));
        assertEquals(4, list.size());
        assertEquals("skyblock.player.spawn", list.get(0).getName());
        assertEquals(PermissionDefault.TRUE, list.get(0).getDefault());
        assertEquals("Teleport to spawn (/spawn)", list.get(0).getDescription());
        assertEquals(PermissionDefault.OP, list.get(1).getDefault(), "missing default = op, like plugin.yml");
        assertEquals(PermissionDefault.FALSE, list.get(2).getDefault());
        assertEquals(Map.of("skyblock.staff.logs.category.chat", true), list.get(3).getChildren());
    }

    @Test
    void rejectsInvalidDefaultsAndEntries() {
        assertThrows(IllegalArgumentException.class, () -> ModulePermissions.parse("t",
                new StringReader("skyblock.a:\n  default: sometimes\n")));
        assertThrows(IllegalArgumentException.class, () -> ModulePermissions.parse("t",
                new StringReader("skyblock.a: true\n")));
        assertThrows(IllegalArgumentException.class, () -> ModulePermissions.parse("t",
                new StringReader("Skyblock A:\n  description: x\n")));
    }

    @Test
    void registersAndUnregistersPerModule() {
        ModulePermissions mp = withFiles(Map.of("permissions/essentials.yml", FILE));
        assertEquals(4, mp.register("essentials"));
        assertNotNull(perms.get("skyblock.player.spawn"));
        assertEquals(4, mp.registered("essentials").size());

        assertEquals(4, mp.register("essentials"), "registering again replaces instead of failing");
        mp.unregister("essentials");
        assertTrue(perms.isEmpty());
        mp.unregister("essentials");
    }

    @Test
    void missingFileIsFineAndBrokenFileIsSkipped() {
        ModulePermissions mp = withFiles(Map.of("permissions/broken.yml", "skyblock.a:\n  default: maybe\n"));
        assertEquals(0, mp.register("motd"));
        assertEquals(0, mp.register("broken"));
        assertTrue(perms.isEmpty());
    }

    @Test
    void neverOverridesOrRemovesSomeoneElsesNode() {
        Permission core = new Permission("skyblock.staff.tp", "core", PermissionDefault.FALSE);
        perms.put(core.getName(), core);
        ModulePermissions mp = withFiles(Map.of("permissions/essentials.yml", FILE));
        assertEquals(3, mp.register("essentials"));
        assertEquals(PermissionDefault.FALSE, perms.get("skyblock.staff.tp").getDefault());
        mp.unregister("essentials");
        assertEquals(Set.of("skyblock.staff.tp"), perms.keySet());
        assertNull(perms.get("skyblock.player.spawn"));
    }
}
