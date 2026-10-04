package com.thelads.core.client.killbanner;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * One kill count's banner frames ("LKB2", written by tools/killbanner/build_killbanner.py): RGBA frames stored as
 * byte-wise differences from the frame before, in one deflate stream. Frames [0, introEnd] play and introEnd holds;
 * the next exitFrames frames play the way out. Without exit frames, exitIcon and exitRest (PNGs) are the settled
 * icon and the rest of the settled banner, for a drawn way out. "LKB3" adds each frame's icon offset (iconY).
 */
public final class KillBannerStrip {
    public final int width, height, frames, introEnd, exitFrames;
    public final byte[] exitIcon, exitRest;
    private final byte[] packed, iconY;
    private final byte[] frame, delta;
    private final Inflater inflater = new Inflater();
    private int decoded = -1;

    private KillBannerStrip(int width, int height, int frames, int introEnd, int exitFrames, byte[] iconY, byte[] packed,
                            byte[] exitIcon, byte[] exitRest) {
        this.width = width;
        this.height = height;
        this.frames = frames;
        this.introEnd = introEnd;
        this.exitFrames = exitFrames;
        this.iconY = iconY;
        this.packed = packed;
        this.exitIcon = exitIcon;
        this.exitRest = exitRest;
        this.frame = new byte[width * height * 4];
        this.delta = new byte[frame.length];
    }

    public static KillBannerStrip read(InputStream source) throws IOException {
        DataInputStream in = new DataInputStream(source);
        byte[] magic = in.readNBytes(4);
        if (magic.length != 4 || magic[0] != 'L' || magic[1] != 'K' || magic[2] != 'B' || (magic[3] != '2' && magic[3] != '3'))
            throw new IOException("Not a kill banner strip");
        int width = in.readUnsignedShort(), height = in.readUnsignedShort();
        in.readUnsignedShort(); // 60 fps, the only rate the banners use
        int frames = in.readUnsignedShort(), introEnd = in.readUnsignedShort(), exitFrames = in.readUnsignedShort();
        byte[] iconY = magic[3] == '3' ? in.readNBytes(frames) : new byte[frames];
        byte[] packed = in.readNBytes(in.readInt());
        byte[] icon = null, rest = null;
        if (exitFrames == 0) {
            icon = in.readNBytes(in.readInt());
            rest = in.readNBytes(in.readInt());
        }
        if (width == 0 || height == 0 || introEnd + 1 + exitFrames != frames)
            throw new IOException("Malformed kill banner strip");
        return new KillBannerStrip(width, height, frames, introEnd, exitFrames, iconY, packed, icon, rest);
    }

    /** Cell pixels the icon sits below its settled place in a frame (Rogue's 5-kill ring drops in with its icon). */
    public int iconY(int index) { return index >= 0 && index < iconY.length ? iconY[index] : 0; }

    public static KillBannerStrip read(byte[] data) throws IOException {
        return read(new ByteArrayInputStream(data));
    }

    /** RGBA bytes of frame index (row-major, straight alpha). Moving forward is cheap; going back restarts the stream. */
    public synchronized byte[] frame(int index) {
        index = Math.max(0, Math.min(frames - 1, index));
        if (index < decoded || decoded < 0) {
            inflater.reset();
            inflater.setInput(packed);
            java.util.Arrays.fill(frame, (byte) 0);
            decoded = -1;
        }
        try {
            while (decoded < index) {
                int read = 0;
                while (read < delta.length) {
                    int n = inflater.inflate(delta, read, delta.length - read);
                    if (n == 0 && (inflater.finished() || inflater.needsInput()))
                        throw new IllegalStateException("Kill banner strip ended at frame " + (decoded + 1));
                    read += n;
                }
                for (int i = 0; i < frame.length; i++) frame[i] += delta[i];
                decoded++;
            }
        } catch (DataFormatException failure) {
            throw new IllegalStateException("Corrupt kill banner strip", failure);
        }
        return frame;
    }
}
