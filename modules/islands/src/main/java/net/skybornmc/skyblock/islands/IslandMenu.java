package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.util.List;

/**
 * The island menu (M2.5): {@code /is} with an island. Every island feature in one place; one plain click each
 * (Bedrock-safe, no right/shift clicks). Live: refreshes every 2 s (members, lock state, chat mode). Icons come from
 * the icon catalogue (I7/I9: base item for everyone, our picture with the pack). No frame; empty slots stay empty.
 * <pre>
 *  . . . . 🌱 . . . 💣     4 island info, 8 danger tile: disband (leader) / leave (everyone else), TNT, red (I9 section 5)
 *  . 🛏 👤 ⚙ ✈ 🚪 🔁 📄 .   10 home, 11 members, 12 permissions, 13 warps, 14 visitors (lock), 15 settings, 16 invites
 *  . 📖 💬 🎨 🗺 ★ ▣ ⚒ .   19 notes, 20 island chat, 21 chat colors, 22 visit islands, 23 stats, 24 bank, 25 upgrades*
 *  . ⛽ . . ▤ . . . .      28 Island Fuel gauge (only while Island Fuel is on, PHASE-7 M7.1), 31 island storage
 *  . . . . . . . . .
 *  ← . . . ✕ . . . .      45 back, 49 close        * = coming soon (gray)
 * </pre>
 * 6 rows are kept for now (B8 compacts the island menu later).
 */
final class IslandMenu extends Menu {

    static final int SLOT_ISLAND = 4;
    static final int SLOT_HOME = 10;
    static final int SLOT_MEMBERS = 11;
    static final int SLOT_PERMISSIONS = 12;
    static final int SLOT_WARPS = 13;
    static final int SLOT_VISITORS = 14;
    static final int SLOT_SETTINGS = 15;
    static final int SLOT_INVITES = 16;
    static final int SLOT_NOTES = 19;
    static final int SLOT_CHAT = 20;
    static final int SLOT_CHAT_COLORS = 21;
    static final int SLOT_VISIT = 22;
    static final int SLOT_STATS = 23;
    static final int SLOT_BANK = 24;
    static final int SLOT_UPGRADES = 25;
    static final int SLOT_FUEL = 28;
    /** Batch 4: the island storage (/is storage), centre of row 4. */
    static final int SLOT_STORAGE = 31;
    /** Disband (leader) / Leave: the red danger tile in the top right corner, far from Back, pages and the main action. */
    static final int SLOT_DANGER = Menu.SLOT_DANGER;
    /** Every content slot, for the layout test (no slot used twice). */
    static final List<Integer> CONTENT = List.of(SLOT_HOME, SLOT_MEMBERS, SLOT_PERMISSIONS, SLOT_WARPS, SLOT_VISITORS, SLOT_SETTINGS,
            SLOT_INVITES, SLOT_NOTES, SLOT_CHAT, SLOT_CHAT_COLORS, SLOT_VISIT, SLOT_STATS, SLOT_BANK, SLOT_UPGRADES, SLOT_FUEL, SLOT_DANGER);

    private final IslandsModule module;
    private final IslandManager islands;
    private final Island island;

