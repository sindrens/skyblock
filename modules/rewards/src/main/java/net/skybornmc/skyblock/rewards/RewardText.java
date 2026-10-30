package net.skybornmc.skyblock.rewards;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Player-facing text of rewards (messages/rewards.yml → {@code reward.*}), for chat lines and menu lore. */
final class RewardText {

    private RewardText() {
    }

    /** {@code money x1, key:ember x1 ...} for the audit row. */
    static String summary(Payout payout) {
        StringBuilder sb = new StringBuilder();
        for (Payout.Line l : payout.lines()) {
            sb.append(sb.isEmpty() ? "" : ", ").append(l.kind().key());
            if (!l.ref().isEmpty()) {
                sb.append(':').append(l.ref());
            }
            sb.append(l.fallback() ? "(fallback " : " x").append(l.value()).append(l.fallback() ? ")" : "");
        }
        for (Payout.Grant g : payout.grants()) {
            if (Payout.TITLE.equals(g.kind()) || Payout.PASS_XP.equals(g.kind())) {
                sb.append(sb.isEmpty() ? "" : ", ").append(g.kind()).append(':').append(g.ref().isEmpty() ? g.amount() : g.ref());
            }
        }
        return ClaimEngine.cut(sb.toString(), 512);
    }

    /** One chat line per reward of a committed payout. */
    static List<Component> lines(RewardsModule m, Player p, Payout payout) {
        List<Component> out = new ArrayList<>();
        for (Payout.Line l : payout.lines()) {
            if (l.fallback()) {
                out.add(m.msg.component(p, "reward.fallback-line", Msg.p("amount", format(m, Currency.CRYSTALS, l.value())),
                        Msg.c("reward", name(m, p, l.kind(), l.ref(), 1, l.minutes(), 0))));
            } else {
                out.add(m.msg.component(p, "reward.line", Msg.c("reward", line(m, p, l))));
            }
        }
        for (Payout.Grant g : payout.grants()) {
            if (Payout.TITLE.equals(g.kind())) {
                Component title = m.cosmetics().map(c -> c.display(p, Cosmetics.Kind.TITLE, g.ref())).orElse(Component.text(g.ref()));
                out.add(m.msg.component(p, "reward.line", Msg.c("reward", m.msg.component(p, "reward.title", Msg.c("title", title)))));
            } else if (Payout.PASS_XP.equals(g.kind())) {
                out.add(m.msg.component(p, "reward.line", Msg.c("reward", m.msg.component(p, "reward.pass-xp", Msg.number("amount", g.amount())))));
            }
        }
        return out;
    }

    private static Component line(RewardsModule m, Player p, Payout.Line l) {
        return switch (l.kind()) {
            case MONEY -> m.msg.component(p, "reward.money", Msg.p("amount", format(m, Currency.MONEY, l.value())));
            case CRYSTALS -> m.msg.component(p, "reward.crystals", Msg.p("amount", format(m, Currency.CRYSTALS, l.value())));
            case GEMS -> m.msg.component(p, "reward.gems", Msg.p("amount", format(m, Currency.GEMS, l.value())));
            case BOOK -> m.msg.component(p, "reward.book-named", Msg.p("name", pretty(l.ref())));
            default -> name(m, p, l.kind(), l.ref(), l.value(), l.minutes(), l.kind() == RewardSpec.Kind.BOOSTER ? l.value() : 0);
        };
    }

    /**
     * The name of a reward that is not a currency (also used for "instead of" lines).
     *
     * @param amount  keys or scrolls
     * @param percent booster strength
     */
    static Component name(RewardsModule m, Player p, RewardSpec.Kind kind, String ref, long amount, int minutes, long percent) {
        return switch (kind) {
            case MONEY -> m.msg.component(p, "reward-names.money");
            case CRYSTALS -> m.msg.component(p, "reward-names.crystals");
            case GEMS -> m.msg.component(p, "reward-names.gems");
            case KEY -> m.msg.component(p, "reward.key", Msg.number("amount", amount), Msg.p("crate", crateName(m, ref)));
            case FUEL -> m.msg.component(p, "reward.fuel", Msg.p("time", time(amount)));
            case BOOSTER -> m.msg.component(p, "reward.booster", Msg.p("type", boosterName(m, ref)), Msg.number("percent", percent),
                    Msg.p("time", time(minutes)));
            case BOOK -> m.msg.component(p, "reward.book", Msg.p("rarity", m.raw("rarities." + ref, capitalize(ref))));
            case SCROLL -> m.msg.component(p, "reward.scroll", Msg.number("amount", amount));
            case TOME -> m.msg.component(p, "reward.tome");
        };
    }

    /** Menu lore of a calendar day: what it gives at this tier, and a note when it pays crystals for now. */
    static Component describe(RewardsModule m, Player p, RewardSpec spec, Payout.Context ctx) {
        Component main = switch (spec.kind()) {
            case MONEY -> m.msg.component(p, "reward.money",
                    Msg.p("amount", format(m, Currency.MONEY, RewardTables.money(spec.amount(), ctx.tier(), ctx.refPerMinute()))));
            case CRYSTALS -> m.msg.component(p, "reward.crystals",
                    Msg.p("amount", format(m, Currency.CRYSTALS, RewardTables.crystals(spec.amount(), ctx.tier(), ctx.growth()))));
            case GEMS -> m.msg.component(p, "reward.gems", Msg.p("amount", format(m, Currency.GEMS, spec.amount())));
            case BOOSTER -> name(m, p, spec.kind(), spec.ref(), 1, spec.minutes(), spec.amount());
            case BOOK -> name(m, p, spec.kind(), spec.ref(), 1, 0, 0);
            default -> name(m, p, spec.kind(), spec.ref(), spec.amount(), spec.minutes(), 0);
        };
        if (spec.kind().currency() || ctx.live().test(spec)) {
            return main;
        }
        long base = spec.fallback() >= 0 ? spec.fallback() : ctx.defaultFallback();
        return m.msg.component(p, "reward.for-now", Msg.c("reward", main),
                Msg.p("amount", format(m, Currency.CRYSTALS, RewardTables.crystals(base, ctx.tier(), ctx.growth()))));
    }

    static String format(RewardsModule m, Currency currency, long amount) {
        return m.economy().map(e -> e.format(currency, amount)).orElse(Format.number(amount));
    }

    static String crateName(RewardsModule m, String crate) {
        return m.raw("crates." + crate, capitalize(crate));
    }

    static String boosterName(RewardsModule m, String type) {
        return m.raw("booster-types." + BoosterType.byId(type).map(BoosterType::id).orElse(type), capitalize(type.replace('_', ' ')));
    }

    /** "2h", "1h 30m", "45m" for minutes. */
    static String time(long minutes) {
        return Format.duration(Duration.ofMinutes(Math.max(0, minutes)));
    }

    static String pretty(String id) {
        String[] words = id.replace('-', '_').split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(sb.isEmpty() ? "" : " ").append(capitalize(w));
            }
        }
        return sb.toString();
    }

    static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
