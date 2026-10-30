package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.moderation.SuggestionService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The suggestion API (spec 12.8) other modules call through {@link SuggestionService}. The suggested punishment is the
 * reason's next ladder step for that player (same offence counting as the menu); staff get the usual
 * {@code [Apply] [Dismiss] [Open /p]} line. Never applies anything by itself.
 */
final class ApiSuggestions implements SuggestionService {

    /** What {@link #suggest} needs to decide, without Bukkit (unit-tested). */
    record Plan(Templates.Template template, Templates.Step step, String details, Long expires) {
    }

    private final Core core;
    private final PunishmentService service;
    private final Templates templates;
    private final Supplier<WarnRules.Settings> rules;
    private final java.util.function.Function<Templates.Template, String> reasonName;

    ApiSuggestions(Core core, PunishmentService service, Templates templates, Supplier<WarnRules.Settings> rules,
                   java.util.function.Function<Templates.Template, String> reasonName) {
        this.core = core;
        this.service = service;
        this.templates = templates;
        this.rules = rules;
        this.reasonName = reasonName;
    }

    /**
     * The step to suggest and the text shown to staff ({@code "<source>: <details>"}, cut to 255). {@code pendingExpire}
     * {@code null} = never expires.
     */
    static Plan plan(Templates.Template template, List<Punishment> history, String source, String details, long now,
                     Duration pendingExpire) {
        return plan(template, history, source, details, now, pendingExpire, false);
    }

    /**
     * Same, with report blocks available or not (P9, spec 14.5: a {@code report-block} step can be suggested when the
     * reports module enforces it; it needs no island context).
     */
    static Plan plan(Templates.Template template, List<Punishment> history, String source, String details, long now,
                     Duration pendingExpire, boolean reportBlocks) {
        int previous = Offences.counting(history, template.id(), template.cutoff(now));
        // P7: suggestions never carry island steps (no island context when they are applied later): the non-island
        // step that replaces them is suggested instead
        Templates.Step step = IslandSteps.adapt(template, IslandSteps.Context.UNAVAILABLE.withReports(reportBlocks))
                .stepFor(previous);
        String src = Notes.clean(source == null ? "" : source);
        String det = Notes.clean(details == null ? "" : details);
        String text = src.isEmpty() ? det : det.isEmpty() ? src : src + ": " + det;
        return new Plan(template, step, Evidence.cut(text, 255), pendingExpire == null ? null : now + pendingExpire.toMillis());
    }

    /** Separator between appended detail lines (spec 13.3: details of repeated alerts are appended). */
    static final String DETAIL_SEPARATOR = " | ";
    /** At most this many detail lines are kept (spec 13.3: max 5 lines). */
    static final int MAX_DETAIL_LINES = 5;
    /** The details column holds this many characters. */
    static final int MAX_DETAIL_CHARS = 255;

    /**
     * {@code existing} with {@code added} appended as a new line, keeping the newest {@code maxLines} lines and dropping the
     * oldest ones until everything fits in {@code maxChars} (the newest line alone is cut if it is still too long).
     */
    static String appendDetails(String existing, String added, int maxLines, int maxChars) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (existing != null && !existing.isBlank()) {
            for (String l : existing.split(java.util.regex.Pattern.quote(DETAIL_SEPARATOR))) {
                if (!l.isBlank()) {
                    lines.add(l.strip());
                }
            }
        }
        if (added != null && !added.isBlank()) {
            lines.add(added.strip());
        }
        while (lines.size() > Math.max(1, maxLines)) {
            lines.removeFirst();
        }
        while (lines.size() > 1 && String.join(DETAIL_SEPARATOR, lines).length() > maxChars) {
            lines.removeFirst();
        }
        return Evidence.cut(String.join(DETAIL_SEPARATOR, lines), maxChars);
    }

    @Override
    public CompletableFuture<Result> suggest(UUID target, String reasonId, String source, String details) {
        return suggest(target, reasonId, source, details, false, Actor.system("suggest"));
    }

    /**
     * Same, from {@code /psuggest} (spec 13.3): {@code append} = a second call for the same target + reason appends its
     * details to the waiting suggestion (max 5 lines) instead of replacing them; {@code actor} is who is audited. Never
     * applies anything, also in warn-rules auto mode and for {@code extras.never-auto} reasons.
     */
    CompletableFuture<Result> suggest(UUID target, String reasonId, String source, String details, boolean append, Actor actor) {
        Optional<Templates.Template> template = reasonId == null ? Optional.empty() : templates.template(reasonId);
        if (template.isEmpty() || target == null) {
            return CompletableFuture.completedFuture(new Result(Status.UNKNOWN_REASON, 0));
        }
        long now = System.currentTimeMillis();
        Duration expire = rules.get().pendingExpire();
        return core.players().offline(target).thenCompose(snap -> {
            if (snap.isEmpty()) {
                return CompletableFuture.completedFuture(new Result(Status.UNKNOWN_PLAYER, 0));
            }
            String name = snap.get().name();
            return service.store().history(target).thenCompose(history -> {
                Plan plan = plan(template.get(), history, source, details, now, expire,
                        service.islandPunish().reportBlocksAvailable());
                return service.store().suggestApi(target, name, template.get().id(), plan.step(),
                        reasonName.apply(template.get()), plan.details(), now, plan.expires(), append).thenApply(r -> {
                            core.scheduler().ensureMain("moderation:suggest", () -> announce(r, actor));
                            return new Result(r.refreshed() ? Status.REFRESHED : Status.CREATED, r.suggestion().id());
                        });
            });
        });
    }

    /** Staff line for a new suggestion (a refresh stays quiet); audit for both. Main thread. */
    private void announce(PunishmentStore.ApiSuggestion r, Actor actor) {
        Suggestion s = r.suggestion();
        core.audit().log(AuditCategory.PUNISHMENT, r.refreshed() ? "suggestion_api_refresh" : "suggestion_api",
                actor, s.target().toString(), s.targetName(), null, null,
                Map.of("suggestion", s.id(), "reason", String.valueOf(s.reasonId()), "action", s.action(),
                        "details", s.details() == null ? "" : s.details()));
        if (r.refreshed()) {
            return;
        }
        service.notifyStaff(NotifyRules.Type.SUGGESTIONS, null, "suggest.api-notify", service.suggestionParts(null, s));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (service.wants(staff, NotifyRules.Type.SUGGESTIONS)) {
                core.sounds().play(staff, "notify");
            }
        }
    }
}
