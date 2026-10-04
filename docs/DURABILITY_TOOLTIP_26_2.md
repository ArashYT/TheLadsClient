# Native durability tooltip (26.x and 1.8.9)

EnhancedToolbars adds a durability line to tool and armour tooltips. It covers the same ground as **Durability Tooltip** (all rights reserved), but the layout, colours and wording are Lads' own. In 1.7.0 the presentation was redesigned so it no longer follows the reference's structure, thresholds or text. No upstream source, translations, textures or libraries are used. The shared logic is `DurabilityPresentation` in common. `NativeDurabilityTooltip` (26.2, 26.3) and `Durability189` (1.8.9) only read the item and the settings.

The settings are unchanged, so saved choices carry over. Detailed Durability turns the line on. Show Max Durability adds the maximum, Colorize Durability turns colours on, and Show Item Attributes controls vanilla's attribute lines.

- **Durability Style**: always one line.
  - *Numbers* shows `Uses left: 1461 / 1561`, or `Uses left: 1461` without the maximum.
  - *Bar* shows a gauge of 20 `|` pips plus a percentage, e.g. `Uses left: |||||||||||||||||||| 93%`. Lit pips take the line colour and spent pips are dark grey. The gauge and the percentage round down, but a tool with one use left still shows one pip and 1%.
  - *Text* shows a condition word for each quarter of the item's life: `Like new` (full), `Good` (75% or more), `Worn` (50% or more), `Battered` (25% or more) and `About to break`.
- **Show Durability Hint** shows or hides the `Uses left:` / `Condition:` label.
- **Durability Color Style**:
  - *Varying* uses the hue of the item's own durability bar: green when new, yellow at half, red when nearly broken.
  - *Base* uses Durability Base Color, which can follow the Lads global colour.
  - *Gold* uses a fixed gold.
  - The label and the maximum always use the base colour. With Colorize off, the whole line uses the base colour.
- **Only Vanilla Tools** limits the line to `minecraft:` items.
- **Show When Full** also shows the line on undamaged items.
- **Excluded Mods** takes entries separated by commas, semicolons or spaces. `mod` hides a whole mod and `mod:item` hides one item. Case is ignored, entries that are not resource ids are dropped, and a mod name never matches by prefix.

On 1.8.9 each colour becomes the nearest of the 16 chat colours.

The integration replaces only vanilla's `item.durability` advanced line. Item IDs, attributes, lore, food overlays, shulker previews and other mods' tooltip lines are kept. Hidden tooltip, damage and maximum-damage components prevent the line. Excluded items and a disabled module keep vanilla's behaviour.

`DurabilityPresentationTest` covers each style, the gauge rounding, the condition ladder, the wear colour scale, every colour choice, out-of-range damage, chat-colour matching and both kinds of exclusion. The runtime probe needs a loaded world, because 26.x item components are not bound at the title screen. Enable it with `-Dthelads.verifyDurabilityTooltip=true`; the marker is `Lads durability tooltip probe END: <n> passed, 0 failed`. It checks real transformed ItemStack tooltips and native text extraction for all three styles, the colours, the filters (including a single excluded item), hidden components and duplicate removal. It then restores the module's options. A failure logs `Lads durability tooltip probe FAILED`. On 1.8.9, Probe150e checks each style's line and takes the `170-tooltip-durability-*` screenshots.
