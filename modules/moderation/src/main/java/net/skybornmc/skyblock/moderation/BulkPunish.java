package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bulk punish (spec 13.8): one reason for up to {@code bulk.max} players, from {@code /pbulk} or the Bulk button of
 * {@code /p}. Always a confirm with the count and what each player gets (whatever {@code menu.confirm} says). Each player
 * goes through the normal pipeline on their own (own ladder step, probation, active punishments, approval per player);
 * skipped players are listed with the reason. What is applied is one audit group ({@code source bulk:<group>} on every
 * punishment plus one {@code bulk_punish} entry), one Discord embed, one staff line and one [Undo all].
 */
final class BulkPunish {

    private final ModerationModule module;

    BulkPunish(ModerationModule module) {
        this.module = module;
    }

    /** One player of a plan: the loaded target ({@code null} = unknown name) and what the preview says. */
    record Entry(String name, PunishMenus.Target target, BulkSelection.Line line) {
    }

    /** A previewed bulk action, waiting for the confirm. */
    record Plan(String reasonId, String reasonName, Templates.Template template, PunishConfig.WarnButton warnButton,
                List<Entry> entries, String group) {

        List<BulkSelection.Line> lines() {
            return entries.stream().map(Entry::line).toList();
        }
    }

    /** True if {@code reasonId} is a reason (ladder) or a warn button. */
    boolean knownReason(String reasonId) {
        return module.templates().template(reasonId).isPresent() || module.templates().warnButton(reasonId).isPresent();
    }

