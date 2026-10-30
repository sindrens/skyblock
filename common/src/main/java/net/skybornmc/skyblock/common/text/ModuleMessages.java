package net.skybornmc.skyblock.common.text;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** {@link Messages} bound to one module's {@code messages/<module>.yml}. */
public record ModuleMessages(Messages messages, String module) {

    public void send(Audience viewer, String key, TagResolver... placeholders) {
        messages.send(viewer, module, key, placeholders);
    }

    public Component component(Audience viewer, String key, TagResolver... placeholders) {
        return messages.component(viewer, module, key, placeholders);
    }

    /** {@link Messages#fixed}: a message without placeholders, parsed once per viewer type. */
    public Component fixed(Audience viewer, String key) {
        return messages.fixed(viewer, module, key);
    }

    public void actionBar(Audience viewer, String key, TagResolver... placeholders) {
        messages.actionBar(viewer, module, key, placeholders);
    }

    public void title(Audience viewer, String key, TagResolver... placeholders) {
        messages.title(viewer, module, key, placeholders);
    }

    public boolean has(String key) {
        return messages.has(module, key);
    }
}
