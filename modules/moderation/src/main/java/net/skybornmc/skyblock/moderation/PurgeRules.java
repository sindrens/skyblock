package net.skybornmc.skyblock.moderation;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Data deletion (spec 14.10), pure: what {@code /ppurge <player> confirm} keeps and removes.
 *
 * <ul>
 *   <li>Removed: evidence snapshots (chat lines), staff notes about the player, appeal texts, watchlist entries, stored IP
 *       data and alt links, suggestion details, handled approval requests.</li>
 *   <li>Kept (minimal ledger): each punishment with only the account id, type, reason id, start/end and punishment id,
 *       for {@code keep-ledger} after it ended (0 = delete everything that is not in force). The player's name is
 *       replaced by the account id; the reason text by the reason id.</li>
 *   <li>A punishment still in force (a ban, mute, island ban, coop block or report block that has not ended) is always
 *       kept, so the ban stays enforced and ban evasion is still detected. An IP-wide ban in force keeps its address
 *       until it ends (the IP is what enforces it); it is removed automatically afterwards.</li>
 *   <li>The audit log is never touched (spec: the purge is audited, without personal data).</li>
 * </ul>
 */
final class PurgeRules {

    private PurgeRules() {
    }

    /**
     * {@code extras.purge}.
     *
     * @param keepLedger    how long the minimal ledger of an ended punishment is kept ({@link Duration#ZERO} = not at all)
     * @param confirmWindow how long a preview can be confirmed
     */
    record Settings(Duration keepLedger, Duration confirmWindow) {
        static final Settings DEFAULT = new Settings(Duration.ofDays(365), Duration.ofSeconds(60));
    }

    /** What happens to one punishment row. */
    enum Fate { KEEP_ACTIVE, KEEP_LEDGER, DELETE }

    /** When a punishment ended: pardoned/undone, else its end, else (warn, kick, permanent) when it was given. */
    static long endOf(Punishment p) {
        if (p.removedAt() != null) {
            return p.removedAt();
        }
        if (p.expires() != null) {
            return p.expires();
        }
        return p.created();
    }

    /** In force right now (timed and not ended): always kept. */
    static boolean inForce(Punishment p, long now) {
        return p.type().timed() && p.active(now);
    }

    static Fate fate(Punishment p, Settings s, long now) {
        if (inForce(p, now)) {
            return Fate.KEEP_ACTIVE;
        }
        Duration keep = s.keepLedger();
        if (keep == null || keep.isZero() || keep.isNegative()) {
            return Fate.DELETE;
        }
        return now - endOf(p) <= keep.toMillis() ? Fate.KEEP_LEDGER : Fate.DELETE;
    }

    /** The IP stays only on an IP-wide punishment still in force. */
    static boolean keepsIp(Punishment p, long now) {
        return p.ipWide() && p.ip() != null && inForce(p, now);
    }

    /** The name stored instead of the player's name: the account id without dashes (32 characters, fits the column). */
    static String anonymousName(UUID account) {
        return account == null ? "purged" : account.toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    /** The reason stored instead of the reason text: the reason id, or {@code purged} for a free-text reason. */
    static String ledgerReason(Punishment p) {
        return p.template() == null || p.template().isBlank() ? "purged" : p.template();
    }

    /** Preview numbers for the punishment rows. */
    record Ledger(int keepActive, int keepLedger, int delete, boolean activeBan) {
    }

    static Ledger ledger(List<Punishment> history, Settings s, long now) {
        int a = 0;
        int l = 0;
        int d = 0;
        boolean ban = false;
        for (Punishment p : history) {
            switch (fate(p, s, now)) {
                case KEEP_ACTIVE -> {
                    a++;
                    ban |= p.type() == Punishment.Type.BAN;
                }
                case KEEP_LEDGER -> l++;
                case DELETE -> d++;
            }
        }
        return new Ledger(a, l, d, ban);
    }

    /** True if a confirm matches the preview: same player, same sender, within the window. */
    static boolean confirms(UUID previewTarget, long previewAt, UUID target, long now, Duration window) {
        return previewTarget != null && previewTarget.equals(target) && now >= previewAt && now - previewAt <= window.toMillis();
    }
}
