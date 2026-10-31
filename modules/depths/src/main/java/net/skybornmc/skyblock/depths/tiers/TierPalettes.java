package net.skybornmc.skyblock.depths.tiers;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.skills.SkillLevelUpEvent;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.depths.DepthsConfig;
import net.skybornmc.skyblock.depths.engine.BlockKeys;
import net.skybornmc.skyblock.depths.engine.DepthsEngine;
import net.skybornmc.skyblock.depths.engine.PaletteMath;
import net.skybornmc.skyblock.depths.engine.ResourceBlock;
import net.skybornmc.skyblock.depths.engine.TierPalette;
import net.skybornmc.skyblock.depths.zones.Aspect;
import net.skybornmc.skyblock.depths.zones.Field;
import net.skybornmc.skyblock.depths.zones.Zone;
import net.skybornmc.skyblock.depths.zones.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Depths v2 tier palette, spike (DV2-SPIKE, owner DV-9). Behind {@code depths.yml → palette.enabled} (off by default).
 *
 * <p>The ore field of the pilot zone ({@code palette.zone}, mine tier 1) shows each player the block of their active
 * tier (coal / copper / iron ore at the same spots), with that tier's rewards: the tiers come from the zones listed in
 * {@code palette.tiers} (their field block, depleted look, respawn time, required-level and drop table tier). The active
 * tier is the highest one the player's Mining level opens (a staff toggle can pick a lower one: D2). It is refreshed on
 * a skill level-up (with the unlock moment: title, sound, particle wave), a profile switch, joining and the periodic
 * tier check ({@code tiers.check-ticks}); those swap quietly.</p>
 *
 * <p>Combat zones never use a palette (owner 31 Oct: simple tier areas there).</p>
 */
public final class TierPalettes implements Listener {

    /** One running unlock particle wave. */
    private static final class Wave {
        final UUID player;
        final long[] keys;
        final int[] dist2;
        int next;
        int tick;

        Wave(UUID player, long[] keys, int[] dist2) {
            this.player = player;
            this.keys = keys;
            this.dist2 = dist2;
        }
    }

    public static final String AUTO = "auto";

    private final Core core;
    private final DepthsConfig config;
    private final ZoneManager zones;
    private final DepthsEngine engine;
    private final ModuleMessages msg;
    private TierPalette palette;
    private Aspect aspect;
    private final Map<UUID, Integer> forced = new HashMap<>();
    private final List<Wave> waves = new ArrayList<>();
    private Scheduler.Task waveTask = Scheduler.Task.NONE;
    private Particle particle;

    public TierPalettes(Core core, DepthsConfig config, ZoneManager zones, DepthsEngine engine, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.zones = zones;
        this.engine = engine;
        this.msg = msg;
    }

    // ------------------------------------------------------------------ lifecycle

