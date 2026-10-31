package net.skybornmc.skyblock.blackmarket;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.crate.CrateKeys;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.enchant.EnchantService;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.npc.NpcClickEvent;
import net.skybornmc.skyblock.common.placeholder.Placeholder;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.stacking.StackingService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.common.wardrobe.WardrobeService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Black Market (PHASE-10-REWARDS.md M10.11): {@code /blackmarket}, always open, the stock rotates.
 * <ul>
 *   <li><b>Rotation</b> (P10-35): 1 headline, 3 rare, 4 common offers picked by weight at the restock times (00:00 and
 *       12:00 Europe/Oslo), stored once per rotation; a chat line announces the headline.</li>
 *   <li><b>Fixed row</b>: the Island Charter (next size above 9x9, shards or gems: P10-38, REVIEW3 F6) and the Disenchant
 *       Scroll.</li>
 *   <li><b>Limits</b> (P10-37): per account per rotation (Phoenix Regalia per season, Eclipse key per month); the headline
 *       has a server-wide stock of 25.</li>
 *   <li><b>One transaction per buy</b>: currency out, stock and limits counted, {@code black_market_buys} row; then the
 *       hand-out (inventory, else /box). Lost on a crash at worst (review row), never doubled. Audit {@code blackmarket/*}.</li>
 * </ul>
 */
public final class BlackMarketModule implements SkyblockModule, Listener {

    static final String ID = "blackmarket";
    static final String PERM_USE = "skyblock.blackmarket.use";
    /** Key source of keys bought here (crates.yml: not a tradeable source, so the keys are bound). */
    static final String KEY_SOURCE = "blackmarket";

    final Core core;
    final ModuleMessages msg;
    final BlackMarketSettings settings = new BlackMarketSettings();
    private final Market market;
    private final Purchases purchases;
    private final Deliveries deliveries;
    private final Counts counts;
    private Executor main;
    private final List<Scheduler.Task> tasks = new ArrayList<>();

    private volatile OfferCatalog catalog = OfferCatalog.EMPTY;
    private volatile Rotation rotation = new Rotation(Format.DEFAULT_ZONE, List.of(java.time.LocalTime.MIDNIGHT));
    private volatile List<CharterRules.Step> charterSteps = List.of();

    public BlackMarketModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
        this.market = new Market(this);
        this.purchases = new Purchases(this);
        this.deliveries = new Deliveries(this);
        this.counts = new Counts(this);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(settings);
        main = new MainThread(core.plugin());
        parse();
        defineTag(); // before the first rotation is picked, so the tag offer can be in it
        registerCommands();
        registerPlaceholders();
        Bukkit.getPluginManager().registerEvents(this, core.plugin());
        long check = 20L * settings.checkSeconds;
        // First check after every module is up (crates, wardrobe, enchants decide which offers can be rolled).
        tasks.add(core.scheduler().repeat("blackmarket:rotation", 60L, check, market::tick));
        long retry = 20L * settings.retrySeconds;
        // The first run also recovers hand-outs a crash cut off.
        tasks.add(core.scheduler().repeat("blackmarket:deliveries", 200L, retry, deliveries::retry));
    }

    @Override
    public void reload() {
        core.configs().load(settings);
        core.messages().load(ID);
        parse();
        market.reset();
        market.tick();
        defineTag();
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (core.menus().openMenu(p) instanceof BlackMarketMenu) {
                p.closeInventory();
            }
        }
        counts.clear();
        core.placeholders().unregisterAll(ID);
    }

    // ================================================================== config

    private void parse() {
        List<String> problems = new ArrayList<>();
        catalog = OfferCatalog.parse(settings.offers, problems::add);
        ZoneId zone = Format.parseZone(settings.zone);
        if (zone == null) {
            problems.add("blackmarket.yml rotation.zone: '" + settings.zone + "' is not a time zone; using Europe/Oslo.");
            zone = Format.DEFAULT_ZONE;
        }
        rotation = new Rotation(zone, Rotation.parseTimes(settings.times, problems::add));
        charterSteps = CharterRules.parseSteps(settings.charterSteps, settings.fromSize, problems::add);
        for (String p : problems) {
            core.plugin().getLogger().warning("Black Market: " + p);
        }
        long live = catalog.all().stream().filter(o -> o.kind() != OfferCatalog.Kind.COMING_SOON).count();
        core.plugin().getLogger().info("Black Market: " + catalog.all().size() + " offers (" + live + " live), restocks at "
                + String.join(", ", rotation.times().stream().map(Object::toString).toList()) + " " + rotation.zone() + ".");
    }

    /** The Black Market tag exists in tags-titles.yml (added once; staff edits stay). */
    private void defineTag() {
        if (!settings.defineTag) {
            return;
        }
        cosmetics().ifPresent(c -> {
            for (OfferCatalog.Offer o : catalog.all()) {
                if (o.kind() == OfferCatalog.Kind.TAG && !c.ids(Cosmetics.Kind.TAG).contains(o.ref())) {
                    c.defineTag(o.ref(), raw("tag.display", o.ref()), Rarity.LEGENDARY, raw("tag.source", "Black Market"));
                }
            }
        });
    }

    /** The picks of a rotation from the offers that can be handed out right now. Main thread. */
    List<Rotation.Pick> picks(long rotationId, long salt) {
        Map<OfferCatalog.Slot, Integer> slots = new EnumMap<>(OfferCatalog.Slot.class);
        slots.put(OfferCatalog.Slot.HEADLINE, settings.headlineSlots);
        slots.put(OfferCatalog.Slot.RARE, settings.rareSlots);
        slots.put(OfferCatalog.Slot.COMMON, settings.commonSlots);
        return Rotation.pick(rotationId, salt, catalog, slots, this::live, settings.headlineStock);
    }

    /** True when the offer's system runs now, so a buy can really be handed out. Main thread. */
    boolean live(OfferCatalog.Offer o) {
        return switch (o.kind()) {
            case SET_TOKEN -> core.services().has(WardrobeService.class);
            case ENCHANT_BOOK -> !books(o.ref()).isEmpty();
            case SCROLL -> core.services().has(EnchantService.class);
            case KEY -> core.services().optional(CrateKeys.class).map(k -> k.exists(o.ref())).orElse(false);
            case SPAWNER -> !spawnerTypes(o).isEmpty();
            case ITEM -> material(o.ref()) != null;
            case TAG -> cosmetics().map(c -> c.ids(Cosmetics.Kind.TAG).contains(o.ref())).orElse(false);
            case COMING_SOON -> false;
        };
    }

    /** Enabled enchants of a rarity (random book offers). */
    List<String> books(String rarity) {
        Optional<EnchantService> e = core.services().optional(EnchantService.class);
        if (e.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String id : e.get().ids()) {
            if (e.get().enabled(id) && e.get().rarity(id).id().equalsIgnoreCase(rarity)) {
                out.add(id);
            }
        }
        return out;
    }

    List<String> spawnerTypes(OfferCatalog.Offer o) {
        try {
            int tier = Integer.parseInt(o.ref());
            return core.services().optional(StackingService.class).map(s -> s.spawnerTypes(tier)).orElse(List.of());
        } catch (NumberFormatException e) {
            return List.of();
        }
    }

    static Material material(String id) {
        Material m = id == null ? null : Material.matchMaterial(id);
        return m != null && m.isItem() && !m.isAir() ? m : null;
    }

    // ================================================================== commands and placeholders

    private void registerCommands() {
        core.commands().register(CommandSpec.command("blackmarket")
                .description("The Black Market: rare offers for shards that change twice a day")
                .category("Rewards")
                .permission(PERM_USE)
                .playerOnly()
                .example("blackmarket")
                .executes(ctx -> {
                    if (!settings.enabled) {
                        throw new CommandFailure(ID, "disabled");
                    }
                    open(ctx.player(), null);
                })
                .sub(new BlackMarketAdmin(this).spec()));
    }

    private void registerPlaceholders() {
        core.placeholders().register(ID, "blackmarket_restock", Placeholder.text(p ->
                Format.duration(Duration.ofMillis(rotation.untilRestock(System.currentTimeMillis())))));
        core.placeholders().register(ID, "blackmarket_headline", Placeholder.text(p -> market.headline()
                .flatMap(r -> catalog.get(r.offerId())).map(o -> plain(offerNameMini(o))).orElse("-")));
        core.placeholders().register(ID, "blackmarket_headline_stock", Placeholder.text(p -> market.headline()
                .map(r -> r.left() < 0 ? "-" : String.valueOf(r.left())).orElse("-")));
    }

    private static String plain(String mini) {
        return mini.replaceAll("<[^>]*>", "").strip();
    }

    /** Opens the Black Market menu (loads the rotation and the player's counts first if needed). */
    void open(Player player, Menu parent) {
        market.tick();
        counts.load(player);
        BlackMarketMenu menu = new BlackMarketMenu(this);
        if (parent != null) {
            menu.parent(parent);
        }
        core.menus().open(player, menu);
    }

    void refreshMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (core.menus().openMenu(p) instanceof BlackMarketMenu menu) {
                menu.refresh();
            }
        }
    }

    void refreshMenu(UUID account) {
        Player p = Bukkit.getPlayer(account);
        if (p != null && core.menus().openMenu(p) instanceof BlackMarketMenu menu) {
            menu.refresh();
        }
    }

    // ================================================================== accessors and helpers

    Executor main() {
        return main;
    }

    OfferCatalog catalog() {
        return catalog;
    }

    Rotation rotation() {
        return rotation;
    }

    List<CharterRules.Step> charterSteps() {
        return charterSteps;
    }

    Market market() {
        return market;
    }

    Purchases purchases() {
        return purchases;
    }

    Deliveries deliveries() {
        return deliveries;
    }

    Counts counts() {
        return counts;
    }

    ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    Optional<Cosmetics> cosmetics() {
        return core.services().optional(Cosmetics.class);
    }

    Optional<IslandService> islands() {
        return core.services().optional(IslandService.class);
    }

    Optional<EconomyService> economy() {
        return core.services().optional(EconomyService.class);
    }

    /** The online player who is on this profile right now, or null. */
    Player receiver(UUID profile) {
        return profiles().onlinePlayer(profile).filter(p -> profiles().profileKey(p).equals(profile)).orElse(null);
    }

    String raw(String key, String fallback) {
        String raw = core.messages().raw(ID, key);
        return raw == null ? fallback : raw;
    }

    /** Name of an offer as MiniMessage ({@code offers.<id>.name}, else the id made readable). */
    String offerNameMini(OfferCatalog.Offer o) {
        String raw = core.messages().raw(ID, "offers." + o.id() + ".name");
        if (raw != null) {
            return raw;
        }
        String[] words = o.id().split("_");
        StringBuilder sb = new StringBuilder("<c:value>");
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(sb.length() > 9 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return sb.toString();
    }

    Component offerName(net.kyori.adventure.audience.Audience viewer, OfferCatalog.Offer o) {
        return msg.component(viewer, "text", Msg.mm("text", offerNameMini(o)));
    }

    /** Extra lore of an offer ({@code offers.<id>.lore}, may be missing). */
    List<Component> offerLore(Player viewer, OfferCatalog.Offer o) {
        String key = "offers." + o.id() + ".lore";
        return msg.has(key) ? core.menus().lines(viewer, ID, key) : List.of();
    }

    /** Menu icon of an offer. */
    Material icon(OfferCatalog.Offer o) {
        Material custom = material(o.icon());
        if (custom != null) {
            return custom;
        }
        return switch (o.kind()) {
            case SET_TOKEN -> Material.LEATHER_CHESTPLATE;
            case ENCHANT_BOOK -> Material.ENCHANTED_BOOK;
            case SCROLL -> Material.PAPER;
            case KEY -> Material.TRIPWIRE_HOOK;
            case SPAWNER -> Material.SPAWNER;
            case ITEM -> Optional.ofNullable(material(o.ref())).orElse(Material.CHEST);
            case TAG -> Material.NAME_TAG;
            case COMING_SOON -> Material.GRAY_DYE;
        };
    }

    String shards(long amount) {
        return economy().map(e -> e.format(Currency.SHARDS, amount)).orElse(Format.number(amount));
    }

    String price(Currency currency, long amount) {
        return economy().map(e -> e.format(currency, amount)).orElse(Format.number(amount));
    }

    void sound(Player p, String key) {
        if (p != null && key != null && !key.isBlank()) {
            core.sounds().play(p, key);
        }
    }

    void fail(org.bukkit.command.CommandSender sender, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... tags) {
        msg.send(sender, key, tags);
        if (sender instanceof Player p) {
            sound(p, "error");
        }
    }

    void audit(String action, Actor actor, UUID player, String name, Long amount, Map<String, Object> details) {
        core.audit().logPlayer(AuditCategory.BLACKMARKET, action, actor, player, name, amount, null, details);
    }

    // ================================================================== events

    /** The Black Market NPC (Market Square alley) opens /blackmarket (sneak-click keeps its help text). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNpc(NpcClickEvent event) {
        if (event.help() || event.role() == null || !settings.enabled || !event.role().equalsIgnoreCase(settings.npcRole.strip())) {
            return;
        }
        Player p = event.getPlayer();
        if (!p.hasPermission(PERM_USE)) {
            return;
        }
        event.setCancelled(true);
        sound(p, "click");
        open(p, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        counts.forget(event.getPlayer().getUniqueId());
        purchases.forget(event.getPlayer().getUniqueId());
    }
}
