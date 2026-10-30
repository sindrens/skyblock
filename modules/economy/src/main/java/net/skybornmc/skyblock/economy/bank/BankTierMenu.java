package net.skybornmc.skyblock.economy.bank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.economy.EconomyModule;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Personal bank tiers (M3.3): the 10 tiers with capacity, unlock cost, payout cap and perks. Owned tiers are gold, the
 * next one can be bought with one click (confirm screen), later ones are locked. The item count is the tier number.
 * <pre>
 *  R O B B B B B O R
 *  B . 1 2 3 4 5 . B      11-15 tiers 1-5
 *  B . 6 7 8 9 10 . B     20-24 tiers 6-10
 *  ...
 * </pre>
 */
final class BankTierMenu extends Menu {

    static final int[] SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33};

    private final BankFeature bank;
    private final UUID profileKey;
    private final int tier;

    BankTierMenu(BankFeature bank, UUID profileKey, int tier) {
        this.bank = bank;
        this.profileKey = profileKey;
        this.tier = tier;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, EconomyModule.ID, "bank.menu.title"),
                service.text(viewer, EconomyModule.ID, "bank.tiers.crumb"));
    }

    @Override
    public String messageModule() {
        return EconomyModule.ID;
    }

    @Override
    public String infoKey() {
        return "bank.tiers.info";
    }

    @Override
    public String helpTopic() {
        return "bank";
    }

    @Override
    protected void build() {
        frame();
        BankTiers tiers = bank.tiers();
        for (BankTiers.PersonalTier t : tiers.personal()) {
            int index = t.tier() - 1;
            if (index >= SLOTS.length) {
                break;
            }
            set(SLOTS[index], button(t));
        }
    }

    private MenuButton button(BankTiers.PersonalTier t) {
        TagResolver[] p = {Msg.number("tier", t.tier()), bank.amountTag(viewer, "capacity", t.capacity()),
                bank.amountTag(viewer, "cost", t.cost()), bank.amountTag(viewer, "cap", t.payoutCap())};
        String state = t.tier() <= tier ? "owned" : t.tier() == tier + 1 ? "next" : "locked";
        Material icon = switch (state) {
            case "owned" -> Material.GOLD_BLOCK;
            case "next" -> Material.EMERALD_BLOCK;
            default -> Material.IRON_BARS;
        };
        List<Component> lore = new ArrayList<>(service.lines(viewer, EconomyModule.ID, "bank.tiers.lore", p));
        String perksKey = "bank.perks.tier-" + t.tier();
        List<Component> perks = service.lines(viewer, EconomyModule.ID, perksKey, p);
        lore.add(Component.empty());
        lore.addAll(service.lines(viewer, EconomyModule.ID, "bank.tiers.perks-title", p));
        lore.addAll(perks.isEmpty() ? service.lines(viewer, EconomyModule.ID, "bank.tiers.no-perks", p) : perks);
        lore.add(Component.empty());
        lore.addAll(service.lines(viewer, EconomyModule.ID, "bank.tiers." + state, p));
        ItemStack item = service.item(icon, service.text(viewer, EconomyModule.ID, "bank.tiers.name-" + state, p), lore);
        item.setAmount(Math.max(1, Math.min(64, t.tier())));
        if (!state.equals("next")) {
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> {
            BankMenu back = new BankMenu(bank);
            if (parent() != null && parent().parent() != null) {
                back.parent(parent().parent());
            }
            ConfirmMenu confirm = new ConfirmMenu(v -> service.text(v, EconomyModule.ID, "bank.tiers.confirm-crumb", p), item.clone(),
                    v -> service.lines(v, EconomyModule.ID, "bank.tiers.confirm", p),
                    player -> bank.buyTier(player, profileKey, tier, () -> service.open(player, back)), player -> { });
            confirm.parent(this);
            service.open(click.player(), confirm);
        });
    }
}
