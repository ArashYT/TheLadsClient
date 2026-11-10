package com.thelads.core.client.social.modsync;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** The hash check every synced jar passes before it may be loaded: the size and SHA-512 the host's list names. */
public final class ModSyncHash {
    private ModSyncHash() {}

    public static MessageDigest sha512() {
        try {
            return MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha512(Path file) throws IOException {
        MessageDigest sha = sha512();
        byte[] buf = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buf)) != -1) sha.update(buf, 0, n);
        }
        return hex(sha.digest());
    }

    /** file is exactly the jar entry names: same size, same SHA-512. A missing or unreadable file is not. */
    public static boolean matches(Path file, ModSyncEntry entry) {
        try {
            return Files.isRegularFile(file) && Files.size(file) == entry.size() && sha512(file).equals(entry.sha512());
        } catch (IOException e) {
            return false;
        }
    }

    public static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return sb.toString();
    }
}
