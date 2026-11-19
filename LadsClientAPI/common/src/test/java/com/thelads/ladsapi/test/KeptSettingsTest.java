package com.thelads.ladsapi.test;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.session.KeptSettings;
import com.thelads.ladsapi.session.LadsSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** /ladsapi set --keep, players.yml, clear and kept scopes across logins (plugin 1.4.0). */
public class KeptSettingsTest {
    private static final String HELLO = "{\"t\":\"hello\",\"v\":1,\"lads\":\"1.13.28\",\"mc\":\"26.3\"}";
    private Path dir;
    private FakePlatform platform;
    private LadsCore core;
    private FakePlayerHandle steve;

    @BeforeEach
    public void setup(@TempDir Path tempDir) {
        dir = tempDir;
        platform = new FakePlatform("paper", false, tempDir);
        core = LadsCore.start(platform);
        assertNotNull(core);
        steve = new FakePlayerHandle(UUID.randomUUID(), "Steve", "world");
        platform.addPlayer(steve);
        join(steve);
    }

    @AfterEach
    public void teardown() {
        if (core != null) core.stop();
    }

    private void join(FakePlayerHandle p) {
        core.join(p);
        core.receive(p, HELLO.getBytes(StandardCharsets.UTF_8));
    }

    /** Quit and log in again: a new session, as on a Paper backend after a server switch or a relog. */
    private void relog() {
        core.quit(steve.uuid());
        join(steve);
    }

