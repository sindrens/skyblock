package net.skybornmc.skyblock.economy;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.item.DupeDetector;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.item.ItemType;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Cooldowns;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CartographyInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Money notes (M3.2), the in-game side: the {@code money_note} custom item, {@code /withdraw}, {@code /eco note} and
 * right-click redeeming. All money movement happens in {@link NoteService} (one database transaction each).
 *
 * <p>Anti-dupe: the note id is the item's unique id (so the {@link DupeDetector} also sees copies), the amount comes from
 * the database row, the item leaves the hand <i>before</i> the redeem runs, and a second redeem of the same id is
 * refused by the database: that copy is confiscated, staff are alerted and an audit row ({@code note_dupe}) is written.</p>
 */
final class MoneyNotes implements Listener {

    static final String ITEM_ID = "money_note";
    static final String PERM_WITHDRAW = "skyblock.economy.withdraw";
    static final String PERM_COOLDOWN_BYPASS = "skyblock.cooldown.bypass";

    static final NamespacedKey AMOUNT = ItemKeys.key("note_amount");
    static final NamespacedKey CURRENCY = ItemKeys.key("note_currency");
    static final NamespacedKey SIGNER = ItemKeys.key("note_signer");

    private static final String COOLDOWN = "withdraw";
    private static final String P = "note.";

    private final EconomyModule module;
    private final NoteService notes;
    private final Cooldowns cooldowns = new Cooldowns();
    /** Players with a withdraw or redeem in flight: one at a time, so spam-clicking never queues extra work. */
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    MoneyNotes(EconomyModule module, NoteService notes) {
        this.module = module;
        this.notes = notes;
    }

    void enable() {
        registerItem();
        module.core.plugin().getServer().getPluginManager().registerEvents(this, module.core.plugin());
    }

    void disable() {
        HandlerList.unregisterAll(this);
        module.core.items().unregister(ITEM_ID);
    }

    private String raw(String key) {
        String raw = module.core.messages().raw(EconomyModule.ID, key);
        return raw == null ? "<red>[missing: economy:" + key + "]</red>" : raw;
    }

    // ================================================================== item

    private void registerItem() {
        if (module.core.items().get(ITEM_ID).isPresent()) {
            return;
        }
        Material material = Material.matchMaterial(module.config.noteMaterial);
        if (material == null || !material.isItem() || material.isAir()) {
            module.core.plugin().getLogger().warning("economy.yml notes.item.material '" + module.config.noteMaterial
                    + "' is not an item; using PAPER.");
            material = Material.PAPER;
        }
        Rarity rarity = Arrays.stream(Rarity.values()).filter(r -> r.id().equalsIgnoreCase(module.config.noteRarity.trim()))
                .findFirst().orElse(Rarity.RARE);
        ItemDefinition.Builder builder = ItemDefinition.builder(ITEM_ID, material, ItemType.NOTE, rarity)
                .name(raw(P + "item.name-unknown"))
                .subtitle(raw(P + "item.subtitle"))
                .description(raw(P + "item.description"))
                .action(raw(P + "item.action-input"), raw(P + "item.action-text"))
                .unique()
                .renderer(new ItemDefinition.Renderer() {
                    @Override
                    public String name(ItemStack stack, ItemDefinition definition) {
                        Optional<Data> data = data(stack);
                        return data.map(d -> LoreBuilder.fill(raw(P + "item.name"), Map.of(
                                "currency", d.currency().key(),
                                "amount", LoreBuilder.escape(exact(d.currency(), d.amount()))))).orElse(definition.name());
                    }

                    @Override
                    public void lore(ItemStack stack, ItemDefinition definition, LoreBuilder lore) {
                        Optional<Data> data = data(stack);
                        if (data.isEmpty()) {
                            return;
                        }
                        Data d = data.get();
                        LoreBuilder.Section info = lore.section(raw(P + "item.info-title"));
                        info.text(LoreBuilder.fill(raw(P + "item.amount"), Map.of("currency", d.currency().key(),
                                "amount", LoreBuilder.escape(exact(d.currency(), d.amount())))));
                        info.text(LoreBuilder.fill(raw(P + "item.signed-by"), Map.of("signer", LoreBuilder.escape(d.signer()))));
                        info.text(LoreBuilder.fill(raw(P + "item.date"), Map.of("date", LoreBuilder.escape(date(d.created())))));
                        info.text(LoreBuilder.fill(raw(P + "item.id"), Map.of("id", LoreBuilder.escape(MoneyNote.shortId(d.id())))));
                    }
                });
        if (module.config.noteGlint) {
            builder.glint();
        }
        module.core.items().register(builder.build());
    }

