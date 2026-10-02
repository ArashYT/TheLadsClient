package com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.etf_properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.generic_properties.FloatRangeFromStringArrayProperty;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v26_2.embedded.etf.utils.ETFEntity;

import java.util.Properties;

import net.minecraft.world.entity.animal.equine.AbstractHorse;

public class JumpProperty extends FloatRangeFromStringArrayProperty {


    protected JumpProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(readPropertiesOrThrow(properties, propertyNum, "jump", "jumpStrength", "jumpHeight"));
    }

    public static JumpProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new JumpProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }

    @Nullable
    @Override
    protected Float getRangeValueFromEntity(ETFEntityRenderState entity) {
        if (entity != null && entity.entity() instanceof AbstractHorse horse)
            return horse.getJumpBoostPower();
        return null;
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"jump", "jumpStrength", "jumpHeight"};
    }

}
