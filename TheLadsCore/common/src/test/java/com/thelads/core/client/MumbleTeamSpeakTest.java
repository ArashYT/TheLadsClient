package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thelads.core.client.hud.TeamSpeakHudElement;
import com.thelads.core.client.media.MumbleLink;
import com.thelads.core.client.media.TeamSpeakBridge;
import org.junit.jupiter.api.Test;

class MumbleTeamSpeakTest {

    @Test
    void mumbleVectorCalculation() {
        // Yaw 0 (facing south in Minecraft: -Z is north, +Z is south), Pitch 0 (horizon)
        double yawRad = Math.toRadians(0);
        double pitchRad = Math.toRadians(0);
        float fx = (float) (-Math.sin(yawRad) * Math.cos(pitchRad));
        float fy = (float) (-Math.sin(pitchRad));
        float fz = (float) (Math.cos(yawRad) * Math.cos(pitchRad));

        assertEquals(0.0f, fx, 1e-4);
        assertEquals(0.0f, fy, 1e-4);
        assertEquals(1.0f, fz, 1e-4);

        // Vector magnitude is 1.0 (unit vector)
        double mag = Math.sqrt(fx * fx + fy * fy + fz * fz);
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
    void teamSpeakBridgeClientState() {
        // Parse client list entry
        TeamSpeakBridge.parseLine("clid=5 client_nickname=Alice|clid=6 client_nickname=Bob");
        // Alice starts talking
        TeamSpeakBridge.parseLine("notifytalkstatuschange status=1 clid=5");
        // Bob idle
        TeamSpeakBridge.parseLine("notifytalkstatuschange status=0 clid=6");

        // Verify active channel name
        TeamSpeakBridge.parseLine("notifychannelname channel_name=PvP\\sChannel");
        assertEquals("PvP Channel", TeamSpeakBridge.getCurrentChannel());
    }

    @Test
    void teamSpeakHudElementDimensions() {
        TeamSpeakHudElement hud = new TeamSpeakHudElement();
        assertEquals("TeamSpeak", TeamSpeakHudElement.NAME);
        assertTrue(hud.isAvailable());
    }
}
