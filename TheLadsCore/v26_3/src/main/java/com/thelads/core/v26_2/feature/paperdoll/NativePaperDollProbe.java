package com.thelads.core.v26_2.feature.paperdoll;

import com.google.gson.JsonElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.LoggerFactory;

/** Opt-in transformed-runtime checks; restores actual equipment and module preferences in finally. */
public final class NativePaperDollProbe {
    private static boolean done;
    private static int ticks, passed;
    private NativePaperDollProbe() {}
    static void tick() {
        if (done || !Boolean.getBoolean("thelads.verifyIntegrations") || !NativePaperDoll.active()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || ++ticks < 90) return;
        done = true;
        try { run(); }
        catch (Throwable failure) { LoggerFactory.getLogger("TheLadsCore").error("Lads paper doll probe FAILED", failure); }
    }

    private static void run() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var module = NativeQualityOfLife.module("Paperdoll");
        boolean enabled = module.isEnabled();
        long modified = module.getLastModified();
        Map<Option, JsonElement> options = new LinkedHashMap<>();
        module.getOptions().forEach(option -> options.put(option, option.save().deepCopy()));
        var main = player.getMainHandItem(); var off = player.getOffhandItem();
        var chest = player.getItemBySlot(EquipmentSlot.CHEST);
        float yaw = player.getYRot(), pitch = player.getXRot(), head = player.yHeadRot, body = player.yBodyRot;
        try {
            module.setEnabled(false);
            require(!NativePaperDoll.visible(mc, false), "disabled master prevents model");
            require(NativePaperDoll.visible(mc, true), "editor shows actual player without enabling module");
            module.setEnabled(true);
            bool("Always Display", true); bool("Show in First Person", false); bool("Show in Third Person", false);
            require(!NativePaperDoll.visible(mc, false), "camera restrictions prevent model");
            bool(mc.options.getCameraType().isFirstPerson() ? "Show in First Person" : "Show in Third Person", true);
            require(NativePaperDoll.visible(mc, false), "allowed current camera shows model");
            number("Default Rotation", 20); number("Maximum Pitch", 10); number("Model Opacity", 50);
            ((DropdownOption) module.getOption("Head Movement")).setIndex(0);
            player.setXRot(70);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            var state = NativePaperDoll.extractState(1, false);
            require(state instanceof AvatarRenderState, "native renderer extracts actual avatar state");
            var avatar = (AvatarRenderState) state;
            require(avatar.skin != null, "actual player skin is retained");
            require(avatar.bodyRot == 160, "left anchor default rotation");
            require(Math.abs(avatar.xRot) <= 10, "pitch is bounded");
            require(avatar.scale == 1 && avatar.shadowPieces.isEmpty() && avatar.outlineColor == 0, "HUD presentation has normalized scale without world shadow/outline");
            require(((PaperDollRenderState) avatar).ladsPaperDollAlpha() == 128, "opacity belongs to extracted state");
            require(avatar.chestEquipment.is(Items.DIAMOND_CHESTPLATE), "actual armor retained");
            require(!avatar.rightHandItemState.isEmpty() && !avatar.leftHandItemState.isEmpty(), "both actual held item render states retained");
            require(avatar.getMainHandItemStack().is(Items.DIAMOND_SWORD), "main-hand identity retained");
            require(avatar.pose == player.getPose(), "actual movement pose retained");
            require(player.getYRot() == yaw && player.yHeadRot == head && player.yBodyRot == body && player.getXRot() == 70,
                    "extraction never alters player orientation");
            require(NativePaperDoll.extractState(1, true).bodyRot == 200, "right anchor mirrors rotation");
            number("Model Opacity", 100);
            require(((PaperDollRenderState) NativePaperDoll.extractState(1, false)).ladsPaperDollAlpha() == 255, "restored opacity does not inherit old alpha");
            var ordinary = mc.getEntityRenderDispatcher().getRenderer(player).createRenderState(player, 1);
            require(((PaperDollRenderState) ordinary).ladsPaperDollAlpha() == 255, "separate world render state remains opaque");
            for (String option : new String[] {"Sprinting", "Swimming", "Crawling", "Crouching", "Creative Flying", "Elytra Gliding", "Riding", "Spin Attacking", "Using Items"}) bool(option, false);
            require(!NativePaperDoll.performingAction(player, false), "all action switches disabled prevents triggers");
            LoggerFactory.getLogger("TheLadsCore").info("Lads paper doll probe END: {} passed, 0 failed", passed);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, main); player.setItemInHand(InteractionHand.OFF_HAND, off);
            player.setItemSlot(EquipmentSlot.CHEST, chest); player.setXRot(pitch);
            options.forEach(Option::load); module.setEnabled(enabled); module.setLastModified(modified);
        }
    }
    private static void bool(String name, boolean value) { ((BoolOption) NativeQualityOfLife.module("Paperdoll").getOption(name)).set(value); }
    private static void number(String name, double value) { ((SliderOption) NativeQualityOfLife.module("Paperdoll").getOption(name)).setValue(value); }
    private static void require(boolean condition, String text) { if (!condition) throw new IllegalStateException(text); passed++; }
}
