package net.skybornmc.skyblock.common.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * REVIEW3 §6 decision ledger: owner answers that are config values ({@code docs/decisions.yml}, bundled as
 * {@code decisions.yml}) reach existing live configs through a one-time migration at startup, before any module loads
 * its config:
 * <ul>
 *   <li>a scalar setting is set to the decided value only while the live value still equals the old default
 *       ({@code migrate-from}); a value the owner set himself is kept (and logged);</li>
 *   <li>for a list setting ({@code item}) the old item is replaced by the new one, the rest of the list stays;</li>
 *   <li>each id runs once per server: applied ids are stored in {@code decisions-applied.yml} in the plugin data folder,
 *       so the owner can change a value back later and it stays;</li>
 *   <li>a missing file or key is marked applied (the new code default is written when the module loads); a broken
 *       file is never touched and is tried again on the next start.</li>
 * </ul>
 */
public final class DecisionMigrations {

    /** File in the plugin data folder that remembers applied decision ids. */
    public static final String APPLIED_FILE = "decisions-applied.yml";

    /** One ledger entry that can migrate (has {@code file}, {@code path} and {@code migrate-from}). */
    public record Decision(String id, String file, String path, Object value, String item, Object migrateFrom) {

        public boolean listItem() {
            return item != null;
        }
    }

    private final File folder;
    private final Logger log;

    public DecisionMigrations(File folder, Logger log) {
        this.folder = Objects.requireNonNull(folder, "folder");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** The migratable entries of a ledger ({@code decisions:} list); documentation-only entries are skipped. */
    public static List<Decision> parse(YamlConfiguration ledger) {
        List<Decision> out = new ArrayList<>();
        for (Map<?, ?> raw : ledger.getMapList("decisions")) {
            Object id = raw.get("id");
            Object file = raw.get("file");
            Object path = raw.get("path");
            Object from = raw.get("migrate-from");
            if (id == null || file == null || path == null || from == null || Boolean.FALSE.equals(raw.get("check"))) {
                continue;
            }
            Object item = raw.get("item");
            out.add(new Decision(String.valueOf(id), String.valueOf(file), String.valueOf(path), raw.get("value"),
                    item == null ? null : String.valueOf(item), from));
        }
        return out;
    }

    /** Reads a ledger (e.g. the bundled resource); an unreadable one gives no entries (logged). */
    public List<Decision> parse(Reader reader) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(reader);
        } catch (IOException | InvalidConfigurationException e) {
            log.warning("decisions.yml could not be read, no decision migrations run: " + e.getMessage());
            return List.of();
        }
        return parse(yaml);
    }

    /**
     * Applies every decision not applied on this server yet. Returns the ids applied in this run (changed or not).
     */
    public List<String> apply(List<Decision> decisions) {
        File appliedFile = new File(folder, APPLIED_FILE);
        YamlConfiguration applied = YamlConfiguration.loadConfiguration(appliedFile);
        ConfigurationSection done = applied.getConfigurationSection("applied");
        List<String> ran = new ArrayList<>();
        for (Decision d : decisions) {
            if (done != null && done.contains(key(d.id()))) {
                continue;
            }
            if (run(d)) {
                applied.set("applied." + key(d.id()), System.currentTimeMillis());
                ran.add(d.id());
            }
        }
        if (!ran.isEmpty()) {
            applied.options().setHeader(List.of("Owner decisions (docs/decisions.yml) already migrated into the live configs.",
                    "Each id runs once; remove a line to run that migration again on the next start."));
            try {
                folder.mkdirs();
                applied.save(appliedFile);
            } catch (IOException e) {
                log.severe("Could not save " + APPLIED_FILE + " (the decision migrations run again next start): " + e.getMessage());
            }
        }
        return ran;
    }

    /** Ids are stored as yml keys; dots would make sections. */
    static String key(String id) {
        return id.replace('.', '_');
    }

    /** Runs one decision. False when it must be tried again (broken or unsavable file). */
    private boolean run(Decision d) {
        File file = new File(folder, d.file());
        if (!file.exists()) {
            return true; // a new file gets the new code default
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            log.warning("Decision " + d.id() + ": " + d.file() + " is not valid YAML, not changed (tried again next start)");
            return false;
        }
        if (!yaml.contains(d.path())) {
            return true;
        }
        Object current = yaml.get(d.path());
        Object next = migrated(d, current);
        if (next == null) {
            log.info("Decision " + d.id() + ": " + d.file() + " → " + d.path() + " is " + current + ", set by the owner; kept");
            return true;
        }
        yaml.set(d.path(), next);
        try {
            yaml.save(file);
        } catch (IOException e) {
            log.severe("Decision " + d.id() + ": could not save " + d.file() + ": " + e.getMessage());
            return false;
        }
        log.info("Decision " + d.id() + ": " + d.file() + " → " + d.path() + " changed " + current + " → " + next
                + " (owner answer; it still had the old default)");
        return true;
    }

    /**
     * The new live value, or null when the live value must stay (the owner changed it, or it is already right).
     * Pure, unit-tested.
     */
    static Object migrated(Decision d, Object current) {
        if (d.listItem()) {
            if (!(current instanceof List<?> list)) {
                return null;
            }
            String from = String.valueOf(d.migrateFrom());
            if (list.stream().noneMatch(v -> from.equals(String.valueOf(v)))) {
                return null;
            }
            List<String> out = new ArrayList<>();
            for (Object v : list) {
                out.add(from.equals(String.valueOf(v)) ? d.item() : String.valueOf(v));
            }
            return out;
        }
        if (ModuleConfig.ValueChange.of(d.migrateFrom(), d.value()).matches(current)
                && !ModuleConfig.ValueChange.of(d.value(), d.value()).matches(current)) {
            return d.value();
        }
        return null;
    }
}
