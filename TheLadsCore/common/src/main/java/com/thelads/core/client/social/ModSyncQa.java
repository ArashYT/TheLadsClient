package com.thelads.core.client.social;

import com.thelads.core.client.social.modsync.ModSyncFlow;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only, inert unless LADS_QA_MODSYNC=host|guest is set (with LADS_P2P_PEER = the other game's name) and the social backend is a
 * local dev one (ws://): the ModSync invite between two games that the real launcher started, with no hand on either keyboard but for
 * the guest's "trust this host's mods?" question, which stays the player's. Host: befriends the peer, opens its world to friends
 * once it is in one, invites the peer once. Guest: accepts the friend request and the invite. The restart with the host's mods, the
 * host's new invite and its acceptance are the product's own. Logs "Lads modsync QA: ..." when the state changes. Everything goes
 * through the public SocialState API.
 */
final class ModSyncQa {
    private static final String ROLE = System.getenv("LADS_QA_MODSYNC"), PEER = System.getenv("LADS_P2P_PEER");
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static int seconds;
    private static boolean opened, invited;
    private static String said = "";

    private ModSyncQa() {}

    /** Once a second (SocialState.sample). */
    static void second(SocialState s, SocialBridge bridge, Collection<SocialState.Invite> invites) {
        if (ROLE == null || PEER == null || !s.client().local()) return;
        seconds++;
        if (!"Connected".equals(s.connectionStatus())) { say(ROLE + ": social " + s.connectionStatus()); return; }
        FriendEntry peer = null;
        for (FriendEntry f : s.friends()) if (PEER.equalsIgnoreCase(f.name())) peer = f;
        if ("host".equals(ROLE)) {
            if (peer == null) {
                if (seconds % 10 == 1) s.addFriend(PEER); // again until the peer is online to be found
                say("host: asking " + PEER + " to be friends");
            } else if (bridge.worldId() == null || !bridge.inWorldOrServer()) { // the world still loads: opening it now fails
                say("host: friends with " + PEER + ", not in a world yet");
            } else if (!s.hostSession().isHosting()) {
                if (!opened) {
                    opened = true;
                    HostOptions o = new HostOptions();
                    o.scope = HostOptions.Scope.FRIENDS;
                    o.approval = HostOptions.Approval.AUTO_FRIENDS;
                    o.backupOnOpen = false;
                    s.startHosting(o);
                }
                say("host: opening the world to friends");
            } else if (!invited) {
                boolean online = peer.presence() != null && peer.presence().online();
                if (online) { invited = true; s.invite(peer.id()); }
                say("host: hosting, " + PEER + (online ? " invited" : " is offline"));
            } else {
                say("host: hosting, modsHash " + s.gameInfo().modsHash() + ", guests " + s.hostSession().activeGuestCount()
                    + ", " + PEER + " " + (peer.presence() == null ? "?" : peer.presence().state()));
            }
        } else if ("guest".equals(ROLE)) {
            String restarted = System.getProperty(ModSyncFlow.HOST_PROPERTY);
            if (peer == null) {
                for (FriendEntry in : s.incoming()) if (PEER.equalsIgnoreCase(in.name())) s.accept(in.id());
                say("guest: waiting for " + PEER + "'s friend request");
            } else if (s.trustQuestion() != null) {
                say("guest: trust question: " + s.trustQuestion().title());
            } else if (s.isGuestSessionActive() || s.connectingTo() != null) {
                say("guest: joining " + s.connectingTo() + " (" + s.connectingStatus() + "), session " + s.isGuestSessionActive()
                    + ", in world " + bridge.inWorldOrServer() + ", restarted for " + restarted + ", fabric.addMods "
                    + System.getProperty("fabric.addMods"));
            } else {
                // A game the launcher restarted for a host accepts that host's invite by itself (SocialState.rejoinHost)
                if (restarted == null) for (SocialState.Invite invite : invites) {
                    if (!invite.isServer() && peer.id().equals(invite.from())) { s.acceptInvite(invite.inviteId()); break; }
                }
                say("guest: friends with " + PEER + ", invites " + invites.size() + ", restarted for " + restarted);
            }
        }
    }

    private static void say(String text) {
        if (text.equals(said)) return;
        said = text;
        LOGGER.info("Lads modsync QA: {}", text);
    }
}
