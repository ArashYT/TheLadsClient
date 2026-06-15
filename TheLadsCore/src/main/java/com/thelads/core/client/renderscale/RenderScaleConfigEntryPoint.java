package com.thelads.core.client.renderscale;

import java.util.Set;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.option.Range;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.PageBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class RenderScaleConfigEntryPoint
implements ConfigEntryPoint {
    public void registerConfigEarly(ConfigBuilder builder) {
    }

    public void registerConfigLate(ConfigBuilder builder) {
        builder.registerOwnModOptions()
            .setName("Render Scale")
            .setColorTheme(builder.createColorTheme().setFullThemeRGB(53503, 3398399, 41159))
            .setNonTintedIcon(Identifier.fromNamespaceAndPath("render-scale", "textures/gui/icon.png"))
            .addPage((PageBuilder) this.buildPage(builder));
    }

    private OptionPageBuilder buildPage(ConfigBuilder builder) {
        OptionPageBuilder page = builder.createOptionPage().setName(Component.literal("Render Scale"));
        Identifier idPreset = Identifier.fromNamespaceAndPath("render-scale", "preset");
        Identifier idScalePercentage = Identifier.fromNamespaceAndPath("render-scale", "scale_percentage");
        Identifier idScaleAlgorithm = Identifier.fromNamespaceAndPath("render-scale", "scale_algorithm");
        Identifier idDynamicResolution = Identifier.fromNamespaceAndPath("render-scale", "dynamic_resolution");
        Identifier idTargetFps = Identifier.fromNamespaceAndPath("render-scale", "target_fps");
        Identifier idMinRenderScale = Identifier.fromNamespaceAndPath("render-scale", "min_render_scale");

        OptionGroupBuilder group = builder.createOptionGroup();

        // Preset Profile Option
        group.addOption((OptionBuilder) builder.createEnumOption(idPreset, RenderScalePreset.class)
            .setStorageHandler(RenderScaleOptions::save)
            .setName(Component.literal("Preset Profile"))
            .setTooltip(Component.literal("Select a preset profile to configure quality and performance instantly, or Custom for manual adjustment."))
            .setDefaultValue(RenderScalePreset.CUSTOM)
            .setElementNameProvider(element -> Component.literal(element.toString()))
            .setImpact(OptionImpact.MEDIUM)
            .setBinding(RenderScaleOptions::setPreset, RenderScaleOptions::getPreset));

        // Render Scale Option
        group.addOption((OptionBuilder) builder.createIntegerOption(idScalePercentage)
            .setStorageHandler(RenderScaleOptions::save)
            .setName(Component.literal("Render Scale"))
            .setTooltip(Component.literal("Adjusts the internal 3D rendering resolution of the game. Lowering it raises FPS significantly. Higher values provide super-sampling anti-aliasing."))
            .setDefaultValue(100)
            .setValueFormatter(value -> Component.literal(value + "%"))
            .setImpact(OptionImpact.HIGH)
            .setBinding(value -> RenderScaleOptions.setRenderScale((float) value.intValue() / 100.0f), () -> Math.round(RenderScaleOptions.getRenderScale() * 100.0f))
            .setEnabledProvider(state -> state.readEnumOption(idPreset, RenderScalePreset.class) == RenderScalePreset.CUSTOM, new Identifier[]{idPreset})
            .setControlHiddenWhenDisabled(false)
            .setRangeProvider(state -> {
                RenderScalePreset preset = (RenderScalePreset) state.readEnumOption(idPreset, RenderScalePreset.class);
                if (preset == RenderScalePreset.ULTRA_PERFORMANCE) {
                    return new Range(50, 50, 5);
                }
                if (preset == RenderScalePreset.BALANCED) {
                    return new Range(75, 75, 5);
                }
                if (preset == RenderScalePreset.QUALITY) {
                    return new Range(100, 100, 5);
                }
                if (preset == RenderScalePreset.SUPER_SAMPLING) {
                    return new Range(150, 150, 5);
                }
                return new Range(25, 200, 5);
            }, new Identifier[]{idPreset})
            .setDefaultProvider(state -> {
                RenderScalePreset preset = (RenderScalePreset) state.readEnumOption(idPreset, RenderScalePreset.class);
                if (preset == RenderScalePreset.ULTRA_PERFORMANCE) {
                    return 50;
                }
                if (preset == RenderScalePreset.BALANCED) {
                    return 75;
                }
                if (preset == RenderScalePreset.QUALITY) {
                    return 100;
                }
                if (preset == RenderScalePreset.SUPER_SAMPLING) {
                    return 150;
                }
                return 100;
            }, new Identifier[]{idPreset}));

        // Scaling Filter Option
        group.addOption((OptionBuilder) builder.createEnumOption(idScaleAlgorithm, ScaleAlgorithm.class)
            .setStorageHandler(RenderScaleOptions::save)
            .setName(Component.literal("Scaling Filter"))
            .setTooltip(Component.literal("The filter used when upscaling/downscaling the 3D world to the screen. Linear is smoother; Nearest is sharper/pixelated."))
            .setDefaultValue(ScaleAlgorithm.LINEAR)
            .setElementNameProvider(element -> Component.literal(element.toString()))
            .setImpact(OptionImpact.LOW)
            .setBinding(RenderScaleOptions::setScaleAlgorithm, RenderScaleOptions::getScaleAlgorithm)
            .setEnabledProvider(state -> state.readEnumOption(idPreset, RenderScalePreset.class) == RenderScalePreset.CUSTOM, new Identifier[]{idPreset})
            .setControlHiddenWhenDisabled(false)
            .setAllowedValuesProvider(state -> {
                RenderScalePreset preset = (RenderScalePreset) state.readEnumOption(idPreset, RenderScalePreset.class);
                if (preset == RenderScalePreset.ULTRA_PERFORMANCE) {
                    return Set.of(ScaleAlgorithm.NEAREST);
                }
                if (preset != RenderScalePreset.CUSTOM) {
                    return Set.of(ScaleAlgorithm.LINEAR);
                }
                return Set.of(ScaleAlgorithm.LINEAR, ScaleAlgorithm.NEAREST);
            }, new Identifier[]{idPreset})
            .setDefaultProvider(state -> {
                RenderScalePreset preset = (RenderScalePreset) state.readEnumOption(idPreset, RenderScalePreset.class);
                if (preset == RenderScalePreset.ULTRA_PERFORMANCE) {
                    return ScaleAlgorithm.NEAREST;
                }
                return ScaleAlgorithm.LINEAR;
            }, new Identifier[]{idPreset}));

        // Dynamic Resolution Option
        group.addOption((OptionBuilder) builder.createBooleanOption(idDynamicResolution)
            .setStorageHandler(RenderScaleOptions::save)
            .setName(Component.literal("Dynamic Resolution"))
            .setTooltip(Component.literal("Dynamically scales the 3D rendering resolution during gameplay to maintain a target frame rate (e.g. 60 FPS)."))
            .setDefaultValue(false)
            .setImpact(OptionImpact.MEDIUM)
            .setBinding(RenderScaleOptions::setDynamicResolution, RenderScaleOptions::isDynamicResolution)
            .setEnabledProvider(state -> state.readEnumOption(idPreset, RenderScalePreset.class) == RenderScalePreset.CUSTOM, new Identifier[]{idPreset})
            .setControlHiddenWhenDisabled(false));

        // Target FPS Option
        group.addOption((OptionBuilder) builder.createIntegerOption(idTargetFps)
            .setStorageHandler(RenderScaleOptions::save)
            .setName(Component.literal("Target FPS"))
            .setTooltip(Component.literal("The target frame rate to maintain. Resolution will adjust dynamically to keep performance around this target."))
            .setRange(30, 240, 5)
            .setDefaultValue(60)
            .setValueFormatter(value -> Component.literal(value + " FPS"))
            .setImpact(OptionImpact.LOW)
            .setBinding(RenderScaleOptions::setTargetFps, RenderScaleOptions::getTargetFps)
            .setEnabledProvider(state -> state.readEnumOption(idPreset, RenderScalePreset.class) == RenderScalePreset.CUSTOM && state.readBooleanOption(idDynamicResolution), new Identifier[]{idPreset, idDynamicResolution})
            .setControlHiddenWhenDisabled(false));

        // Min Render Scale Option
        group.addOption((OptionBuilder) builder.createIntegerOption(idMinRenderScale)
            .setStorageHandler(RenderScaleOptions::save)
            .setName(Component.literal("Min Render Scale"))
            .setTooltip(Component.literal("The lowest allowed render scale when scaling down dynamically. Prevents the image from becoming too blurry."))
            .setRange(25, 100, 5)
            .setDefaultValue(50)
            .setValueFormatter(value -> Component.literal(value + "%"))
            .setImpact(OptionImpact.LOW)
            .setBinding(value -> RenderScaleOptions.setMinRenderScale((float) value.intValue() / 100.0f), () -> Math.round(RenderScaleOptions.getMinRenderScale() * 100.0f))
            .setEnabledProvider(state -> state.readEnumOption(idPreset, RenderScalePreset.class) == RenderScalePreset.CUSTOM && state.readBooleanOption(idDynamicResolution), new Identifier[]{idPreset, idDynamicResolution})
            .setControlHiddenWhenDisabled(false));

        page.addOptionGroup(group);
        return page;
    }
}
