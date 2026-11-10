package com.thelads.ladsapi.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.ladsapi.api.LadsPlayer;
import com.thelads.ladsapi.config.LadsConfig;
import com.thelads.ladsapi.policy.PolicyBuilder;
import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.rules.ModuleRule;
import com.thelads.ladsapi.session.SessionManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Scopes: base -> world -> game mode -> group -> each scope (rules.yml, then its API rules) -> API overrides, later wins;
 * a scope that ends (by hand, by its timer) takes its rules with it and the next policy is the one from before.
 */
public class ScopeOrderTest {
    private static final String CLIENT_HELLO = "{\"t\":\"hello\",\"v\":1,\"lads\":\"1.13.9\",\"mc\":\"26.3\"}";

    private static String state(String policyJson, String module) {
        JsonObject modules = JsonParser.parseString(policyJson).getAsJsonObject().getAsJsonObject("modules");
        return modules.has(module) ? modules.get(module).getAsString() : null;
    }

    private static String type(String json) {
        return JsonParser.parseString(json).getAsJsonObject().get("t").getAsString();
    }

    private static Map<String, Map<String, ModuleRule>> scopes(String... names) {
        Map<String, Map<String, ModuleRule>> scopes = new LinkedHashMap<>();
        for (String name : names) scopes.put(name, new LinkedHashMap<>());
        return scopes;
    }

    @Test
    public void laterLayerWins() {
        LadsConfig config = new LadsConfig();
        LadsRules rules = new LadsRules();
        rules.parseYaml(Map.of(
            "modules", Map.of("Fullbright", "allow"),
            "worlds", Map.of("arena", Map.of("Fullbright", "block")),
            "gamemodes", Map.of("Creative", Map.of("Fullbright", "on")),
            "groups", Map.of("staff", Map.of("Fullbright", "block")),
            "scopes", Map.of("Duel", Map.of("modules", Map.of("Fullbright", "allow")), "event", Map.of("Fullbright", "block"))));

        FakePlayerHandle player = new FakePlayerHandle(UUID.randomUUID(), "Steve", "lobby");
        assertEquals("allow", state(PolicyBuilder.buildPolicyJson(rules, config, player, null, null, 1), "Fullbright"));

        player.setWorld("arena"); // world over base
        assertEquals("block", state(PolicyBuilder.buildPolicyJson(rules, config, player, null, null, 1), "Fullbright"));

        player.setGameMode("creative"); // game mode over world (the section name's case does not matter)
        assertEquals("on", state(PolicyBuilder.buildPolicyJson(rules, config, player, null, null, 1), "Fullbright"));

        player.addPermission("ladsapi.group.staff"); // group over game mode
        assertEquals("block", state(PolicyBuilder.buildPolicyJson(rules, config, player, null, null, 1), "Fullbright"));

        // a scope over the group
        assertEquals("allow", state(PolicyBuilder.buildPolicyJson(rules, config, player, scopes("duel"), null, 1), "Fullbright"));

        // the scope entered later over the one entered earlier, both ways round
        assertEquals("block", state(PolicyBuilder.buildPolicyJson(rules, config, player, scopes("duel", "event"), null, 1), "Fullbright"));
        assertEquals("allow", state(PolicyBuilder.buildPolicyJson(rules, config, player, scopes("event", "duel"), null, 1), "Fullbright"));

        // a scope's API rule over its rules.yml rule
        Map<String, Map<String, ModuleRule>> withApiRule = scopes("event", "duel");
        withApiRule.get("duel").put("Fullbright", new ModuleRule("on"));
        assertEquals("on", state(PolicyBuilder.buildPolicyJson(rules, config, player, withApiRule, null, 1), "Fullbright"));

        // the player's API override over every scope
        Map<String, ModuleRule> overrides = Map.of("Fullbright", new ModuleRule("block"));
        assertEquals("block", state(PolicyBuilder.buildPolicyJson(rules, config, player, withApiRule, overrides, 1), "Fullbright"));

        // a scope rules.yml does not know and nobody gave rules changes nothing
        assertEquals("block", state(PolicyBuilder.buildPolicyJson(rules, config, player, scopes("nothing"), null, 1), "Fullbright"));

        player.addPermission("ladsapi.bypass"); // bypass: no rules at all, scopes included
        assertEquals(null, state(PolicyBuilder.buildPolicyJson(rules, config, player, withApiRule, overrides, 1), "Fullbright"));
    }

    private static final class Joined {
        final FakePlatform platform;
        final SessionManager manager;
        final FakePlayerHandle handle;
        final LadsPlayer player;

        Joined(Path tempDir, LadsRules rules) {
            platform = new FakePlatform("paper", false, tempDir);
            manager = new SessionManager(platform, new LadsConfig(), rules);
            handle = new FakePlayerHandle(UUID.randomUUID(), "Steve", "lobby");
            platform.addPlayer(handle);
            manager.join(handle);
            platform.advanceTicks(1);
            manager.receive(handle, CLIENT_HELLO.getBytes(StandardCharsets.UTF_8));
            player = manager.player(handle.uuid()).orElseThrow();
            handle.getSentPayloads().clear();
        }

