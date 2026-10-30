package net.skybornmc.skyblock.worlds.builds;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The finished builds in {@code <server>/builds} (configurable): every {@code <id>.yml} with a {@code schematic}, its
 * schematic header and problems, plus {@code depths_layout.yml}. A scan reads files only (no world access), so it runs
 * off the main thread; the result is an immutable snapshot.
 *
 * @param builds   by id, sorted
 * @param layout   the Depths layout, or {@code null} if the file is missing/broken
 * @param problems folder-level problems (missing folder, broken files)
 */
public record BuildCatalog(File folder, Map<String, BuildEntry> builds, LayoutFile layout, List<String> problems) {

    public BuildCatalog {
        builds = Collections.unmodifiableMap(new TreeMap<>(builds));
        problems = List.copyOf(problems);
    }

    public static BuildCatalog empty(File folder) {
        return new BuildCatalog(folder, Map.of(), null, List.of());
    }

    /** Ids that may be pasted/applied (no errors). */
    public List<String> validIds() {
        return builds.values().stream().filter(BuildEntry::ok).map(BuildEntry::id).toList();
    }

    /** Scans the folder (blocking, file I/O only). */
    public static BuildCatalog scan(File folder, BuildValidator.Context context) {
        List<String> problems = new ArrayList<>();
        Map<String, BuildEntry> builds = new TreeMap<>();
        if (!folder.isDirectory()) {
            problems.add("builds folder " + folder.getPath() + " does not exist (worlds.yml → builds.folder)");
            return new BuildCatalog(folder, builds, null, problems);
        }
        LayoutFile layout = null;
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            files = new File[0];
        }
        java.util.Arrays.sort(files);
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file);
            } catch (IOException | InvalidConfigurationException e) {
                problems.add(file.getName() + ": not valid YAML, skipped. " + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
                continue;
            }
            if (file.getName().equals(LayoutFile.FILE)) {
                layout = LayoutFile.parse(yaml, problems::add);
                continue;
            }
            if (!yaml.contains("schematic")) {
                continue; // not a build (other data file)
            }
            if (!id.matches("[a-z0-9_]+")) {
                problems.add(file.getName() + ": build ids use lowercase letters, digits and _ only, skipped");
                continue;
            }
            builds.put(id, entry(id, file, yaml, context));
        }
        return new BuildCatalog(folder, builds, layout, problems);
    }

    static BuildEntry entry(String id, File yml, YamlConfiguration yaml, BuildValidator.Context context) {
        List<BuildProblem> problems = new ArrayList<>();
        BuildMeta meta = BuildMetaParser.parse(id, yaml, p -> problems.add(BuildProblem.warning(p)));
        File schematic = meta.schematic() == null ? null : new File(yml.getParentFile(), new File(meta.schematic()).getName());
        boolean found = schematic != null && schematic.isFile();
        SchematicInfo info = null;
        String readError = null;
        if (found) {
            try {
                info = SchematicReader.read(schematic);
            } catch (IOException | RuntimeException e) {
                readError = String.valueOf(e.getMessage());
            }
        }
        problems.addAll(BuildValidator.validate(meta, found, info, readError, context));
        return new BuildEntry(meta, yml, schematic, info, problems);
    }
}
