package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.modules.ToggleNametagsModule;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.slf4j.LoggerFactory;

/** Invoked only by the existing opt-in isolated QA probe; restores preferences before returning. */
final class NativeNametagConnectionProbe {
    private NativeNametagConnectionProbe() {}

    static int run() throws ReflectiveOperationException {
        Minecraft minecraft = Minecraft.getInstance();
        Module tags = NativeQualityOfLife.module("Nametags");
        Module signal = NativeQualityOfLife.module("SignalLoss");
        BoolOption own = (BoolOption) tags.getOption("Show Own Nametag in Third Person");
        BoolOption background = (BoolOption) tags.getOption("Render Background");
        BoolOption localSignal = (BoolOption) signal.getOption("Show in Singleplayer");
        boolean localSignalBefore = localSignal.get();
        boolean tagsBefore = tags.isEnabled(), signalBefore = signal.isEnabled();
        long tagsModifiedBefore = tags.getLastModified(), signalModifiedBefore = signal.getLastModified();
        long tagsModified = tags.getLastModified(), signalModified = signal.getLastModified();
        boolean ownBefore = own.get(), backgroundBefore = background.get();
        CameraType cameraBefore = minecraft.options.getCameraType();
        int passed = 0;
        try {
            Method nameVisible = LivingEntityRenderer.class.getDeclaredMethod("shouldShowName", LivingEntity.class, double.class);
            nameVisible.setAccessible(true);
            var renderer = minecraft.getEntityRenderDispatcher().getRenderer(minecraft.player);
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            // As on 26.3: other pack mods may already show the own name tag; the module must leave their choice alone.
            tags.setEnabled(false);
            own.set(true);
            boolean baseline = (boolean) nameVisible.invoke(renderer, minecraft.player, 4.0);
            own.set(false);
            require((boolean) nameVisible.invoke(renderer, minecraft.player, 4.0) == baseline, "disabled module preserves upstream nametag visibility"); passed++;
            tags.setEnabled(true);
            own.set(false);
            require((boolean) nameVisible.invoke(renderer, minecraft.player, 4.0) == baseline, "own nametag option off preserves upstream visibility"); passed++;
            own.set(true);
            require((boolean) nameVisible.invoke(renderer, minecraft.player, 4.0), "own nametag appears in third person through real renderer"); passed++;
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            tags.setEnabled(false);
            boolean firstPersonBaseline = (boolean) nameVisible.invoke(renderer, minecraft.player, 4.0);
            tags.setEnabled(true);
            require((boolean) nameVisible.invoke(renderer, minecraft.player, 4.0) == firstPersonBaseline, "own nametag option preserves upstream first-person visibility"); passed++;
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            require((boolean) nameVisible.invoke(renderer, minecraft.player, 4.0), "own nametag appears in front third person"); passed++;

            // Background and renames are set on the submitted tag (Essential's icon padding reads it); the renderer follows.
            // Text Shadow is applied while rendering and is verified by the render capture.
            Method prepare = NameTagFeatureRenderer.class.getDeclaredMethod("prepareText", Font.class, NameTagFeatureRenderer.Submit.class);
            prepare.setAccessible(true);
            background.set(true);
            require(effects((Font.PreparedText) prepare.invoke(null, minecraft.font, submitted("LadsQA"))) > 0, "name-tag renderer emits background geometry"); passed++;
            background.set(false);
            require(submitted("LadsQA").backgroundColor() == 0, "background option clears the submitted tag's background"); passed++;
            require(effects((Font.PreparedText) prepare.invoke(null, minecraft.font, submitted("LadsQA"))) == 0, "background option removes geometry"); passed++;
            ToggleNametagsModule names = (ToggleNametagsModule) tags;
            String nicknamesBefore = names.nicknames.getValue(), displayBefore = names.displayName.getValue();
            try {
                names.nicknames.setValue("QaNickTarget=QaNick");
                names.displayName.setValue("QaSelf");
                require(submitted("<QaNickTarget> hi QaNickTargets").text().getString().equals("<QaNick> hi QaNickTargets"), "name tags show nicknames for whole names only"); passed++;
                var info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
                require(info != null && minecraft.gui.hud.getTabList().getNameForDisplay(info).getString().contains("QaSelf"), "tab list shows Your Display Name"); passed++;
            } finally {
                names.nicknames.setValue(nicknamesBefore);
                names.displayName.setValue(displayBefore);
            }
            tags.setEnabled(false);
            require(effects((Font.PreparedText) prepare.invoke(null, minecraft.font, submitted("LadsQA"))) > 0, "disabling module restores vanilla background"); passed++;

            var connection = minecraft.getConnection().getConnection();
            require(connection instanceof PacketActivitySource, "real game connection implements packet activity hook"); passed++;
            long lastPacket = ((PacketActivitySource) connection).lads$lastPacketNanos();
            require(lastPacket != 0, "actual inbound game traffic updated monotonic timestamp"); passed++;
            var policy = new com.thelads.core.client.SignalLossPolicy();
            var settings = new com.thelads.core.client.SignalLossPolicy.Settings(2000, 2000, 1000);
            policy.joined(connection, lastPacket - 6_000_000_000L);
            require(!policy.update(connection, lastPacket + 2_000_000_000L, lastPacket, true, false, true, settings).interrupted(), "no early connection warning"); passed++;
            require(policy.update(connection, lastPacket + 2_001_000_000L, lastPacket, true, false, true, settings).interrupted(), "configured silence threshold boundary"); passed++;
            require(policy.update(connection, lastPacket + 25_000_000_000L, lastPacket, true, false, true, settings).seconds() == 25, "sustained server silence count"); passed++;
            require(policy.update(connection, lastPacket + 26_000_000_000L, lastPacket + 26_000_000_000L, true, false, true, settings).lingering(), "new packet enters configured recovery linger"); passed++;
            require(policy.update(connection, lastPacket + 27_000_000_000L, lastPacket, false, false, true, settings).progress() == 0, "ineligible connection suppresses warning"); passed++;
            policy.joined(connection, lastPacket);
            require(policy.update(connection, lastPacket + 4_999_000_000L, lastPacket, true, false, true, settings).progress() == 0, "join grace suppresses initial silence"); passed++;
            signal.setEnabled(false);
            require(NativeConnectionStatus.warningSeconds() == 0, "module off suppresses actual HUD warning"); passed++;
            signal.setEnabled(true);
            localSignal.set(false);
            if (minecraft.hasSingleplayerServer()) {
                require(NativeConnectionStatus.warningSeconds() == 0, "singleplayer never warns about remote connection loss"); passed++;
            }
            LoggerFactory.getLogger("TheLadsCore").info("Lads nametag and connection probe END: {} passed, 0 failed", passed);
            return passed;
        } finally {
            minecraft.options.setCameraType(cameraBefore);
            own.set(ownBefore);
            background.set(backgroundBefore);
            tags.setEnabled(tagsBefore);
            signal.setEnabled(signalBefore);
            localSignal.set(localSignalBefore);
            tags.setLastModified(tagsModifiedBefore);
            signal.setLastModified(signalModifiedBefore);
            tags.setLastModified(tagsModified);
            signal.setLastModified(signalModified);
        }
    }

    private static NameTagFeatureRenderer.Submit submitted(String name) {
        var collection = new SubmitNodeCollection();
        var camera = new CameraRenderState();
        camera.orientation = new Quaternionf();
        collection.submitNameTag(new PoseStack(), Vec3.ZERO, 0, Component.literal(name), false, 0xf000f0, camera);
        List<NameTagFeatureRenderer.Submit> submits = new ArrayList<>();
        collection.nameTags.sortInto((node, ordered) -> { if (node instanceof NameTagFeatureRenderer.Submit submit) submits.add(submit); });
        require(submits.size() == 1, "one name tag submitted, got " + submits.size());
        return submits.get(0);
    }

    private static int effects(Font.PreparedText text) {
        int[] count = {0};
        text.visit(new Font.GlyphVisitor() {
            @Override public void acceptEffect(TextRenderable effect) { count[0]++; }
        });
        return count[0];
    }

    private static void require(boolean value, String name) {
        if (!value) throw new IllegalStateException(name);
    }
}
