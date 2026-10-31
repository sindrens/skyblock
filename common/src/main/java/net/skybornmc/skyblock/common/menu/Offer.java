package net.skybornmc.skyblock.common.menu;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Something a player can answer with a click: a TPA request, an island invite, a link (GUI-5, GUI_DESIGN §5). Java
 * players get the clickable chat line; Bedrock players can't click chat, so they also get a small 3-row popup built
 * from the same offer ({@link Offers}). Build one with {@link #builder} and hand it to {@code core.menus().offers()}.
 *
 * <p>Texts come from the module's messages file: {@code <key>.title} (chest title), {@code <key>.name} and
 * {@code <key>.lore} (the item that says what this is). Button texts are shared ({@code messages/menus.yml →
 * offer.button.<kind>}) unless a button names its own key. Every text gets the offer's placeholders plus
 * {@code <left>} (time until it expires, when it has an expiry).</p>
 *
 * <pre>{@code
 * core.menus().offers().offer(target, Offer.builder("essentials", "tpa.popup")
 *         .id("tpa:" + sender.getUniqueId()).icon(Material.ENDER_PEARL).placeholders(tags)
 *         .expiresAt(expiry).validWhile(() -> requests.find(to, from).isPresent())
 *         .button(Offer.Button.accept("tpaccept " + sender.getName()))
 *         .button(Offer.Button.deny("tpdeny " + sender.getName()))
 *         .chat(p -> msg.send(p, "tpa.received", tags))
 *         .build());
 * }</pre>
 */
public final class Offer implements OfferQueue.Queued {

    /** Most buttons on one popup (11 / 13 / 15 on the middle row). */
    public static final int MAX_BUTTONS = 3;

    /** The shared button looks ({@code offer.button.<key>} in messages/menus.yml). */
    public enum Kind {
        ACCEPT("accept", Material.LIME_CONCRETE),
        DECLINE("decline", Material.RED_CONCRETE),
        ALLOW("allow", Material.LIME_CONCRETE),
        DENY("deny", Material.RED_CONCRETE),
        TELEPORT("teleport", Material.ENDER_PEARL),
        RUN("run", Material.LIME_CONCRETE);

        private final String key;
        private final Material material;

        Kind(String key, Material material) {
            this.key = key;
            this.material = material;
        }

        public String key() {
            return key;
        }

        public Material material() {
            return material;
        }
    }

    /**
     * One popup button. {@code command} (without the slash) runs as the player after the popup closes, so the command
     * checks everything again (still pending, permission, cooldowns) and audits as usual.
     *
     * @param module   messages module of the texts ({@code null} = menus, the shared {@code offer.button.*} texts)
     * @param key      {@code <key>.name} / {@code <key>.lore}; gets {@code <command>} plus the offer placeholders
     */
    public record Button(Material material, String module, String key, String command) {

        public Button {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(command, "command");
            command = command.startsWith("/") ? command.substring(1) : command;
        }

        public static Button of(Kind kind, String command) {
            return new Button(kind.material(), null, "offer.button." + kind.key(), command);
        }

        public static Button accept(String command) {
            return of(Kind.ACCEPT, command);
        }

        public static Button decline(String command) {
            return of(Kind.DECLINE, command);
        }

        public static Button allow(String command) {
            return of(Kind.ALLOW, command);
        }

        public static Button deny(String command) {
            return of(Kind.DENY, command);
        }

        public static Button teleport(String command) {
            return of(Kind.TELEPORT, command);
        }

        public static Button run(String command) {
            return of(Kind.RUN, command);
        }

        /** A button with the module's own texts ({@code <key>.name} / {@code <key>.lore} in {@code module}). */
        public static Button custom(Material material, String module, String key, String command) {
            return new Button(material, Objects.requireNonNull(module, "module"), key, command);
        }
    }

    private final String id;
    private final String module;
    private final String titleKey;
    private final String nameKey;
    private final String loreKey;
    private final Material icon;
    private final TagResolver[] placeholders;
    private final List<Button> buttons;
    private final Consumer<Player> chat;
    private final Consumer<Player> onInfoClick;
    private final long expiresAt;
    private final BooleanSupplier valid;
    private final long createdAt;

    private Offer(Builder b) {
        this.id = b.id == null ? b.module + ":" + b.key : b.id;
        this.module = b.module;
        this.titleKey = b.titleKey != null ? b.titleKey : b.key + ".title";
        this.nameKey = b.nameKey != null ? b.nameKey : b.key + ".name";
        this.loreKey = b.loreKey != null ? b.loreKey : b.key + ".lore";
        this.icon = b.icon;
        this.placeholders = b.placeholders.toArray(TagResolver[]::new);
        this.buttons = List.copyOf(b.buttons);
        this.chat = b.chat;
        this.onInfoClick = b.onInfoClick;
        this.expiresAt = b.expiresAt;
        this.valid = b.valid;
        this.createdAt = System.currentTimeMillis();
    }

    /** {@code module} = messages file of the texts, {@code key} = their prefix ({@code <key>.title/.name/.lore}). */
    public static Builder builder(String module, String key) {
        return new Builder(module, key);
    }

    /**
     * Pure expiry rule: past {@code expiresAt} (0 = no time limit) or no longer valid (answered, cancelled, the other
     * player left). Unit-tested.
     */
    public static boolean expired(long expiresAt, BooleanSupplier valid, long now) {
        if (expiresAt > 0 && now >= expiresAt) {
            return true;
        }
        return valid != null && !valid.getAsBoolean();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean expired(long now) {
        return expired(expiresAt, valid, now);
    }

    /** True when there is something to show in a popup (buttons, or an item that does something on click). */
    public boolean hasPopup() {
        return !buttons.isEmpty() || onInfoClick != null;
    }

    public String module() {
        return module;
    }

    public String titleKey() {
        return titleKey;
    }

    public String nameKey() {
        return nameKey;
    }

    public String loreKey() {
        return loreKey;
    }

    public Material icon() {
        return icon;
    }

    public TagResolver[] placeholders() {
        return placeholders.clone();
    }

    public List<Button> buttons() {
        return buttons;
    }

    public Consumer<Player> chat() {
        return chat;
    }

    public Consumer<Player> onInfoClick() {
        return onInfoClick;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public long createdAt() {
        return createdAt;
    }

    public static final class Builder {
        private final String module;
        private final String key;
        private String id;
        private String titleKey;
        private String nameKey;
        private String loreKey;
        private Material icon = Material.PAPER;
        private final List<TagResolver> placeholders = new ArrayList<>();
        private final List<Button> buttons = new ArrayList<>();
        private Consumer<Player> chat;
        private Consumer<Player> onInfoClick;
        private long expiresAt;
        private BooleanSupplier valid;

        private Builder(String module, String key) {
            this.module = Objects.requireNonNull(module, "module");
            this.key = Objects.requireNonNull(key, "key");
        }

        /** Same id = same offer: a newer one replaces the queued older one (e.g. {@code tpa:<sender uuid>}). */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder title(String key) {
            this.titleKey = key;
            return this;
        }

        public Builder name(String key) {
            this.nameKey = key;
            return this;
        }

        public Builder lore(String key) {
            this.loreKey = key;
            return this;
        }

        public Builder icon(Material icon) {
            this.icon = Objects.requireNonNull(icon, "icon");
            return this;
        }

        public Builder placeholders(TagResolver... placeholders) {
            this.placeholders.addAll(List.of(placeholders));
            return this;
        }

        public Builder button(Button button) {
            if (buttons.size() >= MAX_BUTTONS) {
                throw new IllegalArgumentException("An offer popup has at most " + MAX_BUTTONS + " buttons");
            }
            buttons.add(Objects.requireNonNull(button, "button"));
            return this;
        }

        /** The chat line (Java: clickable). Sent to every player, Bedrock too (they can read it). */
        public Builder chat(Consumer<Player> chat) {
            this.chat = chat;
            return this;
        }

        /** What a click on the middle item does (links: send the URL once in plain chat). */
        public Builder onInfoClick(Consumer<Player> action) {
            this.onInfoClick = action;
            return this;
        }

        /** Epoch millis after which the offer is expired (0 = no time limit; queued popups still age out). */
        public Builder expiresAt(long expiresAt) {
            this.expiresAt = Math.max(0, expiresAt);
            return this;
        }

        /** Still answerable (e.g. the request is still pending); checked before opening and on every click. */
        public Builder validWhile(BooleanSupplier valid) {
            this.valid = valid;
            return this;
        }

        public Offer build() {
            return new Offer(this);
        }
    }
}
