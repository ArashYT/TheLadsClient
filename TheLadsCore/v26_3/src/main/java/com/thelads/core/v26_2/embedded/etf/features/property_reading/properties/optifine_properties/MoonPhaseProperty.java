package com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.optifine_properties;

import org.jetbrains.annotations.NotNull;
import net.minecraft.client.Minecraft;
import com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.generic_properties.SimpleIntegerArrayProperty;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v26_2.embedded.etf.utils.ETFEntity;

import java.util.Properties;


public class MoonPhaseProperty extends SimpleIntegerArrayProperty {


    protected MoonPhaseProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(getGenericIntegerSplitWithRanges(properties, propertyNum, "moonPhase"));
    }


    public static MoonPhaseProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new MoonPhaseProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"moonPhase"};
    }

    @Override
    protected int getValueFromEntity(ETFEntityRenderState entity) {
        return Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.skyRenderState.moonPhase.index();
    }
}
