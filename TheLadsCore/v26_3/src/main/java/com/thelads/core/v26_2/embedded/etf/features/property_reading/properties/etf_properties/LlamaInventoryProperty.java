package com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.etf_properties;

import org.jetbrains.annotations.NotNull;
import com.thelads.core.v26_2.embedded.etf.features.property_reading.properties.generic_properties.SimpleIntegerArrayProperty;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v26_2.embedded.etf.utils.ETFEntity;

import java.util.Properties;

import net.minecraft.world.entity.animal.equine.Llama;

public class LlamaInventoryProperty extends SimpleIntegerArrayProperty {


    protected LlamaInventoryProperty(Properties properties, int propertyNum) throws RandomPropertyException {
        super(getGenericIntegerSplitWithRanges(properties, propertyNum, "llamaInventory"));
    }

    public static LlamaInventoryProperty getPropertyOrNull(Properties properties, int propertyNum) {
        try {
            return new LlamaInventoryProperty(properties, propertyNum);
        } catch (RandomPropertyException e) {
            return null;
        }
    }


    @Override
    public @NotNull String[] getPropertyIds() {
        return new String[]{"llamaInventory"};
    }

    @Override
    protected int getValueFromEntity(ETFEntityRenderState entity) {
        if (entity != null && entity.entity() instanceof Llama llama)
            return llama.getInventoryColumns();
        return Integer.MIN_VALUE;
    }
}
