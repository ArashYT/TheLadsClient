package com.thelads.core.client.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.util.LaunchToken;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
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

    private static final Pattern HAND_OFF = Pattern.compile("([A-Za-z0-9_-]{1,128}) ([A-Za-z0-9_-]{16,128})");
    private static final int MAX_REPLY_BYTES = 65536;
    private static final int PIPE_OPEN_ATTEMPTS = 5;
    private static final long PIPE_RETRY_DELAY_MS = 20L;

    public String fetch(LadsAccount target, boolean sessionIsOffline) throws Refused {
        if (target.isOffline() && sessionIsOffline) return "0";
        Matcher handOff = HAND_OFF.matcher(safeHandOff());
        if (!handOff.matches()) throw new Refused("Start the game from The Lads Launcher to switch to " + target.getUsername() + ".");
        try {
            return parseReply(ask(handOff.group(1), request(handOff.group(2), "token", target)));
        } catch (IOException e) {
            throw new Refused("The launcher that started this game is closed.");
        }
    }

    /** Off the render thread: the launcher removes a saved account (and its tokens) and republishes its list before it answers. */
    public void remove(LadsAccount target) throws Refused {
        Matcher handOff = HAND_OFF.matcher(safeHandOff());
        if (!handOff.matches()) throw new Refused("Start the game from The Lads Launcher to remove " + target.getUsername() + ".");
        String reply;
        try {
            reply = ask(handOff.group(1), request(handOff.group(2), "remove", target));
        } catch (IOException e) {
            throw new Refused("The launcher that started this game is closed.");
        }
        String error;
        try {
            error = JsonParser.parseString(reply).getAsJsonObject().get("error").getAsString();
        } catch (RuntimeException e) {
            throw new Refused("The launcher did not answer. Try again.");
        }
        if (!error.isEmpty()) throw new Refused(error);
    }

    private static String request(String key, String op, LadsAccount target) {
        JsonObject request = new JsonObject();
        request.addProperty("key", key);
        request.addProperty("op", op);
        request.addProperty("username", target.getUsername());
        request.addProperty("uuid", target.isOffline() ? target.sessionUuid().toString() : target.getUuid());
        request.addProperty("type", target.isOffline() ? "offline" : "microsoft");
        return request.toString();
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
        Matcher handOff = HAND_OFF.matcher(safeHandOff());
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

    private String safeHandOff() {
        String raw = handOff();
        return raw == null ? "" : raw.trim();
    }

    /** The launcher's answer: its token, or {@link Refused} with its reason. */
    private static String parseReply(String reply) throws Refused {
        try {
            JsonObject answer = JsonParser.parseString(reply).getAsJsonObject();
            if (answer.has("token") && !answer.get("token").isJsonNull()) {
                String token = answer.get("token").getAsString();
                if (!token.isEmpty()) return token;
            }
            if (answer.has("error") && !answer.get("error").isJsonNull()) {
                String error = answer.get("error").getAsString();
                if (!error.isEmpty()) throw new Refused(error);
            }
        } catch (Refused e) {
            throw e;
        } catch (RuntimeException e) {
            throw new Refused("The launcher did not answer. Try again.");
        }
        throw new Refused("The launcher did not answer. Try again.");
    }

    protected String handOff() {
        return LaunchToken.handOff();
    }

    protected String ask(String pipe, String request) throws IOException {
        byte[] payload = (request + "\n").getBytes(StandardCharsets.UTF_8);
        try (RandomAccessFile file = openPipe(pipe)) {
            file.write(payload);
            return readReplyLine(file);
        }
    }

    private static RandomAccessFile openPipe(String pipe) throws IOException {
        String path = "\\\\.\\pipe\\" + pipe;
        FileNotFoundException last = null;
        for (int attempt = 0; attempt < PIPE_OPEN_ATTEMPTS; attempt++) {
            try {
                return new RandomAccessFile(path, "rw");
            } catch (FileNotFoundException e) {
                last = e;
                if (attempt + 1 < PIPE_OPEN_ATTEMPTS) {
                    try {
                        Thread.sleep(PIPE_RETRY_DELAY_MS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw last;
    }

    private static String readReplyLine(RandomAccessFile file) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(256);
        byte[] buffer = new byte[1024];
        while (line.size() < MAX_REPLY_BYTES) {
            int read;
            try {
                read = file.read(buffer);
            } catch (IOException e) {
                // On Windows named pipes, a server closing after writing may surface as broken-pipe once buffered bytes are drained.
                break;
            }
            if (read <= 0) break;
            for (int i = 0; i < read; i++) {
                byte b = buffer[i];
                if (b == '\n') {
                    return new String(line.toByteArray(), StandardCharsets.UTF_8).trim();
                }
                if (b != '\r' && line.size() < MAX_REPLY_BYTES) {
                    line.write(b);
                }
            }
        }
        return new String(line.toByteArray(), StandardCharsets.UTF_8).trim();
    }
}
