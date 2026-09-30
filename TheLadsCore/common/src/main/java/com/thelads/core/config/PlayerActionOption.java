package com.thelads.core.config;
/** A persistent visibility trigger with a transient reading from the current player. */
public final class PlayerActionOption extends BoolOption {
    private boolean detected;
    public PlayerActionOption(String name, boolean enabled) { super(name, enabled); }
    public boolean detected() { return detected; }
    public void detect(boolean value) { detected = value; }
}
