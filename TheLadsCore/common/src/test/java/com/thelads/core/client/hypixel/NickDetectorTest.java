package com.thelads.core.client.hypixel;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.Nicknames;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.NickDetectorModule;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NickDetectorTest {
    private static final long S = 1_000_000_000L;
    private static final UUID REAL = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), OTHER = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");
    private static final UUID NICK = UUID.fromString("2f1b6c2e-8f3e-11ef-b864-0242ac120002"), NPC = UUID.fromString("c9b7ab41-5a0f-2c47-b5c2-0d7a9f1e3b11");

    @TempDir File tmp;
    private final List<String> chat = new ArrayList<>();
    private String address = "mc.hypixel.net";
    private final NickDetectorModule module = (NickDetectorModule) ModuleManager.getInstance().getModule(NickDetectorModule.NAME);
    private long clock = 100 * S;

    private static String skin(UUID owner) {
        String json = "{\"timestamp\":1,\"profileId\":\"" + owner.toString().replace("-", "") + "\",\"profileName\":\"Secret\",\"textures\":{}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private final LadsGameBridge game = new DefaultGameBridge() {
        @Override public String getServerAddress() { return address; }
        @Override public boolean isIngame() { return true; }
        @Override public String getPlayerName() { return "Me"; }
        @Override public void displayChatMessage(String text) { chat.add(text); }
        @Override public List<NickDetector.Profile> getTabProfiles() {
            return List.of(new NickDetector.Profile(NICK, "Bobby", null), new NickDetector.Profile(REAL, "Carl", skin(REAL)));
        }
    };

    @BeforeEach void setUp() {
        HypixelSafetyManager.setBypassFile(new File(tmp, "server_bypasses.json"));
        LadsGameBridge.set(game);
    }

    @AfterEach void tearDown() {
        module.setEnabled(false);
        NickDetector.tick(game, clock += 2 * S);
        LadsGameBridge.set(new DefaultGameBridge());
        HypixelSafetyManager.setBypassFile(null);
    }

    @Test void signals() {
        assertTrue(NickDetector.nicked(NICK, "Bobby", null), "version 1");
        assertTrue(NickDetector.nicked(NICK, "Bobby", skin(NICK)), "version 1, its skin or not");
        assertTrue(NickDetector.nicked(REAL, "Bobby", skin(OTHER)), "another account's skin");
        assertFalse(NickDetector.nicked(REAL, "Carl", skin(REAL)), "their own skin");
        assertFalse(NickDetector.nicked(REAL, "Carl", null), "no property: no signal");
        assertFalse(NickDetector.nicked(REAL, "Carl", "not base64!"), "garbage: no signal");
        assertFalse(NickDetector.nicked(NPC, "Bobby", skin(OTHER)), "version 2: an NPC, whatever its skin");
        assertFalse(NickDetector.nicked(NICK, "\u00a7cFake Name", null), "no player's name");
        assertFalse(NickDetector.nicked(null, "Bobby", null));
    }

    @Test void hypixelBlocksItUntilBypassedAndUnknownServersToo() {
        module.setEnabled(true);
        assertEquals(HypixelSafetyManager.Block.BANNED, HypixelSafetyManager.blockOf(NickDetectorModule.NAME, "mc.hypixel.net"));
        assertEquals(HypixelSafetyManager.Block.UNKNOWN, HypixelSafetyManager.blockOf(NickDetectorModule.NAME, "play.some-unknown-server.net"));
        assertEquals(HypixelSafetyManager.Block.NONE, HypixelSafetyManager.blockOf(NickDetectorModule.NAME, "Singleplayer"));
        NickDetector.tick(game, clock += 2 * S);
        assertTrue(NickDetector.markers().isEmpty(), "blocked: on in the settings, marks nothing");

        HypixelSafetyManager.bypass(NickDetectorModule.NAME);
        NickDetector.tick(game, clock += 2 * S);
        assertEquals("Bobby \u00a7c[Nick]\u00a7r", NickDetector.markers().get("bobby"), "bypassed for this server");
        assertNull(NickDetector.markers().get("carl"));

        address = "play.some-unknown-server.net";
        NickDetector.tick(game, clock += 2 * S);
        assertTrue(NickDetector.markers().isEmpty(), "the bypass is that server's alone");
    }

    @Test void marksNameTagsThroughNicknamesAndNoticesOnce() {
        HypixelSafetyManager.bypass(NickDetectorModule.NAME);
        NickDetector.tick(game, clock += 2 * S);
        assertTrue(NickDetector.markers().isEmpty(), "off by default");
        module.setEnabled(true);
        module.chatNotice.set(true);
        NickDetector.tick(game, clock += 2 * S);
        NickDetector.tick(game, clock += 2 * S);
        assertEquals(1, chat.size(), "one notice per player: " + chat);
        assertFalse(chat.get(0).contains("Secret"), "the skin's profile name is never shown");
        assertEquals("[MVP+] Bobby \u00a7c[Nick]\u00a7r: hi", Nicknames.rename("[MVP+] Bobby: hi", Nicknames.active("Me")));
        module.chatNotice.set(false);
        module.setEnabled(false);
        NickDetector.tick(game, clock += 2 * S);
        assertEquals("Bobby: hi", Nicknames.rename("Bobby: hi", Nicknames.active("Me")), "off: no marker");
    }

    @Test void neverMarksTheLocalPlayerNickedThemselves() {
        HypixelSafetyManager.bypass(NickDetectorModule.NAME);
        module.setEnabled(true);
        LadsGameBridge nickedMe = new DefaultGameBridge() {
            @Override public String getServerAddress() { return address; }
            @Override public boolean isIngame() { return true; }
            @Override public String getPlayerName() { return "Me"; }
            @Override public Session session() { return new Session(REAL, "Me", "0"); }
            @Override public List<NickDetector.Profile> getTabProfiles() { return List.of(new NickDetector.Profile(REAL, "Nicky", skin(OTHER))); }
        };
        LadsGameBridge.set(nickedMe);
        NickDetector.tick(nickedMe, clock += 2 * S);
        assertTrue(NickDetector.markers().isEmpty());
    }

    @Test
    void aMarkedNameIsNotMarkedAgain() {
        java.util.Map<String, String> names = java.util.Map.of("qanicked", "QaNicked §c[Nick]§r");
        String once = com.thelads.core.client.Nicknames.rename("QaNicked", names);
        assertEquals("QaNicked §c[Nick]§r", once);
        assertEquals(once, com.thelads.core.client.Nicknames.rename(once, names));
    }
}
