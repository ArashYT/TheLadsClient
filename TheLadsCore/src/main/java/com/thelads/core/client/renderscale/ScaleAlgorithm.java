package com.thelads.core.client.renderscale;

public enum ScaleAlgorithm {
    LINEAR("Linear"),
    NEAREST("Nearest");

    private final String name;

    private ScaleAlgorithm(String name) {
        this.name = name;
    }

    public String toString() {
        return this.name;
    }
}
