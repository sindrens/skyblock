package net.skybornmc.skyblock.pack;

/** Pure: what happens to the pack when a player joins (GUI-P1-PACK step 4.1). */
final class PackJoinPolicy {

    enum Decision { SEND, SKIP_BEDROCK, SKIP_DISABLED, SKIP_HOST_DOWN }

    private PackJoinPolicy() {
    }

    /**
     * Bedrock first (never sent, never kicked), then the switch, then the host: a host that failed its self-check is
     * never offered (nobody gets a dead URL, nobody is kicked). {@code firstOffer} is false for late resends, which
     * follow the same rules (they are only never enforced).
     */
    static Decision decide(boolean bedrock, boolean enabled, boolean healthy, boolean firstOffer) {
        if (bedrock) {
            return Decision.SKIP_BEDROCK;
        }
        if (!enabled) {
            return Decision.SKIP_DISABLED;
        }
        if (!healthy) {
            return Decision.SKIP_HOST_DOWN;
        }
        return Decision.SEND;
    }
}
