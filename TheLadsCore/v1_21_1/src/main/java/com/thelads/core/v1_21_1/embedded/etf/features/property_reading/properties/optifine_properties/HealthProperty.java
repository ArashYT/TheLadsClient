package com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.optifine_properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v1_21_1.embedded.etf.features.property_reading.properties.generic_properties.FloatRangeFromStringArrayProperty;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;

import java.util.Properties;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public class HealthProperty extends FloatRangeFromStringArrayProperty {

    private final boolean isPercentage;

    protected HealthProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(readPropertiesOrThrow(properties, propertyNum, "health"));
        isPercentage = originalInput.contains("%");
    }

    public static HealthProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new HealthProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }


    @Nullable
    @Override
    protected Float getRangeValueFromEntity(ETFEntityRenderState entity) {
        if (entity != null && entity.entity() instanceof LivingEntity alive) {
            float health = alive.getHealth();
            //integer required by optifine when it is percentage
            return isPercentage ? Mth.ceil(health / alive.getMaxHealth() * 100) : health;
        }
        return null;
    }

    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"health"};
    }

}
