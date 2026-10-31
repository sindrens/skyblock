package net.skybornmc.skyblock.quests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The quests of one board in one window (a row of {@code quest_sets} with its quests): a profile's daily or weekly set,
 * or an island's missions of the week. Main thread.
 *
 * @param owner profile key (daily, weekly) or island id (missions)
 */
final class QuestSet {

    final QuestKind kind;
    final UUID owner;
    final int period;
    int rerollsUsed;
    boolean bonusPaid;
    final List<Quest> quests = new ArrayList<>();
    /** Island missions: contribution per helper profile and quest uid (profile → amount), plus the player behind it. */
    final Map<UUID, Map<UUID, Long>> contributions = new HashMap<>();
    final Map<UUID, UUID> helperPlayers = new HashMap<>();
    /** Contribution the database has, per quest uid and profile. */
    final Map<UUID, Map<UUID, Long>> savedContributions = new HashMap<>();
    long lastUsed = System.currentTimeMillis();

    QuestSet(QuestKind kind, UUID owner, int period, int rerollsUsed, boolean bonusPaid) {
        this.kind = kind;
        this.owner = owner;
        this.period = period;
        this.rerollsUsed = rerollsUsed;
        this.bonusPaid = bonusPaid;
    }

    Optional<Quest> slot(int slot) {
        for (Quest q : quests) {
            if (q.slot == slot) {
                return Optional.of(q);
            }
        }
        return Optional.empty();
    }

    void replace(Quest quest) {
        for (int i = 0; i < quests.size(); i++) {
            if (quests.get(i).slot == quest.slot) {
                quests.set(i, quest);
                return;
            }
        }
        quests.add(quest);
    }

    int doneCount() {
        int n = 0;
        for (Quest q : quests) {
            if (q.done()) {
                n++;
            }
        }
        return n;
    }

    boolean allDone() {
        return !quests.isEmpty() && doneCount() == quests.size();
    }

    /** Adds a helper's contribution to an island mission. */
    void contribute(UUID questUid, UUID profile, UUID player, long amount) {
        contributions.computeIfAbsent(questUid, k -> new HashMap<>()).merge(profile, amount, Long::sum);
        helperPlayers.put(profile, player);
    }

    Map<UUID, Long> contributions(UUID questUid) {
        return contributions.getOrDefault(questUid, Map.of());
    }
}
