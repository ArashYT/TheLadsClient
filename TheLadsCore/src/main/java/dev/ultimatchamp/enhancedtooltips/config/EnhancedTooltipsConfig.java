/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  blue.endless.jankson.Comment
 *  blue.endless.jankson.Jankson
 *  blue.endless.jankson.JsonObject
 *  blue.endless.jankson.JsonPrimitive
 *  blue.endless.jankson.api.SyntaxError
 *  dev.isxander.yacl3.api.NameableEnum
 *  dev.isxander.yacl3.platform.YACLPlatform
 *  net.minecraft.network.chat.Component
 */
package dev.ultimatchamp.enhancedtooltips.config;

import blue.endless.jankson.Comment;
import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import blue.endless.jankson.api.SyntaxError;
import dev.isxander.yacl3.api.NameableEnum;
import dev.isxander.yacl3.platform.YACLPlatform;
import dev.ultimatchamp.enhancedtooltips.EnhancedTooltips;
import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class EnhancedTooltipsConfig {
    public GeneralConfig general = new GeneralConfig();
    public PopUpAnimationConfig popUpAnimation = new PopUpAnimationConfig();
    public ItemPreviewAnimationConfig itemPreviewAnimation = new ItemPreviewAnimationConfig();
    public BorderConfig border = new BorderConfig();
    public BackgroundConfig background = new BackgroundConfig();
    public FoodAndDrinksConfig foodAndDrinks = new FoodAndDrinksConfig();
    public MobsConfig mobs = new MobsConfig();
    public MapConfig mapTooltip = new MapConfig();
    public PaintingConfig paintingTooltip = new PaintingConfig();
    public BannerPatternConfig bannerPatternTooltip = new BannerPatternConfig();
    public ArmorConfig armorIconTooltip = new ArmorConfig();
    public DurabilityConfig durability = new DurabilityConfig();
    public HeldItemTooltipConfig heldItemTooltip = new HeldItemTooltipConfig();
    private static final Jankson JANKSON = Jankson.builder().registerSerializer(Color.class, (color, marshaller) -> new JsonPrimitive((Object)String.format("#%02X%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()))).registerDeserializer(JsonPrimitive.class, Color.class, (json, marshaller) -> {
        String hex = json.asString();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() == 8) {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            int a = Integer.parseInt(hex.substring(6, 8), 16);
            return new Color(r, g, b, a);
        }
        if (hex.length() == 6) {
            return Color.decode("#" + hex);
        }
        throw new IllegalArgumentException("Invalid color format: " + json.asString());
    }).build();
    public static final Path CONFIG_PATH = YACLPlatform.getConfigDir().resolve("enhancedtooltips.json5");
    private static EnhancedTooltipsConfig cachedConfig;

    public static EnhancedTooltipsConfig load() {
        EnhancedTooltipsConfig config;
        if (cachedConfig != null) {
            return cachedConfig;
        }
        try {
            if (!Files.exists(CONFIG_PATH, new LinkOption[0])) {
                EnhancedTooltips.LOGGER.info("[{}] Config file not found. Creating a new one...", (Object)"EnhancedTooltips");
                config = new EnhancedTooltipsConfig();
                EnhancedTooltipsConfig.save(config);
            } else {
                String configContent = Files.readString(CONFIG_PATH).trim();
                if (!configContent.startsWith("{") || !configContent.endsWith("}")) {
                    EnhancedTooltips.LOGGER.error("[{}] Config file is empty or invalid. Creating a new one...", (Object)"EnhancedTooltips");
                    config = new EnhancedTooltipsConfig();
                    EnhancedTooltipsConfig.save(config);
                } else {
                    JsonObject configJson = EnhancedTooltipsConfig.ensureDefaults(JANKSON.load(configContent));
                    config = (EnhancedTooltipsConfig)JANKSON.fromJson(configJson, EnhancedTooltipsConfig.class);
                }
            }
        }
        catch (SyntaxError | IOException e) {
            EnhancedTooltips.LOGGER.error("[{}]", (Object)"EnhancedTooltips", (Object)e);
            config = new EnhancedTooltipsConfig();
            EnhancedTooltipsConfig.save(config);
        }
        cachedConfig = config;
        return cachedConfig;
    }

    public static void save(EnhancedTooltipsConfig config) {
        try {
            String jsonString = JANKSON.toJson((Object)config).toJson(true, true);
            Files.createDirectories(CONFIG_PATH.getParent(), new FileAttribute[0]);
            Files.writeString(CONFIG_PATH, (CharSequence)jsonString, new OpenOption[0]);
            cachedConfig = config;
        }
        catch (IOException e) {
            EnhancedTooltips.LOGGER.error("[{}]", (Object)"EnhancedTooltips", (Object)e);
        }
    }

    private static JsonObject ensureDefaults(JsonObject configJson) {
        boolean modified = false;
        EnhancedTooltipsConfig defaultConfig = new EnhancedTooltipsConfig();
        for (Field field : EnhancedTooltipsConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            try {
                String fieldName = field.getName();
                Object defaultValue = field.get(defaultConfig);
                if (configJson.containsKey((Object)fieldName)) continue;
                EnhancedTooltips.LOGGER.info("[{}] Missing config field '{}'. Re-saving as default.", (Object)"EnhancedTooltips", (Object)fieldName);
                configJson.put(fieldName, JANKSON.toJson(defaultValue));
                modified = true;
            }
            catch (IllegalAccessException e) {
                EnhancedTooltips.LOGGER.error("[{}] Failed to access field '{}'", new Object[]{"EnhancedTooltips", field.getName(), e});
            }
        }
        if (modified) {
            EnhancedTooltipsConfig config = (EnhancedTooltipsConfig)JANKSON.fromJson(configJson, EnhancedTooltipsConfig.class);
            EnhancedTooltipsConfig.save(config);
        }
        return configJson;
    }

    public static Screen createConfigScreen(Screen parent) {
        return parent;
    }

    public static class GeneralConfig {
        @Comment(value="Shows the rarity of an item in its tooltip.\n(default: true)")
        public boolean rarityTooltip = true;
        @Comment(value="Shows the category of an item in a badge on its tooltip.\n(default: true)")
        public boolean itemBadges = true;
        @Comment(value="Removes all spacing between lines.\n(default: false)")
        public boolean removeAllSpacing = false;
    }

    public static class PopUpAnimationConfig {
        @Comment(value="Toggles the tooltip pop-up animation.\n(default: true)")
        public boolean enabled = true;
        @Comment(value="Duration of the pop-up animation in seconds.\n(default: 1.5)")
        public float time = 1.5f;
        @Comment(value="Magnitude of the pop-up animation.\n(default: 1.0)")
        public float magnitude = 1.0f;
    }

    public static class ItemPreviewAnimationConfig {
        @Comment(value="Toggles the item preview bouncing animation.\n(default: true)")
        public boolean enabled = true;
        @Comment(value="Duration of the item preview animation in seconds.\n(default: 1.0)")
        public float time = 1.0f;
        @Comment(value="Magnitude of the item preview animation.\n(default: 2.0)")
        public float magnitude = 2.0f;
    }

    public static class BorderConfig {
        @Comment(value="Determines how the border color of tooltips is set.\nRARITY/ITEM_NAME/CUSTOM (default: RARITY)")
        public BorderColorMode borderColor = BorderColorMode.RARITY;
        @Comment(value="Custom border colors when borderColor is set to CUSTOM.")
        public CustomBorderColorsConfig customBorderColors = new CustomBorderColorsConfig();
    }

    public static class BackgroundConfig {
        @Comment(value="Determines how the background of tooltips is set.\nDEFAULT/CUSTOM (default: DEFAULT)")
        public BackgroundMode backgroundMode = BackgroundMode.DEFAULT;
        @Comment(value="Background color of the tooltip.\n(default: #100010F0)")
        public Color backgroundColor = new Color(-267386864, true);
    }

    public static class FoodAndDrinksConfig {
        @Comment(value="Shows the maximum hunger which can be gained from an item in its tooltip.\n(default: true)")
        public boolean hungerTooltip = true;
        @Comment(value="Shows the maximum saturation which can be gained from an item in its tooltip.\n(default: true)")
        public boolean saturationTooltip = true;
        @Comment(value="Shows a list of effects applied on consuming an item in its tooltip.\nWITH_ICONS/WITHOUT_ICONS/OFF (default: WITH_ICONS)")
        public EffectsTooltipMode effectsTooltip = EffectsTooltipMode.WITH_ICONS;
    }

    public static class MobsConfig {
        @Comment(value="The rotation speed of the model.\n(default: 0.2)")
        public float rotationSpeed = 0.2f;
        @Comment(value="Shows a preview of the armor piece on an armor stand or the player.\nPLAYER/ARMOR_STAND/OFF (default: PLAYER)")
        public ArmorTooltipMode armorTooltip = ArmorTooltipMode.PLAYER;
        @Comment(value="Shows a preview of the horse armor on a horse.\n(default: true)")
        public boolean horseArmorTooltip = true;
        @Comment(value="Shows a preview of the nautilus armor on a nautilus.\n(default: true)")
        public boolean nautilusArmorTooltip = true;
        @Comment(value="Shows a preview of the wolf armor on a wolf.\n(default: true)")
        public boolean wolfArmorTooltip = true;
        @Comment(value="Shows a preview of the bucket entity in a bucket.\n(default: true)")
        public boolean bucketTooltip = true;
        @Comment(value="Shows a preview of the spawn egg entity.\n(default: true)")
        public boolean spawnEggTooltip = true;
    }

    public static class MapConfig {
        @Comment(value="Shows a preview of the filled map in its tooltip.\n(default: true)")
        public boolean enabled = true;
    }

    public static class PaintingConfig {
        @Comment(value="Shows a preview of the painting in its tooltip.\n(default: true)")
        public boolean enabled = true;
    }

    public static class BannerPatternConfig {
        @Comment(value="Shows a preview of the banner pattern in its tooltip.\n(default: true)")
        public boolean enabled = true;
    }

    public static class ArmorConfig {
        @Comment(value="Shows the armor attribute in the form of icons.\n(default: true)")
        public boolean enabled = true;
    }

    public static class DurabilityConfig {
        @Comment(value="Shows the durability of an item in its tooltip.\nVALUE/PERCENTAGE/OFF (default: VALUE)")
        public DurabilityTooltipMode durabilityTooltip = DurabilityTooltipMode.VALUE;
        @Comment(value="Shows the durability of an item, represented by a bar, in its tooltip.\n(default: false)")
        public boolean durabilityBar = false;
    }

    public static class HeldItemTooltipConfig {
        @Comment(value="Toggles the improved held items tooltips feature.\nON/MINIMAL/OFF (default: ON)")
        public HeldItemTooltipMode mode = HeldItemTooltipMode.ON;
        @Comment(value="Shows a neat background behind the held item tooltip text.\n(default: true)")
        public boolean showBackground = true;
        @Comment(value="Defines the line limit for the held item tooltip.\n(default: 5)")
        public int maxLines = 5;
        @Comment(value="Adjusts the size of the held item tooltips.\nA scale of 100% displays the tooltip at its default size.\n(default: 1.0)")
        public float scaleFactor = 1.0f;
        @Comment(value="Hides the item's name from its held item toolip.\n(default: false)")
        public boolean hideItemName = false;
        @Comment(value="Shows a dynamic tilt animation for the held item tooltip when scrolling the hotbar.\n(default: true)")
        public boolean tiltAnimation = true;
        @Comment(value="Duration of the tilt animation in ms.\n(default: 300)")
        public int tiltDuration = 300;
        @Comment(value="Magnitude of the tilt animation.\n(default: 10.0)")
        public float tiltMagnitude = 10.0f;
        @Comment(value="Smoothness of the tilt animation.\n(default: 2.0)")
        public float tiltEasing = 2.0f;
    }

    public static enum HeldItemTooltipMode implements NameableEnum
    {
        ON("options.on"),
        MINIMAL("options.particles.minimal"),
        OFF("options.off");

        private final String translationKey;

        private HeldItemTooltipMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component getDisplayName() {
            return Component.translatable((String)this.translationKey);
        }
    }

    public static enum DurabilityTooltipMode implements NameableEnum
    {
        VALUE("enhancedtooltips.config.durabilityTooltip.value"),
        PERCENTAGE("enhancedtooltips.config.durabilityTooltip.percentage"),
        OFF("options.off");

        private final String translationKey;

        private DurabilityTooltipMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component getDisplayName() {
            return Component.translatable((String)this.translationKey);
        }
    }

    public static enum ArmorTooltipMode implements NameableEnum
    {
        PLAYER("entity.minecraft.player"),
        ARMOR_STAND("entity.minecraft.armor_stand"),
        OFF("options.off");

        private final String translationKey;

        private ArmorTooltipMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component getDisplayName() {
            return Component.translatable((String)this.translationKey);
        }
    }

    public static enum EffectsTooltipMode implements NameableEnum
    {
        WITH_ICONS("enhancedtooltips.config.effectsTooltip.withIcons"),
        WITHOUT_ICONS("enhancedtooltips.config.effectsTooltip.withoutIcons"),
        OFF("options.off");

        private final String translationKey;

        private EffectsTooltipMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component getDisplayName() {
            return Component.translatable((String)this.translationKey);
        }
    }

    public static enum BackgroundMode implements NameableEnum
    {
        DEFAULT("resourcePack.vanilla.name"),
        CUSTOM("generator.custom");

        private final String translationKey;

        private BackgroundMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component getDisplayName() {
            return Component.translatable((String)this.translationKey);
        }
    }

    public static enum BorderColor {
        COMMON(0x505000FF),
        UNCOMMON(-171),
        RARE(-11141121),
        EPIC(-65281),
        END_COLOR(0x505000FF);

        private final int rgb;

        private BorderColor(int rgb) {
            this.rgb = rgb;
        }

        public Color getColor() {
            return new Color(this.rgb, true);
        }
    }

    public static class CustomBorderColorsConfig {
        @Comment(value="'Common' rarity border color.\n(default: #5000FF50)")
        public Color common = BorderColor.COMMON.getColor();
        @Comment(value="'Uncommon' rarity border color.\n(default: #FFFF55FF)")
        public Color uncommon = BorderColor.UNCOMMON.getColor();
        @Comment(value="'Rare' rarity border color.\n(default: #55FFFFFF)")
        public Color rare = BorderColor.RARE.getColor();
        @Comment(value="'Epic' rarity border color.\n(default: #FF00FFFF)")
        public Color epic = BorderColor.EPIC.getColor();
        @Comment(value="Gradient end color for the border.\n(default: #5000FF50)")
        public Color endColor = BorderColor.END_COLOR.getColor();
    }

    public static enum BorderColorMode implements NameableEnum
    {
        RARITY("enhancedtooltips.config.borderColor.rarity"),
        ITEM_NAME("enhancedtooltips.config.borderColor.itemName"),
        CUSTOM("generator.custom");

        private final String translationKey;

        private BorderColorMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component getDisplayName() {
            return Component.translatable((String)this.translationKey);
        }
    }
}

