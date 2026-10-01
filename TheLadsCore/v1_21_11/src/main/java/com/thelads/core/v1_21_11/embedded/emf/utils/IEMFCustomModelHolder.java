package com.thelads.core.v1_21_11.embedded.emf.utils;

import com.thelads.core.v1_21_11.embedded.emf.models.parts.EMFModelPartRoot;

public interface IEMFCustomModelHolder {

    default boolean emf$hasModel() {
        return emf$getModel() != null;
    }

    EMFModelPartRoot emf$getModel();

    void emf$setModel(EMFModelPartRoot model);
}
