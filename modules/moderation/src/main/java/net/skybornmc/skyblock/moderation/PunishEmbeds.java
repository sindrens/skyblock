package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.discord.DiscordSink;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The Discord embeds of the punishment log (spec 12.11): one per punishment, pardon and undo, with type, target,
 * reason, length, staff (or "System"), punishment id and, for automatic rules, the rule. Pure. <b>Never</b> an IP
 * address and never evidence text: only the fields below are ever read from a punishment.
 *
 * <p>{@code text} looks up labels in {@code messages/moderation.yml → discord.*} (key without the prefix;
 * {@code rule-count=2} means {@code discord.rule-count} with {@code <count>} = 2).</p>
 */
final class PunishEmbeds {

    private PunishEmbeds() {
    }

    /** Embed kind for a punishment type ({@code ban}, {@code mute}, {@code warn}, {@code kick}). */
    static String kind(Punishment p) {
        return p.type().name().toLowerCase(Locale.ROOT);
    }

    /** A punishment that was just applied. */
    static DiscordSink.Embed applied(Punishment p, PunishConfig.Discord cfg, Function<String, String> text) {
        String kind = kind(p);
        List<DiscordSink.Field> fields = new ArrayList<>();
        fields.add(new DiscordSink.Field(text.apply("fields.type"), typeText(p, text), true));
        fields.add(new DiscordSink.Field(text.apply("fields.target"), name(p.targetName(), text), true));
        fields.add(new DiscordSink.Field(text.apply("fields.staff"), staff(p, text), true));
        fields.add(new DiscordSink.Field(text.apply("fields.reason"), p.reason(), false));
        if (p.type() == Punishment.Type.BAN || p.type() == Punishment.Type.MUTE) {
            fields.add(new DiscordSink.Field(text.apply("fields.length"), p.expires() == null ? text.apply("permanent")
                    : Templates.Step.compact(Duration.ofMillis(Math.max(0, p.expires() - p.created()))), true));
        }
        if (p.type() == Punishment.Type.WARN && p.weight() != Weights.ONE) {
            fields.add(new DiscordSink.Field(text.apply("fields.weight"), Weights.text(p.weight()), true));
        }
        fields.add(new DiscordSink.Field(text.apply("fields.id"), "#" + p.id(), true));
        if (p.automatic()) {
            fields.add(new DiscordSink.Field(text.apply("fields.rule"), rule(p.source(), text), true));
        }
        return new DiscordSink.Embed(text.apply("titles." + kind), cfg.color(kind), fields, text.apply("footer"), p.created());
    }

    /** Punishments that were pardoned (/unban, /unmute, /unwarn) by {@code by} with {@code reason}. */
    static DiscordSink.Embed pardoned(List<Punishment> list, String targetName, String by, String reason, long at,
                                      PunishConfig.Discord cfg, Function<String, String> text) {
        List<DiscordSink.Field> fields = new ArrayList<>();
        Punishment first = list.isEmpty() ? null : list.getFirst();
        fields.add(new DiscordSink.Field(text.apply("fields.type"), first == null ? "-" : typeText(first, text), true));
        fields.add(new DiscordSink.Field(text.apply("fields.target"), name(targetName, text), true));
        fields.add(new DiscordSink.Field(text.apply("fields.staff"), name(by, text), true));
        fields.add(new DiscordSink.Field(text.apply("fields.reason"), reason, false));
        fields.add(new DiscordSink.Field(text.apply("fields.id"), String.join(", ", list.stream().map(p -> "#" + p.id()).toList()), true));
        return new DiscordSink.Embed(text.apply("titles.pardon"), cfg.color("pardon"), fields, text.apply("footer"), at);
    }

    /** A punishment undone with [Undo] by {@code by}. */
    static DiscordSink.Embed undone(Punishment p, String by, String reason, long at, PunishConfig.Discord cfg,
                                    Function<String, String> text) {
        List<DiscordSink.Field> fields = new ArrayList<>();
        fields.add(new DiscordSink.Field(text.apply("fields.type"), typeText(p, text), true));
        fields.add(new DiscordSink.Field(text.apply("fields.target"), name(p.targetName(), text), true));
        fields.add(new DiscordSink.Field(text.apply("fields.staff"), name(by, text), true));
        fields.add(new DiscordSink.Field(text.apply("fields.given-by"), staff(p, text), true));
        fields.add(new DiscordSink.Field(text.apply("fields.reason"), reason, false));
        fields.add(new DiscordSink.Field(text.apply("fields.id"), "#" + p.id(), true));
        return new DiscordSink.Embed(text.apply("titles.undo"), cfg.color("undo"), fields, text.apply("footer"), at);
    }

