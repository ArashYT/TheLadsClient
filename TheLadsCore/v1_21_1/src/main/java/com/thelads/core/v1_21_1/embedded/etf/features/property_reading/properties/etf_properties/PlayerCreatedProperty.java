package com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.etf_properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.generic_properties.BooleanProperty;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;

import java.util.Properties;

import net.minecraft.world.entity.animal.IronGolem;

public class PlayerCreatedProperty extends BooleanProperty {


    protected PlayerCreatedProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(getGenericBooleanThatCanNull(properties, propertyNum, "playerCreated", "player_created"));
    }

    public static PlayerCreatedProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new PlayerCreatedProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }


    @Override
    @Nullable
    protected Boolean getValueFromEntity(ETFEntityRenderState etfEntity) {
        if (etfEntity != null && etfEntity.entity() instanceof IronGolem golem)
            return golem.isPlayerCreated();
        return null;
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"playerCreated", "player_created"};
    }

}
