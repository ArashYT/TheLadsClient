package com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.etf_properties;

import org.jetbrains.annotations.NotNull;
import com.thelads.core.v1_21_1.embedded.etf.features.ETFManager;
import com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.generic_properties.SimpleIntegerArrayProperty;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;

import java.util.Properties;


public class TextureSuffixProperty extends SimpleIntegerArrayProperty {


    protected TextureSuffixProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(getGenericIntegerSplitWithRanges(properties, propertyNum, "textureSuffix", "texture_suffix"));
    }


    public static TextureSuffixProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new TextureSuffixProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"textureSuffix", "texture_suffix"};
    }

    @Override
    protected int getValueFromEntity(ETFEntityRenderState entity) {
        int val = ETFManager.getInstance().LAST_SUFFIX_OF_ENTITY.get(entity.uuid());
        return Math.max(val, 0);
    }
}
