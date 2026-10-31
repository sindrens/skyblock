package net.skybornmc.skyblock.menus;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Navigation: {@code /menu} (hub + menu item in hotbar slot 9), {@code /help} (categories → topics → pages, command
 * lists from the help index) and {@code /settings} (per-player toggles, {@link PlayerSettings} service for other modules).
 */
public final class MenusModule implements SkyblockModule {

    static final String ID = "menus";
    static final String PERM_MENU = "skyblock.menu";
    static final String PERM_HELP = "skyblock.help";
    static final String PERM_SETTINGS = "skyblock.settings";
    static final String PERM_STAFF = "skyblock.staff.menu";
    /** Shared sound groups (common Sounds): owned here because every module's menus and events use them. */
    static final String SETTING_UI_SOUNDS = "ui-sounds";
    static final String SETTING_EVENT_SOUNDS = "event-sounds";

    private final Core core;
    private final MenusConfig config = new MenusConfig();
    private final SettingsRegistry settings;
    private final Nav nav;
    private MenuItem menuItem;

    public MenusModule(Core core) {
        this.core = core;
        this.settings = new SettingsRegistry(core);
        this.nav = new Nav(core, settings);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() {
        core.configs().load(config);
        loadFiles();
        core.services().register(ID, PlayerSettings.class, settings);
        registerBuiltinSettings();
        installSoundFilters();
        menuItem = new MenuItem(nav, config);
        Bukkit.getPluginManager().registerEvents(menuItem, core.plugin());
        settings.onChange(MenuItem.SETTING, (player, value) -> menuItem.apply(player));
        menuItem.applyAll();
        menuItem.start();
        registerCommands();
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(Nav.TEXTS);
        loadFiles();
        installSoundFilters();
        if (menuItem != null) {
            menuItem.applyAll();
            menuItem.start();
        }
    }

    @Override
    public void disable() {
        if (menuItem != null) {
            menuItem.stop();
            HandlerList.unregisterAll(menuItem);
            menuItem.removeAll(); // never leave a dead menu item behind if the module is off
        }
        core.sounds().setUiFilter(null, null);
        core.sounds().setEventFilter(null);
        settings.unregisterAll(ID);
    }

    /** F1: menu sounds and event sounds follow the players' /settings (checked on every play, so changes are instant). */
    private void installSoundFilters() {
        core.sounds().setUiFilter(Set.copyOf(config.uiSoundKeys), player -> settings.get(player, SETTING_UI_SOUNDS));
        core.sounds().setEventFilter(player -> settings.get(player, SETTING_EVENT_SOUNDS));
    }

    private void loadFiles() {
        YamlConfiguration hub = YamlFiles.load(core.plugin(), "menu.yml", this::problem);
        if (hub != null) {
            if (HubWarpsMigration.apply(hub)) {
                try {
                    hub.save(new java.io.File(core.plugin().getDataFolder(), "menu.yml"));
                    core.plugin().getLogger().info("menu.yml: the Warps button now opens the warps menu (UX3; player warps are inside it)");
                } catch (java.io.IOException e) {
                    problem("menu.yml: could not save the Warps button change: " + e.getMessage());
                }
            }
            nav.hub = HubLayout.parse(hub, this::problem);
            nav.staff = HubLayout.parse(hub, "staff-entries", this::problem);
            nav.warps = WarpLayout.parse(hub, this::problem);
        }
        YamlConfiguration help = YamlFiles.load(core.plugin(), "help.yml", this::problem);
        if (help != null) {
            nav.help = HelpBook.parse(help, this::problem);
        }
    }

    private void problem(String text) {
        core.plugin().getLogger().warning(text);
    }

    // ------------------------------------------------------------------ settings

    /**
     * The settings players expect in the menu. The menu item and the two shared sound groups (ui-sounds, event-sounds:
     * {@code Sounds#playUi}/{@code Sounds#playEvent}) are implemented here; the others are placeholders shown as
     * "Coming soon" until the module that owns the feature registers the same id (social: private-messages,
     * afk-broadcast; chat: mentions-sound, chat-colors, chat-timestamps; essentials: join-messages, tpa-requests; hud:
     * scoreboard; islands: island-invites; trade: trade-requests; wardrobe: particles). Still placeholders (no feature
     * yet): tips. The old "depths-visibility" placeholder became the core's {@code show-players} choice (VIS).
     */
    private void registerBuiltinSettings() {
        placeholder("private-messages", SettingCategory.CHAT, Material.WRITABLE_BOOK, true);
        placeholder("mentions-sound", SettingCategory.CHAT, Material.BELL, true);
        placeholder("join-messages", SettingCategory.CHAT, Material.OAK_DOOR, true);
        placeholder("chat-colors", SettingCategory.CHAT, Material.RED_DYE, true);
        placeholder("chat-timestamps", SettingCategory.CHAT, Material.CLOCK, false);
        placeholder("tpa-requests", SettingCategory.SOCIAL, Material.ENDER_PEARL, true);
        placeholder("trade-requests", SettingCategory.SOCIAL, Material.EMERALD, true);
        placeholder("island-invites", SettingCategory.SOCIAL, Material.GRASS_BLOCK, true);
        placeholder("afk-broadcast", SettingCategory.SOCIAL, Material.RED_BED, true);
        placeholder("scoreboard", SettingCategory.DISPLAY, Material.ITEM_FRAME, true);
        settings.register(new SettingToggle(ID, MenuItem.SETTING, SettingCategory.DISPLAY, Material.NETHER_STAR, true, null,
                Nav.TEXTS, "settings.toggles." + MenuItem.SETTING, false));
        placeholder("tips", SettingCategory.DISPLAY, Material.LANTERN, true);
        placeholder("particles", SettingCategory.DISPLAY, Material.BLAZE_POWDER, true);
        active(SETTING_UI_SOUNDS, SettingCategory.SOUNDS, Material.NOTE_BLOCK, true);
        active(SETTING_EVENT_SOUNDS, SettingCategory.SOUNDS, Material.GOAT_HORN, true);
    }

    private void placeholder(String id, SettingCategory category, Material icon, boolean defaultValue) {
        if (settings.toggle(id).filter(t -> !t.comingSoon()).isPresent()) {
            return; // a module already took it over (menus re-enabled at runtime): never put the placeholder back over it
        }
        settings.register(new SettingToggle(ID, id, category, icon, defaultValue, null, Nav.TEXTS, "settings.toggles." + id, true));
    }

    private void active(String id, SettingCategory category, Material icon, boolean defaultValue) {
        settings.register(new SettingToggle(ID, id, category, icon, defaultValue, null, Nav.TEXTS, "settings.toggles." + id, false));
    }

    // ------------------------------------------------------------------ commands

    private void registerCommands() {
        var commands = core.commands();

        commands.register(CommandSpec.command("menu")
                .description("Open the main menu with every feature").category("Info")
                .permission(PERM_MENU).playerOnly().aliases("gui")
                .executes(ctx -> core.menus().open(ctx.player(), new HubMenu(nav))));

        commands.register(CommandSpec.command("staff")
                .description("Staff hub: display editor, config editor and staff tools").category("Staff")
                .permission(PERM_STAFF).playerOnly().aliases("staffmenu")
                .executes(ctx -> core.menus().open(ctx.player(), new StaffMenu(nav))));

        commands.register(CommandSpec.command("admin")
                .description("In-game config editor (every module's settings, validated and logged)").category("Staff")
                .permission(net.skybornmc.skyblock.common.config.ConfigEdits.PERM_ADMIN).playerOnly().aliases("config")
                .executes(ctx -> core.menus().open(ctx.player(), new AdminMenu(nav))));

        Arg<String> topic = Args.word("topic", Suggest.bySender(sender -> {
            List<String> ids = new ArrayList<>(nav.help.ids());
            ids.add("commands");
            return ids;
        }));
        commands.register(CommandSpec.command("help")
                .description("Help menu: guides for every feature and all your commands").category("Info")
                .permission(PERM_HELP).aliases("guide")
                .optional(topic).example("help settings")
                .executes(ctx -> help(ctx.sender(), ctx.has(topic) ? ctx.get(topic) : null)));

        Arg<String> setting = Args.word("setting", Suggest.bySender(sender -> sender instanceof Player p
                ? SettingCategoryOrder.ids(settings, p) : List.of()));
        // VIS: multiple-choice settings take one of their choices (e.g. /settings show-players nobody); toggle = next choice.
        Arg<String> value = Args.oneOf("value", this::settingValues);
        commands.register(CommandSpec.command("settings")
                .description("Change your personal settings").category("Info")
                .permission(PERM_SETTINGS).playerOnly().aliases("options", "prefs")
                .optional(setting).optional(value).example("settings menu-item off")
                .executes(ctx -> settingsCommand(ctx.player(), ctx.has(setting) ? ctx.get(setting) : null,
                        ctx.has(value) ? ctx.get(value) : "toggle")));
    }

    private void help(CommandSender sender, String id) {
        Player player = sender instanceof Player p ? p : null;
        if (id == null) {
            if (player != null) {
                core.menus().open(player, new HelpMenu(nav));
            } else {
                HelpChat.categories(nav, sender);
            }
            return;
        }
        String key = id.toLowerCase(Locale.ROOT);
        if (key.equals("commands")) {
            if (player != null) {
                openUnderHelp(player, new HelpCommandsMenu(nav, List.of(), nav.text(player, "help.all-commands")));
            } else {
                HelpChat.commands(nav, sender, List.of());
            }
            return;
        }
        var topic = nav.help.topic(key);
        if (topic.isPresent()) {
            if (player != null) {
                openUnderHelp(player, new HelpTopicMenu(nav, topic.get()));
            } else {
                HelpChat.topic(nav, sender, topic.get());
            }
            return;
        }
        var category = nav.help.category(key);
        if (category.isPresent() && category.get().status() == HelpBook.Status.ACTIVE) {
            if (player == null) {
                HelpChat.category(nav, sender, category.get());
            } else if (category.get().allCommands()) {
                openUnderHelp(player, new HelpCommandsMenu(nav, List.of(), nav.text(player, "help.all-commands")));
            } else {
                openUnderHelp(player, new HelpCategoryMenu(nav, category.get()));
            }
            return;
        }
        if (category.isPresent()) {
            nav.msg.send(sender, "help.coming-soon", Msg.c("name", nav.crumb(sender, category.get().name())));
            return;
        }
        nav.msg.send(sender, "help.unknown", Msg.p("topic", id));
        if (player != null) {
            core.menus().open(player, new HelpMenu(nav));
        }
    }

    /**
     * Opens a help screen. From a menu (its "?" button) Back returns to that menu (GUI-1); typed in chat, Back leads to
     * the help categories.
     */
    private void openUnderHelp(Player player, Menu menu) {
        if (core.menus().openMenu(player) == null) {
            menu.parent(new HelpMenu(nav));
        }
        core.menus().open(player, menu);
    }

    private void settingsCommand(Player player, String id, String value) {
        if (id == null) {
            core.menus().open(player, new SettingsMenu(nav));
            return;
        }
        SettingToggle toggle = settings.toggle(id.toLowerCase(Locale.ROOT))
                .filter(t -> t.permission() == null || player.hasPermission(t.permission()))
                .orElseThrow(() -> new CommandFailure(Nav.TEXTS, "settings.unknown", Msg.p("setting", id)));
        if (toggle.comingSoon()) {
            throw new CommandFailure(Nav.TEXTS, "settings.not-yet", Msg.p("setting", toggle.id()));
        }
        if (toggle.isChoice()) {
            choiceCommand(player, toggle, value);
            return;
        }
        if (!List.of("on", "off", "toggle").contains(value)) {
            throw new CommandFailure(Nav.TEXTS, "settings.on-off", Msg.p("setting", toggle.id()));
        }
        boolean current = settings.get(player, toggle.id());
        boolean target = switch (value) {
            case "on" -> true;
            case "off" -> false;
            default -> !current;
        };
        if (!settings.set(player, toggle.id(), target)) {
            throw new CommandFailure(Nav.TEXTS, "settings.failed");
        }
        nav.msg.send(player, target ? "settings.changed-on" : "settings.changed-off",
                Msg.c("setting", core.menus().text(player, toggle.textModule(), toggle.textKey() + ".name")));
        core.sounds().play(player, "click");
    }

    private void choiceCommand(Player player, SettingToggle toggle, String value) {
        String target = value.equals("toggle") ? toggle.nextChoice(settings.choice(player, toggle.id())) : value;
        if (!toggle.choices().contains(target)) {
            throw new CommandFailure(Nav.TEXTS, "settings.choice-values", Msg.p("setting", toggle.id()),
                    Msg.p("choices", String.join(", ", toggle.choices())));
        }
        if (!settings.setChoice(player, toggle.id(), target)) {
            throw new CommandFailure(Nav.TEXTS, "settings.failed");
        }
        nav.msg.send(player, "settings.changed-choice",
                Msg.c("setting", core.menus().text(player, toggle.textModule(), toggle.textKey() + ".name")),
                Msg.c("choice", core.menus().text(player, toggle.textModule(), toggle.textKey() + ".choices." + target)));
        core.sounds().play(player, "click");
    }

    /** Every accepted {@code /settings} value: on, off, toggle and the choices of multiple-choice settings. */
    private List<String> settingValues() {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>(List.of("on", "off", "toggle"));
        for (SettingToggle t : settings.toggles()) {
            values.addAll(t.choices());
        }
        return List.copyOf(values);
    }

    /** Tab completion of setting ids: changeable (not coming soon) ones the player may see. */
    static final class SettingCategoryOrder {
        private SettingCategoryOrder() {
        }

        static List<String> ids(SettingsRegistry settings, Player player) {
            List<String> ids = new ArrayList<>();
            for (SettingCategory category : SettingCategory.values()) {
                settings.visible(player, category).stream().filter(t -> !t.comingSoon()).forEach(t -> ids.add(t.id()));
            }
            return ids;
        }
    }
}
