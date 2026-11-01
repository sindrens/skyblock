package net.skybornmc.skyblock.common.frame;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The Adventure side of the frame layer (GUI-FONT 6.1) for one set of switches: framed menu titles and the logo
 * glyphs. Only handed out by {@link Glyphs} to viewers that may see them (Java, pack LOADED). Every run is white (the
 * colours are baked into the glyphs), without shadow and never italic. Built titles are cached (256 entries, LRU).
 */
public final class FrameGlyphs {

    public static final Key FRAME_FONT = Key.key("phoenix", "frame");
    public static final Key TITLE_FONT = Key.key("phoenix", "title");
    private static final int CACHE = 256;

    private record CacheKey(PanelType type, BitSet filled, String plaque, boolean tiles, Component plain) {
    }

    private final FrameMetrics metrics;
    private final FrameSwitches switches;
    private final Map<CacheKey, Component> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, Component> eldest) {
            return size() > CACHE;
        }
    };

    FrameGlyphs(FrameMetrics metrics, FrameSwitches switches) {
        this.metrics = Objects.requireNonNull(metrics);
        this.switches = Objects.requireNonNull(switches);
    }

    public FrameSwitches switches() {
        return switches;
    }

    public FrameMetrics metrics() {
        return metrics;
    }

    /**
     * The framed title of a {@code type} chest whose top inventory has items in {@code filled}: panel, soft tiles (when
     * on), the plaque with {@code plaque} (a sanitised noun; blank, invalid or too wide = no plaque) or, without one,
     * {@code plainTitle} on the panel's top band. With panels switched off this returns {@code plainTitle}.
     */
    public Component menuTitle(PanelType type, BitSet filled, String plaque, Component plainTitle) {
        if (!switches.panelsOn()) {
            return plainTitle;
        }
        String noun = switches.titlesOn() ? PlaqueFit.sanitize(plaque) : "";
        boolean tiles = switches.tilesOn();
        CacheKey key = new CacheKey(type, filled == null ? new BitSet() : (BitSet) filled.clone(), noun, tiles,
                noun.isEmpty() || PlaqueFit.fit(metrics, noun).isEmpty() ? plainTitle : null);
        synchronized (cache) {
            Component cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Optional<PlaqueFit.Fit> fit = PlaqueFit.fit(metrics, noun);
        PanelTitle.Built built = PanelTitle.build(metrics, type, filled, tiles, fit, noun);
        Component title = Component.empty();
        for (PanelTitle.Run run : built.runs()) {
            title = title.append(run(run.font() == PanelTitle.Font.FRAME ? FRAME_FONT : TITLE_FONT, run.text()));
        }
        if (!built.plaque()) {
            title = title.append(plainTitle);
        }
        synchronized (cache) {
            cache.put(key, title);
        }
        return title;
    }

    /** True when {@code plaque} sanitises to a noun that fits the large plaque (else the plain title is shown). */
    public boolean plaqueFits(String plaque) {
        return PlaqueFit.fit(metrics, PlaqueFit.sanitize(plaque)).isPresent();
    }

    /** The logo for the scoreboard title (bottom edge on the title row); empty when {@code gui.font.logo} is off. */
    public Optional<Component> logoWide() {
        return switches.logoOn() ? Optional.of(run(FRAME_FONT, String.valueOf(metrics.logoWide().cp()))) : Optional.empty();
    }

    /** The logo for the tab header (the caller adds one blank line under it); empty when {@code gui.font.logo} is off. */
    public Optional<Component> logoSmall() {
        return switches.logoOn() ? Optional.of(run(FRAME_FONT, String.valueOf(metrics.logoSmall().cp()))) : Optional.empty();
    }

    static Component run(Key font, String text) {
        return Component.text(text).font(font).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none())
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false);
    }
}
