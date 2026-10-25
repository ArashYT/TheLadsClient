package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thelads.core.client.hud.TeamSpeakHudElement;
import com.thelads.core.client.media.MumbleLink;
import com.thelads.core.client.media.TeamSpeakBridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MumbleTeamSpeakTest {

    @BeforeEach
    void setUp() {
        TeamSpeakBridge.stop();
    }

    @Test
    void mumbleVectorCalculation() {
        // Yaw 0 (facing south in Minecraft: -Z is north, +Z is south), Pitch 0 (horizon)
        float[] front = MumbleLink.calculateFrontVector(0f, 0f);

        assertEquals(0.0f, front[0], 1e-4);
        assertEquals(0.0f, front[1], 1e-4);
        assertEquals(1.0f, front[2], 1e-4);

        // Vector magnitude is 1.0 (unit vector)
        double mag = Math.sqrt(front[0] * front[0] + front[1] * front[1] + front[2] * front[2]);
        assertEquals(1.0, mag, 1e-4);
    }

    @Test
    void teamSpeakParsingFields() {
        String talkLine = "notifytalkstatuschange status=1 is_received_whisper=0 clid=42";
        assertEquals(1, TeamSpeakBridge.parseFieldInt(talkLine, "status"));
        assertEquals(42, TeamSpeakBridge.parseFieldInt(talkLine, "clid"));

        String nickLine = "clid=100 client_nickname=TheLadsPlayer\\sGamer";
        assertEquals(100, TeamSpeakBridge.parseFieldInt(nickLine, "clid"));
        assertEquals("TheLadsPlayer\\sGamer", TeamSpeakBridge.parseFieldString(nickLine, "client_nickname"));
    }

    @Test
    void teamSpeakBridgeWhoamiAndClientMove() {
        // whoami response
        TeamSpeakBridge.parseLine("clid=42 cid=101");
        assertEquals(42, TeamSpeakBridge.getMyClid());
        assertEquals(101, TeamSpeakBridge.getMyCid());

        // channelclientlist with voice flag
        TeamSpeakBridge.parseLine("clid=42 client_nickname=LocalUser client_flag_talking=1|clid=99 client_nickname=RemoteUser client_flag_talking=0");
        assertEquals(2, TeamSpeakBridge.getClients().size());

        // another client moved away
        TeamSpeakBridge.parseLine("notifyclientmoved clid=99 ctid=200");
        assertEquals(1, TeamSpeakBridge.getClients().size());

        // local client moved to channel 200
        TeamSpeakBridge.parseLine("notifyclientmoved clid=42 ctid=200");
        assertEquals(200, TeamSpeakBridge.getMyCid());
        assertEquals(0, TeamSpeakBridge.getClients().size()); // cleared pending new channelclientlist
    }

    @Test
    void teamSpeakBridgeClientState() {
        // Parse client list entry
        TeamSpeakBridge.parseLine("clid=5 client_nickname=Alice|clid=6 client_nickname=Bob");
        // Alice starts talking
        TeamSpeakBridge.parseLine("notifytalkstatuschange status=1 clid=5");
        // Bob idle
        TeamSpeakBridge.parseLine("notifytalkstatuschange status=0 clid=6");

        // Verify active channel name
        TeamSpeakBridge.parseLine("channel_name=PvP\\sChannel");
        assertEquals("PvP Channel", TeamSpeakBridge.getCurrentChannel());
    }

    @Test
    void teamSpeakHudElementDimensions() {
        TeamSpeakHudElement hud = new TeamSpeakHudElement();
        assertEquals("TeamSpeak", TeamSpeakHudElement.NAME);
        assertTrue(hud.isAvailable());
    }

    @Test
    void teamSpeakApiKeyOptionAndGetter() {
        assertEquals("ClientQuery API Key", TeamSpeakBridge.OPTION_API_KEY);
        TeamSpeakBridge.setApiKey("test-secret-key-1234");
        assertEquals("test-secret-key-1234", TeamSpeakBridge.getApiKey());
        TeamSpeakBridge.setApiKey("");

        com.thelads.core.config.Module m = com.thelads.core.config.ModuleManager.getInstance().getModule("TeamSpeak");
        if (m != null) {
            com.thelads.core.config.Option opt = m.getOption(TeamSpeakBridge.OPTION_API_KEY);
            assertTrue(opt instanceof com.thelads.core.config.TextOption, "Option should be TextOption");
            com.thelads.core.config.TextOption textOpt = (com.thelads.core.config.TextOption) opt;
            textOpt.setValue("configured-key-5678");
            assertEquals("configured-key-5678", TeamSpeakBridge.getApiKey());
            textOpt.setValue("");
        }
    }
}
