/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.features.alwayson.clientsort.network.handler.validate;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.util.Localization;

public enum PayloadResult {
    INCONSISTENT_STATE(4, Localization.translationKey("payloadResult", "failure.inconsistentState")),
    INVALID_DATA(3, Localization.translationKey("payloadResult", "failure.invalidData")),
    UNSUPPORTED_OP(2, Localization.translationKey("payloadResult", "failure.unsupportedOp")),
    FAILURE(1, Localization.translationKey("payloadResult", "failure")),
    SUCCESS(0, Localization.translationKey("payloadResult", "success")),
    UNKNOWN(-1, Localization.translationKey("payloadResult", "unknown"));

    public final int code;
    public final String translationKey;

    private PayloadResult(int code, String translationKey) {
        this.code = code;
        this.translationKey = translationKey;
    }

    public static boolean isSuccess(int code) {
        return code == PayloadResult.SUCCESS.code;
    }

    public boolean isSuccess() {
        return this.code == PayloadResult.SUCCESS.code;
    }

    public boolean isUnknown() {
        return this.code == PayloadResult.UNKNOWN.code;
    }

    public static PayloadResult get(int code) {
        for (PayloadResult err : PayloadResult.values()) {
            if (code != err.code) continue;
            return err;
        }
        return UNKNOWN;
    }
}
