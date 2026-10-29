package net.skybornmc.skyblock.common.placeholder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.hook.RankProvider;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderRegistryTest {

    private final PlaceholderRegistry registry = new PlaceholderRegistry(Logger.getLogger("test"));

    @Test
    void textAndComponent() {
        registry.registerText("eco", "balance", p -> "$1.2M");
        assertEquals("$1.2M", registry.text(null, "balance").orElseThrow());
        assertEquals("$1.2M", PlainTextComponentSerializer.plainText().serialize(registry.component(null, "balance").orElseThrow()));
        assertTrue(registry.text(null, "nope").isEmpty());
    }

    @Test
    void invalidAndDuplicateKeys() {
        assertThrows(IllegalArgumentException.class, () -> registry.registerText("x", "Bad-Key", p -> ""));
        registry.registerText("x", "a", p -> "");
        assertThrows(IllegalStateException.class, () -> registry.registerText("y", "a", p -> ""));
    }

    @Test
    void failingPlaceholderIsEmpty() {
        registry.registerText("x", "boom", p -> {
            throw new IllegalStateException("broken");
        });
        assertEquals("", registry.text(null, "boom").orElseThrow());
        assertEquals(Component.empty(), registry.component(null, "boom").orElseThrow());
    }

    @Test
    void miniMessageTag() {
        registry.registerText("x", "online", p -> "42");
        Component c = MiniMessage.miniMessage().deserialize("Online: <ph:online> <ph:typo>", registry.resolver(null));
        assertEquals("Online: 42 [?typo]", PlainTextComponentSerializer.plainText().serialize(c));
    }

    @Test
    void unregisterOwner() {
        registry.registerText("eco", "balance", p -> "1");
        registry.unregisterAll("eco");
        assertFalse(registry.has("balance"));
    }

    @Test
    void rankColorParsing() {
        assertEquals(NamedTextColor.RED, RankProvider.firstLegacyColor("&c&lADMIN &c"));
        assertEquals(NamedTextColor.GOLD, RankProvider.parseColor("gold"));
        assertEquals(NamedTextColor.GOLD, RankProvider.parseColor("&6"));
        assertEquals(0xFF8A1F, RankProvider.parseColor("#FF8A1F").value());
        assertEquals(0xFF8A1F, RankProvider.firstLegacyColor("&#FF8A1F&lVIP").value());
        assertEquals(null, RankProvider.parseColor("nonsense"));
    }

    @Test
    void legacyPrefixToComponent() {
        Component c = BuiltinPlaceholders.prefixComponent("&c&lADMIN ");
        assertEquals("ADMIN ", PlainTextComponentSerializer.plainText().serialize(c));
    }
}
