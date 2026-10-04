# Paper Doll (Lads remake) - 1.8.9, 26.2, 26.3

Since 1.7.0 the paper doll is original Lads code, written from the Paper Doll mod's public feature list (a small live model of the player on the HUD while doing things) and the Lads module's existing options; the earlier MPL-licensed adapted files, their notice and source bundle were removed.

## What it does

`PaperdollHudElement` (movable and scalable in the Lads HUD editor, with the module's Anchor and X/Y offsets) draws the actual local player - skin, armour, held items and its current pose (crouching, swimming, riding, flying, eating) - turned toward the middle of the screen by `Default Rotation`. It shows while an enabled trigger happens and for `Display Time (ticks)` after it stops (0: only while it happens), or always with `Always Display`; `Show in First Person` / `Show in Third Person` follow the camera. The head leans into turns (up to `Maximum Yaw`, easing back) and follows the player's pitch (within `Maximum Pitch`) as `Head Movement` says. `Model Opacity` fades it. The HUD editor always previews it, opaque.

Triggers are the module's existing switches (Sprinting, Swimming, Crawling, Crouching, Creative Flying, Elytra Gliding, Riding, Spin Attacking, Using Items, plus Walking to Spectating; 26.x also lists every player pose and state it finds). 1.8.9 has no crawling, gliding or spin attack, so those never fire there; Swimming is moving in water.

## Code

- `common` `PaperDoll`: timing, trigger reading and the angles (`PaperDollTest`); `PaperdollModule` and `PaperdollHudElement` as before.
- 26.x `PaperDoll26`: the player's own render state with the doll's angles, drawn like the inventory doll (`GuiGraphicsExtractor.entity`); opacity is the picture's blit alpha (`AutohideElementsMixin`, `AutohidePictureMixin`), inside any Autohide fade. `PlayerActions` reads the triggers. Stands down while the `paperdoll` mod is installed.
- 1.8.9 `PaperDoll189`: the player drawn with its angles set only for the draw and put back; opacity by constant-alpha blending.

## Verification

Unit: `PaperDollTest`, `PaperdollHudTest`. 26.x: `Lads paper doll probe` (visibility rules, avatar state, angles, untouched player, opacity) and the `LADS_VERIFY_CAPTURE_RAISED` world capture (sprint, sneak, fly, eat, opacity, HUD editor). 1.8.9: `RaisedDollProbe189` with the same shots.
