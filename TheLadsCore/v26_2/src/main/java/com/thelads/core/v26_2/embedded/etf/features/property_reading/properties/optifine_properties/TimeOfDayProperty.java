package com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.optifine_properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.generic_properties.LongRangeFromStringArrayProperty;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v26_2.embedded.etf.utils.ETFEntity;

import java.util.Properties;


public class TimeOfDayProperty extends LongRangeFromStringArrayProperty {


    protected TimeOfDayProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(readPropertiesOrThrow(properties, propertyNum, "dayTime"));
    }

    public static TimeOfDayProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new TimeOfDayProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }


    @Nullable
    @Override
    protected Long getRangeValueFromEntity(ETFEntityRenderState entity) {
        if (entity.world() != null)
            return entity.world().getOverworldClockTime() % 24000;
        return null;
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"dayTime"};
    }

}
