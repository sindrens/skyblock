package net.skybornmc.skyblock.common.frame;

import net.skybornmc.skyblock.common.pack.PackService;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The gate of the frame layer (GUI-FONT 6.1, FORMAT 0 rule 2): a viewer gets glyphs only when their pack is LOADED,
 * they are on Java (not Floodgate), they did not pick "Plain menus" and {@code gui.font.enabled} is on. Everyone else
 * gets {@link Optional#empty()} and sees the plain layer, which is always complete.
 *
 * <p>{@link #of(Player)} is the instance the menu framework installs on enable (empty before that and after disable).</p>
 */
public final class Glyphs {

    private static volatile Glyphs installed;

    private final Supplier<PackService> packs;
    private final Predicate<Player> bedrock;
    private volatile Predicate<Player> plainMenus = p -> false;
    private final Supplier<FrameSwitches> switches;
    private final FrameMetrics metrics;
    private volatile FrameGlyphs current;
    private volatile FrameGlyphs ignoringSwitches;

    public Glyphs(Supplier<PackService> packs, Predicate<Player> bedrock, Supplier<FrameSwitches> switches, FrameMetrics metrics) {
        this.packs = Objects.requireNonNull(packs);
        this.bedrock = Objects.requireNonNull(bedrock);
        this.switches = Objects.requireNonNull(switches);
        this.metrics = metrics;
        reload();
    }

    /** Re-reads the switches (after {@code /skyblock reload core}); clears the title cache. */
    public void reload() {
        if (metrics == null) {
            current = null;
            ignoringSwitches = null;
            return;
        }
        current = new FrameGlyphs(metrics, switches.get());
        ignoringSwitches = new FrameGlyphs(metrics, FrameSwitches.ALL);
    }

    /** The "Plain menus" setting (menus module): true = that player sees plain menus. */
    public void plainMenus(Predicate<Player> plainMenus) {
        this.plainMenus = plainMenus == null ? p -> false : plainMenus;
    }

    /** The bundled metrics, or null when they could not be read (then nobody gets glyphs). */
    public FrameMetrics metrics() {
        return metrics;
    }

    /** Pure truth table of the gate (GlyphGateTest). */
    public static boolean allowed(PackService.Status status, boolean bedrock, boolean plainMenus, boolean enabled) {
        return status == PackService.Status.LOADED && !bedrock && !plainMenus && enabled;
    }

    private PackService.Status status(Player viewer) {
        PackService service = packs.get();
        return service == null || viewer == null ? null : service.status(viewer.getUniqueId());
    }

    /** Glyphs for {@code viewer} with the live switches; empty for Bedrock, no pack, Plain menus or the kill switch. */
    public Optional<FrameGlyphs> forViewer(Player viewer) {
        FrameGlyphs glyphs = current;
        if (glyphs == null || viewer == null) {
            return Optional.empty();
        }
        boolean ok = allowed(status(viewer), bedrock.test(viewer), plainMenus.test(viewer), glyphs.switches().enabled());
        return ok ? Optional.of(glyphs) : Optional.empty();
    }

    /** Staff glyph test: Java with the pack LOADED only; the {@code gui.font.*} switches and Plain menus are ignored. */
    public Optional<FrameGlyphs> forViewerIgnoringSwitches(Player viewer) {
        FrameGlyphs glyphs = ignoringSwitches;
        if (glyphs == null || viewer == null) {
            return Optional.empty();
        }
        return allowed(status(viewer), bedrock.test(viewer), false, true) ? Optional.of(glyphs) : Optional.empty();
    }

    /**
     * Logo helper for the HUD lane (GUI-FONT 6.8): the logo glyph for the scoreboard title, empty for Bedrock, no pack,
     * {@code gui.font.enabled} or {@code gui.font.logo} off. "Plain menus" does not hide the logo (it is about menus).
     */
    public Optional<net.kyori.adventure.text.Component> logoWide(Player viewer) {
        return logoGlyphs(viewer).flatMap(FrameGlyphs::logoWide);
    }

    /** Same for the tab header (the caller adds one blank line under it). */
    public Optional<net.kyori.adventure.text.Component> logoSmall(Player viewer) {
        return logoGlyphs(viewer).flatMap(FrameGlyphs::logoSmall);
    }

    private Optional<FrameGlyphs> logoGlyphs(Player viewer) {
        FrameGlyphs glyphs = current;
        if (glyphs == null || viewer == null) {
            return Optional.empty();
        }
        return allowed(status(viewer), bedrock.test(viewer), false, glyphs.switches().enabled()) ? Optional.of(glyphs) : Optional.empty();
    }

    /** Installs the framework instance ({@code MenuService#enable}); {@code null} clears it. */
    public static void install(Glyphs glyphs) {
        installed = glyphs;
    }

    /** The framework's glyphs for {@code viewer}; empty before the menu framework is enabled. */
    public static Optional<FrameGlyphs> of(Player viewer) {
        Glyphs glyphs = installed;
        return glyphs == null ? Optional.empty() : glyphs.forViewer(viewer);
    }

    /** The installed instance, if any. */
    public static Optional<Glyphs> installed() {
        return Optional.ofNullable(installed);
    }
}
