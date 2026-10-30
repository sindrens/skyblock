package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;

/**
 * {@code /enchanter} main menu (ENCHANTS §4.1):
 * <pre>
 *  row 2:  [Apply a book] [Upgrade]   [Combine books] [Remove]
 *  row 3:        [Book shop]      [Enchant guide]
 *  row 4:              [your crystals]
 *  row 6:  ←  ?  X  help
 * </pre>
 */
final class EnchanterMenu extends Menu {

    static final int SLOT_APPLY = 11;
    static final int SLOT_UPGRADE = 12;
    static final int SLOT_COMBINE = 14;
    static final int SLOT_REMOVE = 15;
    static final int SLOT_SHOP = 21;
    static final int SLOT_GUIDE = 23;
    static final int SLOT_CRYSTALS = 31;

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
    }

    @Override
    protected void build() {
        frame();
        int tools = module.ops().tools(viewer).size();
        long books = module.ops().books(viewer).stream().filter(b -> !b.book().scroll()).count();
        set(SLOT_APPLY, service.item(Material.ENCHANTED_BOOK, viewer, EnchantsModule.ID, "menu.main.apply", Msg.p("books", books)),
                click -> openChild(new ToolPickMenu(module, SlotMenu.Mode.APPLY)));
        set(SLOT_UPGRADE, service.item(Material.EXPERIENCE_BOTTLE, viewer, EnchantsModule.ID, "menu.main.upgrade", Msg.p("tools", tools)),
                click -> openChild(new ToolPickMenu(module, SlotMenu.Mode.UPGRADE)));
        set(SLOT_COMBINE, service.item(Material.ANVIL, viewer, EnchantsModule.ID, "menu.main.combine", Msg.p("books", books)),
                click -> openChild(new CombineMenu(module)));
        set(SLOT_REMOVE, service.item(Material.GRINDSTONE, viewer, EnchantsModule.ID, "menu.main.remove"),
                click -> openChild(new ToolPickMenu(module, SlotMenu.Mode.REMOVE)));
        set(SLOT_SHOP, service.item(Material.AMETHYST_SHARD, viewer, EnchantsModule.ID, "menu.main.shop"),
                click -> module.menus().openShop(viewer, this));
        set(SLOT_GUIDE, service.item(Material.KNOWLEDGE_BOOK, viewer, EnchantsModule.ID, "menu.main.guide"),
                click -> module.menus().openGuide(viewer, this));
        set(SLOT_CRYSTALS, crystals());
    }

    private org.bukkit.inventory.ItemStack crystals() {
        return service.item(Material.PRISMARINE_CRYSTALS, viewer, EnchantsModule.ID, "menu.main.crystals",
                Msg.p("crystals", Format.number(module.menus().crystals(viewer))));
    }
}
