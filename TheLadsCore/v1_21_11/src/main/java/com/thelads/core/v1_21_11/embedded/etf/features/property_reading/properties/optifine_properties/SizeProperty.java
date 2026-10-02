package com.thelads.core.v1_21_11.embedded.etf.features.property_reading.properties.optifine_properties;

import org.jetbrains.annotations.NotNull;
import com.thelads.core.v1_21_11.embedded.etf.features.property_reading.properties.RandomProperty;
import com.thelads.core.v1_21_11.embedded.etf.features.property_reading.properties.generic_properties.SimpleIntegerArrayProperty;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFEntity;

import java.util.Properties;

import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Slime;

public class SizeProperty extends SimpleIntegerArrayProperty {


    protected SizeProperty(Properties properties, int propertyNum) throws RandomProperty.RandomPropertyException {
        super(getGenericIntegerSplitWithRanges(properties, propertyNum, "sizes", "size"));
    }


    public static SizeProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new SizeProperty(properties, propertyNum);
        } catch (RandomProperty.RandomPropertyException e) {
            return null;
        }
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"sizes", "size"};
    }

    @Override
    protected int getValueFromEntity(ETFEntityRenderState entity) {
        if (entity != null && entity.entity() instanceof
                Slime slime // Magma cube extends this in these versions
        ) {
            //magma cube too
            return slime.getSize() - 1;
        } else if (entity != null && entity.entity() instanceof Phantom phantom) {
            return phantom.getPhantomSize();
        }
        return 0;
    }
}
