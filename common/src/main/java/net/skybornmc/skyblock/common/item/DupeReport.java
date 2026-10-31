package net.skybornmc.skyblock.common.item;

import java.util.List;
import java.util.UUID;

/**
 * A unique item UUID found more than once.
 *
 * @param itemUuid  the duplicated instance id
 * @param itemId    custom item id ({@code tool_iron_pickaxe})
 * @param source    the source stored on the item ({@code admin:SlickyMatt})
 * @param holders   one entry per copy, e.g. {@code SlickyMatt inventory slot 3}, {@code SlickyMatt ender chest slot 0}
 * @param playerIds UUIDs of the online players holding or viewing a copy (for the audit log target)
 * @param time      epoch millis of the scan
 * @param holderIds UUIDs of the players with a copy in their <b>own</b> inventory, ender chest or cursor (not players that
 *                  only look into a container holding one); R3-28 trade-blocks these automatically
 */
public record DupeReport(UUID itemUuid, String itemId, String source, List<String> holders, List<UUID> playerIds, long time,
                         List<UUID> holderIds) {

    public DupeReport {
        holders = holders == null ? List.of() : List.copyOf(holders);
        playerIds = playerIds == null ? List.of() : List.copyOf(playerIds);
        holderIds = holderIds == null ? playerIds : List.copyOf(holderIds);
    }

    /** A report where every player counts as a holder. */
    public DupeReport(UUID itemUuid, String itemId, String source, List<String> holders, List<UUID> playerIds, long time) {
        this(itemUuid, itemId, source, holders, playerIds, time, playerIds);
    }

    /** Receives dupe reports. The default logs to the console and alerts staff; M0.8 adds the audit log. */
    @FunctionalInterface
    public interface Handler {
        void onDupe(DupeReport report);
    }
}
