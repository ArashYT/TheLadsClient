package com.thelads.core.client.social.modsync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.social.p2p.Mux;
import com.thelads.core.client.social.p2p.P2PSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The guest's whole sync against a host that serves its game folder, over a real Mux pair: what is fetched, asked and written. */
@Timeout(30)
public class ModSyncFlowTest {
    private static final UUID HOST = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String SITE_URL = "https://cdn.modrinth.com/data/AANobbMI/versions/1/site-mod.jar";

    @TempDir
    Path dir;
    private Path hostDir, guestDir;
    private ModSyncEntry siteMod, hostOnly;
    private ModSyncServer server;
    private final List<String> statuses = new ArrayList<>();
    private final List<List<ModSyncEntry>> asked = new ArrayList<>();
    private boolean trustAnswer = true, siteDown;

    @BeforeEach
    void hostWithTwoMods() throws IOException {
        hostDir = dir.resolve("host");
        guestDir = dir.resolve("guest");
        Files.createDirectories(guestDir);
        siteMod = jar(hostDir.resolve("mods/site-mod.jar"), "sitemod", 4000, ModSyncEntry.MODRINTH, SITE_URL);
        hostOnly = jar(hostDir.resolve("mods/My Own Mod.jar"), "ownmod", 9000, "", "");
        writeList(hostDir, List.of(siteMod, hostOnly));
    }

    private static ModSyncEntry jar(Path file, String id, int bytes, String source, String url) throws IOException {
        byte[] data = new byte[bytes];
        new Random(bytes).nextBytes(data);
        Files.createDirectories(file.getParent());
        Files.write(file, data);
        return new ModSyncEntry(id, id + " name", "1.0", file.getFileName().toString(), bytes, "", ModSyncHash.sha512(file), source, url);
    }

    private static void writeList(Path gameDir, List<ModSyncEntry> mods) throws IOException {
        Path list = gameDir.resolve(ModSyncFlow.LIST_FILE);
        Files.createDirectories(list.getParent());
        Files.writeString(list, ModSyncList.toJson(mods));
    }

    private final ModSyncFlow.Ui ui = new ModSyncFlow.Ui() {
        @Override public void status(String text) {
            statuses.add(text);
        }

        @Override public boolean trust(List<ModSyncEntry> jars) {
            asked.add(jars);
            return trustAnswer;
        }
    };

    /** The site: the host's jar under its listed address, or 404 for everything while it is down. */
    private ModSyncDownloader.Response site(java.net.URI uri) throws IOException {
        if (siteDown || !uri.toString().equals(SITE_URL)) return new ModSyncDownloader.Response(404, null, new ByteArrayInputStream(new byte[0]));
        return new ModSyncDownloader.Response(200, null, new ByteArrayInputStream(Files.readAllBytes(hostDir.resolve("mods/site-mod.jar"))));
    }

    /** One join: the host serves what ModSyncFlow.shared finds in its folder, the guest runs its flow on stream 2 of the same link. */
    private ModSyncFlow.Result join(Map<String, String> loaded, boolean relaunched) throws IOException {
        PipedOutputStream hostOut = new PipedOutputStream(), guestOut = new PipedOutputStream();
        Mux host = new Mux(new PipedInputStream(guestOut, 64 * 1024), hostOut);
        Mux guest = new Mux(new PipedInputStream(hostOut, 64 * 1024), guestOut);
        server = new ModSyncServer(ModSyncFlow.shared(hostDir));
        Thread serving = new Thread(() -> {
            try {
                server.serve(host.in(P2PSession.MODSYNC), host.out(P2PSession.MODSYNC));
            } catch (IOException e) {
                host.close();
            }
        }, "modsync-flow-host");
        serving.setDaemon(true);
        serving.start();
        return ModSyncFlow.guest(guestDir, HOST, "Survival", "HostPlayer", guest.in(P2PSession.MODSYNC), guest.out(P2PSession.MODSYNC), loaded, this::site, relaunched, ui);
    }

    private Path synced(ModSyncEntry m) {
        return guestDir.resolve(ModSyncFlow.FOLDER).resolve(HOST.toString()).resolve(m.localName());
    }

