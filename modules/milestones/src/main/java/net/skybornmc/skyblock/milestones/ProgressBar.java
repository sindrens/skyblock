package net.skybornmc.skyblock.milestones;

import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Milestone progress in the action bar (build plan slice 4): after a counted action of {@code actionbar.actions} (crops and
 * ores by default; mobs off) the player sees the chain, its total and the next tier's target, at most once per
 * {@code actionbar.gap-ms}. Each player can turn it off in {@code /settings} ({@link #SETTING}). The stored totals of a scope
 * are read once (async) the first time they are needed and then kept up to date from the saves; until they are in, nothing
 * is shown. Main thread.
 */
final class ProgressBar {

    static final String SETTING = "milestones-actionbar";

    private final MilestonesModule m;
    /** Stored totals per scope ("ISLAND:<uuid>") → chain → total; a scope key present with an empty map = loaded, nothing yet. */
    private final Map<String, Map<String, Long>> stored = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastShown = new ConcurrentHashMap<>();

    ProgressBar(MilestonesModule m) {
        this.m = m;
    }

    static String scopeKey(ProgressBuffer.Scope scope, UUID id) {
        return scope.name() + ":" + id;
    }

    /** True when this action type shows progress at all ({@code actionbar.enabled} and listed). */
    boolean shows(String action) {
        MilestonesConfig c = m.config();
        return c.actionBarEnabled && action != null
                && c.actionBarActions.stream().anyMatch(a -> a != null && a.trim().toLowerCase(Locale.ROOT).equals(action));
    }

    /** After a counted action: one action bar line (throttled, setting on, totals loaded). */
    void show(Player p, ProgressBuffer.Scope scope, UUID scopeId, MilestoneModel.Chain chain) {
        long now = System.currentTimeMillis();
        Long last = lastShown.get(p.getUniqueId());
        if (last != null && now - last < m.config().actionBarGapMs) {
            return;
        }
        if (!m.core().services().optional(PlayerSettings.class).map(s -> s.get(p, SETTING)).orElse(true)) {
            return;
        }
        Map<String, Long> totals = stored.get(scopeKey(scope, scopeId));
        if (totals == null) {
            load(scope, scopeId);
            return;
        }
        lastShown.put(p.getUniqueId(), now);
        long total = totals.getOrDefault(chain.id(), 0L) + m.engine().pending(scope, scopeId, chain.id());
        int done = chain.tierFor(total);
        if (done >= chain.maxTier()) {
            m.msg().actionBar(p, "actionbar.maxed", Msg.p("chain", chain.name()), Msg.p("progress", Format.number(total)));
            return;
        }
        long target = chain.target(done + 1);
        long from = done == 0 ? 0 : chain.target(done);
        m.msg().actionBar(p, "actionbar.progress", Msg.p("chain", chain.name()), Msg.number("tier", done + 1),
                Msg.p("progress", Format.number(total)), Msg.p("target", Format.number(target)),
                Msg.p("bar", MilestonesMenu.bar(total, from, target)));
    }

    private void load(ProgressBuffer.Scope scope, UUID scopeId) {
        String key = scopeKey(scope, scopeId);
        if (!loading.add(key)) {
            return;
        }
        m.core().database().async(c -> MilestoneStore.rows(c, scope, scopeId)).whenComplete((rows, error) -> {
            loading.remove(key);
            if (error != null) {
                m.core().plugin().getLogger().log(Level.FINE, "Milestones: action bar totals not loaded", error);
                return;
            }
            Map<String, Long> totals = new ConcurrentHashMap<>();
            rows.forEach(r -> totals.put(r.chainId(), r.total()));
            stored.putIfAbsent(key, totals);
        });
    }

    /** A save went through: the saved amounts move from pending to stored. */
    void saved(Map<ProgressBuffer.Key, Long> batch) {
        batch.forEach((k, amount) -> {
            Map<String, Long> totals = stored.get(scopeKey(k.scope(), k.scopeId()));
            if (totals != null) {
                totals.merge(k.chainId(), amount, Long::sum);
            }
        });
    }

    /** A player left: forget their throttle; scope totals stay small and are dropped with {@link #clear}. */
    void quit(UUID player) {
        lastShown.remove(player);
    }

    void clear() {
        stored.clear();
        lastShown.clear();
    }
}
