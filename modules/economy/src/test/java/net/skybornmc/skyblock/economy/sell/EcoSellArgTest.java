package net.skybornmc.skyblock.economy.sell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A6: /eco sell item arguments: custom items are typed as custom.<id> (Brigadier words can't hold ':'). */
class EcoSellArgTest {

    @Test
    void customKeysRoundTrip() {
        assertEquals("custom.depths_coal", SellFeature.typedKey("custom:depths_coal"));
        assertEquals("custom:depths_coal", SellFeature.keyFromTyped("custom.depths_coal"));
        assertEquals("wheat", SellFeature.typedKey("wheat"));
        assertEquals("wheat", SellFeature.keyFromTyped("Wheat"));
        assertEquals("wheat", SellFeature.keyFromTyped("minecraft:wheat"));
        assertEquals("all", SellFeature.keyFromTyped("ALL"));
    }
}