    /** What a note item says about itself (display only: redeeming reads the amount from the database). */
    record Data(UUID id, long amount, Currency currency, String signer, long created) {
    }

    static Optional<Data> data(ItemStack stack) {
        if (!ItemRegistry.is(stack, ITEM_ID)) {
            return Optional.empty();
        }
        Optional<UUID> id = ItemRegistry.uuidOf(stack);
        var pdc = stack.getPersistentDataContainer();
        Long amount = pdc.get(AMOUNT, PersistentDataType.LONG);
        String currency = pdc.get(CURRENCY, PersistentDataType.STRING);
        if (id.isEmpty() || amount == null || currency == null) {
            return Optional.empty();
        }
        String signer = pdc.getOrDefault(SIGNER, PersistentDataType.STRING, "?");
        long created = pdc.getOrDefault(ItemKeys.CREATED, PersistentDataType.LONG, 0L);
        return Currency.byKey(currency).map(c -> new Data(id.get(), amount, c, signer, created));
    }

    private ItemStack create(MoneyNote note, String source) {
        return module.core.items().create(ITEM_ID, 1, source, (PersistentDataContainer pdc) -> {
            pdc.set(ItemKeys.UUID, PersistentDataType.STRING, note.id().toString()); // note id = unique item id
            pdc.set(ItemKeys.CREATED, PersistentDataType.LONG, note.createdAt());
            pdc.set(AMOUNT, PersistentDataType.LONG, note.amount());
            pdc.set(CURRENCY, PersistentDataType.STRING, note.currency().key());
            pdc.set(SIGNER, PersistentDataType.STRING, note.createdName());
        });
    }

    /** Every digit with the currency symbol: {@code $50,000}, {@code ✦ 1,500}. */
    private String exact(Currency currency, long amount) {
        String symbol = module.core.style().symbol(currency.key());
        return currency == Currency.MONEY ? symbol + Format.number(amount) : symbol + " " + Format.number(amount);
    }

    private String date(long millis) {
        try {
            return DateTimeFormatter.ofPattern(module.config.noteDateFormat, Locale.ROOT).withZone(net.skybornmc.skyblock.common.util.Format.zone())
                    .format(Instant.ofEpochMilli(millis));
        } catch (IllegalArgumentException e) {
            return DateTimeFormatter.ISO_LOCAL_DATE.withZone(net.skybornmc.skyblock.common.util.Format.zone()).format(Instant.ofEpochMilli(millis));
        }
    }

    /** Puts the item in the inventory; anything that doesn't fit goes to /box (owner answer 7: never dropped). */
    private void give(Player player, ItemStack item) {
        net.skybornmc.skyblock.common.box.ItemBox.give(module.core.services(), player, net.skybornmc.skyblock.common.box.BoxSource.NOTES, item);
    }

    // ================================================================== commands

    private List<String> allowedKeys() {
        return Arrays.stream(Currency.values()).filter(c -> NoteRules.allowed(module.config, c)).map(Currency::key).toList();
    }

    CommandSpec withdrawCommand() {
        Arg<Long> amount = Args.amount("amount");
        Arg<Currency> currency = Arg.word("currency", (sender, raw) -> Currency.byKey(raw)
                        .filter(c -> NoteRules.allowed(module.config, c))
                        .orElseThrow(() -> CommandFailure.of("invalid-choice", Msg.p("input", raw),
                                Msg.p("choices", String.join(", ", allowedKeys())))),
                Suggest.of(this::allowedKeys));
        return CommandSpec.command("withdraw")
                .description("Turn money into a Money Note item you can trade")
                .category("Economy")
                .permission(PERM_WITHDRAW)
                .playerOnly()
                .arg(amount).optional(currency)
                .example("withdraw 50k")
                .executes(ctx -> withdraw(ctx.player(), ctx.get(amount), ctx.getOr(currency, Currency.MONEY)));
    }

    /** {@code /eco note <player> <amount> <reason>}: staff make a note without taking money. */
    CommandSpec ecoNoteCommand(String[] reasons) {
        Arg<Player> target = Args.onlinePlayer("player");
        Arg<Long> amount = Args.amount("amount");
        Arg<String> reason = Args.text("reason", reasons);
        return CommandSpec.sub("note")
                .description("Give an online player a money note (no money is taken)")
                .arg(target).arg(amount).arg(reason)
                .example("eco note Steve 50k Event prize")
                .executes(ctx -> adminNote(ctx.sender(), ctx.get(target), ctx.get(amount), ctx.get(reason)));
    }

