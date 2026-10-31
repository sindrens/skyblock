package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolKind;
import net.skybornmc.skyblock.common.tools.ToolService;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Tools (M4.2, DESIGN §5.1): the Quartermaster's starter kit (every 24 h, only missing pieces), the Forge ({@code /forge},
 * tiers following the Depths tiers, upgrades keep progress), living tools (levels 1-50, perk slots at 5/15/30/45 and a
 * prestige slot), blueprints (rare drops → special tools with one ability) and skins (cosmetic collection).
 * Publishes {@link ToolService}. Settings and catalog: {@code tools.yml}; texts: {@code messages/tools.yml};
 * data: {@code db/tools}.
 */
public final class ToolsModule implements SkyblockModule {

    static final String ID = "tools";
    static final String PERM_MENU = "skyblock.tools.menu";
    static final String PERM_FORGE = "skyblock.tools.forge";
    static final String PERM_GIVE = "skyblock.tools.admin.give";
    static final String PERM_BLUEPRINT = "skyblock.tools.admin.blueprint";
    static final String PERM_SKIN = "skyblock.tools.admin.skin";

    final Core core;
    private final ToolsConfig config = new ToolsConfig();
    private final ToolRepository repository = new ToolRepository();
    private ModuleMessages msg;
    private volatile ToolCatalog catalog = ToolCatalog.empty();
    private volatile ToolCurve curve = new ToolCurve(400, 1.21, 50);
    private ToolItems items;
    private ToolTracker tracker;
    private Forge forge;
    private BlueprintDrops drops;
    private ToolsListener listener;
    private DepthsHooks depthsHooks;
    private Harvester harvester;
    private Scheduler.Task saveTask;

