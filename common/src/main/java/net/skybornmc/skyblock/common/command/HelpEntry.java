package net.skybornmc.skyblock.common.command;

import java.util.List;

/**
 * One runnable command in the help index (read by {@code /help} in Phase 1 and by the docs generator).
 *
 * @param path        e.g. {@code "skyblock reload"}
 * @param usage       e.g. {@code "/skyblock reload [module]"}
 * @param example     clickable example, e.g. {@code "/skyblock reload motd"}
 * @param permissions every permission needed on the way (all must be held); empty = everyone
 */
public record HelpEntry(String path, String usage, String example, String description, String category,
                        List<String> permissions, List<String> aliases, boolean playerOnly) {
}
