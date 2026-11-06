package net.skybornmc.skyblock.shardblock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.menu.Centered;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuLayout;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * Right-click on a shard block (ECON-SHARDS step 11, panel P3): wallet on slot 4, the two upgrades centred in row 1
 * (Faster Respawn, Double Chance), Pick Up on action slot A, Close. One tap per action (Bedrock-safe). Refreshes every
 * second for the regrow countdown.
 */
final class ShardBlockMenu extends Menu {

    static final int ROWS = 3;
    static final int SLOT_WALLET = 4;

    private final ShardBlockModule module;
    private final ShardBlockManager.Placed block;

    ShardBlockMenu(ShardBlockModule module, ShardBlockManager.Placed block) {
        this.module = module;
        this.block = block;
    }

    @Override
    public int rows() {
        return ROWS;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, ShardBlockModule.ID, "menu.title"));
    }

    @Override
    public String messageModule() {
        return ShardBlockModule.ID;
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    @Override
    protected void tick() {
        if (block.removed) {
            viewer.closeInventory();
            return;
        }
        refresh();
    }

    /** Slots of the two upgrade tiles in row 1 ({@code Centered.columns(2)} → 12 and 14). */
    static int[] upgradeSlots() {
        int[] cols = Centered.columns(2);
        return new int[]{9 + cols[0], 9 + cols[1]};
    }

    @Override
    protected void build() {
        navBar();
        ShardBlockConfig cfg = module.config();
        set(SLOT_WALLET, service.item(cfg.icon("wallet", Material.AMETHYST_CLUSTER), viewer, ShardBlockModule.ID, "menu.wallet",
                Msg.p("crystals", balance(Currency.CRYSTALS)), Msg.p("shards", balance(Currency.SHARDS)), state()));
        int[] slots = upgradeSlots();
        tile(slots[0], ShardRules.Kind.RESPAWN, cfg.icon("respawn", Material.CLOCK));
        tile(slots[1], ShardRules.Kind.DOUBLE, cfg.icon("double", Material.AMETHYST_SHARD));
        set(MenuLayout.actionA(ROWS), service.item(cfg.icon("pickup", Material.HOPPER), viewer, ShardBlockModule.ID, "menu.pickup"),
                click -> module.manager().pickUp(viewer, block));
    }

    private TagResolver state() {
        long now = System.currentTimeMillis();
        Component text = block.ready(now) ? service.text(viewer, ShardBlockModule.ID, "menu.state-ready")
                : service.text(viewer, ShardBlockModule.ID, "menu.state-regrowing",
                Msg.p("time", Format.duration(Duration.ofMillis(block.readyAt - now))));
        return Msg.c("state", text);
    }

    private void tile(int slot, ShardRules.Kind kind, Material icon) {
        ShardRules rules = module.rules();
        int level = kind == ShardRules.Kind.RESPAWN ? block.respawnLevel : block.doubleLevel;
        int max = rules.maxLevel(kind);
        ShardRules.Cost cost = rules.cost(kind, level + 1);
        String k = "menu." + kind.key();
        List<Component> lore = new ArrayList<>();
        lore.add(service.text(viewer, ShardBlockModule.ID, "menu.level", Msg.number("level", level), Msg.number("max", max)));
        lore.add(service.text(viewer, ShardBlockModule.ID, k + ".now", value(kind, level)));
        if (cost == null) {
            lore.add(Component.empty());
            lore.add(service.text(viewer, ShardBlockModule.ID, "menu.maxed"));
            set(slot, service.item(level >= max ? module.config().icon("maxed", Material.GRAY_DYE) : icon,
                    service.text(viewer, ShardBlockModule.ID, k + ".name"), lore));
            return;
        }
        lore.add(service.text(viewer, ShardBlockModule.ID, k + ".next", value(kind, level + 1)));
        lore.add(service.text(viewer, ShardBlockModule.ID, "menu.cost", Msg.p("price", module.price(cost))));
        lore.add(Component.empty());
        lore.add(service.text(viewer, ShardBlockModule.ID, "menu.action"));
        set(slot, service.item(icon, service.text(viewer, ShardBlockModule.ID, k + ".name"), lore),
                click -> module.manager().upgrade(viewer, block, kind));
    }

    private TagResolver value(ShardRules.Kind kind, int level) {
        ShardRules rules = module.rules();
        if (kind == ShardRules.Kind.RESPAWN) {
            return Msg.p("value", Format.duration(Duration.ofSeconds(rules.respawnSeconds(level))));
        }
        double p = rules.doublePercent(level);
        return Msg.p("value", (p == Math.rint(p) ? String.valueOf((long) p) : String.valueOf(p)) + "%");
    }

    private String balance(Currency currency) {
        OptionalLong v = module.core().services().optional(EconomyService.class)
                .map(e -> e.cached(viewer.getUniqueId(), currency)).orElse(OptionalLong.empty());
        return v.isPresent() ? module.price(new ShardRules.Cost(currency, v.getAsLong())) : "...";
    }
}
