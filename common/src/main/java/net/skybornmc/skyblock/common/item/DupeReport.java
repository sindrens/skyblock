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
 */
public record DupeReport(UUID itemUuid, String itemId, String source, List<String> holders, List<UUID> playerIds, long time) {

    /** Receives dupe reports. The default logs to the console and alerts staff; M0.8 adds the audit log. */
    @FunctionalInterface
    public interface Handler {
        void onDupe(DupeReport report);
    }
}
