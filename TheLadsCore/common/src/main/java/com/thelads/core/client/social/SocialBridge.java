package com.thelads.core.client.social;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Per-version hooks for friends + P2P hosting (contract, see docs/superpowers/specs/2026-10-23-friends-p2p-design.md).
 * Each version sets its implementation at client init next to LadsGameBridge.set; the defaults do nothing.
 */
public interface SocialBridge {
    final class Holder {
        private static volatile SocialBridge instance = new SocialBridge() {};
        private Holder() {}
    }

    static SocialBridge get() {
        return Holder.instance;
    }

    static void set(SocialBridge bridge) {
        Holder.instance = bridge;
    }

    record GuestInfo(UUID id, String name, int ping, boolean op, String gameMode) {}

    enum GuestAction { KICK, BAN, OP, DEOP, SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR, TP_TO_ME, MUTE }

    /** profileKind: "plain" or "pack"; packId/packVersion empty for plain profiles. */
    record GameInfo(String mc, String loader, String profileKind, String packId, String packVersion, String modsHash, String lads) {}

    /** In a singleplayer world whose integrated server runs and is not published yet. */
    default boolean canHost() { return false; }

    /** Publishes the integrated server on 127.0.0.1 only (Friends/Invite scope, no LAN broadcast); the port, or -1. */
    default int openToFriends(HostOptions options) { return -1; }

    /** Vanilla Open to LAN with these options (LAN scope). */
    default void openToLan(HostOptions options) {}

    /** Stops accepting guests and disconnects the ones online with this reason. */
    default void closeHosting(String reason) {}

    /** Game mode, op level, PvP, difficulty, gamerules, view cap: applied on the server thread. */
    default void applyHostOptions(HostOptions options) {}

    default List<GuestInfo> guests() { return Collections.emptyList(); }

    default void guestAction(UUID id, GuestAction action) {}

    /** The integrated server refuses a login whose UUID fails this with "You weren't invited to this world". */
    default void setLoginFilter(Predicate<UUID> allowed) {}

    /** Joins the host through the guest's loopback proxy; the connect screen shows displayName, not the address. */
    default void connectToLocal(int port, String displayName) {}

    /** A public-server invite: plain connect to that address. */
    default void connectToServer(String address) {}

    default boolean inWorldOrServer() { return false; }

    /** Leaves the current world/server cleanly (saving a singleplayer world), then runs then on the game thread. */
    default void leaveWorld(Runnable then) { then.run(); }

    default GameInfo gameInfo() { return new GameInfo("", "", "plain", "", "", "", ""); }

    default void openFriendsScreen() {}

    default void openHostScreen() {}

    /** A Lads toast; the actions are ids for SocialState (null = no button). */
    default void toast(String title, String body, String acceptAction, String denyAction) {}

    /** A chat line whose click runs the client-side action id (never sent to the server). */
    default void chatAction(String text, String actionId) {}

    /** Stable id of the open singleplayer world (folder name), or null. */
    default String worldId() { return null; }

    default String worldName() { return null; }
}
