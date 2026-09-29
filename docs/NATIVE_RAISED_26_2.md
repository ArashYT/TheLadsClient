# Built-in HUD layout for Minecraft 26.2

Raised now includes its complete 26.2 Fabric layout engine inside Lads Core. This adapts the Raised **6.0.0 source revision** by yurisuika at commit `4b6a3b8718316d681c3ae6832cdd19c3174a29a4`, whose [gradle.properties](https://github.com/yurisuika/Raised/blob/4b6a3b8718316d681c3ae6832cdd19c3174a29a4/gradle.properties) declares `mod.version = 6.0.0`. This is intentionally newer source than the previously pinned Raised 5.1.2 binary; it adds groups, per-layer anchors and a separate action-bar layer. Source: https://github.com/yurisuika/Raised/tree/4b6a3b8718316d681c3ae6832cdd19c3174a29a4/versions/26.2-fabric

## Native behavior

- Named groups can be created, renamed and removed. Each has x/y offsets and an editable collection of HUD layers; layers shared across groups receive the sum of their group offsets.
- Each layer has nine anchor choices that determine how group offsets apply. The native layers include the hotbar/survival stack, action bar, chat, scoreboard, effects, player list, titles, boss bars, subtitles, toasts and the reference's unknown overlay hooks.
- The original Lads Distance setting still adds the configured chat-open hotbar lift. The full layout adds Raised's normal group positioning, including its default two-pixel hotbar/action-bar lift.
- Hotbar selector repair supports Replace, Patch, Auto and None. The repair is composed inside the existing SmoothHotbar transform, so repaired pixels follow the animated selection.
- Existing Raised resource-pack texture names are preserved. A resource pack can supply its square selector; Auto otherwise uses the mirrored-row repair.
- RSHIFT opens Lads; Raised's Edit layout action opens the native group/layer editor. RSHIFT or Escape returns to the parent. The separate grave-accent settings key is not registered. Local layout commands remain available.
- The master Raised switch disables layout transforms and selector repairs immediately. A separately installed upstream Raised jar retains ownership and its existing Lads external settings page.

## Persistence and source availability

The native editor uses `config/lads-raised.json`. On its first run it imports an existing `config/raised.json` without changing that original. Saves use an atomic file replacement when supported. Invalid native layout data is preserved in a dated `.invalid-*` copy before restoring usable defaults; oversized or incomplete files cannot crash a render-frame lookup.

Raised 5.1.2 layouts with `displacement`, `direction`, `sync` and `resource.texture` migrate to shared groups, individual anchors and the corresponding selector repair preference. The previous binary's `Translate.getX/Y` bytecode confirms that sync uses only the direct target's raw displacement and the calling layer's own direction. Migration preserves that behavior for chains, cycles, missing targets and negative offsets; it does not recursively flatten links. Layers with the same source share an editable group. Renamed boss bar, player list, scoreboard and unknown layers retain their movement, while the new action-bar layer inherits the old hotbar transform. The exact imported file is also retained as `.pre-migration-*` before the native copy is saved in the new format.

The port retains LGPL-3.0-or-later notices, the LGPL/GPL license texts, corresponding modified Java source, and rebuilding/relinking instructions inside the Core JAR under `META-INF/lads-sources/raised`. The preserved Raised resource namespace exists for pack compatibility; Raised is not a separate Fabric mod in the native pack.

The relocated native layer API can be used by Lads integrations. Other mods that explicitly require the original Raised mod ID/API still need that upstream dependency, in which case the native implementation yields to it. Fabric overlay hooks have the same injection boundaries as the reference; this is not a promise that every arbitrary third-party overlay can be repositioned.

## Verification

The opt-in title probe exercises composed group offsets, all anchors, matrix restoration, master disable, actual selector sprite extraction, the real editor action and compact widgets, RSHIFT parent navigation, atomic file round trips and preservation/recovery of invalid data. Migration fixtures include the exact prior ten-layer QA file, direct sync chains and cycles, missing targets, signed offsets, renamed layers, shared editing, selector preference and an idempotent save/reload. It restores the original layout, file path, selected group, screen and module preferences.

The required build passed. The fresh Minecraft 26.2 runtime passed all 52 layout, actual editor, resize and migration checks; evidence is in `artifacts/verification/native-mods-26.2/native-ports-world-second.log`. Visual checks of actual HUD movement and chat interaction are still required; isolated extraction does not prove every displayed layer or click target.
