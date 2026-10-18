package com.thelads.core.modules;

import com.google.gson.JsonElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DoubleOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;

public class KeystrokesModule extends HudModule {
    public static final String NAME = "Keystrokes";
    public static final int STYLE_CLEAN = 0;
    public static final int STYLE_CLASSIC = 1;

    public final DropdownOption style = addOption(new DropdownOption("Style", 0, "Clean", "Classic"));
    public final BoolOption showCps = addOption(new BoolOption("Show CPS", true));
    public final BoolOption showSpace = addOption(new BoolOption("Show space bar", true));
    public final BoolOption showSneakSprintRow = addOption(new BoolOption("Show Sneak/Sprint Row", true));
    public final BoolOption pressAnimation = addOption(new BoolOption("Press Animation", true));
    public final BoolOption tickSyncedKeyPresses = addOption(new BoolOption("Tick Synced Key Presses", false));
    public final DropdownOption colorPreset = addOption(new DropdownOption("Color Preset", 0,
            "Classic", "Inverted", "Clean", "Beehive", "Mint & Cream", "Cyber Grape", "Vibrant Mango", "Dirt", "Retro", "Custom") {
        @Override
        public void load(JsonElement element) {
            super.load(element);
            lastPresetIndex = getIndex();
        }
        @Override
        public void reset() {
            super.reset();
            lastPresetIndex = getIndex();
        }
    });
    public final ColorOption keyColor = addOption(new ColorOption("Key Text Color", false, 0xFFFFFFFF));
    public final ColorOption keyPressedColor = addOption(new ColorOption("Pressed Key Text Color", false, 0xFF000000));
    public final ColorOption keyBackgroundColor = addOption(new ColorOption("Key Background Color", false, 0xAA000000));
    public final ColorOption keyPressedBackgroundColor = addOption(new ColorOption("Pressed Key Background Color", false, 0xAAFFFFFF));
    public final BoolOption keyTextShadow = addOption(new BoolOption("Key Text Shadow", false));
    public final BoolOption keyPressedTextShadow = addOption(new BoolOption("Pressed Key Text Shadow", false));
    public final BoolOption useCustomTextShadowColor = addOption(new BoolOption("Custom Shadow Colors", false));
    public final ColorOption keyTextShadowColor = addOption(new ColorOption("Key Text Shadow Color", false, 0xFF000000));
    public final ColorOption keyPressedTextShadowColor = addOption(new ColorOption("Pressed Key Text Shadow Color", false, 0xFF000000));
    public final BoolOption rainbowKeyNormal = addOption(new BoolOption("Rainbow Key Text", false));
    public final BoolOption rainbowKeyPressed = addOption(new BoolOption("Rainbow Pressed Key Text", false));
    public final BoolOption rainbowBackgroundNormal = addOption(new BoolOption("Rainbow Background", false));
    public final BoolOption rainbowBackgroundPressed = addOption(new BoolOption("Rainbow Pressed Background", false));
    public final BoolOption rainbowKeyTextShadow = addOption(new BoolOption("Rainbow Key Text Shadow", false));
    public final BoolOption rainbowKeyPressedTextShadow = addOption(new BoolOption("Rainbow Pressed Key Text Shadow", false));
    public final DoubleOption rainbowSpeed = addOption(new DoubleOption("Rainbow Speed", 1.0, 0.1, 20.0));

    // Classic options
    public final SliderOption size = addOption(new SliderOption("Size", 100, 50, 200, 25));
    public final ColorOption background = addOption(new ColorOption("Background", true, 0x80000000));
    public final DropdownOption colorMode = addOption(new DropdownOption("Color mode", 0, "Static", "Chroma", "Chroma Fast", "Fade"));

    private int lastPresetIndex = 0;

    public KeystrokesModule() {
        super(NAME, "Animated WASD + mouse key display with CPS.");
    }

    public boolean isClean() {
        return style.getIndex() == STYLE_CLEAN;
    }

    public boolean hidden(Option option) {
        if (style.getIndex() == STYLE_CLASSIC) {
            return option == showSneakSprintRow
                    || option == pressAnimation
                    || option == tickSyncedKeyPresses
                    || option == colorPreset
                    || option == keyColor
                    || option == keyPressedColor
                    || option == keyBackgroundColor
                    || option == keyPressedBackgroundColor
                    || option == keyTextShadow
                    || option == keyPressedTextShadow
                    || option == useCustomTextShadowColor
                    || option == keyTextShadowColor
                    || option == keyPressedTextShadowColor
                    || option == rainbowKeyNormal
                    || option == rainbowKeyPressed
                    || option == rainbowBackgroundNormal
                    || option == rainbowBackgroundPressed
                    || option == rainbowKeyTextShadow
                    || option == rainbowKeyPressedTextShadow
                    || option == rainbowSpeed;
        }

        // Clean mode: hide classic-only background/color mode and HudModule's Text Color
        if (option == background || option == colorMode || "Text Color".equals(option.getName())) {
            return true;
        }

        boolean anyShadow = keyTextShadow.get() || keyPressedTextShadow.get();
        if (!anyShadow) {
            if (option == useCustomTextShadowColor || option == keyTextShadowColor || option == keyPressedTextShadowColor) {
                return true;
            }
        } else if (!useCustomTextShadowColor.get()) {
            if (option == keyTextShadowColor || option == keyPressedTextShadowColor) {
                return true;
            }
        }

        if (!keyTextShadow.get() && option == rainbowKeyTextShadow) {
            return true;
        }
        if (!keyPressedTextShadow.get() && option == rainbowKeyPressedTextShadow) {
            return true;
        }

        return false;
    }

