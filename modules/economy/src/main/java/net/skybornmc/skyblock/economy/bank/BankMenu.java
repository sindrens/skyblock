package net.skybornmc.skyblock.economy.bank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.NumberPickerMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.economy.EconomyModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /bank} (M3.3, PHASE-3 mockup): personal bank on the left, island bank on the right. One plain click per action
 * (Bedrock-safe). Both banks are settled and read when the menu opens; the next-payout countdown updates every second.
 * <pre>
 *  R O B B B B B O R
 *  B . P . | . I . B      11 personal bank, 15 island bank (13/22/31/40 divider)
 *  B D W T | D W H B      19 deposit, 20 withdraw, 21 tiers | 23 deposit, 24 withdraw, 25 history
 *  B . C . | . C . B      29 interest (personal), 33 interest (island)
 *  B . H . | . L . B      38 history (personal), 42 your daily withdraw limit (island)
 *  ← O B ? ✘ H B O R      45 back, 48 info, 49 close, 50 help
 * </pre>
 */
final class BankMenu extends Menu {

    static final int SLOT_PERSONAL = 11;
    static final int SLOT_ISLAND = 15;
    static final int SLOT_P_DEPOSIT = 19;
    static final int SLOT_P_WITHDRAW = 20;
    static final int SLOT_P_TIERS = 21;
    static final int SLOT_I_DEPOSIT = 23;
    static final int SLOT_I_WITHDRAW = 24;
    static final int SLOT_I_HISTORY = 25;
    static final int SLOT_P_INTEREST = 29;
    static final int SLOT_I_INTEREST = 33;
    static final int SLOT_P_HISTORY = 38;
    static final int SLOT_I_LIMIT = 42;
    static final int[] DIVIDER = {13, 22, 31, 40};
    /** Every content slot, for the layout test. */
    static final List<Integer> CONTENT = List.of(SLOT_PERSONAL, SLOT_ISLAND, SLOT_P_DEPOSIT, SLOT_P_WITHDRAW, SLOT_P_TIERS,
            SLOT_I_DEPOSIT, SLOT_I_WITHDRAW, SLOT_I_HISTORY, SLOT_P_INTEREST, SLOT_I_INTEREST, SLOT_P_HISTORY, SLOT_I_LIMIT);

    private final BankFeature bank;
    private UUID profileKey;
    private BankFeature.IslandAccess access;
    private BankFeature.Account personal;
    private BankFeature.Account island;
    private long withdrawnToday;
    private long loadedPeriod = Long.MIN_VALUE;
    private boolean loading;
    private boolean failed;

