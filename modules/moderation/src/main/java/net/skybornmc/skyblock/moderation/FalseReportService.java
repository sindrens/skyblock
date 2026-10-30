package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.moderation.FalseReports;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The false report penalty of spec 14.5 (moderation side), published as {@link FalseReports} for the reports module. A
 * reporter at {@code threshold} invalid reports inside {@code window} gets a suggestion for the reason
 * {@code report-abuse} (its ladder: warn, report-block 3d, report-block 14d, mute 1d): staff see
 * "Steve (3 invalid reports in 30d) → suggested: warn [Apply] [Dismiss]". Never automatic; at most one per player per
 * {@code cap} (a waiting one is refreshed, {@link PunishmentStore#suggestApiCapped}).
 */
final class FalseReportService implements FalseReports {

    private final Core core;
    private final PunishmentService service;
    private final Templates templates;
    private final Supplier<PunishConfig.Extras> extras;
    private final Supplier<WarnRules.Settings> rules;

    FalseReportService(Core core, PunishmentService service, Templates templates, Supplier<PunishConfig.Extras> extras,
                       Supplier<WarnRules.Settings> rules) {
        this.core = core;
        this.service = service;
        this.templates = templates;
        this.extras = extras;
        this.rules = rules;
    }

    private FalseReportRules.Settings settings() {
        return extras.get().falseReports();
    }

    @Override
    public Duration window() {
        return settings().window();
    }

    @Override
    public CompletableFuture<Result> invalidReport(UUID reporter, String reporterName, int invalidInWindow, UUID staff,
                                                   String staffName) {
        FalseReportRules.Settings s = settings();
        Optional<Templates.Template> template = templates.template(s.reason());
        if (!s.enabled() || template.isEmpty() || reporter == null) {
            return CompletableFuture.completedFuture(new Result(Outcome.DISABLED, 0, s.threshold()));
        }
        if (FalseReportRules.step(s, invalidInWindow) == FalseReportRules.Step.NOTHING) {
            return CompletableFuture.completedFuture(new Result(Outcome.BELOW_THRESHOLD, 0, s.threshold()));
        }
        long now = System.currentTimeMillis();
        Duration expire = rules.get().pendingExpire();
        String windowText = Templates.Step.compact(s.window());
        String source = service.text(null, "false-reports.source");
        String details = service.text(null, "false-reports.details", Msg.number("count", invalidInWindow),
                Msg.p("window", windowText));
        return service.store().history(reporter).thenCompose(history -> {
            ApiSuggestions.Plan plan = ApiSuggestions.plan(template.get(), history, source, details, now, expire,
                    service.islandPunish().reportBlocksAvailable());
            String reasonName = service.text(null, "reasons." + template.get().id());
            return service.store().suggestApiCapped(reporter, reporterName, template.get().id(), plan.step(), reasonName,
                    plan.details(), now, plan.expires(), FalseReportRules.capSince(s, now)).thenApply(r -> {
                        Map<String, Object> audit = new LinkedHashMap<>();
                        audit.put("invalid_reports", invalidInWindow);
                        audit.put("window", windowText);
                        audit.put("closed_by", staffName == null ? "?" : staffName);
                        if (r.isEmpty()) {
                            audit.put("result", "capped");
                            core.audit().log(AuditCategory.PUNISHMENT, "false_report_capped", Actor.system("false-reports"),
                                    reporter.toString(), reporterName, null, null, audit);
                            return new Result(Outcome.CAPPED, 0, s.threshold());
                        }
                        PunishmentStore.ApiSuggestion saved = r.get();
                        audit.put("suggestion", saved.suggestion().id());
                        audit.put("action", saved.suggestion().action());
                        core.audit().log(AuditCategory.PUNISHMENT, saved.refreshed() ? "false_report_refresh" : "false_report_suggestion",
                                Actor.system("false-reports"), reporter.toString(), reporterName, null, null, audit);
                        if (!saved.refreshed()) {
                            core.scheduler().ensureMain("moderation:false-report", () -> announce(saved.suggestion()));
                        }
                        return new Result(saved.refreshed() ? Outcome.REFRESHED : Outcome.SUGGESTED, saved.suggestion().id(),
                                s.threshold());
                    });
        });
    }

    /** The usual suggestion line ([Apply] [Dismiss] [Open /p]) to staff with suggestion alerts. Main thread. */
    private void announce(Suggestion s) {
        service.notifyStaff(NotifyRules.Type.SUGGESTIONS, null, "suggest.api-notify", service.suggestionParts(null, s));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (service.wants(staff, NotifyRules.Type.SUGGESTIONS)) {
                core.sounds().play(staff, "notify");
            }
        }
    }
}
