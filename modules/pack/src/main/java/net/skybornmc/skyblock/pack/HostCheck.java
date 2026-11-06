package net.skybornmc.skyblock.pack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * One self-check of the pack host (async thread only): {@code GET} the URL players use (or {@code host.check-url}),
 * ok = HTTP 200 and the body's SHA-1 equals the bundled one. The reason of a failure is plain text for the staff alert
 * ({@code timeout}, {@code HTTP <code>}, {@code wrong file}, {@code no answer yet}), never a stack trace.
 */
final class HostCheck {

    record Result(boolean ok, String reason) {
    }

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    Result run(URI uri, String expectedSha1) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return new Result(false, "HTTP " + response.statusCode());
            }
            if (!PackFiles.sha1(response.body()).equalsIgnoreCase(expectedSha1)) {
                return new Result(false, "wrong file");
            }
            return new Result(true, "");
        } catch (HttpTimeoutException e) {
            return new Result(false, "timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, "no answer yet");
        } catch (Exception e) {
            return new Result(false, "no answer yet");
        }
    }
}
