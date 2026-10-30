package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.pet.PetHook;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Loadouts at runtime (WARDROBE §7, W3): saving presets, applying them atomically and the guards. A swap (LD4):
 * <ol>
 *   <li>Lock the player ({@link Session#busy}: inventory clicks, drops, pickups and profile switches are refused; the
 *   menu closes) and read the Wardrobe Chest.</li>
 *   <li>Plan every move from a snapshot ({@link LoadoutPlanner}, pure). Outgoing items are serialized now.</li>
 *   <li>One database transaction ({@link LoadoutOps#swap}): outgoing items {@code stored}, incoming chest items
 *   {@code releasing} (plan id), new worn rows. While it runs the outgoing items are still in the inventory (locked).</li>
 *   <li>Committed: the planned moves run in one tick (outgoing items taken out, incoming put in, plain items moved),
 *   pieces re-rendered from the rows, then the incoming rows become {@code released} and the lock ends. Refused: nothing
 *   happened. Unknown (database error): everything is re-read and the chest recovery fixes either outcome.</li>
 * </ol>
 * A crash or quit at any point is repaired on the next join by the chest recovery ({@link Chest#recover}): an item in
 * the inventory whose row says stored is a stale copy and is removed; a releasing item that is not in the inventory is
 * given again. Item ids are unique and kept, so each tool exists in exactly one place at every moment.
 */
final class Loadouts {

    static final String PERM_USE = "skyblock.loadout";
    static final String PERM_SLOTS_PREFIX = "skyblock.loadout.slots.";

    private final WardrobeModule module;

    Loadouts(WardrobeModule module) {
        this.module = module;
    }

    PetHook pets() {
        return module.core.services().optional(PetHook.class).orElse(PetHook.NONE);
    }

    /** Presets this player may use now (LD2). */
    int slots(Player player, Session s) {
        WardrobeConfig c = module.config();
        int rank = LoadoutRules.rankSlots(c.loadoutMaxSlots, n -> player.hasPermission(PERM_SLOTS_PREFIX + n));
        return LoadoutRules.slots(c.loadoutFreeSlots, rank, s == null ? 0 : s.boughtSlots, c.loadoutBoughtMax, c.loadoutMaxSlots);
    }

    /** Preset names for tab completion (usable ones only). */
    List<String> names(Player player) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (s == null || !s.loaded) {
            return List.of();
        }
        int slots = slots(player, s);
        return s.loadouts.stream().filter(l -> l.index() < slots).map(Loadout::name).toList();
    }

    // ------------------------------------------------------------------ command

    /** {@code /loadout [name]}: no name opens the menu, a name applies that preset. */
    void command(Player player, String name) {
        if (!module.config().loadoutEnabled) {
            module.fail(player, "loadout.turned-off");
            return;
        }
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (s == null || !s.loaded) {
            module.fail(player, "loading");
            return;
        }
        if (name == null || name.isBlank()) {
            open(player, null);
            return;
        }
        Optional<Loadout> preset = s.loadoutNamed(name);
        if (preset.isEmpty()) {
            module.fail(player, "loadout.unknown", Msg.p("name", name));
            return;
        }
        apply(player, preset.get());
    }

    /** Opens the {@code /loadout} menu (reads the chest first so missing tools show in red). */
    void open(Player player, net.skybornmc.skyblock.common.menu.Menu parent) {
        if (!module.config().loadoutEnabled) {
            module.fail(player, "loadout.turned-off");
            return;
        }
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (s == null || !s.loaded) {
            module.fail(player, "loading");
            return;
        }
        UUID uuid = player.getUniqueId();
        UUID profile = s.profile;
        module.core.database().async(c -> module.store().stored(c, profile)).whenComplete((rows, error) ->
                module.core.scheduler().main("loadout-open", () -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) {
                        return;
                    }
                    Set<UUID> chest = new HashSet<>();
                    if (error == null) {
                        rows.forEach(r -> chest.add(r.itemUuid()));
                    }
                    LoadoutMenu menu = new LoadoutMenu(module, chest);
                    if (parent != null) {
                        menu.parent(parent);
                    }
                    module.core.menus().open(p, menu);
                }));
    }

    // ------------------------------------------------------------------ guards

    long cooldownLeft(Session s) {
        return Math.max(0, s.lastEquip + module.config().loadoutCooldownSeconds * 1000L - System.currentTimeMillis());
    }

    /** Why the player can't swap now (message key), or empty. */
    Optional<String> problem(Player player, Session s) {
        boolean loaded = s != null && s.loaded;
        return LoadoutRules.problem(new LoadoutRules.State(loaded, loaded && (s.busy || s.stripped),
                module.equipment().profiles().isSwitching(player.getUniqueId()), module.frozen(player),
                module.config().loadoutBlockedWorld(player.getWorld().getName()),
                module.combatLeft(player, module.config().loadoutCombatSeconds), loaded ? cooldownLeft(s) : 0,
                player.hasPermission("skyblock.cooldown.bypass")));
    }

    private boolean refuse(Player player, Session s) {
        Optional<String> problem = problem(player, s);
        if (problem.isEmpty()) {
            return false;
        }
        if (problem.get().equals("cooldown")) {
            module.fail(player, "cooldown", Msg.p("time", Format.duration(Duration.ofMillis(Math.max(1000, cooldownLeft(s))))));
        } else {
            module.fail(player, problem.get());
        }
        return true;
    }

    // ------------------------------------------------------------------ apply (LD4)

    void apply(Player player, Loadout preset) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (!module.config().loadoutEnabled) {
            module.fail(player, "loadout.turned-off");
            return;
        }
        if (refuse(player, s)) {
            return;
        }
        if (preset.index() >= slots(player, s)) {
            module.fail(player, "loadout.locked");
            return;
        }
        if (s.loadout(preset.id()).isEmpty()) {
            module.fail(player, "loadout.gone");
            return;
        }
        // Step 1: lock, close the menu, read the chest.
        s.busy = true;
        UUID uuid = player.getUniqueId();
        // Close the menu next tick (never inside the click event); the lock already holds.
        module.core.scheduler().later("loadout-close", 1L, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && module.core.menus().openMenu(p) != null) {
                p.closeInventory();
            }
        });
        UUID profile = s.profile;
        int generation = s.generation;
        module.core.database().async(c -> new Object[]{module.store().stored(c, profile), module.store().storageCount(c, profile)})
                .whenComplete((data, error) -> module.core.scheduler().main("loadout-plan", () -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || module.equipment().sessionOrNull(uuid) != s || s.generation != generation || !s.profile.equals(profile)) {
                        s.busy = false;
                        return;
                    }
                    if (error != null) {
                        s.busy = false;
                        module.core.plugin().getLogger().log(Level.WARNING, "Wardrobe: could not read the chest for a loadout", error);
                        module.fail(p, "error");
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    List<WardrobeStore.Stored> rows = (List<WardrobeStore.Stored>) data[0];
                    plan(p, s, preset, rows, (Integer) data[1]);
                }));
    }

    /** Step 2 (main thread, locked): plan, serialize outgoing items, run the transaction. */
    private void plan(Player p, Session s, Loadout preset, List<WardrobeStore.Stored> rows, int chestUsed) {
        Set<UUID> chest = new HashSet<>();
        rows.forEach(r -> chest.add(r.itemUuid()));
        Map<ArmorPiece, UUID> equipped = new EnumMap<>(ArmorPiece.class);
        s.equipped.forEach((piece, row) -> equipped.put(piece, row.entryId()));
        Set<UUID> owned = new HashSet<>();
        s.entries.forEach(e -> owned.add(e.entryId()));
        int capacity = module.chest().capacity(p);
        LoadoutPlanner.Plan plan = LoadoutPlanner.plan(new LoadoutPlanner.Input(snapshot(p.getInventory()), equipped, owned, chest, chestUsed,
                capacity, preset));
        if (!plan.skips().isEmpty() && !module.config().loadoutPartial) {
            s.busy = false;
            module.fail(p, "loadout.refused", Msg.p("parts", skipText(plan.skips())));
            return;
        }
        if (plan.nothing()) {
            s.busy = false;
            if (plan.skips().isEmpty()) {
                module.msg().send(p, "loadout.already", Msg.p("name", preset.name()));
            } else {
                module.fail(p, "loadout.nothing", Msg.p("parts", skipText(plan.skips())));
            }
            return;
        }
        // Outgoing items: serialized now, while the inventory is locked (they stay in it until the transaction commits).
        PlayerInventory inv = p.getInventory();
        List<WardrobeStore.Stored> outgoing = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (LoadoutPlanner.Op op : plan.ops()) {
            if (op instanceof LoadoutPlanner.Store st) {
                ItemStack item = inv.getItem(st.slot());
                Optional<ItemDefinition> def = module.core.items().definitionOf(item);
                if (item == null || def.isEmpty() || !ItemRegistry.uuidOf(item).filter(st.id()::equals).isPresent()) {
                    s.busy = false;
                    module.fail(p, "error");
                    return;
                }
                outgoing.add(new WardrobeStore.Stored(st.id(), s.profile, def.get().type().id(), def.get().id(), plainName(item),
                        item.serializeAsBytes(), WardrobeStore.StorageState.STORED, null, now));
            }
        }
        List<WardrobeStore.Equipped> wear = new ArrayList<>();
        plan.wear().forEach((piece, entry) -> wear.add(new WardrobeStore.Equipped(piece, entry, UUID.randomUUID())));
        UUID planId = UUID.randomUUID();
        UUID profile = s.profile;
        int generation = s.generation;
        UUID uuid = p.getUniqueId();
        module.loadoutOps().swap(new LoadoutOps.Swap(profile, planId, preset.id(), outgoing, plan.take(), wear, plan.off(), capacity))
                .thenAccept(r -> module.core.scheduler().main("loadout-swap", () -> finish(uuid, s, generation, profile, preset, plan, planId, wear, r)));
    }

    /** Step 3 (main thread): the transaction's answer. */
    private void finish(UUID uuid, Session s, int generation, UUID profile, Loadout preset, LoadoutPlanner.Plan plan, UUID planId,
                        List<WardrobeStore.Equipped> wear, LoadoutOps.Result r) {
        Player p = Bukkit.getPlayer(uuid);
        boolean same = p != null && module.equipment().sessionOrNull(uuid) == s && s.generation == generation && s.profile.equals(profile);
        if (!r.ok()) {
            s.busy = false;
            if (p == null) {
                return;
            }
            switch (r.status()) {
                case FULL -> module.fail(p, "chest.full", Msg.p("size", module.chest().capacity(p)));
                case GONE -> module.fail(p, "loadout.changed");
                case STALE -> module.fail(p, "chest.stale");
                default -> module.fail(p, "error");
            }
            if (r.status() == LoadoutOps.Status.ERROR || r.status() == LoadoutOps.Status.STALE || r.status() == LoadoutOps.Status.GONE) {
                // Unknown outcome or a changed chest: re-read everything; the chest recovery fixes either way.
                module.equipment().load(p);
            }
            return;
        }
        if (!same) {
            s.busy = false;
            return; // offline or switched: the join recovery hands out / removes items, pieces render from the rows
        }
        // Committed: run the planned moves in this tick.
        PlayerInventory inv = p.getInventory();
        List<UUID> released = new ArrayList<>();
        int kept = 0;
        for (LoadoutPlanner.Op op : plan.ops()) {
            switch (op) {
                case LoadoutPlanner.Move m -> {
                    ItemStack item = inv.getItem(m.from());
                    if (item == null || item.isEmpty()) {
                        continue;
                    }
                    int to = m.to();
                    ItemStack there = inv.getItem(to);
                    if (there != null && !there.isEmpty()) {
                        to = freeStorageSlot(inv); // only if the inventory changed against the lock
                    }
                    if (to < 0) {
                        continue; // stays where it is
                    }
                    inv.setItem(to, item);
                    inv.setItem(m.from(), null);
                }
                case LoadoutPlanner.Store st -> {
                    ItemStack at = inv.getItem(st.slot());
                    if (ItemRegistry.uuidOf(at).filter(st.id()::equals).isPresent()) {
                        inv.setItem(st.slot(), null);
                    } else if (!Shop.removeItem(inv, st.id())) { // the stored copy wins (it is in the chest now)
                        // It left the locked inventory some other way: keep the chest copy, tell staff (possible exploit).
                        module.core.plugin().getLogger().warning("Wardrobe: loadout item " + st.id() + " of " + p.getName()
                                + " was no longer in the inventory when it was stored");
                        module.core.audit().logPlayer(AuditCategory.ITEM, "loadout_outgoing_missing", Actor.system("wardrobe"), p.getUniqueId(),
                                p.getName(), null, p.getLocation(), Map.of("item", st.id().toString(), "profile", profile.toString()));
                    }
                }
                case LoadoutPlanner.Take t -> {
                    WardrobeStore.Stored row = r.taken().get(t.id());
                    ItemStack item = null;
                    try {
                        item = row == null ? null : ItemStack.deserializeBytes(row.item());
                    } catch (RuntimeException e) {
                        module.core.plugin().getLogger().log(Level.SEVERE, "Wardrobe: stored item " + t.id() + " can't be read; back in the chest", e);
                    }
                    int to = t.to();
                    ItemStack there = inv.getItem(to);
                    if (there != null && !there.isEmpty()) {
                        to = freeStorageSlot(inv);
                    }
                    if (item == null || to < 0) {
                        module.ops().backToStored(t.id(), planId);
                        kept++;
                        continue;
                    }
                    inv.setItem(to, item);
                    released.add(t.id());
                }
            }
        }
        // Armor: the rows are written; show them.
        List<ArmorPiece> blocked = new ArrayList<>();
        for (ArmorPiece piece : plan.off()) {
            s.equipped.remove(piece);
            if (WardrobeItems.isPiece(inv.getItem(piece.slot()))) {
                inv.setItem(piece.slot(), null);
            }
        }
        for (WardrobeStore.Equipped q : wear) {
            ItemStack current = inv.getItem(q.piece().slot());
            if (current != null && !current.isEmpty() && !WardrobeItems.isPiece(current)) {
                Map<Integer, ItemStack> left = inv.addItem(current);
                if (!left.isEmpty()) {
                    blocked.add(q.piece()); // vanilla armor could not move (changed against the lock): keep it, drop the row
                    continue;
                }
                inv.setItem(q.piece().slot(), null);
            }
            s.equipped.put(q.piece(), q);
        }
        if (!blocked.isEmpty()) {
            module.ops().equip(profile, List.of(), blocked);
        }
        module.equipment().renderWorn(p, s);
        module.equipment().refreshBonuses(s);
        s.lastEquip = System.currentTimeMillis();
        // Pet (hook; nothing until pets exist).
        PetHook pets = pets();
        if (preset.petId() != null && pets.available()) {
            pets.summon(p, preset.petId());
        }
        audit(p, preset, plan, profile, kept);
        int tools = plan.tools() - kept;
        module.msg().actionBar(p, "loadout.applied-bar", Msg.p("name", preset.name()), Msg.p("pieces", plan.pieces()), Msg.p("tools", Math.max(0, tools)));
        module.msg().send(p, "loadout.applied", Msg.p("name", preset.name()), Msg.p("pieces", plan.pieces()), Msg.p("tools", Math.max(0, tools)));
        if (!plan.skips().isEmpty()) {
            module.msg().send(p, "loadout.skipped", Msg.p("parts", skipText(plan.skips())));
        }
        if (kept > 0) {
            module.msg().send(p, "chest.kept", Msg.p("count", kept));
        }
        module.core.sounds().play(p, "success");
        // Stay locked until the incoming rows say released, so nothing can be stored again while it still says releasing.
        module.loadoutOps().released(released, planId).whenComplete((ok, error) -> module.core.scheduler().main("loadout-done", () -> {
            s.busy = false;
            Player again = Bukkit.getPlayer(uuid);
            if (again != null) {
                module.menus().refreshOpen(again);
            }
        }));
    }

    private void audit(Player p, Loadout preset, LoadoutPlanner.Plan plan, UUID profile, int kept) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("loadout", preset.id().toString());
        details.put("name", preset.name());
        details.put("profile", profile.toString());
        Map<String, String> wear = new TreeMap<>();
        plan.wear().forEach((piece, entry) -> wear.put(piece.key(), entry.toString()));
        details.put("wear", wear);
        details.put("off", plan.off().stream().map(ArmorPiece::key).toList());
        details.put("stored", plan.store().stream().map(UUID::toString).toList());
        details.put("taken", plan.take().stream().map(UUID::toString).toList());
        details.put("skipped", plan.skips().stream().map(sk -> (sk.piece() == null ? "slot " + (sk.slot() + 1) : sk.piece().key()) + ":" + sk.reason().key).toList());
        if (kept > 0) {
            details.put("kept_in_chest", kept);
        }
        module.core.audit().logPlayer(AuditCategory.ITEM, "loadout_apply", Actor.player(p), p.getUniqueId(), p.getName(), null, null, details);
    }

    /** {@code Helm (set gone), slot 3 (no room)} (plain). */
    String skipText(List<LoadoutPlanner.Skip> skips) {
        List<String> parts = new ArrayList<>();
        for (LoadoutPlanner.Skip sk : skips) {
            String part = sk.piece() != null ? module.text().plain("piece-name." + sk.piece().key())
                    : module.text().plain("loadout.slot-name").replace("{slot}", String.valueOf(sk.slot() + 1));
            parts.add(part + " (" + module.text().plain("loadout.skip." + sk.reason().key) + ")");
        }
        return String.join(", ", parts);
    }

    /** The inventory as planner cells (0-35, armor 36-39, off-hand 40). */
    Map<Integer, LoadoutPlanner.Cell> snapshot(PlayerInventory inv) {
        Map<Integer, LoadoutPlanner.Cell> cells = new java.util.HashMap<>();
        for (int i = 0; i <= LoadoutPlanner.OFFHAND; i++) {
            cells.put(i, cell(inv.getItem(i)));
        }
        return cells;
    }

    private LoadoutPlanner.Cell cell(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return LoadoutPlanner.Cell.EMPTY;
        }
        if (WardrobeItems.isPiece(item)) {
            return LoadoutPlanner.Cell.PIECE;
        }
        Optional<UUID> id = ItemRegistry.uuidOf(item);
        boolean unique = id.isPresent() && module.core.items().definitionOf(item).map(ItemDefinition::unique).orElse(false);
        return unique ? LoadoutPlanner.Cell.unique(id.get(), module.chest().storable(item)) : LoadoutPlanner.Cell.PLAIN;
    }

    private static int freeStorageSlot(PlayerInventory inv) {
        int free = inv.firstEmpty();
        return free >= 0 && free < inv.getStorageContents().length ? free : -1;
    }

    static String plainName(ItemStack item) {
        return PlainTextComponentSerializer.plainText().serialize(item.effectiveName());
    }

    // ------------------------------------------------------------------ saving and editing

    /** What the player wears and holds now (Save current, LD1): worn pieces, the pet, hotbar unique items. */
    private Map<ArmorPiece, Loadout.Armor> currentArmor(Session s) {
        Map<ArmorPiece, Loadout.Armor> armor = new EnumMap<>(ArmorPiece.class);
        for (ArmorPiece piece : ArmorPiece.values()) {
            WardrobeStore.Equipped q = s.equipped.get(piece);
            armor.put(piece, q == null ? Loadout.Armor.NONE : new Loadout.Armor(q.entryId()));
        }
        return armor;
    }

    private Map<Integer, Loadout.Tool> currentHotbar(Player player) {
        Map<Integer, Loadout.Tool> hotbar = new TreeMap<>();
        PlayerInventory inv = player.getInventory();
        for (int h = 0; h < Loadout.HOTBAR; h++) {
            ItemStack item = inv.getItem(h);
            if (module.chest().storable(item)) {
                hotbar.put(h, new Loadout.Tool(ItemRegistry.uuidOf(item).orElseThrow(), plainName(item), item.getType().getKey().getKey()));
            }
        }
        return hotbar;
    }

    private String defaultIcon(Map<Integer, Loadout.Tool> hotbar) {
        return hotbar.values().stream().map(Loadout.Tool::material).filter(m -> m != null && !m.isBlank()).findFirst()
                .orElse(module.config().loadoutDefaultIcon);
    }

    /** Saves the current gear as a new preset in menu place {@code index}. */
    void saveNew(Player player, int index) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (s == null || !s.loaded) {
            module.fail(player, "loading");
            return;
        }
        int slots = slots(player, s);
        if (index < 0 || index >= slots || s.loadoutAt(index).isPresent()) {
            module.fail(player, "loadout.no-slot");
            return;
        }
        Map<Integer, Loadout.Tool> hotbar = currentHotbar(player);
        List<String> names = s.loadouts.stream().map(Loadout::name).toList();
        String name = null;
        for (int n = index + 1; n < index + 50 && name == null; n++) {
            String candidate = LoadoutRules.cleanName(module.text().plain("loadout.default-name").replace("{n}", String.valueOf(n)));
            if (LoadoutRules.checkName(candidate, module.config().loadoutNameMax, names).isEmpty()) {
                name = candidate;
            }
        }
        if (name == null) {
            name = "L" + (index + 1) + "-" + Integer.toHexString((int) (System.nanoTime() & 0xfff));
        }
        Loadout l = new Loadout(UUID.randomUUID(), s.profile, index, name, defaultIcon(hotbar), currentArmor(s),
                pets().activePet(s.profile).orElse(null), hotbar, System.currentTimeMillis());
        UUID uuid = player.getUniqueId();
        module.loadoutOps().create(l, slots).thenAccept(r -> module.core.scheduler().main("loadout-save", () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (r.ok() && module.equipment().sessionOrNull(uuid) == s && s.profile.equals(l.profile())) {
                s.putLoadout(l);
            }
            if (r.ok()) {
                auditChange(p, uuid, "loadout_save", l, Map.of("new", true, "tools", l.hotbar().size(), "pieces", l.entries().size()));
            }
            if (p == null) {
                return;
            }
            switch (r.status()) {
                case OK -> {
                    module.msg().send(p, "loadout.saved", Msg.p("name", l.name()), Msg.p("pieces", l.entries().size()), Msg.p("tools", l.hotbar().size()));
                    module.core.sounds().play(p, "success");
                }
                case NO_SLOT, TAKEN -> module.fail(p, "loadout.no-slot");
                default -> module.fail(p, "error");
            }
            module.menus().refreshOpen(p);
        }));
    }

    /** Save current over an existing preset. */
    void overwrite(Player player, Loadout old) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (s == null || !s.loaded) {
            module.fail(player, "loading");
            return;
        }
        Loadout l = old.withContents(currentArmor(s), pets().activePet(s.profile).orElse(null), currentHotbar(player), System.currentTimeMillis());
        UUID uuid = player.getUniqueId();
        module.loadoutOps().overwrite(l).thenAccept(r -> module.core.scheduler().main("loadout-overwrite", () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (r.ok() && module.equipment().sessionOrNull(uuid) == s && s.profile.equals(l.profile())) {
                s.putLoadout(l);
            }
            if (r.ok()) {
                auditChange(p, uuid, "loadout_save", l, Map.of("new", false, "tools", l.hotbar().size(), "pieces", l.entries().size()));
            }
            if (p == null) {
                return;
            }
            if (r.ok()) {
                module.msg().send(p, "loadout.saved", Msg.p("name", l.name()), Msg.p("pieces", l.entries().size()), Msg.p("tools", l.hotbar().size()));
                module.core.sounds().play(p, "success");
            } else {
                module.fail(p, r.status() == LoadoutOps.Status.GONE ? "loadout.gone" : "error");
            }
            module.menus().refreshOpen(p);
        }));
    }

    /** Asks for a new name in chat (the menu closes), then renames. */
    void promptRename(Player player, Loadout l, Runnable reopen) {
        module.core.menus().prompt(player, WardrobeModule.ID, "loadout.rename-prompt", module.config().loadoutNameMax + 8,
                input -> rename(player, l, input, reopen), reopen, Msg.p("max", module.config().loadoutNameMax), Msg.p("name", l.name()));
    }

    void rename(Player player, Loadout l, String raw, Runnable after) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (s == null || !s.loaded) {
            module.fail(player, "loading");
            return;
        }
        String name = LoadoutRules.cleanName(raw);
        List<String> others = s.loadouts.stream().filter(o -> !o.id().equals(l.id())).map(Loadout::name).toList();
        Optional<LoadoutRules.NameProblem> problem = LoadoutRules.checkName(name, module.config().loadoutNameMax, others);
        if (problem.isPresent()) {
            module.fail(player, "loadout.name." + problem.get().key, Msg.p("max", module.config().loadoutNameMax));
            after.run();
            return;
        }
        UUID uuid = player.getUniqueId();
        module.loadoutOps().rename(l.id(), s.profile, name).thenAccept(r -> module.core.scheduler().main("loadout-rename", () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (r.ok() && module.equipment().sessionOrNull(uuid) == s) {
                s.loadout(l.id()).ifPresent(cur -> s.putLoadout(cur.withName(name)));
            }
            if (r.ok()) {
                auditChange(p, uuid, "loadout_rename", l, Map.of("old_name", l.name(), "new_name", name));
            }
            if (p == null) {
                return;
            }
            switch (r.status()) {
                case OK -> {
                    module.msg().send(p, "loadout.renamed", Msg.p("name", name));
                    module.core.sounds().play(p, "success");
                }
                case TAKEN -> module.fail(p, "loadout.name.taken");
                case GONE -> module.fail(p, "loadout.gone");
                default -> module.fail(p, "error");
            }
            after.run();
        }));
    }

    void setIcon(Player player, Loadout l, String icon, Runnable after) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (s == null || !s.loaded) {
            module.fail(player, "loading");
            return;
        }
        UUID uuid = player.getUniqueId();
        module.loadoutOps().icon(l.id(), s.profile, icon).thenAccept(r -> module.core.scheduler().main("loadout-icon", () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (r.ok() && module.equipment().sessionOrNull(uuid) == s) {
                s.loadout(l.id()).ifPresent(cur -> s.putLoadout(cur.withIcon(icon)));
            }
            if (r.ok()) {
                auditChange(p, uuid, "loadout_icon", l, Map.of("icon", icon));
            }
            if (p == null) {
                return;
            }
            if (r.ok()) {
                module.core.sounds().play(p, "success");
            } else {
                module.fail(p, r.status() == LoadoutOps.Status.GONE ? "loadout.gone" : "error");
            }
            after.run();
        }));
    }

    void delete(Player player, Loadout l) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (s == null || !s.loaded) {
            module.fail(player, "loading");
            return;
        }
        UUID uuid = player.getUniqueId();
        module.loadoutOps().delete(l.id(), s.profile).thenAccept(r -> module.core.scheduler().main("loadout-delete", () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (r.ok() && module.equipment().sessionOrNull(uuid) == s) {
                s.removeLoadout(l.id());
            }
            if (r.ok()) {
                auditChange(p, uuid, "loadout_delete", l, Map.of());
            }
            if (p == null) {
                return;
            }
            if (r.ok()) {
                module.msg().send(p, "loadout.deleted", Msg.p("name", l.name()));
                module.core.sounds().play(p, "click");
                open(p, null);
            } else {
                module.fail(p, r.status() == LoadoutOps.Status.GONE ? "loadout.gone" : "error");
            }
        }));
    }

    private void auditChange(Player p, UUID uuid, String action, Loadout l, Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("loadout", l.id().toString());
        details.put("name", l.name());
        details.put("slot", l.index() + 1);
        details.put("profile", l.profile().toString());
        details.putAll(extra);
        module.core.audit().logPlayer(AuditCategory.ITEM, action, p == null ? Actor.system("wardrobe") : Actor.player(p), uuid,
                p == null ? null : p.getName(), null, null, details);
    }

    // ------------------------------------------------------------------ merging (W2) keeps presets working

    /** Presets after a merge: slots that wore either input now wear the merged entry (same rule as the database). */
    static List<Loadout> repointed(List<Loadout> presets, UUID a, UUID b, UUID merged) {
        List<Loadout> out = new ArrayList<>();
        for (Loadout l : presets) {
            Map<ArmorPiece, Loadout.Armor> armor = new EnumMap<>(ArmorPiece.class);
            l.armor().forEach((piece, choice) -> armor.put(piece,
                    !choice.empty() && (choice.entryId().equals(a) || choice.entryId().equals(b)) ? new Loadout.Armor(merged) : choice));
            out.add(new Loadout(l.id(), l.profile(), l.index(), l.name(), l.icon(), armor, l.petId(), l.hotbar(), l.updatedAt()));
        }
        return List.copyOf(out);
    }
}
