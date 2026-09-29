# Native Paper Doll — Minecraft 26.2

The native implementation is built into The Lads Core. It yields completely when a manually installed `paperdoll` mod is detected, so there is one renderer and settings owner. The production 26.2 pack retired its external entry after the isolated 19-check runtime probe passed.

Pack baseline: Paper Doll **26.2.2**, Modrinth version `Hati0btw`, Fabric / Minecraft 26.2, official tag commit [d1b1a87c255c421307ac99d104931ecb439d1da3](https://github.com/Fuzss/paper-doll/tree/d1b1a87c255c421307ac99d104931ecb439d1da3). The native port uses official **26.2.3** source at [5968f6f523a2ddc46e5890bd47dc6a5d9bf48b29](https://github.com/Fuzss/paper-doll/tree/5968f6f523a2ddc46e5890bd47dc6a5d9bf48b29), released September 10, 2026. The inspected comparison retains all 26.2.2 behavior and adds model transparency; the native port includes that addition. No unverified future version or external pin update is used.

| Behavior | Native Lads implementation |
| --- | --- |
| Real player | Calls the current local player's actual entity renderer with the frame's partial tick; no dummy entity, geometric stand-in or skin guess. |
| Skin and equipment | Retains native skin, model parts, cape layers, armor, both hands and item render states, including upstream-compatible renderer layers. |
| Animations | Retains actual crouch, swim, crawl, creative-flight, elytra, riding, trident spin, attack and item-use state. |
| Action conditions | Nine independent switches: Sprinting, Swimming, Crawling, Crouching, Creative Flying, Elytra Gliding, Riding, Spin Attacking, Using Items. Dismount grace suppresses the accidental crouch trigger. |
| Timing | Always Display or a configurable action timeout; 0 ticks also means always. Pausing freezes timeout and head easing. Player/world replacement resets transient state. |
| Visibility | Master switch, first-person and third-person switches; HUD-hidden, invisible and spectator states are respected. HUD editing may preview the actual local player without changing the module switch. |
| Head movement | Existing Yaw and Pitch / Yaw Only / None indices retained; Pitch Only appended. Configurable resting angle, maximum yaw and pitch, interpolated yaw decay, north-crossing wrap protection and elytra pitch behavior. |
| Size | Model Scale 1–24, matching upstream's scale × 5 pixels. Minecraft GUI scale applies. |
| Position | Existing saved HUD position, all nine screen anchors, X/Y offsets, screen clamping, real draggable HUD bounds. Dragging changes the anchor to HUD Position and keeps offsets consistent. |
| Opacity | Model Opacity 5–100%, attached only to the extracted doll state. Opaque entity model passes use a translucent texture pass when needed. Existing translucent passes retain their type. |
| World isolation | No player orientation or equipment mutation during production rendering; no world shadows or outline. Normal extracted world states keep full opacity. |

The existing Lads setting names and saved values remain valid, including `Show in First Person`, `Always Display`, `Display Time (ticks)` and `Head Movement`. Existing saved HUD coordinates are reused. Upstream TOML files are preserved; their settings are not silently rewritten or treated as Lads preferences. The legacy Lads defaults display in third person; enable Show in First Person to use the HUD in that view.

The renderer submits the same entity picture-in-picture rendering path as Minecraft inventory rendering. That API ignores the 2D GUI pose, so the Lads adapter explicitly transforms the model bounds and pixel scale to keep the live HUD and editor aligned.

## Verification

- Six local tests cover action timeout, always/zero timing, pause/disable reset, wrapped/clamped/interpolated yaw, dismount grace, all nine anchors, offsets, saved coordinates and editor position conversion.
- With `-Dthelads.verifyIntegrations=true` and the external Paper Doll jar omitted from the isolated instance, a 19-check runtime probe verifies actual avatar extraction, skin, armor, both item render states, pose, unchanged player orientation, camera/master gating, state alpha isolation and mirrored rotation. It restores all temporary equipment and settings in `finally`.
- The runtime marker `Lads paper doll probe END: 19 passed, 0 failed` was recorded in both [world checkpoints](NATIVE_RUNTIME_CHECKPOINT_26_2.md). An actual GPU screenshot also includes the rendered player model. Continuous animation and final desktop layout review remain separate from these automated checks.

## Source and license

Paper Doll code is [MPL-2.0](https://github.com/Fuzss/paper-doll/blob/5968f6f523a2ddc46e5890bd47dc6a5d9bf48b29/LICENSE.md). Upstream assets have a separate all-rights-reserved license and are not copied. The adapted timing/render/submission files retain MPL notices; the full license and notice are included under `assets/theladscore/licenses/`.

Every binary containing the adapted timing code also contains its corresponding Java source. The 26.2 Core JAR additionally includes the complete modified native Paper Doll source directory under `assets/theladscore/sources/paperdoll/`. Recipients can extract the source directly from the JAR using any ZIP reader.
