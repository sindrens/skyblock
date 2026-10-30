package net.skybornmc.skyblock.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatLogicTest {

    private static final ChatFilter.Rules RULES = new ChatFilter.Rules(6, 0.7, true, List.of("skybornmc.net"), true, true,
            List.of("noob"), List.of("badword"));

    @Test
    void colorTiers() {
        String text = "&c&lHi &#FF3D2E there &kx";
        assertEquals("Hi  there x", ChatColors.filter(text, false, false, false));
        assertEquals("&cHi  there x", ChatColors.filter(text, true, false, false));
        assertEquals("&c&lHi  there x", ChatColors.filter(text, true, true, false));
        assertEquals("&c&lHi &#FF3D2E there x", ChatColors.filter(text, true, true, true));
        assertEquals("&cHi", ChatColors.filter("§cHi", true, false, false));
    }

    @Test
    void links() {
        assertEquals("link", ChatFilter.check("join coolserver.net now", RULES, false).blocked());
        assertEquals("link", ChatFilter.check("https://evil.gg/x", RULES, false).blocked());
        assertNull(ChatFilter.check("see play.skybornmc.net", RULES, false).blocked());
        assertNull(ChatFilter.check("join coolserver.net now", RULES, true).blocked(), "bypass");
        assertNull(ChatFilter.check("i have 2.5k coins. ok", RULES, false).blocked());
    }

    @Test
    void ipsAndPhones() {
        assertEquals("ip", ChatFilter.check("join 192.168.1.20:25565", RULES, false).blocked());
        assertEquals("phone", ChatFilter.check("call me 912 40 970", RULES, false).blocked());
        assertEquals("phone", ChatFilter.check("+4791240970", RULES, false).blocked());
        assertNull(ChatFilter.check("i sold for 1000000000", RULES, false).blocked());
        assertNull(ChatFilter.check("worth 10 000 000 now", RULES, false).blocked());
    }

    @Test
    void wordsAndCaps() {
        assertEquals("word", ChatFilter.check("you b4dw0rd", RULES, false).blocked());
        assertEquals("word", ChatFilter.check("b.a.d.w.o.r.d", RULES, false).blocked());
        ChatFilter.Result r = ChatFilter.check("you noob", RULES, false);
        assertEquals("you ****", r.text());
        assertTrue(r.changes().contains("word"));
        ChatFilter.Result caps = ChatFilter.check("HELLO EVERYONE", RULES, false);
        assertEquals("hello everyone", caps.text());
        assertEquals("OK GG", ChatFilter.check("OK GG", RULES, false).text(), "short shouting is fine");
    }

    @Test
    void duplicateKeyAndMentions() {
        assertEquals(ChatFilter.duplicateKey("Hello!!"), ChatFilter.duplicateKey("hello"));
        assertTrue(ChatModule.mentions("hey Steve!", "Steve"));
        assertTrue(ChatModule.mentions("@steve look", "Steve"));
        assertFalse(ChatModule.mentions("Steven is here", "Steve"));
    }

    @Test
    void stripColorsKeepsTextAndHover() {
        var hover = net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("tip"));
        var colored = net.kyori.adventure.text.Component.text("Hi ", net.kyori.adventure.text.format.NamedTextColor.RED)
                .append(net.kyori.adventure.text.Component.text("item").hoverEvent(hover)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        var plain = ChatModule.stripColors(colored);
        assertNull(plain.color());
        assertNull(plain.children().getFirst().color());
        assertEquals(hover, plain.children().getFirst().hoverEvent());
        assertEquals("Hi item", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(plain));
    }

    @Test
    void teamNamesSortByWeightThenName() {
        String owner = TabService.teamName(100, "Zed");
        String mod = TabService.teamName(20, "Alex");
        String member = TabService.teamName(0, "Bob");
        assertTrue(owner.compareTo(mod) < 0);
        assertTrue(mod.compareTo(member) < 0);
        assertTrue(TabService.teamName(0, "Amy").compareTo(member) < 0);
        assertTrue(owner.startsWith(TabService.TEAM_PREFIX));
    }

    @Test
    void titleBeforeTagAfterTheName() {
        ChatConfig c = new ChatConfig();
        assertTrue(c.chatFormat.indexOf("<title>") < c.chatFormat.indexOf("<name>"));
        assertTrue(c.chatFormat.indexOf("<tag>") > c.chatFormat.indexOf("<name>"));
        assertTrue(c.chatFormat.indexOf("<tag>") < c.chatFormat.indexOf("»"));
        assertTrue(c.tabFormat.indexOf("<title>") < c.tabFormat.indexOf("<rank>"));
        assertTrue(c.tabFormat.indexOf("<tag>") > c.tabFormat.indexOf("<name>"));
        assertEquals(" <value>", c.tagPart);
        assertEquals("<value> ", c.tabTitlePart);
    }

    @Test
    void oldDefaultFormatsAreUpgradedButEditsAreKept() {
        java.util.Map<String, String> old = new java.util.HashMap<>();
        ChatConfig.CHANGED_DEFAULTS.forEach((path, values) -> old.put(path, values[0]));
        java.util.Map<String, String> up = ChatConfig.upgrades(old);
        assertEquals(ChatConfig.CHANGED_DEFAULTS.size(), up.size());
        assertEquals("<title><rank><name><tag><afk>", up.get("format.tab"));

        old.put("format.chat", "<rank><name>: <message>"); // the owner's own format
        assertFalse(ChatConfig.upgrades(old).containsKey("format.chat"));
        assertTrue(ChatConfig.upgrades(new ChatConfig().changedDefaultValues()).isEmpty(), "new defaults are final");
    }

    @Test
    void tpsTiers() {
        assertEquals("good", TabService.tpsTier(20.0, 19.0, 15.0));
        assertEquals("good", TabService.tpsTier(19.0, 19.0, 15.0));
        assertEquals("ok", TabService.tpsTier(18.9, 19.0, 15.0));
        assertEquals("ok", TabService.tpsTier(15.0, 19.0, 15.0));
        assertEquals("bad", TabService.tpsTier(14.9, 19.0, 15.0));
    }
}
