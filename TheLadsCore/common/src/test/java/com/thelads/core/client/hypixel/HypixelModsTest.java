package com.thelads.core.client.hypixel;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Hypixel chat lines as Hypixel sends them (formatting codes included where it uses them), the Mod API ping and the modules. */
class HypixelModsTest {
    private static final long S = 1_000_000_000L;
    private final List<String> sent = new ArrayList<>();
    private final List<byte[]> payloads = new ArrayList<>();
    private int[] listed = {1, 1, 1};
    private InetSocketAddress address = new InetSocketAddress("203.0.113.5", 25565);
    private String server = "mc.hypixel.net";
    private final Object connection = new Object(), world = new Object();

    private final LadsGameBridge game = new DefaultGameBridge() {
        @Override public String getServerAddress() { return server; }
        @Override public int[] getListedLatencies() { return listed; }
        @Override public InetSocketAddress getServerSocketAddress() { return address; }
        @Override public void sendChatLine(String text) { sent.add(text); }
        @Override public boolean sendPluginMessage(String channel, byte[] data) { assertEquals(RealPing.CHANNEL, channel); payloads.add(data); return true; }
    };

    @BeforeEach void setUp() {
        ModuleManager.getInstance().getModule(HypixelMods.AUTO_FRIEND).setEnabled(true);
        LadsGameBridge.set(game);
        HypixelMods.resetForTest();
        for (String name : new String[] {HypixelMods.AUTO_GG, HypixelMods.AUTO_FRIEND, HypixelMods.AUTO_TIP, HypixelMods.PLAY_AGAIN, HypixelMods.AUTO_WHO, HypixelMods.CLEANUP}) {
            Module m = ModuleManager.getInstance().getModule(name);
            m.getOptions().forEach(o -> o.reset());
        }
    }

    @AfterEach void tearDown() {
        HypixelMods.tick(game, null, null, 0);
        LadsGameBridge.set(new DefaultGameBridge());
        HypixelSafetyManager.qaOverride = false;
        ModuleManager.getInstance().getModule(HypixelMods.PLAY_AGAIN).setEnabled(false);
        ModuleManager.getInstance().getModule(HypixelMods.AUTO_WHO).setEnabled(false);
        ModuleManager.getInstance().getModule(HypixelMods.AUTO_TIP).setEnabled(true);
        ModuleManager.getInstance().getModule(HypixelMods.AUTO_FRIEND).setEnabled(false);
    }

    @Test void defaults() {
        java.util.Map<String, Boolean> on = new java.util.HashMap<>();
        for (Module m : HypixelMods.modules()) on.put(m.getName(), m.isEnabled());
        assertEquals(java.util.Map.of(HypixelMods.AUTO_GG, true, HypixelMods.AUTO_TIP, true, HypixelMods.CLEANUP, true,
            HypixelMods.AUTO_FRIEND, false, HypixelMods.PLAY_AGAIN, false, HypixelMods.AUTO_WHO, false), on, "Auto Friend: anyone could add you, opt in");
    }

    @Test void locrawAnswerAfterAnotherCommandAndPingMissesReset() {
        ModuleManager.getInstance().getModule(HypixelMods.PLAY_AGAIN).setEnabled(true);
        ModuleManager.getInstance().getModule(HypixelMods.AUTO_FRIEND).setEnabled(true);
        HypixelMods.tick(game, world, connection, 0);
        HypixelMods.tick(game, world, connection, 2 * S);
        HypixelMods.chat("Friend request from Steve", 2 * S);
        HypixelMods.tick(game, world, connection, 4 * S);
        assertEquals(List.of("/locraw", "/friend accept Steve"), sent);
        assertTrue(HypixelMods.chat("{\"server\":\"mini1A\",\"gametype\":\"SKYWARS\",\"mode\":\"solo_insane\"}", 5 * S),
            "still our /locraw's answer though /friend went out after it");
        assertArrayEquals(new String[] {"SKYWARS", "solo_insane"}, HypixelMods.location());
        // Mod API: two misses, then an answer, then two more misses: still on the Mod API (three in a row would end it).
        payloads.clear();
        Object c = new Object();
        long t = 0;
        RealPing.tick(game, c, t);
        for (int i = 0; i < 3; i++) { t += 6 * S; RealPing.tick(game, c, t); }
        RealPing.receive(RealPing.CHANNEL, new byte[] {1, 1, 4, 'p', 'o', 'n', 'g'}, t + 1);
        for (int i = 0; i < 2; i++) { t += 6 * S; RealPing.tick(game, c, t); }
        int before = payloads.size();
        t += 6 * S;
        RealPing.tick(game, c, t);
        assertEquals(before + 1, payloads.size(), "an answer resets the miss count: " + payloads.size());
    }

