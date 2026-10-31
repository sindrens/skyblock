package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.teleport.TeleportOptions;
import net.skybornmc.skyblock.common.teleport.TeleportReason;
import net.skybornmc.skyblock.common.teleport.TeleportService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Optional steps O1-O9 (TUTORIAL.md §3.1, TU6): counted from game events after the core tutorial, each paid once per
 * account ({@code optional.reward-crystals}, TQ8) in one transaction with its "done" flag, offered by a chat tip the first
 * time the player reaches its place (TQ26, the {@code tips} toggle in {@code /settings}), listed in {@code /tutorial}
 * with Show me (teleport, camera, hint). Main thread, except the database work.
 */
final class OptionalSteps {

    static final String SETTING_TIPS = "tips";
    static final String TX_REASON = "tutorial_optional";

    private final TutorialModule module;
    private final Core core;
    private final Set<String> inFlight = new HashSet<>();
    private volatile OptionalStep.Parsed parsed = new OptionalStep.Parsed(List.of(), List.of());
    private Scheduler.Task task;
    private List<OptionalStep> visible = List.of();
    private long visibleAt;

    OptionalSteps(TutorialModule module) {
        this.module = module;
        this.core = module.core();
        rules();
    }

    void rules() {
        TutorialConfig c = module.config();
        parsed = OptionalStep.parse(c.optionalSteps, c.optionalReward);
        visibleAt = 0;
        parsed.problems().forEach(p -> core.plugin().getLogger().warning("tutorial.yml → " + p));
    }

    void start() {
        stop();
        int every = module.config().optionalCheckTicks;
        task = core.scheduler().repeat("tutorial-optional", every, every, this::tick);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** The steps players see: enabled, and their feature exists ({@code if-command}); re-checked every 5 s. */
    List<OptionalStep> visible() {
        long now = System.currentTimeMillis();
        if (now - visibleAt > 5000) {
            visible = parsed.steps().stream().filter(s -> s.enabled() && (s.ifCommand() == null
                    || Bukkit.getCommandMap().getCommand(s.ifCommand()) != null)).toList();
            visibleAt = now;
        }
        return visible;
    }

    Optional<OptionalStep> step(String id) {
        return visible().stream().filter(s -> s.id().equals(id)).findFirst();
    }

    /** True when this player's optional steps count now (module on, core tutorial finished or during-core). */
    boolean counts(Progress p) {
        return p != null && module.config().enabled && module.config().optionalEnabled
                && OptionalRules.counts(p.state, module.config().optionalDuringCore);
    }

    private Progress counting(Player player) {
        Progress p = module.engine().progress(player.getUniqueId());
        return counts(p) ? p : null;
    }

    // ================================================================== signals

    void menuOpened(Player player, String menuId) {
        Progress p = counting(player);
        if (p == null) {
            return;
        }
        for (OptionalStep s : visible()) {
            if (s.kind() == OptionalStep.Kind.MENU && !isDone(p, s) && s.args().stream().anyMatch(a -> a.equalsIgnoreCase(menuId))) {
                complete(player, p, s);
            }
        }
    }

    void npcClicked(Player player, String npcId, String role) {
        Progress p = counting(player);
        if (p == null) {
            return;
        }
        for (OptionalStep s : visible()) {
            if (isDone(p, s)) {
                continue;
            }
            if (s.kind() == OptionalStep.Kind.NPC && role != null && s.args().contains(role.toLowerCase(java.util.Locale.ROOT))) {
                complete(player, p, s);
            } else if (s.kind() == OptionalStep.Kind.VISIT) {
                for (int i = 0; i < s.args().size(); i++) {
                    Optional<TargetSpec> spec = TargetSpec.parse(s.args().get(i));
                    boolean hit = spec.map(t -> switch (t) {
                        case TargetSpec.Npc n -> n.id().equalsIgnoreCase(npcId);
                        case TargetSpec.Role r -> r.role().equalsIgnoreCase(role);
                        default -> false;
                    }).orElse(false);
                    if (hit) {
                        visit(player, p, s, i);
                    }
                }
            }
        }
    }

    void blockBroken(Player player, Block block) {
        Progress p = counting(player);
        if (p == null || doneCount(p) >= visible().size()) {
            return;
        }
        String id = block.getType().getKey().getKey();
        for (OptionalStep s : visible()) {
            if (s.kind() != OptionalStep.Kind.BREAK || isDone(p, s) || !s.args().getFirst().equalsIgnoreCase(id)) {
                continue;
            }
            if (s.islandOnly() && !module.targets().onOwnIsland(player, true)) {
                continue;
            }
            OptionalRules.Entry e = entry(p, s.id());
            e.value = OptionalRules.addCount(e.value, 1, s.needed());
            if (OptionalRules.complete(s, e)) {
                complete(player, p, s);
            } else {
                saveValue(player.getUniqueId(), s.id(), e.value);
                module.msg().actionBar(player, "optional.progress", Msg.c("step", name(player, s)),
                        Msg.number("count", e.value), Msg.number("needed", s.needed()));
            }
        }
    }

    /** Places (visit, near) and first-visit tips, every {@code optional.check-ticks}. */
    private void tick() {
        if (!module.config().enabled || !module.config().optionalEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        List<OptionalStep> steps = visible();
        tickTargets = new java.util.HashMap<>();
        try {
            tickPlayers(steps, now);
        } finally {
            tickTargets = null;
        }
    }

    private void tickPlayers(List<OptionalStep> steps, long now) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Progress p = counting(player);
            if (p == null || module.cameras().running(player.getUniqueId())) {
                continue;
            }
            for (OptionalStep s : steps) {
                if (isDone(p, s)) {
                    continue;
                }
                if (s.kind() == OptionalStep.Kind.VISIT || s.kind() == OptionalStep.Kind.NEAR) {
                    for (int i = 0; i < s.args().size(); i++) {
                        if (near(player, s.args().get(i), s.radius())) {
                            visit(player, p, s, i);
                        }
                    }
                }
            }
            tips(player, p, steps, now);
        }
    }