    /**
     * Builds the plan (names resolved, players loaded, one preview each through the pipeline). Main thread in, main
     * thread out; {@code null} when the reason is unknown.
     */
    CompletableFuture<Plan> plan(Player staff, String reasonId, List<String> names) {
        Optional<Templates.Template> template = module.templates().template(reasonId);
        Optional<PunishConfig.WarnButton> button = template.isPresent() ? Optional.empty() : module.templates().warnButton(reasonId);
        if (template.isEmpty() && button.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Optional<PunishMenus.Target>>> loads = new ArrayList<>();
        for (String name : names) {
            loads.add(module.target(name).exceptionally(e -> Optional.empty()));
        }
        String reasonName = template.map(t -> module.reasonName(staff, t))
                .orElseGet(() -> module.warnButtonText(staff, button.get(), "name"));
        String group = BulkSelection.group(System.currentTimeMillis(), ThreadLocalRandom.current().nextInt());
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenApplyAsync(v -> {
            List<Entry> entries = new ArrayList<>();
            java.util.Set<UUID> seen = new java.util.HashSet<>();
            for (int i = 0; i < names.size(); i++) {
                Optional<PunishMenus.Target> t = loads.get(i).join();
                if (t.isEmpty()) {
                    entries.add(new Entry(names.get(i), null, new BulkSelection.Line(names.get(i), BulkSelection.Kind.SKIP,
                            null, BulkSelection.UNKNOWN)));
                    continue;
                }
                if (!seen.add(t.get().uuid())) {
                    continue; // two spellings of one player
                }
                entries.add(new Entry(t.get().name(), t.get(), preview(staff, t.get(), template.orElse(null), button.orElse(null))));
            }
            return new Plan(template.map(Templates.Template::id).orElseGet(() -> button.get().id()), reasonName,
                    template.orElse(null), button.orElse(null), List.copyOf(entries), group);
        }, runnable -> Bukkit.getScheduler().runTask(module.core().plugin(), runnable));
    }

    /** What this player would get right now (same pipeline and ladder logic as the /p buttons). */
    private BulkSelection.Line preview(Player staff, PunishMenus.Target t, Templates.Template template, PunishConfig.WarnButton button) {
        PunishDecision.Verdict v;
        Templates.Step planned;
        if (template != null) {
            template = t.adapt(template); // P7: island steps this player can't get are replaced
            int previous = t.previous(template.id(), template.cutoff(System.currentTimeMillis()));
            planned = template.stepFor(previous);
            v = module.service().preview(staff, t.uuid(), t.name(), planned, false, t.longestActive(Punishment.Type.MUTE),
                    t.longestActive(Punishment.Type.BAN), template.id(), t.recent(template.id()),
                    module.escalation(t, template, previous, false));
        } else {
            planned = new Templates.Step(Punishment.Type.WARN, null, false, button.weight());
            v = module.service().preview(staff, t.uuid(), t.name(), planned, false, null, null, button.id(), t.recent(button.id()));
        }
        return BulkSelection.line(t.name(), v, planned);
    }

    // ================================================================== texts

    /** "Mute 30m", "sent for approval: Ban permanent", "skipped: already muted for longer". */
    String lineText(Player viewer, BulkSelection.Line l) {
        PunishmentService service = module.service();
        return switch (l.kind()) {
            case APPLY -> service.text(viewer, "bulk.kind.apply", Msg.p("action", service.stepText(viewer, l.step())));
            case REQUEST -> service.text(viewer, "bulk.kind.request", Msg.p("action", service.stepText(viewer, l.step())));
            case SKIP -> service.text(viewer, "bulk.kind.skip", Msg.p("why", module.messages().has("bulk.skip." + l.reason())
                    ? service.text(viewer, "bulk.skip." + l.reason()) : String.valueOf(l.reason())));
        };
    }

    /** Sends the plan in chat (header with the count, one line per player). */
    void sendPlan(Player staff, Plan plan) {
        BulkSelection.Summary s = BulkSelection.summary(plan.lines());
        module.messages().send(staff, "bulk.plan-header", Msg.p("reason", plan.reasonName()), Msg.number("count", plan.entries().size()),
                Msg.number("apply", s.apply()), Msg.number("request", s.request()), Msg.number("skip", s.skip()));
        for (BulkSelection.Line l : plan.lines()) {
            module.messages().send(staff, "bulk.plan-line", Msg.p("player", l.name()), Msg.p("what", lineText(staff, l)));
        }
    }

    // ================================================================== confirm

    /**
     * The confirm (always, spec 13.8): a 3-row Bedrock-safe menu, "Punish 5 players for Spam?", the Confirm lore lists
     * every player with what they get. {@code parent} = the menu to go back to on Cancel ({@code null} = close).
     */
    void confirm(Player staff, Plan plan, Menu parent) {
        sendPlan(staff, plan);
        BulkSelection.Summary s = BulkSelection.summary(plan.lines());
        if (s.apply() + s.request() == 0) {
            module.messages().send(staff, "bulk.nothing");
            module.core().sounds().play(staff, "error");
            return;
        }
        Material icon = plan.template() != null ? plan.template().icon() : plan.warnButton().icon();
        var shown = module.core().menus().item(icon, module.core().menus().text(staff, PunishMenus.MODULE, "bulk.confirm.name",
                Msg.p("reason", plan.reasonName()), Msg.number("count", s.apply() + s.request())), List.of());
        ConfirmMenu menu = new ConfirmMenu(
                p -> module.core().menus().text(p, PunishMenus.MODULE, "bulk.confirm.title", Msg.number("count", s.apply() + s.request())),
                shown,
                p -> {
                    List<Component> lines = new ArrayList<>(module.core().menus().lines(p, PunishMenus.MODULE, "bulk.confirm.details",
                            Msg.p("reason", plan.reasonName()), Msg.number("apply", s.apply()), Msg.number("request", s.request()),
                            Msg.number("skip", s.skip())));
                    for (BulkSelection.Line l : plan.lines()) {
                        lines.addAll(module.core().menus().lines(p, PunishMenus.MODULE, "bulk.confirm.line", Msg.p("player", l.name()),
                                Msg.p("what", lineText(p, l))));
                    }
                    return lines;
                },
                p -> {
                    if (p.hasPermission(ModerationModule.PERM_BULK)) {
                        apply(p, plan);
                    } else {
                        module.messages().send(p, "error.no-permission-menu");
                    }
                },
                p -> module.messages().send(p, "bulk.cancelled"));
        if (parent != null) {
            parent.openChild(menu);
        } else {
            module.core().menus().open(staff, menu);
        }
    }

    // ================================================================== apply

    /** One player's outcome after the confirm. */
    record Outcome(String name, BulkSelection.Line line, Punishment punishment) {
    }

    /**
     * Applies the plan: every player the preview did not skip goes through {@link PunishmentService#issue} on their own
     * (the decision is made again, atomically, so a change since the preview is respected). Then one summary.
     */
    void apply(Player staff, Plan plan) {
        List<CompletableFuture<Outcome>> runs = new ArrayList<>();
        for (Entry e : plan.entries()) {
            if (e.target() == null || e.line().kind() == BulkSelection.Kind.SKIP) {
                runs.add(CompletableFuture.completedFuture(new Outcome(e.name(), e.line(), null)));
                continue;
            }
            CompletableFuture<PunishmentStore.IssueResult> f = plan.template() != null
                    ? module.punishWithTemplate(staff, e.target(), plan.template(), false, false, false, plan.group())
                    : module.punishWithWarnButton(staff, e.target(), plan.warnButton(), false, false, plan.group());
            runs.add(f.thenApply(r -> outcome(e, r)).exceptionally(err -> new Outcome(e.name(),
                    new BulkSelection.Line(e.name(), BulkSelection.Kind.SKIP, e.line().step(), "failed"), null)));
        }
        CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)).thenRun(() -> Bukkit.getScheduler().runTask(
                module.core().plugin(), () -> finish(staff, plan, runs.stream().map(CompletableFuture::join).toList())));
    }

    private static Outcome outcome(Entry e, PunishmentStore.IssueResult r) {
        if (r == null) {
            return new Outcome(e.name(), new BulkSelection.Line(e.name(), BulkSelection.Kind.SKIP, e.line().step(), "failed"), null);
        }
        BulkSelection.Line line = BulkSelection.line(e.name(), r.verdict(), e.line().step());
        return new Outcome(e.name(), line, r.verdict().applied() ? r.punishment() : null);
    }

    /** Summary to the staff member, one staff line, one audit entry, one Discord embed, [Undo all]. Main thread. */
    private void finish(Player staff, Plan plan, List<Outcome> outcomes) {
        List<BulkSelection.Line> lines = outcomes.stream().map(Outcome::line).toList();
        BulkSelection.Summary s = BulkSelection.summary(lines);
        List<Punishment> applied = outcomes.stream().map(Outcome::punishment).filter(java.util.Objects::nonNull).toList();
        Duration window = module.service().extras().undoWindow();
        Component undo = applied.isEmpty() || applied.stream().allMatch(p -> p.type().instant())
                ? Component.empty() : undoButton(staff, plan, applied, window);
        if (staff.isOnline()) {
            module.messages().send(staff, "bulk.done", Msg.p("reason", plan.reasonName()), Msg.number("apply", s.apply()),
                    Msg.number("request", s.request()), Msg.number("skip", s.skip()), Msg.c("undo", undo));
            for (BulkSelection.Line l : lines) {
                if (l.kind() != BulkSelection.Kind.APPLY) {
                    module.messages().send(staff, "bulk.done-line", Msg.p("player", l.name()), Msg.p("what", lineText(staff, l)));
                }
            }
            module.core().sounds().play(staff, s.apply() + s.request() > 0 ? "success" : "error");
        }
        if (!applied.isEmpty()) {
            module.service().notifyStaff(staff, "bulk.notify", Msg.p("staff", staff.getName()), Msg.p("reason", plan.reasonName()),
                    Msg.number("count", applied.size()), Msg.p("players", PunishEmbeds.players(applied, false)));
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("group", plan.group());
        details.put("reason", plan.reasonId());
        details.put("applied", applied.stream().map(Punishment::id).toList());
        details.put("requested", s.request());
        List<String> skipped = new ArrayList<>();
        for (BulkSelection.Line l : lines) {
            if (l.kind() == BulkSelection.Kind.SKIP) {
                skipped.add(l.name() + ":" + l.reason());
            }
        }
        details.put("skipped", skipped);
        module.core().audit().log(AuditCategory.PUNISHMENT, "bulk_punish", Actor.player(staff), null, null,
                (long) applied.size(), staff.getLocation(), details);
        if (module.service().hooks() != null) {
            module.service().hooks().bulkApplied(plan.group(), plan.reasonName(), staff.getName(), applied, s.request(), s.skip());
        }
        // Bedrock: clickable chat is unreliable there, so the result comes with a menu holding [Undo all]
        if (staff.isOnline() && !applied.isEmpty() && module.core().bedrock().isBedrock(staff.getUniqueId())) {
            module.core().menus().open(staff, new BulkMenus.ResultMenu(module, this, plan, applied, s));
        }
    }

    // ================================================================== undo all

    private final Map<String, AtomicBoolean> undone = new java.util.concurrent.ConcurrentHashMap<>();

    private Component undoButton(Player staff, Plan plan, List<Punishment> applied, Duration window) {
        return module.messages().component(staff, "bulk.undo-button", Msg.p("window", PunishConfig.text(window)))
                .hoverEvent(HoverEvent.showText(module.messages().component(staff, "bulk.undo-hover",
                        Msg.number("count", applied.size()), Msg.p("window", PunishConfig.text(window)))))
                .clickEvent(ClickEvent.callback(audience -> {
                    if (audience instanceof Player clicker) {
                        undoAll(clicker, plan, applied);
                    }
                }, options -> options.uses(ClickCallback.UNLIMITED_USES).lifetime(window.plus(Duration.ofMinutes(10)))));
    }

    /**
     * [Undo all]: undoes every punishment of the bulk action still inside the undo window (spec 12.1 rules per punishment:
     * your own, or anyone's with {@code undo.others}). Once per bulk action. Main thread in and out.
     */
    void undoAll(Player staff, Plan plan, List<Punishment> applied) {
        AtomicBoolean used = undone.computeIfAbsent(plan.group(), g -> new AtomicBoolean());
        if (!used.compareAndSet(false, true)) {
            module.messages().send(staff, "bulk.undo-used");
            module.core().sounds().play(staff, "error");
            return;
        }
        List<CompletableFuture<PunishmentStore.UndoResult>> runs = new ArrayList<>();
        for (Punishment p : applied) {
            if (p.type().instant()) {
                continue;
            }
            runs.add(module.service().undoQuiet(staff, p.id(), plan.group()).exceptionally(e -> null));
        }
        CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)).thenRun(() -> Bukkit.getScheduler().runTask(
                module.core().plugin(), () -> {
                    List<Punishment> ok = new ArrayList<>();
                    int failed = 0;
                    for (CompletableFuture<PunishmentStore.UndoResult> f : runs) {
                        PunishmentStore.UndoResult r = f.join();
                        if (r != null && r.result() == Undo.Result.OK) {
                            ok.add(r.punishment());
                        } else {
                            failed++;
                        }
                    }
                    if (ok.isEmpty() && failed > 0) {
                        used.set(false); // nothing happened (e.g. too late): the button may be tried by someone allowed
                    }
                    if (staff.isOnline()) {
                        module.messages().send(staff, failed == 0 ? "bulk.undone" : "bulk.undone-some", Msg.number("count", ok.size()),
                                Msg.number("failed", failed), Msg.p("window",
                                        PunishConfig.text(module.service().extras().undoWindow())));
                        module.core().sounds().play(staff, ok.isEmpty() ? "error" : "success");
                    }
                    if (!ok.isEmpty()) {
                        module.service().notifyStaff(staff, "bulk.undo-notify", Msg.p("staff", staff.getName()),
                                Msg.p("reason", plan.reasonName()), Msg.number("count", ok.size()));
                        module.core().audit().log(AuditCategory.PUNISHMENT, "bulk_undo", Actor.player(staff), null, null,
                                (long) ok.size(), staff.getLocation(), Map.of("group", plan.group(),
                                        "ids", ok.stream().map(Punishment::id).toList(), "failed", failed));
                        if (module.service().hooks() != null) {
                            module.service().hooks().bulkUndone(plan.group(), staff.getName(), ok);
                        }
                    }
                }));
    }
}
