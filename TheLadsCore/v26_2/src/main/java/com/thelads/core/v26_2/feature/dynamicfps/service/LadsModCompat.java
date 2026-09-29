// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.service;
import com.thelads.core.v26_2.feature.dynamicfps.DynamicFPSMod;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;
final class LadsModCompat implements ModCompat {
    private final Set<Object> flawlessFrames = ConcurrentHashMap.newKeySet();
    private final boolean disableOverlay;
    LadsModCompat() {
        disableOverlay = FabricLoader.getInstance().getAllMods().stream().anyMatch(mod -> {
            try {
                var value = mod.getMetadata().getCustomValue("dynamic_fps").getAsObject().get("optimized_overlay");
                return value != null && value.getType() == CustomValue.CvType.BOOLEAN && !value.getAsBoolean();
            } catch (ClassCastException | NullPointerException absent) { return false; }
        });
    }
    @SuppressWarnings("unchecked")
    void initialize() {
        Function<String, Consumer<Boolean>> provider = name -> {
            Object token = new Object();
            return active -> {
                if (active) flawlessFrames.add(token); else flawlessFrames.remove(token);
                DynamicFPSMod.onStatusChanged(false);
            };
        };
        FabricLoader.getInstance().getEntrypoints("frex_flawless_frames", Consumer.class).forEach(api -> api.accept(provider));
    }
    public boolean isDisabled() { return !flawlessFrames.isEmpty(); }
    public boolean disableOverlayOptimization() { return disableOverlay; }
}
