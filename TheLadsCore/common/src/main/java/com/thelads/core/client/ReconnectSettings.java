package com.thelads.core.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.slf4j.LoggerFactory;

/**
 * AutoReconnect's editable lists, kept in config/theladscore/reconnect.json on every version. Files written by 1.6.0 and
 * earlier used other key names; Gson reads those as alternates and the next save writes these names.
 */
public final class ReconnectSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_BYTES = 4 * 1024 * 1024;

    /** Seconds before each automatic attempt, in order. */
    @SerializedName(value = "retryDelays", alternate = "delays")
    public List<Integer> retryDelays = new ArrayList<>(Arrays.asList(3, 10, 30, 60));
    /** Disconnect translation keys (substring match); with the Except Matches filter these never auto-retry. */
    @SerializedName(value = "reasonKeys", alternate = "conditionKeys")
    public List<String> reasonKeys = new ArrayList<>(Arrays.asList(
        // Kicks, bans and refusals a retry can't fix. Timeouts, lost connections, full or restarting servers do retry.
        "multiplayer.disconnect.kicked", "multiplayer.disconnect.banned", "multiplayer.disconnect.ip_banned",
        "multiplayer.disconnect.not_whitelisted", "multiplayer.disconnect.duplicate_login", "multiplayer.disconnect.name_taken",
        "multiplayer.disconnect.idling", "multiplayer.disconnect.incompatible", "multiplayer.disconnect.outdated_client",
        "multiplayer.disconnect.outdated_server", "multiplayer.disconnect.transfers_disabled", "disconnect.spam",
        "disconnect.exceeded_packet_rate"));
    /** Regular expressions searched in the reason text: servers (and 1.8.9) usually send kicks and bans as plain text. */
    @SerializedName(value = "reasonPatterns", alternate = "conditionPatterns")
    public List<String> reasonPatterns = new ArrayList<>(Arrays.asList("(?i)\\bbanned\\b", "(?i)\\bkicked\\b", "(?i)white-?list"));
    /** Messages or commands sent after an automatic reconnect to a matching server or world. */
    @SerializedName(value = "joinActions", alternate = "autoMessages")
    public List<JoinAction> joinActions = new ArrayList<>();

    public static final class JoinAction {
        /** Server address or world folder this profile applies to (exact, or a regex with Match Action IDs as Regex). */
        @SerializedName(value = "target", alternate = "id")
        public String target = "";
        /** Seconds before the first line and between lines. */
        @SerializedName(value = "interval", alternate = "delay")
        public double interval = 1;
        @SerializedName(value = "lines", alternate = "messages")
        public List<String> lines = new ArrayList<>();
        public boolean enabled;
    }

    public ReconnectSettings copy() { return GSON.fromJson(GSON.toJson(this), ReconnectSettings.class); }

    /** The saved lists, or the defaults when the file is missing or unreadable (an unreadable file is left as it is). */
    public static ReconnectSettings load(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) return new ReconnectSettings();
            ReconnectSettings loaded = GSON.fromJson(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), ReconnectSettings.class);
            if (loaded == null) return new ReconnectSettings();
            loaded.validate();
            return loaded;
        } catch (IOException | RuntimeException unreadable) {
            LoggerFactory.getLogger("TheLadsCore").warn("Reconnect lists in {} could not be read; using defaults and leaving the file alone: {}", file, unreadable.toString());
            return new ReconnectSettings();
        }
    }

    /** Drops nulls and out-of-range values; an action with an over-long line is switched off rather than cut short. */
    public void validate() {
        List<Integer> delays = new ArrayList<>();
        if (retryDelays != null) for (Integer seconds : retryDelays) if (seconds != null && seconds > 0 && seconds <= 86400 && delays.size() < 100) delays.add(seconds);
        retryDelays = delays;
        reasonKeys = strings(reasonKeys, 128);
        reasonPatterns = strings(reasonPatterns, 128);
        List<JoinAction> actions = new ArrayList<>();
        if (joinActions != null) for (JoinAction action : joinActions) if (action != null && actions.size() < 64) actions.add(action);
        joinActions = actions;
        for (JoinAction action : joinActions) {
            if (action.target == null) action.target = "";
            if (!(action.interval >= .1 && action.interval <= 3600)) action.interval = 1;
            action.lines = strings(action.lines, 100);
            boolean tooLong = action.target.length() > 512;
            for (String line : action.lines) tooLong |= line.length() > (line.startsWith("/") ? 32767 : 256);
            if (tooLong) action.enabled = false;
        }
    }

    private static List<String> strings(List<String> input, int limit) {
        List<String> result = new ArrayList<>();
        if (input != null) input.stream().filter(Objects::nonNull).limit(limit).forEach(result::add);
        return result;
    }

    /** Written to a temporary file first, so a failed save never leaves half a file. */
    public void save(Path file) {
        validate();
        byte[] json = GSON.toJson(this).getBytes(StandardCharsets.UTF_8);
        if (json.length > MAX_BYTES) throw new IllegalArgumentException("Reconnect settings exceed 4 MiB. Remove some action messages before saving.");
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "reconnect-", ".tmp");
            Files.write(temporary, json);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failed) {
            throw new IllegalStateException("Reconnect settings could not be saved", failed);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}