    private void visit(Player player, Progress p, OptionalStep s, int index) {
        if (isDone(p, s)) {
            return;
        }
        if (s.kind() == OptionalStep.Kind.NEAR) {
            complete(player, p, s);
            return;
        }
        OptionalRules.Entry e = entry(p, s.id());
        if (OptionalRules.visited(e.value, index)) {
            return;
        }
        e.value = OptionalRules.visit(e.value, index);
        if (OptionalRules.complete(s, e)) {
            complete(player, p, s);
        } else {
            saveValue(player.getUniqueId(), s.id(), e.value);
            module.msg().actionBar(player, "optional.progress", Msg.c("step", name(player, s)),
                    Msg.number("count", OptionalRules.progress(s, e)), Msg.number("needed", s.needed()));
            core.sounds().play(player, "click");
        }
    }

    /**
     * Targets resolved during the running {@link #tick()} (PERF6): each visit/near/tip target ({@code npc:}, {@code role:},
     * {@code build:} spec parse + NPC directory lookup) is resolved once per check instead of once per online player and
     * step. Null outside a tick, so other callers always resolve fresh.
     */
    private Map<String, Optional<Location>> tickTargets;

    private boolean near(Player player, String spec, double radius) {
        Optional<Location> at = tickTargets == null ? module.targets().resolve(spec, null)
                : tickTargets.computeIfAbsent(spec, k -> module.targets().resolve(k, null));
        return at.isPresent() && at.get().getWorld() != null && at.get().getWorld().equals(player.getWorld())
                && at.get().distanceSquared(player.getLocation()) <= radius * radius;
    }

    // ================================================================== tips (TQ26)

