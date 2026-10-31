package net.skybornmc.skyblock.pack;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Starts the real web server on a free loopback port (no Minecraft server involved). */
class PackHostHttpTest {

    @Test
    void servesTheZipAnd404sTheRest() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        PackHost host = new PackHost("127.0.0.1", port);
        byte[] data = {1, 2, 3, 4};
        host.serve(new PackHost.Served("p.zip", data));
        host.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<byte[]> ok = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/p.zip")).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, ok.statusCode());
            assertEquals("application/zip", ok.headers().firstValue("Content-Type").orElse(""));
            assertArrayEquals(data, ok.body());
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/other")).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
        } finally {
            host.stop();
        }
    }
}
