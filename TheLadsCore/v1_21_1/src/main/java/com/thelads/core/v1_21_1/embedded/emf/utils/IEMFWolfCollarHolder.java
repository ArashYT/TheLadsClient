package com.thelads.core.v1_21_1.embedded.emf.utils;

import net.minecraft.client.model.WolfModel;
import net.minecraft.world.entity.animal.Wolf;

public interface
    IEMFWolfCollarHolder<T extends Wolf>
{

    default boolean emf$hasCollarModel() {
        return emf$getCollarModel() != null;
    }

    WolfModel<T>
        emf$getCollarModel();

    void emf$setCollarModel(
            WolfModel<T>
            model);


}
