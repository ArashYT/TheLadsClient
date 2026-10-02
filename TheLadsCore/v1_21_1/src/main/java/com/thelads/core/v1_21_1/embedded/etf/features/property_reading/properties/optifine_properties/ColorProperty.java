package com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.optifine_properties;


import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.client.renderer.entity.LlamaRenderer;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.WoolCarpetBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.RandomProperty;
import com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.generic_properties.StringArrayOrRegexProperty;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;

import java.util.Optional;
import java.util.Properties;

public class ColorProperty extends StringArrayOrRegexProperty {


    protected ColorProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(RandomProperty.readPropertiesOrThrow(properties, propertyNum, "colors", "collarColors"));
    }

    public static ColorProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new ColorProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }


    @Override
    protected boolean shouldForceLowerCaseCheck() {
        return true;
    }

    @Override
    @Nullable
    protected String getValueFromEntity(ETFEntityRenderState state) {//todo clean this up
        if (state != null) {
            var entity = state.entity();

            if (entity instanceof Llama llama) {

                DyeColor str;
                    str = llama.getSwag();

                if (str != null) {
                    return str.getName();
                }
            }
            if (entity instanceof Cat cat) return cat.getCollarColor().getName();
            if (entity instanceof Shulker shulker) {
                DyeColor str = shulker.getColor();
                if (str != null) {
                    return str.getName();
                }
            }
            if (entity instanceof Wolf wolf) return wolf.getCollarColor().getName();
            if (entity instanceof Sheep sheep) return sheep.getColor().getName();
            if (entity instanceof TropicalFish fishy) {
                DyeColor str = TropicalFish.getBaseColor(fishy.getVariant().getPackedId());
                return str.getName();
            }
            if (entity instanceof VariantHolder<?> variantHolder) {
                try {
                    //who knows what issues modded mobs might have
                    if (variantHolder.getVariant() instanceof DyeColor dye) {
                        return dye.getName();
                    } else if (variantHolder.getVariant() instanceof Optional<?> optional
                            && optional.isPresent() && optional.get() instanceof DyeColor dye) {
                        return dye.getName();
                    }
                } catch (Exception ignored) {
                }
            }

        }
        return null;
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"colors", "collarColors"};
    }

}