    private void tips(Player player, Progress p, List<OptionalStep> steps, long now) {
        if (!module.config().optionalTips || !tipsOn(player)) {
            return;
        }
        long cooldown = module.config().tipCooldownMinutes * 60_000L;
        if (p.lastTip == 0) {
            p.lastTip = now; // the first tip waits one cooldown after joining or finishing
            return;
        }
        for (OptionalStep s : steps) {
            if (s.tip() == OptionalStep.Tip.NONE || !OptionalRules.tipDue(p.optional.get(s.id()), true, p.lastTip, now, cooldown)) {
                continue;
            }
            if (!atTipPlace(player, s)) {
                continue;
            }
            p.lastTip = now;
            OptionalRules.Entry e = entry(p, s.id());
            e.tippedAt = now;
            UUID id = player.getUniqueId();
            core.database().run(c -> module.store().markTipped(c, id, s.id(), now)).exceptionally(err -> fail("tip", err));
            module.engine().optionalEvent(player, s.id(), "opt_tip");
            module.msg().send(player, "optional.tip", Msg.c("step", name(player, s)), Msg.c("tip", text(player, s, "tip")),
                    Msg.number("crystals", s.reward()));
            core.sounds().play(player, "notify");
            return; // one tip at a time
        }
    }

    private boolean atTipPlace(Player player, OptionalStep s) {
        return switch (s.tip()) {
            case NONE -> false;
            case ANY -> true;
            case ISLAND -> module.targets().onOwnIsland(player, false);
            case DEPTHS -> module.targets().inDepths(player, false);
            case SPAWN -> player.getWorld().getName().equalsIgnoreCase(module.config().spawnWorld);
            case NEAR -> s.firstTarget() != null && near(player, s.firstTarget(), module.config().tipRadius);
        };
    }

    boolean tipsOn(Player player) {
        return core.services().optional(PlayerSettings.class).map(ps -> ps.get(player, SETTING_TIPS)).orElse(true);
    }

    // ================================================================== complete (atomic reward)

