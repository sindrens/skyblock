package net.skybornmc.skyblock.common.profile;

import net.skybornmc.skyblock.common.economy.Currency;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 4 Nov 2026 ("island = profile"): what a profile wipe keeps and what it takes. */
class WipeScopeTest {

    @Test
    void accountThingsAreKept() {
        for (WipeScope.Part part : List.of(WipeScope.Part.CREDITS, WipeScope.Part.RANKS, WipeScope.Part.PERMISSIONS,
                WipeScope.Part.STORE_PURCHASES, WipeScope.Part.ACHIEVEMENTS, WipeScope.Part.COSMETICS, WipeScope.Part.TAGS_TITLES,
                WipeScope.Part.ACCOUNT_PLAYTIME)) {
            assertTrue(WipeScope.kept(part), part + " is kept");
        }
    }

    @Test
    void everythingOfTheIslandRunIsWiped() {
        for (WipeScope.Part part : List.of(WipeScope.Part.MONEY, WipeScope.Part.SHARDS, WipeScope.Part.CRYSTALS,
                WipeScope.Part.PERSONAL_BANK, WipeScope.Part.INVENTORY, WipeScope.Part.ARMOR, WipeScope.Part.OFFHAND,
                WipeScope.Part.ENDER_CHEST, WipeScope.Part.XP, WipeScope.Part.ISLAND_TOOLS, WipeScope.Part.SKILLS,
                WipeScope.Part.QUESTS, WipeScope.Part.TASKS, WipeScope.Part.COLLECTION, WipeScope.Part.MILESTONES,
                WipeScope.Part.STORAGES, WipeScope.Part.ISLAND_HOMES, WipeScope.Part.ISLAND_MEMBERSHIP)) {
            assertFalse(WipeScope.kept(part), part + " is wiped");
        }
        assertFalse(WipeScope.kept(null));
    }

    @Test
    void keptAndWipedListsSplitEveryPart() {
        assertEquals(WipeScope.Part.values().length, WipeScope.keptParts().size() + WipeScope.wipedParts().size());
        assertTrue(WipeScope.keptParts().stream().noneMatch(WipeScope.wipedParts()::contains));
    }

    @Test
    void onlyCreditsSurviveOfTheCurrencies() {
        assertTrue(WipeScope.wipes(Currency.MONEY));
        assertTrue(WipeScope.wipes(Currency.SHARDS));
        assertTrue(WipeScope.wipes(Currency.CRYSTALS));
        assertFalse(WipeScope.wipes(Currency.CREDITS));
        assertFalse(WipeScope.wipes(Currency.CREDITS_EARNED));
        assertFalse(WipeScope.wipes(null));
    }

    @Test
    void reasonsRoundTripTheirKeys() {
        for (WipeReason reason : WipeReason.values()) {
            assertEquals(reason, WipeReason.byKey(reason.key()).orElseThrow());
        }
        assertEquals("island_deleted", WipeReason.ISLAND_DELETED.key());
        assertTrue(WipeReason.byKey("nope").isEmpty());
        assertTrue(WipeReason.byKey(null).isEmpty());
    }

    @Test
    void identityProfilesCannotWipe() {
        assertFalse(ProfileService.IDENTITY.supportsWipe());
        ProfileWipeResult result = ProfileService.IDENTITY.wipe(java.util.UUID.randomUUID(), WipeReason.LEAVE, null, null, null).join();
        assertEquals(ProfileWipeResult.Status.UNAVAILABLE, result.status());
        assertFalse(result.done());
    }
}
