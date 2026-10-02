package com.thelads.core.v26_2.embedded.emf.mod_compat;

import com.thelads.core.v26_2.embedded.emf.utils.EMFUtils;
import com.thelads.core.v26_2.embedded.etf.ETF;

import java.util.Objects;

public abstract class IrisShadowPassDetection {

    public abstract boolean inShadowPass();

    private static IrisShadowPassDetection instance;
    public static IrisShadowPassDetection getInstance() {
        if (instance == null) {
            try{
                instance = new IrisShadowPassDetectionImpl();
            } catch (Throwable e) {
                EMFUtils.log("EMF did not find the Iris API, disabling shadow pass detection");
                instance = new IrisShadowPassDetection() {
                    @Override
                    public boolean inShadowPass() {
                        return false;
                    }
                };
            }
        }
        return instance;
    }
    private static class IrisShadowPassDetectionImpl extends IrisShadowPassDetection {

        IrisShadowPassDetectionImpl(){
            if (!ETF.IRIS_DETECTED) throw new RuntimeException("Iris not detected, cannot use this class");
            Objects.requireNonNull(net.irisshaders.iris.api.v0.IrisApi.getInstance()) ;
        }
        @Override
        public boolean inShadowPass() {
            return net.irisshaders.iris.api.v0.IrisApi.getInstance().isRenderingShadowPass();
        }
    }
}
