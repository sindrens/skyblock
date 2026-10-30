package net.skybornmc.skyblock.depths.zones;

import net.skybornmc.skyblock.common.builds.DepthsLayout;
import net.skybornmc.skyblock.common.builds.DepthsLayoutHook;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * {@link DepthsLayoutHook} of the depths module: reads zones.yml, merges the builders' layout and the applied build
 * ({@link BuildLayoutMerge}), backs the old file up as {@code zones.yml.bak-<time>}, writes the merged file and reloads
 * the depths module so tier gates, fields, {@code /dungeon} and combat zones use it at once.
 */
public final class ZoneBuildHook implements DepthsLayoutHook {

    private final File folder;
    private final Runnable reload;
    private final Logger log;

    public ZoneBuildHook(File folder, Runnable reload, Logger log) {
        this.folder = folder;
        this.reload = reload;
        this.log = log;
    }

    @Override
    public Result merge(DepthsLayout layout) {
        File file = new File(folder, ZoneManager.FILE);
        if (!file.isFile()) {
            return new Result(false, List.of(), List.of(ZoneManager.FILE + " does not exist yet (start the depths module once)"), null);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> parseProblems = new ArrayList<>();
        ZoneSet current;
        try {
            yaml.load(file);
            current = ZoneParser.parse(yaml, ZoneManager.FILE, parseProblems::add);
        } catch (IOException | InvalidConfigurationException e) {
            return new Result(false, List.of(), List.of(ZoneManager.FILE + " is not valid YAML, nothing changed: " + e.getMessage()), null);
        }
        if (current.zones().isEmpty()) {
            return new Result(false, List.of(), List.of(ZoneManager.FILE + " has no zones, nothing changed"), null);
        }
        BuildLayoutMerge.Result merged = BuildLayoutMerge.merge(current, layout);
        World world = Bukkit.getWorld(current.world());
        double border = world == null ? 0 : world.getWorldBorder().getSize() / 2.0;
        List<String> problems = new ArrayList<>(parseProblems);
        merged.unmatched().forEach(u -> problems.add("not matched: " + u));
        problems.addAll(ZoneValidator.validate(merged.set(), border));
        if (merged.changes().isEmpty()) {
            return new Result(true, List.of(), problems, null);
        }
        String backup = ZoneManager.FILE + ".bak-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        try {
            Files.copy(file.toPath(), new File(folder, backup).toPath(), StandardCopyOption.REPLACE_EXISTING);
            File tmp = new File(folder, ZoneManager.FILE + ".tmp");
            Files.writeString(tmp.toPath(), ZoneYaml.write(merged.set()));
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            return new Result(false, List.of(), List.of("could not write " + ZoneManager.FILE + ": " + e.getMessage()), null);
        }
        log.info("Depths zones: merged the builders' layout (" + merged.changes().size() + " changes, backup " + backup + ")");
        reload.run();
        return new Result(true, merged.changes(), problems, backup);
    }
}
