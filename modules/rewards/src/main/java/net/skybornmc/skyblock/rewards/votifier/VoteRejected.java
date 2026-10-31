package net.skybornmc.skyblock.rewards.votifier;

/** A connection or packet that is not a valid vote. The message is a short reason for the (throttled) console log. */
public final class VoteRejected extends Exception {

    public VoteRejected(String reason) {
        super(reason, null, false, false);
    }
}
