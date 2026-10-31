package net.skybornmc.skyblock.afk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owner R3-3 staff family whitelist (pure, tested): {@code anti-abuse.family-groups} entries
 * {@code "name: uuid, uuid, ..."} (the name is optional and only a label). Accounts of one family may earn together on
 * one connection. An account listed in two families keeps the first; bad UUIDs are skipped and reported.
 *
 * @param byPlayer account → family id ({@code family-<n>} or its label)
 */
record FamilyGroups(Map<UUID, String> byPlayer, List<String> problems) {

    static final FamilyGroups NONE = new FamilyGroups(Map.of(), List.of());

    static FamilyGroups parse(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return NONE;
        }
        Map<UUID, String> byPlayer = new HashMap<>();
        List<String> problems = new ArrayList<>();
        int n = 0;
        for (String raw : entries) {
            n++;
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String entry = raw.trim();
            String label = "family-" + n;
            int colon = entry.indexOf(':');
            if (colon >= 0) {
                String name = entry.substring(0, colon).trim();
                if (!name.isEmpty()) {
                    label = name;
                }
                entry = entry.substring(colon + 1);
            }
            int members = 0;
            for (String part : entry.split("[,\\s]+")) {
                if (part.isBlank()) {
                    continue;
                }
                UUID id;
                try {
                    id = UUID.fromString(part.trim());
                } catch (IllegalArgumentException e) {
                    problems.add("afk.yml → anti-abuse.family-groups: '" + part.trim() + "' in '" + raw + "' is not a UUID (skipped)");
                    continue;
                }
                String before = byPlayer.putIfAbsent(id, label);
                if (before != null && !before.equals(label)) {
                    problems.add("afk.yml → anti-abuse.family-groups: " + id + " is in '" + before + "' and '" + label + "'; kept '" + before + "'");
                } else {
                    members++;
                }
            }
            if (members < 2) {
                problems.add("afk.yml → anti-abuse.family-groups: '" + raw + "' has fewer than 2 accounts, so it changes nothing");
            }
        }
        return new FamilyGroups(Map.copyOf(byPlayer), List.copyOf(problems));
    }

    /** The account's family, or null. */
    String of(UUID player) {
        return byPlayer.get(player);
    }
}
