package net.skybornmc.skyblock.cosmetics;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.config.ConfigEdits;
import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.SettingInfo;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Staff → Display (M1.11): how players look in chat, tab and above heads. Each part of the format is edited as text
 * (order, separators, colors), previewed live on your own name before saving, then saved to chat.yml, applied
 * instantly and logged. Formats are the chat module's settings, so the chat module itself needs no changes.
 */
final class DisplayMenu extends Menu {

    /** chat.yml paths shown here, with their icon and slot. */
    record Field(String path, Material icon, int slot) {
    }

    static final List<Field> FIELDS = List.of(
            new Field("format.chat", Material.OAK_SIGN, 20),
            new Field("format.title-part", Material.NAME_TAG, 21),
            new Field("format.tag-part", Material.ARMOR_STAND, 22),
            new Field("format.tab", Material.ITEM_FRAME, 29),
            new Field("format.tab-title-part", Material.NAME_TAG, 30),
            new Field("format.tab-tag-part", Material.ARMOR_STAND, 31),
            new Field("format.afk-part", Material.RED_BED, 32),
            new Field("format.nametag", Material.PLAYER_HEAD, 24));

    static final int SLOT_PREVIEW = 4;
    static final int SLOT_PARTS = 33;

    private final CosmeticsModule module;

    DisplayMenu(CosmeticsModule module) {
        this.module = module;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, CosmeticsModule.ID, "display.crumb-staff", "display.crumb");
    }

    @Override
    public String messageModule() {
        return CosmeticsModule.ID;
    }

    @Override
    public String infoKey() {
        return "display.info";
    }

    @Override
    public String helpTopic() {
        return "display";
    }

    @Override
    protected void build() {
        frame();
        Optional<ModuleConfig> chat = module.preview().chatConfig();
        if (chat.isEmpty()) {
            set(22, service.item(Material.BARRIER, module.text(viewer, "display.no-chat.name"), module.lines(viewer, "display.no-chat.lore")));
            return;
        }
        set(SLOT_PREVIEW, preview(viewer, Map.of()));
        set(SLOT_PARTS, service.item(Material.KNOWLEDGE_BOOK, module.text(viewer, "display.parts.name"), module.lines(viewer, "display.parts.lore")));
        for (Field field : FIELDS) {
            Optional<SettingInfo> info = ConfigEdits.info(module.core(), chat.get(), field.path());
            if (info.isEmpty()) {
                continue;
            }
            SettingInfo i = info.get();
            if (i.type() == boolean.class || i.type() == Boolean.class) {
                boolean state = Boolean.TRUE.equals(i.value());
                set(field.slot(), service.toggle(viewer, CosmeticsModule.ID, "display.fields." + key(field), state,
                        value -> apply(chat.get(), field.path(), value)));
                continue;
            }
            String raw = String.valueOf(i.value());
            List<Component> lore = new ArrayList<>(module.lines(viewer, "display.fields." + key(field) + ".lore"));
            lore.add(Component.empty());
            lore.add(module.text(viewer, "display.current", Msg.p("raw", raw.isEmpty() ? "(empty)" : raw)));
            lore.add(module.text(viewer, "display.default", Msg.p("raw", String.valueOf(i.defaultValue()))));
            lore.addAll(module.lines(viewer, "display.action-edit"));
            set(field.slot(), service.item(field.icon(), module.text(viewer, "display.fields." + key(field) + ".name"), lore),
                    click -> edit(chat.get(), field, raw, String.valueOf(i.defaultValue())));
        }
    }

    private static String key(Field field) {
        return field.path().substring("format.".length());
    }

    /** The head with chat + tab preview (optionally with a not-yet-saved value). */
    private org.bukkit.inventory.ItemStack preview(Player player, Map<String, String> overrides) {
        org.bukkit.inventory.ItemStack head = service.item(Material.PLAYER_HEAD, module.text(player, "display.preview.name"), List.of(
                module.text(player, "menu.preview.chat-label"),
                module.preview().chatLine(player, module.text(player, "menu.preview.message"), overrides),
                Component.empty(),
                module.text(player, "menu.preview.tab-label"),
                module.preview().tabLine(player, overrides),
                Component.empty(),
                module.text(player, "display.preview.afk-label"),
                Component.empty().append(Component.text(player.getName())).append(module.preview().afk(player, overrides))));
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(player));
        return head;
    }

    private void edit(ModuleConfig chat, Field field, String current, String defaultValue) {
        service.prompt(viewer, CosmeticsModule.ID, "display.prompt", 300, input -> {
            String value = input.strip().equalsIgnoreCase("default") ? defaultValue
                    : input.strip().equalsIgnoreCase("empty") ? "" : input;
            Map<String, String> override = Map.of(field.path(), value);
            openChild(new ConfirmMenu(p -> module.text(p, "display.confirm-crumb"), preview(viewer, override),
                    p -> module.lines(p, "display.confirm-details", Msg.p("old", current.isEmpty() ? "(empty)" : current),
                            Msg.p("new", value.isEmpty() ? "(empty)" : value)),
                    p -> {
                        apply(chat, field.path(), value);
                        service.open(p, this);
                    },
                    p -> { }));
        }, () -> service.open(viewer, this), Msg.p("field", field.path()));
    }

    private void apply(ModuleConfig chat, String path, Object value) {
        ConfigEdits.Result result = ConfigEdits.apply(module.core(), viewer, chat, path, value);
        if (result.ok()) {
            module.msg().send(viewer, "display.saved", Msg.p("field", path));
            module.core().sounds().play(viewer, "success");
        } else {
            module.msg().send(viewer, "display.failed", Msg.p("reason", result.error()));
            module.core().sounds().play(viewer, "error");
        }
    }
}
