package com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.etf_properties;

import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.animal.FrogVariant;

import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.level.block.entity.PotDecorations;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.RandomProperty;
import com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.generic_properties.StringArrayOrRegexProperty;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFUtils2;

import java.util.Optional;
import java.util.Properties;

public class VariantProperty extends StringArrayOrRegexProperty {



    protected VariantProperty(String string) throws RandomPropertyException {
        super(string);
    }

    public static VariantProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new VariantProperty(readPropertiesOrThrow(properties, propertyNum, "variant", "variants"));
        } catch (RandomProperty.RandomPropertyException var3) {
            return null;
        }
    }

    @Override
    protected boolean shouldForceLowerCaseCheck() {
        return false;
    }


    public @Nullable String getValueFromEntity(ETFEntityRenderState state) {
        if (state == null) return null;
        var etfEntity = state.entity();
        if (etfEntity instanceof Entity) {
            if (etfEntity instanceof VariantHolder<?> variableEntity) {
                if (variableEntity.getVariant() instanceof StringRepresentable stringIdentifiable) {
                    return stringIdentifiable.getSerializedName();
                }
            
               if (variableEntity.getVariant() instanceof CatVariant catVariant) {
                   return BuiltInRegistries.CAT_VARIANT.getResourceKey(
                           catVariant).map(catVariantRegistryKey -> catVariantRegistryKey.location().getPath()
                         ).orElse(null);
               }
               if (variableEntity.getVariant() instanceof FrogVariant frogVariant) {
                           return BuiltInRegistries.FROG_VARIANT.getResourceKey(
                                   frogVariant).map(frogVariantRegistryKey -> frogVariantRegistryKey.location().getPath()
                            ).orElse(null);
                }
                //e.g. painting entity
                if (variableEntity.getVariant() instanceof Holder<?> registryEntry) {
                    return registryEntry.unwrapKey().isPresent() ? registryEntry.unwrapKey().get().location().getPath() : null;
                }
                //shulker variants
                if (variableEntity.getVariant() instanceof Optional<?> possibleStringIdentifiable) {
                    if (possibleStringIdentifiable.isPresent() && possibleStringIdentifiable.get() instanceof StringRepresentable stringIdentifiable) {
                        return stringIdentifiable.getSerializedName();
                    }
                    return null;
                }
            
                if (variableEntity.getVariant() instanceof VillagerType villagerType) {
                    return villagerType.toString();
                }
                return variableEntity.getVariant().toString();
            }

            return BuiltInRegistries.ENTITY_TYPE.getResourceKey(((Entity) etfEntity).getType()).map(key -> key.location().getPath()).orElse(null);

        } else if (etfEntity instanceof BlockEntity) {
            //noinspection IfCanBeSwitch
            if (etfEntity instanceof SignBlockEntity signBlockEntity
                    && signBlockEntity.getBlockState().getBlock() instanceof SignBlock abstractSignBlock) {
                return abstractSignBlock.type().name();
            }
            if (etfEntity instanceof ShulkerBoxBlockEntity shulkerBoxBlockEntity
                    && shulkerBoxBlockEntity.getBlockState().getBlock() instanceof ShulkerBoxBlock shulkerBoxBlock) {
                return String.valueOf(shulkerBoxBlock.getColor());
            }
            if (etfEntity instanceof BedBlockEntity bedBlockEntity
                    && bedBlockEntity.getBlockState().getBlock() instanceof BedBlock bedBlock) {
                return String.valueOf(bedBlock.getColor());
            }
            if (etfEntity instanceof DecoratedPotBlockEntity pot) {
                PotDecorations sherds = pot.getDecorations();
                return (sherds.back().isPresent() ? sherds.back().get().getDescriptionId() : "none")
                        + "," +
                        (sherds.left().isPresent() ? sherds.left().get().getDescriptionId() : "none")
                        + "," +
                        (sherds.right().isPresent() ? sherds.right().get().getDescriptionId() : "none")
                        + "," +
                        (sherds.front().isPresent() ? sherds.front().get().getDescriptionId() : "none");
            }
            String suffix = "";
            if (etfEntity instanceof SkullBlockEntity skull) {
                suffix = "_direction_" + skull.getBlockState().getValue(SkullBlock.ROTATION);
            }

            return BuiltInRegistries.BLOCK_ENTITY_TYPE.getResourceKey(((BlockEntity) etfEntity).getType()).map(key -> key.location().getPath()).orElse(null) + suffix;
        }
        return null;
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"variant", "variants"};
    }
}