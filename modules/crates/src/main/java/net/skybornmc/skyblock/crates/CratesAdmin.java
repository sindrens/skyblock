package net.skybornmc.skyblock.crates;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.profile.Profile;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Staff sub-commands {@code /crates admin give|take|view} (owner approved P10-41). Admin only
 * ({@code skyblock.crates.admin}), work from the console, full tab completion. Give and take act on the player's
 * <b>active</b> profile (offline: the last active one); view shows every profile (menu with a profile button for players,
 * chat lines for the console). Amounts over {@code admin.confirm-above} ask to confirm. Every give is the normal
 * {@code key_give} audit row with the staff member as actor and reason {@code staff}; takes write {@code key_take}.
 */
final class CratesAdmin {

    static final String PERM_ADMIN = "skyblock.crates.admin";
    static final String REASON = "staff";
    static final String AUDIT_TAKE = "key_take";
    static final String AUDIT_VIEW = "crates_view";

    /** The kind of keys a staff give adds. */
    enum KeyKind { BOUND, TRADEABLE }

    /** A resolved player: their profiles (by slot), shown names, and the one in use (the active profile at first). */
    record Target(UUID player, String name, List<UUID> keys, List<String> profileNames, int index) {

        UUID key() {
            return keys.get(index);
        }

        String profileName() {
            return profileNames.get(index);
        }

        Target next() {
            return new Target(player, name, keys, profileNames, (index + 1) % keys.size());
        }

        Target at(int i) {
            return new Target(player, name, keys, profileNames, i);
        }
    }

    private final CratesModule m;

    CratesAdmin(CratesModule module) {
        this.m = module;
    }

    // ================================================================== command tree

    CommandSpec spec() {
        Arg<String> player = Args.offlinePlayer("player");
        Arg<CrateCatalog.Crate> crate = Arg.word("crate", (sender, raw) -> m.catalog().crate(raw)
                .orElseThrow(() -> new CommandFailure(CratesModule.ID, "unknown-crate", Msg.p("input", raw),
                        Msg.p("crates", String.join(", ", allIds())))), Suggest.of(this::allIds));
        Arg<Integer> amount = Arg.word("amount", (sender, raw) -> amount(raw), Suggest.of("1", "5", "10", "64"));
        Arg<KeyKind> type = Args.choice("type", KeyKind.class);
        int seconds = m.settings.adminConfirmSeconds;
        return CommandSpec.sub("admin")
                .description("Staff tools for crate keys: give, take and view")
                .category("Staff")
                .permission(PERM_ADMIN)
                .example("crates admin view Steve")
                .sub(CommandSpec.sub("give")
                        .description("Give crate keys to a player's active profile (bound unless you add tradeable)")
                        .permission(PERM_ADMIN)
                        .arg(player).arg(crate).arg(amount).optional(type)
                        .confirmIf(seconds, ctx -> needsConfirm(ctx.get(amount)))
                        .confirmMessage(CratesModule.ID, "admin.confirm-give", ctx -> new TagResolver[]{
                                Msg.number("amount", ctx.get(amount)), Msg.mm("key", m.keyMini(ctx.get(crate).id())),
                                Msg.p("player", ctx.get(player))})
                        .example("crates admin give Steve ember 5 bound")
                        .executes(ctx -> give(ctx.sender(), ctx.get(player), ctx.get(crate), ctx.get(amount),
                                ctx.getOr(type, m.settings.adminDefaultTradeable ? KeyKind.TRADEABLE : KeyKind.BOUND))))
                .sub(CommandSpec.sub("take")
                        .description("Take crate keys from a player's active profile (bound first, never below 0)")
                        .permission(PERM_ADMIN)
                        .arg(player).arg(crate).arg(amount)
                        .confirmIf(seconds, ctx -> needsConfirm(ctx.get(amount)))
                        .confirmMessage(CratesModule.ID, "admin.confirm-take", ctx -> new TagResolver[]{
                                Msg.number("amount", ctx.get(amount)), Msg.mm("key", m.keyMini(ctx.get(crate).id())),
                                Msg.p("player", ctx.get(player))})
                        .example("crates admin take Steve ember 5")
                        .executes(ctx -> take(ctx.sender(), ctx.get(player), ctx.get(crate), ctx.get(amount))))
                .sub(CommandSpec.sub("view")
                        .description("A player's keys and pity on every profile")
                        .permission(PERM_ADMIN)
                        .arg(player)
                        .example("crates admin view Steve")
                        .executes(ctx -> view(ctx.sender(), ctx.get(player))));
    }

