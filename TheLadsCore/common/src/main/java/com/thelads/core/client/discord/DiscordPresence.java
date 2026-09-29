package com.thelads.core.client.discord;

import java.nio.charset.StandardCharsets;

/** Immutable, privacy-filtered data passed from Minecraft's thread to the IPC worker. */
public record DiscordPresence(String details, String state, long startTimestamp) {
    public enum Place { MENU, SINGLEPLAYER, MULTIPLAYER }
    public record Privacy(boolean serverAddress, boolean worldName, boolean dimension, boolean elapsed, int detailLevel) {}
    public record Game(Place place, String version, String serverAddress, String worldName, String dimension, long started) {}

    public static DiscordPresence from(Game game, Privacy privacy) {
        String details = privacy.detailLevel() == 2 ? "Playing Minecraft" : switch (game.place()) {
            case MENU -> "In the menus";
            case SINGLEPLAYER -> "Playing singleplayer";
            case MULTIPLAYER -> "Playing multiplayer";
        };
        String state = "Minecraft " + game.version();
        if (privacy.detailLevel() == 0) {
            if (game.place() == Place.MULTIPLAYER && privacy.serverAddress() && !blank(game.serverAddress()))
                state = game.serverAddress();
            else if (game.place() == Place.SINGLEPLAYER && privacy.worldName() && !blank(game.worldName()))
                state = game.worldName();
            if (game.place() != Place.MENU && privacy.dimension() && !blank(game.dimension()))
                state += " | " + game.dimension();
        }
        return new DiscordPresence(text(details), text(state), privacy.elapsed() ? Math.max(0, game.started()) : 0);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    /** Discord's native RPC text fields are 128 bytes, including the terminating zero. */
    static String text(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder();
        int bytes = 0;
        for (int codePoint : value.codePoints().toArray()) {
            if (Character.isISOControl(codePoint)) continue;
            String character = new String(Character.toChars(codePoint));
            int size = character.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > 127) break;
            result.append(character); bytes += size;
        }
        return result.toString();
    }
}
