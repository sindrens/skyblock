package net.skybornmc.skyblock.moderation;

import java.util.Map;

/**
 * One validation error in {@code punishments.yml}. The text is message key {@code key} in {@code messages/moderation.yml}
 * (placeholders: {@code <file>}, {@code <line>}, {@code <path>} plus {@code args}; {@code <hint>} is built from
 * {@code args.suggestion} with {@code config.hint}).
 */
record ConfigProblem(String key, String path, int line, Map<String, String> args) {

    String arg(String name) {
        return args.getOrDefault(name, "");
    }
}
