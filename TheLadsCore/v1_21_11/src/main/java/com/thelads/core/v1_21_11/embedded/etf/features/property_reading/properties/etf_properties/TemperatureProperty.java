package com.thelads.core.v1_21_11.embedded.etf.features.property_reading.properties.etf_properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v1_21_11.embedded.etf.features.property_reading.properties.generic_properties.FloatRangeFromStringArrayProperty;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFEntity;

import java.util.Properties;

public class TemperatureProperty extends FloatRangeFromStringArrayProperty {


    protected TemperatureProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(readPropertiesOrThrow(properties, propertyNum, "temperature"));
    }

    public static TemperatureProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new TemperatureProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }

    @Nullable
    @Override
    protected Float getRangeValueFromEntity(ETFEntityRenderState entity) {
        if (entity == null) return null;

        var level = entity.world();
        if (level == null) return null;

        var biome = level.getBiome(entity.blockPos());
        return biome.value().getHeightAdjustedTemperature(entity.blockPos()
            , entity.world().getSeaLevel()
        );
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"temperature"};
    }

}
