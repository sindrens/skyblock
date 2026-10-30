package net.skybornmc.skyblock.common.teleport;

import org.bukkit.entity.Player;

/**
 * Why a player teleports. Each reason has its own warmup and cooldown ({@code teleport.yml → <reason id>.*}) and its
 * own bypass permission (skips warmup, cooldown and the combat block; guards still apply to warmup teleports).
 *
 * <p>The bypass nodes are declared by the module that owns the feature (essentials: {@code skyblock.staff.teleport.bypass},
 * depths: {@code skyblock.depths.bypass}); an undeclared node still works through LuckPerms/op.</p>
 */
public enum TeleportReason {

    /** {@code /spawn}. */
    SPAWN("spawn", TeleportReason.STAFF_BYPASS),
    /** An accepted {@code /tpa} or {@code /tpahere}. */
    TPA("tpa", TeleportReason.STAFF_BYPASS),
    /** {@code /is home}, {@code /is go}, personal island homes, the Home button. */
    ISLAND_HOME("island-home", TeleportReason.STAFF_BYPASS),
    /** {@code /is visit} and the visit browser. */
    ISLAND_VISIT("island-visit", TeleportReason.STAFF_BYPASS),
    /** {@code /is warp}, warp signs and the warp browser. */
    ISLAND_WARP("island-warp", TeleportReason.STAFF_BYPASS),
    /** {@code /dungeon} tier teleports (bypass: builders and admins with the Depths bypass, as before). */
    DUNGEON("dungeon", "skyblock.depths.bypass"),
    /** The teleport after a profile switch (always instant: the switch already happened). */
    PROFILE("profile", TeleportReason.STAFF_BYPASS),
    /** Instant staff teleports ({@code /tp}, {@code /tphere}, {@code /tppos}, {@code /top}, {@code /back}, staff homes). */
    STAFF("staff", TeleportReason.STAFF_BYPASS);

    /** Owned by the essentials module (permissions/essentials.yml). */
    public static final String STAFF_BYPASS = "skyblock.staff.teleport.bypass";

    private final String id;
    private final String bypassPermission;

    TeleportReason(String id, String bypassPermission) {
        this.id = id;
        this.bypassPermission = bypassPermission;
    }

    /** Config section and message id, e.g. {@code island-visit}. */
    public String id() {
        return id;
    }

    public String bypassPermission() {
        return bypassPermission;
    }

    public boolean bypass(Player player) {
        return player.hasPermission(bypassPermission);
    }
}