    List<String> allIds() {
        return m.catalog().crates().stream().map(CrateCatalog.Crate::id).toList();
    }

    /** Parses a key amount (1 to {@code admin.max-amount}). */
    int amount(String raw) {
        int max = Math.max(1, Math.min(m.settings.adminMaxAmount, CrateKeysService.MAX_PER_CALL));
        return parseAmount(raw, max).orElseThrow(() -> CommandFailure.of("invalid-number", Msg.p("input", raw), Msg.p("min", 1),
                Msg.p("max", max)));
    }

    /** A whole number from 1 to {@code max}; empty otherwise. Pure. */
    static Optional<Integer> parseAmount(String raw, int max) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value >= 1 && value <= max ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException | NullPointerException e) {
            return Optional.empty();
        }
    }

    boolean needsConfirm(int amount) {
        return needsConfirm(amount, m.settings.adminConfirmAbove);
    }

    /** Amounts over the threshold ask to confirm. Pure. */
    static boolean needsConfirm(int amount, int above) {
        return amount > Math.max(0, above);
    }

    // ================================================================== actions

    private void give(CommandSender sender, String name, CrateCatalog.Crate crate, int amount, KeyKind kind) {
        Actor actor = Actor.of(sender);
        boolean bound = kind == KeyKind.BOUND;
        withTarget(sender, name, t -> m.keys().grant(t.key(), crate, amount, bound, REASON, actor, t.player(), t.name())
                .whenComplete((ok, error) -> main(() -> {
                    if (error != null || !Boolean.TRUE.equals(ok)) {
                        m.fail(sender, "admin.failed");
                        return;
                    }
                    m.msg.send(sender, "admin.given", Msg.number("amount", amount), kindTag(kind), Msg.mm("key", m.keyMini(crate.id())),
                            Msg.p("player", t.name()), Msg.p("profile", t.profileName()));
                    success(sender);
                    if (m.settings.adminNotify) {
                        m.profiles().onlinePlayer(t.key()).ifPresent(p -> {
                            m.msg.send(p, "admin.received", Msg.number("amount", amount), kindTag(kind), Msg.mm("key", m.keyMini(crate.id())));
                            m.core.sounds().play(p, "notify");
                        });
                    }
                })));
    }

    private void take(CommandSender sender, String name, CrateCatalog.Crate crate, int amount) {
        Actor actor = Actor.of(sender);
        int attempts = m.settings.retries + 1;
        withTarget(sender, name, t -> m.core.database().transaction(c -> CrateStore.staffTake(c, t.key(), crate.id(), amount,
                System.currentTimeMillis(), attempts)).whenComplete((result, error) -> main(() -> {
            if (error != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Crates: staff take of " + amount + " " + crate.id() + " key(s) from "
                        + t.name() + " failed; nothing was taken", Database.cause(error));
                m.fail(sender, "admin.failed");
                return;
            }
            if (!result.done()) {
                m.fail(sender, "admin.not-enough", Msg.p("player", t.name()), Msg.number("has", result.before().total()),
                        Msg.mm("key", m.keyMini(crate.id())), Msg.number("amount", amount), Msg.p("profile", t.profileName()));
                return;
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("crate", crate.id());
            d.put("profile", t.key().toString());
            d.put("bound", result.bound());
            d.put("tradeable", result.tradeable());
            d.put("before_bound", result.before().bound());
            d.put("before_tradeable", result.before().tradeable());
            m.audit(AUDIT_TAKE, actor, t.player(), t.name(), result.total(), d);
            m.changed(t.key());
            m.msg.send(sender, "admin.taken", Msg.number("amount", result.total()), Msg.mm("key", m.keyMini(crate.id())),
                    Msg.p("player", t.name()), Msg.p("profile", t.profileName()), Msg.number("bound", result.bound()),
                    Msg.number("tradeable", result.tradeable()), Msg.number("left", result.before().total() - result.total()));
            success(sender);
        })));
    }

    private void view(CommandSender sender, String name) {
        Actor actor = Actor.of(sender);
        withTarget(sender, name, t -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("profiles", t.keys().size());
            m.core.audit().logPlayer(AuditCategory.STAFF, AUDIT_VIEW, actor, t.player(), t.name(), null, null, d);
            if (sender instanceof Player staff) {
                if (staff.isOnline()) {
                    m.core.menus().open(staff, new StaffCratesMenu(m, t));
                }
                return;
            }
            printAll(sender, t);
        });
    }

    /** Console (and RCON): one block of lines per profile, the active one first. */
    private void printAll(CommandSender sender, Target t) {
        List<CompletableFuture<CratesModule.ProfileView>> loads = new ArrayList<>();
        for (UUID key : t.keys()) {
            loads.add(m.readView(key));
        }
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).whenComplete((v, error) -> main(() -> {
            if (error != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Crates: loading keys of " + t.name() + " failed", Database.cause(error));
                m.fail(sender, "admin.failed");
                return;
            }
            for (int i = 0; i < t.keys().size(); i++) {
                Target at = t.at(i);
                m.msg.send(sender, i == t.index() ? "admin.view.header-active" : "admin.view.header", Msg.p("player", t.name()),
                        Msg.p("profile", at.profileName()));
                print(sender, loads.get(i).join());
            }
        }));
    }

    private void print(CommandSender sender, CratesModule.ProfileView view) {
        int lines = 0;
        for (CrateCatalog.Crate crate : m.catalog().crates()) {
            CrateStore.Keys k = view.keys(crate.id());
            int pity = view.counter(crate.id(), m.season());
            if (k.total() == 0 && pity == 0) {
                continue;
            }
            boolean hasPity = crate.pity() > 0 && crate.hasTop();
            m.msg.send(sender, hasPity ? "admin.view.line" : "admin.view.line-no-pity", Msg.mm("key", m.keyMini(crate.id())),
                    Msg.number("bound", k.bound()), Msg.number("tradeable", k.tradeable()), Msg.number("pity", pity),
                    Msg.number("max", crate.pity()));
            lines++;
        }
        if (lines == 0) {
            m.msg.send(sender, "admin.view.none");
        }
    }

    // ================================================================== target lookup

    /**
     * Finds the player (online, else from the database), their live profiles and active profile, then runs {@code then}
     * on the main thread. Unknown players and errors are answered here.
     */
    private void withTarget(CommandSender sender, String name, java.util.function.Consumer<Target> then) {
        Player online = Bukkit.getPlayerExact(name);
        CompletableFuture<Optional<Map.Entry<UUID, String>>> who = online != null
                ? CompletableFuture.completedFuture(Optional.of(Map.entry(online.getUniqueId(), online.getName())))
                : m.core.players().offline(name).thenApply(found -> found.map(s -> Map.entry(s.uuid(), s.name())));
        who.thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.<Target>empty());
            }
            UUID id = found.get().getKey();
            String real = found.get().getValue();
            return m.profiles().loadProfiles(id).thenCombine(m.profiles().profileKeyAsync(id),
                    (list, active) -> Optional.of(target(id, real, list, active)));
        }).whenComplete((target, error) -> main(() -> {
            if (error != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Crates: looking up " + name + " failed", Database.cause(error));
                m.fail(sender, "admin.failed");
                return;
            }
            if (target.isEmpty()) {
                m.fail(sender, "admin.unknown-player", Msg.p("player", name));
                return;
            }
            then.accept(target.get());
        }));
    }

    private Target target(UUID player, String name, List<Profile> profiles, UUID active) {
        List<UUID> keys = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Profile p : profiles) {
            keys.add(p.id());
            names.add(profileName(p.name(), p.slot()));
        }
        return build(player, name, keys, names, active, profileName(null, 1));
    }

    /**
     * The target from a player's profiles: without profiles (module off, never loaded) the player UUID is profile 1;
     * the active profile is shown first, falling back to the first one. Pure.
     */
    static Target build(UUID player, String name, List<UUID> keys, List<String> names, UUID active, String firstName) {
        List<UUID> k = new ArrayList<>(keys);
        List<String> n = new ArrayList<>(names);
        if (k.isEmpty()) {
            k.add(player);
            n.add(firstName);
        }
        int index = Math.max(0, k.indexOf(active == null ? player : active));
        return new Target(player, name, List.copyOf(k), List.copyOf(n), index);
    }

    private String profileName(String custom, int slot) {
        if (custom != null && !custom.isBlank()) {
            return custom;
        }
        return m.raw("admin.profile-default", "Profile <slot>").replace("<slot>", String.valueOf(slot));
    }

    private TagResolver kindTag(KeyKind kind) {
        return Msg.mm("kind", m.raw("admin.kind." + kind.name().toLowerCase(java.util.Locale.ROOT), kind.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private void success(CommandSender sender) {
        if (sender instanceof Player p) {
            m.core.sounds().play(p, "success");
        }
    }

    private void main(Runnable action) {
        m.core.scheduler().ensureMain("crates:admin", action);
    }
}
