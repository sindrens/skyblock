package net.skybornmc.skyblock.pack;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/** Pure helpers: the download URL and the stable pack id. */
final class PackUrls {

    private PackUrls() {
    }

    /**
     * The URL players download from: {@code configured} when set, otherwise {@code http://<publicHost>:<port>/<file>}.
     * Throws {@link IllegalArgumentException} for a URL that is not http(s).
     */
    static URI url(String configured, String publicHost, int port, String file) {
        String text = configured == null ? "" : configured.trim();
        if (text.isEmpty()) {
            String host = publicHost == null ? "" : publicHost.trim();
            if (host.isEmpty()) {
                throw new IllegalArgumentException("host.public-host is empty");
            }
            text = "http://" + host + ":" + port + "/" + file;
        }
        URI uri;
        try {
            uri = URI.create(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + text + "' is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ((!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null) {
            throw new IllegalArgumentException("'" + text + "' must be an http(s) URL");
        }
        return uri;
    }

    /** One stable id per variant: the client replaces its copy when a new hash arrives under the same id. */
    static UUID packId(String variant) {
        return UUID.nameUUIDFromBytes(("phoenix-pack:" + variant.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
    }
}
