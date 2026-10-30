package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Permission resolution: config defaults per role, per-island overrides, leader always, visitors walk-and-look. */
class RolePermissionsTest {

    private static RolePermissions defaults() {
        IslandsConfig config = new IslandsConfig();
        RolePermissions.Result result = config.rolePermissions();
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        return result.permissions();
    }

    @Test
    void shippedDefaultsAreSane() {
        RolePermissions perms = defaults();
        for (IslandPerm perm : IslandPerm.values()) {
            assertTrue(perms.allows(null, PermRole.LEADER, perm), "leader: " + perm);
            assertTrue(perms.allows(null, PermRole.ADMIN, perm), "admin: " + perm);
            assertEquals(perm != IslandPerm.SETTINGS, perms.allows(null, PermRole.MOD, perm), "mod: " + perm);
            assertEquals(perm == IslandPerm.HURT_MONSTERS, perms.allows(null, PermRole.VISITOR, perm),
                    "visitors walk, look and fight monsters: " + perm);
        }
        assertTrue(perms.allows(null, PermRole.MEMBER, IslandPerm.BREAK));
        assertTrue(perms.allows(null, PermRole.MEMBER, IslandPerm.CONTAINERS));
        assertFalse(perms.allows(null, PermRole.MEMBER, IslandPerm.SPAWNERS));
        assertTrue(perms.allows(null, PermRole.COOP, IslandPerm.PLACE));
        assertFalse(perms.allows(null, PermRole.COOP, IslandPerm.FIRE));
        assertFalse(perms.allows(null, PermRole.COOP, IslandPerm.SPAWNERS));
    }

    @Test
    void overridesBeatDefaultsAndResetFollowsDefaultsAgain() {
        RolePermissions perms = defaults();
        IslandPerms island = new IslandPerms();
        assertTrue(island.isEmpty());

        island.set(PermRole.VISITOR, IslandPerm.INTERACT, true);
        island.set(PermRole.MEMBER, IslandPerm.CONTAINERS, false);
        assertTrue(perms.allows(island, PermRole.VISITOR, IslandPerm.INTERACT));
        assertFalse(perms.allows(island, PermRole.VISITOR, IslandPerm.CONTAINERS), "only the overridden one changes");
        assertFalse(perms.allows(island, PermRole.MEMBER, IslandPerm.CONTAINERS));
        assertTrue(perms.allows(island, PermRole.MEMBER, IslandPerm.BREAK));
        assertEquals(Boolean.TRUE, island.override(PermRole.VISITOR, IslandPerm.INTERACT));
        assertEquals(Boolean.FALSE, island.override(PermRole.MEMBER, IslandPerm.CONTAINERS));
        assertNull(island.override(PermRole.MEMBER, IslandPerm.BREAK));

        island.set(PermRole.MEMBER, IslandPerm.CONTAINERS, true); // flip deny -> allow
        assertTrue(perms.allows(island, PermRole.MEMBER, IslandPerm.CONTAINERS));
        island.set(PermRole.MEMBER, IslandPerm.CONTAINERS, null);
        island.set(PermRole.VISITOR, IslandPerm.INTERACT, null);
        assertTrue(island.isEmpty());
        assertFalse(perms.allows(island, PermRole.VISITOR, IslandPerm.INTERACT));
    }

    @Test
    void leaderCanNeverBeRestricted() {
        RolePermissions perms = defaults();
        IslandPerms island = new IslandPerms();
        island.set(PermRole.LEADER, IslandPerm.BREAK, false);
        assertTrue(island.isEmpty(), "leader overrides are ignored");
        assertTrue(perms.allows(island, PermRole.LEADER, IslandPerm.BREAK));
        RolePermissions none = RolePermissions.none();
        assertTrue(none.allows(null, PermRole.LEADER, IslandPerm.SPAWNERS));
        assertFalse(none.allows(null, PermRole.MOD, IslandPerm.BREAK));
    }

