// Ported from Capes 1.5.11+26.2 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.capes.menu;

import com.thelads.core.v26_2.embedded.capes.CapeConfig;
import com.thelads.core.v26_2.embedded.capes.CapeType;
import com.thelads.core.v26_2.embedded.capes.Capes;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.Options;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ToggleMenu extends MainMenu {

    public ToggleMenu(Screen parent, Options gameOptions) {
        super(parent, gameOptions);
    }

    @Override
    protected void init() {
        super.init();

        CapeConfig config = Capes.getConfig();

        addRenderableWidget(Button.builder(CapeType.OPTIFINE.getToggleText(config.getEnableOptifine()), button -> {
            config.setEnableOptifine(!config.getEnableOptifine());
            config.save();
            button.setMessage(CapeType.OPTIFINE.getToggleText(config.getEnableOptifine()));
        }).pos(width / 2 - 155, height / 7 + 24).size(150, 20).build());

        addRenderableWidget(Button.builder(CapeType.MINECRAFTCAPES.getToggleText(config.getEnableMinecraftCapesMod()), button -> {
            config.setEnableMinecraftCapesMod(!config.getEnableMinecraftCapesMod());
            config.save();
            button.setMessage(CapeType.MINECRAFTCAPES.getToggleText(config.getEnableMinecraftCapesMod()));
        }).pos(width / 2 - 155 + 160, height / 7 + 24).size(150, 20).build());

        addRenderableWidget(Button.builder(CapeType.LABYMOD.getToggleText(config.getEnableLabyMod()), button -> {
            config.setEnableLabyMod(!config.getEnableLabyMod());
            config.save();
            button.setMessage(CapeType.LABYMOD.getToggleText(config.getEnableLabyMod()));
        }).pos(width / 2 - 155, height / 7 + 2 * 24).size(150, 20).build());

        addRenderableWidget(Button.builder(CapeType.COSMETICA.getToggleText(config.getEnableCosmetica()), button -> {
            config.setEnableCosmetica(!config.getEnableCosmetica());
            config.save();
            button.setMessage(CapeType.COSMETICA.getToggleText(config.getEnableCosmetica()));
        }).pos(width / 2 - 155 + 160, height / 7 + 2 * 24).size(150, 20).build());

        addRenderableWidget(Button.builder(CapeType.CLOAKSPLUS.getToggleText(config.getEnableCloaksPlus()), button -> {
            config.setEnableCloaksPlus(!config.getEnableCloaksPlus());
            config.save();
            button.setMessage(CapeType.CLOAKSPLUS.getToggleText(config.getEnableCloaksPlus()));
        }).pos(width / 2 - 155, height / 7 + 3 * 24).size(150, 20).build());

//        addDrawableChild(ButtonWidget.builder(CapeType.CLOAKSPLUS.getToggleText(config.enableCloaksPlus)) {
//            config.enableCloaksPlus = !config.enableCloaksPlus
//            config.save()
//            it.message = CapeType.CLOAKSPLUS.getToggleText(config.enableCloaksPlus)
//        }.position(width / 2 - 155 + 160, height / 7 + 3 * 24).size(150, 20).build())

        addRenderableWidget(Button.builder(elytraMessage(config.getEnableElytraTexture()), button -> {
            config.setEnableElytraTexture(!config.getEnableElytraTexture());
            config.save();
            button.setMessage(elytraMessage(config.getEnableElytraTexture()));
        }).pos((width / 2) - (200 / 2), height / 7 + 4 * 24).size(200, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            minecraft.gui.setScreen(lastScreen);
        }).pos((width / 2) - (200 / 2), height / 7 + 5 * 24).size(200, 20).build());

    }

    private Component elytraMessage(boolean enabled) {
        return CommonComponents.optionStatus(Component.translatable("options.capes.elytra"), enabled);
    }

}
