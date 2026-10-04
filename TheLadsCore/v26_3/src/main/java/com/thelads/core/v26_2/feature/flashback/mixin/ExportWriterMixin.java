package com.thelads.core.v26_2.feature.flashback.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.combo_options.VideoCodec;
import com.moulberry.flashback.exporting.EncoderQuirks;
import com.moulberry.flashback.exporting.ExportSettings;
import com.moulberry.flashback.exporting.FlashbackFFmpegFrameRecorder;
import com.thelads.core.modules.FlashbackModule;
import com.thelads.core.v26_2.feature.flashback.NativeFlashback;
import java.util.Arrays;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Faster Flashback exports, decided where Flashback opens its FFmpeg encoder (every export, PNG sequences included, goes through
 * this writer): OpenH264 moves to a GPU encoder that works here, PNG drops the alpha of opaque frames and encodes frames in parallel.
 */
@Mixin(targets = "com.moulberry.flashback.exporting.AsyncFFmpegVideoWriter")
abstract class ExportWriterMixin {
    @Shadow private ExportSettings settings;
    @Shadow private boolean started;
    /** The encoder this export uses instead of the one asked for, while one is chosen. */
    @Unique private String lads$encoder;

    @WrapMethod(method = "tryStart")
    private void lads$gpuEncoder(int sourceFormat, Operation<Void> original) {
        lads$encoder = started ? lads$encoder : lads$promoted(settings);
        if (started || lads$encoder == null) { original.call(sourceFormat); return; }
        try {
            original.call(sourceFormat);
            NativeFlashback.LOGGER.info("Flashback Settings: exporting with {} instead of {}", lads$encoder, settings.encoder());
        } catch (Throwable failure) {
            // Fail-safe: the export starts again exactly as asked.
            NativeFlashback.LOGGER.warn("Flashback Settings: {} did not start; exporting with {}", lads$encoder, settings.encoder(), failure);
            lads$encoder = null;
            started = false;
            original.call(sourceFormat);
        }
    }

    /** OpenH264 to a GPU encoder Flashback found working, unless that encoder would resize this frame. */
    @Unique
    private static String lads$promoted(ExportSettings settings) {
        if (!NativeFlashback.gpuEncoder()) return null;
        String gpu = FlashbackModule.promotedEncoder(settings.encoder(), Arrays.asList(VideoCodec.H264.getEncoders()));
        if (gpu == null) return null;
        int width = settings.resolutionX(), height = settings.resolutionY();
        boolean fits = Math.min(width, height) >= EncoderQuirks.minimumFrameSize(gpu) && Math.max(width, height) <= EncoderQuirks.maximumFrameSize(gpu)
            && (long) width * height <= EncoderQuirks.maximumFrameArea(gpu);
        return fits ? gpu : null;
    }

    @WrapOperation(method = "tryStart", at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/exporting/ExportSettings;encoder()Ljava/lang/String;"))
    private String lads$usedEncoder(ExportSettings exportSettings, Operation<String> original) {
        return lads$encoder != null ? lads$encoder : original.call(exportSettings);
    }

    @WrapOperation(method = "tryStart", at = @At(value = "INVOKE",
        target = "Lcom/moulberry/flashback/exporting/PixelFormatHelper;getBestPixelFormat(Ljava/lang/String;IZ)I"))
    private int lads$opaquePng(String encoder, int source, boolean transparent, Operation<Integer> original) {
        // AV_PIX_FMT_RGB24: the frames are opaque, so the alpha channel is a quarter of the work and carries nothing.
        return !transparent && "png".equals(encoder) && NativeFlashback.fasterExports() ? 2 : original.call(encoder, source, transparent);
    }

    @WrapOperation(method = "tryStart", at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/exporting/FlashbackFFmpegFrameRecorder;start()V"))
    private void lads$tune(FlashbackFFmpegFrameRecorder recorder, Operation<Void> original) {
        String encoder = lads$encoder != null ? lads$encoder : settings.encoder();
        if (NativeFlashback.fasterExports()) {
            int threads = FlashbackModule.encoderThreads(encoder, Runtime.getRuntime().availableProcessors());
            if (threads > 0) recorder.setVideoOption("threads", Integer.toString(threads));
            if ("png".equals(encoder)) recorder.setVideoOption("compression_level", Integer.toString(NativeFlashback.module().pngCompression.getIntValue()));
        }
        original.call(recorder);
    }

    /** How long the encoder takes to drain and close after the last frame (Flashback's "Finalizing video"). */
    @WrapMethod(method = "finish")
    private void lads$finishTime(Consumer<String> progress, Operation<Void> original) {
        long start = System.nanoTime();
        original.call(progress);
        NativeFlashback.LOGGER.info("Flashback export finalized in {} ms", (System.nanoTime() - start) / 1_000_000);
    }
}
