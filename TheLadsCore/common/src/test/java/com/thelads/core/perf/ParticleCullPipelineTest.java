package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.ParticleFrustum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParticleCullPipelineTest {
    @BeforeEach
    void setUp() {
        ParticleCullPipeline.setSectionCullingDisabled(false);
        ParticleCullPipeline.setBatchedPipelineDisabled(false);
    }

    @Test
    void optionsControlPipeline() {
        assertTrue(ParticleCullPipeline.isSectionCullingEnabled());
        assertTrue(ParticleCullPipeline.isBatchedPipelineEnabled());

        ParticleCullPipeline.setSectionCullingDisabled(true);
        assertFalse(ParticleCullPipeline.isSectionCullingEnabled());

        ParticleCullPipeline.setBatchedPipelineDisabled(true);
        assertFalse(ParticleCullPipeline.isBatchedPipelineEnabled());
    }

    @Test
    void invalidFrustumLeavesSectionVisible() {
        ParticleFrustum frustum = new ParticleFrustum();
        assertTrue(ParticleCullPipeline.isSectionVisible(frustum, 0, 0, 0));
        assertTrue(ParticleCullPipeline.isSectionVisible(null, 10, 5, -2));
    }
}
