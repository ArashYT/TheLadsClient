package com.thelads.ladsapi.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thelads.ladsapi.session.Features;
import com.thelads.ladsapi.session.LadsSession;
import com.thelads.ladsapi.session.Wire;
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
    public boolean verified() {
        return session.isVerified();
    }

    @Override
    public boolean can(String capability) {
        return session.hasCap(capability);
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
        public LadsScope silent(boolean silent) {
            session.setScopeSilent(name, silent);
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

    @Override
    public void spectate(UUID target, String name, boolean canSwitch) {
        if (target == null) {
            spectateEnd();
            return;
        }
        session.sendSpectate("pov", target, name, canSwitch);
    }

    @Override
    public void spectateFree(boolean canSwitch) {
        session.sendSpectate("free", null, null, canSwitch);
    }

    @Override
    public void spectateEnd() {
        session.sendSpectate("off", null, null, false);
    }

    public LadsSession getSession() {
        return session;
    }

    // ---- Minigame API ----

    private Features f() { return session.features(); }

    @Override
    public LadsInput.Device inputDevice() { return session.getDevice(); }

    @Override
    public LadsInput.Pad padType() { return session.getPad(); }

    @Override
    public String glyph(String kbm, String xbox, String playstation, String nintendo) {
        if (session.getDevice() != LadsInput.Device.GAMEPAD) return kbm;
        return switch (session.getPad()) {
            case PLAYSTATION -> playstation;
            case SWITCH -> nintendo;
            case XBOX, GENERIC -> xbox;
            default -> kbm;
        };
    }

    @Override
    public boolean rumble(double low, double high, int ms) {
        JsonObject o = Wire.obj("rumble");
        o.addProperty("low", Wire.round(Wire.num(low, 0, 1, 0), 3));
        o.addProperty("high", Wire.round(Wire.num(high, 0, 1, 0), 3));
        o.addProperty("ms", Math.max(1, Math.min(2000, ms)));
        return f().send("rumble", o);
    }

    @Override
    public CameraBuilder camera() { return new CameraBuilder(f()); }

    @Override
    public boolean screenFx(ScreenFx fx) {
        JsonObject json = fx == null ? null : fx.toJson();
        if (json == null) return false;
        if (json.has("flash")) {
            JsonObject kept = json.deepCopy();
            kept.remove("flash");
            if (kept.size() > 1) return f().keep("fx", "fx", kept, json, kept.get("ms").getAsLong(), true, Features.countdown("ms", "fade_ms"));
            return f().send("fx", json); // a flash alone plays once and changes no running effect
        }
        return f().keep("fx", "fx", json, json.get("ms").getAsLong(), true, Features.countdown("ms", "fade_ms"));
    }

    @Override
    public boolean clearScreenFx() { return f().remove("fx", "fx", Wire.obj("fx")); }

    @Override
    public boolean flashScreen(String color, int ms) { return screenFx(ScreenFx.create().flash(color, ms)); }

    @Override
    public boolean lockInput(InputLock lock, int ms) {
        JsonObject json = lock == null ? null : lock.toJson(ms);
        if (json == null) return unlockInput();
        return f().keep("lock", "lock", json, json.get("ms").getAsLong(), false, Features.countdown("ms"));
    }

    @Override
    public boolean unlockInput() { return f().remove("lock", "lock", Wire.obj("lock")); }

    @Override
    public Waypoints waypoints() { return new Waypoints(f()); }

    @Override
    public Telegraphs telegraphs() { return new Telegraphs(f()); }

    @Override
    public Hud hud() { return new Hud(f()); }

    @Override
    public Audio audio() { return new Audio(f()); }

    @Override
    public Run run() { return new Run(f(), () -> session.getManager().nowMs()); }

    @Override
    public boolean gameProfile(String name) {
        String n = Wire.text(name, 32);
        if (n == null || n.isBlank()) return f().remove("profile", "profile", Wire.obj("profile"));
        JsonObject o = Wire.obj("profile");
        o.addProperty("name", n);
        return f().keep("profile", "profile", o, 0, true, null);
    }

    @Override
    public boolean actions(java.util.List<ServerAction> actions) {
        if (!f().ok("action")) return false;
        JsonArray arr = new JsonArray();
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        if (actions != null) {
            for (ServerAction a : actions) {
                if (a == null || arr.size() >= 8) continue;
                String id = Wire.id(a.id(), 24);
                if (id == null || !ids.add(id)) continue;
                JsonObject j = new JsonObject();
                j.addProperty("id", id);
                j.addProperty("name", Wire.text(a.name() == null || a.name().isBlank() ? id : a.name(), 24));
                if (a.key() != null && a.key().matches("[a-zA-Z0-9._-]{1,48}")) j.addProperty("key", a.key());
                if (a.pad() != null && PADS.contains(a.pad())) j.addProperty("pad", a.pad());
                arr.add(j);
            }
        }
        JsonObject o = Wire.obj("actions");
        o.add("actions", arr);
        f().setActionIds(ids);
        if (arr.size() == 0) {
            boolean had = f().has("actions");
            f().remove("action", "actions", null);
            return !had || f().send("action", o);
        }
        return f().keep("action", "actions", o, 0, true, null);
    }

    private static final java.util.Set<String> PADS = java.util.Set.of("a", "b", "x", "y", "lb", "rb", "lt", "rt", "ls", "rs",
            "dpad_up", "dpad_down", "dpad_left", "dpad_right");

    @Override
    public boolean dialog(Dialog dialog, Dialog.Callback callback) {
        JsonObject json = dialog == null ? null : dialog.toJson();
        if (json == null) return false;
        return f().dialog(dialog.id(), json, dialog.clampedMs(), dialog.buttonIds(), callback);
    }

    @Override
    public boolean closeDialog(String id) {
        String did = Wire.id(id, 32);
        return did != null && f().closeDialog(did);
    }

    @Override
    public void clearMinigame() {
        Features f = f();
        camera().off();
        clearScreenFx();
        unlockInput();
        waypoints().clear();
        telegraphs().clear();
        hud().clear();
        audio().stopAll();
        Run r = run();
        r.reset(null);
        r.stopGhosts();
        gameProfile(null);
        actions(java.util.List.of());
        f.closeAllDialogs();
    }
}
