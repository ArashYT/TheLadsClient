package com.thelads.core.client.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.util.LaunchToken;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a live account switch gets the target's access token. The launcher owns the accounts and their refresh tokens: the game asks
 * it over a local pipe, with the key it got on stdin beside its launch token ({@link LaunchToken}), and gets one fresh Minecraft access
 * token, what it already gets at launch. Offline to offline needs nobody; anything else needs the launcher's word, because it is
 * what refuses offline accounts in release builds.
 */
public class AccountTokens {
    /** Why the switch cannot go ahead, in words for the switcher's status line. */
    public static final class Refused extends Exception {
        public Refused(String message) {
            super(message);
        }
    }

    private static final Pattern HAND_OFF = Pattern.compile("([A-Za-z0-9-]{1,128}) ([A-Za-z0-9]{16,128})");

    public String fetch(LadsAccount target, boolean sessionIsOffline) throws Refused {
        if (target.isOffline() && sessionIsOffline) return "0";
        Matcher handOff = HAND_OFF.matcher(handOff());
        if (!handOff.matches()) throw new Refused("Start the game from The Lads Launcher to switch to " + target.getUsername() + ".");
        JsonObject request = new JsonObject();
        request.addProperty("key", handOff.group(2));
        request.addProperty("op", "token");
        request.addProperty("username", target.getUsername());
        request.addProperty("uuid", target.getUuid());
        request.addProperty("type", target.getType());
        try {
            return parseReply(ask(handOff.group(1), request.toString()));
        } catch (IOException e) {
            throw new Refused("The launcher that started this game is closed.");
        }
    }

    /**
     * A modpack the launcher withholds its pipe from (third-party mods could ask it for every saved account's token): nothing can
     * switch in place there, the launcher applies the choice at the next launch. Outside a modpack, no hand-off is another launcher's game.
     */
    public boolean nextLaunchOnly() {
        return Boolean.getBoolean("lads.modpack") && !HAND_OFF.matcher(handOff()).matches();
    }

    /** Tells the launcher to pick up the request the switcher just wrote, so its selected account follows now and not at the game's exit. */
    public void switched() {
        Matcher handOff = HAND_OFF.matcher(handOff());
        if (!handOff.matches()) return;
        JsonObject request = new JsonObject();
        request.addProperty("key", handOff.group(2));
        request.addProperty("op", "switched");
        Thread thread = new Thread(() -> {
            try { ask(handOff.group(1), request.toString()); }
            catch (IOException ignored) {} // the launcher still reads the request when the game exits
        }, "Lads Account Switched");
        thread.setDaemon(true);
        thread.start();
    }

    /** The launcher's answer: its token, or {@link Refused} with its reason. */
    private static String parseReply(String reply) throws Refused {
        JsonObject answer;
        try {
            answer = JsonParser.parseString(reply).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new Refused("The launcher did not answer. Try again.");
        }
        if (answer.has("token") && !answer.get("token").getAsString().isEmpty()) return answer.get("token").getAsString();
        throw new Refused(answer.has("error") ? answer.get("error").getAsString() : "The launcher did not answer. Try again.");
    }

    protected String handOff() {
        return LaunchToken.handOff();
    }

    protected String ask(String pipe, String request) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile("\\\\.\\pipe\\" + pipe, "rw")) {
            file.write((request + "\n").getBytes(StandardCharsets.UTF_8));
            return LaunchToken.readLine(new FileInputStream(file.getFD()));
        }
    }
}
