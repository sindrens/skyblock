package net.skybornmc.skyblock.common.menu;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Logger;

/**
 * Bedrock text input without chat (GUI-4b, GUI_DESIGN §5 "typing is the last option", UX_REVIEW §4): a Floodgate form
 * (Cumulus {@code CustomForm}) with one text field. It is the native Bedrock popup with an on-screen keyboard, so touch
 * players can type a note or a warp name without opening chat. Uses Floodgate through reflection (soft dependency, no
 * compile dependency), like {@link net.skybornmc.skyblock.common.text.BedrockCheck}; when Floodgate or its form API is
 * missing, {@link #detect} returns {@link #NONE} and callers fall back to the chat prompt.
 *
 * <p>Why not an anvil: Geyser's anvil needs XP levels on Bedrock to take the result and shows "Too expensive" at cost 0,
 * so it is unreliable for free text. Forms are what Geyser recommends for input.</p>
 */
interface BedrockForms {

    /**
     * Shows a form with one text field. Handlers run on the thread Floodgate calls them on (the caller hops to main).
     *
     * @param player   the Bedrock player
     * @param title    form title (legacy § text)
     * @param label    text above the field (legacy § text)
     * @param value    text already in the field ({@code ""} = empty)
     * @param onText   the typed text ({@code ""} if the field was left empty)
     * @param onClosed the player closed the form (or the answer was invalid)
     * @return false if the form could not be sent (the caller falls back to chat)
     */
    boolean send(UUID player, String title, String label, String value, Consumer<String> onText, Runnable onClosed);

    /**
     * Shows a read-only text popup (GUI-4c: help pages on Bedrock, where lore can't be hovered): a Cumulus
     * {@code SimpleForm} with the text and one button per label. Handlers run on Floodgate's thread.
     *
     * @param content  the text (legacy § text, lines joined with a newline)
     * @param buttons  button labels, top to bottom
     * @param onButton the index of the button pressed
     * @param onClosed the player closed the popup
     * @return false if the popup could not be sent
     */
    default boolean sendText(UUID player, String title, String content, List<String> buttons, IntConsumer onButton, Runnable onClosed) {
        return false;
    }

    /** No forms (no Floodgate, tests). */
    BedrockForms NONE = (player, title, label, value, onText, onClosed) -> false;

    /** Floodgate's form API when it is installed and usable, otherwise {@link #NONE}. */
    static BedrockForms detect(Logger log) {
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) {
            return NONE;
        }
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            Class<?> form = Class.forName("org.geysermc.cumulus.form.Form");
            Class<?> customForm = Class.forName("org.geysermc.cumulus.form.CustomForm");
            Class<?> builderType = Class.forName("org.geysermc.cumulus.form.CustomForm$Builder");
            Class<?> responseType = Class.forName("org.geysermc.cumulus.response.CustomFormResponse");
            Method builder = customForm.getMethod("builder");
            Method title = builderType.getMethod("title", String.class);
            Method input = builderType.getMethod("input", String.class, String.class, String.class);
            Method valid = builderType.getMethod("validResultHandler", Consumer.class);
            Method closed = builderType.getMethod("closedOrInvalidResultHandler", Runnable.class);
            Method build = builderType.getMethod("build");
            Method asInput = responseType.getMethod("asInput", int.class);
            Method sendForm = api.getMethod("sendForm", UUID.class, form);
            Text textForms = Text.find(api, instance, form, sendForm, log);
            return new BedrockForms() {
                @Override
                public boolean send(UUID player, String titleText, String label, String value, Consumer<String> onText, Runnable onClosed) {
                    try {
                        Object b = builder.invoke(null);
                        title.invoke(b, titleText);
                        // The field's own text is the label, so the field is component 0 of the answer.
                        input.invoke(b, label, "", value == null ? "" : value);
                        Consumer<Object> handler = response -> {
                            String text;
                            try {
                                Object typed = asInput.invoke(response, 0);
                                text = typed == null ? "" : typed.toString();
                            } catch (ReflectiveOperationException | RuntimeException e) {
                                onClosed.run();
                                return;
                            }
                            onText.accept(text);
                        };
                        valid.invoke(b, handler);
                        closed.invoke(b, onClosed);
                        Object built = build.invoke(b);
                        return (boolean) sendForm.invoke(instance, player, built);
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        log.warning("[PhoenixSkyblock] Sending a Bedrock form failed, using chat instead: " + e);
                        return false;
                    }
                }

                @Override
                public boolean sendText(UUID player, String titleText, String content, List<String> buttons, IntConsumer onButton,
                                        Runnable onClosed) {
                    return textForms != null && textForms.send(player, titleText, content, buttons, onButton, onClosed);
                }
            };
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            log.warning("[PhoenixSkyblock] Floodgate found but its form API could not be used (chat prompts only): " + e);
            return NONE;
        }
    }

    /** The SimpleForm part of Floodgate's form API (read-only text popups); null when it is missing. */
    final class Text {
        private final Object instance;
        private final Method sendForm;
        private final Method builder;
        private final Method title;
        private final Method content;
        private final Method button;
        private final Method valid;
        private final Method closed;
        private final Method build;
        private final Method clickedId;
        private final Logger log;

        private Text(Object instance, Method sendForm, Method builder, Method title, Method content, Method button, Method valid,
                     Method closed, Method build, Method clickedId, Logger log) {
            this.instance = instance;
            this.sendForm = sendForm;
            this.builder = builder;
            this.title = title;
            this.content = content;
            this.button = button;
            this.valid = valid;
            this.closed = closed;
            this.build = build;
            this.clickedId = clickedId;
            this.log = log;
        }

        static Text find(Class<?> api, Object instance, Class<?> form, Method sendForm, Logger log) {
            try {
                Class<?> simpleForm = Class.forName("org.geysermc.cumulus.form.SimpleForm");
                Class<?> builderType = Class.forName("org.geysermc.cumulus.form.SimpleForm$Builder");
                Class<?> responseType = Class.forName("org.geysermc.cumulus.response.SimpleFormResponse");
                return new Text(instance, sendForm, simpleForm.getMethod("builder"), builderType.getMethod("title", String.class),
                        builderType.getMethod("content", String.class), builderType.getMethod("button", String.class),
                        builderType.getMethod("validResultHandler", Consumer.class),
                        builderType.getMethod("closedOrInvalidResultHandler", Runnable.class), builderType.getMethod("build"),
                        responseType.getMethod("clickedButtonId"), log);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                log.warning("[PhoenixSkyblock] Floodgate's text popups could not be used (help pages stay in the menu): " + e);
                return null;
            }
        }

        boolean send(UUID player, String titleText, String text, List<String> buttons, IntConsumer onButton, Runnable onClosed) {
            try {
                Object b = builder.invoke(null);
                title.invoke(b, titleText);
                content.invoke(b, text);
                for (String label : buttons) {
                    button.invoke(b, label);
                }
                Consumer<Object> handler = response -> {
                    int id;
                    try {
                        id = ((Number) clickedId.invoke(response)).intValue();
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        onClosed.run();
                        return;
                    }
                    onButton.accept(id);
                };
                valid.invoke(b, handler);
                closed.invoke(b, onClosed);
                return (boolean) sendForm.invoke(instance, player, build.invoke(b));
            } catch (ReflectiveOperationException | RuntimeException e) {
                log.warning("[PhoenixSkyblock] Sending a Bedrock text popup failed: " + e);
                return false;
            }
        }
    }
}
