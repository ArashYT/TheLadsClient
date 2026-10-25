package com.thelads.core.client.social;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thelads.core.client.util.ClientPaths;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Host settings for one world, kept next to it in lads-host.json (contract; defaults are the spec's). */
public final class HostOptions {
    public enum Scope { OFF, LAN, FRIENDS, INVITE }
    public enum Approval { AUTO_FRIENDS, ASK, INVITE_ONLY }

    static final String FILE = "lads-host.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile String installId;

    public Scope scope = Scope.FRIENDS;
    public Approval approval = Approval.ASK;
    public int maxPlayers = 8;
    public String guestGameMode = "survival";
    public int guestOpLevel = 0;
    public boolean pvp = true;
    /** null = keep the world's difficulty. */
    public String difficulty = null;
    public Map<String, String> gamerules = new LinkedHashMap<>();
    /** 0 = the host's view distance. */
    public int viewDistanceCap = 0;
    public boolean relayOnly = false;
    public boolean backupOnOpen = true;
    public boolean showJoinable = true;
    public String welcome = "";
    /** UUIDs banned from this world: their join requests are denied, they get no invite and the login filter refuses them. */
    public Set<String> banned = new LinkedHashSet<>();

    /** This install's id when it saved the file: values that grant power (op level, auto-accept, gamerules) count only from our own file. */
    public String writtenBy;

    public boolean isBanned(java.util.UUID id) {
        return id != null && banned.contains(id.toString());
    }

    /** The world's saved options, or the defaults when there are none or the file is unreadable. */
    public static HostOptions load(Path worldDir) {
        Path file = worldDir.resolve(FILE);
        if (!Files.isRegularFile(file)) return new HostOptions();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            HostOptions o = GSON.fromJson(r, HostOptions.class);
            if (o == null) return new HostOptions();
            o.sanitized();
            if (!installId().equals(o.writtenBy)) o.withoutGrantedPower();
            return o;
        } catch (IOException | RuntimeException e) {
            return new HostOptions();
        }
    }

    public void save(Path worldDir) throws IOException {
        writtenBy = installId();
        try (Writer w = Files.newBufferedWriter(worldDir.resolve(FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(this, w);
        }
    }

    /**
     * A file this install did not write (a world someone shipped): no guest op level, no auto-accept, no gamerules, until the host saves
     * the Host screen once (F17). Everything else (and bans) still applies.
     */
    private void withoutGrantedPower() {
        guestOpLevel = 0;
        if (approval == Approval.AUTO_FRIENDS) approval = Approval.ASK;
        gamerules = new LinkedHashMap<>();
    }

    /** A random id for this Lads install, kept in the Lads folder (install-id); per run only if that file can't be written. */
    static String installId() {
        String id = installId;
        if (id != null) return id;
        synchronized (HostOptions.class) {
            if (installId != null) return installId;
            Path file = ClientPaths.getBaseDir().resolve("install-id");
            try {
                if (Files.isRegularFile(file)) {
                    String saved = new String(Files.readAllBytes(file), StandardCharsets.US_ASCII).trim();
                    if (saved.matches("[0-9a-f]{32}")) return installId = saved;
                }
                id = randomId();
                Files.createDirectories(file.getParent());
                Files.write(file, id.getBytes(StandardCharsets.US_ASCII));
            } catch (IOException e) {
                id = randomId();
            }
            return installId = id;
        }
    }

    private static String randomId() {
        byte[] raw = new byte[16];
        new java.security.SecureRandom().nextBytes(raw);
        StringBuilder hex = new StringBuilder(32);
        for (byte b : raw) hex.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return hex.toString();
    }

    /** Clamps values a hand-edited file could break. */
    HostOptions sanitized() {
        if (scope == null) scope = Scope.FRIENDS;
        if (approval == null) approval = Approval.ASK;
        maxPlayers = Math.max(1, Math.min(16, maxPlayers));
        guestOpLevel = Math.max(0, Math.min(4, guestOpLevel));
        viewDistanceCap = Math.max(0, Math.min(32, viewDistanceCap));
        if (guestGameMode == null) guestGameMode = "survival";
        if (gamerules == null) gamerules = new LinkedHashMap<>();
        if (welcome == null) welcome = "";
        if (banned == null) banned = new LinkedHashSet<>();
        if (welcome.length() > 256) welcome = welcome.substring(0, 256);
        return this;
    }
}
