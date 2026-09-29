# Native crosshair for Minecraft 26.2

The Lads **Crosshair Tweaks** module now owns its renderer and drawing editor. Its earlier Cross/Dot/Square options and saved indices remain valid. The implementation is original code; it contains no copied Custom Crosshair or Crosshair Tweaks sources/assets.

The exact Custom Crosshair reference is `custom-crosshair-mod-v1.6.7-fabric-mc26.2.jar`, Modrinth version `Cecs5C4L`. Its packaged `fabric.mod.json` declares **CC0-1.0**. The earlier audit's ARR statement was incorrect. The separate Microcontrollers `crosshairtweaks-1.5.3+26.2-fabric.jar` declares LGPL-3.0. Public settings and observed reference behavior were inspected to identify functionality.

## Controls

The existing RSHIFT settings page contains appearance, dynamics, context and indicator controls; **Drawing Editor** opens the native drawing screen.

- Cross, Dot, Square, Vanilla sprite, Circle, Triangle, Arrow, camera-oriented Debug axes and Drawn shapes; independent width/height, thickness, scale, rotation and X/Y offsets.
- Main/global color, separate dot/outline colors, elapsed-time rainbow speed and phase, and adaptive inverse scene blending. Inverse-blend opacity attenuates RGB as well as alpha, so intermediate opacity changes the rendered result.
- Dynamic attack recovery and bow/crossbow/trident charge gaps, separate player/hostile/passive target colors. These are local visual changes; they do not alter attack range, hit results or combat actions.
- Ordinary, hidden-HUD, debug, third-person, spectator, ranged-item, throwable and spyglass visibility. Vanilla perspective/spectator eligibility remains the default; spectator menu-provider targets remain valid. Keep Vanilla Debug preserves the real 3D axes unless visibility disables them.
- Independent first/third-person crosshair blend and opacity; independent first/third-person attack-indicator blend and opacity; disable crosshair, hide in containers, disable attack indicator and debug attack indicator. These are the fourteen controls from the separate Crosshair Tweaks reference.
- Concentric cooldown progress rings for ender pearls and chorus fruit even after switching items, plus held items with actual cooldown groups. Tool indicators show the actual remaining durability beside the item icon. Projectile indicators count supported inventory ammunition and loaded crossbow projectiles; creative mode and an eligible Infinity bow are shown explicitly.
- The drawing editor paints/erases continuous strokes, retains 32 undo steps, resizes centered drawings, mirrors, and previews color/scale/rotation. Save writes a bounded bitmap atomically to `config/thelads/crosshair-drawing.json` and selects Drawn. Cancel/Escape leaves the active drawing intact. The bitmap contains only dimensions and monochrome rows; external images and code are not loaded.

If either external mod ID `custom-crosshair-mod` or `crosshairtweaks` is present, the native renderer yields completely. No unmanaged JAR or external configuration is deleted or rewritten. To use the native settings, the external renderer must be absent from that instance.

## Verification

`CrosshairDesignTest` covers ten policy, geometry, opacity, drawing-persistence and resize tests. The opt-in `-Dthelads.verifyCrosshair=true` world probe exercises the transformed Minecraft crosshair method, all nine shapes, actual pipeline selection, attack/debug/context switches, target fixtures, item data and editor input handlers. It restores module settings/recency, camera, attack settings, targeting, inventory, cooldown state and debug-entry state in a finally block without saving mock preferences.

**Final runtime PASS:** `Lads native crosshair probe END: 53 passed, 0 failed` on packaged Core SHA-256 `52D16AD48F6776945F790288D79496EA2B05AFC66E4A78F7462A79D2C0395DB5`. The [complete final game log](../artifacts/verification/native-final-checkpoint/world-pass.log) and [exact JAR](../artifacts/verification/native-final-checkpoint/theladscore.jar) are preserved. This includes the spectator `MISS` guard and the editor resize check for both compact and wide-short viewports. The drawing preview is shown only when the viewport has room above the bottom controls.

The runner requires that success marker and rejects `Lads native crosshair probe FAILED`. All ten `CrosshairDesignTest` tests also pass. These checks establish transformed shape/pipeline extraction and editor handler behavior; they are not a claim of physical mouse input, physical RSHIFT interaction or measured presentation FPS. The combined run separately passed the actual world/GPU probes and captured the [real mods menu](../artifacts/verification/native-final-checkpoint/mods-menu.png).
