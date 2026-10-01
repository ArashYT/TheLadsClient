package com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.etf_properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.RandomProperty;
import com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.generic_properties.StringArrayOrRegexProperty;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v26_2.embedded.etf.utils.ETFEntity;

import java.util.Properties;

public class TeamProperty extends StringArrayOrRegexProperty {


    protected TeamProperty(Properties properties, int propertyNum) throws RandomProperty.RandomPropertyException {
        super(RandomProperty.readPropertiesOrThrow(properties, propertyNum, "teams", "team"));
    }

    public static TeamProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new TeamProperty(properties, propertyNum);
        } catch (RandomProperty.RandomPropertyException e) {
            return null;
        }
    }


    @Override
    public @Nullable String getValueFromEntity(ETFEntityRenderState etfEntity) {
        if (etfEntity.scoreboardTeam() != null) {
            return etfEntity.scoreboardTeam().getName();
        }
        return null;
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"teams", "team"};
    }

    @Override
    protected boolean shouldForceLowerCaseCheck() {
        return false;
    }


}
