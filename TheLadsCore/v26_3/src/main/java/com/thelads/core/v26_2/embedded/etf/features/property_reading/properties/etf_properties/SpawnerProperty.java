package com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.etf_properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v26_2.embedded.etf.ETFApi;
import com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.generic_properties.BooleanProperty;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v26_2.embedded.etf.utils.ETFEntity;

import java.util.Properties;

public class SpawnerProperty extends BooleanProperty {


    protected SpawnerProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(getGenericBooleanThatCanNull(properties, propertyNum, "isSpawner", "spawner"));
    }

    public static SpawnerProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new SpawnerProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }


    @Override
    @Nullable
    protected Boolean getValueFromEntity(ETFEntityRenderState etfEntity) {
        if (etfEntity != null) {
            return etfEntity.uuid().getLeastSignificantBits() == ETFApi.ETF_SPAWNER_MARKER;
        }
        return null;
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"isSpawner", "spawner"};
    }

}
