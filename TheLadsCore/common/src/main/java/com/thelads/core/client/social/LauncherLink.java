package com.thelads.core.client.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.LadsGameBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The launcher's Friends page while this game runs. The backend keeps one social link per account (a second one replaces the first),
 * so the launcher stays off it while a game runs: the game writes what the page shows to {@code .lads-social/friends.json} and runs
 * the page's Invite / Join / Accept from {@code .lads-social/command.json}. A game the launcher started from its Friends page carries
 * {@code -Dlads.social.join=<host>} or {@code -Dlads.social.invite=<invite id> -Dlads.social.invite.from=<host>}: done once, as soon as the link is up.
 * The page also chats (DMs, pictures it puts in {@code .lads-social/outbox/}) and runs the party through the game.
 */
final class LauncherLink {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore/LauncherLink");
    static final String FOLDER = ".lads-social", FRIENDS = "friends.json", COMMAND = "command.json", OUTBOX = "outbox";
    /** The last DMs per friend the page gets (the game keeps 200). */
    static final int PAGE_DMS = 40;
    private static final Pattern OUTBOX_FILE = Pattern.compile("[A-Za-z0-9_-]{1,64}\\.(?:jpg|png)");
    static final String JOIN_PROPERTY = "lads.social.join", INVITE_PROPERTY = "lads.social.invite", INVITE_FROM_PROPERTY = "lads.social.invite.from";
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private String startJoin = System.getProperty(JOIN_PROPERTY), startInvite = System.getProperty(INVITE_PROPERTY),
            startInviteFrom = System.getProperty(INVITE_FROM_PROPERTY);
    private int connectedSeconds;
    private String written;

    /** Once a second on the game thread (SocialState.sample). */
    void second(SocialState state, Path gameDir, boolean connected) {
        if (gameDir == null) return;
        connectedSeconds = connected ? connectedSeconds + 1 : 0;
        // A second after the hello: our presence (the backend's compatibility check reads its game) has gone out first
        if (connectedSeconds == 2) startup(state);
        Path folder = gameDir.resolve(FOLDER);
        if (connected) command(state, folder);
        write(state, folder, connected);
    }

    private void startup(SocialState state) {
        UUID host = SocialClient.parseUuid(startJoin);
        if (host != null) {
            LOGGER.info("LauncherLink: asking {} to join (launcher's Friends page)", host);
            state.requestJoin(host);
        }
        UUID from = SocialClient.parseUuid(startInviteFrom);
        if (startInvite != null && ID.matcher(startInvite).matches() && from != null) {
            LOGGER.info("LauncherLink: accepting {}'s invite (launcher's Friends page)", from);
            state.acceptLauncherInvite(startInvite, from);
        }
        startJoin = startInvite = startInviteFrom = null;
    }

    private static void command(SocialState state, Path folder) {
        Path file = folder.resolve(COMMAND);
        if (!Files.isRegularFile(file)) return;
        JsonObject cmd;
        try {
            if (Files.size(file) > 4096) throw new IllegalArgumentException("too big");
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Files.deleteIfExists(file);
            cmd = JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            try { Files.deleteIfExists(file); } catch (Exception ignored) {}
            LOGGER.warn("LauncherLink: unreadable command ({})", e.getMessage());
            return;
        }
        String t = str(cmd, "t");
        UUID to = SocialClient.parseUuid(str(cmd, "to"));
        if ("dm".equals(t)) {
            String text = str(cmd, "text");
            if (to != null && text != null) state.sendDm(to, text);
        } else if ("dm_img".equals(t)) {
            String name = str(cmd, "file");
            if (to != null && name != null && OUTBOX_FILE.matcher(name).matches()) sendPicture(state, to, folder.resolve(OUTBOX).resolve(name));
        } else if ("party_invite".equals(t)) {
            if (to != null) state.inviteToParty(to);
        } else if ("party_accept".equals(t) || "party_decline".equals(t)) {
            UUID from = SocialClient.parseUuid(str(cmd, "from"));
            if ("party_accept".equals(t)) state.acceptPartyInvite(from);
            else state.declinePartyInvite(from);
        } else if ("party_leave".equals(t)) {
            state.leaveParty();
        } else if ("party_kick".equals(t)) {
            state.kickFromParty(to);
        } else if ("party_promote".equals(t)) {
            state.promoteInParty(to);
        } else if ("invite".equals(t)) {
            if (to != null) state.invite(to);
        } else if ("join".equals(t)) {
            UUID host = SocialClient.parseUuid(str(cmd, "host"));
            if (host != null) state.requestJoin(host);
        } else if ("accept".equals(t) || "decline".equals(t)) {
            String id = str(cmd, "inviteId");
            if (id != null && ID.matcher(id).matches()) {
                if ("accept".equals(t)) state.acceptInvite(id);
                else state.declineInvite(id);
            }
        }
    }

