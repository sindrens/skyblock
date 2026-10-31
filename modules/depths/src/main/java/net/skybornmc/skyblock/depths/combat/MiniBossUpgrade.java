package net.skybornmc.skyblock.depths.combat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * M8.2 one-time upgrade of a live {@code mobs.yml}: the ten mini-bosses get the abilities they now have in the jar's file.
 *
 * <p>Why a text edit and not a YAML rewrite: it only ever touches a mini-boss line that is <b>exactly</b> what M4.8
 * shipped (spaces between values don't matter), and replaces that one line with the jar's new line (old values plus
 * {@code abilities}). A line the owner changed in any way (a number, a drop, own abilities) is left alone and logged as
 * skipped, and the rest of the file (comments, layout, the owner's other edits) stays byte for byte the same, which a
 * YAML save would not do. The id {@value #ID} is then added to {@code applied-upgrades} at the end of the file, so the
 * upgrade runs once: an owner who later removes the abilities again keeps it that way. The old file is kept as
 * {@value #BACKUP} when anything changed.</p>
 *
 * <p>New installs copy the jar's file, which already has the abilities and the marker, so nothing runs there. Pure apart
 * from {@link #upgrade(Path, String)}; unit-tested.</p>
 */
public final class MiniBossUpgrade {

    /** Upgrade id written to {@code applied-upgrades}. */
    public static final String ID = "m8-2-mini-boss-abilities";
    /** Top-level key that lists the upgrades a live file already had (not a catalog section). */
    public static final String MARKER_KEY = "applied-upgrades";
    /** Copy of the file before the upgrade. */
    public static final String BACKUP = "mobs.yml.before-m8-2";

    /** The mini-boss lines exactly as M4.8 shipped them (the only lines this upgrade replaces). */
    static final Map<String, String> M48_LINES = linked(
            "grave_warden", "grave_warden:     {type: zombie, health: 300, damage: 4, scale: 1.6, xp: 100, vanilla-xp: 50, crystal-chance: 5.2, glowing: true, name-visible: true, drops: [{item: depths_rotten_flesh, min: 50, max: 70}]}",
            "crypt_archer", "crypt_archer:     {type: skeleton, health: 330, damage: 5, scale: 1.4, xp: 200, vanilla-xp: 50, crystal-chance: 8.4, glowing: true, name-visible: true, drops: [{item: depths_bone, min: 50, max: 70}]}",
            "broodmother", "broodmother:      {type: spider, health: 360, damage: 6, scale: 2.0, xp: 440, vanilla-xp: 50, crystal-chance: 12.2, glowing: true, name-visible: true, drops: [{item: depths_spider_eye, min: 50, max: 70}]}",
            "king_slime", "king_slime:       {type: slime, size: 4, health: 390, damage: 7, scale: 1.5, xp: 800, vanilla-xp: 50, crystal-chance: 19.6, glowing: true, name-visible: true, drops: [{item: depths_slime_ball, min: 50, max: 70}]}",
            "tide_caller", "tide_caller:      {type: drowned, health: 420, damage: 8, scale: 1.5, xp: 1440, vanilla-xp: 50, crystal-chance: 28, glowing: true, name-visible: true, drops: [{item: depths_prismarine, min: 50, max: 70}]}",
            "coven_elder", "coven_elder:      {type: witch, health: 450, damage: 9, scale: 1.4, xp: 2700, vanilla-xp: 50, crystal-chance: 40, glowing: true, name-visible: true, drops: [{item: depths_glowstone, min: 50, max: 70}]}",
            "raid_captain", "raid_captain:     {type: vindicator, health: 500, damage: 11, scale: 1.4, xp: 5000, vanilla-xp: 50, crystal-chance: 62, glowing: true, name-visible: true, drops: [{item: depths_raider_scrap, min: 50, max: 70}]}",
            "forge_lord", "forge_lord:       {type: blaze, health: 510, damage: 12, scale: 1.8, xp: 8400, vanilla-xp: 50, crystal-chance: 88, glowing: true, name-visible: true, drops: [{item: depths_blaze_rod, min: 50, max: 70}]}",
            "bone_king", "bone_king:        {type: wither_skeleton, health: 540, damage: 13, scale: 1.5, xp: 14000, vanilla-xp: 50, crystal-chance: 128, glowing: true, name-visible: true, drops: [{item: depths_wither_ash, min: 50, max: 70}]}",
            "warlord", "warlord:          {type: ravager, health: 600, damage: 15, scale: 1.3, xp: 24000, vanilla-xp: 50, crystal-chance: 198, glowing: true, name-visible: true, drops: [{item: depths_warlord_sigil, min: 50, max: 70}]}");

    private static final Pattern MARKER_LINE = Pattern.compile("^" + Pattern.quote(MARKER_KEY) + ":\\s*\\[(.*)]\\s*$");

    /**
     * What the upgrade did to a file's text.
     *
     * @param text     the new text (the same text when nothing changed)
     * @param upgraded mini-boss ids that got their abilities
     * @param skipped  mini-boss id → why it was left alone (edited by the owner, not in the file)
     * @param ran      false if the marker was already there (nothing checked)
     */
    public record Result(String text, List<String> upgraded, Map<String, String> skipped, boolean ran) {
    }

    private MiniBossUpgrade() {
    }

    /** True if the text already lists {@value #ID} in {@code applied-upgrades}. */
    public static boolean applied(String text) {
        for (String line : text.split("\n", -1)) {
            var m = MARKER_LINE.matcher(stripCr(line));
            if (m.matches() && List.of(m.group(1).split(",")).stream().map(String::trim).anyMatch(ID::equals)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Upgrades the text of a live {@code mobs.yml} with the mini-boss lines of the jar's file.
     *
     * @param live    the live file's text
     * @param bundled the jar's file's text (source of the new lines)
     */
    public static Result apply(String live, String bundled) {
        if (applied(live)) {
            return new Result(live, List.of(), Map.of(), false);
        }
        Map<String, String> fresh = new LinkedHashMap<>();
        for (String line : bundled.split("\n", -1)) {
            String t = stripCr(line).trim();
            for (String id : M48_LINES.keySet()) {
                if (t.startsWith(id + ":") && t.contains("abilities:")) {
                    fresh.put(id, t);
                }
            }
        }
        String[] lines = live.split("\n", -1);
        List<String> upgraded = new ArrayList<>();
        Map<String, String> skipped = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : M48_LINES.entrySet()) {
            String id = e.getKey();
            String target = squash(e.getValue());
            String replacement = fresh.get(id);
            boolean seen = false;
            boolean done = false;
            for (int i = 0; i < lines.length; i++) {
                String line = stripCr(lines[i]);
                String t = line.trim();
                if (!t.startsWith(id + ":")) {
                    continue;
                }
                seen = true;
                if (replacement != null && squash(t).equals(target)) {
                    String indent = line.substring(0, line.indexOf(t.charAt(0)));
                    lines[i] = indent + replacement + (lines[i].endsWith("\r") ? "\r" : "");
                    done = true;
                }
            }
            if (done) {
                upgraded.add(id);
            } else if (replacement == null) {
                skipped.put(id, "no new line in the jar's file");
            } else {
                skipped.put(id, seen ? "changed by the owner" : "not in the file");
            }
        }
        String text = String.join("\n", lines);
        return new Result(withMarker(text), List.copyOf(upgraded), skipped, true);
    }

    /** Adds {@value #ID} to {@code applied-upgrades} (a new line at the end if the key is not there yet). */
    static String withMarker(String text) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            var m = MARKER_LINE.matcher(stripCr(lines[i]));
            if (m.matches()) {
                String inner = m.group(1).trim();
                lines[i] = MARKER_KEY + ": [" + (inner.isEmpty() ? "" : inner + ", ") + ID + "]";
                return String.join("\n", lines);
            }
        }
        StringBuilder out = new StringBuilder(text);
        if (!text.isEmpty() && !text.endsWith("\n")) {
            out.append('\n');
        }
        out.append("\n# One-time upgrades this file already had (the plugin adds them; leave this line as it is).\n")
                .append(MARKER_KEY).append(": [").append(ID).append("]\n");
        return out.toString();
    }

    /**
     * Runs the upgrade on the live file (if it exists and was not upgraded yet): backup, then the new text.
     *
     * @return what happened, or {@code null} if there was nothing to do (no file, already applied)
     */
    public static Result upgrade(Path file, String bundled) throws IOException {
        if (bundled == null || !Files.isRegularFile(file)) {
            return null;
        }
        String live = Files.readString(file, StandardCharsets.UTF_8);
        Result result = apply(live, bundled);
        if (!result.ran()) {
            return null;
        }
        if (!result.upgraded().isEmpty()) {
            Files.copy(file, file.resolveSibling(BACKUP), StandardCopyOption.REPLACE_EXISTING);
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, result.text(), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return result;
    }

    /** Spaces between values don't count as an edit. */
    static String squash(String line) {
        return line.trim().replaceAll("\\s+", " ");
    }

    private static String stripCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private static Map<String, String> linked(String... pairs) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out.put(pairs[i], pairs[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(out);
    }
}
