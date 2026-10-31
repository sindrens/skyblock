package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.item.DupeReport;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.item.ItemType;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.moderation.SanctionService;
import net.skybornmc.skyblock.common.player.PlayerSaves;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Key items (PHASE-10 M10.4). In a crate's preview, <b>tradeable</b> keys become unique key items ({@code crate_key},
 * the item's unique id = the {@code crate_key_items} row, so the {@code DupeDetector} sees copies). Bound keys are never
 * withdrawn. USE a key item to put the key back on your active profile, exactly once. Key items trade on {@code /trade}
 * and the AH like any unbound item.
 *
 * <p>Crash safety (SECFIX1 {@link PlayerSaves}; lose at worst, never duplicate):</p>
 * <ul>
 *   <li><b>Withdraw</b> (database → inventory): one transaction takes the keys and writes the active rows; only then are
 *       the items made and given (inventory, overflow {@code /box}) and the player file saved. A crash in between loses
 *       the items (rows stay active, nothing can redeem them). A player who left or switched profile meanwhile gets the
 *       keys back on the profile they came from (rows {@code refunded}).</li>
 *   <li><b>Redeem</b> (inventory → database): the item leaves the hand, the player file is saved, and only then one
 *       transaction uses the row up and adds the key. A failed save or transaction gives the item back. Because the file
 *       is saved first, a used-up id found again is always a real copy: it stays taken, is audited and is reported to
 *       the {@code DupeDetector}.</li>
 * </ul>
 */
final class KeyItems implements Listener {

    static final String ITEM_ID = "crate_key";
    /** The crate a key item opens. */
    static final NamespacedKey CRATE = ItemKeys.key("key_crate");
    /** Name of the player who withdrew it (display only: the database row decides). */
    static final NamespacedKey WITHDRAWN_BY = ItemKeys.key("key_withdrawn_by");

    private final CratesModule m;
    /** One withdraw or redeem at a time per player. */
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private boolean registered;

    KeyItems(CratesModule module) {
        this.m = module;
    }

    void enable() {
        register();
        Bukkit.getPluginManager().registerEvents(this, m.core.plugin());
    }

    void disable() {
        HandlerList.unregisterAll(this);
        if (registered) {
            m.core.items().unregister(ITEM_ID);
            registered = false;
        }
        busy.clear();
    }

    boolean busy(UUID player) {
        return busy.contains(player);
    }

    // ================================================================== item

    private void register() {
        if (m.core.items().get(ITEM_ID).isPresent()) {
            return;
        }
        Material material = Material.matchMaterial(m.settings.keyItemMaterial);
        if (material == null || !material.isItem() || material.isAir()) {
            m.core.plugin().getLogger().warning("crates-settings.yml key-items.material '" + m.settings.keyItemMaterial
                    + "' is not an item; using TRIPWIRE_HOOK.");
            material = Material.TRIPWIRE_HOOK;
        }
        ItemDefinition.Builder builder = ItemDefinition.builder(ITEM_ID, material, ItemType.CRATE_KEY, Rarity.RARE)
                .name(m.raw("key-items.item.name-unknown", "Crate Key"))
                .subtitle(m.raw("key-items.item.subtitle", ""))
                .description(m.raw("key-items.item.description", ""))
                .action(m.raw("key-items.item.action-input", "USE"), m.raw("key-items.item.action-text", ""))
                .unique()
                .renderer(new ItemDefinition.Renderer() {
                    @Override
                    public String name(ItemStack stack, ItemDefinition definition) {
                        return crateOf(stack).map(m::keyMini).orElse(definition.name());
                    }

                    @Override
                    public void lore(ItemStack stack, ItemDefinition definition, LoreBuilder lore) {
                        Optional<String> crate = crateOf(stack);
                        if (crate.isEmpty()) {
                            return;
                        }
                        String by = stack.getPersistentDataContainer().getOrDefault(WITHDRAWN_BY, PersistentDataType.STRING, "?");
                        String id = ItemRegistry.uuidOf(stack).map(u -> u.toString().substring(0, 8)).orElse("?");
                        LoreBuilder.Section info = lore.section(m.raw("key-items.item.info-title", ""));
                        String crateName = m.catalog().crate(crate.get()).map(m::crateMini).orElse(LoreBuilder.escape(crate.get()));
                        info.text(LoreBuilder.fill(m.raw("key-items.item.crate", "{crate}"), Map.of("crate", crateName)));
                        info.text(LoreBuilder.fill(m.raw("key-items.item.withdrawn-by", "{player}"),
                                Map.of("player", LoreBuilder.escape(by))));
                        info.text(LoreBuilder.fill(m.raw("key-items.item.id", "{id}"), Map.of("id", LoreBuilder.escape(id))));
                    }

                    @Override
                    public Rarity rarity(ItemStack stack, ItemDefinition definition) {
                        return crateOf(stack).flatMap(c -> m.catalog().crate(c)).map(c -> KeyItems.rarity(c.rarity())).orElse(definition.rarity());
                    }
                });
        if (m.settings.keyItemGlint) {
            builder.glint();
        }
        m.core.items().register(builder.build());
        registered = true;
    }

