/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.base.Preconditions
 */
package com.thelads.core.features.alwayson.vmp.common.util;

import com.google.common.base.Preconditions;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class SimpleObjectPool<T> {
    private final Function<SimpleObjectPool<T>, T> constructor;
    private final Consumer<T> initializer;
    private final Consumer<T> onRecycle;
    private final int size;
    private Object[] cachedObjects = null;
    private int allocatedCount = 0;

    public SimpleObjectPool(Function<SimpleObjectPool<T>, T> constructor, Consumer<T> initializer, Consumer<T> onRecycle, int size) {
        this.constructor = Objects.requireNonNull(constructor);
        this.initializer = Objects.requireNonNull(initializer);
        this.onRecycle = Objects.requireNonNull(onRecycle);
        Preconditions.checkArgument((size > 0 ? 1 : 0) != 0);
        this.size = size;
    }

    private void init() {
        if (this.cachedObjects == null) {
            this.cachedObjects = new Object[this.size];
            for (int i = 0; i < this.size; ++i) {
                T object = this.constructor.apply(this);
                this.cachedObjects[i] = object;
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public T alloc() {
        T object;
        SimpleObjectPool simpleObjectPool = this;
        synchronized (simpleObjectPool) {
            this.init();
            if (this.allocatedCount >= this.size) {
                T object2 = this.constructor.apply(this);
                return object2;
            }
            int ordinal = this.allocatedCount++;
            object = (T)this.cachedObjects[ordinal];
            this.cachedObjects[ordinal] = null;
        }
        this.initializer.accept(object);
        return object;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void release(T object) {
        SimpleObjectPool simpleObjectPool = this;
        synchronized (simpleObjectPool) {
            this.init();
            if (this.allocatedCount == 0) {
                return;
            }
            this.onRecycle.accept(object);
            this.cachedObjects[--this.allocatedCount] = object;
        }
    }

    public int getAllocatedCount() {
        return this.allocatedCount;
    }
}

