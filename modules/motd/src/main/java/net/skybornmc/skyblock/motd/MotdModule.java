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
            Motd motd = random
                    ? motds.get(ThreadLocalRandom.current().nextInt(motds.size()))
                    : motds.get((int) ((now / Math.max(1000, rotateMillis)) % motds.size()));
            double phase = animate ? MotdRenderer.phaseAt(now, cycleMillis, frames) : 0;
            event.motd(renderer.render(motd.line1(), motd.line2(), phase, center));
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
        String header = yaml.getString("header", "");
        ConfigurationSection motds = yaml.getConfigurationSection("motds");
        List<Motd> chosen = new ArrayList<>();
        for (String id : config.active) {
            ConfigurationSection section = motds == null ? null : motds.getConfigurationSection(id);
            if (section == null) {
                plugin.getLogger().warning("motd.yml → active: MOTD '" + id + "' does not exist under 'motds' (skipped).");
                continue;
            }
            chosen.add(new Motd(id, section.getString("line1", header), section.getString("line2", "")));
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
        hoverLines = List.copyOf(hover);
        if (!chosen.isEmpty()) {
            active = List.copyOf(chosen);
        }
    }
}
