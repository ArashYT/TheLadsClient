package com.thelads.core.v26_2.social;

import com.thelads.core.client.social.SocialBridge;
import java.util.UUID;

public final class SocialBridge26Checks {
    private static int passed;

    public static void main(String[] args) {
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable ignored) {
        }

        SocialBridge26 bridge = new SocialBridge26();

        // 1. Loopback scope flag
        require(!SocialBridge26.isLoopbackScope(), "initial loopback scope is false");
        SocialBridge26.setLoopbackScope(true);
        require(SocialBridge26.isLoopbackScope(), "loopback scope sets to true");
        SocialBridge26.setLoopbackScope(false);
        require(!SocialBridge26.isLoopbackScope(), "loopback scope resets to false");

        // 2. Login filter predicate
        require(SocialBridge26.getLoginFilter() == null, "initial login filter is null");
        UUID allowed = UUID.randomUUID();
        UUID denied = UUID.randomUUID();
        bridge.setLoginFilter(id -> id != null && id.equals(allowed));
        require(SocialBridge26.getLoginFilter() != null, "login filter set");
        require(SocialBridge26.getLoginFilter().test(allowed), "login filter allows invited UUID");
        require(!SocialBridge26.getLoginFilter().test(denied), "login filter rejects uninvited UUID");
        require(!SocialBridge26.getLoginFilter().test(null), "login filter rejects null UUID");
        bridge.setLoginFilter(null);

        // 3. GameInfo
        SocialBridge.GameInfo info = bridge.gameInfo();
        require(info != null, "gameInfo non-null");
        require("fabric".equals(info.loader()), "loader is fabric");
        require("plain".equals(info.profileKind()), "profileKind is plain");

        // 4. Friends button text
        require("Friends".equals(SocialBridge26.friendsButtonText().getString()), "friendsButtonText default");

        // 5. Frozen action ids go to SocialState without throwing (no game, no connection)
        String[] frozenActions = {
            "invite_accept:inv_123",
            "invite_decline:inv_123",
            "join_accept:req_456",
            "join_deny:req_456",
            "friend_accept:00000000-0000-0000-0000-000000000001",
            "friend_decline:00000000-0000-0000-0000-000000000001",
            "open_friends",
            "dm:00000000-0000-0000-0000-000000000001"
        };
        for (String act : frozenActions) {
            SocialBridge26.onChatActionClicked(act);
            passed++;
        }

        // 6. Mute set clearing on disconnect
        UUID mutedGuest = UUID.randomUUID();
        SocialBridge26.onWorldDisconnect();
        require(!SocialBridge26.isMuted(mutedGuest), "initially unmuted");
        P2PHostState26.muteGuest(mutedGuest);
        require(SocialBridge26.isMuted(mutedGuest), "guest marked muted");
        SocialBridge26.onWorldDisconnect();
        require(!SocialBridge26.isMuted(mutedGuest), "guest unmuted on world disconnect");

        System.out.println("PASS " + passed + " SocialBridge26 checks");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed++;
    }
}