    BankMenu(BankFeature bank) {
        this.bank = bank;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("bank.menu.title"));
    }

    @Override
    public String messageModule() {
        return EconomyModule.ID;
    }

    @Override
    public String infoKey() {
        return "bank.menu.info";
    }

    @Override
    public String helpTopic() {
        return "bank";
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    @Override
    protected void tick() {
        if (personal == null) {
            return;
        }
        if (bank.ops().currentPeriod() != loadedPeriod) {
            reload(); // a payout time passed while the menu was open
            return;
        }
        set(SLOT_P_INTEREST, interestItem(personal));
        if (island != null) {
            set(SLOT_I_INTEREST, interestItem(island));
        }
    }

    @Override
    protected void build() {
        frame();
        ItemStack divider = service.filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot : DIVIDER) {
            set(slot, divider);
        }
        if (personal == null) {
            if (failed) {
                set(SLOT_PERSONAL, service.item(Material.RED_STAINED_GLASS, viewer, "error-item"));
                set(SLOT_ISLAND, service.item(Material.RED_STAINED_GLASS, viewer, "error-item"));
                return;
            }
            set(SLOT_PERSONAL, service.item(Material.CLOCK, viewer, "loading"));
            set(SLOT_ISLAND, service.item(Material.CLOCK, viewer, "loading"));
            load();
            return;
        }
        buildPersonal();
        buildIsland();
    }

    // ------------------------------------------------------------------------------------------------ loading

    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        profileKey = bank.profiles().profileKey(viewer);
        access = bank.islandAccess(profileKey);
        UUID key = profileKey;
        BankFeature.IslandAccess acc = access;
        CompletableFuture<BankFeature.Account> p = bank.personal(key);
        CompletableFuture<BankFeature.Account> i = acc == null ? CompletableFuture.completedFuture(null) : bank.island(acc.island());
        CompletableFuture<Long> w = acc == null ? CompletableFuture.completedFuture(0L)
                : bank.ops().store().withdrawnOn(acc.island().islandId(), key, bank.ops().today());
        CompletableFuture.allOf(p, i, w).whenComplete((v, error) -> Bukkit.getScheduler().runTask(bank.core.plugin(), () -> {
            loading = false;
            if (error != null) {
                bank.core.plugin().getLogger().warning("Bank: loading the bank menu of " + viewer.getName() + " failed: " + error);
                failed = true;
            } else {
                personal = p.join();
                island = i.join();
                withdrawnToday = w.join();
                loadedPeriod = bank.ops().currentPeriod();
            }
            if (isOpen()) {
                refresh();
            }
        }));
    }

    private void reload() {
        personal = null;
        island = null;
        failed = false;
        refresh();
    }

    /** Opens a fresh copy of this menu (after an action: shows the new balances). */
    private void reopen(Player player) {
        BankMenu fresh = new BankMenu(bank);
        if (parent() != null) {
            fresh.parent(parent());
        }
        service.open(player, fresh);
    }

    // ------------------------------------------------------------------------------------------------ personal side

    private void buildPersonal() {
        BankFeature.Account a = personal;
        long cap = a.terms().capacity();
        TagResolver[] p = {amount("balance", a.balance()), amount("capacity", cap), amount("room", BankRules.room(cap, a.balance())),
                Msg.number("tier", a.tier()), Msg.number("max", bank.tiers().maxTier())};
        set(SLOT_PERSONAL, MenuButton.display(item(Material.GOLD_BLOCK, "bank.menu.personal", p)));
        long purse = bank.module.cachedMoney(viewer);
        long maxDeposit = BankRules.maxDeposit(purse, cap, a.balance());
        set(SLOT_P_DEPOSIT, depositButton(a, maxDeposit));
        set(SLOT_P_WITHDRAW, withdrawButton(a, a.balance(), null));
        set(SLOT_P_TIERS, MenuButton.of(item(Material.EXPERIENCE_BOTTLE, "bank.menu.tiers", p),
                click -> openChild(new BankTierMenu(bank, profileKey, a.tier()))));
        set(SLOT_P_INTEREST, interestItem(a));
        set(SLOT_P_HISTORY, MenuButton.of(item(Material.BOOK, "bank.menu.history-personal"),
                click -> openChild(new BankHistoryMenu(bank, a.ref(), text("bank.menu.history-personal.crumb")))));
    }

    // ------------------------------------------------------------------------------------------------ island side

    private void buildIsland() {
        if (access == null || island == null) {
            set(SLOT_ISLAND, MenuButton.display(item(Material.BARRIER, "bank.menu.no-island")));
            return;
        }
        BankFeature.Account a = island;
        long cap = a.terms().capacity();
        TagResolver[] p = {amount("balance", a.balance()), amount("capacity", cap), amount("room", BankRules.room(cap, a.balance())),
                Msg.p("island", access.island().name()), Msg.c("role", text("bank.roles." + access.role().name().toLowerCase(Locale.ROOT)))};
        set(SLOT_ISLAND, MenuButton.display(item(Material.ENDER_CHEST, "bank.menu.island", p)));
        if (access.deposit()) {
            set(SLOT_I_DEPOSIT, depositButton(a, BankRules.maxDeposit(bank.module.cachedMoney(viewer), cap, a.balance())));
        } else {
            set(SLOT_I_DEPOSIT, locked(Material.HOPPER, "bank.menu.deposit"));
        }
        long remaining = BankRules.remainingToday(cap, access.limitPercent(), withdrawnToday);
        if (access.withdraw()) {
            BankOps.Limit limit = new BankOps.Limit(access.island().islandId(), profileKey,
                    InterestMath.percentOf(cap, access.limitPercent()));
            set(SLOT_I_WITHDRAW, withdrawButton(a, BankRules.maxWithdraw(a.balance(), remaining), limit));
        } else {
            set(SLOT_I_WITHDRAW, locked(Material.DROPPER, "bank.menu.withdraw"));
        }
        if (access.history()) {
            set(SLOT_I_HISTORY, MenuButton.of(item(Material.KNOWLEDGE_BOOK, "bank.menu.history-island"),
                    click -> openChild(new BankHistoryMenu(bank, a.ref(), text("bank.menu.history-island.crumb")))));
        } else {
            set(SLOT_I_HISTORY, locked(Material.KNOWLEDGE_BOOK, "bank.menu.history-island"));
        }
        set(SLOT_I_INTEREST, interestItem(a));
        String limitKey = !access.withdraw() ? "bank.menu.limit.none"
                : remaining == Long.MAX_VALUE ? "bank.menu.limit.unlimited" : "bank.menu.limit.limited";
        set(SLOT_I_LIMIT, MenuButton.display(item(Material.PAPER, limitKey,
                amount("left", remaining == Long.MAX_VALUE ? 0 : remaining), Msg.number("percent", Math.max(0, access.limitPercent())))));
    }

    // ------------------------------------------------------------------------------------------------ buttons

    private MenuButton depositButton(BankFeature.Account a, long max) {
        long min = bank.config.depositMin;
        if (max < min) {
            return MenuButton.display(item(Material.HOPPER, "bank.menu.deposit-none"));
        }
        return MenuButton.of(item(Material.HOPPER, "bank.menu.deposit", amount("max", max)), click -> {
            UUID key = profileKey;
            Menu back = this;
            NumberPickerMenu picker = new NumberPickerMenu(v -> service.text(v, EconomyModule.ID, "bank.picker.deposit"), min, max, min,
                    steps(max), (player, value) -> bank.deposit(player, key, a.ref(), value, a.terms(), () -> reopen(player)));
            picker.parent(back);
            service.open(click.player(), picker);
        });
    }

    private MenuButton withdrawButton(BankFeature.Account a, long max, BankOps.Limit limit) {
        long min = bank.config.withdrawMin;
        boolean free = bank.ops().feeNow(1_000_000L) == 0;
        TagResolver[] p = {amount("max", max), Msg.p("fee", percent(bank.config.withdrawFeePercent)),
                Msg.p("free", BankFeature.time(bank.config.withdrawFreeMinutes * 60_000L))};
        if (max < min) {
            return MenuButton.display(item(Material.DROPPER, a.balance() < min ? "bank.menu.withdraw-none" : "bank.menu.withdraw-limit", p));
        }
        return MenuButton.of(item(Material.DROPPER, free ? "bank.menu.withdraw-free" : "bank.menu.withdraw", p), click -> {
            UUID key = profileKey;
            Menu back = this;
            NumberPickerMenu picker = new NumberPickerMenu(v -> service.text(v, EconomyModule.ID, "bank.picker.withdraw"), min, max, min,
                    steps(max), (player, value) -> confirmWithdraw(player, key, a, value, limit));
            picker.parent(back);
            service.open(click.player(), picker);
        });
    }

    /** Shows what the withdrawal costs (fee, forfeited interest) before anything moves. */
    private void confirmWithdraw(Player player, UUID key, BankFeature.Account a, long value, BankOps.Limit limit) {
        long fee = bank.ops().feeNow(value);
        TagResolver[] p = {amount("amount", value), amount("fee", fee), amount("net", value - fee),
                Msg.p("percent", percent(bank.config.withdrawFeePercent))};
        BankMenu fresh = new BankMenu(bank);
        if (parent() != null) {
            fresh.parent(parent());
        }
        ConfirmMenu confirm = new ConfirmMenu(v -> service.text(v, EconomyModule.ID, "bank.confirm.crumb"),
                service.item(Material.DROPPER, player, EconomyModule.ID, "bank.confirm.shown", p),
                v -> service.lines(v, EconomyModule.ID, fee > 0 ? "bank.confirm.fee" : "bank.confirm.free", p),
                pl -> bank.withdraw(pl, key, a.ref(), value, a.terms(), limit, () -> reopen(pl)), pl -> { });
        confirm.parent(fresh);
        service.open(player, confirm);
    }

    private ItemStack interestItem(BankFeature.Account a) {
        BankConfig cfg = bank.config;
        long next = InterestMath.untilNextPayout(System.currentTimeMillis(), cfg.periodMillis());
        TagResolver[] p = {Msg.p("rate", percent(a.terms().ratePercent())), Msg.p("period", BankFeature.time(cfg.periodMillis())),
                amount("cap", a.terms().payoutCap()), Msg.p("next", BankFeature.time(next)),
                Msg.p("need", BankFeature.time(cfg.activityMinutes * 60_000L)), Msg.number("days", cfg.activityDays),
                Msg.p("have", BankFeature.time(a.activityMinutes() * 60_000L))};
        List<Component> lore = new ArrayList<>(lines("bank.menu.interest.lore", p));
        String status;
        if (!a.terms().earning()) {
            status = a.ref().isIsland() ? "bank.menu.interest.paused-island" : "bank.menu.interest.paused";
        } else if (a.forfeited()) {
            status = "bank.menu.interest.forfeited";
        } else if (a.balance() >= a.terms().capacity()) {
            status = "bank.menu.interest.full";
        } else if (a.balance() <= 0) {
            status = "bank.menu.interest.empty";
        } else {
            status = "bank.menu.interest.earning";
        }
        lore.addAll(lines(status, p));
        return service.item(Material.CLOCK, text("bank.menu.interest.name", p), lore);
    }

    private MenuButton locked(Material icon, String key) {
        List<Component> lore = new ArrayList<>(lines(key + ".about"));
        lore.addAll(lines("bank.menu.locked"));
        return MenuButton.display(service.item(icon, text(key + ".name", amount("max", 0)), lore));
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** Three picker steps that fit the largest amount (e.g. max 1.2M: 10k, 100k, 1M). */
    static long[] steps(long max) {
        int digits = String.valueOf(Math.max(1, max)).length();
        long small = 1;
        for (int i = 0; i < digits - 3; i++) {
            small *= 10;
        }
        return new long[]{small, small * 10, small * 100};
    }

    /** 0.15 → "0.15", 2.0 → "2". */
    static String percent(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private TagResolver amount(String name, long value) {
        return bank.amountTag(viewer, name, value);
    }

    private ItemStack item(Material material, String key, TagResolver... placeholders) {
        return service.item(material, viewer, EconomyModule.ID, key, placeholders);
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, EconomyModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, EconomyModule.ID, key, placeholders);
    }
}
