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

    @Test
    void subServerQuickWarpConnectsAwayAndNeverFiresOffNetwork() {
        var sent = new java.util.ArrayList<String>();
        var connected = new java.util.ArrayList<String>();
        boolean[] inGame = {false};
        var bridge = new DefaultGameBridge() {
            @Override public String getServerAddress() { return address; }
            @Override public boolean isIngame() { return inGame[0]; }
            @Override public boolean hasPlayer() { return inGame[0]; }
            @Override public void sendChatLine(String text) { sent.add(text); }
            public void connectToServer(String name, String addr) { connected.add(name + "@" + addr); }
        };
        LadsGameBridge.set(bridge);
        LadsNetwork.clearPendingCommand();

        // Away from network: connects first and queues /server parkour
        address = "Singleplayer";
        LadsNetwork.joinSubServer(bridge, LadsNetwork.findSubServer("parkour"));
        assertEquals(java.util.List.of("The Lads Network@mc.arashyt.ca"), connected);
        assertEquals("/server parkour", LadsNetwork.pendingCommand());
        assertTrue(sent.isEmpty());

        // Anti-Ban guarantee: if player lands on Hypixel or Singleplayer, pending command is immediately wiped without sending!
        address = "mc.hypixel.net";
        inGame[0] = true;
        LadsNetwork.tick(bridge);
        assertNull(LadsNetwork.pendingCommand(), "Queued warp must immediately cancel on non-Lads servers");
        assertTrue(sent.isEmpty(), "Never send /server on another server");

        // Now queue again and complete login onto mc.arashyt.ca
        inGame[0] = false;
        address = "Unknown";
        LadsNetwork.joinSubServer(bridge, LadsNetwork.findSubServer("pvparena"));
        address = "mc.arashyt.ca";
        inGame[0] = true;
        for (int i = 0; i < 30; i++) LadsNetwork.tick(bridge);
        assertEquals(java.util.List.of("/server pvparena"), sent);
        assertNull(LadsNetwork.pendingCommand());

        // Already connected on mc.arashyt.ca: immediate switch without reconnecting
        LadsNetwork.joinSubServer(bridge, LadsNetwork.findSubServer("smp"));
        assertEquals(java.util.List.of("/server pvparena", "/server smp"), sent);
        assertEquals(2, connected.size(), "Already on network: switches via /server without reconnecting");
    }

    @Test
    void hubDialogRendersModesAndDispatchesHubOrSubServer() {
        var sent = new java.util.ArrayList<String>();
        var connected = new java.util.ArrayList<String>();
        var bridge = new DefaultGameBridge() {
            @Override public String getServerAddress() { return address; }
            @Override public boolean isIngame() { return "mc.arashyt.ca".equals(address); }
            @Override public boolean hasPlayer() { return "mc.arashyt.ca".equals(address); }
            @Override public void sendChatLine(String text) { sent.add(text); }
            public void connectToServer(String name, String addr) { connected.add(addr); }
        };
        LadsGameBridge.set(bridge);
        var dialog = new com.thelads.core.client.gui.LadsNetworkHubDialog();
        var drawCalls = new java.util.ArrayList<String>();
        var g = new com.thelads.core.client.bridge.LadsGraphics() {
            @Override public void fill(int minX, int minY, int maxX, int maxY, int color) { drawCalls.add("fill"); }
            @Override public void drawText(String text, int x, int y, int color, boolean shadow) { drawCalls.add("text:" + text); }
            @Override public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) { drawCalls.add("center:" + text); }
            @Override public int textWidth(String text) { return text != null ? text.length() * 6 : 0; }
            @Override public int fontHeight() { return 9; }
            @Override public void pushPose() {}
            @Override public void popPose() {}
            @Override public void translate(float x, float y) {}
            @Override public void scale(float sx, float sy) {}
            @Override public void enableScissor(int minX, int minY, int maxX, int maxY) {}
            @Override public void disableScissor() {}
            @Override public void blit(String texture, int x, int y, int u, int v, int width, int height) {}
            @Override public void drawHead(String username, String uuid, int x, int y, int size) {}
            @Override public int getScaledWidth() { return 640; }
            @Override public int getScaledHeight() { return 360; }
        };

        // Offline / away: clicking main banner connects to mc.arashyt.ca
        address = "Singleplayer";
        dialog.open();
        dialog.render(g, 0, 0);
        var mainBtn = dialog.joinMainBounds();
        dialog.click(mainBtn.x() + 5, mainBtn.y() + 5, 0);
        assertFalse(dialog.isOpen());
        assertEquals(java.util.List.of("mc.arashyt.ca"), connected);

        // Connected on mc.arashyt.ca with hello game & hub: main banner sends /hub, sub-card sends /server pvpduels
        address = "mc.arashyt.ca";
        hello(",\"game\":\"Lobby\",\"hub\":\"/hub\"");
        drawCalls.clear();
        dialog.open();
        dialog.render(g, 0, 0);
        assertTrue(drawCalls.stream().anyMatch(c -> c.contains("Active Mode: Lobby")));
        dialog.click(dialog.joinMainBounds().x() + 5, dialog.joinMainBounds().y() + 5, 0);
        assertEquals(java.util.List.of("/hub"), sent);

        dialog.open();
        dialog.render(g, 0, 0);
        var duels = dialog.subServerBounds("pvpduels");
        dialog.click(duels.x() + 5, duels.y() + 5, 0);
        assertEquals(java.util.List.of("/hub", "/server pvpduels"), sent);
    }
}
