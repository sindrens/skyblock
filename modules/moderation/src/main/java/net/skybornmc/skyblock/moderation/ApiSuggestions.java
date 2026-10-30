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
        int previous = Offences.counting(history, template.id(), template.cutoff(now));
        Templates.Step step = template.stepFor(previous);
        String src = Notes.clean(source == null ? "" : source);
        String det = Notes.clean(details == null ? "" : details);
        String text = src.isEmpty() ? det : det.isEmpty() ? src : src + ": " + det;
        return new Plan(template, step, Evidence.cut(text, 255), pendingExpire == null ? null : now + pendingExpire.toMillis());
    }

    @Override
    public CompletableFuture<Result> suggest(UUID target, String reasonId, String source, String details) {
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
                Plan plan = plan(template.get(), history, source, details, now, expire);
                return service.store().suggestApi(target, name, template.get().id(), plan.step(),
                        reasonName.apply(template.get()), plan.details(), now, plan.expires()).thenApply(r -> {
                            core.scheduler().ensureMain("moderation:suggest", () -> announce(r));
                            return new Result(r.refreshed() ? Status.REFRESHED : Status.CREATED, r.suggestion().id());
                        });
            });
        });
    }

    /** Staff line for a new suggestion (a refresh stays quiet); audit for both. Main thread. */
    private void announce(PunishmentStore.ApiSuggestion r) {
        Suggestion s = r.suggestion();
        core.audit().log(AuditCategory.PUNISHMENT, r.refreshed() ? "suggestion_api_refresh" : "suggestion_api",
                Actor.system("suggest"), s.target().toString(), s.targetName(), null, null,
                Map.of("suggestion", s.id(), "reason", String.valueOf(s.reasonId()), "action", s.action(),
                        "details", s.details() == null ? "" : s.details()));
        if (r.refreshed()) {
            return;
        }
        service.notifyStaff(null, "suggest.api-notify", service.suggestionParts(null, s));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(ModerationModule.PERM_NOTIFY)) {
                core.sounds().play(staff, "notify");
            }
        }
    }
}
