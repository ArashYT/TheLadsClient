package com.thelads.core.modules;

import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DoubleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.Option;
import com.thelads.core.config.TextOption;
import com.thelads.core.client.CrosshairBackground;

public class CrosshairModule extends Module {
    public final ActionOption drawingEditor = new ActionOption("Drawing Editor", "Edit crosshair drawing");
    public final ColorOption color = new ColorOption("Color", false, 0xFFFFFFFF);
    public final DoubleOption scale = new DoubleOption("Scale", 1.0, 0.1, 5.0);
    public final DoubleOption thickness = new DoubleOption("Thickness", 1.0, 0.1, 10.0);
    // What the previews (this page and the drawing editor) show behind the crosshair: CrosshairBackground.
    public final DropdownOption background = new DropdownOption("Background", CrosshairBackground.BIOME, CrosshairBackground.MODES);
    public final DropdownOption biome = new DropdownOption("Background Biome", CrosshairBackground.DEFAULT_BIOME, CrosshairBackground.biomeNames());
    public final DropdownOption time = new DropdownOption("Time of Day", CrosshairBackground.DEFAULT_TIME, CrosshairBackground.TIMES);
    public final ColorOption gradientStart = new ColorOption("Gradient Start", false, 0xff5aa0e6);
    public final ColorOption gradientEnd = new ColorOption("Gradient End", false, 0xff1a1f2e);
    public final DropdownOption gradientDirection = new DropdownOption("Gradient Direction", 0, CrosshairBackground.DIRECTIONS);
    public final ColorOption solidColor = new ColorOption("Background Color", false, 0xff3c5a78);
    /** The chosen picture's copy in the shared backgrounds folder ("" none); set through {@link #chooseImage}, never shown. */
    public final TextOption image = new TextOption("Background Image", "");
    public final ActionOption chooseImage = new ActionOption("Your Image", "Choose image...");

    public CrosshairModule() {
        super("Crosshair Tweaks", "Design your crosshair, visibility, aiming feedback and item indicators.");
        addOption(color);
        addOption(scale);
        addOption(thickness);
        // The preview backgrounds first: they change what the preview beside this list shows.
        addOption(background);
        addOption(biome);
        addOption(time);
        addOption(gradientStart);
        addOption(gradientEnd);
        addOption(gradientDirection);
        addOption(solidColor);
        addOption(chooseImage);
        addOption(image);
        chooseImage.setAction(CrosshairBackground::choose);
        chooseImage.setLabel(() -> CrosshairBackground.chooseLabel(this));
        // Existing saved shape indices remain stable.
        addOption(new DropdownOption("Shape", 0, "Cross", "Dot", "Square", "Vanilla", "Circle", "Triangle", "Arrow", "Debug", "Drawn"));
        addOption(new SliderOption("Gap", 2, 0, 12, 1));
        addOption(new BoolOption("Outline", true));
        addOption(new BoolOption("Center Dot", false));
        addOption(new SliderOption("Width", 5, 1, 64, 1));
        addOption(new SliderOption("Height", 5, 1, 64, 1));
        addOption(new SliderOption("Rotation", 0, -180, 180, 1));
        addOption(new SliderOption("Offset X", 0, -500, 500, 1));
        addOption(new SliderOption("Offset Y", 0, -500, 500, 1));
        addOption(new ColorOption("Outline Color", false, 0xff000000));
        addOption(new ColorOption("Dot Color", false, 0xffffffff));
        addOption(new BoolOption("Adaptive Color", false));
        addOption(new BoolOption("Rainbow", false));
        addOption(new SliderOption("Rainbow Speed", 1, 0.1, 10, 0.1));
        addOption(new SliderOption("Rainbow Phase", 0, 0, 360, 1));
        addOption(new BoolOption("Dynamic Attack Gap", false));
        addOption(new BoolOption("Dynamic Bow Gap", false));
        addOption(new BoolOption("Highlight Hostiles", false));
        addOption(new BoolOption("Highlight Passives", false));
        addOption(new BoolOption("Highlight Players", false));
        addOption(new ColorOption("Hostile Color", false, 0xffff5555));
        addOption(new ColorOption("Passive Color", false, 0xff55ff55));
        addOption(new ColorOption("Player Color", false, 0xff55ffff));
        addOption(new BoolOption("Item Cooldown", false));
        addOption(new ColorOption("Cooldown Color", false, 0xff55ffff));
        addOption(new BoolOption("Tool Durability Indicator", false));
        addOption(new BoolOption("Projectile Indicator", false));
        addOption(new BoolOption("Visible Normally", true));
        addOption(new BoolOption("Visible with Hidden HUD", false));
        addOption(new BoolOption("Visible with Debug", true));
        addOption(new BoolOption("Keep Vanilla Debug", true));
        addOption(new BoolOption("Visible Holding Ranged Weapon", true));
        addOption(new BoolOption("Visible Holding Throwable", true));
        addOption(new BoolOption("Visible Using Spyglass", false));
        // These are the fourteen Crosshair Tweaks controls, without duplicate aliases.
        addOption(new BoolOption("Disable Crosshair", false));
        addOption(new BoolOption("Hide in Containers", false));
        addOption(new BoolOption("Show in Third Person", false));
        addOption(new BoolOption("Show in Spectator", false));
        addOption(new BoolOption("Remove Blend First Person", false));
        addOption(new BoolOption("Remove Blend Third Person", false));
        addOption(new SliderOption("Opacity First Person", 100, 0, 100, 1).percent());
        addOption(new SliderOption("Opacity Third Person", 100, 0, 100, 1).percent());
        addOption(new BoolOption("Disable Attack Indicator", false));
        addOption(new BoolOption("Remove Attack Blend First Person", false));
        addOption(new BoolOption("Remove Attack Blend Third Person", false));
        addOption(new SliderOption("Attack Opacity First Person", 100, 0, 100, 1).percent());
        addOption(new SliderOption("Attack Opacity Third Person", 100, 0, 100, 1).percent());
        addOption(new BoolOption("Debug Attack Indicator", false));
        addOption(drawingEditor);
    }

    /** The settings page shows only the background options of the chosen kind (Live Game: its biome stands in outside a world). */
    public boolean hidden(Option option) {
        int mode = background.getIndex();
        if (option == image) return true;
        if (option == biome) return mode != CrosshairBackground.BIOME && mode != CrosshairBackground.LIVE;
        if (option == time) return mode != CrosshairBackground.BIOME && mode != CrosshairBackground.LIVE || CrosshairBackground.timeless(biome.getIndex());
        if (option == gradientStart || option == gradientEnd || option == gradientDirection) return mode != CrosshairBackground.GRADIENT;
        if (option == solidColor) return mode != CrosshairBackground.SOLID;
        if (option == chooseImage) return mode != CrosshairBackground.IMAGE;
        return false;
    }
}
