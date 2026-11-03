package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.discord.DiscordPresence;
import com.thelads.core.client.ladsapi.LadsApiClient;
import com.thelads.core.client.social.Presence;
import com.thelads.core.client.social.SocialBridge;
import com.thelads.core.client.social.SocialState;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LadsNetworkTest {
    private static final Predicate<String> NO_TREE = command -> false;
    private final LadsGameBridge before = LadsGameBridge.get();
    private String address = "Singleplayer";

    @BeforeEach
    void setUp() {
        LadsGameBridge.set(new DefaultGameBridge() {
            @Override public String getServerAddress() { return address; }
        });
        LadsApiClient.install(bytes -> { }, "26.3");
        LadsApiClient.reset();
    }

    @AfterEach
    void tearDown() {
        LadsApiClient.reset();
        LadsApiClient.install(null, "");
        LadsGameBridge.set(before);
    }

    private static void hello(String extra) {
        LadsApiClient.receive(("{\"t\":\"hello\",\"v\":1,\"server\":\"The Lads Network\"" + extra + "}").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void addressesWithPortsAndOtherHosts() {
        assertTrue(LadsNetwork.isAddress("mc.arashyt.ca"));
        assertTrue(LadsNetwork.isAddress(" Lobby.ArashYT.ca:25565 "));
        assertTrue(LadsNetwork.isAddress("mc.arashyt.ca."));
        assertTrue(LadsNetwork.isAddress("mc.arashyt.ca.:25565"));
        assertFalse(LadsNetwork.isAddress(null));
        assertFalse(LadsNetwork.isAddress("mc.hypixel.net"));
        assertFalse(LadsNetwork.isAddress("arashyt.ca.evil.net:25565"));
        assertFalse(LadsNetwork.isAddress("Singleplayer"));
    }

    @Test
    void pauseButtonIsNetworkAwayAndHubOnlyOnTheNetworkWithAHub() {
        assertEquals("Network", LadsNetwork.pauseLabel(NO_TREE), "in singleplayer");
        address = "mc.hypixel.net";
        assertEquals("Network", LadsNetwork.pauseLabel(NO_TREE), "on another server");
        address = "mc.arashyt.ca";
        assertNull(LadsNetwork.pauseLabel(NO_TREE), "nothing to hop to: no hello field, no tree command");
        assertEquals("Hub", LadsNetwork.pauseLabel("lobby"::equals), "the plugin not updated yet: the command tree still knows");
        hello(",\"hub\":\"/server lobby\"");
        assertEquals("Hub", LadsNetwork.pauseLabel(NO_TREE));
    }

    @Test
    void hubCommandIsNeverSentOnAnotherServer() {
        hello(",\"hub\":\"/hub\"");
        address = "mc.hypixel.net";
        assertNull(LadsNetwork.hubCommand(command -> true), "another server's hello or tree is never acted on");
        address = "mc.arashyt.ca";
        assertEquals("/hub", LadsNetwork.hubCommand(NO_TREE));
        assertEquals("/hub", LadsNetwork.hubCommand(command -> false));
    }

    @Test
    void helloNamesTheHubFirstThenTheTree() {
        address = "mc.arashyt.ca";
        assertEquals("/lobby", LadsNetwork.hubCommand("lobby"::equals));
        assertEquals("/hub", LadsNetwork.hubCommand(command -> true), "hub before lobby");
        hello(",\"hub\":\"server main\"");
        assertEquals("/server main", LadsNetwork.hubCommand(command -> true), "the slash is added");
    }

    @Test
    void aHubThatIsNotAPlainCommandIsDropped() {
        address = "mc.arashyt.ca";
        hello(",\"hub\":\"/op Steve now please\"");
        assertNull(LadsNetwork.hubCommand(NO_TREE));
        hello(",\"hub\":\"/say hi\\nthere\"");
        assertNull(LadsNetwork.hubCommand(NO_TREE));
    }

    @Test
    void gameOnlyOnTheNetworkAndGoneAfterASwitch() {
        address = "mc.arashyt.ca";
        hello("");
        assertNull(LadsNetwork.game(), "an older plugin sends no game");
        hello(",\"game\":\"  Bedwars  \",\"hub\":\"/hub\"");
        assertEquals("Bedwars", LadsNetwork.game());
        hello(""); // a backend's hello behind a proxy that named them
        assertEquals("Bedwars", LadsNetwork.game());
        assertEquals("/hub", LadsNetwork.hubCommand(NO_TREE));

        address = "mc.hypixel.net";
        assertNull(LadsNetwork.game());
        address = "mc.arashyt.ca";

        LadsApiClient.serverSwitch();
        assertNull(LadsNetwork.game(), "the old backend's game goes");
        assertEquals("/hub", LadsNetwork.hubCommand(NO_TREE), "the proxy's hub command stays");
        LadsApiClient.reset();
        assertNull(LadsNetwork.hubCommand(NO_TREE), "a disconnect drops it");
    }

    @Test
    void discordShowsTheGameOnTheNetworkOnly() {
        DiscordPresence.Privacy full = new DiscordPresence.Privacy(true, false, false, false, 0);
        hello(",\"game\":\"Bedwars\"");
        assertEquals("In Bedwars", DiscordPresence.from(game("mc.arashyt.ca", null), full).state());
        assertEquals("Paused", DiscordPresence.from(game("mc.arashyt.ca", "Paused"), full).state(), "an open screen still wins");
        assertEquals("Minecraft 26.3", DiscordPresence.from(game("mc.hypixel.net", null), full).state(), "never another server's text");
    }

    private static DiscordPresence.Game game(String address, String screen) {
        return new DiscordPresence.Game(DiscordPresence.Place.MULTIPLAYER, "26.3", address, null, null, screen, 1);
    }

    @Test
    void friendOnTheNetworkCanBeJoinedUnlessWeAreThereToo() {
        SocialState state = new SocialState();
        SocialBridge.GameInfo other = new SocialBridge.GameInfo("1.8.9", "forge", "plain", "", "", "", "1.0");
        var friend = new com.thelads.core.client.social.FriendEntry(java.util.UUID.randomUUID(), "Arash", null,
            new Presence("server", other, "mc.arashyt.ca:25565", null), 0);
        assertTrue(state.canJoin(friend).ok(), "any version: the network takes them all");
        address = "mc.arashyt.ca";
        assertFalse(state.canJoin(friend).ok());
        address = "Singleplayer";

        var elsewhere = new com.thelads.core.client.social.FriendEntry(friend.id(), "Arash", null,
            new Presence("server", other, "mc.hypixel.net", null), 0);
        assertFalse(state.canJoin(elsewhere).ok(), "another server stays not joinable");
    }
}
