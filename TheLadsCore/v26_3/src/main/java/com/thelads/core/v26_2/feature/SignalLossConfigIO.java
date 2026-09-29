package com.thelads.core.v26_2.feature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.modules.SignalLossModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Imports the original file read-only; native command reload touches only the SignalLoss section. */
final class SignalLossConfigIO {
    private SignalLossConfigIO() {}
    static JsonObject read(Path file) throws IOException {
        if (Files.size(file) > 4 * 1024 * 1024) throw new IOException("SignalLoss configuration is too large");
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }
    static void importLegacy(Path file, SignalLossModule destination) throws IOException {
        JsonObject json = read(file); var value = new SignalLossModule();
        if (json.has("enabled")) value.setEnabled(json.get("enabled").getAsBoolean());
        if (json.has("timeoutThreshold")) value.timeout.setValue(integer(json, "timeoutThreshold"));
        if (json.has("minWarningTime")) value.minimum.setValue(integer(json, "minWarningTime"));
        if (json.has("lingerTime")) value.linger.setValue(integer(json, "lingerTime"));
        if (json.has("drawBackground")) value.background.set(json.get("drawBackground").getAsBoolean());
        if (json.has("showInSingleplayer")) value.singleplayer.set(json.get("showInSingleplayer").getAsBoolean());
        if (json.has("toastPosition")) value.position.setIndex(switch (json.get("toastPosition").getAsString().toUpperCase(Locale.ROOT)) {
            case "LEFT" -> 0; case "CENTER" -> 1; case "RIGHT" -> 2;
            default -> throw new IllegalArgumentException("Unknown SignalLoss position");
        });
        if (json.has("textColor")) value.textColor.setColor(json.get("textColor").getAsInt());
        if (json.has("backgroundColor")) value.backgroundColor.setColor(json.get("backgroundColor").getAsInt());
        copy(value, destination);
    }
    private static String integer(JsonObject json, String key) {
        int result = Integer.parseInt(json.get(key).getAsString());
        if (result < 0) throw new IllegalArgumentException("Negative SignalLoss timing");
        return Integer.toString(result);
    }
    static void reloadNative(Path file, SignalLossModule destination) throws IOException {
        JsonObject section = read(file).getAsJsonObject("modules").getAsJsonObject("SignalLoss");
        if (section == null) throw new IOException("No saved SignalLoss preferences");
        var value = new SignalLossModule();
        if (section.has("enabled")) value.setEnabled(section.get("enabled").getAsBoolean());
        if (section.has("options")) {
            JsonObject options = section.getAsJsonObject("options");
            value.getOptions().forEach(option -> { if (options.has(option.getName())) option.load(options.get(option.getName())); });
        }
        copy(value, destination);
    }
    static void copy(SignalLossModule source, SignalLossModule destination) {
        source.getOptions().forEach(option -> destination.getOption(option.getName()).load(option.save()));
        destination.setEnabled(source.isEnabled());
    }
    static boolean defaults(SignalLossModule module) {
        var defaults = new SignalLossModule();
        return module.isEnabled() && module.getOptions().stream().allMatch(option -> option.save().equals(defaults.getOption(option.getName()).save()));
    }
}