    @Override
    public void touch() {
        super.touch();
        int current = colorPreset.getIndex();
        if (current != lastPresetIndex) {
            lastPresetIndex = current;
            if (current < 9) {
                applyPreset(current);
            }
        } else if (current < 9 && !matchesPreset(current)) {
            colorPreset.setIndex(9);
            lastPresetIndex = 9;
        }
    }

    public void applyPreset(int preset) {
        switch (preset) {
            case 0 -> { // Classic
                keyColor.setColor(0xFFFFFFFF);
                keyBackgroundColor.setColor(0xAA000000);
                keyPressedColor.setColor(0xFF000000);
                keyPressedBackgroundColor.setColor(0xAAFFFFFF);
                keyTextShadow.set(false);
                keyPressedTextShadow.set(false);
                useCustomTextShadowColor.set(false);
                keyTextShadowColor.setColor(0xFF000000);
                keyPressedTextShadowColor.setColor(0xFF000000);
            }
            case 1 -> { // Inverted
                keyColor.setColor(0xFF000000);
                keyBackgroundColor.setColor(0xAAFFFFFF);
                keyPressedColor.setColor(0xFFFFFFFF);
                keyPressedBackgroundColor.setColor(0xAA000000);
                keyTextShadow.set(false);
                keyPressedTextShadow.set(false);
                useCustomTextShadowColor.set(false);
                keyTextShadowColor.setColor(0xFF000000);
                keyPressedTextShadowColor.setColor(0xFF000000);
            }
            case 2 -> { // Clean
                keyColor.setColor(0xFFFFFFFF);
                keyBackgroundColor.setColor(0x00FFFFFF);
                keyPressedColor.setColor(0xFF000000);
                keyPressedBackgroundColor.setColor(0x00FFFFFF);
                keyTextShadow.set(true);
                keyPressedTextShadow.set(true);
                useCustomTextShadowColor.set(true);
                keyTextShadowColor.setColor(0xFF000000);
                keyPressedTextShadowColor.setColor(0xFFFFFFFF);
            }
            case 3 -> { // Beehive
                keyColor.setColor(0xFFFFD000);
                keyBackgroundColor.setColor(0xFF000000);
                keyPressedColor.setColor(0xFF000000);
                keyPressedBackgroundColor.setColor(0xFFFFD000);
                keyTextShadow.set(false);
                keyPressedTextShadow.set(false);
                useCustomTextShadowColor.set(false);
                keyTextShadowColor.setColor(0xFF000000);
                keyPressedTextShadowColor.setColor(0xFF000000);
            }
            case 4 -> { // Mint & Cream
                keyColor.setColor(0xFF00A896);
                keyBackgroundColor.setColor(0xFFF0F3BD);
                keyPressedColor.setColor(0xFF05668D);
                keyPressedBackgroundColor.setColor(0xFF02C39A);
                keyTextShadow.set(false);
                keyPressedTextShadow.set(false);
                useCustomTextShadowColor.set(false);
                keyTextShadowColor.setColor(0xFF000000);
                keyPressedTextShadowColor.setColor(0xFF000000);
            }
            case 5 -> { // Cyber Grape
                keyColor.setColor(0xFFC7EDE4);
                keyBackgroundColor.setColor(0xFF820B8A);
                keyPressedColor.setColor(0xFF672A4E);
                keyPressedBackgroundColor.setColor(0xFFAF9AB2);
                keyTextShadow.set(false);
                keyPressedTextShadow.set(false);
                useCustomTextShadowColor.set(false);
                keyTextShadowColor.setColor(0xFF000000);
                keyPressedTextShadowColor.setColor(0xFF000000);
            }
            case 6 -> { // Vibrant Mango
                keyColor.setColor(0xFF4200FF);
                keyBackgroundColor.setColor(0xFFFFBC42);
                keyPressedColor.setColor(0xFF000000);
                keyPressedBackgroundColor.setColor(0xFFD81159);
                keyTextShadow.set(false);
                keyPressedTextShadow.set(false);
                useCustomTextShadowColor.set(false);
                keyTextShadowColor.setColor(0xFF000000);
                keyPressedTextShadowColor.setColor(0xFF000000);
            }
            case 7 -> { // Dirt
                keyColor.setColor(0xFF753A38);
                keyBackgroundColor.setColor(0xFFA1BA5A);
                keyPressedColor.setColor(0xFFC4DF96);
                keyPressedBackgroundColor.setColor(0xFF633332);
                keyTextShadow.set(false);
                keyPressedTextShadow.set(false);
                useCustomTextShadowColor.set(false);
                keyTextShadowColor.setColor(0xFF000000);
                keyPressedTextShadowColor.setColor(0xFF000000);
            }
            case 8 -> { // Retro
                keyColor.setColor(0xFFA71D31);
                keyBackgroundColor.setColor(0xFFF1F0CC);
                keyPressedColor.setColor(0xFF3F0D12);
                keyPressedBackgroundColor.setColor(0xFFD5BF86);
                keyTextShadow.set(false);
                keyPressedTextShadow.set(false);
                useCustomTextShadowColor.set(false);
                keyTextShadowColor.setColor(0xFF000000);
                keyPressedTextShadowColor.setColor(0xFF000000);
            }
        }
        if (preset < 9) {
            rainbowKeyNormal.set(false);
            rainbowKeyPressed.set(false);
            rainbowBackgroundNormal.set(false);
            rainbowBackgroundPressed.set(false);
            rainbowKeyTextShadow.set(false);
            rainbowKeyPressedTextShadow.set(false);
        }
    }

