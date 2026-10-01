// Adapted from Controlling 19.0.5 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.controlling.client;

import net.minecraft.network.chat.Component;

public class DisplayableBoolean {
    
    private boolean state;
    private final Component whenTrue;
    private final Component whenFalse;
    
    public DisplayableBoolean(boolean initialState, Component whenTrue, Component whenFalse) {
        
        this.state = initialState;
        this.whenTrue = whenTrue;
        this.whenFalse = whenFalse;
    }
    
    public boolean state() {
        
        return state;
    }
    
    public boolean toggle(){
        state(!state());
        return state();
    }
    
    public void state(boolean state) {
        
        this.state = state;
    }
    
    public Component currentDisplay() {
        
        return state ? whenTrue() : whenFalse();
    }
    
    public Component whenTrue() {
        
        return whenTrue;
    }
    
    public Component whenFalse() {
        
        return whenFalse;
    }
    
}
