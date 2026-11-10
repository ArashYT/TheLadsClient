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
    public LadsScope scope(String name) {
        String key = LadsSession.scopeKey(name);
        if (session.enterScope(key)) resend();
        return new Scope(key);
    }

    @Override
    public java.util.List<String> scopes() {
        return session.scopeNames();
    }

    private final class Scope implements LadsScope {
        private final String name;

        Scope(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public LadsScope block(String module) {
            session.setScopeModuleState(name, module, "block");
            return sent();
        }

        @Override
        public LadsScope allow(String module) {
            session.setScopeModuleState(name, module, "allow");
            return sent();
        }

        @Override
        public LadsScope forceOn(String module) {
            session.setScopeModuleState(name, module, "on");
            return sent();
        }

        @Override
        public LadsScope limit(String module, String option, double min, double max) {
            session.setScopeOptionLimit(name, module, option, min, max);
            return sent();
        }

        @Override
        public LadsScope force(String module, String option, Object value) {
            session.setScopeOptionForce(name, module, option, value);
            return sent();
        }

        @Override
        public LadsScope clear(String module) {
            session.clearScopeRules(name, module);
            return sent();
        }

        @Override
        public LadsScope expireIn(long seconds) {
            session.expireScope(name, seconds);
            return this;
        }

        @Override
        public void end() {
            if (session.endScope(name)) resend();
        }

        private LadsScope sent() {
            resend();
            return this;
        }
    }

    @Override
    public void resend() {
        session.sendPolicy();
    }

    public LadsSession getSession() {
        return session;
    }
}
