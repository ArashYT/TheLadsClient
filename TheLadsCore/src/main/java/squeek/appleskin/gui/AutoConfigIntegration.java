/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  me.shedaniel.autoconfig.AutoConfig
 *  me.shedaniel.autoconfig.ConfigData
 *  me.shedaniel.autoconfig.ConfigHolder
 *  me.shedaniel.autoconfig.annotation.Config
 *  me.shedaniel.autoconfig.annotation.ConfigEntry$Gui$Excluded
 *  me.shedaniel.autoconfig.annotation.ConfigEntry$Gui$Tooltip
 *  me.shedaniel.autoconfig.serializer.DummyConfigSerializer
 *  net.minecraft.world.InteractionResult
 */
package squeek.appleskin.gui;

import java.io.IOException;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.DummyConfigSerializer;
import net.minecraft.world.InteractionResult;
import squeek.appleskin.ModConfig;

@Config(name="appleskin")
public class AutoConfigIntegration
implements ConfigData {
    @ConfigEntry.Gui.Excluded
    private static final ModConfig DEFAULTS = new ModConfig();
    @ConfigEntry.Gui.Tooltip
    public boolean showFoodValuesInTooltip;
    @ConfigEntry.Gui.Tooltip
    public boolean showFoodValuesInTooltipAlways;
    @ConfigEntry.Gui.Tooltip
    public boolean showSaturationHudOverlay;
    @ConfigEntry.Gui.Tooltip
    public boolean showFoodValuesHudOverlay;
    @ConfigEntry.Gui.Tooltip
    public boolean showFoodValuesHudOverlayWhenOffhand;
    @ConfigEntry.Gui.Tooltip
    public boolean showFoodExhaustionHudUnderlay;
    @ConfigEntry.Gui.Tooltip
    public boolean showFoodHealthHudOverlay;
    @ConfigEntry.Gui.Tooltip
    public boolean showVanillaAnimationsOverlay;
    @ConfigEntry.Gui.Tooltip
    public float maxHudOverlayFlashAlpha;

    public AutoConfigIntegration() {
        this.showFoodValuesInTooltip = AutoConfigIntegration.DEFAULTS.showFoodValuesInTooltip;
        this.showFoodValuesInTooltipAlways = AutoConfigIntegration.DEFAULTS.showFoodValuesInTooltipAlways;
        this.showSaturationHudOverlay = AutoConfigIntegration.DEFAULTS.showSaturationHudOverlay;
        this.showFoodValuesHudOverlay = AutoConfigIntegration.DEFAULTS.showFoodValuesHudOverlay;
        this.showFoodValuesHudOverlayWhenOffhand = AutoConfigIntegration.DEFAULTS.showFoodValuesHudOverlayWhenOffhand;
        this.showFoodExhaustionHudUnderlay = AutoConfigIntegration.DEFAULTS.showFoodExhaustionHudUnderlay;
        this.showFoodHealthHudOverlay = AutoConfigIntegration.DEFAULTS.showFoodHealthHudOverlay;
        this.showVanillaAnimationsOverlay = AutoConfigIntegration.DEFAULTS.showVanillaAnimationsOverlay;
        this.maxHudOverlayFlashAlpha = AutoConfigIntegration.DEFAULTS.maxHudOverlayFlashAlpha;
    }

    public static void init() {
        ConfigHolder<AutoConfigIntegration> holder = AutoConfig.register(AutoConfigIntegration.class, DummyConfigSerializer::new);
        holder.registerSaveListener((manager, rawData) -> {
            AutoConfigIntegration data = (AutoConfigIntegration) rawData;
            ModConfig.INSTANCE.maxHudOverlayFlashAlpha = data.maxHudOverlayFlashAlpha;
            ModConfig.INSTANCE.showFoodHealthHudOverlay = data.showFoodHealthHudOverlay;
            ModConfig.INSTANCE.showFoodExhaustionHudUnderlay = data.showFoodExhaustionHudUnderlay;
            ModConfig.INSTANCE.showFoodValuesInTooltip = data.showFoodValuesInTooltip;
            ModConfig.INSTANCE.showFoodValuesHudOverlay = data.showFoodValuesHudOverlay;
            ModConfig.INSTANCE.showFoodValuesInTooltipAlways = data.showFoodValuesInTooltipAlways;
            ModConfig.INSTANCE.showSaturationHudOverlay = data.showSaturationHudOverlay;
            ModConfig.INSTANCE.showVanillaAnimationsOverlay = data.showVanillaAnimationsOverlay;
            ModConfig.INSTANCE.showFoodValuesHudOverlayWhenOffhand = data.showFoodValuesHudOverlayWhenOffhand;
            try {
                ModConfig.INSTANCE.save();
                return InteractionResult.SUCCESS;
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        holder.registerLoadListener((manager, rawData) -> {
            AutoConfigIntegration data = (AutoConfigIntegration) rawData;
            data.maxHudOverlayFlashAlpha = ModConfig.INSTANCE.maxHudOverlayFlashAlpha;
            data.showFoodHealthHudOverlay = ModConfig.INSTANCE.showFoodHealthHudOverlay;
            data.showFoodExhaustionHudUnderlay = ModConfig.INSTANCE.showFoodExhaustionHudUnderlay;
            data.showFoodValuesInTooltip = ModConfig.INSTANCE.showFoodValuesInTooltip;
            data.showFoodValuesHudOverlay = ModConfig.INSTANCE.showFoodValuesHudOverlay;
            data.showFoodValuesInTooltipAlways = ModConfig.INSTANCE.showFoodValuesInTooltipAlways;
            data.showSaturationHudOverlay = ModConfig.INSTANCE.showSaturationHudOverlay;
            data.showVanillaAnimationsOverlay = ModConfig.INSTANCE.showVanillaAnimationsOverlay;
            data.showFoodValuesHudOverlayWhenOffhand = ModConfig.INSTANCE.showFoodValuesHudOverlayWhenOffhand;
            return InteractionResult.SUCCESS;
        });
        holder.load();
    }
}