    private String policy(FakePlayerHandle p) {
        List<String> all = p.getSentStrings();
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).contains("\"t\":\"policy\"")) return all.get(i);
        fail("no policy sent");
        return null;
    }

    private String run(String... args) {
        return String.join(" | ", core.command(args, true));
    }

    private String players() throws IOException {
        Path f = dir.resolve("players.yml");
        return Files.isRegularFile(f) ? Files.readString(f) : "";
    }

    private static final String PARTICLES = "\"Minecraft\":{\"particles\":{\"value\":\"all\",\"lock\":true}}";

    @Test
    public void temporarySetIsGoneAfterQuitAndRejoin() throws IOException {
        assertTrue(run("set", "Steve", "Minecraft.particles", "all").contains("until logout"));
        assertTrue(policy(steve).contains(PARTICLES), policy(steve));
        assertEquals("", players(), "a temporary set writes nothing");
        relog();
        assertFalse(policy(steve).contains("particles"), policy(steve));
    }

    @Test
    public void keptSetSurvivesRejoinAndIsInPlayersYml() throws IOException {
        String res = run("set", "Steve", "Minecraft.particles", "--keep", "all");
        assertTrue(res.contains("kept"), res);
        assertTrue(policy(steve).contains(PARTICLES), policy(steve));
        String yml = players();
        assertTrue(yml.contains(steve.uuid().toString()) && yml.contains("name: Steve") && yml.contains("particles"), yml);
        assertFalse(Files.exists(dir.resolve("players.yml.tmp")), "temp file moved away");
        relog();
        assertTrue(policy(steve).contains(PARTICLES), policy(steve));
        // a flag at the very end works too
        assertTrue(run("set", "Steve", "Minimap", "block", "--keep").contains("kept"));
        relog();
        assertTrue(policy(steve).contains("\"Minimap\":\"block\""), policy(steve));
    }

    @Test
    public void temporarySetOnSameOptionWinsWhileSessionLasts() {
        run("set", "Steve", "Minecraft.particles", "--keep", "all");
        run("set", "Steve", "Minecraft.particles", "minimal");
        assertTrue(policy(steve).contains("\"particles\":{\"value\":\"minimal\""), policy(steve));
        relog();
        assertTrue(policy(steve).contains("\"particles\":{\"value\":\"all\""), policy(steve));
    }

    @Test
    public void clearRemovesTemporaryAndKept() throws IOException {
        run("set", "Steve", "Minecraft.particles", "--keep", "all");
        run("set", "Steve", "Minimap", "block");
        String res = run("clear", "Steve");
        assertTrue(res.contains("temporary") && res.contains("kept"), res);
        assertFalse(policy(steve).contains("particles") || policy(steve).contains("Minimap"), policy(steve));
        assertFalse(players().contains("particles"), players());
        relog();
        assertFalse(policy(steve).contains("particles"), policy(steve));
        assertTrue(run("clear", "Steve").contains("no"), "second clear has nothing to clear");
    }

    @Test
    public void clearOneModuleKeepsTheOthers() throws IOException {
        run("set", "Steve", "Minecraft.particles", "--keep", "all");
        run("set", "Steve", "Minimap", "--keep", "block");
        run("clear", "Steve", "minimap");
        assertFalse(players().contains("Minimap"), players());
        assertTrue(players().contains("particles"), players());
        assertTrue(run("clear", "Steve", "Minecraft.particles").contains("module name"));
    }

    @Test
    public void offlineClearByNameAndByUuid() throws IOException {
        run("set", "Steve", "Minimap", "--keep", "block");
        core.quit(steve.uuid());
        platform.removePlayer(steve);
        assertTrue(run("clear", "steve").contains("kept"), "by last known name");
        assertFalse(players().contains("Minimap"), players());

        platform.addPlayer(steve);
        join(steve);
        run("set", "Steve", "Minimap", "--keep", "block");
        core.quit(steve.uuid());
        platform.removePlayer(steve);
        assertTrue(run("clear", steve.uuid().toString()).contains("kept"), "by UUID");
        assertFalse(core.sessionManager().kept().has(steve.uuid()));
        assertTrue(run("clear", "Nobody").contains("not found"));
    }

    @Test
    public void keptScopeComesBackAndOffRemovesIt() throws IOException {
        assertTrue(run("scope", "Steve", "parkour", "--keep").contains("kept"));
        assertTrue(players().contains("parkour"), players());
        relog();
        assertTrue(core.sessionManager().getSessions().get(steve.uuid()).scopeNames().contains("parkour"));
        assertTrue(run("inspect", "Steve").contains("Kept scopes"));
        assertTrue(run("scope", "Steve", "parkour", "off").contains("no longer kept"));
        assertFalse(players().contains("parkour"), players());
        relog();
        assertFalse(core.sessionManager().getSessions().get(steve.uuid()).scopeNames().contains("parkour"));
        // timed scopes cannot be kept
        assertTrue(run("scope", "Steve", "parkour", "30", "--keep").contains("cannot be kept"));
        assertFalse(core.sessionManager().kept().has(steve.uuid()));
    }

    @Test
    public void temporaryScopeIsNotKept() throws IOException {
        run("scope", "Steve", "event");
        assertEquals("", players());
        relog();
        assertTrue(core.sessionManager().getSessions().get(steve.uuid()).scopeNames().isEmpty());
    }

    @Test
    public void inspectListsKeptApartFromTemporary() {
        run("set", "Steve", "Minimap", "--keep", "block");
        run("set", "Steve", "Fullbright", "allow");
        String out = run("inspect", "Steve");
        assertTrue(out.contains("Temporary overrides") && out.contains("[Fullbright]"), out);
        assertTrue(out.contains("Kept overrides (players.yml)") && out.contains("[Minimap]"), out);
    }

    @Test
    public void playersYmlRoundTripsThroughARestart() {
        run("set", "Steve", "Minecraft.particles", "--keep", "all");
        run("set", "Steve", "Crosshair.Size", "--keep", "max=40");
        run("scope", "Steve", "parkour", "--keep");
        core.stop();
        core = LadsCore.start(platform);
        assertNotNull(core);
        steve.getSentPayloads().clear();
        join(steve);
        String p = policy(steve);
        assertTrue(p.contains(PARTICLES) && p.contains("\"Size\":{\"max\":40}"), p);
        assertTrue(core.sessionManager().getSessions().get(steve.uuid()).scopeNames().contains("parkour"));
    }

    @Test
    public void reloadReadsPlayersYmlAgain() throws IOException {
        run("set", "Steve", "Minimap", "--keep", "block");
        String yml = players().replace("block", "allow");
        Files.writeString(dir.resolve("players.yml"), yml);
        run("reload");
        assertTrue(policy(steve).contains("\"Minimap\":\"allow\""), policy(steve));
    }

    @Test
    public void hostileInputIsRefused() throws IOException {
        // names that rules.yml would never hold
        assertTrue(run("set", "Steve", "Bad\nName", "--keep", "block").contains("Bad module"));
        assertTrue(run("set", "Steve", "Minecraft.bad\u0000opt", "--keep", "1").contains("Bad option"));
        assertTrue(run("scope", "Steve", "Bad Scope!", "--keep").startsWith("§c"));
        assertEquals("", players(), "nothing was written");
        assertTrue(run("set", "Steve", "Minimap", "--keep", "maybe").contains("Invalid state"));
    }

    @Test
    public void hostilePlayersYmlLoadsOnlyWhatIsSane() throws IOException {
        UUID good = UUID.randomUUID();
        String longText = "x".repeat(1000);
        String yml = ""
                + "players:\n"
                + "  not-a-uuid:\n    name: X\n    rules: {Minimap: block}\n"
                + "  " + good + ":\n"
                + "    name: Alex\n"
                + "    rules:\n"
                + "      Minimap: block\n"
                + "      \"Evil\\nModule\": block\n"
                + "      Weird: maybe\n"
                + "      Minecraft:\n"
                + "        particles: {value: all, lock: true}\n"
                + "        huge: {value: " + longText + "}\n"
                + "        nested: {value: [1, 2]}\n"
                + "        \"bad\\u0000name\": {value: 1}\n"
                + "    scopes: [parkour, \"Bad Scope!\", " + longText + "]\n"
                + "  " + UUID.randomUUID() + ": 12\n";
        Files.writeString(dir.resolve("players.yml"), yml);
        KeptSettings kept = new KeptSettings(dir, core.rules(), platform.logger());
        assertEquals(List.of("parkour"), kept.scopes(good));
        assertEquals("block", kept.rules(good).get("Minimap").getState());
        assertNull(kept.rules(good).get("Evil\nModule"));
        assertNull(kept.rules(good).get("Weird"), "unknown state dropped, module empty");
        var mc = kept.rules(good).get("Minecraft").getOptions();
        assertEquals(java.util.Set.of("particles"), mc.keySet());
        assertNull(kept.find("X"));
        // a java object tag must not be built
        Files.writeString(dir.resolve("players.yml"), "players: !!javax.script.ScriptEngineManager []\n");
        KeptSettings tagged = new KeptSettings(dir, core.rules(), platform.logger());
        assertFalse(tagged.has(good));
        // a broken file keeps what is in memory
        Files.writeString(dir.resolve("players.yml"), "players: [unclosed\n");
        kept.load();
        assertEquals(List.of("parkour"), kept.scopes(good));
    }

    @Test
    public void worldKeyWithStarMatchesDynamicWorlds() {
        core.rules().parseYaml(new java.util.LinkedHashMap<>(java.util.Map.of("worlds", java.util.Map.of(
                "witp_*", java.util.Map.of("Minimap", "block"),
                "witp_tpls_9", java.util.Map.of("Minimap", "allow")))));
        steve.setWorld("witp_tpls_61");
        core.worldChanged(steve);
        assertTrue(policy(steve).contains("\"Minimap\":\"block\""), policy(steve));
        steve.setWorld("witp_tpls_9");
        core.worldChanged(steve);
        assertTrue(policy(steve).contains("\"Minimap\":\"allow\""), policy(steve));
        steve.setWorld("lobby");
        core.worldChanged(steve);
        assertFalse(policy(steve).contains("Minimap"), policy(steve));
    }

    @Test
    public void tabCompleteOffersKeep() {
        List<String> s = com.thelads.ladsapi.command.LadsCommandHandler.suggest(core, new String[]{"set", "Steve", "Minimap", "--k"}, List.of("Steve"));
        assertEquals(List.of("--keep"), s);
        List<String> sc = com.thelads.ladsapi.command.LadsCommandHandler.suggest(core, new String[]{"scope", "Steve", "parkour", ""}, List.of("Steve"));
        assertTrue(sc.contains("--keep") && sc.contains("off"), sc.toString());
        List<String> after = com.thelads.ladsapi.command.LadsCommandHandler.suggest(core, new String[]{"set", "Steve", "--keep", "Minimap", "b"}, List.of("Steve"));
        assertTrue(after.contains("block"), after.toString());
    }

    @SuppressWarnings("unused")
    private LadsSession session() {
        return core.sessionManager().getSessions().get(steve.uuid());
    }
}
