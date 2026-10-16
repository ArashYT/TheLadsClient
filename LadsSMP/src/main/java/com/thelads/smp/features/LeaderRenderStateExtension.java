package com.thelads.smp.features;

/**
 * Render state extension mixed into HumanoidRenderState for 'zombieimprovements'.
 */
public interface LeaderRenderStateExtension {
    boolean ladssmp$isLeader();
    void ladssmp$setLeader(boolean leader);
}
