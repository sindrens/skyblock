package net.skybornmc.skyblock.common.profile;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileNamesTest {

    @Test
    void namesAreCleanedAndChecked() {
        assertEquals("My farm", ProfileNames.clean("  My    farm "));
        assertEquals(Optional.empty(), ProfileNames.check("Iron_Grinder-2", 16));
        assertEquals(Optional.of(ProfileNames.Problem.EMPTY), ProfileNames.check("   ", 16));
        assertEquals(Optional.of(ProfileNames.Problem.TOO_LONG), ProfileNames.check("abcdefghijklmnopq", 16));
        assertEquals(Optional.of(ProfileNames.Problem.CHARACTERS), ProfileNames.check("<red>x", 16));
        assertEquals(Optional.of(ProfileNames.Problem.CHARACTERS), ProfileNames.check("&cFarm", 16));
    }

    @Test
    void identityKeysAndGuards() {
        java.util.UUID a = java.util.UUID.randomUUID();
        java.util.UUID b = java.util.UUID.randomUUID();
        assertEquals(a, ProfileService.IDENTITY.profileKey(a));
        assertEquals(true, ProfileService.IDENTITY.sameOwner(a, a));
        assertEquals(false, ProfileService.IDENTITY.ownOtherProfile(a, a));
        assertEquals(false, ProfileService.IDENTITY.sameOwner(a, b));
    }
}
