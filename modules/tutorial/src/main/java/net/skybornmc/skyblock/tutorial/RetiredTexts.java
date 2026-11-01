package net.skybornmc.skyblock.tutorial;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Old bundled texts of {@code messages/tutorial.yml} that TUT-FIX2 replaced (Messages#retire): a live text that still
 * equals its old text word for word is upgraded on start; an owner-edited text is kept. Titles move to the bold flame
 * yellow verb phrase, hints mention Pip and the trail, Pip's lines fit his 2-line name display.
 */
final class RetiredTexts {

    private RetiredTexts() {
    }

    static Map<String, String> all() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("start.chat", "\n  <grad:phoenix><bold>Welcome to Phoenix Skyblock!</bold></grad>\n  <c:text>Your island in the sky is waiting.\n\n  <c:ember><sym:arrow></c:ember> <c:text>Next: follow the flames to the <c:value>Island portal</c:value><c:text>.\n  <c:muted>Stuck? /tutorial</c:muted>\n");
        m.put("resume.title.title", "<c:brand>Welcome back");
        m.put("step-done.early", "<c:success><sym:check></c:success> <c:text><step> done early");
        m.put("steps.portal.hint", "<c:text>Follow the flames to the <c:value>Island portal");
        m.put("steps.portal.title.title", "<c:brand>Find your island");
        m.put("steps.portal.title.subtitle", "<c:text>Walk into the Island portal");
        m.put("steps.arrive.title.title", "<c:brand>Welcome home");
        m.put("steps.arrive.title.subtitle", "<c:text>This island is yours");
        m.put("steps.chest.hint", "<c:text>Open your starter chest");
        m.put("steps.crop.hint", "<c:text>Plant wheat: <c:value><count></c:value><c:muted> / <needed>");
        m.put("steps.menu.hint", "<c:text>Click the <c:value>Nether Star</c:value> in your hotbar");
        m.put("steps.menu.title.title", "<c:brand>The menu");
        m.put("steps.menu.title.subtitle", "<c:text>Click the Nether Star");
        m.put("steps.depths.title.title", "<c:brand>The Depths");
        m.put("steps.depths.title.subtitle", "<c:text>Mine, sell, forge, repeat");
        m.put("steps.mine.hint", "<c:text>Drops go to your satchel <c:muted>(<count>/<needed>)");
        m.put("steps.sell.hint", "<c:text>Press <c:value>Sell all</c:value> in your satchel");
        m.put("steps.sell.title.title", "<c:brand>Sell your loot");
        m.put("steps.sell.title.subtitle", "<c:text>/menu » The Depths » Satchel");
        m.put("steps.forge.hint", "<c:text>Better tools = more money");
        m.put("steps.help.objective", "Talk to the Guide");
        m.put("steps.help.hint", "<c:text>Click the Depths Guide once");
        m.put("steps.help.lore", "<c:text>Click the Depths Guide once.\n<c:text>Every NPC explains itself.");
        m.put("steps.bank.title.title", "<c:brand>Keep it safe");
        m.put("steps.bank.title.subtitle", "<c:text>Open /bank, deposit some money");
        m.put("idle.title.title", "<c:brand>Still there?");
        m.put("stuck.title.title", "<c:brand>Next step");
        m.put("cameras.spawn.one.subtitle", "<c:text>Welcome to the spawn");
        m.put("cameras.spawn.two.title", "<c:brand>The Gatehouse");
        m.put("cameras.spawn.two.subtitle", "<c:text>Every portal starts here");
        m.put("cameras.spawn.three.title", "<c:brand>Your Island");
        m.put("cameras.island.one.title", "<c:brand>Welcome home");
        m.put("cameras.island.one.subtitle", "<c:text>This island is yours");
        m.put("cameras.island.two.title", "<c:brand>Grow it");
        m.put("cameras.depths.one.title", "<c:brand>The Depths");
        m.put("cameras.depths.two.title", "<c:brand>Mine Tier 1");
        m.put("cameras.market.one.title", "<c:brand>Market Square");
        m.put("cameras.market.two.title", "<c:brand>Sell and trade");
        m.put("pip.lines.portal", "Your island is past the gate. Follow me!");
        m.put("pip.lines.arrive", "Look! This island is all yours.");
        m.put("pip.lines.chest", "Everything you need to start is in here.");
        m.put("pip.lines.crop", "Use your hoe on dirt, then plant.");
        m.put("pip.lines.menu", "The star in your hotbar opens everything.");
        m.put("pip.lines.depths", "Open the menu, then The Depths.");
        m.put("pip.lines.mine", "Break the ore. It grows back for you only.");
        m.put("pip.lines.sell", "Open your satchel and press Sell all.");
        m.put("pip.lines.forge", "The Forge turns loot into better tools.");
        m.put("pip.lines.help", "Click the Guide. Every NPC helps.");
        m.put("pip.lines.bank", "Keep your money safe in the bank.");
        m.put("pip.lines.finish", "You did it! Fly high, friend.");
        m.put("pip.lines.idle", "Still there? Your next step is waiting.");
        m.put("npc-lines.portal.guide", "Your island portal is north, through the Gatehouse.");
        return Map.copyOf(m);
    }
}
