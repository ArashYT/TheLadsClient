/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.features.alwayson.clientsort.exception;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.network.handler.validate.PayloadResult;

public abstract class PayloadHandlerException
extends Exception {
    public static String GENERIC_MESSAGE = "Unexpected exception, check server logs for more info.";
    public final PayloadResult result;

    public PayloadHandlerException(String message, PayloadResult result) {
        super(message);
        this.result = result;
    }

    public PayloadHandlerException(String message) {
        this(message, PayloadResult.FAILURE);
    }

    public static class InconsistentStateException
    extends PayloadHandlerException {
        public InconsistentStateException(String message) {
            super(message, PayloadResult.INCONSISTENT_STATE);
        }
    }

    public static class InvalidDataException
    extends PayloadHandlerException {
        public InvalidDataException(String message) {
            super(message, PayloadResult.INVALID_DATA);
        }
    }

    public static class UnsupportedOpException
    extends PayloadHandlerException {
        public UnsupportedOpException(String message) {
            super(message, PayloadResult.UNSUPPORTED_OP);
        }
    }
}