    @Test void gameEndInEveryModeButNotFromPlayers() {
        String[] ends = {
            "                       1st Killer - §r§b[MVP§r§c+§r§b] Steve§r§7 - 7",              // Bed Wars, SkyWars
            "                                Winner - §r§a[VIP] Steve",                         // SkyWars
            "                              Winner: §r§aPLAYERS",                               // Murder Mystery
            "                      Murderer: Alex (3 Kills)",
            "                       1st Place - [VIP] Steve",                                  // TNT Run, Arcade
            "                    1st - Steve (Steve's build)",                                 // Build Battle
            "             §6§lWINNER! §r§b[MVP+] Steve §r§7[VIP] Alex",                        // Duels
            "                      Winning Team - Red",
            "                         Red won the game!",
            "                              GAME OVER!",
            "                   You survived 15 rounds!",                                       // Zombies
            "                Alpha Infected: Steve (5 infections)",
            "  #1 Steve (01:23:45)",
            "                          Classic Duel - 2:13",                                    // Duels
            "                     Bridge CTF Duel - 12:34",
            "                  They captured all wools!",
            "             [MVP+] Steve - 12 Final Kills",                                       // Mega Walls
            "                 Zombies - 1:23:45 (Round 12)",
            "§6This game has been recorded. §r§e§lClick here to watch the Replay!",
            "▬▬▬▬▬▬▬▬▬▬\n                                 Bed Wars\n\n                 1st Killer - Steve - 3\n▬▬▬▬▬▬▬▬▬▬",
        };
        for (String line : ends) assertTrue(HypixelChat.gameEnd(line), line);
        String[] not = {
            "[MVP+] Troll: 1st Killer - me - 99", "Party > [VIP] Troll: GAME OVER!", "§7Troll§7: Winner: me", "From [MVP+] Troll: Steve won the game!",
            "[VIP] Troll: You survived 3 rounds!", "Bed Wars", "                                 Bed Wars", "The game starts in 1 second!",
        };
        for (String line : not) assertFalse(HypixelChat.gameEnd(line), line);
        assertTrue(HypixelChat.gameStart("§eThe game starts in §r§c1§r§e second!"));
        assertTrue(HypixelChat.gameStart("Cages open in: 1 second!"));
        assertFalse(HypixelChat.gameStart("The game starts in 2 seconds!"));
        assertFalse(HypixelChat.gameStart("[VIP] Troll: The game starts in 1 second!"));
    }

