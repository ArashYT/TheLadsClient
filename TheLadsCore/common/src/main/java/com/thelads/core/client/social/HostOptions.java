package com.thelads.core.client.social;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    public boolean isBanned(java.util.UUID id) {
        return id != null && banned.contains(id.toString());
    }

    /** The world's saved options, or the defaults when there are none or the file is unreadable. */
    public static HostOptions load(Path worldDir) {
        Path file = worldDir.resolve(FILE);
        if (!Files.isRegularFile(file)) return new HostOptions();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            HostOptions o = GSON.fromJson(r, HostOptions.class);
            return o == null ? new HostOptions() : o.sanitized();
        } catch (IOException | RuntimeException e) {
            return new HostOptions();
        }
    }

    public void save(Path worldDir) throws IOException {
        try (Writer w = Files.newBufferedWriter(worldDir.resolve(FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(this, w);
        }
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
