package com.thelads.core.v1_21_11.embedded.etf.features.property_reading.properties.etf_properties.external;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import com.thelads.core.v1_21_11.embedded.etf.features.property_reading.properties.generic_properties.SimpleIntegerArrayProperty;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFEntity;

import java.util.Properties;

public class ClientGameModeProperty extends SimpleIntegerArrayProperty {


    protected ClientGameModeProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(getGenericIntegerSplitWithRanges(properties, propertyNum, "clientGameMode"));
    }


    public static ClientGameModeProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new ClientGameModeProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"clientGameMode"};
    }

    @Override
    protected int getValueFromEntity(ETFEntityRenderState entity) {
        if (Minecraft.getInstance().player != null) {
            var mode = Minecraft.getInstance().player.gameMode();
            if (mode != null) return mode.getId();
        }
        return -1;
    }
}
