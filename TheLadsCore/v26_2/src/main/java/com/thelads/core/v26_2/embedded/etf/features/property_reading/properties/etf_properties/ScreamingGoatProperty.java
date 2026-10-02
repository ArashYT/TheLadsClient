package com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.etf_properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.generic_properties.BooleanProperty;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v26_2.embedded.etf.utils.ETFEntity;

import java.util.Properties;

import net.minecraft.world.entity.animal.goat.Goat;

public class ScreamingGoatProperty extends BooleanProperty {


    protected ScreamingGoatProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(getGenericBooleanThatCanNull(properties, propertyNum, "screamingGoat", "screaming_goat"));
    }

    public static ScreamingGoatProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new ScreamingGoatProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }

    @Override
    @Nullable
    protected Boolean getValueFromEntity(ETFEntityRenderState etfEntity) {
        if (etfEntity != null && etfEntity.entity() instanceof Goat goat) return goat.isScreamingGoat();
        return null;
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"screamingGoat", "screaming_goat"};
    }

}
