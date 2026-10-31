package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PickListMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Island chat role colors (M2.5, owner I11): one wool per role in its current color. The leader clicks a role and picks
 * its color from a small list of {@code chat.palette} (GUI-4/G9, one plain click, Bedrock-safe); everyone else sees them
 * read-only.
 * <pre>
 *  R O B B ⓘ B B O R      4 summary
 *  B L . A . M . E B      19 leader, 21 admin, 23 mod, 25 member
 *  ...
 *  ← O B ? ✘ H B O R
 * </pre>
 */
final class ChatColorsMenu extends Menu {

    static final int SLOT_SUMMARY = 4;
    static final Map<Role, Integer> SLOTS = Map.of(Role.LEADER, 19, Role.ADMIN, 21, Role.MOD, 23, Role.MEMBER, 25);

    private final IslandsModule module;
    private final IslandManager islands;
    private final IslandChat chat;
    private final Island island;
    private boolean pending;

    ChatColorsMenu(IslandsModule module, IslandManager islands, IslandChat chat, Island island) {
        this.module = module;
        this.islands = islands;
        this.chat = chat;
        this.island = island;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("menu.crumb"), text("chat.colors.crumb"));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return "chat.colors.info";
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
        if (islands.byId(island.id()) != island || !IslandChatRules.chats(island.roleOrNull(islands.key(viewer)))) {
            viewer.closeInventory();
            return;
        }
        if (!pending) {
            refresh();
        }
    }

    @Override
    protected void build() {
        frame();
        boolean editable = IslandChatRules.canPickColors(island.roleOrNull(islands.key(viewer)));
        set(SLOT_SUMMARY, MenuButton.display(service.item(new ItemStack(Material.OAK_SIGN), text("chat.colors.summary.name"),
                lines(editable ? "chat.colors.summary.lore-edit" : "chat.colors.summary.lore-view",
                        Msg.p("count", chat.palette().size())))));
        for (Role role : IslandChatRules.COLOR_ROLES) {
            set(SLOTS.get(role), button(role, editable));
        }
    }

    private MenuButton button(Role role, boolean editable) {
        String current = chat.colorName(island, role);
        TagResolver[] p = {
                Msg.c("role", Component.text(module.raw("role." + MemberStore.key(role)), chat.color(island, role))),
                Msg.p("color", colorName(current))};
        List<Component> lore = new ArrayList<>(lines("chat.colors.role.lore", p));
        lore.add(Component.empty());
        lore.addAll(lines(editable && !chat.palette().isEmpty() ? "chat.colors.role.click" : "chat.colors.role.locked", p));
        Material wool = Material.matchMaterial(IslandChatRules.wool(current));
        ItemStack item = service.item(new ItemStack(wool == null ? Material.WHITE_WOOL : wool), text("chat.colors.role.name", p), lore);
        if (!editable) {
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> {
            if (pending) {
                return;
            }
            pick(role, current);
        });
    }

    /** One wool per palette color in its own color; one click sets it (G9: no forward-only cycling). */
    private void pick(Role role, String current) {
        List<PickListMenu.Option<String>> options = new ArrayList<>();
        for (String color : chat.palette()) {
            Material wool = Material.matchMaterial(IslandChatRules.wool(color));
            net.kyori.adventure.text.format.TextColor tint = IslandChatRules.parse(color);
            options.add(new PickListMenu.Option<>(color, new ItemStack(wool == null ? Material.WHITE_WOOL : wool),
                    Component.text(colorName(color), tint == null ? net.kyori.adventure.text.format.NamedTextColor.WHITE : tint)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    List.of(), color.equals(current)));
        }
        openChild(new PickListMenu<>(List.of(text("chat.colors.crumb"), Component.text(module.raw("role." + MemberStore.key(role)))),
                options, color -> {
                    pending = true;
                    chat.setColor(viewer, island, role, color, () -> {
                        pending = false;
                        refresh();
                    });
                }));
    }

    /** A palette color for people: "light purple", or the hex code. */
    private static String colorName(String color) {
        return color.replace('_', ' ');
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
