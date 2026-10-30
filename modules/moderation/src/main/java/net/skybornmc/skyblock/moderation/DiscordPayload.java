package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.discord.DiscordSink;

import java.time.Instant;

/**
 * JSON body of a Discord webhook call for one embed. Pure. Mentions are disabled ({@code allowed_mentions.parse = []},
 * so a reason containing {@code @everyone} pings nobody), markdown in values is escaped and every part is cut to
 * Discord's limits (title 256, field name 256, value 1024, footer 2048, 25 fields).
 */
final class DiscordPayload {

    static final int MAX_TITLE = 256;
    static final int MAX_NAME = 256;
    static final int MAX_VALUE = 1024;
    static final int MAX_FOOTER = 2048;
    static final int MAX_FIELDS = 25;

    private DiscordPayload() {
    }

    static String json(DiscordSink.Embed e) {
        StringBuilder b = new StringBuilder(256);
        b.append("{\"embeds\":[{\"title\":").append(str(Evidence.cut(e.title(), MAX_TITLE)));
        b.append(",\"color\":").append(e.color() & 0xFFFFFF);
        b.append(",\"fields\":[");
        int n = 0;
        for (DiscordSink.Field f : e.fields()) {
            if (n >= MAX_FIELDS) {
                break;
            }
            if (n++ > 0) {
                b.append(',');
            }
            b.append("{\"name\":").append(str(Evidence.cut(f.name(), MAX_NAME)))
                    .append(",\"value\":").append(str(Evidence.cut(markdown(f.value().isBlank() ? "-" : f.value()), MAX_VALUE)))
                    .append(",\"inline\":").append(f.inline()).append('}');
        }
        b.append(']');
        if (e.footer() != null && !e.footer().isBlank()) {
            b.append(",\"footer\":{\"text\":").append(str(Evidence.cut(e.footer(), MAX_FOOTER))).append('}');
        }
        if (e.timestamp() > 0) {
            b.append(",\"timestamp\":").append(str(Instant.ofEpochMilli(e.timestamp()).toString()));
        }
        b.append("}],\"allowed_mentions\":{\"parse\":[]}}");
        return b.toString();
    }

    /** Escapes Discord markdown so reasons show literally. */
    static String markdown(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            if ("\\*_~`|>".indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    /** A JSON string literal. */
    static String str(String s) {
        StringBuilder out = new StringBuilder(s.length() + 2).append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
