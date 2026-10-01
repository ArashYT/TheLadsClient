// Adapted from Capes 1.5.4+1.21 by Cael (LGPL-2.1-only); modified by The Lads: remapped to Mojang mappings and repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.capes.mixin;

import com.thelads.core.v1_21_1.embedded.capes.menu.SelectorMenu;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkinCustomizationScreen.class)
public abstract class MixinSkinOptionsScreen extends OptionsSubScreen {

    @Unique
    private static final ResourceLocation CAPE_OPTIONS_ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath("capes","icon/cape_options");
    @Unique
    private final SelectorMenu capes$selectorMenu = new SelectorMenu(this, this.options);

    public MixinSkinOptionsScreen(Screen parent, Options gameOptions, Component title) {
        super(parent, gameOptions, title);
    }

    @Inject(method = "addOptions", at = @At("RETURN"))
    public void addOptions(CallbackInfo info) {
        this.addRenderableWidget(SpriteIconButton.builder(Component.empty(), (buttonWidget) -> this.minecraft.setScreen(capes$selectorMenu), true).size(20, 20).sprite(CAPE_OPTIONS_ICON_TEXTURE, 16, 16).build()).setPosition(list.getRowLeft() - 25, list.getY() + 4);
    }
}
