package com.thelads.core.mixin.alwayson.advancementsreloaded;

import com.thelads.core.features.alwayson.advancementsreloaded.screens.AdvancementReloadedScreen;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = PauseScreen.class, priority = 1001)
public abstract class PauseScreenAdvrMixin extends Screen {
    protected PauseScreenAdvrMixin(Component title) {
        super(title);
    }

    @ModifyArg(method = "createPauseMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/PauseScreen;openScreenButton(Lnet/minecraft/network/chat/Component;Ljava/util/function/Supplier;)Lnet/minecraft/client/gui/components/Button;"), index = 1)
    private Supplier<Screen> modifyAdvancementsButton(Supplier<Screen> original) {
        if (original.get() instanceof AdvancementsScreen && this.minecraft.player != null && this.minecraft.player.connection != null) {
            return () -> {
                if (!com.thelads.core.config.ModuleManager.getInstance().getModule("AdvancementsReloaded").isEnabled()) {
                    return original.get();
                }
                if (this.minecraft.player != null && this.minecraft.player.connection != null) {
                    return new AdvancementReloadedScreen(this.minecraft.player.connection.getAdvancements(), this);
                }
                return original.get();
            };
        }
        return original;
    }
}
