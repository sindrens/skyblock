package net.skybornmc.skyblock.staff;

/** Permission nodes of the staff module. */
final class StaffPerms {

    static final String MODE = "skyblock.staff.mode";
    static final String FREEZE = "skyblock.staff.freeze";
    static final String FREEZE_EXEMPT = "skyblock.staff.freeze.exempt";
    static final String INSPECT = "skyblock.staff.inspect";
    static final String REPORT = "skyblock.player.report";
    static final String REPORT_EXEMPT = "skyblock.report.bypass-cooldown";
    static final String REPORTS = "skyblock.staff.reports";
    static final String NOTIFY = "skyblock.staff.notify";
    /**
     * R3-9a: may read the DMs attached to a report. The same node as reading DMs in {@code /logs} (owned by the logs
     * module; moderators and up, R3-9b), so who may see DMs is decided in one place.
     */
    static final String DM_EVIDENCE = "skyblock.staff.logs.category.dm";
    /** F15: in staff mode, island bypass turns on by itself and creative works on islands (owner and admin). */
    static final String ISLAND_CREATIVE = net.skybornmc.skyblock.common.staff.StaffModeEvent.ISLAND_CREATIVE_PERMISSION;

    private StaffPerms() {
    }
}