    private List<String> files(Path folder) throws IOException {
        if (!Files.exists(folder)) return List.of();
        try (Stream<Path> s = Files.list(folder)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void missingModsComeFromTheSiteAndTheHostThenTheRestartIsAsked() throws Exception {
        assertEquals(ModSyncFlow.Result.RELAUNCH, join(Map.of(), false));

        assertTrue(ModSyncHash.matches(synced(siteMod), siteMod));
        assertTrue(ModSyncHash.matches(synced(hostOnly), hostOnly));
        assertEquals(List.of(List.of(hostOnly)), asked, "one question, only for the jar that is on no site");
        assertEquals(List.of(), files(guestDir.resolve("mods")), "the guest's own mods folder is never written");

        JsonObject request = JsonParser.parseString(Files.readString(guestDir.resolve(ModSyncFlow.RELAUNCH_FILE))).getAsJsonObject();
        assertEquals(HOST.toString(), request.get("host").getAsString());
        assertEquals("Survival", request.get("world").getAsString(), "names the launcher's new modpack");
        assertEquals("HostPlayer", request.get("owner").getAsString());
        JsonArray mods = request.getAsJsonArray("mods");
        assertEquals(2, mods.size());
        for (int i = 0; i < 2; i++) {
            ModSyncEntry m = i == 0 ? siteMod : hostOnly;
            JsonObject o = mods.get(i).getAsJsonObject();
            assertEquals(m.localName(), o.get("file").getAsString());
            assertEquals(m.size(), o.get("size").getAsLong());
            assertEquals(m.sha512(), o.get("sha512").getAsString());
        }
        for (int i = 0; i < 200 && !server.guestRelaunches(); i++) Thread.sleep(10);
        assertTrue(server.guestRelaunches(), "the host was told the guest comes back");
        assertEquals("Restarting the game with 2 mods", statuses.get(statuses.size() - 1));
    }

    @Test
    void trustIsAskedOncePerHost() throws Exception {
        join(Map.of(), false);
        Files.delete(synced(hostOnly)); // the jar is gone, the answer is remembered
        assertEquals(ModSyncFlow.Result.RELAUNCH, join(Map.of(), false));
        assertEquals(1, asked.size());
        assertTrue(ModSyncHash.matches(synced(hostOnly), hostOnly));
    }

    @Test
    void refusedTrustFetchesNothingFromTheHostAndAsksNoRestart() throws Exception {
        trustAnswer = false;
        assertEquals(ModSyncFlow.Result.CANCELLED, join(Map.of(), false));
        assertFalse(Files.exists(synced(hostOnly)));
        assertFalse(Files.exists(guestDir.resolve(ModSyncFlow.RELAUNCH_FILE)));
        assertFalse(server.guestRelaunches());
        trustAnswer = true; // a refusal is not remembered: the next join asks again
        assertEquals(ModSyncFlow.Result.RELAUNCH, join(Map.of(), false));
        assertEquals(2, asked.size());
    }

    @Test
    void aFailedSiteDownloadComesFromTheHostAfterTheQuestion() throws Exception {
        siteDown = true;
        assertEquals(ModSyncFlow.Result.RELAUNCH, join(Map.of(), false));
        assertEquals(1, asked.size());
        assertEquals(2, asked.get(0).size());
        assertTrue(ModSyncHash.matches(synced(siteMod), siteMod));
    }

    @Test
    void nothingMissingConnectsWithoutAFileOrAQuestion() throws Exception {
        // the same jars in the guest's own profile, or the same mod versions already loaded
        Files.createDirectories(guestDir.resolve("mods"));
        for (String name : List.of("site-mod.jar", "My Own Mod.jar")) Files.copy(hostDir.resolve("mods").resolve(name), guestDir.resolve("mods").resolve(name));
        writeList(guestDir, List.of(siteMod, hostOnly));
        assertEquals(ModSyncFlow.hash(ModSyncFlow.shared(hostDir)), ModSyncFlow.hash(ModSyncFlow.shared(guestDir)));
        assertEquals(ModSyncFlow.Result.CONNECT, join(Map.of(), false));

        Files.delete(guestDir.resolve(ModSyncFlow.LIST_FILE));
        assertEquals(ModSyncFlow.Result.CONNECT, join(Map.of("sitemod", "1.0", "ownmod", "1.0"), false));
        assertEquals(List.of(), asked);
        assertFalse(Files.exists(guestDir.resolve(ModSyncFlow.FOLDER)));
        assertFalse(Files.exists(guestDir.resolve(ModSyncFlow.RELAUNCH_FILE)));
    }

    @Test
    void aGameStartedWithTheHostsJarsNeverRestartsAgainForThem() throws Exception {
        assertEquals(ModSyncFlow.Result.RELAUNCH, join(Map.of(), false));
        Files.delete(guestDir.resolve(ModSyncFlow.RELAUNCH_FILE));
        // restarted with both jars, but the loader took another version of one: still missing, nothing new to fetch
        assertEquals(ModSyncFlow.Result.CONNECT, join(Map.of("sitemod", "1.0", "ownmod", "2.0"), true));
        assertFalse(Files.exists(guestDir.resolve(ModSyncFlow.RELAUNCH_FILE)));
        // a normal start later (own setup again): the jars are still there, so the restart needs no download and no question
        assertEquals(ModSyncFlow.Result.RELAUNCH, join(Map.of(), false));
        assertEquals(1, asked.size());
    }

    @Test
    void sharedListsOnlyJarsThatAreInTheModsFolderAsListed() throws Exception {
        assertEquals(List.of(siteMod, hostOnly), ModSyncFlow.shared(hostDir).stream().map(ModSyncServer.Shared::entry).toList());
        Files.write(hostDir.resolve("mods/site-mod.jar"), new byte[10]); // changed since the launcher listed it
        assertEquals(List.of(hostOnly), ModSyncFlow.shared(hostDir).stream().map(ModSyncServer.Shared::entry).toList());

        ModSyncEntry outside = new ModSyncEntry("x", "X", "1", "../outside.jar", 9000, "", hostOnly.sha512(), "", "");
        Files.copy(hostDir.resolve("mods/My Own Mod.jar"), hostDir.resolve("outside.jar"));
        writeList(hostDir, List.of(outside));
        assertEquals(List.of(), ModSyncFlow.shared(hostDir), "a listed name never leaves the mods folder");

        assertEquals(List.of(), ModSyncFlow.shared(null));
        assertEquals(List.of(), ModSyncFlow.shared(guestDir), "no list: nothing to share");
        assertEquals("", ModSyncFlow.hash(List.of()));
    }
}
