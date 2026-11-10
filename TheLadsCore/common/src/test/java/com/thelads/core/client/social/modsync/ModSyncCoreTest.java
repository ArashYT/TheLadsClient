package com.thelads.core.client.social.modsync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** ModSync's pure parts: the list (parse, hash, diff), the source choice and the hash check. */
public class ModSyncCoreTest {
    private static final String A = "a".repeat(128), B = "b".repeat(128), C = "c".repeat(128);

    private static ModSyncEntry mod(String id, String version, String sha512) {
        return new ModSyncEntry(id, id, version, id + "-" + version + ".jar", 1000, "", sha512, "", "");
    }

    private static ModSyncEntry from(String source, String url) {
        return new ModSyncEntry("m", "M", "1", "m.jar", 1000, "", A, source, url);
    }

    @Test
    void listSurvivesJsonAndDropsARepeatedJar() {
        ModSyncEntry site = new ModSyncEntry("lithium", "Lithium", "0.11", "lithium.jar", 5, "f".repeat(40), A, "modrinth",
                "https://cdn.modrinth.com/data/x/versions/y/lithium.jar");
        List<ModSyncEntry> back = ModSyncList.parse(ModSyncList.toJson(List.of(site, mod("own", "1", B), site)));
        assertEquals(List.of(site, mod("own", "1", B)), back);
    }

    @Test
    void hashIgnoresOrderAndIsEmptyWithoutMods() {
        assertEquals("", ModSyncList.hash(List.of()));
        String ab = ModSyncList.hash(List.of(mod("a", "1", A), mod("b", "1", B)));
        assertEquals(32, ab.length());
        assertEquals(ab, ModSyncList.hash(List.of(mod("b", "1", B), mod("a", "1", A))));
        assertNotEquals(ab, ModSyncList.hash(List.of(mod("a", "1", A))));
    }

    @Test
    void aBadEntryRefusesTheWholeList() {
        assertRefused(new ModSyncEntry("m", "M", "1", "m.jar", 1000, "", "abc", "", ""), "no sha512");
        assertRefused(new ModSyncEntry("m", "M", "1", "m.jar", 0, "", A, "", ""), "bad size");
        assertRefused(new ModSyncEntry("m", "M", "1", "m.jar", ModSyncEntry.MAX_JAR + 1, "", A, "", ""), "bad size");
        assertRefused(new ModSyncEntry("m", "M", "1", "m.exe", 1000, "", A, "", ""), "not a jar name");
        assertRefused(new ModSyncEntry("theladscore", "M", "1", "m.jar", 1000, "", A, "", ""), "reserved mod id");
        assertRefused(new ModSyncEntry("m", "M", "1", "m.jar", 1000, "", A, "mysite", ""), "unknown source");
        assertThrows(IllegalArgumentException.class, () -> ModSyncList.parse("[]"));
        assertThrows(IllegalArgumentException.class, () -> ModSyncList.parse("x".repeat(ModSyncList.MAX_JSON + 1)));
    }

    private static void assertRefused(ModSyncEntry entry, String why) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ModSyncList.parse(ModSyncList.toJson(List.of(mod("ok", "1", B), entry))));
        assertTrue(e.getMessage().contains(why), e.getMessage());
    }

    @Test
    void missingIsWhatTheGuestHasNeitherAsAJarNorLoaded() {
        List<ModSyncEntry> host = List.of(mod("same-jar", "1", A), mod("loaded", "2.0", B), mod("other-version", "3.0", C),
                mod("absent", "1", "d".repeat(128)));
        List<ModSyncEntry> missing = ModSyncList.missing(host, Set.of(A), Map.of("loaded", "2.0", "other-version", "2.9"));
        assertEquals(List.of("other-version", "absent"), missing.stream().map(ModSyncEntry::id).toList());
        assertEquals(List.of(), ModSyncList.missing(List.of(), Set.of(), Map.of()));
    }

    @Test
    void onlyTheSitesOwnCdnCountsAsASite() {
        assertEquals(ModSyncSource.Kind.SITE, ModSyncSource.choose(from("modrinth", "https://cdn.modrinth.com/data/a/versions/b/m.jar")));
        assertEquals(ModSyncSource.Kind.SITE, ModSyncSource.choose(from("curseforge", "https://edge.forgecdn.net/files/1/2/m.jar")));
        assertEquals(ModSyncSource.Kind.SITE, ModSyncSource.choose(from("curseforge", "https://mediafilez.forgecdn.net/files/1/2/m.jar")));
        for (ModSyncEntry p2p : List.of(from("", ""), from("modrinth", ""),
                from("", "https://cdn.modrinth.com/data/a/versions/b/m.jar"),              // no source named
                from("modrinth", "http://cdn.modrinth.com/data/a/versions/b/m.jar"),      // not https
                from("modrinth", "https://edge.forgecdn.net/files/1/2/m.jar"),            // another site's CDN
                from("modrinth", "https://cdn.modrinth.com.evil.example/m.jar"),
                from("modrinth", "https://evil.example/cdn.modrinth.com/m.jar"),
                from("modrinth", "https://user@cdn.modrinth.com/m.jar"),
                from("modrinth", "https://cdn.modrinth.com:8443/m.jar"),
                from("modrinth", "https://cdn.modrinth.com/m.jar?to=evil"),
                from("curseforge", "https://cdn.modrinth.com/data/a/versions/b/m.jar"),
                from("modrinth", "not a url"))) {
            assertEquals(ModSyncSource.Kind.P2P, ModSyncSource.choose(p2p), p2p.source() + " " + p2p.url());
        }
    }

    @Test
    void planSplitsBySourceAndAsksForTrustOnlyForHostJars() {
        ModSyncEntry site = from("modrinth", "https://cdn.modrinth.com/data/a/versions/b/m.jar"), own = mod("own", "1", B);
        ModSyncSource.Plan plan = ModSyncSource.plan(List.of(site, own));
        assertEquals(List.of(site), plan.site());
        assertEquals(List.of(own), plan.p2p());
        assertTrue(plan.needsTrust());
        assertEquals(2000, plan.bytes());
        assertFalse(ModSyncSource.plan(List.of(site)).needsTrust());
        assertTrue(ModSyncSource.plan(List.of()).isEmpty());
    }

    @Test
    void hashCheckNeedsTheExactBytes(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("m.jar");
        Files.write(jar, new byte[] {1, 2, 3, 4});
        String sha = ModSyncHash.sha512(jar);
        assertEquals(128, sha.length());
        ModSyncEntry entry = new ModSyncEntry("m", "M", "1", "m.jar", 4, "", sha, "", "");
        assertTrue(ModSyncHash.matches(jar, entry));
        assertFalse(ModSyncHash.matches(jar, new ModSyncEntry("m", "M", "1", "m.jar", 5, "", sha, "", ""))); // size
        assertFalse(ModSyncHash.matches(dir.resolve("absent.jar"), entry));
        Files.write(jar, new byte[] {1, 2, 3, 5});
        assertFalse(ModSyncHash.matches(jar, entry)); // one byte changed
    }

    @Test
    void localNameNeverCarriesTheHostsPath() {
        ModSyncEntry evil = new ModSyncEntry("m", "M", "1", "..\\..\\mods/evil mod (1).jar", 10, "", A, "", "");
        String name = evil.localName();
        assertEquals("aaaaaaaaaaaa-.._.._mods_evil_mod__1_.jar", name);
        assertFalse(name.contains("/") || name.contains("\\") || name.contains(" "));
        assertEquals(name, Path.of(name).getFileName().toString());
    }
}
