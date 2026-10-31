package net.skybornmc.skyblock.staff;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** STAFF-R3: new report reasons (R3-31) with their config migration, and the F15 creative block log and inventory rule. */
class StaffR3Test {

    // ------------------------------------------------------------------ R3-31 report reasons

    @Test
    void newReasonsAreInTheDefaultBeforeOther() {
        List<String> reasons = new StaffConfig().reportReasons;
        assertTrue(reasons.containsAll(List.of("Scamming", "Threats", "Griefing")));
        assertEquals("Other", reasons.getLast(), "the last reason asks for free text");
        assertTrue(reasons.size() <= 15, "the reason picker has 15 slots");
        assertTrue(reasons.stream().allMatch(r -> r.length() <= 32), "reports.reason is VARCHAR(32)");
        assertEquals(reasons.size(), reasons.stream().distinct().count());
        assertTrue(reasons.containsAll(StaffConfig.REASONS_V1), "no old reason is lost");
    }

    @Test
    void liveFileWithTheOldListIsUpgradedOnceButAnEditedListIsKept() {
        StaffConfig config = new StaffConfig();
        assertEquals(2, config.version());
        ModuleConfig.ValueChange change = config.valueChanges().get(2).get(StaffConfig.settingPath("reportReasons"));
        assertEquals("reports" + ".reasons", StaffConfig.settingPath("reportReasons"));
        assertEquals(StaffConfig.REASONS_V2, change.to());
        assertTrue(change.matches(new java.util.ArrayList<>(StaffConfig.REASONS_V1)), "an unedited live list is upgraded");
        assertFalse(change.matches(List.of("Hacking", "Other")), "the owner's own list is kept");
        assertEquals(StaffConfig.REASONS_V2, config.reportReasons);
    }

    @Test
    void reasonParsingKnowsTheNewReasons() {
        String[] parsed = ReportRules.parse("griefing broke my farm", StaffConfig.REASONS_V2);
        assertEquals("Griefing", parsed[0]);
        assertEquals("broke my farm", parsed[1]);
        assertEquals("Scamming", ReportRules.parse("Scamming", StaffConfig.REASONS_V2)[0]);
        assertEquals("Threats", ReportRules.parse("threats", StaffConfig.REASONS_V2)[0]);
    }

    @Test
    void evidenceDefaultsMatchTheOwnersAnswer() {
        StaffConfig config = new StaffConfig();
        assertEquals(10, config.dmEvidenceCount);
        assertEquals(180, config.dmEvidenceKeepDays);
    }

    // ------------------------------------------------------------------ F15 creative block log

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000ad");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000f");

    private static CreativeBlockBuffer.Key key(UUID staff, String island, CreativeBlockBuffer.Action action) {
        return new CreativeBlockBuffer.Key(staff, "s", island, "islands", action);
    }

    @Test
    void blocksAreBatchedPerStaffIslandAndActionAndSplitAtTheRowLimit() {
        CreativeBlockBuffer buffer = new CreativeBlockBuffer();
        for (int i = 0; i < 250; i++) {
            buffer.add(key(ADMIN, "isl-1", CreativeBlockBuffer.Action.PLACE), new CreativeBlockBuffer.Block(i, 64, 0, "stone"));
        }
        buffer.add(key(ADMIN, "isl-1", CreativeBlockBuffer.Action.BREAK), new CreativeBlockBuffer.Block(1, 65, 2, "dirt"));
        buffer.add(key(ADMIN, "isl-2", CreativeBlockBuffer.Action.PLACE), new CreativeBlockBuffer.Block(5, 70, 5, "glass"));
        assertEquals(252, buffer.size());

        List<CreativeBlockBuffer.Batch> batches = buffer.drain(100);
        assertEquals(5, batches.size(), "250 places = 3 rows, plus one break row and one row for the other island");
        assertEquals(List.of(100, 100, 50, 1, 1), batches.stream().map(b -> b.blocks().size()).toList());
        assertEquals("0,64,0 stone", batches.getFirst().described().getFirst());
        assertEquals("1,65,2 dirt", batches.get(3).described().getFirst());
        assertEquals("island_creative_break", batches.get(3).key().action().audit);
        assertEquals("isl-2", batches.get(4).key().island());
        assertEquals(0, buffer.size());
        assertTrue(buffer.drain(100).isEmpty());
    }

    @Test
    void leavingStaffModeDrainsOnlyThatStaffMember() {
        CreativeBlockBuffer buffer = new CreativeBlockBuffer();
        buffer.add(key(ADMIN, "isl-1", CreativeBlockBuffer.Action.PLACE), new CreativeBlockBuffer.Block(0, 0, 0, "stone"));
        buffer.add(key(OWNER, "isl-1", CreativeBlockBuffer.Action.PLACE), new CreativeBlockBuffer.Block(1, 1, 1, "stone"));
        List<CreativeBlockBuffer.Batch> admin = buffer.drain(ADMIN, 100);
        assertEquals(1, admin.size());
        assertEquals(ADMIN, admin.getFirst().key().staff());
        assertEquals(1, buffer.size(), "the owner's block waits for the next write");
        assertEquals(OWNER, buffer.drain(0).getFirst().key().staff(), "a row limit below 1 still writes");
    }

    @Test
    void creativeInventoryWorksInStaffModeOnlyForPermittedStaffInCreative() {
        assertTrue(StaffMode.islandCreativeClick(true, true, true));
        assertFalse(StaffMode.islandCreativeClick(false, true, true), "normal inventory clicks stay locked in staff mode");
        assertFalse(StaffMode.islandCreativeClick(true, false, true), "not in creative");
        assertFalse(StaffMode.islandCreativeClick(true, true, false), "mods have no island creative");
    }
}