    IslandMenu(IslandsModule module, IslandManager islands, Island island) {
        this.module = module;
        this.islands = islands;
        this.island = island;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("menu.crumb"));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return "island";
    }

    @Override
    public int tickInterval() {
        return 40;
    }

    @Override
    protected void tick() {
        if (islands.byId(island.id()) != island || !IslandChatRules.chats(role())) {
            viewer.closeInventory();
            return;
        }
        refresh();
    }

    private Role role() {
        return island.roleOrNull(islands.key(viewer));
    }

    @Override
    protected void build() {
        navBar();
        Role role = role();
        if (role == null) {
            return;
        }
        set(SLOT_ISLAND, islandButton(role));
        set(SLOT_HOME, button("feature/home", "menu.home", click -> {
            viewer.closeInventory();
            islands.teleportHome(viewer);
        }));
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(islands.person(island.owner()))));
        set(SLOT_MEMBERS, MenuButton.of(service.item(head, text("menu.members.name"), lines("menu.members.lore",
                Msg.p("members", island.memberCount()), Msg.p("slots", islands.slots(island)), Msg.p("coops", island.coops().size()))),
                click -> openChild(new MembersMenu(module, islands, island))));
        set(SLOT_PERMISSIONS, MenuButton.of(service.icon(viewer, "island/permissions", text("menu.permissions.name"),
                lines(MemberRules.canEditPerms(role, PermRole.MEMBER) ? "menu.permissions.lore-edit" : "menu.permissions.lore-view")),
                click -> openChild(new PermPresetsMenu(module, islands, island))));
        WarpManager warps = module.warps();
        set(SLOT_WARPS, MenuButton.of(service.icon(viewer, "island/warps", text("menu.warps.name"), lines("menu.warps.lore",
                Msg.p("warps", warps.warps(island.id()).size()), Msg.p("slots", warps.slots(island)))),
                click -> openChild(new WarpBrowserMenu(module, islands, module.visits(), warps, island))));
        set(SLOT_VISITORS, visitors(role));
        set(SLOT_SETTINGS, MenuButton.of(service.icon(viewer, "island/settings", text("menu.settings.name"),
                lines(module.islandSettings().canChange(island, viewer) ? "menu.settings.lore-edit" : "menu.settings.lore-view")),
                click -> module.islandSettings().openFor(viewer, island, this)));
        MemberManager members = islands.memberManager();
        long now = System.currentTimeMillis();
        int pending = members.invites().pendingFrom(island.id(), now) + members.invites().of(islands.key(viewer), now).size();
        set(SLOT_INVITES, MenuButton.of(service.icon(viewer, "island/invites", text("menu.invites.name"),
                lines("menu.invites.lore", Msg.p("count", pending))), click -> openChild(new InvitesMenu(module, islands, members))));
        set(SLOT_NOTES, notes());
        IslandChat chat = module.chat();
        boolean on = chat.isToggled(viewer);
        set(SLOT_CHAT, MenuButton.of(service.icon(viewer, on ? "island/chat_on" : "island/chat_off",
                text(on ? "menu.chat.name-on" : "menu.chat.name-off"), lines(on ? "menu.chat.lore-on" : "menu.chat.lore-off")), click -> {
                    chat.toggle(viewer);
                    refresh();
                }));
        set(SLOT_CHAT_COLORS, MenuButton.of(service.icon(viewer, "island/chat_colors", text("menu.chat-colors.name"),
                lines(IslandChatRules.canPickColors(role) ? "menu.chat-colors.lore-edit" : "menu.chat-colors.lore-view")),
                click -> openChild(new ChatColorsMenu(module, islands, chat, island))));
        set(SLOT_VISIT, button("island/visit", "menu.visit",
                click -> openChild(new VisitMenu(module, islands, module.visits(), warps, Showcase.Sort.DISCOVER))));
        set(SLOT_STATS, stats());
        set(SLOT_BANK, bank());
        set(SLOT_UPGRADES, upgrades());
        set(SLOT_STORAGE, storage());
        fuel();
        // I9 section 5: one red danger tile (TNT) in the corner; the click still asks (disband: two steps).
        if (role == Role.LEADER) {
            set(SLOT_DANGER, service.dangerItem(viewer, IslandsModule.ID, "menu.disband"), click -> {
                viewer.closeInventory();
                module.lifecycle().disband(viewer);
            });
        } else {
            set(SLOT_DANGER, service.dangerItem(viewer, IslandsModule.ID, "menu.leave"), click -> members.leave(viewer, this));
        }
    }

    /** The island info item; with the progression module it opens /is level (PHASE-5 M5.2). */
    private MenuButton islandButton(Role role) {
        var progression = module.progression();
        boolean canOpen = progression.isPresent() && viewer.hasPermission(IslandsModule.PERM_LEVEL);
        ItemStack item = islandItem(role, canOpen);
        if (!canOpen) {
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> progression.get().openLevelMenu(viewer, this));
    }

    private ItemStack islandItem(Role role, boolean clickForLevel) {
        String created = module.config().dateFormatter().format(Instant.ofEpochMilli(island.createdAt()));
        int blocks = island.sizeChunks() * IslandGrid.CHUNK;
        TagResolver[] p = {
                Msg.p("island", islands.displayName(island)),
                Msg.p("role", module.raw("role." + MemberStore.key(role))),
                Msg.p("leader", islands.ownerName(island)),
                Msg.p("members", island.memberCount()), Msg.p("slots", islands.slots(island)),
                Msg.p("size", blocks),
                Msg.p("warps", module.warps().warps(island.id()).size()), Msg.p("warp-slots", module.warps().slots(island)),
                Msg.p("level", module.levelText(island.id())),
                Msg.p("created", created),
                Msg.p("type", module.raw(island.team() ? "menu.island.team" : "menu.island.solo"))};
        List<Component> lore = new java.util.ArrayList<>(lines("menu.island.lore", p));
        lore.addAll(lines(island.locked() ? "menu.island.locked" : "menu.island.open"));
        if (clickForLevel) {
            lore.add(Component.empty());
            lore.addAll(lines("menu.island.click-level"));
        }
        return service.icon(viewer, "feature/island", text("menu.island.name", p), lore);
    }

    /** Visitors: open/locked, who is here, bans; leader/admin/mod click to lock or unlock. */
    private MenuButton visitors(Role role) {
        VisitManager visits = module.visits();
        boolean locked = island.locked();
        boolean manage = VisitRules.canManageVisitors(role);
        TagResolver[] p = {Msg.p("visitors", visits.visitorsOn(island).size()), Msg.p("bans", visits.bans(island.id()).size())};
        List<Component> lore = new java.util.ArrayList<>(lines(locked ? "menu.visitors.lore-locked" : "menu.visitors.lore-open", p));
        lore.add(Component.empty());
        lore.addAll(lines(manage ? (locked ? "menu.visitors.click-unlock" : "menu.visitors.click-lock") : "menu.visitors.click-view"));
        ItemStack item = service.icon(viewer, locked ? "island/visitors_locked" : "island/visitors_open", text("menu.visitors.name"), lore);
        if (!manage) {
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> visits.lock(viewer, !island.locked()));
    }

    /** M2.6: island stats (every member). */
    private MenuButton stats() {
        IslandStats stats = module.stats();
        if (stats == null) {
            return comingSoon("island/stats", "menu.stats-off");
        }
        return button("island/stats", "menu.stats", click -> stats.open(viewer, island, this, false));
    }

    /** M3.3: opens the bank menu of the economy module (personal and island bank side by side). */
    private MenuButton bank() {
        var bank = module.core().services().optional(net.skybornmc.skyblock.common.economy.BankService.class);
        if (bank.isEmpty()) {
            return comingSoon("island/bank", "menu.bank-off");
        }
        return button("island/bank", "menu.bank", click -> bank.get().openIslandBank(viewer, this));
    }

    /** PHASE-5 M5.4: /is upgrade of the progression module; "Coming soon" without it. */
    private MenuButton upgrades() {
        var progression = module.progression();
        if (progression.isEmpty() || !progression.get().upgradesAvailable() || !viewer.hasPermission(IslandsModule.PERM_UPGRADE)) {
            return comingSoon("island/upgrades", "menu.upgrades");
        }
        return button("island/upgrades", "menu.upgrades-open", click -> progression.get().openUpgradeMenu(viewer, this));
    }

    /** Batch 4: the shared island storage; locked (with the way to unlock it) before the Island Vault upgrade. */
    private MenuButton storage() {
        int rows = islands.vaultRows(island.id());
        if (rows <= 0) {
            return MenuButton.display(service.item(new ItemStack(Material.ENDER_CHEST), text("menu.storage-locked.name"),
                    lines("menu.storage-locked.lore")));
        }
        return MenuButton.of(service.item(new ItemStack(Material.ENDER_CHEST), text("menu.storage.name"),
                lines("menu.storage.lore", Msg.p("rows", rows))), click -> module.vault().open(viewer));
    }

    /** PHASE-7 M7.1: the production module's Island Fuel gauge (display only; nothing while Island Fuel is off). */
    private void fuel() {
        net.skybornmc.skyblock.common.production.ProductionService.of(module.core().services())
                .flatMap(p -> p.fuelGauge(viewer, island.id()))
                .ifPresent(item -> set(SLOT_FUEL, MenuButton.display(item)));
    }

    private MenuButton notes() {
        IslandNotes notes = module.notes();
        if (notes == null || !notes.rights(viewer, island).canView()) {
            return MenuButton.display(service.icon(viewer, "state/locked", text("menu.notes.name"), lines("menu.notes.lore-locked")));
        }
        return MenuButton.of(service.icon(viewer, "island/notes", text("menu.notes.name"), lines("menu.notes.lore")),
                click -> notes.open(viewer, island, this));
    }

    /** A button with an icon catalogue key ({@code island/stats}) and the texts {@code <key>.name} / {@code <key>.lore}. */
    private MenuButton button(String iconKey, String key, java.util.function.Consumer<net.skybornmc.skyblock.common.menu.MenuClick> onClick) {
        return MenuButton.of(service.icon(viewer, iconKey, text(key + ".name"), lines(key + ".lore")), onClick);
    }

    private MenuButton comingSoon(String iconKey, String key) {
        return MenuButton.display(service.icon(viewer, iconKey, text(key + ".name"), lines(key + ".lore")));
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
