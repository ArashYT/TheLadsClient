package com.thelads.core.client.social.modsync;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One mod jar of a host's profile, as the host publishes it. sha512 names the jar's exact bytes: every fetched file is checked
 * against it. source/url say where the host's launcher found the same file ("modrinth" or "curseforge" with its CDN address);
 * both are empty for a jar that exists only on the host's PC, which comes over the P2P link. file is the host's name for the jar
 * and is only shown: on disk the guest uses {@link #localName()}.
 */
public record ModSyncEntry(String id, String name, String version, String file, long size, String sha1, String sha512,
                           String source, String url) {
    public static final long MAX_JAR = 256L * 1024 * 1024;
    public static final String MODRINTH = "modrinth", CURSEFORGE = "curseforge";
    /** Never synced: the game, the loader and the client itself come from the guest's own launcher. */
    public static final Set<String> RESERVED = Set.of("minecraft", "java", "fabricloader", "fabric-loader", "theladscore", "forge", "fml");
    private static final Pattern SHA512 = Pattern.compile("[0-9a-f]{128}"), SHA1 = Pattern.compile("[0-9a-f]{40}");

    public ModSyncEntry {
        id = clip(id, 64);
        name = clip(name, 128);
        version = clip(version, 64);
        file = file == null ? "" : file;
        sha1 = sha1 == null ? "" : sha1.toLowerCase(Locale.ROOT);
        sha512 = sha512 == null ? "" : sha512.toLowerCase(Locale.ROOT);
        source = source == null ? "" : source;
        url = url == null ? "" : url;
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** What is wrong with this entry (a host's list is untrusted input), or null when it can be used. */
    public String problem() {
        if (!SHA512.matcher(sha512).matches()) return "no sha512";
        if (!sha1.isEmpty() && !SHA1.matcher(sha1).matches()) return "bad sha1";
        if (size <= 0 || size > MAX_JAR) return "bad size";
        if (file.isEmpty() || file.length() > 160 || !file.toLowerCase(Locale.ROOT).endsWith(".jar")) return "not a jar name";
        for (int i = 0; i < file.length(); i++) if (file.charAt(i) < 0x20) return "not a jar name";
        if (RESERVED.contains(id.toLowerCase(Locale.ROOT))) return "reserved mod id";
        if (!source.isEmpty() && !source.equals(MODRINTH) && !source.equals(CURSEFORGE)) return "unknown source";
        if (url.length() > 512) return "bad url";
        return null;
    }

    /**
     * The jar's name in the guest's sync folder: the start of its hash, then the host's name with everything but letters, digits
     * and "._+-" replaced. The host's own text never reaches a path.
     */
    public String localName() {
        String stem = file.toLowerCase(Locale.ROOT).endsWith(".jar") ? file.substring(0, file.length() - 4) : file;
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < stem.length() && safe.length() < 80; i++) {
            char c = stem.charAt(i);
            boolean plain = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '.' || c == '_' || c == '+' || c == '-';
            safe.append(plain ? c : '_');
        }
        return sha512.substring(0, Math.min(12, sha512.length())) + "-" + safe + ".jar";
    }
}
