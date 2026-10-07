package com.thelads.core.client.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EssentialConfigEnforcer {
    private EssentialConfigEnforcer() {}

    public static void enforce(Path gameDir) {
        if (gameDir == null) return;
        Path essentialDir = gameDir.resolve("essential");
        enforceToml(essentialDir.resolve("config.toml"));
        enforceOnboarding(essentialDir.resolve("onboarding.json"));
    }

    private static void enforceToml(Path tomlFile) {
        try {
            if (!Files.exists(tomlFile)) return;
            String text = Files.readString(tomlFile, StandardCharsets.UTF_8);
            String updated = text;

            String[][] settings = {
                {"privacy.general", "display_current_server", "true"},
                {"general.general", "streamer_mode", "false"},
                {"general.general", "telemetry", "false"},
                {"general.online_status", "show_essential_indicator_on_nametags", "false"},
                {"general.online_status", "show_essential_indicator_on_tab", "false"},
                {"general.experience", "show_nameplate_in_third_person", "false"},
                {"quality_of_life.nameplate", "show_my_nameplate_in_third-person", "false"},
                {"quality_of_life.screenshots", "essential_screenshots", "false"},
                {"quality_of_life.screenshots", "vanilla_screenshot_message", "true"},
                {"emotes.general", "disable_emotes", "true"},
                {"cosmetics.general", "disable_cosmetics", "true"},
                {"quality_of_life.discord_integration", "set_activity_status_on_discord", "false"}
            };

            for (String[] s : settings) {
                updated = setTomlValue(updated, s[0], s[1], s[2]);
            }

            if (!updated.equals(text)) {
                Files.writeString(tomlFile, updated, StandardCharsets.UTF_8);
            }
        } catch (Throwable ignored) {}
    }

    private static String setTomlValue(String text, String section, String key, String value) {
        String keyPattern = "(?m)^([\\t ]*" + Pattern.quote(key) + "[\\t ]*=[\\t ]*)[^\\r\\n]*";
        Pattern p = Pattern.compile(keyPattern);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.replaceAll("$1" + value);
        }
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        String sectionPattern = "(?m)^[\\t ]*\\[[\\t ]*" + Pattern.quote(section) + "[\\t ]*\\][^\\r\\n]*";
        Matcher sm = Pattern.compile(sectionPattern).matcher(text);
        if (sm.find()) {
            return text.substring(0, sm.end()) + newline + "\t\t" + key + " = " + value + text.substring(sm.end());
        }
        String sep = text.isEmpty() || text.endsWith("\n") ? "" : newline;
        return text + sep + "[" + section + "]" + newline + "\t" + key + " = " + value + newline;
    }

    private static void enforceOnboarding(Path onboardingFile) {
        try {
            if (!Files.exists(onboardingFile)) return;
            String text = Files.readString(onboardingFile, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            boolean changed = false;
            if (!json.has("sent_auto_update_telemetry") || json.get("sent_auto_update_telemetry").getAsBoolean()) {
                json.addProperty("sent_auto_update_telemetry", false);
                changed = true;
            }
            if (!json.has("allow_telemetry") || json.get("allow_telemetry").getAsBoolean()) {
                json.addProperty("allow_telemetry", false);
                changed = true;
            }
            if (changed) {
                Files.writeString(onboardingFile, json.toString(), StandardCharsets.UTF_8);
            }
        } catch (Throwable ignored) {}
    }
}
