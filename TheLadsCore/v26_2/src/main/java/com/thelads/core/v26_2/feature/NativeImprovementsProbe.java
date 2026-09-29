package com.thelads.core.v26_2.feature;

import com.thelads.core.config.*;
import com.thelads.core.client.ClientTools;
import com.thelads.core.client.bridge.LadsGameBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.particles.ParticleTypes;
import java.util.*;

/** Called only by the explicit isolated-world QA harness. Exercises transformed game APIs. */
final class NativeImprovementsProbe {
    private static int passed;
    private static com.google.gson.JsonObject captureSettings;
    static int run() throws Exception {
        var mc = Minecraft.getInstance();
        NativeWorldVerification.checkedGameDirectory(mc.gameDirectory.toPath());
        var settings = ConfigManager.toJson();
        var inventory = mc.player.getInventory();
        var items = new ArrayList<ItemStack>();
        for (int i = 0; i < inventory.getContainerSize(); i++) items.add(inventory.getItem(i).copy());
        var oldHit = mc.hitResult;
        String clipboard = mc.keyboardHandler.getClipboard();
        var chat = mc.gui.hud.getChat(); var chatState = chat.storeState();
        try {
            for (String name : List.of("Clock", "Stopwatch", "ItemCounter", "ReachDisplay", "ServerAddress", "PortalCoordinates", "ClientTools", "ParticleBudget")) {
                require(ModuleSupport.isBuiltIn(name), name + " is connected");
                NativeQualityOfLife.module(name).setEnabled(true);
            }
            for (String key : List.of("key.thelads.copy_coordinates", "key.thelads.stopwatch", "key.thelads.stopwatch_reset"))
                require(Arrays.stream(mc.options.keyMappings).anyMatch(k -> k.getName().equals(key)), key + " registered in real controls");
            NativeClientTools.tick();
            ((ActionOption)NativeQualityOfLife.module("ClientTools").getOption("Copy coordinates")).run();
            require(mc.keyboardHandler.getClipboard().contains("X: " + mc.player.blockPosition().getX()), "copy action writes coordinates to clipboard");
            var watch = ClientTools.STOPWATCH;
            boolean running = watch.running();
            ((ActionOption)NativeQualityOfLife.module("Stopwatch").getOption("Start or pause")).run();
            require(watch.running() != running, "timer action toggles"); watch.toggle();
            for (int i = 0; i < inventory.getContainerSize(); i++) inventory.setItem(i, ItemStack.EMPTY);
            inventory.setItem(0, new ItemStack(Items.ARROW, 32)); inventory.setItem(1, new ItemStack(Items.TIPPED_ARROW, 5));
            require(NativeClientTools.itemCount(1).equals("Arrows: 37"), "counter includes arrow variants");
            mc.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 7));
            NativeClientTools.itemCount(0);
            require(NativeClientTools.itemCount(1).equals("Arrows: 44"), "counter includes offhand exactly once");
            var weak = new ItemStack(Items.DIAMOND_SWORD); weak.setDamageValue(weak.getMaxDamage() - 1);
            inventory.setItem(inventory.getSelectedSlot(), weak);
            NativeClientTools.tick();
            var overlay = net.minecraft.client.gui.Hud.class.getDeclaredField("overlayMessageString"); overlay.setAccessible(true);
            require(((Component)overlay.get(mc.gui.hud)).getString().contains("Low durability"), "real action bar displays durability warning");
            mc.gui.hud.setOverlayMessage(Component.literal("sentinel"), false); NativeClientTools.tick();
            require(((Component)overlay.get(mc.gui.hud)).getString().equals("sentinel"), "durability warning does not spam each tick");
            for (int i = 0; i < 36; i++) inventory.setItem(i, new ItemStack(Items.STONE, 64));
            NativeClientTools.tick();
            require(((Component)overlay.get(mc.gui.hud)).getString().contains("Inventory is full"), "full inventory transition reaches action bar");
            ((BoolOption)NativeQualityOfLife.module("ClientTools").getOption("Chat timestamps")).set(true);
            var original = Component.literal("Styled QA").withStyle(net.minecraft.ChatFormatting.AQUA);
            var signature = new MessageSignature(new byte[256]); var tag = GuiMessageTag.system();
            chat.addPlayerMessage(original, signature, tag);
            var messages = chat.getClass().getDeclaredField("allMessages"); messages.setAccessible(true);
            var message = (GuiMessage)((List<?>)messages.get(chat)).getFirst();
            require(message.content().getString().matches("\\[\\d{2}:\\d{2}\\] Styled QA"), "transformed chat entry adds timestamp");
            require(message.signature() == signature && message.tag() == tag && original.getString().equals("Styled QA"), "chat signature, tag and original content retained");
            require(message.content().getSiblings().getLast().getStyle().equals(original.getStyle()), "original chat styling retained");
            var bridge = LadsGameBridge.get();
            require(bridge.getActivePotionEffects() == bridge.getActivePotionEffects(), "same tick reuses potion snapshot");
            int originalTick = mc.player.tickCount;
            var oldSpeed = mc.player.getEffect(net.minecraft.world.effect.MobEffects.SPEED);
            if (oldSpeed != null) oldSpeed = new net.minecraft.world.effect.MobEffectInstance(oldSpeed);
            try {
                mc.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 1200));
                mc.player.tickCount++;
                require(bridge.getActivePotionEffects().stream().anyMatch(t -> t.contains("60s")), "next player tick refreshes real effects");
            } finally {
                mc.player.removeEffect(net.minecraft.world.effect.MobEffects.SPEED);
                if (oldSpeed != null) mc.player.addEffect(oldSpeed);
                mc.player.tickCount = originalTick;
            }
            require(bridge.getActiveResourcePacks() == bridge.getActiveResourcePacks(), "unchanged pack selection reuses snapshot");
            var repository = mc.getResourcePackRepository();
            var selected = new ArrayList<>(repository.getSelectedIds());
            try {
                repository.setSelected(List.of());
                require(bridge.getActiveResourcePacks().equals(repository.getSelectedPacks().stream().map(net.minecraft.server.packs.repository.Pack::getId).toList()), "pack cache follows selection changes");
            } finally { repository.setSelected(selected); }
            require(bridge.getServerAddress().equals("Singleplayer"), "local connection HUD label");
            require(!ClientTools.portal(bridge.getDimensionId(), -1, -9).isBlank(), "dimension bridge supplies portal conversion");
            double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
            ((SliderOption)NativeQualityOfLife.module("ParticleBudget").getOption("Particles per tick")).setValue(8);
            for (int i = 0; i < 8; i++) require(mc.particleEngine.createParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0) != null, "decorative quota accepts " + i);
            require(mc.particleEngine.createParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0) == null, "transformed particle engine enforces quota");
            require(mc.particleEngine.createParticle(ParticleTypes.CRIT, x, y, z, 0, 0, 0) != null, "gameplay particles bypass quota");
            require(mc.particleEngine.createParticle(ParticleTypes.SMOKE, x + 200, y, z, 0, 0, 0) == null, "distance limit applies to decoration");
            var target = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, x, y, z + 2);
            target.setId(Integer.MAX_VALUE - 130);
            var location = mc.player.getEyePosition().add(0, 0, 2);
            mc.hitResult = new net.minecraft.world.phys.EntityHitResult(target, location);
            mc.gameMode.attack(mc.player, target);
            require(NativeClientTools.reachText().equals("Reach: 2.00 blocks"), "real attack method records client hit distance");
            org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("Lads improvements probe END: {} passed, 0 failed", passed);
            return passed;
        } finally {
            for (int i = 0; i < items.size(); i++) inventory.setItem(i, items.get(i));
            mc.hitResult = oldHit; mc.keyboardHandler.setClipboard(clipboard); chat.restoreState(chatState);
            ConfigManager.applyJson(settings);
        }
    }
    static void prepareCapture() {
        if (!Boolean.getBoolean("thelads.verifyRequestedFeaturesOnly") || passed == 0 || captureSettings != null) return;
        captureSettings = ConfigManager.toJson();
        for (var module : ModuleManager.getInstance().getModules()) if (module instanceof com.thelads.core.modules.HudModule) module.setEnabled(false);
        for (String name : List.of("Clock", "Stopwatch", "ItemCounter", "ReachDisplay", "ServerAddress", "PortalCoordinates")) NativeQualityOfLife.module(name).setEnabled(true);
        if (!ClientTools.STOPWATCH.running()) ClientTools.STOPWATCH.toggle();
        var mc = Minecraft.getInstance();
        var adapter = new com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter(new net.minecraft.client.gui.GuiGraphicsExtractor(mc, new net.minecraft.client.renderer.state.gui.GuiRenderState(), 0, 0));
        Object before = adapter.textMetricsKey();
        mc.reloadResourcePacks().whenComplete((ignored, failure) -> mc.execute(() -> {
            if (failure == null && adapter.textMetricsKey() != before)
                org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("Lads font reload probe END: 1 passed, 0 failed; actual resource reload invalidates text metrics");
            else org.slf4j.LoggerFactory.getLogger("TheLadsCore").error("Lads font reload probe FAILED", failure);
        }));
    }
    static void restoreCapture() {
        if (captureSettings != null) { ConfigManager.applyJson(captureSettings); captureSettings = null; }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
        passed++;
    }
}
