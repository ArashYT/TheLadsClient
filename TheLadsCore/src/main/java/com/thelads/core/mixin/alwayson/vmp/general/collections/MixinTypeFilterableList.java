/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
 *  it.unimi.dsi.fastutil.objects.ObjectArrayList
 *  net.minecraft.util.ClassInstanceMultiMap
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Overwrite
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.thelads.core.mixin.alwayson.vmp.general.collections;

import com.thelads.core.features.alwayson.vmp.common.general.collections.ITypeFilterableList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.ClassInstanceMultiMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={ClassInstanceMultiMap.class}, priority=1005)
public abstract class MixinTypeFilterableList<T>
extends AbstractCollection<T>
implements ITypeFilterableList {
    @Mutable
    @Shadow
    @Final
    private Map<Class<?>, List<T>> byClass;
    @Mutable
    @Shadow
    @Final
    private List<T> allInstances;
    @Shadow
    @Final
    private Class<T> baseClass;

    @Redirect(method={"<init>"}, at=@At(value="FIELD", target="Lnet/minecraft/util/ClassInstanceMultiMap;byClass:Ljava/util/Map;", opcode=181))
    private void redirectSetElementsByType(ClassInstanceMultiMap<T> instance, Map<Class<?>, List<T>> value) {
        this.byClass = new Object2ObjectLinkedOpenHashMap();
    }

    @Redirect(method={"<init>"}, at=@At(value="FIELD", target="Lnet/minecraft/util/ClassInstanceMultiMap;allInstances:Ljava/util/List;", opcode=181))
    private void redirectSetAllElements(ClassInstanceMultiMap<T> instance, List<T> value) {
        this.allInstances = new ObjectArrayList();
    }

    @Redirect(method={"<init>"}, at=@At(value="INVOKE", target="Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;", remap=false))
    private HashMap<?, ?> redirectNewHashMap() {
        return null;
    }

    @Redirect(method={"<init>"}, at=@At(value="INVOKE", target="Lcom/google/common/collect/Lists;newArrayList()Ljava/util/ArrayList;", remap=false))
    private ArrayList<?> redirectNewArrayList() {
        return null;
    }

    @Override
    public Object[] getBackingArray() {
        return ((ObjectArrayList)this.allInstances).elements();
    }

    @Overwrite
    public <S> Collection<S> find(Class<S> type) {
        List<T> cached = this.byClass.get(type);
        if (cached != null) {
            return (Collection<S>) (List) cached;
        }
        if (!this.baseClass.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Don't know how to search for " + String.valueOf(type));
        }
        List list = this.byClass.computeIfAbsent(type, typeClass -> {
            ObjectArrayList ts = new ObjectArrayList(this.allInstances.size());
            for (Object _allElement : ((ObjectArrayList)this.allInstances).elements()) {
                if (!typeClass.isInstance(_allElement)) continue;
                ts.add(_allElement);
            }
            return ts;
        });
        return (Collection<S>) list;
    }
}

