package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.v1_20_1.feature.LocalSkins1201;
import com.thelads.core.v1_20_1.gui.LadsButton1201;
import com.thelads.core.v1_20_1.gui.PlayerPreview1201;
import com.thelads.core.v1_20_1.gui.SkinChangerScreen1201;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.SkinCustomizationScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skin customisation as on 26.3: the Lads backdrop, a turnable preview of the skin, a toggle per outer layer and Change skin. */
@Mixin(SkinCustomizationScreen.class)
public abstract class SkinScreenMixin extends OptionsSubScreen {
    protected SkinScreenMixin(Screen parent, Options options, Component title) {
        super(parent, options, title);
    }

    @Inject(method = "init()V", at = @At("HEAD"), cancellable = true)
    private void ladsInit(CallbackInfo ci) {
        int left = Math.max(12, width / 2 - 180), top = 55, previewWidth = Math.min(150, width / 3);
        LocalSkins1201.initialize();
        var preview = new PlayerPreview1201(previewWidth, Math.max(50, Math.min(height - 150, (int) (previewWidth * 1.6))),
            () -> LocalSkins1201.current() == null ? ladsAccountSkin() : LocalSkins1201.current().texture(),
            () -> LocalSkins1201.current() == null ? ladsAccountSlim() : LocalSkins1201.current().slim());
        preview.setX(left);
        preview.setY(top);
        addRenderableWidget(preview);
        addRenderableWidget(new LadsButton1201(left, height - 60, previewWidth, 20, Component.literal("Change skin"),
            b -> minecraft.setScreen(new SkinChangerScreen1201(this))));
        int x = left + previewWidth + 20, w = Math.max(90, Math.min(195, width - x - 12));
        int row = 0, step = Math.max(20, Math.min(27, (height - 100) / 8));
        for (var part : PlayerModelPart.values()) {
            addRenderableWidget(new LadsButton1201(x, top + row++ * step, w, 20,
                part.getName().copy().append(options.isModelPartEnabled(part) ? ": ON" : ": OFF"), button -> {
                    options.toggleModelPart(part, !options.isModelPartEnabled(part));
                    button.setMessage(part.getName().copy().append(options.isModelPartEnabled(part) ? ": ON" : ": OFF"));
                }));
        }
        addRenderableWidget(new LadsButton1201(x, top + row * step, w, 20,
            Component.literal("Main hand: " + (options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.RIGHT ? "right" : "left")), button -> {
                options.mainHand().set(options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.RIGHT
                    ? net.minecraft.world.entity.HumanoidArm.LEFT : net.minecraft.world.entity.HumanoidArm.RIGHT);
                button.setMessage(Component.literal("Main hand: " + (options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.RIGHT ? "right" : "left")));
            }));
        addRenderableWidget(new LadsButton1201(width / 2 - 55, height - 29, 110, 20, Component.literal("Done"), button -> {
            options.save();
            onClose();
        }));
        ci.cancel();
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void ladsRender(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        g.fill(0, 0, width, height, 0xFF100B10);
        g.fill(0, 0, width, 2, 0xFFCF1535);
        int left = Math.max(12, width / 2 - 180);
        g.fill(left - 4, 49, left + Math.min(150, width / 3) + 4, height - 40, 0xFF24151D);
        super.render(g, mx, my, delta);
        g.drawCenteredString(font, Component.literal("THE LADS / SKIN CUSTOMIZATION"), width / 2, 15, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.literal("Drag your skin preview to rotate"), width / 2, 32, 0xFFB7A5AF);
        ci.cancel();
    }

    @org.spongepowered.asm.mixin.Unique
    private static net.minecraft.resources.ResourceLocation ladsAccountSkin() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.getSkinManager().getInsecureSkinLocation(mc.getUser().getGameProfile());
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean ladsAccountSlim() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var profile = mc.getUser().getGameProfile();
        var skin = mc.getSkinManager().getInsecureSkinInformation(profile).get(com.mojang.authlib.minecraft.MinecraftProfileTexture.Type.SKIN);
        if (skin != null) return "slim".equals(skin.getMetadata("model"));
        return "slim".equals(net.minecraft.client.resources.DefaultPlayerSkin.getSkinModelName(profile.getId()));
    }
}
