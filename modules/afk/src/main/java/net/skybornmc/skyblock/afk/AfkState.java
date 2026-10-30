package net.skybornmc.skyblock.afk;

/** What a player in (or out of) the pool sees on the action bar. */
enum AfkState {
    /** Not in the pool. */
    OUTSIDE,
    /** Data still loading after join. */
    LOADING,
    /** In the pool without the permission to earn. */
    NO_PERMISSION,
    /** Earning: time is credited. */
    EARNING,
    /** The account's daily cap is reached. */
    CAPPED,
    /** The IP address's daily cap is reached (all accounts together). */
    IP_CAPPED,
    /** Another account on the same IP address is earning. */
    IP_BLOCKED
}
