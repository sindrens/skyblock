package net.skybornmc.skyblock.common.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** R3-32 / answer 305: private messages use only the link, IP and phone checks of the chat filter. */
class TextFilterContactTest {

    @Test
    void contactRefusalsStayEverythingElsePasses() {
        for (String reason : new String[]{"link", "ip", "phone"}) {
            TextFilter.Result r = TextFilter.contactOnly(new TextFilter.Result("join x", reason), "join x");
            assertEquals(reason, r.blocked());
            assertEquals("join x", r.text());
        }
        TextFilter.Result word = TextFilter.contactOnly(new TextFilter.Result("bad word", "word"), "bad word");
        assertFalse(word.isBlocked(), "the swear list is not used for DMs");
        TextFilter.Result changed = TextFilter.contactOnly(new TextFilter.Result("**** you", null), "damn you");
        assertEquals("damn you", changed.text(), "no word replacement or caps lowering in DMs");
        assertNull(TextFilter.contactOnly(null, "hi").blocked());
    }

    @Test
    void checkContactUsesTheService() {
        TextFilter chat = (author, text) -> text.contains("www.") ? new TextFilter.Result(text, "link")
                : text.contains("idiot") ? new TextFilter.Result(text, "word") : new TextFilter.Result(text.toLowerCase(), null);
        assertEquals("link", chat.checkContact(null, "go to www.x.com").blocked());
        assertFalse(chat.checkContact(null, "you idiot").isBlocked());
        assertEquals("HELLO", chat.checkContact(null, "HELLO").text());
    }
}
