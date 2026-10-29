package net.skybornmc.skyblock.staff;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Pure report rules (testable): reason matching and anti-abuse checks. */
final class ReportRules {

    /** Why a report is refused, or empty when it may be filed. */
    enum Refusal { SELF, COOLDOWN, PLAYTIME, TOO_MANY_OPEN, ALREADY_REPORTED }

    private ReportRules() {
    }

    static Optional<Refusal> check(boolean self, long cooldownLeftMillis, long playtimeMillis, int minPlaytimeMinutes,
                                   int openReports, int maxOpen, boolean sameTargetOpen) {
        if (self) {
            return Optional.of(Refusal.SELF);
        }
        if (cooldownLeftMillis > 0) {
            return Optional.of(Refusal.COOLDOWN);
        }
        if (minPlaytimeMinutes > 0 && playtimeMillis < minPlaytimeMinutes * 60_000L) {
            return Optional.of(Refusal.PLAYTIME);
        }
        if (sameTargetOpen) {
            return Optional.of(Refusal.ALREADY_REPORTED);
        }
        if (openReports >= maxOpen) {
            return Optional.of(Refusal.TOO_MANY_OPEN);
        }
        return Optional.empty();
    }

    /**
     * Splits {@code /report <player> <text>} text into (reason, details): a text starting with a configured reason
     * uses that reason (the rest is the details); anything else is the last reason ("Other") with the text as details.
     */
    static String[] parse(String text, List<String> reasons) {
        String trimmed = text == null ? "" : text.strip();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String best = null;
        for (String reason : reasons) {
            String r = reason.toLowerCase(Locale.ROOT);
            if ((lower.equals(r) || lower.startsWith(r + " ")) && (best == null || reason.length() > best.length())) {
                best = reason;
            }
        }
        if (best != null) {
            return new String[]{best, trimmed.substring(best.length()).strip()};
        }
        return new String[]{reasons.isEmpty() ? "Other" : reasons.getLast(), trimmed};
    }

    /** Cleans free text for storage/display: one line, trimmed, max length. */
    static String clean(String text, int max) {
        String one = text.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").strip();
        return one.length() > max ? one.substring(0, max) : one;
    }
}
