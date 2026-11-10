package com.thelads.core.client.social.modsync;

import com.thelads.core.client.social.p2p.Mux;
import com.thelads.core.client.social.p2p.P2PSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** ModSync's transfers: host to guest over a real Mux pair on stream 2, and the site download's redirect and hash rules. */
@Timeout(30)
public class ModSyncTransferTest {
    @TempDir
    Path dir;

    private ModSyncEntry jar(Path file, int bytes, String source, String url) throws IOException {
        byte[] data = new byte[bytes];
        new Random(bytes).nextBytes(data);
        Files.createDirectories(file.getParent());
        Files.write(file, data);
        return new ModSyncEntry("m", "M", "1", file.getFileName().toString(), bytes, "", ModSyncHash.sha512(file), source, url);
    }

    /** Host and guest ends of one link; the host serves until its stream ends, and a protocol error ends the link as HostSession does. */
    private ModSyncClient link(ModSyncServer server) throws IOException {
        PipedOutputStream hostOut = new PipedOutputStream(), guestOut = new PipedOutputStream();
        Mux host = new Mux(new PipedInputStream(guestOut, 64 * 1024), hostOut);
        Mux guest = new Mux(new PipedInputStream(hostOut, 64 * 1024), guestOut);
        Thread serving = new Thread(() -> {
            try {
                server.serve(host.in(P2PSession.MODSYNC), host.out(P2PSession.MODSYNC));
            } catch (IOException e) {
                host.close();
            }
        }, "modsync-test-host");
        serving.setDaemon(true);
        serving.start();
        return new ModSyncClient(guest.in(P2PSession.MODSYNC), guest.out(P2PSession.MODSYNC));
    }

    private void assertNoFiles(Path folder) throws IOException {
        if (!Files.exists(folder)) return;
        try (Stream<Path> files = Files.list(folder)) {
            assertEquals(List.of(), files.toList());
        }
    }

    @Test
    void guestGetsTheListAndAJarLargerThanTheFlowWindow() throws Exception {
        Path hostJar = dir.resolve("host/big mod.jar");
        ModSyncEntry entry = jar(hostJar, 700 * 1024, "", ""); // the Mux credit window is 512 KB
        ModSyncServer server = new ModSyncServer(List.of(new ModSyncServer.Shared(entry, hostJar)));
        ModSyncClient client = link(server);

        assertEquals(List.of(entry), client.list());
        Path target = dir.resolve("guest").resolve(entry.localName());
        long[] seen = {0};
        client.fetch(entry, target, n -> seen[0] += n);
        assertArrayEquals(Files.readAllBytes(hostJar), Files.readAllBytes(target));
        assertEquals(entry.size(), seen[0]);

        client.byeRelaunch();
        for (int i = 0; i < 200 && !server.guestRelaunches(); i++) Thread.sleep(10);
        assertTrue(server.guestRelaunches());
    }

    @Test
    void hostServesOnlyItsListedJars() throws Exception {
        Path hostJar = dir.resolve("host/listed.jar");
        ModSyncEntry listed = jar(hostJar, 5000, "", "");
        Files.writeString(dir.resolve("host/secret.txt"), "not a mod");
        ModSyncClient client = link(new ModSyncServer(List.of(new ModSyncServer.Shared(listed, hostJar))));

        ModSyncEntry other = new ModSyncEntry("x", "X", "1", "secret.jar", 9, "", "e".repeat(128), "", "");
        IOException refused = assertThrows(IOException.class, () -> client.fetch(other, dir.resolve("guest/secret.jar"), null));
        assertTrue(refused.getMessage().contains("not shared"), refused.getMessage());
        assertNoFiles(dir.resolve("guest"));
        client.fetch(listed, dir.resolve("guest/listed.jar"), null); // the link still works after a refusal
        assertTrue(ModSyncHash.matches(dir.resolve("guest/listed.jar"), listed));
    }

    @Test
    void aJarThatChangedOnTheHostFailsTheHashCheckAndLeavesNoFile() throws Exception {
        Path hostJar = dir.resolve("host/swapped.jar");
        ModSyncEntry entry = jar(hostJar, 5000, "", "");
        byte[] other = Files.readAllBytes(hostJar);
        other[100] ^= 1; // same size, one bit different
        Files.write(hostJar, other);
        ModSyncClient client = link(new ModSyncServer(List.of(new ModSyncServer.Shared(entry, hostJar))));

        IOException failed = assertThrows(IOException.class, () -> client.fetch(entry, dir.resolve("guest/swapped.jar"), null));
        assertTrue(failed.getMessage().contains("hash check failed"), failed.getMessage());
        assertNoFiles(dir.resolve("guest"));
    }