    @Test void friendRequestsUnknownCommandsAndLocraw() {
        String request = "§9§m-----------------------------------------------------§r§9\n§eFriend request from §r§b[MVP§r§c+§r§b] Steve_2§r§e\n"
            + "§r§a§l[ACCEPT] §r§8- §r§c§l[DENY] §r§8- §r§7§l[IGNORE]§r\n§9§m-----------------------------------------------------§r";
        assertEquals("Steve_2", HypixelChat.friendRequest(request));
        assertEquals("Alex", HypixelChat.friendRequest("Friend request from Alex"));
        assertNull(HypixelChat.friendRequest("[VIP] Troll: Friend request from Troll"));
        assertTrue(HypixelChat.unknownCommand("§cUnknown command. Type \"/help\" for help."));
        assertTrue(HypixelChat.unknownCommand("Unknown or incomplete command, see below for error"));
        assertTrue(HypixelChat.unknownCommand("Unknown command. Try /help for a list of commands"));
        assertFalse(HypixelChat.unknownCommand("[VIP] Troll: Unknown command lol"));
        assertArrayEquals(new String[] {"BEDWARS", "BEDWARS_EIGHT_ONE"},
            HypixelChat.location("{\"server\":\"mini123A\",\"gametype\":\"BEDWARS\",\"mode\":\"BEDWARS_EIGHT_ONE\",\"map\":\"Lighthouse\"}"));
        assertArrayEquals(new String[] {"BEDWARS", null}, HypixelChat.location("{\"server\":\"dynamiclobby12\",\"gametype\":\"BEDWARS\",\"lobbyname\":\"bedwarslobby5\"}"));
        assertNull(HypixelChat.location("[VIP] Troll: {\"server\":\"x\"}"));
        assertEquals("/play bedwars_eight_one", HypixelChat.playCommand("BEDWARS", "BEDWARS_EIGHT_ONE"));
        assertEquals("/play solo_insane", HypixelChat.playCommand("SKYWARS", "solo_insane"));
        assertEquals("/play blitz_solo_normal", HypixelChat.playCommand("SURVIVAL_GAMES", "solo_normal"));
        assertEquals("/play duels_classic_duel", HypixelChat.playCommand("DUELS", "DUELS_CLASSIC_DUEL"));
        assertEquals("/play tnt_tntrun", HypixelChat.playCommand("TNTGAMES", "TNTRUN"));
        assertNull(HypixelChat.playCommand("BEDWARS", null));
        assertNull(HypixelChat.playCommand("SKYBLOCK", "hub"));
        assertNull(HypixelChat.playCommand("MAIN", "lobby"));
    }

    @Test void chatCleanupKinds() {
        Object[][] lines = {
            {"§b[MVP§c+§b] Steve§f §6joined the lobby!", HypixelChat.Spam.LOBBY_JOIN},
            {" §b>§c>§a>§r §6[MVP§2++§6] Steve§f §6joined the lobby! §a<§c<§b<", HypixelChat.Spam.LOBBY_JOIN},
            {"[MVP+] Steve spooked into the lobby!", HypixelChat.Spam.LOBBY_JOIN},
            {"§b✦ §r§b[MVP§r§c+§r§b] Steve §r§7found a §r§e★★★★★ §r§bMystery Box§r§7!", HypixelChat.Spam.MYSTERY_BOX},
            {"[VIP] Steve found Legendary Rainbow Necklace in a Mystery Box!", HypixelChat.Spam.MYSTERY_BOX},
            {"Steve has found Angel's Descent in the Soul Well!", HypixelChat.Spam.MYSTERY_BOX},
            {"They have gifted 15 ranks so far!", HypixelChat.Spam.GIFTS},
            {"[MVP++] Steve gifted the rank of MVP+ to Alex!", HypixelChat.Spam.GIFTS},
            {"You tipped 5 players in 5 different games!", HypixelChat.Spam.TIPS},
            {"You already tipped everyone that has boosters active, so there isn't anybody to be tipped right now!", HypixelChat.Spam.TIPS},
            {"No one has a network booster active right now! Try again later.", HypixelChat.Spam.TIPS},
            {"➲ Steve activated 3x coins for Bed Wars!", HypixelChat.Spam.BOOSTERS},
            {"Buy Network Boosters at https://store.hypixel.net", HypixelChat.Spam.BOOSTERS},
            {"[WATCHDOG ANNOUNCEMENT]\nWatchdog has banned 5,431 players in the last 7 days.\nStaff have banned an additional 1,234 in the last 7 days.\n"
                + "Blacklisted modifications are a bannable offense!", HypixelChat.Spam.WATCHDOG},
            {"+5 Karma!", HypixelChat.Spam.KARMA},
        };
        for (Object[] line : lines) assertEquals(line[1], HypixelChat.spam((String) line[0]), (String) line[0]);
        for (String line : new String[] {"[MVP+] Steve: joined the lobby!", "[VIP] Troll: They have gifted 15 ranks so far!", "Steve: +5 Karma!",
            "Friend > Steve joined.", "[MVP+] Steve: I found a Mystery Box!", "§eThe game starts in §r§c1§r§e second!"})
            assertNull(HypixelChat.spam(line), line);
    }

