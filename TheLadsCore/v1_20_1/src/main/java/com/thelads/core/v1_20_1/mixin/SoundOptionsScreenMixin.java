package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.client.sound.ModuleVolumes;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Music &amp; Sounds: Kill Banners and Combo Sounds sliders under the game's own volumes, bound to each module's Volume (ModuleVolumes). */
@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsScreenMixin {
    @Shadow private OptionsList list;

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/OptionsList;addSmall([Lnet/minecraft/client/OptionInstance;)V",
        ordinal = 0, shift = At.Shift.AFTER))
    private void lads$moduleVolumes(CallbackInfo callback) {
        list.addSmall(new OptionInstance<?>[] {lads$slider(0), lads$slider(1)});
    }

    /** A volume slider as the game draws its own ("Kill Banners: 60%", "Off" at 0), read now from the module. */
    @Unique
    private static OptionInstance<Double> lads$slider(int slider) {
        return new OptionInstance<>(ModuleVolumes.LABELS[slider], OptionInstance.noTooltip(),
            (caption, value) -> ModuleVolumes.percent(value) == 0 ? Options.genericValueLabel(caption, CommonComponents.OPTION_OFF)
                : Component.translatable("options.percent_value", caption, ModuleVolumes.percent(value)),
            OptionInstance.UnitDouble.INSTANCE, ModuleVolumes.get(slider), value -> ModuleVolumes.set(slider, value));
    }
}
