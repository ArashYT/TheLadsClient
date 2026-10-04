# Better Resolution (native RenderScale) — Minecraft 26.2

1.7.0 renamed the RenderScale module to **Better Resolution** (module id `BetterResolution`); saved RenderScale settings carry over, and the same pipeline runs on 26.2, 26.3 and 1.8.9. It is Lads' own remake of the Better Resolution mod's features: no upstream code. It stands down while an external Better Resolution jar is loaded.

Lads renders the world into its own color/depth target at the selected resolution, then composites that image into Minecraft's original target before the HUD and menus render. Window size, GUI scale, mouse coordinates and the presentation target remain native. The world pass includes terrain, entities, particles, weather, the held item, outlines and spectator post effects.

## Controls

| Control | Behavior |
| --- | --- |
| Enabled | Applies world scaling. Disabled releases the extra target and restores the native outline attachment. |
| Preset | Custom uses Scale; Ultra Performance is 50%, Balanced 75%, Quality 85%, Super Sampling 150%. |
| Scale | Custom world width and height, 50–200% in 5% steps; greyed out unless the preset is Custom. Half resolution uses one quarter of the native pixel count; 200% uses four times the pixels. |
| Algorithm | Smooth (default for new settings) is a ringing-free bicubic reconstruction that also blends along strong edges, softening stair steps; Sharp holds world pixels flat with a narrow blend between them plus contrast-adaptive sharpening (no blur); Linear blends adjacent source pixels; Nearest preserves individual source pixels. All run in one GPU fullscreen pass; Smooth and Sharp share `assets/theladscore/shaders/include/world_upscale.glsl` and fall back to Linear if their shader cannot compile. |
| Dynamic Resolution | Adapts between Min Scale and the selected preset/custom ceiling using smoothed elapsed frame cadence. Changes are limited to five percentage points per second with a dead band around the target. |
| Target FPS | The adaptive resolution target: 30, 60, 90, 120 or 144. Unlimited uses a fixed selected scale. This control does not set Minecraft's frame limiter. |
| Min Scale | Dynamic lower bound, clamped to the selected ceiling. Default 50%. Older saved minimum values remain respected. |

Dynamic scaling stops adapting while paused, unfocused or minimized and ignores long loading hitches. It cannot guarantee the target frame rate or fix CPU/server bottlenecks. At 100% with dynamic resolution off, the extra target and composite are bypassed.

## Renderer integration

`RenderScaleMixin` switches `GameRenderer.mainRenderTarget` only for the world section of `render`. The existing frame graph derives transparency, particle and weather target dimensions from that target. The persistent outline attachment is resized to match. Global screen uniforms are set for each section, while `WindowRenderState` and GUI projection stay unchanged. The original target is restored before GUI rendering and in a `finally` block if another renderer throws.

The composite uses Minecraft's unblended fullscreen pipeline and sampler cache; it does not inject raw OpenGL calls or replace Sodium terrain code. The bundled Sodium renderer's hooks were inspected against this boundary. Actual coexistence still requires the runtime test below. Resize/fullscreen changes dispose the old world target, and the next world frame creates the correct dimensions. Disable, world unload and shutdown release owned textures. Shutdown does not recreate vanilla attachments.

Exordium remains separate and unavailable: correctly caching GUI layers requires invalidation for input, chat, tooltip/item rendering, animation, blur, resize and resource reload. A whole-game FPS limit is not an implementation of that feature.

## Verification

- Required multi-version `gradlew.bat build deploy -x test` passes.
- `RenderScalePolicyTest` (common, run by `:common:test`) passes the same 22 sizing and adaptive-policy checks. These do not establish working graphics.
- Actual GPU/world verification is opt-in with `-Dthelads.verifyRenderScale=true` in `artifacts/verification/26.2-title` only. It does not change module settings or save temporary preferences.
- The probe waits for an unpaused world with no screen, then runs six short stages: 50% linear, 50% nearest, 150% supersampling, disabled, 100%, and bounded dynamic scaling. Interactive runs require focus. The strictly isolated [automatic world harness](AUTO_WORLD_QA_26_2.md) can also verify the GPU behind a locked desktop. Opening a screen during an active probe reports failure and ends the temporary override.
- Checks inspect actual color/depth/outline dimensions, native GUI/presentation restoration, repeated world composites, target disposal and dynamic bounds. A separate GPU readback checks red/blue pixels to prove full-target coverage and distinct linear/nearest sampling.
- Both [native world checkpoints](NATIVE_RUNTIME_CHECKPOINT_26_2.md) recorded `Lads render scale probe END: 61 passed, 0 failed`, with over 200 world frames per stage. A failure prints `Lads render scale probe FAILED`. The probe also checks the persistent outline attachment outside the frame graph; its transient public getter is not used for lifecycle management.
- Physical window resize, fullscreen and visual comparison of text sharpness should also be checked in the QA game. GPU readback and attachment checks are not a frame-rate benchmark.

No verification flag is needed for normal use. This implementation targets Minecraft 26.2; later releases need a separate renderer compatibility check.
