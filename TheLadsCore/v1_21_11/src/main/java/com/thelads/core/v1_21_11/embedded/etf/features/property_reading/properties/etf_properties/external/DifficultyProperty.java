package com.thelads.core.v1_21_11.embedded.etf.features.property_reading.properties.etf_properties.external;

import org.jetbrains.annotations.NotNull;
import com.thelads.core.v1_21_11.embedded.etf.features.property_reading.properties.generic_properties.SimpleIntegerArrayProperty;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFEntity;

import java.util.Properties;

public class DifficultyProperty extends SimpleIntegerArrayProperty {


    protected DifficultyProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(getGenericIntegerSplitWithRanges(properties, propertyNum, "difficulty"));
    }


    public static DifficultyProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new DifficultyProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"difficulty"};
    }

    @Override
    protected int getValueFromEntity(ETFEntityRenderState entity) {
        if (entity != null) {
            return entity.world().getDifficulty().getId(); // 0 - 3 in vanilla
        }
        return 0;
    }
}