    /** A picture the page made (a JPEG/PNG under the upload cap): read, removed, sent off the game thread. */
    private static void sendPicture(SocialState state, UUID to, Path file) {
        DmMedia.background(() -> {
            try {
                if (!Files.isRegularFile(file) || Files.size(file) > DmMedia.MAX_UPLOAD_BYTES) throw new java.io.IOException("missing or too big");
                byte[] picture = Files.readAllBytes(file);
                Files.deleteIfExists(file);
                state.sendPicture(to, picture);
            } catch (Exception e) {
                try { Files.deleteIfExists(file); } catch (Exception ignored) {}
                LOGGER.warn("LauncherLink: picture {} not sent ({})", file.getFileName(), e.getMessage());
            }
        });
    }

    private void write(SocialState state, Path folder, boolean connected) {
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("connected", connected);
        o.addProperty("canInvite", connected && state.canInvite());
        JsonArray friends = new JsonArray();
        for (FriendEntry f : state.friends()) {
            JsonObject e = new JsonObject();
            e.addProperty("uuid", f.id().toString());
            e.addProperty("name", f.name());
            if (f.nick() != null && !f.nick().isEmpty()) e.addProperty("nick", f.nick());
            Presence p = f.presence() == null ? Presence.OFFLINE : f.presence();
            JsonObject presence = new JsonObject();
            presence.addProperty("state", p.state());
            if (p.server() != null) presence.addProperty("server", p.server());
            if (p.world() != null) {
                JsonObject w = new JsonObject();
                w.addProperty("name", p.world().name());
                w.addProperty("scope", p.world().scope());
                w.addProperty("slots", p.world().slots());
                w.addProperty("used", p.world().used());
                presence.add("world", w);
            }
            e.add("presence", presence);
            List<SocialState.Dm> dms = state.dms(f.id());
            if (!dms.isEmpty()) {
                JsonArray chat = new JsonArray();
                for (SocialState.Dm dm : dms.subList(Math.max(0, dms.size() - PAGE_DMS), dms.size())) {
                    JsonObject m = new JsonObject();
                    if (dm.from() != null) m.addProperty("from", dm.from().toString());
                    m.addProperty("text", dm.text());
                    m.addProperty("ts", dm.ts());
                    if (dm.img() != null) m.addProperty("img", dm.img());
                    if (dm.gif() != null) m.addProperty("gif", dm.gif());
                    chat.add(m);
                }
                e.add("dms", chat);
            }
            friends.add(e);
        }
        o.add("friends", friends);
        JsonArray invites = new JsonArray();
        for (SocialState.Invite i : state.invites()) {
            JsonObject e = new JsonObject();
            e.addProperty("inviteId", i.inviteId());
            if (i.from() != null) e.addProperty("from", i.from().toString());
            if (i.isServer()) e.addProperty("server", i.serverAddress());
            else if (i.worldName() != null) e.addProperty("world", i.worldName());
            invites.add(e);
        }
        o.add("invites", invites);
        LadsGameBridge.Session session = state.session();
        if (session != null && session.uuid() != null) o.addProperty("self", session.uuid().toString());
        SocialState.Party party = state.party();
        if (party != null) {
            JsonObject p = new JsonObject();
            p.addProperty("leader", party.leader().toString());
            JsonArray members = new JsonArray();
            for (SocialState.PartyMember m : party.members()) {
                JsonObject e = new JsonObject();
                e.addProperty("id", m.id().toString());
                e.addProperty("name", m.name());
                members.add(e);
            }
            p.add("members", members);
            o.add("party", p);
        }
        JsonArray partyInvites = new JsonArray();
        for (SocialState.PartyInvite i : state.partyInvites()) {
            JsonObject e = new JsonObject();
            e.addProperty("from", i.from().toString());
            e.addProperty("name", i.name());
            partyInvites.add(e);
        }
        o.add("partyInvites", partyInvites);
        String json = o.toString();
        if (json.equals(written)) return;
        try {
            Files.createDirectories(folder);
            Path tmp = folder.resolve(FRIENDS + ".tmp");
            Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, folder.resolve(FRIENDS), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            written = json;
        } catch (Exception e) {
            if (written != null) LOGGER.warn("LauncherLink: friends list not written ({})", e.getMessage());
            written = json; // tried; the next change tries again
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }
}