    private boolean rainbowOff() {
        return !rainbowKeyNormal.get()
                && !rainbowKeyPressed.get()
                && !rainbowBackgroundNormal.get()
                && !rainbowBackgroundPressed.get()
                && !rainbowKeyTextShadow.get()
                && !rainbowKeyPressedTextShadow.get();
    }

    private boolean standardShadowsOff() {
        return !keyTextShadow.get()
                && !keyPressedTextShadow.get()
                && !useCustomTextShadowColor.get()
                && keyTextShadowColor.getColor() == 0xFF000000
                && keyPressedTextShadowColor.getColor() == 0xFF000000;
    }

    private boolean cleanShadowsMatch() {
        return keyTextShadow.get()
                && keyPressedTextShadow.get()
                && useCustomTextShadowColor.get()
                && keyTextShadowColor.getColor() == 0xFF000000
                && keyPressedTextShadowColor.getColor() == 0xFFFFFFFF;
    }

    private boolean matchesPreset(int preset) {
        if (!rainbowOff()) return false;
        return switch (preset) {
            case 0 -> standardShadowsOff()
                    && keyColor.getColor() == 0xFFFFFFFF && keyBackgroundColor.getColor() == 0xAA000000
                    && keyPressedColor.getColor() == 0xFF000000 && keyPressedBackgroundColor.getColor() == 0xAAFFFFFF;
            case 1 -> standardShadowsOff()
                    && keyColor.getColor() == 0xFF000000 && keyBackgroundColor.getColor() == 0xAAFFFFFF
                    && keyPressedColor.getColor() == 0xFFFFFFFF && keyPressedBackgroundColor.getColor() == 0xAA000000;
            case 2 -> cleanShadowsMatch()
                    && keyColor.getColor() == 0xFFFFFFFF && keyBackgroundColor.getColor() == 0x00FFFFFF
                    && keyPressedColor.getColor() == 0xFF000000 && keyPressedBackgroundColor.getColor() == 0x00FFFFFF;
            case 3 -> standardShadowsOff()
                    && keyColor.getColor() == 0xFFFFD000 && keyBackgroundColor.getColor() == 0xFF000000
                    && keyPressedColor.getColor() == 0xFF000000 && keyPressedBackgroundColor.getColor() == 0xFFFFD000;
            case 4 -> standardShadowsOff()
                    && keyColor.getColor() == 0xFF00A896 && keyBackgroundColor.getColor() == 0xFFF0F3BD
                    && keyPressedColor.getColor() == 0xFF05668D && keyPressedBackgroundColor.getColor() == 0xFF02C39A;
            case 5 -> standardShadowsOff()
                    && keyColor.getColor() == 0xFFC7EDE4 && keyBackgroundColor.getColor() == 0xFF820B8A
                    && keyPressedColor.getColor() == 0xFF672A4E && keyPressedBackgroundColor.getColor() == 0xFFAF9AB2;
            case 6 -> standardShadowsOff()
                    && keyColor.getColor() == 0xFF4200FF && keyBackgroundColor.getColor() == 0xFFFFBC42
                    && keyPressedColor.getColor() == 0xFF000000 && keyPressedBackgroundColor.getColor() == 0xFFD81159;
            case 7 -> standardShadowsOff()
                    && keyColor.getColor() == 0xFF753A38 && keyBackgroundColor.getColor() == 0xFFA1BA5A
                    && keyPressedColor.getColor() == 0xFFC4DF96 && keyPressedBackgroundColor.getColor() == 0xFF633332;
            case 8 -> standardShadowsOff()
                    && keyColor.getColor() == 0xFFA71D31 && keyBackgroundColor.getColor() == 0xFFF1F0CC
                    && keyPressedColor.getColor() == 0xFF3F0D12 && keyPressedBackgroundColor.getColor() == 0xFFD5BF86;
            default -> false;
        };
    }
}
