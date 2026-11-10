package com.thelads.core.client.social.modsync;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.LongConsumer;

/**
 * Fetches a SITE jar straight from Modrinth's or CurseForge's CDN, the way a manifest install does: https on the site's own
 * hosts only (every redirect is checked too), and the file is kept only when its size and SHA-512 are the entry's.
 */
public final class ModSyncDownloader {
    static final int MAX_REDIRECTS = 3;

    /** One GET without following redirects. */
    public interface Http {
        Response get(URI uri) throws IOException;
    }

    /** location is the Location header of a 3xx answer, else null. */
    public record Response(int status, String location, InputStream body) {}

    private ModSyncDownloader() {}

    public static void download(ModSyncEntry entry, Path target, Http http, LongConsumer progress) throws IOException {
        URI uri = ModSyncSource.siteUri(entry);
        if (uri == null) throw new IOException(entry.file() + ": not on a mod site");
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            Response response = http.get(uri);
            try (InputStream body = response.body()) {
                if (response.status() == 200) {
                    ModSyncWire.receive(body, -1, entry, target, progress);
                    return;
                }
                if (response.status() / 100 != 3 || response.location() == null) throw new IOException(entry.file() + ": HTTP " + response.status());
                URI next;
                try {
                    next = uri.resolve(response.location());
                } catch (IllegalArgumentException e) {
                    next = null;
                }
                if (!ModSyncSource.allowed(entry.source(), next)) throw new IOException(entry.file() + ": redirect away from the mod site refused");
                uri = next;
            }
        }
        throw new IOException(entry.file() + ": too many redirects");
    }

    /** The real client: redirects are never followed by itself, 15 s to connect, 30 s for the answer to start. */
    public static Http http() {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(15)).build();
        return uri -> {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "TheLadsClient-ModSync").GET().build();
            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                return new Response(response.statusCode(), response.headers().firstValue("Location").orElse(null), response.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("download interrupted");
            }
        };
    }
}
