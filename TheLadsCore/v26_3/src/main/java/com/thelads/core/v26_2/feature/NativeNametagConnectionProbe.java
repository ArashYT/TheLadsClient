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
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
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

            passed += submittedTags(minecraft, (ToggleNametagsModule) tags);
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

    /** Background and Text Shadow on the real name-tag submission, and renames through the real tab list. */
    private static int submittedTags(Minecraft minecraft, ToggleNametagsModule tags) {
        BoolOption background = (BoolOption) tags.getOption("Render Background"), shadow = (BoolOption) tags.getOption("Text Shadow");
        boolean backgroundBefore = background.get(), shadowBefore = shadow.get();
        String nicknamesBefore = tags.nicknames.getValue(), displayBefore = tags.displayName.getValue();
        int passed = 0;
        try {
            background.set(false);
            shadow.set(true);
            var text = submitted();
            require(text.backgroundColor() == 0 && text.dropShadow(), "background off and text shadow on the submitted name tag"); passed++;
            background.set(true);
            shadow.set(false);
            text = submitted();
            require(text.backgroundColor() != 0 && !text.dropShadow(), "vanilla background and flat text restored"); passed++;
            tags.nicknames.setValue("QaNickTarget=QaNick");
            tags.displayName.setValue("QaSelf");
            require(NativeNicknames.rename(Component.literal("<QaNickTarget> hi QaNickTargets")).getString().equals("<QaNick> hi QaNickTargets"), "nicknames replace whole names only"); passed++;
            var info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
            require(info != null && minecraft.gui.hud.getTabList().getNameForDisplay(info).getString().contains("QaSelf"), "tab list shows Your Display Name"); passed++;
            return passed;
        } finally {
            background.set(backgroundBefore);
            shadow.set(shadowBefore);
            tags.nicknames.setValue(nicknamesBefore);
            tags.displayName.setValue(displayBefore);
        }
    }

    private static TextFeatureRenderer.Content.Text submitted() {
        var collection = new SubmitNodeCollection(false, new TranslucentFeatureRenderPhase());
        var camera = new CameraRenderState();
        camera.orientation = new Quaternionf();
        collection.submitNameTag(new PoseStack(), Vec3.ZERO, 0, Component.literal("LadsQA"), false, 0xf000f0, camera);
        List<TextFeatureRenderer.Content.Text> texts = new ArrayList<>();
        FeatureRenderPhase.Output output = (node, ordered) -> {
            if (node instanceof TextFeatureRenderer.Submit submit && submit.content() instanceof TextFeatureRenderer.Content.Text text) texts.add(text);
        };
        collection.solid.sortInto(output);
        collection.nameTags.sortInto(output);
        require(texts.size() == 1, "one name tag submitted, got " + texts.size());
        return texts.get(0);
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
