package net.skybornmc.skyblock.boosters;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.booster.BoosterService;
import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.item.BoundItems;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.profile.ProfileDeletedEvent;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Boosters (PHASE-10-REWARDS.md M10.2, owner R3, R4, RW2, O11, RX39).
 * <ul>
 *   <li><b>Personal boosters</b> are bound items (crates, later the calendar and quests): right-click starts them. Types
 *       sell, skill XP, tool XP and crystals. The same type never stacks, it adds time (max {@code personal.max-minutes},
 *       3 h); another strength of a running type waits. Time counts only while you play that profile.</li>
 *   <li><b>Banked time</b> (the AFK pool's sell booster, moved here) starts by itself when the type is free and you are
 *       not in the AFK pool.</li>
 *   <li><b>Global boosters</b> (Phoenix/Eclipse crates, later the store): everyone online gets the bonus; one per type
 *       runs, the rest queue. Boss bar countdown; {@code /thanks} or "thanks" in chat gives the starter 1 gem, once per
 *       booster per player.</li>
 *   <li>Every booster is a {@link net.skybornmc.skyblock.common.bonus.BonusSource}: the family caps and the +300% sell
 *       cap always apply (O11).</li>
 * </ul>
 * Starting an item: the item is taken, then one transaction marks its unique id used and adds the time; refusals and
 * errors give it back. A copy of an item that started before can never start again.
 */
public final class BoostersModule implements SkyblockModule, Listener {

    static final String ID = "boosters";
    static final String PERM_USE = "skyblock.boosters.use";
    static final String PERM_THANKS = "skyblock.boosters.thanks";
    static final String SETTING_BAR = "booster-bar";

    final Core core;
    final ModuleMessages msg;
    final BoostersSettings settings = new BoostersSettings();
    private final BoosterItems items;
    private final PersonalBoosters personal;
    private final GlobalBoosters global;
    private BoosterServiceImpl service;
    private Executor main;
    private Scheduler.Task task;
    private Map<BoosterType, Material> materials = Map.of();
    private Material globalMaterial = Material.NETHER_STAR;

    public BoostersModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
        this.items = new BoosterItems(this);
        this.personal = new PersonalBoosters(this);
        this.global = new GlobalBoosters(this);
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
        materials = BoostersSettings.materials(settings.materials, core.plugin().getLogger()::warning);
        globalMaterial = readGlobalMaterial();
        items.register(materials, globalMaterial);
        service = new BoosterServiceImpl(this);
        core.services().register(ID, BoosterService.class, service);
        core.services().optional(BonusService.class).ifPresent(b -> b.register(ID, new BoosterBonusSource(this)));
        core.services().optional(PlayerSettings.class).ifPresent(s -> {
            s.register(new SettingToggle(ID, SETTING_BAR, SettingCategory.DISPLAY, Material.NETHER_STAR, true, null, ID,
                    "settings.booster-bar", false));
            s.onChange(SETTING_BAR, global::setting);
        });
        registerCommands();
        Bukkit.getPluginManager().registerEvents(this, core.plugin());
        for (Player p : Bukkit.getOnlinePlayers()) {
            personal.joined(p);
        }
        global.load();
        task = core.scheduler().repeat("boosters:tick", 20L, 20L, () -> {
            personal.tick();
            global.tick();
        });
    }

    @Override
    public void reload() {
        core.configs().load(settings);
        core.messages().load(ID);
        global.refreshBars();
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        if (task != null) {
            task.cancel();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (core.menus().openMenu(p) instanceof BoostersMenu) {
                p.closeInventory();
            }
        }
        personal.saveAllBlocking();
        global.stopBlocking();
        core.services().optional(BonusService.class).ifPresent(b -> b.unregister(ID));
        core.services().optional(PlayerSettings.class).ifPresent(s -> s.unregisterAll(ID));
        core.services().unregisterAll(ID);
        items.unregister();
    }

    /** The item of a personal booster type (read at start). */
    Material material(BoosterType type) {
        return materials.getOrDefault(type, Material.GOLD_INGOT);
    }

    /** The item of global boosters (read at start). */
    Material globalMaterial() {
        return globalMaterial;
    }

    private Material readGlobalMaterial() {
        Material m = Material.matchMaterial(settings.globalMaterial.trim().toUpperCase(Locale.ROOT));
        if (m == null || !m.isItem() || m.isAir()) {
            core.plugin().getLogger().warning("boosters.yml → items.global-material: '" + settings.globalMaterial
                    + "' is not an item, using nether_star");
            return Material.NETHER_STAR;
        }
        return m;
    }

    // ================================================================== commands

    private void registerCommands() {
        core.commands().register(CommandSpec.command("boosters")
                .description("See your running, banked and owned boosters and the global ones")
                .category("Rewards")
                .permission(PERM_USE)
                .playerOnly()
                .aliases("booster")
                .example("boosters")
                .executes(ctx -> openMenu(ctx.player())));
        core.commands().register(CommandSpec.command("thanks")
                .description("Thank the players whose global boosters run (they get a gem)")
                .category("Rewards")
                .permission(PERM_THANKS)
                .playerOnly()
                .aliases("thank")
                .example("thanks")
                .executes(ctx -> {
                    if (!global.ready()) {
                        throw new CommandFailure(ID, "loading");
                    }
                    global.thank(ctx.player(), false);
                }));
    }

    void openMenu(Player player) {
        core.menus().open(player, new BoostersMenu(this));
    }

    // ================================================================== helpers

    Executor main() {
        return main;
    }

    PersonalBoosters personal() {
        return personal;
    }

    GlobalBoosters global() {
        return global;
    }

    BoosterItems items() {
        return items;
    }

    ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    String raw(String key) {
        String raw = core.messages().raw(ID, key);
        return raw == null ? "" : raw;
    }

    List<String> rawList(String key) {
        String raw = core.messages().raw(ID, key);
        return raw == null || raw.isEmpty() ? List.of() : List.of(raw.split("<newline>", -1));
    }

    /** The display name of a type ({@code types.<id>}). */
    String typeName(BoosterType type) {
        String raw = core.messages().raw(ID, "types." + type.id());
        return raw == null ? type.id() : raw;
    }

    boolean bedrock(Player player) {
        return player != null && core.bedrock().isBedrock(player.getUniqueId());
    }

    boolean flag(Player player, String placeholder) {
        return player != null && core.placeholders().has(placeholder)
                && core.placeholders().text(player, placeholder).map("true"::equalsIgnoreCase).orElse(false);
    }

    void sound(Player p, String key) {
        if (p != null && key != null && !key.isBlank()) {
            core.sounds().play(p, key);
        }
    }

    void audit(String action, Actor actor, UUID player, String name, Long amount, Map<String, Object> details) {
        core.audit().logPlayer(AuditCategory.BOOSTERS, action, actor, player, name, amount, null, details);
    }

    /** True if the player shows global booster boss bars ({@code /settings}). */
    boolean wantsBar(Player p) {
        return core.services().optional(PlayerSettings.class).map(s -> s.get(p, SETTING_BAR)).orElse(true);
    }

    void refreshMenu(Player p) {
        if (p != null && core.menus().openMenu(p) instanceof BoostersMenu menu) {
            menu.refresh();
        }
    }

    /** Why this player can't start boosters now (message key), or empty. */
    Optional<String> problem(Player player) {
        if (flag(player, "frozen")) {
            return Optional.of("frozen");
        }
        if (flag(player, "staff_mode")) {
            return Optional.of("staff-mode");
        }
        if (profiles().isSwitching(player.getUniqueId())) {
            return Optional.of("switching");
        }
        return Optional.empty();
    }

    /**
     * Takes the booster item with this unique id out of the player's inventory (the stack that was clicked when it still
     * matches, else wherever it is now). Returns a copy of what was taken.
     */
    Optional<ItemStack> take(Player p, ItemStack clicked, UUID itemUuid) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack s = contents[slot];
            if (s != null && ItemRegistry.uuidOf(s).filter(itemUuid::equals).isPresent()) {
                ItemStack copy = s.clone();
                copy.setAmount(1);
                if (s.getAmount() > 1) {
                    s.setAmount(s.getAmount() - 1);
                    inv.setItem(slot, s);
                } else {
                    inv.setItem(slot, null);
                }
                return Optional.of(copy);
            }
        }
        return Optional.empty();
    }

    /** Gives a booster item back after a refusal or error: the inventory, else /box (owner answer 7: never dropped). */
    void giveBack(Player online, UUID profile, ItemStack item) {
        if (online != null && profiles().profileKey(online).equals(profile)) {
            ItemBox.give(core.services(), online, BoxSource.BOOSTERS, item);
            return;
        }
        ItemBox.of(core.services()).ifPresentOrElse(
                box -> box.deliver(profile, List.of(item), BoxSource.BOOSTERS, Actor.system(ID)).exceptionally(e -> {
                    core.plugin().getLogger().log(Level.SEVERE, "Boosters: could not give a booster item back to profile " + profile, e);
                    return null;
                }),
                () -> core.plugin().getLogger().severe("Boosters: no item box to give a booster item back to profile " + profile));
    }

    /** A copy of an item that already started: it stays used up; the player is told and staff can see it in /logs. */
    void reuse(Player online, UUID player, String name, BoosterItems.Spec spec, UUID profile) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("type", spec.type().id());
        d.put("global", spec.global());
        d.put("item", spec.itemUuid().toString());
        d.put("profile", profile.toString());
        audit("reuse_refused", Actor.SYSTEM, player, name, (long) spec.percent(), d);
        if (online != null) {
            msg.send(online, "already-used");
        }
    }

    /** Starts a booster item (right-click or the menu). Main thread. */
    void use(Player p, ItemStack stack) {
        Optional<BoosterItems.Spec> spec = BoosterItems.read(stack);
        if (spec.isEmpty()) {
            msg.send(p, "invalid");
            return;
        }
        if (!p.hasPermission(PERM_USE)) {
            msg.send(p, "no-permission");
            return;
        }
        Optional<String> problem = problem(p);
        if (problem.isPresent()) {
            msg.send(p, problem.get());
            return;
        }
        UUID profile = profiles().profileKey(p);
        if (BoundItems.isBound(stack) && !BoundItems.mayReceive(stack, profile)) {
            msg.send(p, "not-yours");
            return;
        }
        BoosterItems.Spec s = spec.get();
        if (!settings.valid(s.percent(), s.minutes(), s.global())) {
            msg.send(p, "invalid");
            return;
        }
        if (s.global()) {
            global.start(p, stack, s);
        } else {
            personal.start(p, stack, s);
        }
    }

    // ================================================================== events

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!BoosterItems.isBooster(item)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // main hand only, so one click never starts two boosters
        }
        use(event.getPlayer(), item);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        personal.joined(event.getPlayer());
        global.joined(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        personal.quit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileDeleted(ProfileDeletedEvent event) {
        UUID profile = event.profile().id();
        personal.chain(profile, () -> core.database().run(c -> BoosterStore.deleteProfile(c, profile))).whenComplete((v, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Boosters: could not remove the boosters of deleted profile " + profile, e);
            } else {
                audit("profile_reset", Actor.SYSTEM, event.profile().owner(), null, 0L, Map.of("profile", profile.toString()));
            }
        });
    }

    /** RW2: saying thanks in chat while a global booster runs thanks it (quietly ignored otherwise). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!settings.thanksEnabled) {
            return;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!BoosterRules.isThanks(text, settings.chatPhrases, settings.chatMaxLength)) {
            return;
        }
        Player p = event.getPlayer();
        core.scheduler().ensureMain("boosters:chat-thanks", () -> {
            if (p.isOnline() && global.ready() && p.hasPermission(PERM_THANKS)) {
                global.thank(p, true);
            }
        });
    }

    /** Owned booster items in a player's inventory (for the menu), in slot order. */
    List<ItemStack> owned(Player p) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : p.getInventory().getContents()) {
            if (s != null && BoosterItems.read(s).isPresent()) {
                out.add(s);
            }
        }
        return out;
    }
}