    /** The regression set from the 1.9.1 research (resources/hypixel/chat-fixtures.json: Hypixel lines per mode, and tricky negatives). */
    @Test void researchFixtures() throws Exception {
        com.google.gson.JsonArray fixtures;
        try (var in = getClass().getResourceAsStream("/hypixel/chat-fixtures.json")) {
            fixtures = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray();
        }
        int checked = 0;
        for (var element : fixtures) {
            var fixture = element.getAsJsonObject();
            String raw = fixture.get("raw").getAsString(), kind = fixture.get("kind").getAsString(), what = kind + ": " + raw;
            HypixelChat.Spam spam = HypixelChat.spam(raw);
            switch (kind) {
                case "game_end" -> assertTrue(HypixelChat.gameEnd(raw), what);
                case "friend_request" -> assertNotNull(HypixelChat.friendRequest(raw), what);
                case "tip" -> assertEquals(HypixelChat.Spam.TIPS, spam, what);
                case "lobby_join" -> assertEquals(HypixelChat.Spam.LOBBY_JOIN, spam, what);
                case "spam" -> assertNotNull(spam, what);
                case "none" -> {
                    assertFalse(HypixelChat.gameEnd(raw), what);
                    assertNull(HypixelChat.friendRequest(raw), what);
                    assertNull(spam, what);
                }
                default -> fail("unknown kind " + what);
            }
            if (!kind.equals("game_end")) assertFalse(HypixelChat.gameEnd(raw), what);
            checked++;
        }
        assertTrue(checked >= 50, "fixtures read: " + checked);
    }

    @Test void modApiPingPacketsMatchTheSpec() {
        assertArrayEquals(new byte[] {1}, RealPing.request()); // VarInt version 1
        byte[] pong = {1, 1, 4, 'p', 'o', 'n', 'g'};            // success, version 1, "pong"
        assertEquals(1, RealPing.decode(pong));
        assertEquals(-3, RealPing.decode(new byte[] {0, 3}));   // error: rate limited
        assertEquals(-1, RealPing.decode(new byte[] {0, 1}));   // error: disabled
        assertEquals(0, RealPing.decode(new byte[] {1, 2, 4, 'p', 'o', 'n', 'g'})); // another version: ignored
        assertEquals(0, RealPing.decode(new byte[] {1, 1, 9, 'p'})); // cut short
        assertEquals(0, RealPing.decode(new byte[0]));
    }

    @Test void realPingOnHypixelHidesOthersAndMeasuresYours() {
        RealPing.tick(game, connection, 0);
        assertTrue(RealPing.fakeListing());
        assertEquals(RealPing.NONE, RealPing.own(1), "no measured ping yet: no number");
        assertEquals(RealPing.NONE, RealPing.row(1, false));
        RealPing.tick(game, connection, 2 * S);
        assertEquals(1, payloads.size(), "a Mod API ping went out");
        assertTrue(RealPing.receive(RealPing.CHANNEL, new byte[] {1, 1, 4, 'p', 'o', 'n', 'g'}, 2 * S + 42_000_000L));
        assertEquals(42, RealPing.own(1));
        assertEquals(42, RealPing.row(1, true));
        assertEquals(RealPing.NONE, RealPing.row(1, false));
        RealPing.tick(game, connection, 3 * S);
        assertEquals(1, payloads.size(), "at most one ping every 5 s");
        RealPing.tick(game, connection, 7 * S + 1);
        assertEquals(2, payloads.size());
        assertFalse(RealPing.receive("minecraft:brand", new byte[] {1}, 8 * S));
    }

