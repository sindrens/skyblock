package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.skills.SkillXpGainEvent;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Set levels at runtime (WARDROBE §3, W2): where set XP comes from, who earns it, level-ups and their feedback, saving
 * earned XP in batches, and the level-50 trail. Main thread.
 *
 * <p>XP sources fed here without touching other modules: Depths ore blocks and logs and Depths mob kills (the skills
 * module's {@link SkillXpGainEvent}, sources {@code depths_block}, {@code depths_log}, {@code mob_kill}; stacked kills
 * ({@code stack_kill}) give nothing, WD12), island generator blocks and logs of trees that grew (only blocks remembered
 * from {@link BlockFormEvent} / {@link StructureGrowEvent}, so placed blocks never count), fully grown crops, and
 * fishing once it is available. Selling, bosses and conquest call {@code WardrobeService#setXp} from their modules.</p>
 *
 * <p>Earned XP goes into the session at once (levels and bonuses update immediately) and is saved every
 * {@code xp.flush-seconds}, on quit and on shutdown with an additive update, so a crash loses at most that window and
 * never duplicates XP.</p>
 */
final class Leveling implements Listener {

    static final String SETTING_TRAIL = "wardrobe-trail";
    /** F1: the shared /settings toggle "Particles" (see other players' cosmetic particles), owned here for now. */
    static final String SETTING_PARTICLES = "particles";

    private final WardrobeModule module;
    private BlockMemory generated;
    private BlockMemory grownLogs;
    private Set<Material> crops = EnumSet.noneOf(Material.class);
    private Set<Material> generatorBlocks = EnumSet.noneOf(Material.class);
    /** Set id → XP activity → rate (rebuilt on reload). */
    private Map<String, Map<String, Double>> sources = Map.of();
    private Map<String, Double> caps = Map.of();
    private Scheduler.Task flushTask;
    private Scheduler.Task trailTask;
    private volatile PlayerSettings settings;

    Leveling(WardrobeModule module) {
        this.module = module;
        reload();
    }

    /** Rebuilds the cached config views (sources per set, caps, block lists). */
    void reload() {
        WardrobeConfig c = module.config();
        Map<String, Map<String, Double>> next = new HashMap<>();
        for (SetDef def : module.registry().all()) {
            next.put(def.id(), def.enabled() ? c.sources(def.activity()) : Map.of());
        }
        sources = next;
        caps = c.hourlyCapMap();
        crops = materials(c.crops, "xp.crops");
        generatorBlocks = materials(c.generatorBlocks, "xp.generator-blocks");
        if (generated == null || grownLogs == null) {
            generated = new BlockMemory(c.trackedBlocks);
            grownLogs = new BlockMemory(c.trackedBlocks);
        }
    }

    private Set<Material> materials(List<String> raw, String path) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        for (String s : raw) {
            Material m = Material.matchMaterial(s.trim());
            if (m == null || !m.isBlock()) {
                module.core.plugin().getLogger().warning("wardrobe.yml → " + path + ": '" + s + "' is not a block (skipped)");
            } else {
                out.add(m);
            }
        }
        return out;
    }

    void start() {
        long flushTicks = Math.max(5, module.config().flushSeconds) * 20L;
        flushTask = module.core.scheduler().repeat("wardrobe-xp-flush", flushTicks, flushTicks, this::flushAll);
        long trail = Math.max(2, module.config().trailInterval);
        trailTask = module.core.scheduler().repeat("wardrobe-trail", trail, trail, this::trails);
        // The menus module (which publishes PlayerSettings) enables after us; register the trail toggle one tick later.
        module.core.scheduler().later("wardrobe-settings", 1L, () -> module.core.services().optional(PlayerSettings.class).ifPresent(s -> {
            try {
                s.register(new SettingToggle(WardrobeModule.ID, SETTING_TRAIL, SettingCategory.DISPLAY, Material.BLAZE_POWDER, true, null,
                        WardrobeModule.ID, "settings.trail", false));
            } catch (IllegalStateException | IllegalArgumentException e) {
                module.core.plugin().getLogger().fine("Wardrobe: trail toggle already registered: " + e.getMessage());
            }
            try {
                s.register(new SettingToggle(WardrobeModule.ID, SETTING_PARTICLES, SettingCategory.DISPLAY, Material.BLAZE_POWDER, true, null,
                        "navigation", "settings.toggles." + SETTING_PARTICLES, false));
            } catch (IllegalStateException | IllegalArgumentException e) {
                module.core.plugin().getLogger().fine("Wardrobe: particles toggle already registered: " + e.getMessage());
            }
            settings = s;
        }));
    }

    /** Saves every session's earned XP, blocking (shutdown). */
    void stop() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        if (trailTask != null) {
            trailTask.cancel();
        }
        module.core.services().optional(PlayerSettings.class).ifPresent(s -> s.unregisterAll(WardrobeModule.ID));
        Map<UUID, Double> all = new HashMap<>();
        for (Session s : module.equipment().sessions()) {
            s.pendingXp.forEach((id, xp) -> all.merge(id, xp, Double::sum));
            s.pendingXp.clear();
        }
        if (!all.isEmpty() && !module.ops().flushXpNow(all, module.config().levels())) {
            module.core.plugin().getLogger().severe("Wardrobe: set XP of " + all.size() + " set(s) could not be saved on shutdown");
        }
    }

    // ------------------------------------------------------------------ saving

    void flushAll() {
        for (Session s : module.equipment().sessions()) {
            flush(s);
        }
    }

    /** Sends the session's earned XP to the database; on failure it is put back for the next try. */
    void flush(Session s) {
        if (s.pendingXp.isEmpty()) {
            return;
        }
        Map<UUID, Double> snapshot = new HashMap<>(s.pendingXp);
        s.pendingXp.clear();
        module.ops().flushXp(snapshot, module.config().levels()).thenAccept(ok -> {
            if (!ok) {
                module.core.scheduler().main("wardrobe-xp-retry", () -> {
                    Session now = module.equipment().sessionOrNull(s.player);
                    Session target = now != null ? now : s;
                    snapshot.forEach((id, xp) -> target.pendingXp.merge(id, xp, Double::sum));
                });
            }
        });
    }

    /** Before the session is dropped (Equipment's quit handler runs at MONITOR, after this). */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Session s = module.equipment().sessionOrNull(event.getPlayer().getUniqueId());
        if (s != null) {
            flush(s);
        }
    }

    /** After a (re)load from the database: XP earned but not saved yet is added on top again. */
    void applyPending(Session s) {
        if (s.pendingXp.isEmpty()) {
            return;
        }
        SetLevels levels = module.config().levels();
        for (Map.Entry<UUID, Double> e : s.pendingXp.entrySet()) {
            s.entry(e.getKey()).ifPresent(entry -> {
                double xp = levels.clamp(entry.xp() + e.getValue(), entry.isMerged());
                s.replace(entry.withXp(xp, levels.level(xp, entry.isMerged())));
            });
        }
    }

    // ------------------------------------------------------------------ earning

    /** {@code WardrobeService#setXp}: an action id (amount = units) or a plain XP activity (amount = raw XP). */
    void api(Player player, String action, double amount) {
        if (player == null || action == null || !(amount > 0) || !Double.isFinite(amount)) {
            return;
        }
        Optional<SetXp.Action> a = SetXp.Action.byId(action);
        if (a.isPresent()) {
            gain(player, a.get(), amount);
            return;
        }
        String activity = action.trim().toLowerCase(Locale.ROOT);
        if (SetXp.ACTIVITIES.contains(activity)) {
            earn(player, activity, amount);
        }
    }

    void gain(Player player, SetXp.Action action, double units) {
        earn(player, action.activity(), module.config().actionXp(action) * units);
    }

    /** Gives {@code xp} of an XP activity to the worn sets that earn from it. */
    void earn(Player player, String activity, double xp) {
        if (!module.config().levelsEnabled || !(xp > 0)) {
            return;
        }
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (s == null || !s.loaded || s.stripped || s.equipped.isEmpty()) {
            return;
        }
        double allowed = s.caps.allow(activity, xp, caps.getOrDefault(activity, 0.0), System.currentTimeMillis());
        if (!(allowed > 0)) {
            return;
        }
        List<SetXp.WornEntry> worn = new ArrayList<>();
        for (Map.Entry<UUID, Integer> w : s.wornPerEntry().entrySet()) {
            s.entry(w.getKey()).ifPresent(e -> worn.add(new SetXp.WornEntry(e.entryId(), e.setId(), e.mergedSetId(), w.getValue())));
        }
        Map<UUID, Double> split = SetXp.split(worn, activity, allowed, id -> sources.getOrDefault(id, Map.of()), module.config().shares());
        if (split.isEmpty()) {
            return;
        }
        SetLevels levels = module.config().levels();
        UUID shown = null;
        double best = 0;
        for (Map.Entry<UUID, Double> g : split.entrySet()) {
            Optional<WardrobeStore.Entry> found = s.entry(g.getKey());
            if (found.isEmpty()) {
                continue;
            }
            WardrobeStore.Entry e = found.get();
            double xpNow = levels.clamp(e.xp() + g.getValue(), e.isMerged());
            double added = xpNow - e.xp();
            if (!(added > 0)) {
                continue; // max level
            }
            s.pendingXp.merge(e.entryId(), added, Double::sum);
            s.replace(e.withXp(xpNow, levels.level(xpNow, e.isMerged())));
            afterChange(player, s, e.entryId(), e.level());
            if (added > best) {
                best = added;
                shown = e.entryId();
            }
        }
        if (shown != null) {
            bar(player, s, shown, best);
        }
    }

    /** After an entry's XP changed: level-up feedback, bonuses, pieces (trim, glint, lore) and the open menu. */
    void afterChange(Player player, Session s, UUID entryId, int levelBefore) {
        Optional<WardrobeStore.Entry> found = s.entry(entryId);
        if (found.isEmpty() || found.get().level() <= levelBefore) {
            return;
        }
        WardrobeStore.Entry e = found.get();
        int level = e.level();
        boolean title = false;
        for (int l = levelBefore + 1; l <= level; l++) {
            title |= module.config().titleLevel(l);
        }
        String name = module.text().entryName(module.registry(), e);
        String mult = WardrobeText.number(module.bonusMultiplier(level));
        if (title) {
            module.msg().title(player, "level-title", Msg.p("set", name), Msg.p("level", level), Msg.p("multiplier", mult));
        } else {
            module.msg().actionBar(player, "level-up", Msg.p("set", name), Msg.p("level", level), Msg.p("multiplier", mult));
        }
        if (level >= module.config().maxLevel) {
            module.msg().send(player, "level-max", Msg.p("set", name), Msg.p("level", level));
        }
        s.lastBar = System.currentTimeMillis();
        module.core.sounds().play(player, "level-up");
        module.equipment().refreshBonuses(s);
        module.equipment().rerender(player, s, entryId::equals);
        module.core.audit().logPlayer(AuditCategory.ITEM, "wardrobe_level_up", Actor.system("wardrobe"), player.getUniqueId(),
                player.getName(), null, null, Map.of("set", e.setId(), "merged", String.valueOf(e.mergedSetId()), "entry", entryId.toString(),
                        "from", levelBefore, "to", level, "profile", s.profile.toString()));
        module.menus().refreshOpen(player);
    }

    /** At most one "set XP" action bar line per {@code xp.bar-seconds}. */
    private void bar(Player player, Session s, UUID entryId, double gained) {
        int seconds = module.config().barSeconds;
        long now = System.currentTimeMillis();
        if (seconds <= 0 || now - s.lastBar < seconds * 1000L) {
            return;
        }
        s.entry(entryId).ifPresent(e -> {
            s.lastBar = now;
            SetLevels levels = module.config().levels();
            module.msg().actionBar(player, "xp-bar", Msg.p("set", module.text().entryName(module.registry(), e)),
                    Msg.p("level", e.level()), Msg.p("percent", (int) Math.floor(levels.progress(e.xp(), e.isMerged()) * 100)),
                    Msg.p("xp", WardrobeText.number(gained)));
        });
    }

    // ------------------------------------------------------------------ sources

    /** Depths blocks/logs and Depths kills, through the skills module's XP event (after boosters, only if not cancelled). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkillXp(SkillXpGainEvent event) {
        if (!(event.getAmount() > 0)) {
            return;
        }
        Player player = event.getPlayer();
        switch (event.getSource()) {
            case "depths_block" -> gain(player, SetXp.Action.DEPTHS_ORE, 1);
            case "depths_log" -> gain(player, SetXp.Action.DEPTHS_LOG, 1);
            case "mob_kill" -> {
                if (module.config().combatWorld(player.getWorld().getName())) {
                    gain(player, SetXp.Action.DEPTHS_KILL, 1);
                }
            }
            default -> {
                // stack_kill (WD12) and everything else: no set XP
            }
        }
    }

    /** Remembers blocks an island generator made (only those give island mining XP). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        Block b = event.getBlock();
        if (module.config().islandWorld(b.getWorld().getName()) && generatorBlocks.contains(event.getNewState().getType())) {
            generated.add(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    /** Remembers the logs of a tree that grew (only those give island foraging XP). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent event) {
        if (!module.config().islandWorld(event.getWorld().getName())) {
            return;
        }
        UUID world = event.getWorld().getUID();
        for (BlockState state : event.getBlocks()) {
            if (Tag.LOGS.isTagged(state.getType())) {
                grownLogs.add(world, state.getX(), state.getY(), state.getZ());
            }
        }
    }

    /** A placed block is never a generator block or a grown log, whatever was there before. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block b = event.getBlockPlaced();
        generated.remove(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        grownLogs.remove(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (!module.config().islandWorld(b.getWorld().getName())) {
            return;
        }
        Player player = event.getPlayer();
        UUID world = b.getWorld().getUID();
        if (generated.remove(world, b.getX(), b.getY(), b.getZ())) {
            gain(player, SetXp.Action.ISLAND_ORE, 1);
            return;
        }
        if (grownLogs.remove(world, b.getX(), b.getY(), b.getZ())) {
            gain(player, SetXp.Action.ISLAND_LOG, 1);
            return;
        }
        if (crops.contains(b.getType()) && b.getBlockData() instanceof Ageable age && age.getAge() >= age.getMaximumAge()) {
            if (module.config().cropsNeedHoe && !Tag.ITEMS_HOES.isTagged(player.getInventory().getItemInMainHand().getType())) {
                return;
            }
            gain(player, SetXp.Action.CROP, 1);
        }
    }

    /** Fishing XP once fishing is a feature on this server (Phase 7). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && module.config().featureAvailable("fishing")) {
            gain(event.getPlayer(), SetXp.Action.FISH, 1);
        }
    }

    // ------------------------------------------------------------------ trail (level 50)

    /** Whether the player gets the trail now: all 4 pieces of one entry at the trail level, not in the warzone, toggle on. */
    boolean trailActive(Player player, Session s) {
        if (s == null || !s.loaded || s.warzone || !module.config().levelsEnabled) {
            return false;
        }
        if (!trailOn(player)) {
            return false;
        }
        return trailEntry(s).isPresent();
    }

    /** The viewer's "Particles" toggle (on while the settings service is not there). */
    boolean particlesOn(Player viewer) {
        PlayerSettings ps = settings;
        return ps == null || ps.get(viewer, SETTING_PARTICLES);
    }

    /**
     * Whether a viewer gets a trail puff (pure, for tests): the owner always sees their own trail (they control it with
     * the wardrobe-trail toggle); others only with "Particles" on, when they can see the owner and are within range.
     */
    static boolean showsTrail(boolean self, boolean viewerWantsParticles, boolean canSee, double distanceSquared, double range) {
        if (self) {
            return true;
        }
        return viewerWantsParticles && canSee && distanceSquared <= range * range;
    }

    boolean trailOn(Player player) {
        PlayerSettings ps = settings;
        return ps == null || ps.get(player, SETTING_TRAIL);
    }

    /** The entry with all 4 pieces worn at the trail level, if any. */
    Optional<WardrobeStore.Entry> trailEntry(Session s) {
        for (Map.Entry<UUID, Integer> w : s.wornPerEntry().entrySet()) {
            if (w.getValue() >= 4) {
                Optional<WardrobeStore.Entry> e = s.entry(w.getKey());
                if (e.isPresent() && e.get().level() >= module.config().trailLevel) {
                    return e;
                }
            }
        }
        return Optional.empty();
    }

    void toggleTrail(Player player) {
        PlayerSettings ps = settings;
        if (ps == null) {
            module.fail(player, "error");
            return;
        }
        boolean next = !ps.get(player, SETTING_TRAIL);
        ps.set(player, SETTING_TRAIL, next);
        module.msg().send(player, next ? "trail-on" : "trail-off");
        module.core.sounds().play(player, "click");
    }

    private final Map<String, Color> colors = new LinkedHashMap<>();

    private void trails() {
        Particle particle;
        try {
            particle = Particle.valueOf(module.config().trailParticle.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return;
        }
        for (Session s : module.equipment().sessions()) {
            Player p = Bukkit.getPlayer(s.player);
            if (p == null || !trailActive(p, s) || hidden(p)) {
                continue;
            }
            Location at = p.getLocation();
            Location last = s.lastTrail;
            s.lastTrail = at;
            if (last == null || !last.getWorld().equals(at.getWorld()) || last.distanceSquared(at) < 0.04) {
                continue; // only while moving
            }
            Optional<WardrobeStore.Entry> entry = trailEntry(s);
            Particle.DustOptions dust = null;
            if (particle == Particle.DUST) {
                dust = new Particle.DustOptions(color(entry.map(WardrobeStore.Entry::look).orElse(null)), 1.0f);
            } else if (particle.getDataType() != Void.class) {
                continue;
            }
            Location puff = at.clone().add(0, 0.1, 0);
            double range = module.config().trailViewDistance;
            // F1: sent per viewer, so players who turned "Particles" off never get other players' trails.
            for (Player viewer : p.getWorld().getPlayers()) {
                boolean self = viewer.equals(p);
                if (!showsTrail(self, self || particlesOn(viewer), self || viewer.canSee(p),
                        viewer.getLocation().distanceSquared(at), range)) {
                    continue;
                }
                try {
                    if (dust != null) {
                        viewer.spawnParticle(particle, puff, module.config().trailCount, 0.2, 0.05, 0.2, 0, dust);
                    } else {
                        viewer.spawnParticle(particle, puff, module.config().trailCount, 0.2, 0.05, 0.2, 0);
                    }
                } catch (RuntimeException e) {
                    module.core.plugin().getLogger().log(Level.FINE, "Wardrobe: trail particle failed", e);
                }
            }
        }
    }

    /** Invisible, spectating or vanished players ("vanished" metadata, the essentials convention) never show a trail. */
    @SuppressWarnings("deprecation") // the "vanished" metadata is the convention the essentials module keeps using
    private static boolean hidden(Player p) {
        if (p.isInvisible() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return true;
        }
        return p.hasMetadata("vanished") && p.getMetadata("vanished").stream().anyMatch(org.bukkit.metadata.MetadataValue::asBoolean);
    }

    private Color color(String lookSetId) {
        Optional<SetDef> def = module.registry().get(lookSetId);
        if (def.isPresent() && def.get().look().dye() >= 0) {
            return Color.fromRGB(def.get().look().dye());
        }
        return colors.computeIfAbsent(module.config().trailColor, raw -> {
            try {
                return Color.fromRGB(Integer.parseInt(raw.trim().replace("#", ""), 16) & 0xFFFFFF);
            } catch (NumberFormatException e) {
                return Color.ORANGE;
            }
        });
    }
}
