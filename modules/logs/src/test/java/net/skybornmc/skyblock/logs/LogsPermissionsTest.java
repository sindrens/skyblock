package net.skybornmc.skyblock.logs;

import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.module.ModulePermissions;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every audit category has its own declared skyblock.staff.logs.category.<key> node, and the wildcard covers them all. */
class LogsPermissionsTest {

    private static Map<String, Permission> nodes() throws Exception {
        Path file = Path.of("src/main/resources/permissions/logs.yml");
        try (Reader reader = Files.newBufferedReader(file)) {
            List<Permission> list = ModulePermissions.parse(file.toString(), reader);
            return list.stream().collect(Collectors.toMap(Permission::getName, Function.identity()));
        }
    }

    @Test
    void everyCategoryIsDeclaredAsStaffOnly() throws Exception {
        Map<String, Permission> nodes = nodes();
        Permission all = nodes.get(LogsModule.PERM_CATEGORY + "*");
        assertNotNull(all, "wildcard node");
        for (AuditCategory category : AuditCategory.values()) {
            String node = LogsModule.PERM_CATEGORY + category.key();
            Permission permission = nodes.get(node);
            assertNotNull(permission, "declare " + node + " in permissions/logs.yml");
            assertEquals(PermissionDefault.OP, permission.getDefault(), node);
            assertEquals(Boolean.TRUE, all.getChildren().get(node), "wildcard child " + node);
        }
        assertEquals(AuditCategory.values().length, all.getChildren().size(), "wildcard has only real categories");
        assertTrue(nodes.keySet().stream().filter(n -> n.startsWith(LogsModule.PERM_CATEGORY) && !n.endsWith("*"))
                .allMatch(n -> AuditCategory.byKey(n.substring(LogsModule.PERM_CATEGORY.length())).isPresent()), "no stale nodes");
    }
}
