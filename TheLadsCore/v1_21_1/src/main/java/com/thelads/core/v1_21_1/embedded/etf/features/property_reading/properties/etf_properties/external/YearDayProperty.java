package com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.etf_properties.external;

import org.jetbrains.annotations.NotNull;
import com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.generic_properties.SimpleIntegerArrayProperty;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;

import java.util.Calendar;
import java.util.Properties;


public class YearDayProperty extends SimpleIntegerArrayProperty {


    protected YearDayProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(getGenericIntegerSplitWithRanges(properties, propertyNum, "yearDay", "dayYear"));
    }


    public static YearDayProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new YearDayProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"yearDay", "dayYear"};
    }

    @Override
    protected int getValueFromEntity(ETFEntityRenderState entity) {
        return Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        //first 1
    }
}
