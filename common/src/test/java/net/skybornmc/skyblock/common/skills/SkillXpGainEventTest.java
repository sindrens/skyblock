package net.skybornmc.skyblock.common.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The base amount stays what the source asked for, whatever listeners do (island pillars count only it, RK20). */
class SkillXpGainEventTest {

    @Test
    void baseAmountIgnoresListenerChanges() {
        SkillXpGainEvent event = new SkillXpGainEvent(null, SkillType.MINING, 10, "depths_block");
        event.setAmount(25);
        assertEquals(10, event.getBaseAmount());
        assertEquals(25, event.getAmount());
        event.setAmount(Double.NaN);
        assertEquals(0, event.getAmount());
        assertEquals(10, event.getBaseAmount());
    }

    @Test
    void brokenBaseAmountsAreZero() {
        assertEquals(0, new SkillXpGainEvent(null, SkillType.COMBAT, -5, "x").getBaseAmount());
        assertEquals(0, new SkillXpGainEvent(null, SkillType.COMBAT, Double.NaN, "x").getBaseAmount());
    }
}
