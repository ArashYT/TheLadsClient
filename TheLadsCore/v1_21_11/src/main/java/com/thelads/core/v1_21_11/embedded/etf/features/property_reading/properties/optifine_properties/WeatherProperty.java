package com.thelads.core.v1_21_11.embedded.etf.features.property_reading.properties.optifine_properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v1_21_11.embedded.etf.features.property_reading.properties.generic_properties.StringArrayOrRegexProperty;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFEntity;

import java.util.Properties;


public class WeatherProperty extends StringArrayOrRegexProperty {
    protected WeatherProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(readPropertiesOrThrow(properties, propertyNum, "weather"));
        if (ARRAY.contains("rain")) ARRAY.add("thunder");
    }

    public static WeatherProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new WeatherProperty(properties, propertyNum);
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
    protected String getValueFromEntity(ETFEntityRenderState entity) {
        if (entity.world() != null) {
            if (entity.world().isThundering()) return "thunder";
            if (entity.world().isRaining()) return "rain";
            return "clear";
        }
        return null;
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"weather"};
    }

}
