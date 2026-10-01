# The Lads Client 1.4.6 plan

Branch `feature/client-1.4.6` (from 1.4.5 = 991cd3d). Lanes work in worktrees on `work/146-<lane>` and merge back after review.

## Requests (user, 2026-10-01)
Bugs
- B1 1.8.9 Legacy Swing looks different from 26.3's.
- B2 1.8.9 + 26.x: with Legacy Swing on, switching items has no equip (pull-out) animation.
Other
- O1 FancyMenu in modpack mode (launcher writes `B:modpack_mode = 'true';`). This also hides Drippy's title edit button (Drippy shows it only while FancyMenu's overlay is visible).
- O2 Hide the loading screen mod's (Drippy) button on the title screen and everywhere: covered by O1; verify by screenshot.
- O3 Recreate inside Core, hidden (no Lads module, no menu entry), then remove the external jars:
  Capes, Controlling (+Searchables), Entity Culling, ETF, EMF, Fix Book GUI, Hovering Hotbar, Ksyxis, Lazy AI, NBT Autocomplete,
  Optimized Cushions, Quick-Pack, Server Pinger Fixer, Fast IP Ping, Tooltips TXF, World Play Time (Reborn), ImmediatelyFast.
- O4 Remove: GammaUtils, MotionBlur(Plus) (+Satin on 1.21.1), Sound Physics Remastered, ClientSort.
- O5 Armor HUD: helmet on top, boots at the bottom.
- O6 Kill Banner remake: Valorant Rogue (4 colours) and Reaver (base purple, Red, Black/dark red, White) incl. headshot banner,
  matching the real animations. Reference videos: `C:\Users\Arash\Videos\Val skins Previews\` (Reaver/Rogue ... Level 4*.mp4),
  YouTube Hf3G6JpvIl0 (Rogue), m4Ei447hj3c (Reaver), xlN6wZ_do7w (Reaver headshot).
- O7 Pause menu: fullscreen toggle button, top right, standard fullscreen icon.
- O8 Title + pause: Essential's buttons bottom-left above the account name, visible and stable (no breakage after "Essential & extras" and back).
- O9 Title + pause buttons: hover = slightly bigger + glow, animated.

## Embedded mods (O3) conventions
- Code: `TheLadsCore/<adapter>/src/main/java/com/thelads/core/<pkg>/embedded/<modkey>/` (v26_3 uses package `v26_2`).
- Each has its own mixin config `theladscore.embedded.<modkey>.mixins.json` whose plugin extends `embedded.EmbeddedMixinPlugin`,
  and init lines in `embedded.EmbeddedMods.clientInit()` guarded by `EmbeddedMods.active("<original id>")`:
  an installed original jar wins and the embedded copy stays off.
- Upstream config file names kept. No Lads UI, no Mod Menu entry. Licences: MIT/Apache/LGPL/MPL code is ported with notices and
  (LGPL/MPL) its sources in the jar; GPL / ARR / tr7zw-licensed mods (Lazy AI, Tooltips TXF, Entity Culling) are clean-room.
- Lead (not lanes) edits the pack manifests, retired lists, catalog rows and docs.

## Lanes
- A `work/146-etf` (worktree `..\w146a`): ETF + EMF.
- B `work/146-mods` (`..\w146b`): Fix Book GUI, Ksyxis, Server Pinger Fixer, Fast IP Ping, Quick-Pack, World Play Time,
  Hovering Hotbar, Optimized Cushions, ImmediatelyFast, Entity Culling, Lazy AI.
- C `work/146-ui` (`..\w146c`): Controlling + Searchables, NBT Autocomplete, Capes, Tooltips TXF.
- Lead (main checkout): B1, B2, O1/O2, O4, O5, O6, O7, O8, O9, manifests, catalog, docs, version, QA, release.

## Status
- [x] Scaffolding: `embedded.EmbeddedMods` + `EmbeddedMixinPlugin` in all four Fabric adapters.
