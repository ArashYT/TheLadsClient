package com.thelads.core.v26_2.feature.paperdoll;

import com.google.gson.JsonElement;
import com.thelads.core.client.hud.PaperDoll;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.v26_2.feature.NativeAutohide;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import org.slf4j.LoggerFactory;

/** QA only (thelads.verifyIntegrations, in the QA world): the doll's visibility, angles and opacity on the real renderer. Restores all. */
final class PaperDollProbe26 {
    private static boolean done;
    private static int ticks, passed;
    private PaperDollProbe26() {}

    static void tick(Minecraft mc) {
        if (done || !Boolean.getBoolean("thelads.verifyIntegrations") || ++ticks < 90) return;
        done = true;
        var module = NativeQualityOfLife.module("Paperdoll");
        boolean enabled = module.isEnabled();
        long modified = module.getLastModified();
        Map<Option, JsonElement> saved = new LinkedHashMap<>();
        module.getOptions().forEach(option -> saved.put(option, option.save().deepCopy()));
        var player = mc.player;
        float pitch = player.getXRot(), yaw = player.getYRot(), body = player.yBodyRot, head = player.yHeadRot;
        try {
            module.setEnabled(false);
            check(!PaperDoll.INSTANCE.visible(true) && !PaperDoll.INSTANCE.visible(false), "module off: no doll in gameplay");
            check(pictures(false).length == 0, "module off: nothing drawn");
            check(pictures(true).length == 1, "the HUD editor previews the doll anyway");
            module.setEnabled(true);
            bool("Always Display", true);
            bool("Show in First Person", false);
            bool("Show in Third Person", false);
            check(!PaperDoll.INSTANCE.visible(mc.options.getCameraType().isFirstPerson()), "camera switches hide it");
            bool(mc.options.getCameraType().isFirstPerson() ? "Show in First Person" : "Show in Third Person", true);
            check(pictures(false).length == 1, "always display with the current camera allowed draws it");
            number("Default Rotation", 20);
            number("Maximum Pitch", 10);
            ((com.thelads.core.config.DropdownOption) module.getOption("Head Movement")).setIndex(0);
            player.setXRot(70);
            var left = PaperDoll26.state(1, true);
            check(left instanceof AvatarRenderState avatar && avatar.skin != null, "the player's own avatar state and skin");
            var avatar = (AvatarRenderState) left;
            check(avatar.bodyRot == 160 && ((AvatarRenderState) PaperDoll26.state(1, false)).bodyRot == 200, "turned toward the screen middle from either side");
            check(avatar.xRot == 10, "pitch held within Maximum Pitch (" + avatar.xRot + ")");
            check(avatar.scale == 1 && avatar.shadowPieces.isEmpty() && avatar.outlineColor == 0, "no world scale, shadow or outline");
            check(player.getXRot() == 70 && player.getYRot() == yaw && player.yBodyRot == body && player.yHeadRot == head, "the player is not turned");
            number("Model Opacity", 50);
            NativeAutohide.PICTURES.clear();
            var faded = pictures(false);
            check(faded.length == 1 && Float.valueOf(0.5f).equals(NativeAutohide.PICTURES.get(faded[0])), "Model Opacity fades the doll's picture");
            NativeAutohide.PICTURES.clear();
            var opaque = pictures(true);
            check(opaque.length == 1 && !NativeAutohide.PICTURES.containsKey(opaque[0]), "the editor preview stays opaque");
            LoggerFactory.getLogger("TheLadsCore").info("Lads paper doll probe END: {} passed, 0 failed", passed);
        } catch (Throwable failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads paper doll probe FAILED after {} checks", passed, failure);
        } finally {
            player.setXRot(pitch);
            NativeAutohide.PICTURES.clear();
            saved.forEach(Option::load);
            module.setEnabled(enabled);
            module.setLastModified(modified);
        }
    }

    /** The entity pictures one doll draw adds to a fresh GUI state. */
    private static GuiEntityRenderState[] pictures(boolean editor) {
        var state = new GuiRenderState();
        PaperDoll26.render(new GuiGraphicsExtractor(Minecraft.getInstance(), state, 0, 0), 20, 20, 70, 70, editor);
        var found = new java.util.ArrayList<GuiEntityRenderState>();
        state.forEachPictureInPicture(picture -> { if (picture instanceof GuiEntityRenderState entity) found.add(entity); });
        return found.toArray(GuiEntityRenderState[]::new);
    }

    private static void bool(String name, boolean value) { ((BoolOption) NativeQualityOfLife.module("Paperdoll").getOption(name)).set(value); }
    private static void number(String name, double value) { ((SliderOption) NativeQualityOfLife.module("Paperdoll").getOption(name)).setValue(value); }
    private static void check(boolean condition, String text) {
        if (!condition) throw new IllegalStateException(text);
        passed++;
    }
}