    @Test
    void parsingSkipsUnknownKeysAndAcceptsWildcardAndCase() {
        RolePermissions.Result result = RolePermissions.parse(List.of("*"), List.of("*"), List.of("BREAK", "armor_stands", "fly"),
                List.of(" place "), List.of("ender-pearls", ""), java.util.Map.of());
        assertEquals(1, result.problems().size());
        assertTrue(result.problems().getFirst().contains("fly"));
        RolePermissions perms = result.permissions();
        assertEquals(IslandPerm.wildcard(), perms.defaultMask(PermRole.MOD));
        assertFalse(perms.defaultAllows(PermRole.MOD, IslandPerm.SETTINGS), "* never includes permissions with own defaults");
        assertTrue(perms.defaultAllows(PermRole.MEMBER, IslandPerm.BREAK));
        assertTrue(perms.defaultAllows(PermRole.MEMBER, IslandPerm.ARMOR_STANDS));
        assertFalse(perms.defaultAllows(PermRole.MEMBER, IslandPerm.PLACE));
        assertTrue(perms.defaultAllows(PermRole.COOP, IslandPerm.PLACE));
        assertTrue(perms.defaultAllows(PermRole.VISITOR, IslandPerm.ENDER_PEARLS));
        assertEquals(IslandPerm.ENDER_PEARLS.bit(), perms.defaultMask(PermRole.VISITOR));
    }

    @Test
    void rolesMapToPermissionRoles() {
        assertEquals(PermRole.VISITOR, PermRole.of(null));
        assertEquals(PermRole.LEADER, PermRole.of(Role.LEADER));
        assertEquals(PermRole.MOD, PermRole.of(Role.MOD));
        assertEquals(PermRole.ADMIN, PermRole.of(Role.ADMIN));
        assertEquals(PermRole.ADMIN, PermRole.byKey("admin").orElseThrow());
        assertEquals(PermRole.MEMBER, PermRole.of(Role.MEMBER));
        assertEquals(PermRole.COOP, PermRole.of(Role.COOP));
        assertEquals(PermRole.VISITOR, PermRole.byKey("Visitor").orElseThrow());
        assertTrue(PermRole.byKey("owner").isEmpty());
        assertFalse(PermRole.LEADER.editable());
    }

    @Test
    void permissionKeysAreUniqueAndStable() {
        assertEquals(IslandPerm.values().length, java.util.Arrays.stream(IslandPerm.values()).map(IslandPerm::key).distinct().count());
        assertTrue(IslandPerm.values().length < 64, "masks are longs");
        for (IslandPerm perm : IslandPerm.values()) {
            assertEquals(perm, IslandPerm.byKey(perm.key()).orElseThrow());
        }
        assertEquals("armor-stands", IslandPerm.ARMOR_STANDS.key());
        assertEquals("ender-pearls", IslandPerm.ENDER_PEARLS.key());
    }

    // ------------------------------------------------------------------ ISL2

    @Test
    void hurtMonstersDefaultsToEveryRoleIncludingVisitors() {
        RolePermissions perms = defaults();
        for (PermRole role : PermRole.values()) {
            assertTrue(perms.allows(null, role, IslandPerm.HURT_MONSTERS), "hurt monsters: " + role);
        }
        IslandPerms island = new IslandPerms();
        island.set(PermRole.VISITOR, IslandPerm.HURT_MONSTERS, false);
        assertFalse(perms.allows(island, PermRole.VISITOR, IslandPerm.HURT_MONSTERS), "islands can take it away");
    }

    @Test
    void settingsDefaultsToLeaderAndAdmin() {
        RolePermissions perms = defaults();
        assertTrue(perms.allows(null, PermRole.LEADER, IslandPerm.SETTINGS));
        assertTrue(perms.allows(null, PermRole.ADMIN, IslandPerm.SETTINGS));
        assertFalse(perms.allows(null, PermRole.MOD, IslandPerm.SETTINGS));
        assertFalse(perms.allows(null, PermRole.MEMBER, IslandPerm.SETTINGS));
    }

    @Test
    void managementRightsNeverReachCoopOrVisitors() {
        RolePermissions perms = defaults();
        IslandPerms island = new IslandPerms();
        island.set(PermRole.VISITOR, IslandPerm.SETTINGS, true);
        island.set(PermRole.COOP, IslandPerm.SETTINGS, true);
        assertTrue(island.isEmpty(), "not even stored");
        assertFalse(perms.allows(island, PermRole.VISITOR, IslandPerm.SETTINGS));
        RolePermissions.Result bad = RolePermissions.parse(List.of(), List.of(), List.of(), List.of("settings"), List.of("*"),
                java.util.Map.of(IslandPerm.SETTINGS, List.of("visitor", "member", "boss")));
        assertEquals(3, bad.problems().size(), bad.problems().toString());
        assertFalse(bad.permissions().defaultAllows(PermRole.VISITOR, IslandPerm.SETTINGS));
        assertFalse(bad.permissions().defaultAllows(PermRole.COOP, IslandPerm.SETTINGS));
        assertTrue(bad.permissions().defaultAllows(PermRole.MEMBER, IslandPerm.SETTINGS));
        assertFalse(bad.permissions().defaultAllows(PermRole.VISITOR, IslandPerm.HURT_MONSTERS), "own list only");
    }

