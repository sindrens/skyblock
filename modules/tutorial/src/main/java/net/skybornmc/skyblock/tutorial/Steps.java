package net.skybornmc.skyblock.tutorial;

import java.util.List;

/** Ids of the core steps (TUTORIAL.md §3) and the pseudo-step {@link #FINISH}. */
final class Steps {

    static final String PORTAL = "portal";
    static final String ARRIVE = "arrive";
    static final String CHEST = "chest";
    static final String CROP = "crop";
    static final String MENU = "menu";
    static final String DEPTHS = "depths";
    static final String MINE = "mine";
    static final String SELL = "sell";
    static final String FORGE = "forge";
    static final String HELP = "help";
    static final String BANK = "bank";
    /** Not a step: "when the tutorial is finished" in gate lines. */
    static final String FINISH = "finish";

    /** Every step the engine knows how to detect, in the plan's order. */
    static final List<String> KNOWN = List.of(PORTAL, ARRIVE, CHEST, CROP, MENU, DEPTHS, MINE, SELL, FORGE, HELP, BANK);

    private Steps() {
    }
}
