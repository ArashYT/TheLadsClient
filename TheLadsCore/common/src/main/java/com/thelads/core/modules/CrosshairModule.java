package com.thelads.core.modules;

import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DoubleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.ActionOption;

public class CrosshairModule extends Module {
    public final ActionOption drawingEditor = new ActionOption("Drawing Editor", "Edit crosshair drawing");
    public final ColorOption color = new ColorOption("Color", false, 0xFFFFFFFF);
    public final DoubleOption scale = new DoubleOption("Scale", 1.0, 0.1, 5.0);
    public final DoubleOption thickness = new DoubleOption("Thickness", 1.0, 0.1, 10.0);

    public CrosshairModule() {
        super("Crosshair Tweaks", "Design your crosshair, visibility, aiming feedback and item indicators.");
        addOption(color);
        addOption(scale);
        addOption(thickness);
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
        addOption(new SliderOption("Opacity First Person", 100, 0, 100, 1));
        addOption(new SliderOption("Opacity Third Person", 100, 0, 100, 1));
        addOption(new BoolOption("Disable Attack Indicator", false));
        addOption(new BoolOption("Remove Attack Blend First Person", false));
        addOption(new BoolOption("Remove Attack Blend Third Person", false));
        addOption(new SliderOption("Attack Opacity First Person", 100, 0, 100, 1));
        addOption(new SliderOption("Attack Opacity Third Person", 100, 0, 100, 1));
        addOption(new BoolOption("Debug Attack Indicator", false));
        addOption(drawingEditor);
    }
}
