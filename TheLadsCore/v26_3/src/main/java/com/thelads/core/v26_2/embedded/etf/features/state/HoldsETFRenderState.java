package com.thelads.core.v26_2.embedded.etf.features.state;

import com.thelads.core.v26_2.embedded.etf.utils.ETFEntity;

public interface HoldsETFRenderState {
    ETFEntityRenderState etf$getState();
    void etf$initState(ETFEntity entity);
}