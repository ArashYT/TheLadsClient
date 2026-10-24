package com.thelads.core.client.ladsapi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-game QA probe for LadsClientAPI acceptance checks.
 * Emits one structured JSON line per check recording server policy status, module states,
 * effective vs own option values, presence size, and zero-leak anti-ban proof.
 */
public final class LadsApiQa {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private LadsApiQa() {}

    public static JsonObject probe(String step) {
        JsonObject root = new JsonObject();
        root.addProperty("step", step == null ? "" : step);

        ServerPolicy policy = ServerPolicy.active();
        boolean policyActive = policy != null;
        root.addProperty("policyActive", policyActive);
        root.addProperty("serverName", policy != null ? policy.serverName() : LadsApiClient.serverName());

        // Modules: Fullbright, XaeroMinimap
        JsonObject modules = new JsonObject();
        ModuleManager mm = ModuleManager.getInstance();

        Module fbMod = mm != null ? mm.getModule("Fullbright") : null;
        JsonObject fbObj = new JsonObject();
        fbObj.addProperty("policyState", policy != null ? policy.module("Fullbright").name() : "NONE");
        fbObj.addProperty("enabled", fbMod != null && fbMod.isEnabled());
        modules.add("Fullbright", fbObj);

        Module xmMod = mm != null ? mm.getModule("XaeroMinimap") : null;
        JsonObject xmObj = new JsonObject();
        xmObj.addProperty("policyState", policy != null ? policy.module("XaeroMinimap").name() : "NONE");
        xmObj.addProperty("enabled", xmMod != null && xmMod.isEnabled());
        modules.add("XaeroMinimap", xmObj);

        // The minimap players see is the "Minimap" module (XaeroMinimap is its hidden legacy name).
        Module mapMod = mm != null ? mm.getModule("Minimap") : null;
        JsonObject mapObj = new JsonObject();
        mapObj.addProperty("policyState", policy != null ? policy.module("Minimap").name() : "NONE");
        mapObj.addProperty("enabled", mapMod != null && mapMod.isEnabled());
        mapObj.addProperty("own", mapMod != null && mapMod.isEnabledSetting());
        modules.add("Minimap", mapObj);

        root.add("modules", modules);

        // Options:
        // Fullbright.Brightness, Crosshair Tweaks.Shape/Gap/Outline Color, 1.7 Animations.1.7 sword block pose
        JsonObject options = new JsonObject();

        if (fbMod != null && fbMod.getOption("Brightness") instanceof SliderOption brightness) {
            JsonObject opt = new JsonObject();
            opt.addProperty("effective", brightness.getValue());
            opt.addProperty("own", brightness.getOwnValue());
            options.add("Fullbright.Brightness", opt);
        }

        Module crosshairMod = mm != null ? mm.getModule("Crosshair Tweaks") : null;
        if (crosshairMod != null) {
            if (crosshairMod.getOption("Shape") instanceof DropdownOption shape) {
                JsonObject opt = new JsonObject();
                opt.addProperty("effective", shape.getValue());
                int ownIdx = shape.getOwnIndex();
                String[] choices = shape.getChoices();
                opt.addProperty("own", (choices != null && ownIdx >= 0 && ownIdx < choices.length) ? choices[ownIdx] : "");
                opt.addProperty("effectiveIndex", shape.getIndex());
                opt.addProperty("ownIndex", ownIdx);
                options.add("Crosshair Tweaks.Shape", opt);
            }
            if (crosshairMod.getOption("Gap") instanceof SliderOption gap) {
                JsonObject opt = new JsonObject();
                opt.addProperty("effective", gap.getValue());
                opt.addProperty("own", gap.getOwnValue());
                options.add("Crosshair Tweaks.Gap", opt);
            }
            if (crosshairMod.getOption("Outline Color") instanceof ColorOption outlineColor) {
                JsonObject opt = new JsonObject();
                opt.addProperty("effective", String.format(java.util.Locale.ROOT, "#%08X", outlineColor.getColor()));
                opt.addProperty("own", String.format(java.util.Locale.ROOT, "#%08X", outlineColor.getOwnColor()));
                opt.addProperty("effectiveInt", outlineColor.getColor());
                opt.addProperty("ownInt", outlineColor.getOwnColor());
                options.add("Crosshair Tweaks.Outline Color", opt);
            }
        }

        Module oldAnimMod = mm != null ? mm.getModule("1.7 Animations") : null;
        if (oldAnimMod != null && oldAnimMod.getOption("1.7 sword block pose") instanceof BoolOption blockPose) {
            JsonObject opt = new JsonObject();
            opt.addProperty("effective", blockPose.get());
            opt.addProperty("own", blockPose.getOwn());
            options.add("1.7 Animations.1.7 sword block pose", opt);
        }

        root.add("options", options);

        root.addProperty("toastTitle", LadsApiUi.activeToastTitle()); // the QA bar draws over the toast, so its text goes here
        root.addProperty("toastNotice", LadsApiUi.activeToastNotice());
        root.addProperty("presenceSize", LadsPresence.size());
        root.addProperty("sentBeforeHello", LadsApiClient.sentBeforeHello());

        return root;
    }

    public static String probeJson(String step) {
        return GSON.toJson(probe(step));
    }

    public static void recordProbe(String step) {
        String json = probeJson(step);
        LOGGER.info("[LadsAPI-QA] PROBE: {}", json);
        System.out.println("[LadsAPI-QA] PROBE: " + json);
    }
}
