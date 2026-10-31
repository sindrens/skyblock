package net.skybornmc.skyblock.boosters;

import java.util.List;
import java.util.Locale;

/** Pure booster rules (unit-tested, no server). Times in milliseconds. */
final class BoosterRules {

    static final long SECOND = 1_000L;
    static final long MINUTE = 60_000L;

    private BoosterRules() {
    }

    /** What starting a booster item does. */
    enum Outcome {
        /** Nothing of this type ran: it starts now. */
        STARTED,
        /** The same type and strength ran: the time was added (R3: never stacks, adds time). */
        EXTENDED,
        /** It would pass the most time one type may hold (RX39: 3 h). */
        FULL,
        /** Another strength of this type runs: it can start after that one ends. */
        OTHER_STRENGTH,
        /** This exact item was started before (a copy or a rolled-back inventory). */
        ALREADY_USED,
        /** The numbers on the item are outside the limits. */
        INVALID
    }

    /** A running booster of one type: strength and time left (0 or less = finished). */
    record Run(int percent, long remaining) {

        boolean running() {
            return remaining > 0 && percent > 0;
        }
    }

    /** Result of {@link #activate}: the outcome and the time left afterwards. */
    record Decision(Outcome outcome, long remaining) {

        boolean ok() {
            return outcome == Outcome.STARTED || outcome == Outcome.EXTENDED;
        }
    }

    /**
     * Starting {@code add} ms of a {@code percent} booster on top of {@code current} (null = none).
     *
     * @param max the most time one type may hold
     */
    static Decision activate(Run current, int percent, long add, long max) {
        if (percent <= 0 || add <= 0) {
            return new Decision(Outcome.INVALID, current == null ? 0 : Math.max(0, current.remaining()));
        }
        if (current == null || !current.running()) {
            return add > max ? new Decision(Outcome.FULL, 0) : new Decision(Outcome.STARTED, add);
        }
        if (current.percent() != percent) {
            return new Decision(Outcome.OTHER_STRENGTH, current.remaining());
        }
        if (current.remaining() > max - add) {
            return new Decision(Outcome.FULL, current.remaining());
        }
        return new Decision(Outcome.EXTENDED, current.remaining() + add);
    }

    /**
     * How much banked time may start now: nothing while another strength of the type runs, otherwise as much as fits
     * under {@code max}.
     */
    static long bankMove(Run current, int bankPercent, long banked, long max) {
        if (banked <= 0 || bankPercent <= 0) {
            return 0;
        }
        if (current != null && current.running() && current.percent() != bankPercent) {
            return 0;
        }
        long used = current != null && current.running() ? current.remaining() : 0;
        return Math.max(0, Math.min(banked, max - used));
    }

    /** Bank after adding {@code add} (capped at {@code max}; a cap of 0 or less means no cap). */
    static long bankAdd(long banked, long add, long max) {
        long sum = Math.max(0, banked) + Math.max(0, add);
        if (sum < 0) {
            sum = Long.MAX_VALUE;
        }
        return max > 0 ? Math.min(max, sum) : sum;
    }

    /** {@code text} as lowercase words separated by single spaces (letters and digits only). */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        boolean space = true;
        for (char ch : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                sb.append(ch);
                space = false;
            } else if (!space) {
                sb.append(' ');
                space = true;
            }
        }
        int end = sb.length();
        if (end > 0 && sb.charAt(end - 1) == ' ') {
            sb.setLength(end - 1);
        }
        return sb.toString();
    }

    /**
     * True if a chat line thanks a booster: it contains one of {@code phrases} as whole words ({@code "ty"} matches
     * "ty!" and "ty for the boost", never "type"), and is at most {@code maxLength} characters long.
     */
    static boolean isThanks(String message, List<String> phrases, int maxLength) {
        if (message == null || message.isBlank() || message.length() > maxLength || phrases == null) {
            return false;
        }
        String words = " " + normalize(message) + " ";
        for (String phrase : phrases) {
            String p = normalize(phrase);
            if (!p.isEmpty() && words.contains(" " + p + " ")) {
                return true;
            }
        }
        return false;
    }

    /** {@code 1h 05m}, {@code 12m 30s}, {@code 45s} ({@code 0s} for nothing). */
    static String time(long millis) {
        long s = Math.max(0, (millis + 999) / SECOND);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) {
            return h + "h " + (m < 10 ? "0" : "") + m + "m";
        }
        if (m > 0) {
            return m + "m " + (sec < 10 ? "0" : "") + sec + "s";
        }
        return sec + "s";
    }

    /**
     * PERF8: whether the global boosters' time left is saved in this tick. Never in the first tick after the load: no time
     * has passed yet, so it would only write back what was just read (that write used to land in the first
     * {@code boosters:tick} after every start). After that every {@code periodMillis}, counted from the first tick.
     */
    static boolean saveDue(boolean firstTick, long lastSave, long now, long periodMillis) {
        return !firstTick && now - lastSave >= periodMillis;
    }

    /** Boss bar progress 0..1 (time left / full length). */
    static float progress(long remaining, long duration) {
        if (duration <= 0) {
            return 0f;
        }
        double p = (double) Math.max(0, remaining) / duration;
        return (float) Math.max(0, Math.min(1, p));
    }
}
