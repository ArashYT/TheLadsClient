package com.thelads.core.v1_21_11.embedded.etf.features.property_reading.properties.etf_properties.external;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v1_21_11.embedded.etf.features.property_reading.properties.RandomProperty;
import com.thelads.core.v1_21_11.embedded.etf.features.property_reading.properties.generic_properties.StringArrayOrRegexProperty;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFEntity;

import java.util.Properties;

import net.minecraft.client.Minecraft;

public class LanguageProperty extends StringArrayOrRegexProperty {


    protected LanguageProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(RandomProperty.readPropertiesOrThrow(properties, propertyNum, "language"));
    }

    public static LanguageProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new LanguageProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }


    @Override
    public @Nullable String getValueFromEntity(ETFEntityRenderState etfEntity) {
        return Minecraft.getInstance().options.languageCode;
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"language"};
    }

    @Override
    protected boolean shouldForceLowerCaseCheck() {
        return false;
    }


}
