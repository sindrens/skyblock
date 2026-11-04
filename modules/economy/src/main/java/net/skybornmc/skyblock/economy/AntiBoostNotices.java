package net.skybornmc.skyblock.economy;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.economy.AntiBoost;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Bukkit side of {@link AntiBoostImpl}: the audit row ({@code economy/anti_boost}), the staff notice after repeated
 * refusals ({@code skyblock.staff.economy.anomalies}) and the player texts ({@code economy.yml anti-boost.*}).
 */
final class AntiBoostNotices implements AntiBoostImpl.Reporter {

    private final EconomyModule module;

    AntiBoostNotices(EconomyModule module) {
        this.module = module;
    }

    /** The account (player) of a profile key. */
    private UUID owner(UUID profile) {
        return profile == null ? null : module.profiles().ownerOf(profile);
    }

    private static String nameOf(UUID player) {
        if (player == null) {
            return "?";
        }
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
        return offline.getName() == null ? player.toString().substring(0, 8) : offline.getName();
    }

    @Override
    public void audit(AntiBoost.Check refused, String path, Actor actor, List<AntiBoost.Transfer> transfers) {
        UUID player = owner(refused.profile());
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("path", path);
        d.put("side", refused.side() == null ? "error" : refused.side().name().toLowerCase(Locale.ROOT));
        if (refused.profile() != null) {
            d.put("profile", refused.profile().toString());
        }
        d.put("tried", refused.amount());
        d.put("room", refused.room());
        d.put("cap", refused.cap());
        d.put("minutes_left", refused.minutesLeft());
        List<Map<String, Object>> moves = new ArrayList<>();
        for (AntiBoost.Transfer t : transfers) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", t.from().toString());
            m.put("to", t.to().toString());
            m.put("amount", t.amount());
            moves.add(m);
        }
        d.put("transfers", moves);
        module.core.audit().logPlayer(AuditCategory.ECONOMY, AntiBoost.AUDIT_ACTION, actor, player,
                player == null ? null : nameOf(player), refused.amount(), null, d);
    }

    @Override
    public void alert(AntiBoost.Check refused, String path, int count, int minutes) {
        UUID player = owner(refused.profile());
        module.core.scheduler().main("economy:anti-boost-alert", () -> {
            String name = nameOf(player);
            String side = refused.side() == AntiBoost.Side.SENDER ? "sender" : "receiver";
            module.core.plugin().getLogger().info("Anti-boost: " + name + " was refused " + count + " times within " + minutes
                    + " min (" + side + " cap, last path " + path + ")");
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (!staff.hasPermission(net.skybornmc.skyblock.economy.admin.EconomyWatch.PERM_ANOMALIES)) {
                    continue;
                }
                module.msg.send(staff, "anti-boost.alert", Msg.p("player", name), Msg.number("count", count),
                        Msg.number("minutes", minutes), Msg.c("side", module.msg.component(staff, "anti-boost.side." + side)),
                        Msg.p("path", path), module.amountTag("amount", staff, Currency.MONEY, refused.amount()));
            }
        });
    }

    @Override
    public void tell(Player viewer, AntiBoost.Check refused, String newPlayerName) {
        if (refused.error()) {
            module.msg.send(viewer, "anti-boost.error");
            module.core.sounds().play(viewer, "error");
            return;
        }
        boolean self = refused.profile().equals(module.profiles().profileKey(viewer));
        String side = refused.side() == AntiBoost.Side.SENDER ? "sender" : "receiver";
        String name = newPlayerName != null ? newPlayerName : nameOf(owner(refused.profile()));
        TagResolver[] tags = {
                Msg.p("player", name),
                module.amountTag("room", viewer, Currency.MONEY, refused.room()),
                module.amountTag("cap", viewer, Currency.MONEY, refused.cap()),
                module.amountTag("amount", viewer, Currency.MONEY, refused.amount()),
                Msg.p("hours", Format.duration(Duration.ofMinutes(refused.minutes()))),
                Msg.p("left", Format.duration(Duration.ofMinutes(refused.minutesLeft())))};
        module.msg.send(viewer, "anti-boost." + side + (self ? ".self" : ".other"), tags);
        module.core.sounds().play(viewer, "error");
    }
}
