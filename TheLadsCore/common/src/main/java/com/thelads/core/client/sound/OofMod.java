package com.thelads.core.client.sound;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.OofModModule;

public final class OofMod {
    public static final String MODULE_NAME = "Oof Mod";
    private static OofModModule module;
    private static volatile boolean active;
    private static volatile int hurtSound;
    private static volatile int deathSound;
    private static volatile float volume = 1.0f;
    private static volatile boolean localOnly = true;

    private static byte[] oofPcm;
    private static byte[] bonkPcm;
    private static byte[] popPcm;
    private static byte[] bellPcm;

    private OofMod() {}

    public static void setModule(OofModModule m) {
        module = m;
        refresh();
    }

    public static void refresh() {
        OofModModule m = null;
        try {
            var mgr = ModuleManager.getInstance();
            if (mgr != null && mgr.getModule(MODULE_NAME) instanceof OofModModule om) {
                m = om;
            }
        } catch (Throwable ignored) {}
        if (m == null) m = module;
        if (m == null) {
            active = false;
            return;
        }
        active = m.isEnabled();
        hurtSound = m.hurtSound.getIndex();
        deathSound = m.deathSound.getIndex();
        volume = (float) (m.volume.getValue() / 100.0);
        localOnly = m.localOnly.get();
    }

    public static boolean isEnabled() { return active; }

    public static void onPlayerHurt(boolean isLocalPlayer) {
        if (!active || (localOnly && !isLocalPlayer)) return;
        playSound(hurtSound);
    }

    public static void onPlayerDeath(boolean isLocalPlayer) {
        if (!active || (localOnly && !isLocalPlayer)) return;
        playSound(deathSound);
    }

    private static void playSound(int soundIndex) {
        byte[] pcm = null;
        switch (soundIndex) {
            case 0 -> pcm = getOofPcm();
            case 1 -> pcm = getBellPcm();
            case 2 -> pcm = getBonkPcm();
            case 3 -> pcm = getPopPcm();
            default -> {}
        }
        if (pcm != null && pcm.length > 0) {
            byte[] buffer = pcm;
            float vol = volume;
            java.util.concurrent.ForkJoinPool.commonPool().execute(() -> {
                playDirectPcm(buffer, 22050, vol);
            });
        }
    }

    private static void playDirectPcm(byte[] pcm, int sampleRate, float vol) {
        try {
            javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(
                sampleRate, 16, 1, true, false
            );
            javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(
                javax.sound.sampled.SourceDataLine.class, format
            );
            if (!javax.sound.sampled.AudioSystem.isLineSupported(info)) return;
            javax.sound.sampled.SourceDataLine line = (javax.sound.sampled.SourceDataLine) javax.sound.sampled.AudioSystem.getLine(info);
            line.open(format);
            if (line.isControlSupported(javax.sound.sampled.FloatControl.Type.MASTER_GAIN)) {
                javax.sound.sampled.FloatControl gain = (javax.sound.sampled.FloatControl) line.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
                float dB = (float) (Math.log10(Math.max(vol, 0.0001f)) * 20.0);
                dB = Math.max(gain.getMinimum(), Math.min(dB, gain.getMaximum()));
                gain.setValue(dB);
            }
            line.start();
            line.write(pcm, 0, pcm.length);
            line.drain();
            line.close();
        } catch (Throwable ignored) {}
    }

    public static synchronized byte[] getOofPcm() {
        if (oofPcm != null) return oofPcm;
        int sampleRate = 22050;
        int length = (int) (sampleRate * 0.35);
        byte[] pcm = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            double t = (double) i / sampleRate;
            double freq = 280.0 - 140.0 * (t / 0.35);
            double envelope = Math.sin(Math.PI * (t / 0.35));
            short sample = (short) (Math.sin(2.0 * Math.PI * freq * t) * envelope * 28000);
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return oofPcm = pcm;
    }

    public static synchronized byte[] getBonkPcm() {
        if (bonkPcm != null) return bonkPcm;
        int sampleRate = 22050;
        int length = (int) (sampleRate * 0.2);
        byte[] pcm = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            double t = (double) i / sampleRate;
            double freq = 440.0 - 300.0 * (t / 0.2);
            double envelope = Math.exp(-t * 20.0);
            short sample = (short) (Math.sin(2.0 * Math.PI * freq * t) * envelope * 30000);
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return bonkPcm = pcm;
    }

    public static synchronized byte[] getPopPcm() {
        if (popPcm != null) return popPcm;
        int sampleRate = 22050;
        int length = (int) (sampleRate * 0.08);
        byte[] pcm = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            double t = (double) i / sampleRate;
            double freq = 120.0 + 600.0 * (t / 0.08);
            double envelope = Math.exp(-t * 35.0);
            short sample = (short) (Math.sin(2.0 * Math.PI * freq * t) * envelope * 26000);
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return popPcm = pcm;
    }

    public static synchronized byte[] getBellPcm() {
        if (bellPcm != null) return bellPcm;
        int sampleRate = 22050;
        int length = (int) (sampleRate * 0.4);
        byte[] pcm = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            double t = (double) i / sampleRate;
            double freq = 880.0;
            double envelope = Math.exp(-t * 8.0);
            short sample = (short) (Math.sin(2.0 * Math.PI * freq * t) * envelope * 24000);
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return bellPcm = pcm;
    }
}