    @Test
    void newPermissionsHaveOwnDefaultsAndStableKeys() {
        assertEquals("hurt-monsters", IslandPerm.HURT_MONSTERS.key());
        assertEquals("settings", IslandPerm.SETTINGS.key());
        assertTrue(IslandPerm.HURT_MONSTERS.ownDefaults());
        assertTrue(IslandPerm.SETTINGS.management());
        assertFalse(IslandPerm.BREAK.ownDefaults());
        assertEquals(0, IslandPerm.wildcard() & IslandPerm.SETTINGS.bit());
        assertEquals(0, IslandPerm.notApplicable(PermRole.MEMBER));
        assertEquals(IslandPerm.SETTINGS.bit() | IslandPerm.NOTES_WRITE.bit() | IslandPerm.NOTES_MANAGE.bit(),
                IslandPerm.notApplicable(PermRole.VISITOR), "management rights (NOTES: write, manage) never reach visitors");
    }

    @Test
    void hostileMobsNeedTheHurtMonstersPermission() {
        assertEquals(IslandPerm.HURT_MONSTERS, IslandProtection.damagePerm(fake(org.bukkit.entity.Zombie.class)));
        assertEquals(IslandPerm.HURT_MONSTERS, IslandProtection.damagePerm(fake(org.bukkit.entity.Creeper.class)));
        assertEquals(IslandPerm.ANIMALS, IslandProtection.damagePerm(fake(org.bukkit.entity.Cow.class)));
        assertEquals(IslandPerm.VILLAGERS, IslandProtection.damagePerm(fake(org.bukkit.entity.Villager.class)));
        assertNull(IslandProtection.damagePerm(fake(org.bukkit.entity.Player.class)));
        assertNull(IslandProtection.entityPerm(fake(org.bukkit.entity.Zombie.class), false), "right-clicking monsters stays free");
    }

    /** An entity of the given type for instanceof checks only (no method may be called). */
    private static <T extends org.bukkit.entity.Entity> T fake(Class<T> type) {
        return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            throw new UnsupportedOperationException(method.getName());
        }));
    }

    // ------------------------------------------------------------------ NOTES

    @Test
    void notesDefaultsMatchOwnerN1() {
        RolePermissions perms = defaults();
        for (PermRole role : List.of(PermRole.LEADER, PermRole.ADMIN, PermRole.MOD)) {
            assertTrue(perms.allows(null, role, IslandPerm.NOTES_READ), "read: " + role);
            assertTrue(perms.allows(null, role, IslandPerm.NOTES_WRITE), "write: " + role);
            assertTrue(perms.allows(null, role, IslandPerm.NOTES_MANAGE), "manage: " + role);
        }
        assertTrue(perms.allows(null, PermRole.MEMBER, IslandPerm.NOTES_READ));
        assertTrue(perms.allows(null, PermRole.MEMBER, IslandPerm.NOTES_WRITE), "members add and tick");
        assertFalse(perms.allows(null, PermRole.MEMBER, IslandPerm.NOTES_MANAGE));
        assertTrue(perms.allows(null, PermRole.COOP, IslandPerm.NOTES_READ), "coop reads (owner N1)");
        assertFalse(perms.allows(null, PermRole.COOP, IslandPerm.NOTES_WRITE));
        assertFalse(perms.allows(null, PermRole.VISITOR, IslandPerm.NOTES_READ), "visitors see nothing");
    }

    @Test
    void notesWriteAndManageNeverReachCoopOrVisitors() {
        RolePermissions perms = defaults();
        IslandPerms island = new IslandPerms();
        island.set(PermRole.COOP, IslandPerm.NOTES_WRITE, true);
        island.set(PermRole.VISITOR, IslandPerm.NOTES_MANAGE, true);
        assertFalse(perms.allows(island, PermRole.COOP, IslandPerm.NOTES_WRITE));
        assertFalse(perms.allows(island, PermRole.VISITOR, IslandPerm.NOTES_MANAGE));
        island.set(PermRole.VISITOR, IslandPerm.NOTES_READ, true);
        assertTrue(perms.allows(island, PermRole.VISITOR, IslandPerm.NOTES_READ), "an island may make its notes public");
        island.set(PermRole.MEMBER, IslandPerm.NOTES_WRITE, false);
        assertFalse(perms.allows(island, PermRole.MEMBER, IslandPerm.NOTES_WRITE), "the matrix toggle (owner N1)");
        assertEquals(0L, IslandPerm.wildcard() & IslandPerm.NOTES_READ.bit(), "* in a role list does not grant notes");
    }
}