    /** Most player names listed in one bulk embed (the count is always exact). */
    static final int BULK_NAMES = 20;

    /** A bulk action (spec 13.8): one embed with the count, the reason, the staff member and the players. */
    static DiscordSink.Embed bulk(String group, String reason, String staff, List<Punishment> applied, int requested, int skipped,
                                  long at, PunishConfig.Discord cfg, Function<String, String> text) {
        List<DiscordSink.Field> fields = new ArrayList<>();
        fields.add(new DiscordSink.Field(text.apply("fields.reason"), reason, true));
        fields.add(new DiscordSink.Field(text.apply("fields.staff"), name(staff, text), true));
        fields.add(new DiscordSink.Field(text.apply("fields.count"), String.valueOf(applied.size()), true));
        if (requested > 0) {
            fields.add(new DiscordSink.Field(text.apply("fields.requested"), String.valueOf(requested), true));
        }
        if (skipped > 0) {
            fields.add(new DiscordSink.Field(text.apply("fields.skipped"), String.valueOf(skipped), true));
        }
        if (!applied.isEmpty()) {
            fields.add(new DiscordSink.Field(text.apply("fields.players"), players(applied, true), false));
        }
        fields.add(new DiscordSink.Field(text.apply("fields.group"), group, true));
        return new DiscordSink.Embed(text.apply("titles.bulk"), cfg.color("bulk"), fields, text.apply("footer"), at);
    }

    /** [Undo all] of a bulk action. */
    static DiscordSink.Embed bulkUndone(String group, String staff, List<Punishment> undone, long at, PunishConfig.Discord cfg,
                                        Function<String, String> text) {
        List<DiscordSink.Field> fields = new ArrayList<>();
        fields.add(new DiscordSink.Field(text.apply("fields.staff"), name(staff, text), true));
        fields.add(new DiscordSink.Field(text.apply("fields.count"), String.valueOf(undone.size()), true));
        fields.add(new DiscordSink.Field(text.apply("fields.players"), players(undone, false), false));
        fields.add(new DiscordSink.Field(text.apply("fields.group"), group, true));
        return new DiscordSink.Embed(text.apply("titles.bulk-undo"), cfg.color("undo"), fields, text.apply("footer"), at);
    }

    /**
     * A raid lockdown started or ended (spec 13.9). {@code event}: {@code start}, {@code stop}, {@code expired} or
     * {@code restart}; {@code by} = who did it (null = system). No player names: a lockdown is never about one player.
     */
    static DiscordSink.Embed lockdown(Lockdown.Active a, String event, String by, long at, PunishConfig.Discord cfg,
                                      Function<String, String> text) {
        List<DiscordSink.Field> fields = new ArrayList<>();
        fields.add(new DiscordSink.Field(text.apply("fields.lockdown"), text.apply("lockdown-kinds." + a.kind().id()), true));
        fields.add(new DiscordSink.Field(text.apply("fields.staff"), name(by, text), true));
        fields.add(new DiscordSink.Field(text.apply("fields.length"), Templates.Step.compact(a.length()), true));
        if (a.auto()) {
            fields.add(new DiscordSink.Field(text.apply("fields.started-by"), text.apply("lockdown-auto"), true));
        }
        return new DiscordSink.Embed(text.apply("titles.lockdown-" + event), cfg.color("lockdown"), fields,
                text.apply("footer"), at);
    }

    /** "Steve (mute 30m), Alex (mute 6h) +3 more": names and lengths only. */
    static String players(List<Punishment> list, boolean withStep) {
        List<String> parts = new ArrayList<>();
        for (Punishment p : list.subList(0, Math.min(BULK_NAMES, list.size()))) {
            String step = p.type().name().toLowerCase(Locale.ROOT) + (p.type() == Punishment.Type.BAN || p.type() == Punishment.Type.MUTE
                    ? " " + (p.expires() == null ? "perm" : Templates.Step.compact(Duration.ofMillis(Math.max(0, p.expires() - p.created()))))
                    : "");
            parts.add(withStep ? p.targetName() + " (" + step + ")" : p.targetName());
        }
        String out = String.join(", ", parts);
        return list.size() > BULK_NAMES ? out + " +" + (list.size() - BULK_NAMES) : out;
    }

