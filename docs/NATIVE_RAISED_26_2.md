# Raised (Lads remake) - 1.8.9, 26.2, 26.3

Since 1.7.0 Raised is original Lads code, written from the Raised mod's public feature list and its observed behaviour; the earlier adapted copy of Raised 6.0.0 (its screens, commands, layer registry and config file) was removed with its LGPL notice and source bundle.

## What it does

The `Raised` module (on by default) has three sliders, all in GUI pixels:

| Option | Default | Moves |
| --- | --- | --- |
| Hotbar | 2 | The hotbar and everything on it (hearts, hunger, armour, air, XP bar and level, mount health, held item name) and the action bar, up off the bottom edge as on Bedrock. |
| Distance | 14 | The same group, further up while chat is open, so the chat box does not cover it. (Its 1.6.0 name and value.) |
| Chat | 0 | Chat, drawn and clicked at the raised position. |

Once the hotbar is off the edge, the hotbar selection frame gets its missing last row (26.x: its top row drawn again under it unless something already drew it 24 tall; 1.8.9: all 24 rows of the frame drawn). Lads HUD elements attached to the hotbar (Armor HUD) follow the lift through `LadsGraphics.hotbarLift()`. On 26.x the embedded Hovering Hotbar still adds its own 2 px on top, as in 1.6.0.

Not remade: Raised's free groups, per-layer anchors, x offsets and the boss bar, scoreboard, effects, tab list, title, subtitle and toast layers. Lads already positions its own Boss Bar, Scoreboard and Potion Effects HUD modules in the HUD editor.

## Code

- `common` `RaisedModule`: the options and the lift rules (`RaisedModuleTest`).
- 26.x `Raised26` with `NativeHudMixin` (hotbar group, action bar, selection frame) and `ClientToolsChatMixin.ladsRaiseChat` (chat lays out above a shorter screen, so drawing, hover and clicks agree). Stands down while the `raised` mod is installed (listed as that mod instead).
- 1.8.9 `Raised189`: Forge `RenderGameOverlayEvent` Pre/Post around the hotbar parts and the chat event's position; `GuiIngameForgeMixin` (held item name, action bar), `GuiNewChatMixin` (chat clicks), `GuiIngameMixin` (selection frame).

## 1.6.0 settings

`Distance` and the module switch keep their saved values. On 26.x a 1.6.0 layout file (`config/lads-raised.json`) is read once: the summed vertical offset of the groups holding the hotbar and chat (applied by their anchors) becomes `Hotbar` and `Chat`, and the file is kept as `lads-raised.json.migrated`. The default 1.6.0 layout gives Hotbar 2, the same look.

## Verification

Unit: `RaisedModuleTest`. 26.x: `Lads raised title probe` (module, defaults, mixins applied) and the `LADS_VERIFY_CAPTURE_RAISED` world capture (`RaisedDollCapture`: default, chat open, changed values). 1.8.9: `RaisedDollProbe189` in the self-test (`LADS_VERIFY_189_ONLY=raised` runs it alone), including a pointer on the raised chat line.
