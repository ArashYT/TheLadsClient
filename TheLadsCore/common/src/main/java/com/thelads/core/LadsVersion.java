package com.thelads.core;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** The Lads Core version (gradle.properties mod_version), shown in window titles and the title screen. */
public final class LadsVersion {
    public static final String VERSION = read();

    private LadsVersion() {}

    private static String read() {
        try (InputStream in = LadsVersion.class.getResourceAsStream("/assets/theladscore/version.txt")) {
            if (in == null) return "dev";
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); // no readAllBytes: the 1.8.9 Core runs on Java 8
            byte[] buffer = new byte[64];
            for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
            return text.isEmpty() || text.startsWith("$") ? "dev" : text;
        } catch (Exception e) {
            return "dev";
        }
    }

    /** "The Lads Client 1.4.4". */
    public static String clientName() {
        return "The Lads Client " + VERSION;
    }
}
