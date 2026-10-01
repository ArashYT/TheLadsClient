package com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.etf_properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.generic_properties.BooleanProperty;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;

import java.util.Properties;

public class MovingProperty extends BooleanProperty {


    protected MovingProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(getGenericBooleanThatCanNull(properties, propertyNum, "moving", "is_moving"));
    }

    public static MovingProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new MovingProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }

    @Override
    @Nullable
    protected Boolean getValueFromEntity(ETFEntityRenderState etfEntity) {
        return etfEntity.velocity().horizontalDistance() != 0;
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"moving", "is_moving"};
    }

}
