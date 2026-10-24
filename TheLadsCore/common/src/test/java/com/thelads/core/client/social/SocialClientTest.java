package com.thelads.core.client.social;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SocialClientTest {

    @Test
    public void backoffCalculationFollowsSpecification() {
        // 5s doubling up to 5 min, minus up to 20% jitter
        long b1_min = SocialClient.backoffMs(1, 1.0);
        long b1_max = SocialClient.backoffMs(1, 0.0);
        assertEquals(4000L, b1_min);
        assertEquals(5000L, b1_max);

        long b2_min = SocialClient.backoffMs(2, 1.0);
        long b2_max = SocialClient.backoffMs(2, 0.0);
        assertEquals(8000L, b2_min);
        assertEquals(10000L, b2_max);

        long b7_min = SocialClient.backoffMs(7, 1.0);
        long b7_max = SocialClient.backoffMs(7, 0.0);
        assertEquals(240000L, b7_min);
        assertEquals(300000L, b7_max);

        long b10_max = SocialClient.backoffMs(10, 0.0);
        assertEquals(300000L, b10_max, "Must cap at 5 minutes");
    }

    @Test
    public void endpointOverrideValidation() {
        // Valid default
        URI def = SocialClient.resolveEndpoint(null);
        assertEquals("wss", def.getScheme());

        // Local ws allowed
        URI local = SocialClient.resolveEndpoint("ws://127.0.0.1:8080/social/ws");
        assertNotNull(local);
        assertEquals("ws", local.getScheme());

        // Remote insecure ws rejected
        URI remoteInsecure = SocialClient.resolveEndpoint("ws://malicious.example.com/social/ws");
        assertNull(remoteInsecure, "Remote plain ws must be rejected");

        // Remote wss allowed
        URI remoteSecure = SocialClient.resolveEndpoint("wss://custom.example.com/social/ws");
        assertNotNull(remoteSecure);
    }

    @Test
    public void reconnectsWithBackoffAndRestoresPresence() {
        List<JsonObject> sent = new ArrayList<>();
        SocialClient client = new SocialClient("ws://127.0.0.1:9999/social/ws") {
            @Override
            public void send(JsonObject obj) {
                sent.add(obj);
            }
        };

        SocialBridge.GameInfo gameInfo = new SocialBridge.GameInfo("1.20.1", "fabric", "plain", "", "", "", "1.9.18");
        Presence presence = new Presence("singleplayer", gameInfo, null, null);

        // Update presence while offline
        client.setPresence(presence);

        // When connection is restored, onConnected publishes the cached presence
        client.onConnected();

        assertTrue(sent.stream().anyMatch(m ->
                "presence".equals(m.get("t").getAsString()) && "singleplayer".equals(m.get("state").getAsString())),
                "Reconnecting must automatically restore the latest presence");
    }
}
