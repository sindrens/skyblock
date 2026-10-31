package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;

/**
 * {@code /enchanter} main menu (ENCHANTS §4.1):
 * <pre>
 *  row 2:  [Apply a book] [Upgrade] [Armor (E5)] [Combine books] [Remove]
 *  row 3:        [Book shop]      [Enchant guide]
 *  row 4:      [your crystals]   [next guaranteed book]
 *  row 6:  ←  ?  X  help
 * </pre>
 */
final class EnchanterMenu extends Menu {

    static final int SLOT_APPLY = 11;
    static final int SLOT_UPGRADE = 12;
    static final int SLOT_ARMOR = 13;
    static final int SLOT_COMBINE = 14;
    static final int SLOT_REMOVE = 15;
    static final int SLOT_SHOP = 21;
    static final int SLOT_GUIDE = 23;
    static final int SLOT_CRYSTALS = 30;
    static final int SLOT_NEXT_BOOK = 32;

    private final EnchantsModule module;

    EnchanterMenu(EnchantsModule module) {
        this.module = module;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, EnchantsModule.ID, "menu.title");
    }

    @Override
    public String messageModule() {
        return EnchantsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.main.info";
    }

    @Override
    public String helpTopic() {
        return "enchants";
    }

    @Override
    public int tickInterval() {
        return 40;
    }

    @Override
    protected void tick() {
        set(SLOT_CRYSTALS, crystals());
        set(SLOT_NEXT_BOOK, nextBook());
    }

    @Override
    protected void build() {
        frame();
        int tools = module.ops().tools(viewer).size();
        long books = module.ops().books(viewer).stream().filter(b -> !b.book().scroll()).count();
        set(SLOT_APPLY, service.item(Material.ENCHANTED_BOOK, viewer, EnchantsModule.ID, "menu.main.apply", Msg.p("books", books)),
                click -> ToolPickMenu.open(this, module, SlotMenu.Mode.APPLY));
        set(SLOT_UPGRADE, service.item(Material.EXPERIENCE_BOTTLE, viewer, EnchantsModule.ID, "menu.main.upgrade", Msg.p("tools", tools)),
                click -> ToolPickMenu.open(this, module, SlotMenu.Mode.UPGRADE));
        int sets = module.wardrobe() == null ? 0 : module.wardrobe().entries(viewer).size();
        set(SLOT_ARMOR, service.item(Material.DIAMOND_CHESTPLATE, viewer, EnchantsModule.ID,
                module.wardrobe() == null ? "menu.main.armor-off" : "menu.main.armor", Msg.p("sets", sets)),
                click -> {
                    if (module.wardrobe() == null) {
                        service.sound(viewer, "error");
                        return;
                    }
                    module.menus().openArmor(viewer, this);
                });
        set(SLOT_COMBINE, service.item(Material.ANVIL, viewer, EnchantsModule.ID, "menu.main.combine", Msg.p("books", books)),
                click -> openChild(new CombineMenu(module)));
        set(SLOT_REMOVE, service.item(Material.GRINDSTONE, viewer, EnchantsModule.ID, "menu.main.remove"),
                click -> ToolPickMenu.open(this, module, SlotMenu.Mode.REMOVE));
        set(SLOT_SHOP, service.item(Material.AMETHYST_SHARD, viewer, EnchantsModule.ID, "menu.main.shop"),
                click -> module.menus().openShop(viewer, this));
        set(SLOT_GUIDE, service.item(Material.KNOWLEDGE_BOOK, viewer, EnchantsModule.ID, "menu.main.guide"),
                click -> module.menus().openGuide(viewer, this));
        set(SLOT_CRYSTALS, crystals());
        set(SLOT_NEXT_BOOK, nextBook());
    }

    /** ENCHANTS §3.4 / §4.1: actions until the next guaranteed book per Depths activity (first finds and pity). */
    private org.bukkit.inventory.ItemStack nextBook() {
        java.util.Map<Pool, Long> next = module.drops() == null ? java.util.Map.of() : module.drops().nextGuaranteed(viewer);
        return service.item(Material.CLOCK, viewer, EnchantsModule.ID, "menu.main.next-book",
                Msg.p("mining", count(next, Pool.MINING)), Msg.p("foraging", count(next, Pool.FORAGING)),
                Msg.p("combat", count(next, Pool.COMBAT)));
    }

    private String count(java.util.Map<Pool, Long> next, Pool pool) {
        if (!next.containsKey(pool)) {
            return module.text().plain("menu.main.next-book.closed");
        }
        Long n = next.get(pool);
        if (n == null) {
            return "...";
        }
        return n == Long.MAX_VALUE ? module.text().plain("menu.main.next-book.none") : Format.number(n);
    }

    private org.bukkit.inventory.ItemStack crystals() {
        return service.item(Material.PRISMARINE_CRYSTALS, viewer, EnchantsModule.ID, "menu.main.crystals",
                Msg.p("crystals", Format.number(module.menus().crystals(viewer))));
    }
}