    @Test
    void aGuestAskingForTheSameJarAgainAndAgainLosesTheLink() throws Exception {
        Path hostJar = dir.resolve("host/again.jar");
        ModSyncEntry entry = jar(hostJar, 2000, "", "");
        ModSyncClient client = link(new ModSyncServer(List.of(new ModSyncServer.Shared(entry, hostJar))));
        for (int i = 0; i < ModSyncServer.MAX_SENDS; i++) client.fetch(entry, dir.resolve("guest/again.jar"), null);
        assertThrows(IOException.class, () -> client.fetch(entry, dir.resolve("guest/again.jar"), null));
    }

    /** A fake site: address -> (status, Location or body). */
    private static ModSyncDownloader.Http site(Map<String, Object[]> pages, List<String> asked) {
        return uri -> {
            asked.add(uri.toString());
            Object[] page = pages.get(uri.toString());
            if (page == null) return new ModSyncDownloader.Response(404, null, new ByteArrayInputStream(new byte[0]));
            return new ModSyncDownloader.Response((Integer) page[0], page[1] instanceof String s ? s : null,
                    new ByteArrayInputStream(page[1] instanceof byte[] b ? b : new byte[0]));
        };
    }

    @Test
    void siteDownloadFollowsARedirectOnlyWithinTheSite() throws Exception {
        String edge = "https://edge.forgecdn.net/files/1/2/m.jar", media = "https://mediafilez.forgecdn.net/files/1/2/m.jar";
        ModSyncEntry entry = jar(dir.resolve("src/m.jar"), 3000, "curseforge", edge);
        byte[] bytes = Files.readAllBytes(dir.resolve("src/m.jar"));
        Path target = dir.resolve("guest/m.jar");

        List<String> asked = new ArrayList<>();
        ModSyncDownloader.download(entry, target, site(Map.of(edge, new Object[] {302, media}, media, new Object[] {200, bytes}), asked), null);
        assertTrue(ModSyncHash.matches(target, entry));
        assertEquals(List.of(edge, media), asked);

        Files.delete(target);
        asked.clear();
        String evil = "https://evil.example/m.jar";
        IOException refused = assertThrows(IOException.class, () -> ModSyncDownloader.download(entry, target,
                site(Map.of(edge, new Object[] {302, evil}, evil, new Object[] {200, bytes}), asked), null));
        assertTrue(refused.getMessage().contains("redirect"), refused.getMessage());
        assertEquals(List.of(edge), asked, "the other host is never contacted");
        assertNoFiles(dir.resolve("guest"));
    }

    @Test
    void siteDownloadKeepsOnlyTheExactFile() throws Exception {
        String url = "https://cdn.modrinth.com/data/a/versions/b/m.jar";
        ModSyncEntry entry = jar(dir.resolve("src/m.jar"), 3000, "modrinth", url);
        byte[] bytes = Files.readAllBytes(dir.resolve("src/m.jar"));
        Path target = dir.resolve("guest/m.jar");
        List<String> asked = new ArrayList<>();

        byte[] wrong = bytes.clone();
        wrong[0] ^= 1;
        assertThrows(IOException.class, () -> ModSyncDownloader.download(entry, target, site(Map.of(url, new Object[] {200, wrong}), asked), null));
        byte[] longer = new byte[bytes.length + 1];
        assertThrows(IOException.class, () -> ModSyncDownloader.download(entry, target, site(Map.of(url, new Object[] {200, longer}), asked), null));
        assertThrows(IOException.class, () -> ModSyncDownloader.download(entry, target, site(Map.of(), asked), null)); // 404
        assertNoFiles(dir.resolve("guest"));

        ModSyncEntry elsewhere = new ModSyncEntry("m", "M", "1", "m.jar", 3000, "", entry.sha512(), "modrinth", "https://evil.example/m.jar");
        asked.clear();
        assertThrows(IOException.class, () -> ModSyncDownloader.download(elsewhere, target, site(Map.of(), asked), null));
        assertEquals(List.of(), asked, "an address off the site is never contacted");

        ModSyncDownloader.download(entry, target, site(Map.of(url, new Object[] {200, bytes}), asked), null);
        assertTrue(ModSyncHash.matches(target, entry));
    }
}
