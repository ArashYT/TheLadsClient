package com.thelads.core.client.discord;

import com.thelads.core.client.ladsapi.LadsApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordNetworkNameTest {
    private static final DiscordPresence.Privacy FULL = new DiscordPresence.Privacy(true, false, false, false, 0);

    private static String details(String address) {
        return DiscordPresence.from(new DiscordPresence.Game(DiscordPresence.Place.MULTIPLAYER, "26.3", address, null, null, null, 1), FULL).details();
    }

    @AfterEach
    void reset() {
        LadsApiClient.reset();
        LadsApiClient.install(null, "");
    }

    @Test
    void ourNetworkShowsTheNameItsApiSentOthersKeepTheirHost() {
        LadsApiClient.install(bytes -> { }, "26.3");
        assertEquals("Playing on mc.arashyt.ca", details("mc.arashyt.ca")); // no hello yet

        LadsApiClient.receive("{\"t\":\"hello\",\"v\":1,\"server\":\"The Lads Network\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals("Playing on The Lads Network", details("mc.arashyt.ca"));
        assertEquals("Playing on The Lads Network", details("Lobby.ArashYT.ca:25565"));
        // Any other server keeps its host: a server's own name never reaches the player's Discord status.
        assertEquals("Playing on play.example.net", details("play.example.net"));
        assertEquals("Playing on notarashyt.ca", details("notarashyt.ca"));
    }
}
