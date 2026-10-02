package com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.etf_properties;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.optifine_properties.NBTProperty;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v26_2.embedded.etf.utils.ETFEntity;

import java.util.Properties;

public class NBTClientProperty extends NBTProperty {
    protected NBTClientProperty(final Properties properties, final int propertyNum, final String nbtPrefix) throws RandomPropertyException {
        super(properties, propertyNum, nbtPrefix);
    }

    public static NBTProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new NBTClientProperty(properties, propertyNum, "nbtClient");
        } catch (RandomPropertyException e) {
            return null;
        }
    }

    @Override
    protected CompoundTag getEntityNBT(final ETFEntityRenderState entity) {
        if (Minecraft.getInstance().player != null) {
            return ((ETFEntity)Minecraft.getInstance().player).etf$getNbt();
        }
        return INTENTIONAL_FAILURE;
    }
}
