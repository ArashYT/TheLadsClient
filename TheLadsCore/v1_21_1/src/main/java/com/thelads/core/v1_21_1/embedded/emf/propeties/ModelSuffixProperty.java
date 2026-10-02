package com.thelads.core.v1_21_1.embedded.emf.propeties;

import org.jetbrains.annotations.NotNull;
import com.thelads.core.v1_21_1.embedded.emf.EMFManager;
import com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.generic_properties.SimpleIntegerArrayProperty;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;

import java.util.Properties;

public class ModelSuffixProperty extends SimpleIntegerArrayProperty {
    protected ModelSuffixProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(getGenericIntegerSplitWithRanges(properties, propertyNum, "modelSuffix", "model_suffix"));
    }

    public static ModelSuffixProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new ModelSuffixProperty(properties, propertyNum);
        } catch (RandomPropertyException var3) {
            return null;
        }
    }

    public @NotNull String[] getPropertyIds() {
        return new String[]{"modelSuffix", "model_suffix"};
    }

    protected int getValueFromEntity(ETFEntityRenderState entity) {
        int val = EMFManager.getInstance().lastModelSuffixOfEntity.get(entity.uuid());
        return Math.max(val, 0);
    }
}
