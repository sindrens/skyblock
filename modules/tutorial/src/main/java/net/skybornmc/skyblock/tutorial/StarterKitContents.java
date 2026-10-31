package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.tools.ToolService;
import net.skybornmc.skyblock.common.tutorial.StarterContents;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The custom starter chest and the Starter pack (TUTORIAL.md §6, owner answer 158, TQ14-TQ18): plain items from
 * {@code starter-chest.items}, the Phoenix Field Guide and the Forge starter kit (via {@link ToolService#starterKit}, which
 * saves the profile's kit claim first). Nothing is ever dropped: what does not fit goes to {@code /box}. Main thread.
 */
final class StarterKitContents implements StarterContents {

    private final TutorialModule module;
    private final Core core;

    StarterKitContents(TutorialModule module) {
        this.module = module;
        this.core = module.core();
    }

    // ------------------------------------------------------------------ chest

    @Override
    public List<String> fillChest(Block chest, UUID ownerProfile) {
        TutorialConfig c = module.config();
        UUID owner = ProfileService.of(core.services()).ownerOf(ownerProfile);
        Player player = Bukkit.getPlayer(owner);
        List<String> added = new ArrayList<>();
        List<ItemStack> overflow = new ArrayList<>();
        Inventory inv = chest != null && chest.getState() instanceof Container box ? box.getInventory() : null;

        StarterSpec.Result items = StarterSpec.chest(c.chestItems, StarterKitContents::isItem);
        items.problems().forEach(p -> core.plugin().getLogger().warning("tutorial.yml → " + p));
        for (StarterSpec.Entry e : items.entries()) {
            ItemStack stack = new ItemStack(Material.matchMaterial(e.material()), e.amount());
            put(inv, e.slot(), stack, overflow);
            added.add(e.material() + ":" + e.amount());
        }
        if (c.bookSlot >= 0) {
            put(inv, c.bookSlot, FieldGuide.book(module, player), overflow);
            added.add("field_guide:1");
        }
        if (inv != null) {
            rememberChest(owner, chest);
        }
        if (!overflow.isEmpty()) {
            deliver(owner, player, overflow);
        }
        if (c.chestKit) {
            Location at = chest == null ? null : chest.getLocation();
            String name = player != null ? player.getName() : Bukkit.getOfflinePlayer(owner).getName();
            core.services().optional(ToolService.class).ifPresent(tools -> tools.starterKit(ownerProfile, owner, name, true)
                    .thenAccept(kit -> addKit(at, owner, kit)));
        }
        return added;
    }

    /** The tools arrive after their claim is saved: into the kit slots of the chest, else the owner's inventory / box. */
    private void addKit(Location at, UUID owner, List<ItemStack> kit) {
        if (kit.isEmpty()) {
            return;
        }
        List<Integer> slots = StarterSpec.slots(module.config().kitSlots);
        List<ItemStack> overflow = new ArrayList<>();
        Inventory inv = at != null && at.getBlock().getState() instanceof Container box ? box.getInventory() : null;
        for (int i = 0; i < kit.size(); i++) {
            put(inv, i < slots.size() ? slots.get(i) : -1, kit.get(i), overflow);
        }
        if (!overflow.isEmpty()) {
            deliver(owner, Bukkit.getPlayer(owner), overflow);
        }
        Player p = Bukkit.getPlayer(owner);
        if (p != null) {
            module.listener().checkChest(p);
        }
    }

    /** Into {@code slot} when free, else the first free slot, else {@code overflow}. */
    private static void put(Inventory inv, int slot, ItemStack stack, List<ItemStack> overflow) {
        if (inv == null) {
            overflow.add(stack);
            return;
        }
        if (slot >= 0 && slot < inv.getSize() && (inv.getItem(slot) == null || inv.getItem(slot).isEmpty())) {
            inv.setItem(slot, stack);
            return;
        }
        overflow.addAll(inv.addItem(stack).values());
    }

    /** Owner online: inventory then /box. Offline: straight into the box of that profile (never dropped). */
    private void deliver(UUID owner, Player player, List<ItemStack> items) {
        if (player != null) {
            ItemBox.give(core.services(), player, BoxSource.TUTORIAL, items);
            module.msg().send(player, "chest.fallback");
            return;
        }
        UUID profile = ProfileService.of(core.services()).profileKey(owner);
        ItemBox.of(core.services()).ifPresentOrElse(
                box -> box.deliver(profile, items, BoxSource.TUTORIAL, Actor.system("tutorial")),
                () -> core.plugin().getLogger().warning("Tutorial: starter items for an offline player could not be stored (no /box module)"));
    }

    private void rememberChest(UUID owner, Block chest) {
        String world = chest.getWorld().getName();
        int x = chest.getX();
        int y = chest.getY();
        int z = chest.getZ();
        Progress p = module.engine().progress(owner);
        // TUT-FIX: while the tutorial has not finished, the newest island's chest is the one the chest step points at (a
        // staff reset or a new island used to keep the old island's point, so the real chest was never detected).
        // After the finish the first island's chest is kept (another profile's island changes nothing).
        if (p != null && (p.chest == null || !p.state.complete())) {
            p.chest = world + ":" + x + "," + y + "," + z;
        }
        core.database().run(c -> {
            if (module.store().load(c, owner).map(pr -> pr.chest == null || !pr.state.complete()).orElse(false)) {
                module.store().setChest(c, owner, world, x, y, z);
            }
        }).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Tutorial: saving the starter chest point failed", Database.cause(e));
            return null;
        });
    }

    static boolean isItem(String key) {
        Material m = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
        return m != null && m.isItem() && !m.isAir();
    }

    // ------------------------------------------------------------------ chest step at its start

    /**
     * At the start of the chest step (§8): no known chest, chest gone or empty → the Starter pack instead (once), and the
     * step is done. A player who already holds the tool (opened the chest earlier) is done at once.
     */
    void enterChestStep(Player player, Progress p) {
        if (module.listener().hasTool(player) && p.chestOpened) {
            module.engine().signal(player, Steps.CHEST);
            return;
        }
        Optional<Location> chest = module.targets().chest(player, p);
        boolean usable = chest.isPresent() && chest.get().getBlock().getState() instanceof Container box && !box.getInventory().isEmpty();
        if (usable) {
            return;
        }
        if (chest.isEmpty() && module.targets().home(player).isPresent() && p.chest == null && recentIsland(player)) {
            return; // the island was just made and its chest point is being saved; the chest step waits for it
        }
        if (givePack(player) || module.listener().hasTool(player)) {
            module.engine().signal(player, Steps.CHEST);
        }
    }

    /** True for a short moment after the island was created (the chest fill runs right after the paste). */
    private boolean recentIsland(Player player) {
        Progress p = module.engine().progress(player.getUniqueId());
        return p != null && System.currentTimeMillis() - p.stepStartedAt < 3000;
    }

    // ------------------------------------------------------------------ pack

    @Override
    public boolean givePack(Player player) {
        TutorialConfig c = module.config();
        Progress p = module.engine().progress(player.getUniqueId());
        if (!c.starterPack || p == null || p.state.complete() || p.packGiven) {
            return false;
        }
        p.packGiven = true; // in memory at once (loaded from the database): a second call gives nothing
        UUID id = player.getUniqueId();
        UUID profile = ProfileService.of(core.services()).profileKey(player);
        core.database().transaction(conn -> module.store().claimPack(conn, id)).whenComplete((claimed, error) -> core.scheduler().main("tutorial-pack", () -> {
            if (error != null || !Boolean.TRUE.equals(claimed)) {
                if (error != null) {
                    core.plugin().getLogger().log(Level.WARNING, "Tutorial: Starter pack claim failed", Database.cause(error));
                    p.packGiven = false;
                }
                return;
            }
            if (!player.isOnline()) {
                return;
            }
            List<ItemStack> items = new ArrayList<>();
            StarterSpec.Result spec = StarterSpec.pack(c.packItems, StarterKitContents::isItem);
            spec.problems().forEach(pr -> core.plugin().getLogger().warning("tutorial.yml → " + pr));
            for (StarterSpec.Entry e : spec.entries()) {
                items.add(new ItemStack(Material.matchMaterial(e.material()), e.amount()));
            }
            if (c.packBook) {
                items.add(FieldGuide.book(module, player));
            }
            ItemBox.give(core.services(), player, BoxSource.TUTORIAL, items);
            if (c.packKit) {
                core.services().optional(ToolService.class).ifPresent(tools -> tools.starterKit(profile, id, player.getName(), true)
                        .thenAccept(kit -> {
                            if (!kit.isEmpty() && player.isOnline()) {
                                ItemBox.give(core.services(), player, BoxSource.TUTORIAL, kit);
                            }
                        }));
            }
            core.audit().logPlayer(AuditCategory.TUTORIAL, "tutorial_pack", Actor.player(player), id, player.getName(), null,
                    player.getLocation(), Map.of("items", items.size(), "kit", c.packKit, "profile", profile.toString()));
        }));
        module.msg().send(player, "pack.given");
        core.sounds().play(player, "success");
        return true;
    }
}
