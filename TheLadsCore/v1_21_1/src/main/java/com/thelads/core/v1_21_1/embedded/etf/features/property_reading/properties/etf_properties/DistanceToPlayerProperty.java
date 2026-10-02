package com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.etf_properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.generic_properties.FloatRangeFromStringArrayProperty;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;

import java.util.Properties;

import net.minecraft.client.Minecraft;

public class DistanceToPlayerProperty extends FloatRangeFromStringArrayProperty {


    protected DistanceToPlayerProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(readPropertiesOrThrow(properties, propertyNum, "distance", "distanceFromPlayer"));
    }

    public static DistanceToPlayerProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new DistanceToPlayerProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }


    @Nullable
    @Override
    protected Float getRangeValueFromEntity(ETFEntityRenderState entity) {
        if (Minecraft.getInstance().player == null)
            return null;
        return entity.distanceTo(Minecraft.getInstance().player);
    }

    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"distance", "distanceFromPlayer"};
    }

}
