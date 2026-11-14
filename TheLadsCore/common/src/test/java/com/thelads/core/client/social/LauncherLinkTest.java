package com.thelads.core.client.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** The launcher's Friends page through a running game: chat, pictures and the party in friends.json and command.json. */
public class LauncherLinkTest {
    private final UUID self = UUID.randomUUID(), alice = UUID.randomUUID();
    @TempDir Path dir;
    private SocialState state;
    private List<JsonObject> sent;
    private LauncherLink link;

    @BeforeEach
    public void setup() {
        SocialBridge.set(new SocialBridge() {});
        LadsGameBridge.set(new DefaultGameBridge() {
            @Override public Session session() { return new Session(self, "Me", "token"); }
        });
        sent = new CopyOnWriteArrayList<>();
        state = new SocialState();
        state.setClientSender(sent::add);
        state.addFriendLocally(new FriendEntry(alice, "Alice", null, Presence.OFFLINE, 0));
        link = new LauncherLink();
    }

    @AfterEach
    public void cleanup() {
        SocialBridge.set(new SocialBridge() {});
        LadsGameBridge.set(new DefaultGameBridge());
    }

    private JsonObject page() throws Exception {
        link.second(state, dir, true);
        return JsonParser.parseString(Files.readString(dir.resolve(".lads-social/friends.json"))).getAsJsonObject();
    }

    private void command(String json) throws Exception {
        Files.createDirectories(dir.resolve(".lads-social"));
        Files.writeString(dir.resolve(".lads-social/command.json"), json);
        link.second(state, dir, true);
    }

    private JsonObject last(String t) {
        for (int i = sent.size() - 1; i >= 0; i--) if (t.equals(sent.get(i).get("t").getAsString())) return sent.get(i);
        return null;
    }

    @Test
    public void pageGetsChatPartyAndSelf() throws Exception {
        state.onMessage(SocialProtocol.msg("dm", "from", alice.toString(), "text", "hi", "ts", 5L));
        JsonObject members = SocialProtocol.msg("party_state", "leader", self.toString());
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (UUID id : new UUID[]{self, alice}) {
            JsonObject m = new JsonObject();
            m.addProperty("id", id.toString());
            m.addProperty("name", id.equals(self) ? "Me" : "Alice");
            arr.add(m);
        }
        members.add("members", arr);
        state.onMessage(members);
        JsonObject o = page();
        assertEquals(self.toString(), o.get("self").getAsString());
        JsonObject friend = o.getAsJsonArray("friends").get(0).getAsJsonObject();
        JsonObject dm = friend.getAsJsonArray("dms").get(0).getAsJsonObject();
        assertEquals(alice.toString(), dm.get("from").getAsString());
        assertEquals("hi", dm.get("text").getAsString());
        assertEquals(self.toString(), o.getAsJsonObject("party").get("leader").getAsString());
        assertEquals(2, o.getAsJsonObject("party").getAsJsonArray("members").size());
    }

    @Test
    public void commandsChatAndRunTheParty() throws Exception {
        command("{\"t\":\"dm\",\"to\":\"" + alice + "\",\"text\":\"hello\"}");
        assertEquals("hello", last("dm").get("text").getAsString());
        command("{\"t\":\"party_invite\",\"to\":\"" + alice + "\"}");
        assertEquals(alice.toString(), last("party_invite").get("to").getAsString());
        state.onMessage(SocialProtocol.msg("party_invite_in", "from", alice.toString(), "name", "Alice"));
        assertEquals("Alice", page().getAsJsonArray("partyInvites").get(0).getAsJsonObject().get("name").getAsString());
        command("{\"t\":\"party_accept\",\"from\":\"" + alice + "\"}");
        assertEquals(alice.toString(), last("party_accept").get("from").getAsString());
    }

    @Test
    public void pictureFromTheOutboxGoesAsDmImgParts() throws Exception {
        Path outbox = Files.createDirectories(dir.resolve(".lads-social/outbox"));
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
        Files.write(outbox.resolve("shot1.jpg"), jpeg);
        command("{\"t\":\"dm_img\",\"to\":\"" + alice + "\",\"file\":\"shot1.jpg\"}");
        for (int i = 0; i < 100 && last("dm_img") == null; i++) Thread.sleep(20);
        JsonObject img = last("dm_img");
        assertNotNull(img);
        assertEquals(alice.toString(), img.get("to").getAsString());
        assertFalse(Files.exists(outbox.resolve("shot1.jpg")));
    }

    @Test
    public void outboxNamesOutsideTheFolderAreRefused() throws Exception {
        Files.writeString(dir.resolve("secret.jpg"), "x", StandardCharsets.UTF_8);
        command("{\"t\":\"dm_img\",\"to\":\"" + alice + "\",\"file\":\"../../secret.jpg\"}");
        Thread.sleep(200);
        assertNull(last("dm_img"));
        assertTrue(Files.exists(dir.resolve("secret.jpg")));
    }
}