    /**
     * Done (TQ8): {@code done_at} and the crystals in one transaction (idempotency key
     * {@code tutorial_optional:<uuid>:<id>}); a step paid before is marked done without paying again.
     */
    void complete(Player player, Progress p, OptionalStep s) {
        UUID id = player.getUniqueId();
        String flight = id + ":" + s.id();
        if (isDone(p, s) || !inFlight.add(flight)) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID profile = ProfileService.of(core.services()).profileKey(player);
        Optional<EconomyService> economy = core.services().optional(EconomyService.class);
        boolean pay = s.reward() > 0 && economy.isPresent();
        // 1 = done and paid, 0 = done without pay, -1 = it was done before (nothing to show)
        CompletableFuture<Integer> done;
        if (pay) {
            Transaction tx = Transaction.deposit(AccountRef.player(profile), Currency.CRYSTALS, s.reward(), TX_REASON)
                    .actor(Actor.system("tutorial")).reference("tutorial_optional:" + s.id())
                    .idempotencyKey("tutorial_optional:" + id + ":" + s.id()).detail("target_name", player.getName());
            done = economy.get().executeBatch(List.of(tx), conn -> module.store().completeOptional(conn, id, s.id(), now, true))
                    .thenCompose(result -> {
                        if (result.ok()) {
                            return CompletableFuture.completedFuture(1);
                        }
                        if (result.status() == TxResult.Status.DUPLICATE) {
                            return CompletableFuture.completedFuture(-1); // done before (another server or tick)
                        }
                        return CompletableFuture.failedFuture(new IllegalStateException("optional reward " + result.status()));
                    });
        } else {
            done = core.database().transaction(conn -> module.store().completeOptional(conn, id, s.id(), now, false)).thenApply(ok -> ok ? 0 : -1);
        }
        done.whenComplete((outcome, error) -> core.scheduler().main("tutorial-optional-done", () -> {
            inFlight.remove(flight);
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Tutorial: optional step " + s.id() + " for " + player.getName() + " failed",
                        Database.cause(error));
                return; // not done; the next event tries again
            }
            OptionalRules.Entry e = entry(p, s.id());
            if (e.doneAt == 0) {
                e.doneAt = now;
            }
            if (outcome == null || outcome < 0) {
                return;
            }
            e.rewarded = outcome == 1;
            module.engine().optionalEvent(player, s.id(), "opt_done");
            core.audit().logPlayer(AuditCategory.TUTORIAL, "tutorial_optional", Actor.player(player), id, player.getName(),
                    e.rewarded ? s.reward() : null, player.getLocation(),
                    Map.of("step", s.id(), "paid", e.rewarded, "crystals", e.rewarded ? s.reward() : 0, "profile", profile.toString()));
            if (!player.isOnline()) {
                return;
            }
            if (module.msg().has("optional." + s.id() + ".done")) {
                module.msg().send(player, "optional." + s.id() + ".done");
            }
            module.msg().send(player, e.rewarded ? "optional.done-reward" : "optional.done", Msg.c("step", name(player, s)),
                    Msg.number("crystals", s.reward()), Msg.number("done", doneCount(p)), Msg.number("total", visible().size()));
            core.sounds().play(player, "success");
        }));
    }

    // ================================================================== Show me (menu)

    /** Show me: the hint, then the teleport ({@code goto}) and the camera ({@code camera}) when the step has them. */
    void showMe(Player player, OptionalStep s) {
        module.msg().send(player, "optional.show", Msg.c("step", name(player, s)), Msg.c("hint", text(player, s, "hint")));
        Optional<Location> to = s.gotoSpec() == null ? Optional.empty() : module.targets().resolve(s.gotoSpec(), null);
        Runnable camera = () -> {
            if (s.camera() != null && player.isOnline()) {
                module.cameras().play(player, s.camera(), null);
            }
        };
        if (to.isPresent() && core.services().optional(TeleportService.class).isPresent()) {
            Location dest = to.get().clone();
            if (TargetSpec.parse(s.gotoSpec()).filter(t -> !(t instanceof TargetSpec.Point)).isPresent()) {
                // An NPC: stand 2.5 blocks in front of it, facing it.
                dest = dest.add(dest.getDirection().setY(0).normalize().multiply(2.5));
                dest.setYaw(dest.getYaw() + 180f);
            }
            core.services().get(TeleportService.class).request(player, dest, TeleportReason.SPAWN, TeleportOptions.builder()
                    .destination(module.msg().component(player, "take-me-there.destination"))
                    .cause(PlayerTeleportEvent.TeleportCause.PLUGIN)
                    .build());
            if (s.camera() != null) {
                core.scheduler().later("tutorial-optional-camera", 30, camera);
            }
        } else {
            camera.run();
        }
    }

    // ================================================================== helpers

    boolean isDone(Progress p, OptionalStep s) {
        OptionalRules.Entry e = p.optional.get(s.id());
        return e != null && e.done();
    }

    int doneCount(Progress p) {
        int n = 0;
        for (OptionalStep s : visible()) {
            if (isDone(p, s)) {
                n++;
            }
        }
        return n;
    }

    private static OptionalRules.Entry entry(Progress p, String id) {
        return p.optional.computeIfAbsent(id, k -> new OptionalRules.Entry());
    }

    private void saveValue(UUID player, String step, int value) {
        core.database().run(c -> module.store().setOptionalValue(c, player, step, value)).exceptionally(e -> fail("progress", e));
    }

    private Void fail(String what, Throwable e) {
        core.plugin().getLogger().log(Level.WARNING, "Tutorial: saving an optional step (" + what + ") failed", Database.cause(e));
        return null;
    }

    net.kyori.adventure.text.Component name(Player viewer, OptionalStep s) {
        return text(viewer, s, "name");
    }

    net.kyori.adventure.text.Component text(Player viewer, OptionalStep s, String part) {
        String key = "optional." + s.id() + "." + part;
        return module.msg().has(key) ? module.msg().component(viewer, key) : net.kyori.adventure.text.Component.text(s.id());
    }
}
