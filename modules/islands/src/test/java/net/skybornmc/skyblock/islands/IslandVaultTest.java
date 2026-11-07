package net.skybornmc.skyblock.islands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Batch 4: the island storage size. */
class IslandVaultTest {

    @Test
    void rowsComeFromTheUpgradeButNeverHideStoredItems() {
        assertEquals(18, IslandVault.slots(2, -1), "Island Vault I: 2 rows");
        assertEquals(54, IslandVault.slots(6, 10));
        assertEquals(54, IslandVault.slots(9, -1), "never more than 6 rows");
        assertEquals(36, IslandVault.slots(2, 30), "staff lowered the upgrade: the stored slot 30 still shows");
        assertEquals(9, IslandVault.slots(0, -1));
    }
}