        String lastPolicy() {
            String last = handle.getLastSentString();
            assertEquals("policy", type(last));
            return last;
        }
    }

    @Test
    public void scopeEndsAndTheRulesFromBeforeReturn(@TempDir Path tempDir) {
        LadsRules rules = new LadsRules();
        rules.parseYaml(Map.of("modules", Map.of("Fullbright", "allow"), "scopes", Map.of("duel", Map.of("Fullbright", "block"))));
        Joined j = new Joined(tempDir, rules);

        j.player.scope("Duel"); // rules.yml's scopes.duel, whatever the case
        assertEquals("block", state(j.lastPolicy(), "Fullbright"));
        assertEquals(List.of("duel"), j.player.scopes());

        int sent = j.handle.getSentPayloads().size();
        j.player.scope("duel"); // already in it: nothing new to send
        assertEquals(sent, j.handle.getSentPayloads().size());

        j.player.scope("duel").force("Fullbright", "Brightness", 5).end();
        assertEquals("allow", state(j.lastPolicy(), "Fullbright"));
        assertFalse(JsonParser.parseString(j.lastPolicy()).getAsJsonObject().getAsJsonObject("options").has("Fullbright"));
        assertTrue(j.player.scopes().isEmpty());

        j.player.scope("duel"); // entering again starts clean: the old API rule is gone
        assertFalse(JsonParser.parseString(j.lastPolicy()).getAsJsonObject().getAsJsonObject("options").has("Fullbright"));
    }

    @Test
    public void timedScopeEndsByItself(@TempDir Path tempDir) {
        Joined j = new Joined(tempDir, new LadsRules());

        j.player.scope("flash").block("Fullbright").expireIn(30);
        assertEquals("block", state(j.lastPolicy(), "Fullbright"));

        j.platform.advanceTicks(599);
        assertEquals("block", state(j.lastPolicy(), "Fullbright"));
        int revBefore = JsonParser.parseString(j.lastPolicy()).getAsJsonObject().get("rev").getAsInt();

        j.platform.advanceTicks(1); // 30 s
        assertEquals(null, state(j.lastPolicy(), "Fullbright"));
        assertEquals(revBefore + 1, JsonParser.parseString(j.lastPolicy()).getAsJsonObject().get("rev").getAsInt());
        assertTrue(j.player.scopes().isEmpty());
    }

    @Test
    public void newTimerReplacesTheOldOne(@TempDir Path tempDir) {
        Joined j = new Joined(tempDir, new LadsRules());

        j.player.scope("flash").block("Fullbright").expireIn(10);
        j.player.scope("flash").expireIn(30);
        j.platform.advanceTicks(200); // the 10 s timer fires and must do nothing
        assertEquals(List.of("flash"), j.player.scopes());
        j.platform.advanceTicks(400);
        assertTrue(j.player.scopes().isEmpty());
        assertEquals(null, state(j.lastPolicy(), "Fullbright"));

        j.player.scope("flash").block("Fullbright").expireIn(10);
        j.player.scope("flash").expireIn(0); // no timer any more
        j.platform.advanceTicks(400);
        assertEquals(List.of("flash"), j.player.scopes());

        j.player.scope("flash").expireIn(10);
        j.player.scope("flash").end(); // ended by hand, entered again: the old timer must not end the new one
        j.player.scope("flash").block("Fullbright");
        j.platform.advanceTicks(400);
        assertEquals(List.of("flash"), j.player.scopes());
    }

    @Test
    public void timerAfterQuitSendsNothing(@TempDir Path tempDir) {
        Joined j = new Joined(tempDir, new LadsRules());
        j.player.scope("flash").block("Fullbright").expireIn(5);
        j.manager.quit(j.handle.uuid());
        int sent = j.handle.getSentPayloads().size();
        j.platform.advanceTicks(200);
        assertEquals(sent, j.handle.getSentPayloads().size());
    }

    @Test
    public void gameModeChangeSendsThePolicyForTheNewMode(@TempDir Path tempDir) {
        LadsRules rules = new LadsRules();
        rules.parseYaml(Map.of("gamemodes", Map.of("spectator", Map.of("Fullbright", "block"))));
        Joined j = new Joined(tempDir, rules);
        j.handle.setGameMode("survival");
        j.manager.gameModeChanged(j.handle);
        assertEquals(null, state(j.lastPolicy(), "Fullbright"));

        j.handle.setGameMode("spectator");
        j.manager.gameModeChanged(j.handle);
        assertEquals("block", state(j.lastPolicy(), "Fullbright"));
        // a bare policy: the client kept its session, so no hello (which would make it drop everything first)
        assertTrue(j.handle.getSentStrings().stream().allMatch(s -> "policy".equals(type(s))));

        Joined plain = new Joined(tempDir, new LadsRules()); // no per-game-mode rules: nothing to send
        plain.handle.setGameMode("creative");
        plain.manager.gameModeChanged(plain.handle);
        assertTrue(plain.handle.getSentPayloads().isEmpty());
    }
}
