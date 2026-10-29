package net.skybornmc.skyblock.depths;

/** Permission nodes of the depths module (declared in permissions/depths.yml). */
public final class DepthsPerms {

    /** {@code /depths} itself (each sub-command needs its own node too). */
    public static final String COMMAND = "skyblock.depths.command";
    /** {@code /depths debug}. */
    public static final String DEBUG = "skyblock.depths.debug";
    /** Fast-break alerts. */
    public static final String ALERTS = "skyblock.depths.alerts";
    /** Change blocks inside regions for real while in creative mode. */
    public static final String BUILD = "skyblock.depths.build";

    /** {@code /depths generate}: build the placeholder layout. */
    public static final String GENERATE = "skyblock.depths.generate";
    /** {@code /depths tp}: staff teleport to a zone. */
    public static final String TP = "skyblock.depths.tp";

    private DepthsPerms() {
    }
}
