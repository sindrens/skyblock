package net.skybornmc.skyblock.economy;

import java.util.Locale;

/** The balance-changing {@code /eco} sub-commands. */
enum EcoAction {
    GIVE, TAKE, SET, RESET;

    /** Message/audit spelling ({@code give}). */
    String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
