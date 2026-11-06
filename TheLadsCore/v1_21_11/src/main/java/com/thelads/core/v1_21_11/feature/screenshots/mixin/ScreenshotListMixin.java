package com.thelads.core.v1_21_11.feature.screenshots.mixin;

import com.thelads.core.shared.ScreenshotSources;
import java.io.File;
import java.util.Comparator;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "io.github.lgatodu47.screenshot_viewer.screen.manage_screenshots.ScreenshotList", remap = false)
public abstract class ScreenshotListMixin {
    @Shadow(remap = false)
    private boolean invertedOrder;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @ModifyArg(
        method = {"<init>", "onConfigUpdate"},
        at = @At(
            value = "INVOKE",
            target = "Lio/github/lgatodu47/catconfig/CatConfig;getOrFallback(Lio/github/lgatodu47/catconfig/ConfigOption;Ljava/lang/Object;)Ljava/lang/Object;",
            remap = false
        ),
        index = 1,
        remap = false
    )
    private Object ladsFallbackNewestFirst(Object fallback) {
        if (fallback instanceof Enum<?> e
            && "ASCENDING".equals(e.name())
            && e.getClass().getName().endsWith(".ScreenshotListOrder")) {
            return Enum.valueOf((Class<? extends Enum>) e.getClass(), "DESCENDING");
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    @Redirect(
        method = "init()V",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;sort(Ljava/util/Comparator;)V",
            remap = false
        ),
        remap = false
    )
    private void ladsSortByModifiedTime(List<?> rawFiles, Comparator<?> ignored) {
        List<File> target = (List<File>) rawFiles;
        List<File> sorted = ScreenshotSources.sortByModifiedTime(target, this.invertedOrder);
        for (int i = 0; i < sorted.size(); i++) {
            target.set(i, sorted.get(i));
        }
    }
}