    private CommandFailure fail(String key, TagResolver... placeholders) {
        return new CommandFailure(EconomyModule.ID, P + key, placeholders);
    }

    // ================================================================== /withdraw

    private void withdraw(Player player, long amount, Currency currency) {
        if (net.skybornmc.skyblock.common.moderation.SanctionService.denied(module.core.services(), player, net.skybornmc.skyblock.common.moderation.SanctionService.Use.NOTE)) {
            return; // P7: frozen balance or blocked trading
        }
        UUID uuid = player.getUniqueId();
        long played = module.core.players().get(uuid).map(PlayerData::playtimeMillis).orElse(0L);
        Optional<NoteRules.Problem> problem = NoteRules.withdraw(module.config, amount, currency, module.frozen(player), played);
        if (problem.isPresent()) {
            throw fail(problem.get().key, Msg.c("currency", module.currencyName(player, currency)),
                    module.amountTag("min", player, currency, module.config.noteMin(currency)),
                    module.exactAmountTag("max", player, currency, module.config.noteMax(currency)),
                    Msg.p("time", Format.duration(Duration.ofMillis(NoteRules.playtimeLeft(module.config, played)))));
        }
        if (player.getInventory().firstEmpty() < 0) {
            throw fail("inventory-full");
        }
        Duration left = cooldowns.remaining(uuid, COOLDOWN);
        if (!left.isZero() && !player.hasPermission(PERM_COOLDOWN_BYPASS)) {
            throw fail("cooldown", Msg.p("time", Format.duration(left)));
        }
        if (!busy.add(uuid)) {
            throw fail("busy");
        }
        cooldowns.set(uuid, COOLDOWN, Duration.ofSeconds(module.config.noteWithdrawCooldownSeconds));
        UUID id = UUID.randomUUID();
        String name = player.getName();
        // The note belongs to the profile the money comes from (PROF): created_by = the active profile key.
        UUID key = module.profiles().profileKey(uuid);
        module.then(player, notes.withdraw(id, key, uuid, name, currency, amount).whenComplete((r, e) -> busy.remove(uuid)), result -> {
            switch (result.outcome()) {
                case SUCCESS -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online == null || module.profiles().isSwitching(uuid) || !module.profiles().profileKey(uuid).equals(key)) {
                        // Left (or switched profile) before the note could be handed out: cash it straight back to the
                        // same profile (same atomic redeem path), so it never lands in another profile's inventory.
                        refundOffline(result.note(), key, uuid, name);
                        return;
                    }
                    give(online, create(result.note(), "withdraw:" + name));
                    module.msg.send(online, P + "withdrawn", module.exactAmountTag("amount", online, currency, amount),
                            module.amountTag("balance", online, currency, result.balance() == null ? 0 : result.balance()),
                            Msg.p("id", MoneyNote.shortId(id)));
                    module.core.sounds().play(online, "success");
                }
                case INSUFFICIENT_FUNDS -> {
                    cooldowns.clear(uuid, COOLDOWN);
                    long have = module.service().cached(uuid, currency).orElse(0);
                    module.msg.send(player, P + "insufficient", module.exactAmountTag("amount", player, currency, amount),
                            module.amountTag("balance", player, currency, have));
                    module.core.sounds().play(player, "error");
                }
                default -> {
                    cooldowns.clear(uuid, COOLDOWN);
                    module.msg.send(player, "error");
                    module.core.sounds().play(player, "error");
                }
            }
        });
    }

    private void refundOffline(MoneyNote note, UUID key, UUID player, String name) {
        notes.redeem(note.id(), key, player, name).thenAccept(r -> module.core.plugin().getLogger().info("Money notes: " + name
                + " left before note " + note.id() + " was handed out; refunded: " + r.outcome()));
    }

    // ================================================================== /eco note

    private void adminNote(CommandSender sender, Player target, long amount, String reason) {
        if (NoteRules.admin(module.config, amount).isPresent()) {
            throw fail("above-maximum", Msg.c("currency", module.currencyName(sender, Currency.MONEY)),
                    module.exactAmountTag("max", sender, Currency.MONEY, module.config.noteMax(Currency.MONEY)));
        }
        if (target.getInventory().firstEmpty() < 0) {
            throw fail("admin-inventory-full", Msg.p("player", target.getName()));
        }
        Actor staff = Actor.of(sender);
        UUID targetId = target.getUniqueId();
        String targetName = target.getName();
        UUID id = UUID.randomUUID();
        module.then(sender, notes.createAdmin(id, Currency.MONEY, amount, staff, reason), result -> {
            if (!result.ok()) {
                module.msg.send(sender, "error");
                return;
            }
            Player online = Bukkit.getPlayer(targetId);
            if (online == null) {
                notes.voidNote(id);
                module.msg.send(sender, P + "admin-target-left", Msg.p("player", targetName));
                return;
            }
            give(online, create(result.note(), "admin:" + staff.name()));
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("note_id", id.toString());
            details.put("currency", Currency.MONEY.key());
            details.put("reason", reason);
            module.core.audit().logPlayer(AuditCategory.ECONOMY, TxReason.ADMIN_NOTE, staff, targetId, targetName, amount,
                    online.getLocation(), details);
            TagResolver[] p = {Msg.p("player", targetName), Msg.p("staff", sender.getName()), Msg.p("reason", reason),
                    Msg.p("id", MoneyNote.shortId(id)), module.exactAmountTag("amount", sender, Currency.MONEY, amount)};
            module.msg.send(sender, P + "admin-done", p);
            if (sender instanceof Player s) {
                module.core.sounds().play(s, "success");
            }
            if (!online.equals(sender)) {
                module.msg.send(online, P + "admin-received", module.exactAmountTag("amount", online, Currency.MONEY, amount));
                module.core.sounds().play(online, "money");
            }
            for (Player staffMember : Bukkit.getOnlinePlayers()) {
                if (!staffMember.equals(sender) && staffMember.hasPermission(EconomyModule.PERM_ALERTS)) {
                    module.msg.send(staffMember, P + "admin-alert", p);
                }
            }
            if (!(sender instanceof ConsoleCommandSender)) {
                module.msg.send(Bukkit.getConsoleSender(), P + "admin-alert", p);
            }
        });
    }

    // ================================================================== redeem

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack hand = event.getItem();
        if (!ItemRegistry.is(hand, ITEM_ID)) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // notes are redeemed from the main hand only
        }
        Player player = event.getPlayer();
        if (net.skybornmc.skyblock.common.moderation.SanctionService.denied(module.core.services(), player, net.skybornmc.skyblock.common.moderation.SanctionService.Use.NOTE)) {
            return; // P7: blocked trading (a note is money from someone else)
        }
        if (module.frozen(player)) {
            module.msg.send(player, P + "frozen-redeem");
            module.core.sounds().play(player, "error");
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!busy.add(uuid)) {
            return; // one redeem at a time per player
        }
        // Take the note out of the hand first, so this exact item can't be used again while the database works.
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
        Location where = player.getLocation();
        Optional<UUID> id = ItemRegistry.uuidOf(taken);
        if (id.isEmpty()) {
            busy.remove(uuid);
            confiscate(player, uuid, name, where, null, NoteService.Result.of(NoteService.Outcome.UNKNOWN, null), taken);
            return;
        }
        UUID key = module.profiles().profileKey(uuid);
        notes.redeem(id.get(), key, uuid, name).whenComplete((r, e) -> busy.remove(uuid)).whenCompleteAsync((result, error) -> {
            if (error != null || result == null) {
                giveBack(uuid, name, taken, id.get(), "error");
                return;
            }
            switch (result.outcome()) {
                case SUCCESS -> redeemed(uuid, result);
                case ALREADY_REDEEMED, UNKNOWN, VOID -> confiscate(Bukkit.getPlayer(uuid), uuid, name, where, id.get(), result, taken);
                case OVERFLOW -> giveBack(uuid, name, taken, id.get(), "overflow");
                case OWN_PROFILE -> giveBack(uuid, name, taken, id.get(), "own-profile");
                default -> giveBack(uuid, name, taken, id.get(), "error");
            }
        }, module.mainThread());
    }

    private void redeemed(UUID uuid, NoteService.Result result) {
        Player online = Bukkit.getPlayer(uuid);
        if (online == null) {
            return; // the money is on their account; nothing to show
        }
        MoneyNote note = result.note();
        module.msg.send(online, P + "redeemed", module.exactAmountTag("amount", online, note.currency(), note.amount()),
                Msg.p("signer", note.createdName()),
                module.amountTag("balance", online, note.currency(), result.balance() == null ? 0 : result.balance()));
        module.core.sounds().play(online, "money");
    }

    /** Redeem failed without changing anything: the note goes back to the player. */
    private void giveBack(UUID uuid, String name, ItemStack taken, UUID id, String why) {
        Player online = Bukkit.getPlayer(uuid);
        if (online == null) {
            // Nothing was cashed and the note is still active in the database; staff can re-issue it (/eco note).
            module.core.plugin().getLogger().severe("Money notes: could not return note " + id + " to " + name + " (offline after a "
                    + why + "); the note is still active, re-issue it with /eco note.");
            module.core.audit().logPlayer(AuditCategory.ECONOMY, "note_return_failed", Actor.SYSTEM, uuid, name,
                    data(taken).map(Data::amount).orElse(null), null, Map.of("note_id", id.toString(), "why", why));
            return;
        }
        give(online, taken);
        module.msg.send(online, switch (why) {
            case "overflow" -> P + "overflow";
            case "own-profile" -> P + "own-profile";
            default -> "error";
        });
        module.core.sounds().play(online, "error");
    }

    /** A copy of a cashed note (or a note that doesn't exist): it stays taken, staff are told, an audit row is written. */
    private void confiscate(Player online, UUID uuid, String name, Location where, UUID id, NoteService.Result result, ItemStack taken) {
        MoneyNote note = result.note();
        String why = switch (result.outcome()) {
            case ALREADY_REDEEMED -> "already_redeemed";
            case VOID -> "void";
            default -> "unknown";
        };
        Optional<Data> shown = data(taken);
        long amount = note != null ? note.amount() : shown.map(Data::amount).orElse(0L);
        Currency currency = note != null ? note.currency() : shown.map(Data::currency).orElse(Currency.MONEY);
        String noteId = id == null ? "none" : id.toString();
        String cashedBy = note != null && note.redeemedName() != null ? note.redeemedName() : "-";
        String cashedAgo = note != null && note.redeemedAt() != null
                ? Format.duration(Duration.ofMillis(Math.max(0, System.currentTimeMillis() - note.redeemedAt()))) : "-";

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("note_id", noteId);
        details.put("why", why);
        details.put("currency", currency.key());
        details.put("item_source", taken.getPersistentDataContainer().getOrDefault(ItemKeys.SOURCE, PersistentDataType.STRING, "?"));
        if (note != null) {
            details.put("signed_by", note.createdName());
            details.put("note_kind", note.kind().key());
            if (note.redeemedName() != null) {
                details.put("redeemed_by", note.redeemedName());
                details.put("redeemed_at", note.redeemedAt());
            }
        }
        module.core.audit().logPlayer(AuditCategory.ECONOMY, "note_dupe", Actor.player(uuid, name), uuid, name, amount, where, details);

        TagResolver[] staffP = {Msg.p("player", name), Msg.p("id", id == null ? "-" : MoneyNote.shortId(id)),
                Msg.p("by", cashedBy), Msg.p("ago", cashedAgo), Msg.p("why", raw(P + "why." + why))};
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(DupeDetector.PERM_NOTIFY)) {
                module.msg.send(staff, P + "dupe-alert", append(staffP, module.exactAmountTag("amount", staff, currency, amount)));
                module.core.sounds().play(staff, "warning");
            }
        }
        module.msg.send(Bukkit.getConsoleSender(), P + "dupe-alert",
                append(staffP, module.exactAmountTag("amount", Bukkit.getConsoleSender(), currency, amount)));
        module.core.dupes().requestScan(); // other copies of this id may still be around
        if (online != null) {
            module.msg.send(online, P + "confiscated", Msg.p("why", raw(P + "why." + why)));
            module.core.sounds().play(online, "error");
        }
    }

    private static TagResolver[] append(TagResolver[] base, TagResolver extra) {
        TagResolver[] out = Arrays.copyOf(base, base.length + 1);
        out[base.length] = extra;
        return out;
    }

    // ================================================================== notes are not crafting/trading material

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (ItemRegistry.is(item, ITEM_ID)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        // The auto-crafter has no recipe that turns a note into money; never let it eat one.
        if (event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter) {
            for (ItemStack item : crafter.getInventory().getContents()) {
                if (ItemRegistry.is(item, ITEM_ID)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareResult(PrepareResultEvent event) {
        if (event.getInventory() instanceof CartographyInventory inv) {
            for (ItemStack item : inv.getContents()) {
                if (ItemRegistry.is(item, ITEM_ID)) {
                    event.setResult(null);
                    return;
                }
            }
        }
    }

    /** Villagers buy paper: a note must never be sold to one. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMerchantClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory() instanceof MerchantInventory)) {
            return;
        }
        ItemStack hotbar = event.getHotbarButton() >= 0 ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null;
        if (ItemRegistry.is(event.getCurrentItem(), ITEM_ID) || ItemRegistry.is(event.getCursor(), ITEM_ID)
                || ItemRegistry.is(hotbar, ITEM_ID)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMerchantDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory() instanceof MerchantInventory && ItemRegistry.is(event.getOldCursor(), ITEM_ID)) {
            event.setCancelled(true);
        }
    }
}
