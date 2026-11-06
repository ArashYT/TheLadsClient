package com.thelads.core.v1_21_11.feature.screenshots.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets = "io.github.lgatodu47.screenshot_viewer.config.ScreenshotViewerOptions", remap = false)
public abstract class ScreenshotViewerOptionsMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @ModifyArgs(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Lio/github/lgatodu47/catconfig/ConfigOptionBuilder;createEnum(Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Enum;)Lio/github/lgatodu47/catconfig/ConfigOption;",
            remap = false
        ),
        remap = false
    )
    private static void ladsDefaultNewestFirst(Args args) {
        if ("default_list_order".equals(args.get(0))) {
            Class<? extends Enum> enumType = args.get(1);
            args.set(2, Enum.valueOf(enumType, "DESCENDING"));
        }
    }
}
