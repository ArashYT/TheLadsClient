/*
 * Decompiled with CFR 0.152.
 */
package squeek.appleskin.helpers;

import net.minecraft.resources.Identifier;

public class TextureHelper {
    public static final Identifier MOD_ICONS = Identifier.fromNamespaceAndPath("appleskin", "textures/icons.png");
    public static final Identifier HUNGER_OUTLINE_SPRITE = Identifier.fromNamespaceAndPath("appleskin", "tooltip_hunger_outline");
    public static final Identifier FOOD_EMPTY_HUNGER_TEXTURE = Identifier.withDefaultNamespace("hud/food_empty_hunger");
    public static final Identifier FOOD_HALF_HUNGER_TEXTURE = Identifier.withDefaultNamespace("hud/food_half_hunger");
    public static final Identifier FOOD_FULL_HUNGER_TEXTURE = Identifier.withDefaultNamespace("hud/food_full_hunger");
    public static final Identifier FOOD_EMPTY_TEXTURE = Identifier.withDefaultNamespace("hud/food_empty");
    public static final Identifier FOOD_HALF_TEXTURE = Identifier.withDefaultNamespace("hud/food_half");
    public static final Identifier FOOD_FULL_TEXTURE = Identifier.withDefaultNamespace("hud/food_full");
    public static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
    public static final Identifier HEART_HARDCORE_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container_hardcore");
    public static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
    public static final Identifier HEART_HARDCORE_FULL = Identifier.withDefaultNamespace("hud/heart/hardcore_full");
    public static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");
    public static final Identifier HEART_HARDCORE_HALF = Identifier.withDefaultNamespace("hud/heart/hardcore_half");

    public static Identifier getFoodTexture(boolean isRotten, FoodType type) {
        return switch (type.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> {
                if (isRotten) {
                    yield FOOD_EMPTY_HUNGER_TEXTURE;
                }
                yield FOOD_EMPTY_TEXTURE;
            }
            case 1 -> {
                if (isRotten) {
                    yield FOOD_HALF_HUNGER_TEXTURE;
                }
                yield FOOD_HALF_TEXTURE;
            }
            case 2 -> isRotten ? FOOD_FULL_HUNGER_TEXTURE : FOOD_FULL_TEXTURE;
        };
    }

    public static Identifier getHeartTexture(boolean hardcore, HeartType type) {
        return switch (type.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> {
                if (hardcore) {
                    yield HEART_HARDCORE_CONTAINER;
                }
                yield HEART_CONTAINER;
            }
            case 1 -> {
                if (hardcore) {
                    yield HEART_HARDCORE_FULL;
                }
                yield HEART_FULL;
            }
            case 2 -> hardcore ? HEART_HARDCORE_HALF : HEART_HALF;
        };
    }

    public static enum FoodType {
        EMPTY,
        HALF,
        FULL;

    }

    public static enum HeartType {
        CONTAINER,
        FULL,
        HALF;

    }
}