    /**
     * A new appeal (spec 13.10 step 1): appeal and punishment id, type, reason, player, their text and a short history
     * summary. Never an IP address, evidence or notes.
     */
    static DiscordSink.Embed appealNew(Appeal a, Punishment p, AppealRules.HistorySummary history, PunishConfig.Discord cfg,
                                       Function<String, String> text) {
        List<DiscordSink.Field> fields = new ArrayList<>();
        fields.add(new DiscordSink.Field(text.apply("fields.appeal"), "#" + a.id(), true));
        fields.add(new DiscordSink.Field(text.apply("fields.target"), name(a.playerName(), text), true));
        fields.add(new DiscordSink.Field(text.apply("fields.id"), "#" + a.punishmentId(), true));
        if (p != null) {
            fields.add(new DiscordSink.Field(text.apply("fields.type"), typeText(p, text), true));
            fields.add(new DiscordSink.Field(text.apply("fields.staff"), staff(p, text), true));
            fields.add(new DiscordSink.Field(text.apply("fields.reason"), p.reason(), false));
        }
        fields.add(new DiscordSink.Field(text.apply("fields.text"), a.text(), false));
        fields.add(new DiscordSink.Field(text.apply("fields.history"), history.bans() + " / " + history.mutes() + " / "
                + history.warns() + " / " + history.kicks(), false));
        return new DiscordSink.Embed(text.apply("titles.appeal"), cfg.color("appeal"), fields, text.apply("footer"), a.created());
    }

    /** A decision on an appeal (accepted, reduced, denied, closed). */
    static DiscordSink.Embed appealDecision(Appeal a, PunishConfig.Discord cfg, Function<String, String> text) {
        List<DiscordSink.Field> fields = new ArrayList<>();
        fields.add(new DiscordSink.Field(text.apply("fields.appeal"), "#" + a.id(), true));
        fields.add(new DiscordSink.Field(text.apply("fields.target"), name(a.playerName(), text), true));
        fields.add(new DiscordSink.Field(text.apply("fields.id"), "#" + a.punishmentId(), true));
        fields.add(new DiscordSink.Field(text.apply("fields.decision"), text.apply("appeal-status." + a.status().key()), true));
        fields.add(new DiscordSink.Field(text.apply("fields.handler"), name(a.handlerName(), text), true));
        if (a.response() != null) {
            fields.add(new DiscordSink.Field(text.apply("fields.response"), a.response(), false));
        }
        String kind = a.status() == Appeal.Status.ACCEPTED ? "pardon" : "appeal";
        return new DiscordSink.Embed(text.apply("titles.appeal-" + a.status().key()), cfg.color(kind), fields,
                text.apply("footer"), a.handled() == null ? 0 : a.handled());
    }

    /** "Ban", "IP-ban" (scope only, never the address), "Mute", "Warning", "Kick". */
    static String typeText(Punishment p, Function<String, String> text) {
        String kind = p.ipWide() && p.type() == Punishment.Type.BAN ? "ipban"
                : p.ipWide() && p.type() == Punishment.Type.MUTE ? "ipmute" : kind(p);
        return text.apply("types." + kind);
    }

    private static String staff(Punishment p, Function<String, String> text) {
        // console and automatic rules have no uuid: "System"
        return p.issuer() == null ? text.apply("system") : name(p.issuerName(), text);
    }

    private static String name(String name, Function<String, String> text) {
        return name == null || name.isBlank() ? text.apply("system") : name;
    }

    /**
     * {@code auto:warn-rule:2} → "2 warnings", {@code auto:warn-rule:repeat} → "repeat". The count is passed to the
     * lookup as {@code rule-count=<n>} (the service fills the {@code <count>} placeholder).
     */
    static String rule(String source, Function<String, String> text) {
        String rule = source.substring(source.lastIndexOf(':') + 1);
        return "repeat".equals(rule) ? text.apply("rule-repeat") : text.apply(RULE_COUNT + rule);
    }

    /** Lookup key prefix for "<n> warnings". */
    static final String RULE_COUNT = "rule-count=";
}
