package com.thelads.core.client.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The launcher keeps the account's access token off the game's command line (where Task Manager and crash dumps show it):
 * it passes {@link #PLACEHOLDER} as --accessToken and writes the real token as the first line of the game's stdin.
 * The session mixins swap it in through {@link #resolve}. The launcher only does this when this class is in the Core jar.
 */
public final class LaunchToken {
    public static final String PLACEHOLDER = "lads-stdin-token";
    private static final Logger LOGGER = LoggerFactory.getLogger(LaunchToken.class);
    private static String token;

    private LaunchToken() {}

    /** The token to use for a session created with {@code given}: the launcher's stdin token when given is the placeholder. */
    public static synchronized String resolve(String given) {
        if (!PLACEHOLDER.equals(given)) return given;
        if (token == null) {
            token = readLine(System.in);
            if (token.isEmpty()) {
                LOGGER.warn("[Lads] The launcher did not send the session token; online features need a relaunch from The Lads Launcher.");
                token = "0";
            } else LOGGER.info("[Lads] Session token received from the launcher.");
        }
        return token;
    }

    /** The first line of {@code in} (without the line break), or "" on end of stream or error. */
    static String readLine(InputStream in) {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        try {
            for (int b; (b = in.read()) != -1 && b != '\n'; ) if (b != '\r') line.write(b);
        } catch (IOException e) {
            return "";
        }
        return new String(line.toByteArray(), StandardCharsets.UTF_8).trim();
    }
}