    @Test void fakeListingIsDetectedOnAnyNetworkButNotLocally() {
        server = "play.example.net";
        address = null; // no status ping in this test
        assertFalse(RealPing.fakeListed(new int[] {1, 0, 1}, null));
        assertTrue(RealPing.fakeListed(new int[] {1, 0, 1}, new InetSocketAddress("203.0.113.5", 25565)));
        assertFalse(RealPing.fakeListed(new int[] {1, 0, 1}, new InetSocketAddress("192.0.2.1", 25565)), "LAN: 1 ms can be real");
        assertFalse(RealPing.fakeListed(new int[] {1, 0, 1}, new InetSocketAddress("127.0.0.1", 25565)));
        assertFalse(RealPing.fakeListed(new int[] {1}, new InetSocketAddress("203.0.113.5", 25565)), "one player proves nothing");
        assertFalse(RealPing.fakeListed(new int[] {1, 37, 1}, new InetSocketAddress("203.0.113.5", 25565)));
        listed = new int[] {40, 55};
        RealPing.tick(game, connection, 0);
        assertFalse(RealPing.fakeListing());
        assertEquals(40, RealPing.own(40));
        assertEquals(55, RealPing.row(55, false));
    }

    @Test void statusPingAgainstALocalServer() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread server = new Thread(() -> {
                try (Socket s = listener.accept()) {
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    byte[] handshake = read(in), request = read(in);
                    assertEquals(0, handshake[0]);
                    assertEquals(1, handshake[handshake.length - 1], "next state: status");
                    assertArrayEquals(new byte[] {0}, request);
                    byte[] json = "{\"version\":{\"name\":\"1.8\",\"protocol\":47}}".getBytes(StandardCharsets.UTF_8);
                    ByteArrayOutputStream status = new ByteArrayOutputStream();
                    status.write(0);
                    RealPing.writeVarInt(status, json.length);
                    status.write(json);
                    write(out, status.toByteArray());
                    byte[] ping = read(in);
                    assertEquals(1, ping[0]);
                    Thread.sleep(30);
                    write(out, ping); // pong: the same id and payload
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            server.start();
            int ms = RealPing.statusPing(new InetSocketAddress(InetAddress.getLoopbackAddress(), listener.getLocalPort()), "localhost", 3000);
            server.join(5000);
            assertTrue(ms >= 25 && ms < 2000, "status ping " + ms + " ms");
        }
    }

    private static byte[] read(InputStream in) throws Exception {
        int length = 0;
        for (int shift = 0; ; shift += 7) { int b = in.read(); length |= (b & 0x7F) << shift; if ((b & 0x80) == 0) break; }
        byte[] body = new byte[length];
        new DataInputStream(in).readFully(body);
        return body;
    }

    private static void write(DataOutputStream out, byte[] body) throws Exception {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        RealPing.writeVarInt(packet, body.length);
        packet.write(body);
        out.write(packet.toByteArray());
        out.flush();
    }

    @Test void autoGgPlayAgainAndTheRateLimit() {
        ModuleManager.getInstance().getModule(HypixelMods.PLAY_AGAIN).setEnabled(true);
        ModuleManager.getInstance().getModule(HypixelMods.AUTO_TIP).setEnabled(false);
        ((com.thelads.core.config.TextOption) ModuleManager.getInstance().getModule(HypixelMods.AUTO_GG).getOption("Second Message")).setValue("good game");
        ((com.thelads.core.config.SliderOption) ModuleManager.getInstance().getModule(HypixelMods.AUTO_GG).getOption("Second Message seconds")).setValue(0);
        HypixelMods.tick(game, world, connection, 0);
        HypixelMods.tick(game, world, connection, 2 * S);
        assertEquals(List.of("/locraw"), sent);
        assertTrue(HypixelMods.chat("{\"server\":\"mini1A\",\"gametype\":\"BEDWARS\",\"mode\":\"BEDWARS_EIGHT_ONE\",\"map\":\"Aquarium\"}", 3 * S),
            "our own /locraw answer is hidden");
        assertFalse(HypixelMods.chat("                       1st Killer - Steve - 7", 10 * S));
        assertFalse(HypixelMods.chat("                       2nd Killer - Alex - 3", 10 * S));
        assertFalse(HypixelMods.chat("§6This game has been recorded. §r§e§lClick here to watch the Replay!", 10 * S));
        for (long t = 10 * S; t <= 20 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/locraw", "/ac gg", "/ac good game", "/play bedwars_eight_one"), sent, "once per game, in order");
        HypixelMods.chat("                       1st Killer - Steve - 7", 21 * S);
        for (long t = 21 * S; t <= 30 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(4, sent.size(), "a second summary in the same world says nothing");
        // A new world (the next game) re-arms it.
        Object next = new Object();
        sent.clear();
        HypixelMods.tick(game, next, connection, 40 * S);
        HypixelMods.chat("                                Winner - Steve", 41 * S);
        for (long t = 41 * S; t <= 50 * S; t += S / 20) HypixelMods.tick(game, next, connection, t);
        assertEquals(List.of("/locraw", "/ac gg", "/ac good game"), sent, "no /play without a /locraw answer");
    }

    @Test void commandsAreAtLeastOneAndAHalfSecondsApart() {
        List<Long> times = new ArrayList<>();
        LadsGameBridge timed = new DefaultGameBridge() {
            long now;
            @Override public String getServerAddress() { return server; }
            @Override public void sendChatLine(String text) { times.add(now); }
            @Override public int getPing() { return (int) (now = clock); }
        };
        HypixelMods.tick(timed, world, connection, 0);
        for (int i = 0; i < 5; i++) HypixelMods.chat("Friend request from Player" + i, S);
        for (clock = S; clock <= 20 * S; clock += S / 20) { timed.getPing(); HypixelMods.tick(timed, world, connection, clock); }
        assertEquals(5, times.size());
        for (int i = 1; i < times.size(); i++) assertTrue(times.get(i) - times.get(i - 1) >= HypixelMods.GAP, times.toString());
    }
    private long clock;

    @Test void autoFriendOncePerPlayerAndAutoTipStopsWhenUnknown() {
        HypixelMods.tick(game, world, connection, 0);
        HypixelMods.chat("Friend request from [VIP] Steve", S);
        HypixelMods.chat("Friend request from [VIP] Steve", 2 * S);
        for (long t = S; t <= 5 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/friend accept Steve"), sent);
        sent.clear();
        HypixelMods.tick(game, world, connection, 31 * S);
        assertEquals(List.of("/tip all"), sent, "the first tip half a minute after joining");
        HypixelMods.chat("§cUnknown command. Type \"/help\" for help.", 32 * S);
        assertTrue(HypixelMods.tipStopped());
        for (long t = 32 * S; t <= 3600 * S; t += 10 * S) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/tip all"), sent, "no more tips this session");
        HypixelMods.tick(game, world, new Object(), 3601 * S);
        assertFalse(HypixelMods.tipStopped(), "a new connection tries again");
    }

    @Test void autoWhoAndCleanupOnlyOnHypixel() {
        ModuleManager.getInstance().getModule(HypixelMods.AUTO_WHO).setEnabled(true);
        HypixelMods.tick(game, world, connection, 0);
        assertTrue(HypixelMods.chat("[MVP+] Steve joined the lobby!", S));
        assertFalse(HypixelMods.chat("[MVP+] Steve: hello", S));
        HypixelMods.chat("§eThe game starts in §r§c1§r§e second!", S);
        for (long t = S; t <= 5 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/who"), sent);
        ModuleManager.getInstance().getModule(HypixelMods.CLEANUP).getOption("Lobby joins").load(new com.google.gson.JsonPrimitive(false));
        assertFalse(HypixelMods.chat("[MVP+] Steve joined the lobby!", 6 * S), "that kind switched off");
        server = "play.example.net";
        sent.clear();
        HypixelMods.tick(game, world, connection, 7 * S);
        assertFalse(HypixelMods.chat("They have gifted 15 ranks so far!", 7 * S), "off Hypixel nothing is hidden");
        HypixelMods.chat("Friend request from Steve", 7 * S);
        for (long t = 7 * S; t <= 60 * S; t += S / 2) HypixelMods.tick(game, world, connection, t);
        assertTrue(sent.isEmpty(), "off Hypixel nothing is sent: " + sent);
        HypixelSafetyManager.qaOverride = true;
        assertTrue(HypixelSafetyManager.isHypixel(), "the QA override makes any server Hypixel");
    }
}
