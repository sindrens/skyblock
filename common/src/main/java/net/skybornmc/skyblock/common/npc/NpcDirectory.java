package net.skybornmc.skyblock.common.npc;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.List;
import java.util.Optional;

/**
 * The NPCs and holograms placed from the applied builds (npcs module), published as a service
 * ({@code core.services().optional(NpcDirectory.class)}). Hooks for later features: the tutorial points its arrow at
 * {@link #npcLocation} and listens for {@link NpcClickEvent}; quests can check "talk to the Banker". Main thread.
 */
public interface NpcDirectory {

    /** One placed NPC: its build-point id ({@code bank}), role ({@code bank}, {@code forge}...) and where it stands. */
    record NpcInfo(String id, String role, Location location) {
    }

    /** Every NPC from the builds (spawned or not; its chunk may be unloaded). */
    List<NpcInfo> npcs();

    /** Where an NPC stands (feet), by build-point id. */
    Optional<Location> npcLocation(String npcId);

    /** The first NPC with this role, e.g. the tutorial's "go to the Bank" step. */
    Optional<NpcInfo> npcByRole(String role);

    /** Where a hologram or leaderboard is (its text position), by build-point id. */
    Optional<Location> hologramLocation(String hologramId);

    /** The NPC id of an entity (the NPC body, its name text or its click box), or empty when it is not one of ours. */
    Optional<String> npcId(Entity entity);
}
