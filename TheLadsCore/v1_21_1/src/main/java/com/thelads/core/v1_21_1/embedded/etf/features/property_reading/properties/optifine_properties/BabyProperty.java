package com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.optifine_properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.RandomProperty;
import com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.generic_properties.BooleanProperty;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;

import java.util.Properties;

import net.minecraft.world.entity.LivingEntity;


public class BabyProperty extends BooleanProperty {


    protected BabyProperty(Properties properties, int propertyNum) throws RandomProperty.RandomPropertyException {
        super(getGenericBooleanThatCanNull(properties, propertyNum, "baby"));
    }

    public static BabyProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new BabyProperty(properties, propertyNum);
        } catch (RandomProperty.RandomPropertyException e) {
            return null;
        }
    }


    @Override
    @Nullable
    protected Boolean getValueFromEntity(ETFEntityRenderState entity) {
        if (entity != null && entity.entity() instanceof LivingEntity alive) {
            return alive.isBaby();
        }
        return null;
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"baby"};
    }

}
