package net.skybornmc.skyblock.common.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** JSON for the {@code details} column, archive lines and the spool file (Gson ships with Paper). */
final class AuditJson {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static final Type MAP = new TypeToken<LinkedHashMap<String, Object>>() { }.getType();

    private AuditJson() {
    }

    static String details(Map<String, Object> details) {
        return details == null || details.isEmpty() ? null : GSON.toJson(details);
    }

    static Map<String, Object> details(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> map = GSON.fromJson(json, MAP);
            return map == null ? Map.of() : map;
        } catch (JsonParseException e) {
            return Map.of("raw", json);
        }
    }

    /** One full row as a JSON line (archives, exports, spool). */
    static String line(AuditEntry e) {
        JsonObject o = new JsonObject();
        if (e.id() > 0) {
            o.addProperty("id", e.id());
        }
        o.addProperty("time", e.time());
        o.addProperty("season", e.season());
        o.addProperty("server", e.server());
        o.addProperty("category", e.category().key());
        o.addProperty("action", e.action());
        if (e.actor().uuid() != null) {
            o.addProperty("actor_uuid", e.actor().uuid().toString());
        }
        o.addProperty("actor_name", e.actor().name());
        o.addProperty("target", e.target());
        o.addProperty("target_name", e.targetName());
        o.addProperty("amount", e.amount());
        o.addProperty("world", e.world());
        o.addProperty("x", e.x());
        o.addProperty("y", e.y());
        o.addProperty("z", e.z());
        o.add("details", GSON.toJsonTree(e.details()));
        return GSON.toJson(o);
    }

    /** Reads a line written by {@link #line}; returns {@code null} if it is not a valid row. */
    static AuditEntry parse(String line) {
        try {
            JsonObject o = GSON.fromJson(line, JsonObject.class);
            if (o == null) {
                return null;
            }
            AuditCategory category = AuditCategory.byKey(str(o, "category")).orElse(null);
            if (category == null) {
                return null;
            }
            String actorUuid = str(o, "actor_uuid");
            Actor actor = new Actor(actorUuid == null ? null : UUID.fromString(actorUuid), str(o, "actor_name"));
            Map<String, Object> details = o.has("details") && o.get("details").isJsonObject()
                    ? GSON.fromJson(o.get("details"), MAP) : Map.of();
            return new AuditEntry(0, o.get("time").getAsLong(), o.has("season") ? o.get("season").getAsInt() : 0,
                    str(o, "server"), category, str(o, "action"), actor, str(o, "target"), str(o, "target_name"),
                    num(o, "amount"), str(o, "world"), intOrNull(o, "x"), intOrNull(o, "y"), intOrNull(o, "z"), details);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static Long num(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : null;
    }

    private static Integer intOrNull(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : null;
    }
}
