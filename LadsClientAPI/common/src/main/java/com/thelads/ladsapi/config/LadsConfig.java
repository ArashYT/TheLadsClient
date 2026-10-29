package com.thelads.ladsapi.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * Handles config.yml according to PLAN 6.2.
 */
public final class LadsConfig {
    public static final String DEFAULT_CONFIG = """
# LadsClientAPI Configuration
enabled: true
unlisted-modules: inherit      # inherit | allow | block
require-lads-client: false     # kick non-Lads players (private servers)
require-lads-client-message: "You must connect with the Lads Client to play on this server."
min-client-version: ""         # e.g. "1.9.13"
min-client-version-message: "Your Lads Client is outdated (%client% < %min%). Please update."
server-name: "Lads Server"
notice: ""
proxy-rules: false             # proxy mode: run rules on proxy (only when on Velocity)
badge:
  presence: true               # tell Lads clients who else runs Lads (badge)
  placeholder: "&c✿ "          # %ladsapi_badge% for TAB/LuckPerms (vanilla viewers); empty = off
debug: false
""";

    private boolean enabled = true;
    private String unlistedModules = "inherit";
    private boolean requireLadsClient = false;
    private String requireLadsClientMessage = "You must connect with the Lads Client to play on this server.";
    private String minClientVersion = "";
    private String minClientVersionMessage = "Your Lads Client is outdated (%client% < %min%). Please update.";
    private String serverName = "Lads Server";
    private String notice = "";
    private boolean proxyRules = false;
    private boolean badgePresence = true;
    private String badgePlaceholder = "&c✿ ";
    private boolean debug = false;

    public static LadsConfig load(Path dataFolder, Logger logger) {
        LadsConfig config = new LadsConfig();
        try {
            if (!Files.exists(dataFolder)) {
                Files.createDirectories(dataFolder);
            }
            Path configFile = dataFolder.resolve("config.yml");
            if (!Files.exists(configFile)) {
                Files.writeString(configFile, DEFAULT_CONFIG, StandardCharsets.UTF_8);
            } else {
                config.readFrom(configFile, logger);
            }
        } catch (IOException e) {
            if (logger != null) {
                logger.warning("[LadsClientAPI] Failed to load config.yml: " + e.getMessage());
            }
        }
        return config;
    }

    public void readFrom(Path configFile, Logger logger) {
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(configFile)) {
            Map<String, Object> map = yaml.load(in);
            if (map != null) {
                resetDefaults();
                applyMap(map);
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("[LadsClientAPI] Error parsing config.yml: " + e.getMessage());
            }
        }
    }

    public void resetDefaults() {
        this.enabled = true;
        this.unlistedModules = "inherit";
        this.requireLadsClient = false;
        this.requireLadsClientMessage = "You must connect with the Lads Client to play on this server.";
        this.minClientVersion = "";
        this.minClientVersionMessage = "Your Lads Client is outdated (%client% < %min%). Please update.";
        this.serverName = "Lads Server";
        this.notice = "";
        this.proxyRules = false;
        this.badgePresence = true;
        this.badgePlaceholder = "&c✿ ";
        this.debug = false;
    }

    public void applyMap(Map<String, Object> map) {
        if (map.containsKey("enabled")) {
            this.enabled = Boolean.parseBoolean(String.valueOf(map.get("enabled")));
        }
        if (map.containsKey("unlisted-modules")) {
            this.unlistedModules = String.valueOf(map.get("unlisted-modules")).trim();
        }
        if (map.containsKey("require-lads-client")) {
            this.requireLadsClient = Boolean.parseBoolean(String.valueOf(map.get("require-lads-client")));
        }
        if (map.containsKey("require-lads-client-message")) {
            this.requireLadsClientMessage = String.valueOf(map.get("require-lads-client-message"));
        }
        if (map.containsKey("min-client-version")) {
            Object v = map.get("min-client-version");
            this.minClientVersion = v == null ? "" : String.valueOf(v).trim();
        }
        if (map.containsKey("min-client-version-message")) {
            this.minClientVersionMessage = String.valueOf(map.get("min-client-version-message"));
        }
        if (map.containsKey("server-name")) {
            this.serverName = String.valueOf(map.get("server-name"));
        }
        if (map.containsKey("notice")) {
            Object n = map.get("notice");
            this.notice = n == null ? "" : String.valueOf(n);
        }
        if (map.containsKey("proxy-rules")) {
            this.proxyRules = Boolean.parseBoolean(String.valueOf(map.get("proxy-rules")));
        }
        if (map.get("badge") instanceof Map<?, ?> badgeMap) {
            if (badgeMap.containsKey("presence")) {
                this.badgePresence = Boolean.parseBoolean(String.valueOf(badgeMap.get("presence")));
            }
            if (badgeMap.containsKey("placeholder")) {
                Object p = badgeMap.get("placeholder");
                this.badgePlaceholder = p == null ? "" : String.valueOf(p);
            }
        }
        if (map.containsKey("debug")) {
            this.debug = Boolean.parseBoolean(String.valueOf(map.get("debug")));
        }
    }

    public boolean isEnabled() { return enabled; }
    public String getUnlistedModules() { return unlistedModules; }
    public boolean isRequireLadsClient() { return requireLadsClient; }
    public String getRequireLadsClientMessage() { return requireLadsClientMessage; }
    public String getMinClientVersion() { return minClientVersion; }
    public String getMinClientVersionMessage() { return minClientVersionMessage; }
    public String getServerName() { return serverName; }
    public String getNotice() { return notice; }
    public boolean isProxyRules() { return proxyRules; }
    public boolean isBadgePresence() { return badgePresence; }
    public String getBadgePlaceholder() { return badgePlaceholder; }
    public boolean isDebug() { return debug; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setUnlistedModules(String unlistedModules) { this.unlistedModules = unlistedModules; }
    public void setRequireLadsClient(boolean requireLadsClient) { this.requireLadsClient = requireLadsClient; }
    public void setMinClientVersion(String minClientVersion) { this.minClientVersion = minClientVersion; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public void setNotice(String notice) { this.notice = notice; }
    public void setProxyRules(boolean proxyRules) { this.proxyRules = proxyRules; }
    public void setBadgePresence(boolean badgePresence) { this.badgePresence = badgePresence; }
    public void setBadgePlaceholder(String badgePlaceholder) { this.badgePlaceholder = badgePlaceholder; }
    public void setDebug(boolean debug) { this.debug = debug; }
}
