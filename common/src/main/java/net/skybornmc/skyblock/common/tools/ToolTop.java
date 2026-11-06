package net.skybornmc.skyblock.common.tools;

import net.skybornmc.skyblock.common.leaderboard.TopLine;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Owner 7 Nov 2026 (Top Tools board): players ranked by their own island tool prestiges, a rebirth counting as
 * {@code rebirth.board-weight} prestiges. Published by the islandtools module; read off the main thread.
 */
@FunctionalInterface
public interface ToolTop {

    /** The best {@code limit} players for one tool ({@code hoe}, {@code pickaxe}, {@code rod}, {@code sword}) or {@code all}. */
    CompletableFuture<List<TopLine>> top(String kind, int limit);
}
