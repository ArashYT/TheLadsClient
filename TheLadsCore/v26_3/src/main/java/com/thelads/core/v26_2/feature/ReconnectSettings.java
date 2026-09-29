package com.thelads.core.v26_2.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.modules.AutoReconnectModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/** Native editable lists. The upstream file is imported read-only and remains intact. */
public final class ReconnectSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    public List<Integer> delays = new ArrayList<>(List.of(3, 10, 30, 60));
    public List<String> conditionKeys = new ArrayList<>(List.of("disconnect.loginFailedInfo", "disconnect.spam", "disconnect.timeout",
        "disconnect.unknownHost", "multiplayer.disconnect.banned", "multiplayer.disconnect.code_of_conduct", "multiplayer.disconnect.incompatible",
        "multiplayer.disconnect.ip_banned", "multiplayer.disconnect.kicked", "multiplayer.disconnect.name_taken", "multiplayer.disconnect.not_whitelisted",
        "multiplayer.disconnect.outdated_client", "multiplayer.disconnect.outdated_server"));
    public List<String> conditionPatterns = new ArrayList<>();
    public List<Action> autoMessages = new ArrayList<>();

    public static final class Action {
        public String id = "";
        public double delay = 1;
        public List<String> messages = new ArrayList<>();
        public boolean enabled;
        public Action copy() { Action value = new Action(); value.id = id; value.delay = delay; value.messages = new ArrayList<>(messages); value.enabled = enabled; return value; }
    }
    public ReconnectSettings copy() { return GSON.fromJson(GSON.toJson(this), ReconnectSettings.class); }
    static Path verificationFile;
    private static Path file() { return verificationFile != null ? verificationFile : FabricLoader.getInstance().getConfigDir().resolve("theladscore/reconnect.json"); }

    static ReconnectSettings load(AutoReconnectModule module) {
        return load(module, file(), FabricLoader.getInstance().getConfigDir().resolve("autoreconnectrf.json"), true);
    }
    static ReconnectSettings load(AutoReconnectModule module, Path nativeFile, Path upstream, boolean saveModule) {
        try {
            Path source = Files.isRegularFile(nativeFile) ? nativeFile : upstream;
            if (!Files.isRegularFile(source) || Files.size(source) > MAX_FILE_BYTES) return new ReconnectSettings();
            JsonObject root = JsonParser.parseString(Files.readString(source)).getAsJsonObject();
            boolean imported = source.equals(upstream);
            JsonObject options = imported && root.has("options") ? root.getAsJsonObject("options") : root;
            ReconnectSettings result = GSON.fromJson(options, ReconnectSettings.class);
            result.validate();
            if (imported) {
                if (options.has("initial")) module.initial.set(options.get("initial").getAsBoolean());
                if (options.has("infinite")) module.infinite.set(options.get("infinite").getAsBoolean());
                if (options.has("conditionType")) module.reasonMode.setIndex(options.get("conditionType").getAsBoolean() ? 1 : 0);
                if (options.has("regexIds")) module.regexIds.set(options.get("regexIds").getAsBoolean());
                if (options.has("commandSigning")) module.signedCommands.set(options.get("commandSigning").getAsBoolean());
                module.actionsEnabled.set(false);
                result.autoMessages.forEach(action -> action.enabled = false);
                result.save(nativeFile);
                if (saveModule) com.thelads.core.config.ConfigManager.save();
            }
            return result;
        } catch (IOException | RuntimeException invalid) {
            LoggerFactory.getLogger("TheLadsCore").warn("Reconnect preferences could not be read; the original file was preserved: {}", invalid.toString());
            return new ReconnectSettings();
        }
    }

    public void validate() {
        delays = delays == null ? new ArrayList<>() : new ArrayList<>(delays.stream().filter(value -> value != null && value > 0 && value <= 86400).limit(100).toList());
        conditionKeys = clean(conditionKeys, 128);
        conditionPatterns = clean(conditionPatterns, 128);
        autoMessages = autoMessages == null ? new ArrayList<>() : new ArrayList<>(autoMessages.stream().filter(java.util.Objects::nonNull).limit(64).toList());
        for (Action action : autoMessages) {
            action.id = action.id == null ? "" : action.id;
            action.delay = Double.isFinite(action.delay) && action.delay >= .1 && action.delay <= 3600 ? action.delay : 1;
            action.messages = clean(action.messages, 100);
            if (action.id.length() > 512 || action.messages.stream().anyMatch(text -> text.length() > (text.startsWith("/") ? 32767 : 256))) action.enabled = false;
        }
    }
    private static List<String> clean(List<String> input, int count) {
        return input == null ? new ArrayList<>() : new ArrayList<>(input.stream().filter(java.util.Objects::nonNull).limit(count).toList());
    }
    public void save() { save(file()); }
    void save(Path destination) {
        validate();
        String encoded = GSON.toJson(this);
        if (encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_FILE_BYTES)
            throw new IllegalArgumentException("Reconnect settings exceed 4 MiB. Remove some action messages before saving.");
        Path temporary = null;
        try {
            Files.createDirectories(destination.getParent());
            temporary = Files.createTempFile(destination.getParent(), "reconnect-", ".tmp");
            Files.writeString(temporary, encoded);
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unavailable) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException unavailable) { throw new IllegalStateException("Reconnect settings could not be saved", unavailable); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) {} }
    }
}
