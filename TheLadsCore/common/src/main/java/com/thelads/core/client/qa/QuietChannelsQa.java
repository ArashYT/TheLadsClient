package com.thelads.core.client.qa;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only: what the client tells the server it is on (Fabric's c:register / minecraft:register: every receiver the client has,
 * global or per connection) must not name a Lads channel before that server speaks it. hypixel:* only after the first Mod API
 * ping, appleskin:* never, jade:* only when the server itself registered jade:client_handshake. {@link #check} logs one line per
 * sample ("Lads quiet channels &lt;when&gt;: PASS|FAIL ...") and a running "Lads quiet channels END: N checks, M failed".
 */
public final class QuietChannelsQa {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static int checks, failed;

    private QuietChannelsQa() {}

    /** The channels (as "namespace:path") that a client announcing {@code announced} should not be announcing. */
    public static Set<String> forbidden(Collection<String> announced, boolean serverJade, boolean pinged) {
        Set<String> bad = new TreeSet<>();
        for (String channel : announced)
            if (channel.startsWith("appleskin:") || channel.startsWith("jade:") && !serverJade || channel.startsWith("hypixel:") && !pinged) bad.add(channel);
        return bad;
    }

    /**
     * @param announced   the client's receivers on this connection (and the global ones: they are announced at every join)
     * @param heard       what the server on this connection reports having been told (its sendable channels)
     * @param serverJade  the server registered jade:client_handshake
     * @param pinged      the first Mod API ping went out on this connection (hypixel:ping may be announced now)
     * @param mustAnnounce channels that have to be in both lists (positive controls), may be empty
     */
    public static boolean check(String when, Collection<String> announced, Collection<String> heard, boolean serverJade, boolean pinged,
        Collection<String> mustAnnounce) {
        Set<String> problems = new LinkedHashSet<>();
        Set<String> badAnnounced = forbidden(announced, serverJade, pinged), badHeard = forbidden(heard, serverJade, pinged);
        if (!badAnnounced.isEmpty()) problems.add("client announces " + badAnnounced);
        if (!badHeard.isEmpty()) problems.add("server heard " + badHeard);
        for (String channel : mustAnnounce) {
            if (!announced.contains(channel)) problems.add(channel + " not announced by the client");
            if (!heard.contains(channel)) problems.add(channel + " not heard by the server");
        }
        checks++;
        if (!problems.isEmpty()) failed++;
        LOGGER.info("Lads quiet channels {}: {} [server registered jade:client_handshake={}, ping sent={}] {} client announces {}, server heard {}",
            when, problems.isEmpty() ? "PASS" : "FAIL " + problems, serverJade, pinged, "|", new TreeSet<>(announced), new TreeSet<>(heard));
        LOGGER.info("Lads quiet channels END: {} checks, {} failed", checks, failed);
        return problems.isEmpty();
    }
}
