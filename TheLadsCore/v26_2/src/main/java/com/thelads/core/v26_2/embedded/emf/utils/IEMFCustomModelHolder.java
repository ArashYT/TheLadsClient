package com.thelads.core.v26_2.embedded.emf.utils;

import com.thelads.core.v26_2.embedded.emf.models.parts.EMFModelPartRoot;

public interface IEMFCustomModelHolder {

    default boolean emf$hasModel() {
        return emf$getModel() != null;
    }

    EMFModelPartRoot emf$getModel();

    void emf$setModel(EMFModelPartRoot model);
}
