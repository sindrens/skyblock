package net.skybornmc.skyblock.motd;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.skybornmc.skyblock.common.config.ConfigManager;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Server list MOTD with hex colors, animated gradients (one frame per list refresh), rotation between
 * MOTDs, auto-centering and hover info lines. Configured in {@code motd.yml}.
 */
public final class MotdModule implements SkyblockModule, Listener {

    private record Motd(String id, String line1, String line2) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final MotdConfig config = new MotdConfig();
    private final MotdRenderer renderer = new MotdRenderer();

    private volatile List<Motd> active = List.of();
    private volatile List<String> hoverLines = List.of();
    private volatile boolean random;
    private volatile long rotateMillis;
    private volatile boolean animate;
    private volatile long cycleMillis;
    private volatile int frames;
    private volatile boolean center;
    private volatile Instant countdownTarget;
    private volatile String countdownFinished = "NOW";

    public MotdModule(JavaPlugin plugin, ConfigManager configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    @Override
    public String id() {
        return "motd";
    }

    @Override
    public void enable() {
        load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void reload() {
        load();
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        List<Motd> motds = active;
        if (!motds.isEmpty()) {
            long now = System.currentTimeMillis();
            Instant target = countdownTarget;
            List<Motd> usable = target == null
                    ? motds.stream().filter(m -> !m.line1().contains("{countdown}") && !m.line2().contains("{countdown}")).toList()
                    : motds;
            if (!usable.isEmpty()) {
                Motd motd = random
                        ? usable.get(ThreadLocalRandom.current().nextInt(usable.size()))
                        : usable.get((int) ((now / Math.max(1000, rotateMillis)) % usable.size()));
                String countdown = target == null ? "" : countdown(target, Instant.ofEpochMilli(now));
                double phase = animate ? MotdRenderer.phaseAt(now, cycleMillis, frames) : 0;
                event.motd(renderer.render(motd.line1().replace("{countdown}", countdown),
                        motd.line2().replace("{countdown}", countdown), phase, center));
            }
        }

        List<String> hover = hoverLines;
        if (!hover.isEmpty()) {
            event.setHidePlayers(false);
            List<PaperServerListPingEvent.ListedPlayerInfo> listed = event.getListedPlayers();
            listed.clear();
            for (String line : hover) {
                listed.add(new PaperServerListPingEvent.ListedPlayerInfo(line, new UUID(0, 0)));
            }
        }
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), "motd.yml");
        if (!file.exists()) {
            plugin.saveResource("motd.yml", false);
        }
        // Typed settings: validated, merged; a wrong value keeps the previous one.
        configs.load(config);

        // Free-text parts (header + message texts). If the YAML is broken, the previous MOTDs stay active.
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) {
            return;
        }
        String brand = yaml.getString("brand", yaml.getString("header", ""));
        ConfigurationSection motds = yaml.getConfigurationSection("motds");
        List<Motd> chosen = new ArrayList<>();
        for (String id : config.active) {
            ConfigurationSection section = motds == null ? null : motds.getConfigurationSection(id);
            if (section == null) {
                plugin.getLogger().warning("motd.yml → active: MOTD '" + id + "' does not exist under 'motds' (skipped).");
                continue;
            }
            chosen.add(new Motd(id, section.getString("line1", "{brand}").replace("{brand}", brand),
                    section.getString("line2", "").replace("{brand}", brand)));
        }

        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        MiniMessage mm = MiniMessage.miniMessage();
        List<String> hover = new ArrayList<>();
        for (String line : config.hover) {
            hover.add(legacy.serialize(mm.deserialize(line)));
        }

        random = config.mode == MotdConfig.Mode.RANDOM;
        rotateMillis = config.rotateSeconds * 1000L;
        animate = config.animationEnabled;
        cycleMillis = config.animationCycleSeconds * 1000L;
        frames = config.animationFrames;
        center = config.center;
        countdownFinished = config.countdownFinished;
        countdownTarget = parseTarget(config.countdownTarget, config.countdownZone);
        hoverLines = List.copyOf(hover);
        if (!chosen.isEmpty()) {
            active = List.copyOf(chosen);
        }
    }

    private Instant parseTarget(String target, String zone) {
        if (target == null || target.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(target.trim()).atZone(ZoneId.of(zone)).toInstant();
        } catch (DateTimeException e) {
            plugin.getLogger().severe("motd.yml → countdown: '" + target + "' / '" + zone
                    + "' is not a valid date-time (use 2027-06-01T18:00 and a zone like Europe/Oslo). Countdown messages are skipped.");
            return null;
        }
    }

    /** "2d 1h", "5h 12m", "12m 30s", or the finished text. */
    String countdown(Instant target, Instant now) {
        Duration left = Duration.between(now, target);
        if (left.isNegative() || left.isZero()) {
            return countdownFinished;
        }
        long days = left.toDays();
        long hours = left.toHoursPart();
        long minutes = left.toMinutesPart();
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m " + left.toSecondsPart() + "s";
    }
}
