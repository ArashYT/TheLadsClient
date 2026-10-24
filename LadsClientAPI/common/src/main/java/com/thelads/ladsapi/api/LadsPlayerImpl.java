package com.thelads.ladsapi.api;

import com.thelads.ladsapi.session.LadsSession;
import java.util.UUID;

/**
 * Implementation of LadsPlayer interface.
 */
public final class LadsPlayerImpl implements LadsPlayer {
    private final LadsSession session;

    public LadsPlayerImpl(LadsSession session) {
        this.session = session;
    }

    @Override
    public UUID uuid() {
        return session.getHandle().uuid();
    }

    @Override
    public String clientVersion() {
        return session.getLadsVersion();
    }

    @Override
    public String minecraftVersion() {
        return session.getMcVersion();
    }

    @Override
    public void block(String module) {
        session.setApiOverrideModuleState(module, "block");
        resend();
    }

    @Override
    public void allow(String module) {
        session.setApiOverrideModuleState(module, "allow");
        resend();
    }

    @Override
    public void forceOn(String module) {
        session.setApiOverrideModuleState(module, "on");
        resend();
    }

    @Override
    public void limit(String module, String option, double min, double max) {
        session.setApiOverrideOptionLimit(module, option, min, max);
        resend();
    }

    @Override
    public void force(String module, String option, Object value) {
        session.setApiOverrideOptionForce(module, option, value);
        resend();
    }

    @Override
    public void clear(String module) {
        session.clearApiOverrides(module);
        resend();
    }

    @Override
    public void resend() {
        session.sendPolicy();
    }

    public LadsSession getSession() {
        return session;
    }
}