    /** The item rarity of a crate's rarity id ({@code legendary}); unknown = common. Pure. */
    static Rarity rarity(String id) {
        if (id != null) {
            for (Rarity r : Rarity.values()) {
                if (r.id().equals(id.trim().toLowerCase(Locale.ROOT))) {
                    return r;
                }
            }
        }
        return Rarity.COMMON;
    }

    static boolean isKeyItem(ItemStack stack) {
        return ItemRegistry.is(stack, ITEM_ID);
    }

    /** The crate id stored on a key item (display; redeeming reads the row). */
    static Optional<String> crateOf(ItemStack stack) {
        if (!isKeyItem(stack)) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getPersistentDataContainer().get(CRATE, PersistentDataType.STRING));
    }

    private ItemStack create(UUID id, String crate, String withdrawnBy, long now) {
        return m.core.items().create(ITEM_ID, 1, "withdraw:" + withdrawnBy, pdc -> {
            pdc.set(ItemKeys.UUID, PersistentDataType.STRING, id.toString()); // row id = unique item id
            pdc.set(ItemKeys.CREATED, PersistentDataType.LONG, now);
            pdc.set(CRATE, PersistentDataType.STRING, crate);
            pdc.set(WITHDRAWN_BY, PersistentDataType.STRING, withdrawnBy);
        });
    }

    // ================================================================== withdraw

    /** True when withdrawing is on and the item is registered. */
    boolean enabled() {
        return m.settings.keyItemsEnabled && registered;
    }

    /**
     * Withdraws up to {@code want} tradeable keys of {@code crate} as key items. {@code done} runs on the main thread when
     * it finished (any outcome).
     */
    void withdraw(Player player, CrateCatalog.Crate crate, long want, Runnable done) {
        Runnable after = done == null ? () -> { } : done;
        if (!enabled()) {
            m.fail(player, "key-items.disabled");
            after.run();
            return;
        }
        Optional<String> problem = m.problem(player);
        if (problem.isPresent()) {
            m.fail(player, problem.get());
            after.run();
            return;
        }
        // A trade block (P7) also blocks making tradeable items; a box at its hard cap can't take the overflow.
        if (SanctionService.denied(m.core.services(), player, SanctionService.Use.TRADE)
                || ItemBox.refuses(m.core.services(), player)) {
            after.run();
            return;
        }
        UUID uuid = player.getUniqueId();
        UUID profile = m.profiles().profileKey(player);
        CratesModule.ProfileView view = m.view(profile);
        long tradeable = view == null ? want : view.keys(crate.id()).tradeable();
        int amount = KeyItemStore.amount(tradeable, want, m.settings.keyItemMaxPerWithdraw);
        if (amount < 1) {
            m.fail(player, "key-items.none", Msg.mm("key", m.keyMini(crate.id())));
            after.run();
            return;
        }
        if (!busy.add(uuid)) {
            m.fail(player, "key-items.busy");
            after.run();
            return;
        }
        String name = player.getName();
        List<UUID> ids = new ArrayList<>(amount);
        for (int i = 0; i < amount; i++) {
            ids.add(UUID.randomUUID());
        }
        long now = System.currentTimeMillis();
        m.core.database().transaction(c -> KeyItemStore.withdraw(c, profile, crate.id(), uuid, name, ids, now))
                .whenCompleteAsync((ok, error) -> {
                    busy.remove(uuid);
                    try {
                        withdrawn(uuid, name, profile, crate, ids, now, ok, error);
                    } finally {
                        after.run();
                    }
                }, m.main());
    }

    private void withdrawn(UUID uuid, String name, UUID profile, CrateCatalog.Crate crate, List<UUID> ids, long now, Boolean ok,
                           Throwable error) {
        Player player = Bukkit.getPlayer(uuid);
        if (error != null) {
            m.core.plugin().getLogger().log(Level.WARNING, "Crates: key withdraw of " + name + " failed; nothing was taken",
                    Database.cause(error));
            if (player != null) {
                m.fail(player, "error");
            }
            return;
        }
        if (!Boolean.TRUE.equals(ok)) {
            if (player != null) {
                m.fail(player, "key-items.none", Msg.mm("key", m.keyMini(crate.id())));
            }
            m.changed(profile);
            return;
        }
        if (player == null || m.profiles().isSwitching(uuid) || !m.profiles().profileKey(uuid).equals(profile)) {
            refund(uuid, name, profile, crate, ids);
            return;
        }
        List<ItemStack> items = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            items.add(create(id, crate.id(), name, now));
        }
        List<ItemStack> boxed = ItemBox.give(m.core.services(), player, BoxSource.CRATE, items);
        PlayerSaves.save(player, "a key withdraw");
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("crate", crate.id());
        d.put("profile", profile.toString());
        d.put("items", ids.stream().map(UUID::toString).toList());
        d.put("to_box", boxed.size());
        m.audit("key_withdraw", Actor.player(player), uuid, name, (long) ids.size(), d);
        m.msg.send(player, boxed.isEmpty() ? "key-items.withdrawn" : "key-items.withdrawn-box", Msg.number("amount", ids.size()),
                Msg.number("box", boxed.size()), Msg.mm("key", m.keyMini(crate.id())));
        m.core.sounds().play(player, "success");
        m.changed(profile);
    }

    /** The player left or switched profile before the items were made: the keys go back to the profile they came from. */
    private void refund(UUID uuid, String name, UUID profile, CrateCatalog.Crate crate, List<UUID> ids) {
        long now = System.currentTimeMillis();
        m.core.database().transaction(c -> KeyItemStore.refund(c, profile, crate.id(), ids, now)).whenCompleteAsync((back, error) -> {
            if (error != null) {
                m.core.plugin().getLogger().log(Level.SEVERE, "Crates: " + ids.size() + " withdrawn " + crate.id() + " key(s) of " + name
                        + " could not be given back to profile " + profile + "; give them with /crates admin give", Database.cause(error));
                return;
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("crate", crate.id());
            d.put("profile", profile.toString());
            d.put("items", ids.stream().map(UUID::toString).toList());
            m.audit("key_withdraw_refund", Actor.system(CratesModule.ID), uuid, name, (long) back, d);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                m.msg.send(online, "key-items.refunded", Msg.number("amount", back), Msg.mm("key", m.keyMini(crate.id())));
            }
            m.changed(profile);
        }, m.main());
    }

    // ================================================================== redeem (USE)

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!isKeyItem(event.getItem())) {
            return;
        }
        if (event.getClickedBlock() != null && m.yard() != null && m.yard().at(event.getClickedBlock()).isPresent()) {
            return; // a crate block at the crate yard: the physical crate handles the click
        }
        event.setCancelled(true); // a tripwire hook must never be placed
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // main hand only, so one click never redeems two
        }
        redeem(event.getPlayer());
    }

    private void redeem(Player player) {
        if (!player.hasPermission(CratesModule.PERM_USE)) {
            return;
        }
        Optional<String> problem = m.problem(player);
        if (problem.isPresent()) {
            m.fail(player, problem.get());
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!busy.add(uuid)) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isKeyItem(hand)) {
            busy.remove(uuid);
            return;
        }
        // The item leaves the hand first, so this exact item can't be used again while the database works.
        ItemStack taken = hand.clone();
        taken.setAmount(1);
        if (hand.getAmount() > 1) {
            ItemStack rest = hand.clone();
            rest.setAmount(hand.getAmount() - 1);
            player.getInventory().setItemInMainHand(rest);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        String name = player.getName();
        UUID profile = m.profiles().profileKey(player);
        Optional<UUID> id = ItemRegistry.uuidOf(taken);
        if (id.isEmpty()) {
            busy.remove(uuid);
            copy(uuid, name, taken, null, null, KeyItemStore.Outcome.UNKNOWN);
            return;
        }
        long now = System.currentTimeMillis();
        PlayerSaves.commitAfterSave(player, "a key item redeem", () -> m.core.database().transaction(c ->
                        KeyItemStore.redeem(c, id.get(), profile, uuid, name, now, crate -> m.catalog().crate(crate).isPresent())))
                .whenCompleteAsync((result, error) -> {
                    busy.remove(uuid);
                    if (error != null || result == null) {
                        if (error != null && !(Database.cause(error) instanceof PlayerSaves.SaveFailed)) {
                            m.core.plugin().getLogger().log(Level.WARNING, "Crates: key item redeem of " + name + " failed; the item "
                                    + "goes back", Database.cause(error));
                        }
                        giveBack(uuid, name, profile, taken, "key-items.failed");
                        return;
                    }
                    redeemed(uuid, name, profile, taken, id.get(), result);
                }, m.main());
    }

    private void redeemed(UUID uuid, String name, UUID profile, ItemStack taken, UUID id, KeyItemStore.Redeem result) {
        KeyItemStore.Row row = result.row();
        switch (result.outcome()) {
            case REDEEMED -> {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("crate", row.crate());
                d.put("item", id.toString());
                d.put("profile", profile.toString());
                d.put("withdrawn_by", row.accountName());
                d.put("withdrawn_profile", row.profile().toString());
                m.audit("key_redeem", Actor.player(uuid, name), uuid, name, 1L, d);
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    m.msg.send(online, "key-items.redeemed", Msg.mm("key", m.keyMini(row.crate())));
                    m.core.sounds().play(online, "success");
                }
                m.changed(profile);
            }
            case OTHER_PROFILE -> {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("crate", row.crate());
                d.put("item", id.toString());
                d.put("profile", profile.toString());
                d.put("withdrawn_profile", row.profile().toString());
                m.audit("key_redeem_refused", Actor.player(uuid, name), uuid, name, 1L, d);
                giveBack(uuid, name, profile, taken, "key-items.other-profile");
            }
            case UNKNOWN_CRATE -> giveBack(uuid, name, profile, taken, "key-items.unknown-crate");
            case ALREADY_USED, UNKNOWN -> copy(uuid, name, taken, id, row, result.outcome());
        }
    }

    /** Nothing was redeemed: the item goes back (inventory, then /box; offline or another profile: that profile's box). */
    private void giveBack(UUID uuid, String name, UUID profile, ItemStack taken, String messageKey) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && m.profiles().profileKey(uuid).equals(profile) && !m.profiles().isSwitching(uuid)) {
            ItemBox.give(m.core.services(), online, BoxSource.CRATE, taken);
            PlayerSaves.save(online, "a key item give-back");
            m.fail(online, messageKey);
            return;
        }
        Optional<ItemBox> box = ItemBox.of(m.core.services());
        if (box.isPresent()) {
            box.get().deliver(profile, List.of(taken), BoxSource.CRATE, Actor.system(CratesModule.ID));
        } else {
            m.core.plugin().getLogger().severe("Crates: key item " + ItemRegistry.uuidOf(taken).orElse(null) + " of " + name
                    + " could not be given back (no /box); its key is still unused: give it with /crates admin give");
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("item", ItemRegistry.uuidOf(taken).map(UUID::toString).orElse("none"));
        d.put("profile", profile.toString());
        m.audit("key_item_return_box", Actor.system(CratesModule.ID), uuid, name, 1L, d);
    }

    /** A copy (used-up or unknown id): it stays taken, the holder is told, audited and reported to the DupeDetector. */
    private void copy(UUID uuid, String name, ItemStack taken, UUID id, KeyItemStore.Row row, KeyItemStore.Outcome outcome) {
        Player player = Bukkit.getPlayer(uuid);
        String source = taken.getPersistentDataContainer().getOrDefault(ItemKeys.SOURCE, PersistentDataType.STRING, "?");
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("item", id == null ? "none" : id.toString());
        d.put("why", outcome == KeyItemStore.Outcome.ALREADY_USED ? "already_used" : "unknown");
        d.put("item_source", source);
        crateOf(taken).ifPresent(c -> d.put("crate", c));
        if (row != null) {
            d.put("state", row.state());
            d.put("withdrawn_by", row.accountName());
            if (row.redeemedName() != null) {
                d.put("redeemed_by", row.redeemedName());
                d.put("redeemed_at", row.redeemedAt());
            }
        }
        m.audit("key_item_copy", Actor.player(uuid, name), uuid, name, 1L, d);
        if (player != null) {
            m.fail(player, "key-items.copy");
        }
        if (id != null && m.core.dupes() != null) {
            String where = name + " main hand (" + (row == null ? "no such key item" : "already " + row.state()
                    + (row.redeemedName() != null ? " by " + row.redeemedName() : "")) + ")";
            m.core.dupes().report(new DupeReport(id, ITEM_ID, source, List.of(where), List.of(uuid), System.currentTimeMillis()));
        }
    }

    // ================================================================== key items are not crafting material

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isKeyItem(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter) {
            for (ItemStack item : crafter.getInventory().getContents()) {
                if (isKeyItem(item)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
}
