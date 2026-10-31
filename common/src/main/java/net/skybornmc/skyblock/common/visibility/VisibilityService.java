package net.skybornmc.skyblock.common.visibility;

import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * The only owner of {@code Player#hidePlayer}/{@code showPlayer} for this plugin (REVIEW3 D4). Bukkit tracks hides per
 * plugin, so two features calling hide/show on their own would undo each other (the AFK pool showing a vanished
 * moderator, for example). Every feature goes through here instead:
 *
 * <pre>hidden(viewer, target) = target vanished (viewer lacks see-vanished)
 *                          OR (target is not staff AND (a module rule hides OR the viewer's Show players setting hides))</pre>
 *
 * <p>Only changes are applied (a diff per viewer), on join, quit, world change, setting change, vanish toggle, region
 * enter/leave and when a module asks ({@link #refresh}); never a per-tick scan of all pairs. Main thread only.</p>
 *
 * <p>Hidden players are also gone from that viewer's tab list (Minecraft does that for hidden players). Their client no
 * longer knows the body, so it can't collide with it either. Commands and tab completion must not treat a hidden
 * player as offline: use {@link #knows} (vanish only) instead of {@code Player#canSee} for "does this player exist for
 * me" checks, and {@code canSee} only for "is the body shown" checks (trails, area effects).</p>
 *
 * <p>Published by the core: {@code core.services().optional(VisibilityService.class)}.</p>
 */
public interface VisibilityService {

    /** May see vanished staff (owned by the essentials module, which owns {@code /vanish}). */
    String SEE_VANISHED = "skyblock.staff.vanish.see";
    /** Staff that stays visible to everyone while not vanished (plugin.yml; granted to helpers and up). */
    String ALWAYS_SHOWN = "skyblock.staff.visible";
    /** The {@code /settings} choice id. */
    String SETTING = "show-players";

    /** A module rule: true = {@code viewer} must not see {@code target} (e.g. both stand in the AFK pool). Keep it cheap. */
    @FunctionalInterface
    interface Rule {
        boolean hides(Player viewer, Player target);
    }

    static Optional<VisibilityService> of(ServiceRegistry services) {
        return services.optional(VisibilityService.class);
    }

    // ------------------------------------------------------------------ vanish

    /** Turns vanish on or off for {@code player} and applies it to every viewer. */
    void setVanished(Player player, boolean vanished);

    boolean isVanished(UUID player);

    default boolean isVanished(Player player) {
        return isVanished(player.getUniqueId());
    }

    /**
     * True if {@code target} exists for {@code viewer}: not vanished, or the viewer may see vanished staff. Ignores rules
     * and settings, so a player hidden by the AFK pool or the Show players setting can still be messaged, traded with
     * and tab-completed.
     */
    boolean knows(Player viewer, Player target);

    /** True if this service currently hides {@code target}'s body from {@code viewer}. */
    boolean hidden(Player viewer, Player target);

    // ------------------------------------------------------------------ rules and relations

    /**
     * Adds a rule. {@code owner} is the module id (rules are removed with {@link #removeAll} when it stops). Call
     * {@link #refresh} for the players a rule's answer changes for (e.g. on entering and leaving an area).
     */
    void addRule(String owner, String id, Rule rule);

    /**
     * Adds a "friends" source for the {@code Friends + island} choice: true = {@code viewer} counts {@code target} as a
     * friend. Island members and coop players are built in; a friends system adds itself here later.
     */
    void addRelation(String owner, BiPredicate<Player, Player> related);

    /** Removes the rules and relations of {@code owner} and re-applies. */
    void removeAll(String owner);

    // ------------------------------------------------------------------ settings and refresh

    /** The player's {@code Show players} choice (the configured default while /settings is not available). */
    ShowMode mode(Player player);

    /** Re-applies everything about {@code player}: as a viewer (who they see) and as a target (who sees them). */
    void refresh(Player player);

    /** Re-applies everything for everyone (after a reload). */
    void refreshAll();
}
