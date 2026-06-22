package com.thelads.core.client.capes.menu;

import com.thelads.core.client.capes.CapeConfig;
import com.thelads.core.client.capes.CapeType;
import com.thelads.core.client.capes.Capes;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ToggleMenu extends MainMenu {
    public ToggleMenu(Screen parent, Options gameOptions) {
        super(parent, gameOptions);
    }

    @Override
    protected void init() {
        super.init();
        CapeConfig config = Capes.INSTANCE.getCONFIG();

        this.addRenderableWidget(Button.builder(CapeType.OPTIFINE.getToggleText(config.getEnableOptifine()), button -> {
            config.setEnableOptifine(!config.getEnableOptifine());
            config.save();
            button.setMessage(CapeType.OPTIFINE.getToggleText(config.getEnableOptifine()));
        }).pos(this.width / 2 - 155, this.height / 7 + 24).size(150, 20).build());

        this.addRenderableWidget(Button.builder(CapeType.MINECRAFTCAPES.getToggleText(config.getEnableMinecraftCapesMod()), button -> {
            config.setEnableMinecraftCapesMod(!config.getEnableMinecraftCapesMod());
            config.save();
            button.setMessage(CapeType.MINECRAFTCAPES.getToggleText(config.getEnableMinecraftCapesMod()));
        }).pos(this.width / 2 - 155 + 160, this.height / 7 + 24).size(150, 20).build());

        this.addRenderableWidget(Button.builder(CapeType.LABYMOD.getToggleText(config.getEnableLabyMod()), button -> {
            config.setEnableLabyMod(!config.getEnableLabyMod());
            config.save();
            button.setMessage(CapeType.LABYMOD.getToggleText(config.getEnableLabyMod()));
        }).pos(this.width / 2 - 155, this.height / 7 + 48).size(150, 20).build());

        this.addRenderableWidget(Button.builder(CapeType.COSMETICA.getToggleText(config.getEnableCosmetica()), button -> {
            config.setEnableCosmetica(!config.getEnableCosmetica());
            config.save();
            button.setMessage(CapeType.COSMETICA.getToggleText(config.getEnableCosmetica()));
        }).pos(this.width / 2 - 155 + 160, this.height / 7 + 48).size(150, 20).build());

        this.addRenderableWidget(Button.builder(CapeType.CLOAKSPLUS.getToggleText(config.getEnableCloaksPlus()), button -> {
            config.setEnableCloaksPlus(!config.getEnableCloaksPlus());
            config.save();
            button.setMessage(CapeType.CLOAKSPLUS.getToggleText(config.getEnableCloaksPlus()));
        }).pos(this.width / 2 - 155, this.height / 7 + 72).size(150, 20).build());

        this.addRenderableWidget(Button.builder(this.elytraMessage(config.getEnableElytraTexture()), button -> {
            config.setEnableElytraTexture(!config.getEnableElytraTexture());
            config.save();
            button.setMessage(this.elytraMessage(config.getEnableElytraTexture()));
        }).pos(this.width / 2 - 100, this.height / 7 + 96).size(200, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(this.lastScreen);
            }
        }).pos(this.width / 2 - 100, this.height / 7 + 120).size(200, 20).build());
    }

    private MutableComponent elytraMessage(boolean enabled) {
        return CommonComponents.optionStatus(Component.translatable("options.capes.elytra"), enabled);
    }
}
