package com.thelads.core.v1_20_1.feature.fastquit.mixin;

import com.thelads.core.client.FastQuit;
import java.nio.file.Path;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The world list's info line reads "Saving..." while the world saves in the background (asked every frame). */
@Mixin(LevelSummary.class)
abstract class LevelSummaryFastQuitMixin {
    @Shadow public abstract Path getIcon();

    @Inject(method = "getInfo", at = @At("HEAD"), cancellable = true)
    private void lads$savingInfo(CallbackInfoReturnable<Component> cir) {
        Path icon = getIcon();
        if (icon != null && icon.getParent() != null && FastQuit.saving(icon.getParent()))
            cir.setReturnValue(Component.literal("Saving...").withStyle(ChatFormatting.YELLOW));
    }
}
