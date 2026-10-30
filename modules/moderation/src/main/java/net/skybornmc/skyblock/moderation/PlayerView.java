package net.skybornmc.skyblock.moderation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What {@code /mypunishments} may show a player about themselves (spec 12.12). Pure. Only the player's own rows;
 * never IP addresses, alts, evidence or staff notes (those are not even passed in), and staff names only when
 * {@code player-view.show-staff} is on.
 */
final class PlayerView {

    private PlayerView() {
    }

    /** The mute in force now (the longest; permanent wins), or {@code null}. */
    static Punishment activeMute(List<Punishment> own, long now) {
        return PunishmentStore.longest(own.stream().filter(p -> p.type() == Punishment.Type.MUTE && p.active(now)).toList());
    }

    /** The newest {@code n} entries, newest first. */
    static List<Punishment> last(List<Punishment> own, int n) {
        List<Punishment> sorted = new ArrayList<>(own);
        sorted.sort(Comparator.comparingLong(Punishment::created).thenComparingLong(Punishment::id).reversed());
        return List.copyOf(sorted.subList(0, Math.min(Math.max(0, n), sorted.size())));
    }

    /** The staff name the player sees: the real one only when {@code showStaff}, else {@code hidden}. */
    static String staff(Punishment p, boolean showStaff, String hidden, String system) {
        if (!showStaff) {
            return hidden;
        }
        return p.issuer() == null ? system : p.issuerName();
    }

    /** A clickable link: {@code discord.skybornmc.net} → {@code https://discord.skybornmc.net}. */
    static String link(String url) {
        String u = url == null ? "" : url.strip();
        return u.startsWith("http://") || u.startsWith("https://") ? u : "https://" + u;
    }

    /** Only rows about {@code self} (defence in depth: the query already filters by uuid). */
    static List<Punishment> own(List<Punishment> rows, java.util.UUID self) {
        return rows.stream().filter(p -> self.equals(p.target())).toList();
    }
}
