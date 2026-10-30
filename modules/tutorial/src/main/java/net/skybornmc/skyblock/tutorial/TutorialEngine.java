package net.skybornmc.skyblock.tutorial;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tutorial.TutorialGate;
import net.skybornmc.skyblock.common.tutorial.TutorialService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The tutorial's state machine at runtime (TUTORIAL.md §3, §8, §9): loads each account's progress on join, takes "done"
 * signals from {@link TutorialListener}, saves every change, opens the locks ({@link TutorialGate}) and pays the finish
 * reward in one transaction with the finished flag. Main thread, except the database work.
 */
final class TutorialEngine implements TutorialService, TutorialGate {

    static final String PERM_BYPASS = "skyblock.tutorial.bypass";

    private final TutorialModule module;
    private final Core core;
    private final Map<UUID, Progress> online = new ConcurrentHashMap<>();
    private final Set<UUID> finishing = new HashSet<>();
    private final Map<UUID, Long> lockedChat = new HashMap<>();
    /** Pending "arrive" completions (time-based step until the TU3 camera exists). */
    private final Map<UUID, net.skybornmc.skyblock.common.util.Scheduler.Task> arriveTasks = new HashMap<>();
    private volatile TutorialFlow flow;
    private volatile Map<String, String> gates = Map.of();

    TutorialEngine(TutorialModule module) {
        this.module = module;
        this.core = module.core();
        rules();
    }

    /** (Re)reads flow.steps and gates; problems go to the log. */
    void rules() {
        TutorialConfig c = module.config();
        TutorialFlow f = TutorialFlow.parse(c.steps);
        GateRules.Parsed g = GateRules.parse(c.gates, f);
        f.problems().forEach(p -> core.plugin().getLogger().warning("tutorial.yml → " + p));
        g.problems().forEach(p -> core.plugin().getLogger().warning("tutorial.yml → " + p));
        flow = f;
        gates = g.required();
    }

    TutorialFlow flow() {
        return flow;
    }

    Map<String, String> gates() {
        return gates;
    }

    private ModuleMessages msg() {
        return module.msg();
    }

    /** The loaded progress of an online player, or {@code null}. */
    Progress progress(UUID player) {
        return online.get(player);
    }

    java.util.Collection<Progress> loaded() {
        return online.values();
    }

    // ================================================================== loading

