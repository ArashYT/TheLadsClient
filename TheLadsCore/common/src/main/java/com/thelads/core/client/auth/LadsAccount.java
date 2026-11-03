package com.thelads.core.client.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class LadsAccount {
    public final String username;
    public final String uuid;
    public final String type;
    public final String accessToken;
    private boolean selected;

    public LadsAccount(String username, String uuid, String type, String accessToken) {
        this.username = username != null ? username : "Player";
        this.uuid = uuid != null ? uuid.trim() : "";
        this.type = type != null ? type.trim() : "offline";
        this.accessToken = accessToken != null ? accessToken : "0";
        this.selected = false;
    }

    public String getUsername() { return username; }
    public String getUuid() { return uuid; }
    public String getType() { return type; }
    public String getAccessToken() { return accessToken; }
    public boolean isOffline() { return !"microsoft".equalsIgnoreCase(type); }
    /** The id a session of this account plays under: an offline name's own, never a file's word for it. */
    public UUID sessionUuid() {
        if (isOffline()) return offlineUuid(username);
        String clean = uuid.replace("-", "").trim();
        if (!clean.matches("[0-9A-Fa-f]{32}")) {
            throw new IllegalArgumentException("Invalid Microsoft account UUID for " + username);
        }
        return UUID.fromString(clean.replaceFirst("^(.{8})(.{4})(.{4})(.{4})(.{12})$", "$1-$2-$3-$4-$5"));
    }
    public static UUID offlineUuid(String name) { return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)); }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
}