    public ToolsModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        core.configs().load(config);
        msg = core.messages(ID);
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        catalog = loadCatalog();
        curve = buildCurve();
        items = new ToolItems(this);
        items.register();
        tracker = new ToolTracker(this);
        forge = new Forge(this);
        drops = new BlueprintDrops(this);
        core.services().register(ID, ToolService.class, tracker);
        listener = new ToolsListener(this);
        Bukkit.getPluginManager().registerEvents(listener, core.plugin());
        harvester = new Harvester(this);
        harvester.apply();
        Bukkit.getPluginManager().registerEvents(harvester, core.plugin());
        registerDepthsHooks();
        registerCommands();
        scheduleSave();
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        catalog = loadCatalog();
        curve = buildCurve();
        if (harvester != null) {
            harvester.apply();
        }
        scheduleSave();
    }

    @Override
    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        if (harvester != null) {
            HandlerList.unregisterAll(harvester);
            harvester = null;
        }
        if (depthsHooks != null) {
            HandlerList.unregisterAll(depthsHooks);
            depthsHooks = null;
        }
        if (tracker != null) {
            Bukkit.getOnlinePlayers().forEach(tracker::renderAll);
            tracker.flushBlocking();
        }
        if (items != null) {
            items.unregister();
        }
    }

    // ------------------------------------------------------------------ accessors

    ToolsConfig config() {
        return config;
    }

    ToolCatalog catalog() {
        return catalog;
    }

    ToolCurve curve() {
        return curve;
    }

    ToolItems items() {
        return items;
    }

    ToolTracker tracker() {
        return tracker;
    }

    Forge forge() {
        return forge;
    }

    BlueprintDrops drops() {
        return drops;
    }

    ToolRepository repository() {
        return repository;
    }

    ModuleMessages msg() {
        return msg;
    }

    /** The harvester hoe (null before enable). */
    Harvester harvester() {
        return harvester;
    }

    /**
     * Tool types the Forge offers right now (G2: hide what is not built): hoe tiers only while farming is on (a
     * {@code FarmingService} is registered, {@code crops.yml → enabled}), rod tiers only while the Fishing skill is open
     * (M7.8). The starter pieces stay in the starter kit either way.
     */
    boolean forgeOpen(ToolKind kind) {
        boolean farming = net.skybornmc.skyblock.common.production.FarmingService.of(core.services()).isPresent();
        boolean fishing = core.services().optional(net.skybornmc.skyblock.common.skills.SkillService.class)
                .map(s -> s.available(net.skybornmc.skyblock.common.skills.SkillType.FISHING)).orElse(false);
        return openKind(kind, farming, fishing);
    }

    static boolean openKind(ToolKind kind, boolean farming, boolean fishing) {
        return switch (kind) {
            case HOE -> farming;
            case ROD -> fishing;
            default -> true;
        };
    }

    /** The tool types shown as Forge tabs, in catalog order. */
    List<ToolKind> forgeKinds() {
        return catalog.kinds().stream().filter(this::forgeOpen).toList();
    }

    /** The Depths bridge, or null when the depths module is not in this build. */
    ToolsListener.Depths depths() {
        return depthsHooks;
    }

    // ------------------------------------------------------------------ setup

    private ToolCurve buildCurve() {
        try {
            return new ToolCurve(config.curveBase, config.curveGrowth, config.maxLevel);
        } catch (IllegalArgumentException e) {
            core.plugin().getLogger().warning("tools.yml curve: " + e.getMessage() + "; using 400 / 1.21 / 50");
            return new ToolCurve(400, 1.21, 50);
        }
    }

    /** Depths XP and blueprint drops only if the depths classes are in the jar (see {@link DepthsHooks}). */
    private void registerDepthsHooks() {
        try {
            depthsHooks = new DepthsHooks(this);
            Bukkit.getPluginManager().registerEvents(depthsHooks, core.plugin());
        } catch (NoClassDefFoundError e) {
            depthsHooks = null;
            core.plugin().getLogger().info("Tools: the depths module is not in this build; Depths tool XP and blueprint drops are off.");
        }
    }

    private void scheduleSave() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        long ticks = Math.max(5, config.saveIntervalSeconds) * 20L;
        saveTask = core.scheduler().repeat("tools-save", ticks, ticks, () -> tracker.flushAsync());
    }

    /** Reads the catalog sections of tools.yml; missing sections are copied from tools-defaults.yml first. */
    private ToolCatalog loadCatalog() {
        File file = new File(core.plugin().getDataFolder(), config.fileName());
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (file.exists()) {
                yaml.load(file);
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().warning("tools.yml is not valid YAML, using the default tool catalog. "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return ToolCatalog.load(defaults(), w -> core.plugin().getLogger().warning(w));
        }
        YamlConfiguration defaults = defaults();
        boolean changed = false;
        int catalogVersion = yaml.getInt(CATALOG_VERSION_KEY, 1);
        if (file.exists() && catalogVersion < 3) {
            // v2 (M4.9): Forge and blueprint recipes use the Depths drop items instead of vanilla items.
            // v3 (M4.8): sword recipes use the combat drops.
            backup(file, "tools-catalog-v" + catalogVersion + "-backup.yml");
            for (String section : List.of("tiers", "blueprints")) {
                replaceEntries(yaml, defaults, section);
            }
            core.plugin().getLogger().info("tools.yml: tool tiers and blueprints updated to catalog v" + CATALOG_VERSION
                    + " (Depths drop recipes); the old file is kept as tools-catalog-v" + catalogVersion + "-backup.yml");
            changed = true;
        } else if (file.exists() && catalogVersion < 4) {
            // v4 (PHASE-7 M7.4): hoe and rod tiers (only lists that still hold just the starter piece; owner edits stay).
            List<String> filled = new ArrayList<>();
            for (ToolKind kind : List.of(ToolKind.HOE, ToolKind.ROD)) {
                if (fillStarterOnlyTiers(yaml, defaults, kind)) {
                    filled.add(kind.id());
                }
            }
            if (!filled.isEmpty()) {
                backup(file, "tools-catalog-v" + catalogVersion + "-backup.yml");
                core.plugin().getLogger().info("tools.yml: tiers for " + String.join(", ", filled) + " added (catalog v4, harvester hoe "
                        + "and rod tiers); the old file is kept as tools-catalog-v" + catalogVersion + "-backup.yml");
            }
            changed = true;
        }
        if (yaml.getInt(CATALOG_VERSION_KEY, 1) != CATALOG_VERSION) {
            yaml.set(CATALOG_VERSION_KEY, CATALOG_VERSION);
            changed = true;
        }
        for (String section : ToolCatalog.SECTIONS) {
            // A section can hold plain settings too (blueprints.drops-enabled): copy the defaults when it has no entries.
            if (!hasEntries(yaml, section) && defaults.contains(section)) {
                replaceEntries(yaml, defaults, section);
                changed = true;
            }
        }
        if (changed) {
            try {
                yaml.save(file);
            } catch (IOException e) {
                core.plugin().getLogger().log(Level.WARNING, "Could not write the default tool catalog to tools.yml", e);
            }
        }
        ToolCatalog loaded = ToolCatalog.load(yaml, w -> core.plugin().getLogger().warning(w));
        for (ToolKind kind : config.starterKinds()) {
            if (loaded.tier(kind, 1).isEmpty()) {
                core.plugin().getLogger().warning("tools.yml: starter kit type '" + kind.id() + "' has no tiers; it is left out of the kit.");
            }
        }
        return loaded;
    }

    /** Version of the catalog sections; a lower version in tools.yml replaces tiers and blueprints once (backup kept). */
    static final int CATALOG_VERSION = 4;

    /**
     * Catalog v4: replaces {@code tiers.<kind>} with the default list when it holds at most one tier (the starter piece);
     * a list the owner already extended is kept. Returns true if it changed.
     */
    static boolean fillStarterOnlyTiers(org.bukkit.configuration.ConfigurationSection yaml, org.bukkit.configuration.ConfigurationSection defaults,
                                        ToolKind kind) {
        String path = "tiers." + kind.id();
        List<?> current = yaml.getList(path);
        List<?> def = defaults.getList(path);
        if (def == null || def.size() <= 1 || (current != null && current.size() > 1)) {
            return false;
        }
        yaml.set(path, def);
        return true;
    }
    static final String CATALOG_VERSION_KEY = "catalog-version";

    /** True if the section has at least one catalog entry (plain setting keys don't count). */
    static boolean hasEntries(org.bukkit.configuration.ConfigurationSection yaml, String section) {
        org.bukkit.configuration.ConfigurationSection s = yaml.getConfigurationSection(section);
        if (s == null) {
            return yaml.contains(section) && !yaml.isConfigurationSection(section);
        }
        for (String key : s.getKeys(false)) {
            if (!ToolCatalog.SETTING_KEYS.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /** Replaces the catalog entries of a section with the defaults, keeping plain setting keys. */
    static void replaceEntries(org.bukkit.configuration.ConfigurationSection yaml, org.bukkit.configuration.ConfigurationSection defaults,
                               String section) {
        java.util.Map<String, Object> keep = new java.util.LinkedHashMap<>();
        org.bukkit.configuration.ConfigurationSection current = yaml.getConfigurationSection(section);
        if (current != null) {
            for (String key : ToolCatalog.SETTING_KEYS) {
                if (current.contains(key)) {
                    keep.put(key, current.get(key));
                }
            }
        }
        yaml.set(section, null);
        org.bukkit.configuration.ConfigurationSection def = defaults.getConfigurationSection(section);
        if (def != null) {
            for (String key : def.getKeys(false)) {
                yaml.set(section + "." + key, def.get(key));
            }
        } else if (defaults.contains(section)) {
            yaml.set(section, defaults.get(section));
        }
        keep.forEach((key, value) -> yaml.set(section + "." + key, value));
    }

    private void backup(File file, String name) {
        try {
            java.nio.file.Files.copy(file.toPath(), new File(file.getParentFile(), name).toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            core.plugin().getLogger().log(Level.WARNING, "Could not back up tools.yml before the catalog update", e);
        }
    }

    private YamlConfiguration defaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("tools-defaults.yml")) {
            if (in != null) {
                yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().log(Level.SEVERE, "tools-defaults.yml in the jar is broken", e);
        }
        return yaml;
    }

    // ------------------------------------------------------------------ commands

    private List<String> kindIds() {
        return catalog.kinds().stream().map(ToolKind::id).toList();
    }

    private List<String> tierSuggestions() {
        Set<String> out = new LinkedHashSet<>();
        for (ToolKind kind : catalog.kinds()) {
            catalog.tiers(kind).forEach(t -> out.add(t.id()));
        }
        int max = catalog.kinds().stream().mapToInt(catalog::maxTier).max().orElse(1);
        for (int i = 1; i <= max; i++) {
            out.add(String.valueOf(i));
        }
        return new ArrayList<>(out);
    }

    private void registerCommands() {
        Arg<String> forgeType = Args.oneOf("type", () -> forgeKinds().stream().map(ToolKind::id).toList());
        core.commands().register(CommandSpec.command("forge")
                .description("The Forge: upgrade your tools, craft blueprints, claim the starter kit").category("Tools")
                .permission(PERM_FORGE).playerOnly().optional(forgeType).example("forge pickaxe")
                .executes(ctx -> core.menus().open(ctx.player(), new ForgeMenu(this,
                        forgeKind(ctx.has(forgeType) ? ToolKind.byId(ctx.get(forgeType)).orElse(ToolKind.PICKAXE) : heldKind(ctx.player()))))));

        Arg<Player> givePlayer = Args.onlinePlayer("player");
        Arg<String> giveTool = Args.oneOf("tool", this::kindIds);
        Arg<String> giveTier = Arg.word("tier", (sender, raw) -> raw.toLowerCase(Locale.ROOT), Suggest.of(this::tierSuggestions));
        core.commands().register(CommandSpec.command("tool")
                .description("Open the tool menu of the custom tool in your hand").category("Tools")
                .permission(PERM_MENU).example("tool")
                .executes(ctx -> {
                    Player player = ctx.player();
                    Optional<ToolData> held = ToolItems.read(player.getInventory().getItemInMainHand());
                    if (held.isEmpty()) {
                        throw new CommandFailure(ID, "tool.hold-a-tool");
                    }
                    openToolMenu(player, held.get().id());
                })
                .sub(CommandSpec.sub("give").description("Give a player a custom tool (staff)").category("Staff").permission(PERM_GIVE)
                        .arg(givePlayer).arg(giveTool).optional(giveTier).example("tool give Steve pickaxe iron")
                        .executes(ctx -> giveTool(ctx.sender(), ctx.get(givePlayer), ctx.get(giveTool), ctx.has(giveTier) ? ctx.get(giveTier) : "1"))));

        Arg<Player> bpPlayer = Args.onlinePlayer("player");
        Arg<String> bpId = Args.oneOf("blueprint", () -> new ArrayList<>(catalog.blueprints().keySet()));
        core.commands().register(CommandSpec.command("blueprint")
                .description("Blueprint staff tools").category("Staff").permission(PERM_BLUEPRINT)
                .sub(CommandSpec.sub("give").description("Give a player a blueprint (staff)").category("Staff").permission(PERM_BLUEPRINT)
                        .arg(bpPlayer).arg(bpId).example("blueprint give Steve magma_pickaxe")
                        .executes(ctx -> giveBlueprint(ctx.sender(), ctx.get(bpPlayer), ctx.get(bpId)))));

        Arg<String> skinPlayer = Args.offlinePlayer("player");
        Arg<String> skinId = Args.oneOf("skin", () -> new ArrayList<>(catalog.skins().keySet()));
        core.commands().register(CommandSpec.command("skin")
                .description("Tool skin staff tools").category("Staff").permission(PERM_SKIN)
                .sub(CommandSpec.sub("give").description("Add a tool skin to a player's collection (staff)").category("Staff").permission(PERM_SKIN)
                        .arg(skinPlayer).arg(skinId).example("skin give Steve frostbite")
                        .executes(ctx -> giveSkin(ctx.sender(), ctx.get(skinPlayer), ctx.get(skinId)))));
    }

    /** The requested Forge tab, or pickaxes when that type is not offered (yet). */
    ToolKind forgeKind(ToolKind wanted) {
        return wanted != null && catalog.kinds().contains(wanted) && forgeOpen(wanted) ? wanted : ToolKind.PICKAXE;
    }

    private ToolKind heldKind(Player player) {
        return ToolItems.read(player.getInventory().getItemInMainHand()).map(ToolData::kind)
                .filter(catalog.kinds()::contains).orElse(ToolKind.PICKAXE);
    }

    private void giveTool(CommandSender sender, Player target, String kindId, String tierRaw) {
        ToolKind kind = ToolKind.byId(kindId).orElseThrow(() -> new CommandFailure(ID, "admin.unknown-tool", Msg.p("tool", kindId)));
        ToolCatalog.TierDef tier = catalog.tier(kind, tierRaw).orElseThrow(() -> new CommandFailure(ID, "admin.unknown-tier",
                Msg.p("tier", tierRaw), Msg.p("tiers", String.join(", ", catalog.tiers(kind).stream().map(ToolCatalog.TierDef::id).toList()))));
        ToolData tool = ToolData.fresh(UUID.randomUUID(), kind, tier.tier(), false, null);
        ItemStack stack = items.create(tool, "admin:" + sender.getName(), sender.getName(), null);
        forge.give(target, stack, net.skybornmc.skyblock.common.box.BoxSource.STAFF);
        UUID owner = target.getUniqueId();
        String ownerName = target.getName();
        int season = config.seasonId;
        core.database().run(c -> repository.insert(c, tool, owner, ownerName, null, "admin:" + sender.getName(), season, System.currentTimeMillis()))
                .exceptionally(e -> {
                    core.plugin().getLogger().log(Level.WARNING, "Tools: given tool row not saved (saved on first use)", Database.cause(e));
                    return null;
                });
        String name = items.displayName(tool, stack);
        msg.send(sender, "admin.tool-given", Msg.mm("tool", name), Msg.p("player", target.getName()));
        if (!target.equals(sender)) {
            msg.send(target, "admin.tool-received", Msg.mm("tool", name));
        }
        core.audit().log(AuditCategory.STAFF, "tool_give", Actor.of(sender), tool.id().toString(), target.getName(), null, target.getLocation(),
                Map.of("kind", kind.id(), "tier", tier.tier(), "player", target.getUniqueId().toString()));
    }

    private void giveBlueprint(CommandSender sender, Player target, String id) {
        ToolCatalog.BlueprintDef def = catalog.blueprint(id).orElseThrow(() -> new CommandFailure(ID, "admin.unknown-blueprint", Msg.p("blueprint", id)));
        drops.give(target, def, "admin:" + sender.getName(), Actor.of(sender));
        msg.send(sender, "admin.blueprint-given", Msg.mm("blueprint", items.blueprintName(def.id())), Msg.p("player", target.getName()));
        if (!target.equals(sender)) {
            msg.send(target, "admin.blueprint-received", Msg.mm("blueprint", items.blueprintName(def.id())));
        }
        core.audit().log(AuditCategory.STAFF, "blueprint_give", Actor.of(sender), target.getUniqueId().toString(), target.getName(), null,
                target.getLocation(), Map.of("blueprint", def.id()));
    }

    private void giveSkin(CommandSender sender, String playerName, String id) {
        ToolCatalog.SkinDef skin = catalog.skin(id).orElseThrow(() -> new CommandFailure(ID, "admin.unknown-skin", Msg.p("skin", id)));
        core.players().offline(playerName).thenCompose(found -> {
            if (found.isEmpty()) {
                return java.util.concurrent.CompletableFuture.completedFuture((Object[]) null);
            }
            UUID uuid = found.get().uuid();
            return core.database().transaction(c -> repository.grantSkin(c, uuid, skin.id(), "admin:" + sender.getName(), System.currentTimeMillis()))
                    .thenApply(added -> new Object[]{uuid, found.get().name(), added});
        }).whenComplete((res, error) -> core.scheduler().main("tools-skin-give", () -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Tools: skin give failed", Database.cause(error));
                msg.send(sender, "error");
                return;
            }
            if (res == null) {
                msg.send(sender, "admin.unknown-player", Msg.p("player", playerName));
                return;
            }
            UUID uuid = (UUID) res[0];
            String name = (String) res[1];
            boolean added = (Boolean) res[2];
            if (!added) {
                msg.send(sender, "admin.skin-owned", Msg.p("player", name), Msg.mm("skin", items.skinName(skin.id())));
                return;
            }
            msg.send(sender, "admin.skin-given", Msg.mm("skin", items.skinName(skin.id())), Msg.p("player", name));
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && !online.equals(sender)) {
                msg.send(online, "admin.skin-received", Msg.mm("skin", items.skinName(skin.id())));
            }
            core.audit().logPlayer(AuditCategory.STAFF, "skin_give", Actor.of(sender), uuid, name, null, null, Map.of("skin", skin.id()));
        }));
    }

    // ------------------------------------------------------------------ menu actions

    void openToolMenu(Player player, UUID toolId) {
        core.menus().open(player, new ToolMenu(this, toolId));
    }

    void openSkins(Player player, UUID toolId, Menu parent) {
        UUID uuid = player.getUniqueId();
        core.database().async(c -> repository.skins(c, uuid)).whenComplete((owned, error) -> core.scheduler().main("tools-skins", () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                return;
            }
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Tools: could not load skins of " + p.getName(), Database.cause(error));
                msg.send(p, "error");
                return;
            }
            SkinsMenu menu = new SkinsMenu(this, toolId, owned);
            if (parent != null) {
                menu.parent(parent);
            }
            core.menus().open(p, menu);
        }));
    }

    /** Applies (or with null removes) a skin on the tool. The menu only offers owned skins. */
    void applySkin(Player player, UUID toolId, String skin) {
        int slot = ToolTracker.find(player.getInventory(), toolId);
        Optional<ToolData> tool = slot < 0 ? Optional.empty() : ToolItems.read(player.getInventory().getItem(slot));
        if (tool.isEmpty()) {
            forge.fail(player, "tool.gone");
            return;
        }
        if (skin != null && catalog.skin(skin).map(s -> !s.fits(tool.get().kind())).orElse(true)) {
            forge.fail(player, "skins.does-not-fit");
            return;
        }
        String before = tool.get().skin();
        ToolData next = tool.get().withSkin(skin);
        player.getInventory().setItem(slot, items.apply(player.getInventory().getItem(slot), next));
        tracker.markDirty(next, player);
        msg.send(player, skin == null ? "skins.removed" : "skins.applied", Msg.mm("skin", skin == null ? "" : items.skinName(skin)));
        forge.sound(player, "success");
        core.audit().log(AuditCategory.ITEM, "tool_skin", Actor.player(player), toolId.toString(), player.getName(), null, null,
                Map.of("before", before == null ? "" : before, "after", skin == null ? "" : skin));
    }

    /** Prestige (from the tool menu's confirm): checks again, then level 1 + prestige slot + star. */
    void prestige(Player player, UUID toolId) {
        int slot = ToolTracker.find(player.getInventory(), toolId);
        Optional<ToolData> tool = slot < 0 ? Optional.empty() : ToolItems.read(player.getInventory().getItem(slot));
        if (tool.isEmpty()) {
            forge.fail(player, "tool.gone");
            return;
        }
        ToolProgress.PrestigeCheck check = ToolProgress.canPrestige(tool.get(), curve, config.seasonId, config.prestigePerSeason, config.prestigeEnabled);
        if (check != ToolProgress.PrestigeCheck.OK) {
            forge.fail(player, "prestige." + check.name().toLowerCase(Locale.ROOT).replace('_', '-'), Msg.p("max", curve.maxLevel()));
            return;
        }
        ToolData next = ToolProgress.prestige(tool.get(), config.seasonId);
        ItemStack stack = items.apply(player.getInventory().getItem(slot), next);
        player.getInventory().setItem(slot, stack);
        tracker.markDirty(next, player);
        tracker.flushAsync();
        msg.title(player, "prestige.title", Msg.mm("tool", items.displayName(next, stack)), Msg.p("count", next.prestige()));
        msg.send(player, "prestige.done", Msg.mm("tool", items.displayName(next, stack)), Msg.p("count", next.prestige()));
        forge.sound(player, config.soundPrestige);
        core.audit().log(AuditCategory.ITEM, "tool_prestige", Actor.player(player), toolId.toString(), player.getName(), (long) next.prestige(),
                player.getLocation(), Map.of("season", config.seasonId, "perks", ToolCodec.perks(next.perks())));
    }
}
