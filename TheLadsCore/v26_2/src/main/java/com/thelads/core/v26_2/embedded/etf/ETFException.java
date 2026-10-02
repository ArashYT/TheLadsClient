package com.thelads.core.v26_2.embedded.etf;

import net.minecraft.world.level.block.entity.BlockEntity;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFState;

public class ETFException extends RuntimeException {
    public ETFException(String message) {
        super(amendMessage(message));
    }

    private static String amendMessage(String message) {
        var entity = ETFState.state();
        return message + """
                
                ----------------------
                ETF context:
                 - Entity = %s
                 - EMF installed = %s
                ----------------------
                """.formatted(entity == null ? "null" : entity.isBlockEntity() ? ((BlockEntity) entity.entity()).getType() : entity.entityType(),
                ETF.isThisModLoaded("entity_model_features"));
    }
}
