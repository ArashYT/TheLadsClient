package com.thelads.core.client;

import java.util.List;
import java.util.Locale;

/** Multiplayer screen shortcuts: whether clipboard text is a server address, and finding a saved server with the same one. */
public final class ServerAddressInput {
    private ServerAddressInput() {}

    /** The trimmed clipboard text when it is a plausible host[:port] (or [ipv6][:port]), else null. */
    public static String clean(String clipboard) {
        if (clipboard == null) return null;
        String text = clipboard.trim();
        if (text.isEmpty() || text.length() > 255) return null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c <= ' ' || c == 0x7F || Character.isWhitespace(c)) return null;
        }
        String host = text, port = null;
        if (text.charAt(0) == '[') {
            int end = text.indexOf(']');
            if (end < 3) return null;
            host = text.substring(1, end);
            if (end + 1 < text.length()) {
                if (text.charAt(end + 1) != ':') return null;
                port = text.substring(end + 2);
            }
            return validIpv6(host) && (port == null || validPort(port)) ? text : null;
        }
        int colon = text.indexOf(':');
        if (colon >= 0) {
            if (text.indexOf(':', colon + 1) >= 0) return null;
            host = text.substring(0, colon);
            port = text.substring(colon + 1);
        }
        return validHost(host) && (port == null || validPort(port)) ? text : null;
    }

    static boolean validPort(String port) {
        if (port.isEmpty() || port.length() > 5) return false;
        for (int i = 0; i < port.length(); i++) if (port.charAt(i) < '0' || port.charAt(i) > '9') return false;
        int value = Integer.parseInt(port);
        return value >= 1 && value <= 65535;
    }

    static boolean validIpv6(String host) {
        boolean colon = false;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == ':') colon = true;
            else if (Character.digit(c, 16) < 0 && c != '.') return false;
        }
        return colon;
    }

    static boolean validHost(String host) {
        if (host.isEmpty() || host.length() > 253) return false;
        String[] labels = host.split("[.]", -1);
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63 || label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') return false;
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '-' || c == '_')) return false;
            }
        }
        return true;
    }

    /** Case-insensitive comparison key: a trailing default port ":25565" is the same server as none. */
    static String key(String address) {
        String k = address.trim().toLowerCase(Locale.ROOT);
        return k.endsWith(":25565") && k.indexOf(':') == k.length() - 6 ? k.substring(0, k.length() - 6) : k;
    }

    /** Index of the first saved address equal to {@code address} (case-insensitive), or -1. Null entries are skipped. */
    public static int indexOf(List<String> saved, String address) {
        String wanted = key(address);
        for (int i = 0; i < saved.size(); i++) {
            String s = saved.get(i);
            if (s != null && key(s).equals(wanted)) return i;
        }
        return -1;
    }
}
