package com.thelads.core.client.auth;

public class LadsAccount {
    public final String username;
    public final String uuid;
    public final String type;
    public final String accessToken;
    private boolean selected;

    public LadsAccount(String username, String uuid, String type, String accessToken) {
        this.username = username != null ? username : "Player";
        this.uuid = uuid != null ? uuid : "";
        this.type = type != null ? type : "offline";
        this.accessToken = accessToken != null ? accessToken : "0";
        this.selected = false;
    }

    public String getUsername() { return username; }
    public String getUuid() { return uuid; }
    public String getType() { return type; }
    public String getAccessToken() { return accessToken; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
}
