package com.thelads.core.v1_21_1.embedded.etf.features.state;

import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;

public interface HoldsETFRenderState {
    ETFEntityRenderState etf$getState();
    void etf$initState(ETFEntity entity);
}