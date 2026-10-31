package net.skybornmc.skyblock.menus;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI-2: the clean /menu hub (GUI_DESIGN §7.1, owner G1/G2/G7) and the You menu (§7.2) from menu.yml. */
class HubButtonsTest {

    private static YamlConfiguration resource(String name) throws Exception {
        try (var in = HubButtonsTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    private static final Predicate<String> ALL = s -> true;
    /** Item ids without a server (the item registry needs one): lower-case words only. */
    static final Predicate<String> ITEMS = id -> id.matches("[a-z_]+");

    @Test
    void bundledHubHas15ButtonsIn4Groups() throws Exception {
        List<String> problems = new ArrayList<>();
        ButtonLayout hub = ButtonLayout.hub(resource("menu.yml"), ITEMS, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(15, hub.buttons().size(), "your head + 14 features");
        assertEquals(List.of("play", "money", "power", "more"), List.copyOf(hub.groups().keySet()));

        Map<String, List<Integer>> slots = hub.buttons().stream().filter(b -> b.group() != null)
                .collect(Collectors.groupingBy(ButtonLayout.Button::group, Collectors.mapping(ButtonLayout.Button::slot, Collectors.toList())));
        assertEquals(List.of(10, 12, 14, 16), slots.get("play"));
        assertEquals(List.of(19, 21, 23, 25), slots.get("money"));
        assertEquals(List.of(28, 30, 32, 34), slots.get("power"));
        assertEquals(List.of(39, 41), slots.get("more"));

        ButtonLayout.Button head = hub.buttons().stream().filter(b -> b.slot() == HubMenu.SLOT_PROFILE).findFirst().orElseThrow();
        assertEquals(ButtonLayout.YOU, head.action(), "your head opens the You menu");
        assertTrue(hub.buttons().stream().anyMatch(b -> b.slot() == 16 && ButtonLayout.TRAVEL.equals(b.action())));
        ButtonLayout.Button rewards = hub.buttons().stream().filter(b -> ButtonLayout.REWARDS.equals(b.action())).findFirst().orElseThrow();
        assertEquals(39, rewards.slot());
        assertEquals("daily", rewards.command(), "nothing ready: opens /daily");
        assertEquals("RewardsService", rewards.service());
    }

    @Test
    void noDuplicateActionsAndHelpOnlyOnSlot50() throws Exception {
        YamlConfiguration menu = resource("menu.yml");
        ButtonLayout hub = ButtonLayout.hub(menu, ITEMS, p -> { });
        ButtonLayout you = ButtonLayout.you(menu, ITEMS, p -> { });
        Set<String> actions = new HashSet<>();
        for (ButtonLayout.Button b : hub.buttons()) {
            assertTrue(actions.add(b.actionKey()), "duplicate in the hub: " + b.id());
            assertFalse(ButtonLayout.HELP.equals(b.action()) || "help".equals(b.label()), "Help & guides is the framework's slot 50");
            assertFalse(ButtonLayout.SETTINGS.equals(b.action()), "settings moved to the You menu");
        }
        Set<String> youActions = new HashSet<>();
        for (ButtonLayout.Button b : you.buttons()) {
            assertTrue(youActions.add(b.actionKey()), "duplicate in You: " + b.id());
            assertFalse(actions.contains(b.actionKey()), b.id() + " is in the hub and in You");
        }
        // The /top command is the staff teleport, never the leaderboard button.
        assertFalse(hub.buttons().stream().anyMatch(b -> "top".equals(b.command())));
    }

    @Test
    void youMenuHasProfilesTitlesAchievementsSettingsStoreRulesCollection() throws Exception {
        List<String> problems = new ArrayList<>();
        ButtonLayout you = ButtonLayout.you(resource("menu.yml"), ITEMS, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(List.of("profiles", "titles", "achievements", "settings", "store", "rules", "collection"),
                you.buttons().stream().map(ButtonLayout.Button::id).toList());
        assertEquals(List.of(10, 11, 12, 13, 14, 15, 16), you.buttons().stream().map(ButtonLayout.Button::slot).toList());
    }

    @Test
    void everyButtonHasTexts() throws Exception {
        YamlConfiguration menu = resource("menu.yml");
        YamlConfiguration texts = resource("messages/navigation.yml");
        for (ButtonLayout.Button b : ButtonLayout.hub(menu, ITEMS, p -> { }).buttons()) {
            if (!ButtonLayout.YOU.equals(b.action())) {
                assertTrue(texts.isString("hub.buttons." + b.id() + ".name"), b.id());
                assertTrue(texts.isList("hub.buttons." + b.id() + ".lore"), b.id());
            }
        }
        for (String group : ButtonLayout.hub(menu, ITEMS, p -> { }).groups().keySet()) {
            assertTrue(texts.isString("hub.groups." + group + ".name"), group);
        }
        for (ButtonLayout.Button b : ButtonLayout.you(menu, ITEMS, p -> { }).buttons()) {
            assertTrue(texts.isString("you.buttons." + b.id() + ".name"), b.id());
        }
        for (String key : new String[]{"hub.head.name", "hub.head.lore", "you.head.lore", "hub.rewards.ready-name", "hub.rewards.ready",
                "hub.rewards.none", "hub.rewards.nothing", "hub.rewards.sources.daily", "hub.fallback-name", "you.title", "you.info"}) {
            assertTrue(texts.contains(key), key);
        }
    }

    @Test
    void unbuiltFeaturesAreHiddenOnTheLiveServer() throws Exception {
        ButtonLayout hub = ButtonLayout.hub(resource("menu.yml"), ITEMS, p -> { });
        // A server without the wardrobe and rewards modules, and without the /enchanter command.
        Predicate<String> services = s -> !s.equals("WardrobeService") && !s.equals("RewardsService");
        Predicate<String> commands = c -> !c.equals("enchanter");
        Map<String, ButtonLayout.State> live = hub.buttons().stream()
                .collect(Collectors.toMap(ButtonLayout.Button::id, b -> b.state(services, commands, false)));
        assertEquals(ButtonLayout.State.HIDDEN, live.get("wardrobe"));
        assertEquals(ButtonLayout.State.HIDDEN, live.get("rewards"));
        assertEquals(ButtonLayout.State.HIDDEN, live.get("enchanter"));
        assertEquals(ButtonLayout.State.ACTIVE, live.get("sell"));
        assertEquals(ButtonLayout.State.ACTIVE, live.get("you"), "built-in screens need nothing");
        assertEquals(ButtonLayout.State.ACTIVE, live.get("travel"));
        // The test server switch shows them as Coming soon.
        assertEquals(ButtonLayout.State.COMING_SOON, hub.buttons().stream().filter(b -> b.id().equals("wardrobe")).findFirst()
                .orElseThrow().state(services, commands, true));
        // Everything built: all 15 show.
        assertEquals(15, hub.buttons().stream().filter(b -> b.state(ALL, ALL, false) == ButtonLayout.State.ACTIVE).count());
    }

    @Test
    void stateRules() {
        assertEquals(ButtonLayout.State.ACTIVE, ButtonLayout.state(true, true, false));
        assertEquals(ButtonLayout.State.HIDDEN, ButtonLayout.state(true, false, false));
        assertEquals(ButtonLayout.State.COMING_SOON, ButtonLayout.state(true, false, true));
        assertEquals(ButtonLayout.State.HIDDEN, ButtonLayout.state(false, true, true), "show: false hides everywhere");
    }

    @Test
    void badButtonsAreSkippedWithAReason() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("hub-groups.play.mark", "lime_stained_glass_pane");
        yaml.set("hub.a.slot", 10);
        yaml.set("hub.a.command", "is");
        yaml.set("hub.b.slot", 12);
        yaml.set("hub.b.command", "/is"); // same action as a
        yaml.set("hub.c.slot", 10);       // slot used
        yaml.set("hub.c.command", "shop");
        yaml.set("hub.d.slot", 0);        // frame
        yaml.set("hub.d.command", "bank");
        yaml.set("hub.e.slot", 14);
        yaml.set("hub.e.action", "teleport"); // unknown action
        yaml.set("hub.f.slot", 16);
        yaml.set("hub.f.command", "sell");
        yaml.set("hub.f.group", "nope");  // kept, without group
        yaml.set("hub.g.slot", 19);       // no command
        List<String> problems = new ArrayList<>();
        ButtonLayout hub = ButtonLayout.hub(yaml, ITEMS, problems::add);
        assertEquals(List.of("a", "f"), hub.buttons().stream().map(ButtonLayout.Button::id).toList());
        assertEquals(null, hub.buttons().get(1).group());
        assertEquals(6, problems.size(), String.join("\n", problems));
    }

    @Test
    void groupMarksSitOnTheLeftEdgeOfTheRow() {
        ButtonLayout.Button a = new ButtonLayout.Button("a", 21, "money", "stone", "command", "x", null, true, null);
        ButtonLayout.Button b = new ButtonLayout.Button("b", 25, "money", "stone", "command", "y", null, true, null);
        assertEquals(18, ButtonLayout.markSlot(List.of(b, a)));
        assertEquals(-1, ButtonLayout.markSlot(List.of()), "all hidden: no mark");
    }
}
