package com.thelads.core.shared;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;

/**
 * Friendly names for server addresses, from thelads/known_servers.json (the launcher's ServerNameResolver reads the same
 * file): a known server matches its domain and every subdomain; any other domain is named after its registrable label,
 * title-cased (play.funnyservername.com is "Funnyservername"). Bare IPs and single labels get no name.
 */
public final class ServerNames {
    /** The name Minecraft gives a new server entry (selectServer.defaultName in English). */
    public static final String VANILLA_DEFAULT = "Minecraft Server";
    private static final Pattern HOST = Pattern.compile("[a-z0-9_-]+(\\.[a-z0-9_-]+)*");
    private static final Map<String, String> NAMES = new HashMap<>();
    private static final Set<String> SUFFIXES = new HashSet<>();
    private static final Set<String> PREFIXES = new HashSet<>();

    static {
        try (InputStream in = ServerNames.class.getResourceAsStream("/thelads/known_servers.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement server : root.getAsJsonArray("servers")) {
                String name = server.getAsJsonObject().get("name").getAsString();
                for (JsonElement domain : server.getAsJsonObject().getAsJsonArray("domains")) NAMES.put(domain.getAsString(), name);
            }
            for (JsonElement suffix : root.getAsJsonArray("suffixes")) SUFFIXES.add(suffix.getAsString());
            for (JsonElement prefix : root.getAsJsonArray("prefixes")) PREFIXES.add(prefix.getAsString());
        } catch (Exception failure) {
            // Unknown domains are still named; only the known-server names are missing.
            LoggerFactory.getLogger("TheLadsCore").error("Could not read thelads/known_servers.json", failure);
        }
    }

    private ServerNames() {
    }

    /** The friendly name for a server address (port and case ignored), or null for a bare IP, a single label or no address. */
    public static String resolve(String address) {
        if (address == null) return null;
        String host = address.trim().toLowerCase(Locale.ROOT);
        int colon = host.indexOf(':');
        if (colon >= 0) {
            if (host.indexOf(':', colon + 1) >= 0) return null; // IPv6
            host = host.substring(0, colon);
        }
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (!HOST.matcher(host).matches()) return null;
        String[] labels = host.split("\\.");
        // Top-level domains are never numeric: "192.168" (an IP being typed) and IPv4 addresses get no name.
        if (labels.length < 2 || labels[labels.length - 1].chars().allMatch(Character::isDigit)) return null;
        for (int i = 0; i < labels.length; i++) {
            String known = NAMES.get(join(labels, i));
            if (known != null) return known;
        }
        int end = labels.length - 1;
        for (int i = 1; i < labels.length; i++) {
            if (SUFFIXES.contains(join(labels, i))) {
                end = i;
                break;
            }
        }
        int start = 0;
        while (start < end && PREFIXES.contains(labels[start])) start++;
        return start < end ? titleCase(labels[end - 1]) : null;
    }

    private static String join(String[] labels, int from) {
        return String.join(".", java.util.Arrays.copyOfRange(labels, from, labels.length));
    }

    private static String titleCase(String label) {
        StringBuilder name = new StringBuilder();
        for (String word : label.split("[-_]")) {
            if (word.isEmpty()) continue;
            if (name.length() > 0) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.length() == 0 ? null : name.toString();
    }

    /**
     * One Add/Edit Server screen's name field. While the name is empty, the default or the name this filled in, each address
     * change fills in the address's friendly name (or puts the earlier name back when the address has none). A name the user
     * typed is never replaced.
     */
    public static final class AutoName {
        private final String defaultName;
        private String address, filled, before;

        /** @param defaultName the screen's translated default server name */
        public AutoName(String defaultName) {
            this.defaultName = defaultName;
        }

        /** The text the name field should show now, or null to leave it as it is. Call it whenever either field changes. */
        public String update(String name, String address) {
            if (address.equals(this.address)) return null;
            this.address = address;
            boolean ours = name.equals(filled);
            if (!ours && !name.trim().isEmpty() && !name.equals(defaultName) && !name.equals(VANILLA_DEFAULT)) {
                filled = null;
                return null;
            }
            if (!ours) before = name;
            filled = resolve(address);
            String next = filled != null ? filled : before;
            return next.equals(name) ? null : next;
        }
    }
}
