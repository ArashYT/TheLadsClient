package com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.etf_properties;

import org.jetbrains.annotations.NotNull;
import com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.generic_properties.SimpleIntegerArrayProperty;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v26_2.embedded.etf.utils.ETFEntity;

import java.util.Properties;


public class LightProperty extends SimpleIntegerArrayProperty {


    protected LightProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(getGenericIntegerSplitWithRanges(properties, propertyNum, "light"));
    }


    public static LightProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new LightProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"light"};
    }

    @Override
    protected int getValueFromEntity(ETFEntityRenderState entity) {
        if (entity == null || entity.world() == null || entity.blockPos() == null) return -1;

        return entity.world().getMaxLocalRawBrightness(entity.blockPos());
    }
}
