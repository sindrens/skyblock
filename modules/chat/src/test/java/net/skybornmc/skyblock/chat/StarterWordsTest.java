package net.skybornmc.skyblock.chat;

import net.skybornmc.skyblock.common.config.ConfigManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner answer 301: the English starter word lists (replace mild swearing, block slurs). */
class StarterWordsTest {

    private final ChatFilter.Rules rules = new ChatConfig().rules();

    @Test
    void mildSwearingIsStarredOut() {
        ChatFilter.Result result = ChatFilter.check("what the fuck is this", rules, false);
        assertNull(result.blocked());
        assertEquals("what the **** is this", result.text());
    }

    @Test
    void slursBlockTheMessageEvenWithLeetspeak() {
        assertEquals("word", ChatFilter.check("you are a r3tard", rules, false).blocked());
        assertEquals("word", ChatFilter.check("just kys", rules, false).blocked());
    }

    @Test
    void normalWordsPass() {
        for (String fine : new String[]{"class assignment", "scunthorpe", "I like cocoons", "spicy food", "grass"}) {
            ChatFilter.Result result = ChatFilter.check(fine, rules, false);
            assertNull(result.blocked(), fine);
            assertEquals(fine, result.text(), fine);
        }
    }

    @Test
    void emptyListsOfOldFilesGetTheStarterLists(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("chat.yml"), "config-version: 1\nprotection:\n  words:\n    replace: []\n    block: [badword]\n");
        ChatConfig config = new ChatConfig();
        assertTrue(new ConfigManager(dir.toFile(), Logger.getLogger("test")).load(config).isEmpty());
        assertEquals(ChatConfig.STARTER_REPLACE, config.replaceWords, "an untouched empty list gets the starter list");
        assertEquals(java.util.List.of("badword"), config.blockWords, "an owner-made list is kept");
    }

    @Test
    void signsAndBooksUseTheFilterButKeepTheirCase() {
        java.util.List<String> lines = java.util.Arrays.asList("SHOP HERE", "what the fuck", "", null);
        java.util.List<String> filtered = TextSurfaces.filterLines(lines, rules, false);
        assertEquals("SHOP HERE", filtered.get(0), "shouting is fine on a sign");
        assertEquals("what the ****", filtered.get(1));
        assertEquals("", filtered.get(2));
        assertNull(filtered.get(3));
        assertNull(TextSurfaces.filterLines(java.util.List.of("hi", "you retard"), rules, false), "a blocked line refuses all");
        assertNull(TextSurfaces.filterLines(java.util.List.of("join myserver.com"), rules, false), "links too");
        assertEquals("BIG **** sign", TextSurfaces.starOnly("BIG fuck sign", "big **** sign"));
    }

    @Test
    void surfacesDropPrivateUseGlyphs() {
        assertEquals(java.util.List.of("SHOP", "ok"), TextSurfaces.filterLines(java.util.List.of("S\uE301HOP", "o\uF801k"), rules, false));
        assertEquals(java.util.Arrays.asList("a", null, ""), TextSurfaces.stripLines(java.util.Arrays.asList("\uE000a", null, "\uE001")));
    }
}
