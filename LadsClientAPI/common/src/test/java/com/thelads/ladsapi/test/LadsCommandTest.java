package com.thelads.ladsapi.test;

import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.api.LadsClientAPI;
import com.thelads.ladsapi.api.LadsPlayer;
import com.thelads.ladsapi.rules.ModuleRule;
import com.thelads.ladsapi.rules.RuleOption;
import com.thelads.ladsapi.session.LadsSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LadsCommandTest {
    private FakePlatform platform;
    private LadsCore core;
    private FakePlayerHandle player;

    @BeforeEach
    public void setup(@TempDir Path tempDir) {
        platform = new FakePlatform("paper", false, tempDir);
        core = LadsCore.start(platform);
        assertNotNull(core);

        player = new FakePlayerHandle(UUID.randomUUID(), "Steve", "world");
        platform.addPlayer(player);
        core.join(player);

        // Send hello to complete handshake
        String hello = "{\"t\":\"hello\",\"v\":1,\"lads\":\"1.9.13\",\"mc\":\"26.3\"}";
        core.receive(player, hello.getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    public void teardown() {
        if (core != null) {
            core.stop();
        }
    }

    @Test
    public void testSetMultiWordModule() {
        List<String> res = core.command(new String[]{"set", "Steve", "Crosshair", "Tweaks", "block"}, true);
        assertEquals(1, res.size());
        assertTrue(res.get(0).contains("Set module Crosshair Tweaks state to block"));

        Optional<LadsPlayer> lp = LadsClientAPI.get().player(player.uuid());
        assertTrue(lp.isPresent());
        LadsSession session = core.sessionManager().getSessions().get(player.uuid());
        ModuleRule rule = session.getApiOverrides().get("Crosshair Tweaks");
        assertNotNull(rule);
        assertEquals("block", rule.getState());
    }

    @Test
    public void testSetMultiWordModuleAndOption() {
        List<String> res = core.command(new String[]{"set", "Steve", "Crosshair", "Tweaks.Outline", "Color", "value=#FF0000"}, true);
        assertEquals(1, res.size());
        assertTrue(res.get(0).contains("Set Crosshair Tweaks.Outline Color to value=#FF0000"));

        LadsSession session = core.sessionManager().getSessions().get(player.uuid());
        ModuleRule rule = session.getApiOverrides().get("Crosshair Tweaks");
        assertNotNull(rule);
        RuleOption opt = rule.getOption("Outline Color");
        assertNotNull(opt);
        assertEquals("#FF0000", opt.getValue());
    }

    @Test
    public void testSetModuleWithDotsAndOptions() {
        List<String> res = core.command(new String[]{"set", "Steve", "1.7", "Animations.1.7", "sword", "block", "pose", "value=false"}, true);
        assertEquals(1, res.size());
        assertTrue(res.get(0).contains("Set 1.7 Animations.1.7 sword block pose"));

        LadsSession session = core.sessionManager().getSessions().get(player.uuid());
        ModuleRule rule = session.getApiOverrides().get("1.7 Animations");
        assertNotNull(rule);
        RuleOption opt = rule.getOption("1.7 sword block pose");
        assertNotNull(opt);
        assertEquals(false, opt.getValue());
    }

    @Test
    public void testSetUnknownModuleAppliesWithWarning() {
        List<String> res = core.command(new String[]{"set", "Steve", "BogusModule", "block"}, true);
        assertTrue(res.stream().anyMatch(s -> s.contains("not in schema")));
        assertTrue(res.stream().anyMatch(s -> s.contains("Set module BogusModule state to block")));

        LadsSession session = core.sessionManager().getSessions().get(player.uuid());
        assertTrue(session.getApiOverrides().containsKey("BogusModule"));
        assertEquals("block", session.getApiOverrides().get("BogusModule").getState());
    }

    @Test
    public void testSetUnknownOptionAppliesWithWarning() {
        List<String> res = core.command(new String[]{"set", "Steve", "Fullbright.BogusOption", "value=1"}, true);
        assertTrue(res.stream().anyMatch(s -> s.contains("not in schema")));
        assertTrue(res.stream().anyMatch(s -> s.contains("Set Fullbright.BogusOption to value=1")));
    }
    @Test
    public void testSuggestSubcommandsAndPlayers() {
        List<String> subs = com.thelads.ladsapi.command.LadsCommandHandler.suggest(core, new String[]{""}, List.of("Steve", "Alex"));
        assertTrue(subs.contains("set"));
        assertTrue(subs.contains("reload"));

        List<String> players = com.thelads.ladsapi.command.LadsCommandHandler.suggest(core, new String[]{"set", "S"}, List.of("Steve", "Alex"));
        assertEquals(List.of("Steve"), players);
    }

    @Test
    public void testSuggestModulesAndRules() {
        List<String> targets = com.thelads.ladsapi.command.LadsCommandHandler.suggest(core, new String[]{"set", "Steve", "Full"}, List.of("Steve"));
        assertTrue(targets.contains("Fullbright"));
        assertTrue(targets.contains("Fullbright.Brightness"));

        List<String> modRules = com.thelads.ladsapi.command.LadsCommandHandler.suggest(core, new String[]{"set", "Steve", "Fullbright", ""}, List.of("Steve"));
        assertEquals(List.of("allow", "block", "on"), modRules);

        List<String> optRules = com.thelads.ladsapi.command.LadsCommandHandler.suggest(core, new String[]{"set", "Steve", "Fullbright.Brightness", ""}, List.of("Steve"));
        assertEquals(List.of("max=", "min=", "value="), optRules);
    }

    @Test
    public void testSuggestMultiWordModules() {
        List<String> res1 = com.thelads.ladsapi.command.LadsCommandHandler.suggest(core, new String[]{"set", "Steve", "Crosshair", "T"}, List.of("Steve"));
        assertEquals(List.of("Tweaks"), res1);

        List<String> res2 = com.thelads.ladsapi.command.LadsCommandHandler.suggest(core, new String[]{"set", "Steve", "Crosshair", "Tweaks", ""}, List.of("Steve"));
        assertEquals(List.of("allow", "block", "on"), res2);
    }
}