    /** Loads (or creates) the account on join; starts the tutorial for new players and resumes a running one. */
    void load(Player player) {
        UUID id = player.getUniqueId();
        int version = module.config().flowVersion;
        core.database().async(c -> module.store().loadOrCreate(c, id, version)).whenComplete((p, error) -> core.scheduler().main("tutorial-load", () -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Tutorial: loading " + player.getName() + " failed; locks stay closed until a rejoin",
                        Database.cause(error));
                return;
            }
            if (!player.isOnline()) {
                return;
            }
            online.put(id, p);
            joined(player, p);
        }));
    }

    private void joined(Player player, Progress p) {
        long now = System.currentTimeMillis();
        p.lastActivity = now;
        if (!module.config().enabled) {
            return;
        }
        if (p.state == State.NEW) {
            if (player.hasPermission(PERM_BYPASS)) {
                msg().send(player, "start.staff-offer"); // TQ28: offered, never forced on staff
                return;
            }
            start(player);
            return;
        }
        if (p.state != State.ACTIVE) {
            return;
        }
        boolean complete = StepRules.resume(p, flow, module.config().flowVersion, now);
        save(p);
        event(player, p, "resume");
        if (complete) {
            finish(player, p);
            return;
        }
        module.guides().show(player, p);
        msg().title(player, "resume.title");
        if (module.config().rejoinTeleport && module.targets().hasPlace(p.step)) {
            msg().actionBar(player, "resume.sneak-hint");
            core.scheduler().later("tutorial-rejoin", module.config().rejoinDelayTicks, () -> {
                if (!player.isOnline() || online.get(player.getUniqueId()) != p || p.state != State.ACTIVE) {
                    return;
                }
                if (player.isSneaking()) {
                    msg().send(player, "resume.stay");
                    return;
                }
                module.targets().takeMeThere(player, p, false);
            });
        }
        enterStep(player, p, false);
    }

    void quit(Player player) {
        Progress p = online.remove(player.getUniqueId());
        cancelArrive(player.getUniqueId());
        lockedChat.remove(player.getUniqueId());
        module.guides().hide(player);
        if (p != null && p.state == State.ACTIVE) {
            event(player, p, "quit");
        }
    }

    /** Module stop: hide every guide (progress is saved on every change already). */
    void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            module.guides().hide(p);
        }
        arriveTasks.values().forEach(net.skybornmc.skyblock.common.util.Scheduler.Task::cancel);
        arriveTasks.clear();
        online.clear();
    }

    // ================================================================== TutorialGate

    @Override
    public boolean open(Player player, String gate) {
        if (!module.config().enabled) {
            return true;
        }
        Progress p = online.get(player.getUniqueId());
        return GateRules.open(gate, gates, flow, p == null ? null : p.state, p == null ? Set.of() : p.doneSet(),
                player.hasPermission(PERM_BYPASS));
    }

    @Override
    public void tellLocked(Player player, String gate) {
        Progress p = online.get(player.getUniqueId());
        String need = gate == null ? null : gates.get(gate);
        core.sounds().play(player, "error");
        if (p == null) {
            msg().send(player, "locked.loading");
            return;
        }
        boolean finish = need == null || need.equals(Steps.FINISH);
        String key = finish ? "finish" : "step";
        var placeholders = finish ? new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[0]
                : new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{
                        Msg.number("number", flow.number(need)), Msg.c("step", stepName(player, need))};
        msg().actionBar(player, "locked.action-bar-" + key, placeholders);
        long now = System.currentTimeMillis();
        Long last = lockedChat.get(player.getUniqueId());
        if (last == null || now - last >= module.config().lockedChatCooldownSeconds * 1000L) {
            lockedChat.put(player.getUniqueId(), now);
            msg().send(player, "locked." + key, placeholders);
        }
    }

    // ================================================================== TutorialService

    @Override
    public Optional<State> state(UUID player) {
        Progress p = online.get(player);
        return p == null ? Optional.empty() : Optional.of(p.state);
    }

    @Override
    public Optional<String> currentStep(UUID player) {
        Progress p = online.get(player);
        return p == null || p.state != State.ACTIVE ? Optional.empty() : Optional.ofNullable(p.step);
    }

    @Override
    public void start(Player player) {
        Progress p = online.get(player.getUniqueId());
        if (p == null || !module.config().enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!StepRules.start(p, flow, now)) {
            return;
        }
        save(p);
        event(player, p, "start");
        audit(player, "tutorial_start", Map.of("step", String.valueOf(p.step)));
        msg().title(player, "start.title");
        msg().send(player, "start.chat");
        module.guides().show(player, p);
        enterStep(player, p, true);
    }

    @Override
    public void openMenu(Player player) {
        core.menus().open(player, new TutorialMenu(module, player.getUniqueId()));
    }

    @Override
    public void openStaffView(Player staff, UUID player, String name, Menu back) {
        core.menus().open(staff, new StaffTutorialMenu(module, player, name).parent(back));
    }

    // ================================================================== signals

    /** A step's "done when" happened for this player (TUTORIAL §3). Ignored when not running or already done. */
    void signal(Player player, String step) {
        Progress p = online.get(player.getUniqueId());
        if (p == null || p.state != State.ACTIVE || !module.config().enabled) {
            return;
        }
        complete(player, p, step);
    }

    /** Adds to a counted step (crops, mined blocks); completes it at {@code needed}. */
    void count(Player player, String step, int amount, int needed) {
        Progress p = online.get(player.getUniqueId());
        if (p == null || p.state != State.ACTIVE || !module.config().enabled) {
            return;
        }
        boolean reached = StepRules.count(p, flow, step, amount, needed, module.config().earlySteps);
        Integer value = p.counts.get(step);
        if (value != null) {
            UUID id = player.getUniqueId();
            int v = value;
            core.database().run(c -> module.store().setCount(c, id, step, v)).exceptionally(e -> logFailure("count", e));
        }
        p.lastActivity = System.currentTimeMillis();
        p.idleReminded = false;
        if (reached) {
            complete(player, p, step);
        } else if (step.equals(p.step)) {
            module.guides().refresh(player, p);
        }
    }

    private void complete(Player player, Progress p, String step) {
        long now = System.currentTimeMillis();
        long inStep = now - p.stepStartedAt;
        StepRules.Outcome outcome = StepRules.done(p, flow, step, module.config().earlySteps, now);
        if (outcome == StepRules.Outcome.IGNORED) {
            return;
        }
        boolean early = outcome == StepRules.Outcome.EARLY;
        UUID id = player.getUniqueId();
        UUID profile = ProfileService.of(core.services()).profileKey(player);
        core.database().transaction(c -> {
            module.store().markDone(c, id, step, now, early, profile);
            module.store().save(c, p);
            module.store().event(c, id, step, "done", now, bedrock(player), early ? 0 : inStep);
            return null;
        }).exceptionally(e -> logFailure("step", e));
        audit(player, "tutorial_step", Map.of("step", step, "early", early, "millis", early ? 0 : inStep));
        if (early) {
            msg().actionBar(player, "step-done.early", Msg.c("step", stepName(player, step)));
            core.sounds().play(player, "success");
            return;
        }
        if (step.equals(Steps.ARRIVE)) {
            cancelArrive(id);
        }
        String doneKey = "steps." + step + ".done";
        if (msg().has(doneKey)) {
            msg().actionBar(player, doneKey);
        }
        msg().send(player, "step-done.chat", Msg.c("step", stepName(player, step)), Msg.number("number", flow.number(step)),
                Msg.number("total", flow.size()));
        core.sounds().play(player, "unlock");
        if (outcome == StepRules.Outcome.COMPLETE) {
            finish(player, p);
            return;
        }
        enterStep(player, p, true);
    }

    /** Shows a step's start (title, hint) and runs what the step needs at its start. */
    void enterStep(Player player, Progress p, boolean announce) {
        String step = p.step;
        if (step == null) {
            return;
        }
        if (announce && msg().has("steps." + step + ".title.title")) {
            msg().title(player, "steps." + step + ".title");
        }
        module.guides().refresh(player, p);
        switch (step) {
            case Steps.ARRIVE -> scheduleArrive(player);
            case Steps.CHEST -> module.starter().enterChestStep(player, p);
            case Steps.DEPTHS -> module.listener().checkZone(player);
            case Steps.CROP, Steps.MINE -> {
                // A counter may already be full (counted early): complete at once.
                int needed = step.equals(Steps.CROP) ? module.config().cropCount : module.config().mineCount;
                if (p.count(step) >= needed) {
                    complete(player, p, step);
                }
            }
            default -> {
                // done by events only
            }
        }
    }

    /** The arrival step: a title on the island, done after {@code steps.arrive.seconds} (TU3 replaces it with the camera). */
    private void scheduleArrive(Player player) {
        UUID id = player.getUniqueId();
        cancelArrive(id);
        msg().title(player, "steps.arrive.title");
        core.sounds().play(player, "unlock");
        arriveTasks.put(id, core.scheduler().later("tutorial-arrive", module.config().arriveSeconds * 20L, () -> {
            arriveTasks.remove(id);
            if (player.isOnline()) {
                signal(player, Steps.ARRIVE);
            }
        }));
    }

    private void cancelArrive(UUID id) {
        net.skybornmc.skyblock.common.util.Scheduler.Task t = arriveTasks.remove(id);
        if (t != null) {
            t.cancel();
        }
    }

    // ================================================================== finish (atomic reward)

    /**
     * Finish (TUTORIAL §9.5): {@code active → finished} and the reward (money + crystals, idempotency key
     * {@code tutorial_reward:<uuid>}) in one transaction; the title follows. A player whose reward was paid before (staff
     * reset) finishes without a reward.
     */
    void finish(Player player, Progress p) {
        UUID id = player.getUniqueId();
        if (!finishing.add(id)) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID profile = ProfileService.of(core.services()).profileKey(player);
        TutorialConfig c = module.config();
        boolean pay = !p.rewarded && (c.rewardMoney > 0 || c.rewardCrystals > 0);
        Optional<EconomyService> economy = core.services().optional(EconomyService.class);
        CompletableFuture<Boolean> done;
        if (pay && economy.isPresent()) {
            List<Transaction> txs = new ArrayList<>();
            if (c.rewardMoney > 0) {
                txs.add(Transaction.deposit(AccountRef.player(profile), Currency.MONEY, c.rewardMoney, TxReason.TUTORIAL_REWARD)
                        .actor(Actor.system("tutorial")).reference("tutorial:" + id).idempotencyKey("tutorial_reward:" + id + ":money")
                        .detail("target_name", player.getName()));
            }
            if (c.rewardCrystals > 0) {
                txs.add(Transaction.deposit(AccountRef.player(profile), Currency.CRYSTALS, c.rewardCrystals, TxReason.TUTORIAL_REWARD)
                        .actor(Actor.system("tutorial")).reference("tutorial:" + id).idempotencyKey("tutorial_reward:" + id + ":crystals")
                        .detail("target_name", player.getName()));
            }
            done = economy.get().executeBatch(txs, conn -> module.store().finish(conn, id, now, profile, true))
                    .thenCompose(result -> {
                        if (result.ok()) {
                            return CompletableFuture.completedFuture(true);
                        }
                        if (result.status() == TxResult.Status.DUPLICATE) {
                            // Paid before (idempotency key) or finished elsewhere: finish without paying again.
                            return core.database().transaction(conn -> module.store().finish(conn, id, now, profile, true))
                                    .thenApply(ok -> {
                                        p.rewarded = true;
                                        return ok;
                                    });
                        }
                        return CompletableFuture.failedFuture(new IllegalStateException("reward " + result.status()));
                    })
                    .thenApply(ok -> {
                        if (ok && !p.rewarded) {
                            p.rewarded = true;
                        }
                        return ok;
                    });
        } else {
            boolean rewardedBefore = p.rewarded;
            done = core.database().transaction(conn -> module.store().finish(conn, id, now, profile, rewardedBefore));
            pay = false;
        }
        boolean paid = pay;
        done.whenComplete((ok, error) -> core.scheduler().main("tutorial-finish", () -> {
            finishing.remove(id);
            if (error != null || !Boolean.TRUE.equals(ok)) {
                if (error != null) {
                    core.plugin().getLogger().log(Level.WARNING, "Tutorial: finishing for " + player.getName() + " failed", Database.cause(error));
                }
                if (player.isOnline()) {
                    msg().send(player, "finish.error");
                }
                return; // stays active; the next signal or rejoin tries again
            }
            StepRules.finished(p, now);
            event(player, p, "finish");
            audit(player, "tutorial_finish", Map.of("profile", profile.toString(), "paid", paid));
            if (paid) {
                core.audit().logPlayer(AuditCategory.TUTORIAL, "tutorial_reward", Actor.system("tutorial"), id, player.getName(),
                        c.rewardMoney, null, Map.of("money", c.rewardMoney, "crystals", c.rewardCrystals, "profile", profile.toString()));
            }
            grantTitle(player);
            if (!player.isOnline()) {
                return;
            }
            module.guides().hide(player);
            msg().title(player, "finish.title");
            core.sounds().play(player, "levelup");
            msg().send(player, paid ? "finish.chat-reward" : "finish.chat", Msg.money("money", c.rewardMoney),
                    Msg.number("crystals", c.rewardCrystals), Msg.p("title", c.rewardTitle));
            msg().send(player, "finish.unlocked");
            msg().send(player, "finish.next");
            core.scheduler().later("tutorial-where-next", 40, () -> {
                if (player.isOnline()) {
                    core.menus().open(player, new WhereNextMenu(module));
                }
            });
        }));
    }

    private void grantTitle(Player player) {
        String title = module.config().rewardTitle;
        if (title == null || title.isBlank()) {
            return;
        }
        core.services().optional(net.skybornmc.skyblock.common.cosmetics.Cosmetics.class)
                .ifPresent(c -> c.grantTitle(player.getUniqueId(), title, "tutorial"));
    }

    // ================================================================== player actions from the menu

    /** "I'm stuck": hint again, offer Take me there, and after enough presses a skip (only skippable steps). */
    void stuck(Player player) {
        Progress p = online.get(player.getUniqueId());
        if (p == null || p.state != State.ACTIVE || p.step == null) {
            return;
        }
        String step = p.step;
        boolean canSkip = StepRules.stuck(p, step, module.config().stuckSkipAfter, module.config().stuckSkippable);
        event(player, p, "stuck");
        msg().title(player, msg().has("steps." + step + ".title.title") ? "steps." + step + ".title" : "stuck.title",
                Msg.c("step", stepName(player, step)));
        msg().send(player, "stuck.shown", Msg.c("step", stepName(player, step)), Msg.c("hint", hint(player, step)));
        if (canSkip) {
            msg().send(player, "stuck.can-skip");
        }
        module.guides().refresh(player, p);
    }

    boolean canSkip(Progress p) {
        return p != null && p.step != null && p.stuck.getOrDefault(p.step, 0) >= module.config().stuckSkipAfter
                && module.config().stuckSkippable.contains(p.step);
    }

    /** Player skip of a stuck, skippable step. */
    void skipStuck(Player player) {
        Progress p = online.get(player.getUniqueId());
        if (!canSkip(p)) {
            msg().send(player, "stuck.not-skippable");
            return;
        }
        String step = p.step;
        event(player, p, "skip");
        complete(player, p, step);
    }

    // ================================================================== staff (audited)

    /** Staff skip (no reward). Loads the account when the player is offline. */
    CompletableFuture<Boolean> staffSkip(Actor actor, UUID player, String name) {
        return change(player, p -> StepRules.staffSkip(p, System.currentTimeMillis()), false, "staff_skip").thenApply(changed -> {
            if (changed) {
                core.audit().logPlayer(AuditCategory.TUTORIAL, "tutorial_staff_skip", actor, player, name, null, null, Map.of());
            }
            return changed;
        });
    }

    /** Staff reset: back to the start, locks return, no second reward. */
    CompletableFuture<Boolean> staffReset(Actor actor, UUID player, String name) {
        return change(player, p -> {
            StepRules.reset(p);
            return true;
        }, true, "staff_reset").thenApply(changed -> {
            if (changed) {
                core.audit().logPlayer(AuditCategory.TUTORIAL, "tutorial_staff_reset", actor, player, name, null, null, Map.of());
                Player online = Bukkit.getPlayer(player);
                if (online != null) {
                    module.guides().hide(online);
                    Progress p = this.online.get(player);
                    if (p != null && !online.hasPermission(PERM_BYPASS)) {
                        start(online);
                    }
                }
            }
            return changed;
        });
    }

    /** Staff "jump to step" (support). */
    CompletableFuture<Boolean> staffStep(Actor actor, UUID player, String name, String step) {
        return change(player, p -> StepRules.jump(p, flow, step, System.currentTimeMillis()), true, "staff_step").thenApply(changed -> {
            if (changed) {
                core.audit().logPlayer(AuditCategory.TUTORIAL, "tutorial_staff_step", actor, player, name, null, null, Map.of("step", step));
                Player online = Bukkit.getPlayer(player);
                Progress p = this.online.get(player);
                if (online != null && p != null) {
                    module.guides().show(online, p);
                    enterStep(online, p, true);
                }
            }
            return changed;
        });
    }

    /**
     * Applies a staff change to the online copy (main thread) or a freshly loaded one (offline), then saves it; with
     * {@code clearSteps} the done steps and counters are rewritten from the changed progress. Completes on the main thread.
     */
    private CompletableFuture<Boolean> change(UUID player, java.util.function.Predicate<Progress> edit, boolean clearSteps, String kind) {
        Progress live = online.get(player);
        CompletableFuture<Progress> source = live != null ? CompletableFuture.completedFuture(live)
                : core.database().async(c -> module.store().load(c, player).orElse(null));
        CompletableFuture<Boolean> out = new CompletableFuture<>();
        source.whenComplete((p, error) -> core.scheduler().main("tutorial-staff", () -> {
            if (error != null || p == null) {
                if (error != null) {
                    core.plugin().getLogger().log(Level.WARNING, "Tutorial: staff change failed", Database.cause(error));
                }
                out.complete(false);
                return;
            }
            if (!edit.test(p)) {
                out.complete(false);
                return;
            }
            cancelArrive(player);
            Map<String, Boolean> done = Map.copyOf(p.done);
            Map<String, Integer> counts = Map.copyOf(p.counts);
            long now = System.currentTimeMillis();
            core.database().transaction(c -> {
                if (clearSteps) {
                    module.store().clearSteps(c, player);
                    for (Map.Entry<String, Boolean> e : done.entrySet()) {
                        module.store().markDone(c, player, e.getKey(), now, e.getValue(), null);
                    }
                    for (Map.Entry<String, Integer> e : counts.entrySet()) {
                        module.store().setCount(c, player, e.getKey(), e.getValue());
                    }
                }
                module.store().save(c, p);
                module.store().event(c, player, p.step, kind, now, false, 0);
                return null;
            }).whenComplete((ignored, e2) -> core.scheduler().main("tutorial-staff-saved", () -> {
                if (e2 != null) {
                    core.plugin().getLogger().log(Level.WARNING, "Tutorial: saving a staff change failed", Database.cause(e2));
                    out.complete(false);
                    return;
                }
                Player on = Bukkit.getPlayer(player);
                if (on != null && p.state.complete()) {
                    module.guides().hide(on);
                    msg().send(on, "staff.skipped-notice");
                }
                out.complete(true);
            }));
        }));
        return out;
    }

    /** Loads an offline account for staff views (completes on the main thread; empty when the account has no row). */
    CompletableFuture<Optional<Progress>> lookup(UUID player) {
        Progress live = online.get(player);
        if (live != null) {
            return CompletableFuture.completedFuture(Optional.of(live));
        }
        CompletableFuture<Optional<Progress>> out = new CompletableFuture<>();
        core.database().async(c -> module.store().load(c, player)).whenComplete((p, e) -> core.scheduler().main("tutorial-lookup",
                () -> out.complete(e != null || p == null ? Optional.empty() : p)));
        return out;
    }

    // ================================================================== helpers

    void save(Progress p) {
        core.database().run(c -> module.store().save(c, p)).exceptionally(e -> logFailure("save", e));
    }

    void event(Player player, Progress p, String kind) {
        UUID id = player.getUniqueId();
        String step = p.step;
        long now = System.currentTimeMillis();
        long inStep = p.stepStartedAt > 0 ? now - p.stepStartedAt : 0;
        boolean be = bedrock(player);
        core.database().run(c -> module.store().event(c, id, step, kind, now, be, inStep)).exceptionally(e -> logFailure("event", e));
    }

    private Void logFailure(String what, Throwable e) {
        core.plugin().getLogger().log(Level.WARNING, "Tutorial: saving (" + what + ") failed", Database.cause(e));
        return null;
    }

    void audit(Player player, String action, Map<String, Object> details) {
        core.audit().logPlayer(AuditCategory.TUTORIAL, action, Actor.player(player), player.getUniqueId(), player.getName(), null,
                player.getLocation(), details);
    }

    boolean bedrock(Player player) {
        return core.bedrock().isBedrock(player.getUniqueId());
    }

    /** A step's short name for this viewer ({@code steps.<id>.name}). */
    Component stepName(Player viewer, String step) {
        return msg().has("steps." + step + ".name") ? msg().component(viewer, "steps." + step + ".name") : Component.text(step);
    }

    Component hint(Player viewer, String step) {
        return msg().has("steps." + step + ".hint") ? msg().component(viewer, "steps." + step + ".hint") : Component.empty();
    }

    /** Plain objective for the scoreboard line ({@code steps.<id>.objective}). */
    String objective(String step) {
        String raw = core.messages().raw(TutorialModule.ID, "steps." + step + ".objective");
        return raw == null ? step : raw;
    }
}