    /** (Re)builds the palette from depths.yml + zones.yml and sets every online player's tier (quietly). */
    public void reload() {
        stopWaves();
        engine.palettes(List.of());
        palette = null;
        aspect = null;
        if (!config.paletteEnabled) {
            return;
        }
        Consumer<String> warn = problem -> core.plugin().getLogger().warning("Depths palette: " + problem + " (palette off)");
        TierPalette built = build(warn);
        if (built == null) {
            return;
        }
        particle = particle(config.paletteWaveParticle);
        engine.palettes(List.of(built));
        palette = built;
        core.plugin().getLogger().info("Depths palette (spike) on: " + built.id() + " shows " + built.tiers().stream()
                .map(t -> t.name() + " (" + t.zoneId() + ", level " + t.requiredLevel() + ")").toList()
                + " on " + built.base().getKey().getKey() + " slots");
        SkillService skills = skills();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player, skills, null);
        }
    }

    public void stop() {
        stopWaves();
        engine.palettes(List.of());
        palette = null;
        forced.clear();
    }

    private TierPalette build(Consumer<String> warn) {
        Zone pilot = zones.zone(config.paletteZone).orElse(null);
        if (pilot == null) {
            warn.accept("palette.zone '" + config.paletteZone + "' is not a zone in zones.yml");
            return null;
        }
        if (pilot.aspect() == Aspect.COMBAT) {
            warn.accept("palette.zone '" + pilot.id() + "' is a combat zone; combat zones use simple tier areas");
            return null;
        }
        if (pilot.fields().isEmpty()) {
            warn.accept("palette.zone '" + pilot.id() + "' has no fields");
            return null;
        }
        List<TierPalette.Tier> tiers = new ArrayList<>();
        for (String id : config.paletteTiers) {
            Zone zone = zones.zone(id).orElse(null);
            if (zone == null || zone.aspect() != pilot.aspect()) {
                warn.accept("palette.tiers: '" + id + "' is not a " + pilot.aspect().id() + " zone");
                return null;
            }
            Field.Resource r = zone.fields().isEmpty() || zone.fields().getFirst().resources().isEmpty() ? null
                    : zone.fields().getFirst().resources().getFirst();
            BlockData shown = r == null ? null : ZoneManager.blockData(r.block());
            if (shown == null) {
                warn.accept("palette.tiers: zone '" + id + "' has no valid field block");
                return null;
            }
            BlockData depleted = ZoneManager.blockData(r.depleted() == null ? config.defaultDepleted : r.depleted());
            if (depleted == null) {
                depleted = Material.TUFF.createBlockData();
            }
            int respawn = (r.respawnSeconds() == null ? config.defaultRespawnSeconds : r.respawnSeconds()) * 20;
            String name = zone.resourceName() == null || zone.resourceName().isBlank() ? zone.name() : zone.resourceName();
            tiers.add(new TierPalette.Tier(zone.tier(), zone.id(), name, zone.requiredLevel(),
                    new ResourceBlock(shown.getMaterial(), depleted, respawn), shown));
        }
        if (tiers.isEmpty()) {
            warn.accept("palette.tiers is empty");
            return null;
        }
        Material base = tiers.getFirst().resource().block();
        Set<String> regions = new LinkedHashSet<>();
        for (Field field : pilot.fields()) {
            if (field.resources().stream().anyMatch(res -> base == Material.matchMaterial(res.block()))) {
                regions.add(pilot.regionId(field));
            }
        }
        if (regions.isEmpty()) {
            warn.accept("no field of '" + pilot.id() + "' holds tier 1's block " + base.getKey().getKey());
            return null;
        }
        for (TierPalette.Tier tier : tiers) {
            if (tier.resource().block().getHardness() < base.getHardness()) {
                core.plugin().getLogger().warning("Depths palette: " + tier.resource().block().getKey().getKey()
                        + " is softer than the real block " + base.getKey().getKey()
                        + "; the real block should be the softest (vanilla's own break check uses it)");
            }
        }
        aspect = pilot.aspect();
        return new TierPalette(pilot.id(), regions, base, tiers);
    }

    private static Particle particle(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(name.trim().toLowerCase(Locale.ROOT));
        Particle found = key == null ? null : Registry.PARTICLE_TYPE.get(key);
        return found != null && found.getDataType() == Void.class ? found : null;
    }

    // ------------------------------------------------------------------ active tier

    private SkillService skills() {
        return core.services().optional(SkillService.class).orElse(null);
    }

    public boolean enabled() {
        return palette != null;
    }

    public TierPalette palette() {
        return palette;
    }

    /** Called by the periodic tier check of {@link TierGates} (quiet). */
    public void onCheck(Player player, SkillService skills) {
        if (palette != null) {
            refresh(player, skills, null);
        }
    }

    /**
     * Sets the player's active tier from their level. {@code levelUpTo} = the new level of a level-up in the palette's
     * skill (plays the unlock moment when the tier goes up), {@code null} = quiet.
     */
    private DepthsEngine.SwapResult refresh(Player player, SkillService skills, Integer levelUpTo) {
        if (palette == null) {
            return null;
        }
        Integer level = levelUpTo != null ? levelUpTo
                : skills == null || !skills.available(aspect.skill()) ? null : skills.level(player, aspect.skill());
        int target = PaletteMath.activeTier(level, palette.requiredLevels(), forced.getOrDefault(player.getUniqueId(), PaletteMath.AUTO));
        DepthsEngine.SwapResult result = engine.paletteTier(player, palette.id(), target);
        if (result != null && PaletteMath.isUnlock(result.oldTier(), result.newTier(), levelUpTo != null)) {
            celebrate(player, result);
        }
        return result;
    }

    /** The wing tier whose rewards apply to {@code player} in {@code zone} (ambient mobs). */
    public int tierFor(Player player, Zone zone) {
        if (palette == null || zone == null || !zone.id().equals(palette.id())) {
            return zone == null ? 0 : zone.tier();
        }
        return palette.tier(engine.paletteTier(player, palette.id())).wingTier();
    }

    private boolean inPilotZone(Player player) {
        Zone here = zones.zoneAt(player.getLocation());
        return here != null && palette != null && here.id().equals(palette.id());
    }

    // ------------------------------------------------------------------ unlock moment

    private void celebrate(Player player, DepthsEngine.SwapResult result) {
        if (config.paletteEffectsOnlyInZone && !inPilotZone(player)) {
            return;
        }
        TierPalette.Tier tier = palette.tier(result.newTier());
        msg.title(player, "palette.unlocked-" + aspect.id(), Msg.p("name", tier.name()), Msg.p("tier", tier.wingTier()));
        if (config.paletteUnlockSound != null && !config.paletteUnlockSound.isBlank()) {
            core.sounds().play(player, config.paletteUnlockSound);
        }
        if (particle != null && result.keys().length > 0) {
            waves.add(new Wave(player.getUniqueId(), result.keys(), result.dist2()));
            if (waveTask == Scheduler.Task.NONE) {
                waveTask = core.scheduler().repeat("depths-palette-wave", 1L, 1L, this::waveTick);
            }
        }
    }

    /** One ring of every running wave: particles over the slots between the last radius and the new one. */
    private void waveTick() {
        double speed = config.paletteWaveSpeed;
        int maxRadius = config.paletteWaveRadius;
        int perTick = config.paletteWaveMaxPerTick;
        for (Iterator<Wave> it = waves.iterator(); it.hasNext(); ) {
            Wave wave = it.next();
            Player player = Bukkit.getPlayer(wave.player);
            double radius = Math.min(maxRadius, (wave.tick + 1) * speed);
            if (player == null || particle == null) {
                it.remove();
                continue;
            }
            int end = PaletteMath.ringEnd(wave.dist2, wave.next, radius);
            int count = end - wave.next;
            int step = Math.max(1, (count + perTick - 1) / Math.max(1, perTick));
            for (int i = wave.next; i < end; i += step) {
                long key = wave.keys[i];
                player.spawnParticle(particle, BlockKeys.x(key) + 0.5, BlockKeys.y(key) + 1.1, BlockKeys.z(key) + 0.5,
                        1, 0.2, 0.05, 0.2, 0.01);
            }
            wave.next = end;
            wave.tick++;
            if (radius >= maxRadius || wave.next >= wave.keys.length) {
                it.remove();
            }
        }
        if (waves.isEmpty()) {
            waveTask.cancel();
            waveTask = Scheduler.Task.NONE;
        }
    }

    private void stopWaves() {
        waves.clear();
        waveTask.cancel();
        waveTask = Scheduler.Task.NONE;
    }

    // ------------------------------------------------------------------ events

    /** Before TierGates' MONITOR handler re-checks (which would swap quietly): this one plays the unlock moment. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLevelUp(SkillLevelUpEvent event) {
        if (palette != null && event.getSkill() == aspect.skill()) {
            refresh(event.getPlayer(), skills(), event.getNewLevel());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileSwitched(ProfileSwitchedEvent event) {
        forced.remove(event.getPlayer().getUniqueId());
        refresh(event.getPlayer(), skills(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer(), skills(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forced.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ /depths palette (staff/debug, spike only)

    /** Tab choices of the tier argument: auto, 1..n. */
    public List<String> tierChoices() {
        List<String> out = new ArrayList<>();
        out.add(AUTO);
        int n = palette == null ? config.paletteTiers.size() : palette.size();
        for (int i = 1; i <= n; i++) {
            out.add(Integer.toString(i));
        }
        return out;
    }

    /** {@code /depths palette [tier] [player]}: no tier = status; a tier = D2 lower-tier toggle (clamped to what is open). */
    public void command(CommandSender sender, String tierArg, Player target) {
        if (palette == null) {
            msg.send(sender, "palette.disabled");
            return;
        }
        if (tierArg != null && target != null) {
            int pick = AUTO.equalsIgnoreCase(tierArg) ? PaletteMath.AUTO : parse(tierArg);
            if (pick == PaletteMath.AUTO) {
                forced.remove(target.getUniqueId());
            } else {
                forced.put(target.getUniqueId(), pick);
            }
            refresh(target, skills(), null);
            int now = engine.paletteTier(target, palette.id());
            TierPalette.Tier tier = palette.tier(now);
            Map<String, Object> details = new java.util.LinkedHashMap<>();
            details.put("palette", palette.id());
            details.put("picked", tierArg.toLowerCase(Locale.ROOT));
            details.put("shown_tier", now);
            core.audit().logPlayer(AuditCategory.WORLD, "depths_palette_tier",
                    Actor.of(sender), target.getUniqueId(),
                    target.getName(), null, null, details);
            msg.send(sender, "palette.set", Msg.p("player", target.getName()), Msg.p("name", tier.name()),
                    Msg.p("tier", now), Msg.c("mode", msg.component(sender, pick == PaletteMath.AUTO ? "palette.mode-auto" : "palette.mode-picked")));
            return;
        }
        status(sender, target);
    }

    private static int parse(String text) {
        try {
            return Math.max(1, Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return PaletteMath.AUTO;
        }
    }

    private void status(CommandSender sender, Player target) {
        msg.send(sender, "palette.info.header");
        msg.send(sender, "palette.info.zone", Msg.p("zone", palette.id()), Msg.p("regions", palette.regionIds().size()),
                Msg.p("block", palette.base().getKey().getKey()));
        msg.send(sender, "palette.info.tiers", Msg.p("tiers", String.join(", ", palette.tiers().stream()
                .map(t -> t.name() + " " + t.requiredLevel()).toList())));
        if (target != null) {
            SkillService skills = skills();
            Integer level = skills == null || !skills.available(aspect.skill()) ? null : skills.level(target, aspect.skill());
            int now = engine.paletteTier(target, palette.id());
            Integer pick = forced.get(target.getUniqueId());
            msg.send(sender, "palette.info.player", Msg.p("player", target.getName()), Msg.p("name", palette.tier(now).name()),
                    Msg.p("tier", now), Msg.p("max", palette.size()), Msg.p("level", level == null ? 0 : level),
                    Msg.c("mode", msg.component(sender, pick == null ? "palette.mode-auto" : "palette.mode-picked")));
        }
        DepthsEngine.PaletteStats s = engine.paletteStats();
        msg.send(sender, "palette.info.swaps", Msg.p("swaps", s.swaps()), Msg.p("blocks", s.lastBlocks()),
                Msg.p("ms", String.format(Locale.ROOT, "%.2f", s.lastMillis())),
                Msg.p("max", String.format(Locale.ROOT, "%.2f", s.maxMillis())), Msg.p("ticks", s.lastDrainTicks()),
                Msg.p("total", String.format(Locale.ROOT, "%.0f", s.lastDrainTotalMillis())));
        msg.send(sender, "palette.info.index", Msg.p("chunks", s.indexedChunks()), Msg.p("scans", s.scans()),
                Msg.p("running", s.swapping()));
    }
}
