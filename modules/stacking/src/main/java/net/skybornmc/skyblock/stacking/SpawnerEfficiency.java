package net.skybornmc.skyblock.stacking;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Owner F2 (feature card D2): diminishing spawner output per island and tier. The k-th spawner of one tier on an island
 * works at the percent of the band k falls in ({@code stacking.yml → spawners.efficiency}, default 1–16 at 100%, 17–32
 * at 60%, 33–64 at 35%, 65–128 at 20%, 129+ at 10%). The island's effective output for n spawners is the sum of the
 * bands, {@code E(n)}; every spawner block of that tier runs at {@code E(n) / n} so the blocks together make exactly
 * {@code E(n)} spawners' worth. Pure and immutable.
 *
 * <p>Line format: {@code from-to:percent} or {@code from+:percent} for the last, open band. Bands start at 1, follow
 * each other without gaps and end with an open band. An empty list switches the curve off (every spawner 100%).</p>
 */
final class SpawnerEfficiency {

    /** Owner F2 defaults (card D2). */
    static final List<String> DEFAULT = List.of("1-16:100", "17-32:60", "33-64:35", "65-128:20", "129+:10");

    /** One band: spawners {@code from..to} (inclusive; {@code to = Long.MAX_VALUE} for the open band) work at {@code percent}. */
    record Band(long from, long to, double percent) {
        long size() {
            return to - from + 1;
        }
    }

    static final SpawnerEfficiency OFF = new SpawnerEfficiency(List.of(new Band(1, Long.MAX_VALUE, 100)));

    private final List<Band> bands;

    private SpawnerEfficiency(List<Band> bands) {
        this.bands = List.copyOf(bands);
    }

    /** The default curve (never fails). */
    static SpawnerEfficiency defaults() {
        return parse(DEFAULT, problem -> {
            throw new IllegalStateException(problem);
        });
    }

    /**
     * Reads the config lines. Any broken line is reported and the whole list falls back to the owner defaults (a half
     * curve would be worse than either). An empty list switches the curve off.
     */
    static SpawnerEfficiency parse(List<String> lines, Consumer<String> problems) {
        if (lines == null || lines.isEmpty()) {
            return OFF;
        }
        List<Band> out = new ArrayList<>();
        long expected = 1;
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String where = "stacking.yml → spawners.efficiency: '" + raw + "'";
            Band band = band(raw);
            String problem = null;
            if (band == null) {
                problem = where + " needs the form from-to:percent or from+:percent (e.g. 17-32:60)";
            } else if (band.from() != expected) {
                problem = where + " must start at " + expected + " (bands start at 1 and follow each other without gaps)";
            } else if (band.to() < band.from()) {
                problem = where + " ends before it starts";
            } else if (band.percent() < 0 || band.percent() > 100) {
                problem = where + " percent must be 0-100";
            } else if (band.to() == Long.MAX_VALUE && i != lines.size() - 1) {
                problem = where + " is open (+) but is not the last band";
            } else if (band.to() != Long.MAX_VALUE && i == lines.size() - 1) {
                problem = where + " is the last band and must be open, like 129+:10";
            }
            if (problem != null) {
                problems.accept(problem + "; the owner defaults " + DEFAULT + " are used");
                return defaults();
            }
            out.add(band);
            expected = band.to() == Long.MAX_VALUE ? Long.MAX_VALUE : band.to() + 1;
        }
        return new SpawnerEfficiency(out);
    }

    private static Band band(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        int colon = s.indexOf(':');
        if (colon <= 0 || colon == s.length() - 1) {
            return null;
        }
        String range = s.substring(0, colon);
        try {
            double percent = Double.parseDouble(s.substring(colon + 1).replace("%", ""));
            if (Double.isNaN(percent) || Double.isInfinite(percent)) {
                return null;
            }
            if (range.endsWith("+")) {
                return new Band(Long.parseLong(range.substring(0, range.length() - 1)), Long.MAX_VALUE, percent);
            }
            int dash = range.indexOf('-');
            if (dash <= 0) {
                return null;
            }
            return new Band(Long.parseLong(range.substring(0, dash)), Long.parseLong(range.substring(dash + 1)), percent);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    List<Band> bands() {
        return bands;
    }

    /** Percent at which the k-th spawner (1-based) of a tier on one island works. */
    double percentOf(long k) {
        if (k < 1) {
            return bands.getFirst().percent();
        }
        for (Band b : bands) {
            if (k <= b.to()) {
                return b.percent();
            }
        }
        return bands.getLast().percent();
    }

    /** Percent at which the next spawner works when the island already has {@code n} of that tier. */
    double nextPercent(long n) {
        return percentOf(Math.max(0, n) + 1);
    }

    /** {@code E(n)}: spawners' worth of output that {@code n} spawners of one tier on one island make together. */
    double effective(long n) {
        if (n <= 0) {
            return 0;
        }
        double sum = 0;
        for (Band b : bands) {
            if (n < b.from()) {
                break;
            }
            long inBand = Math.min(n, b.to()) - b.from() + 1;
            sum += inBand * b.percent() / 100.0;
        }
        return sum;
    }

    /** {@code E(n) / n}: the share of full output each spawner block of that tier runs at (1 for no spawners). */
    double factor(long n) {
        return n <= 0 ? 1.0 : effective(n) / n;
    }
}
