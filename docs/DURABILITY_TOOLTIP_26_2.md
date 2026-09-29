# Native durability tooltip for 26.2

EnhancedToolbars now recreates the declared display behavior of **Durability Tooltip 1.1.6-fabric-mc26.2** using original Lads code. The reference is all rights reserved: no upstream Java source, translations, textures or libraries were copied into Lads. Public settings and visible output semantics were checked against the exact pinned artifact, recorded in `artifacts/verification/native-mods-26.2/parity-durability-tooltip-1.1.6.txt`.

The existing controls remain active: Detailed Durability enables the extra display, Show Max Durability controls numeric denominators, Colorize Durability enables reactive colors, and Show Item Attributes controls vanilla attribute tooltips. Seven new controls provide:

- **Numbers / Bar / Text** presentation. Bars contain ten filled/empty text segments; the reference also uses text glyphs, so Minecraft's regular tooltip renderer handles font layout and extraction.
- **Show Durability Hint** to include or omit the label.
- **Varying / Base / Gold** reactive colors and **Durability Base Color**, including the normal Lads global-color option.
- **Only Vanilla Tools** by the actual item's `minecraft` namespace.
- **Show When Full** for undamaged items.
- **Excluded Mods**, a comma-separated namespace list. Whitespace and case are normalized; invalid IDs are ignored. The bounded parser reads up to 4,096 characters.

Varying colors and condition text use the reference's 40% and 10% boundaries. Full durability is pristine; damaged items become slightly damaged, severely damaged and nearly broken. Numeric full durability appears once rather than `maximum / maximum`. Hint text, separators and the denominator retain the base color under varying colors. Disabling Colorize uses the base color throughout.

The native integration replaces only the vanilla `item.durability` advanced entry when adding its own durability display. It preserves item IDs, attributes, lore, food overlays, shulker previews and other custom tooltip components. Hidden tooltip, damage and maximum-damage components prevent the extra display. Excluded items and disabled features retain vanilla behavior, including vanilla's own advanced tooltip.

`DurabilityPresentationTest` passes ten focused tests covering format output, exact thresholds, all color modes, malformed/extreme damage, namespace filtering and full-item visibility. The new opt-in runtime probe requires a loaded world because 26.2 item components are not bound at the title. Enable `-Dthelads.verifyDurabilityTooltip=true`; expected marker is `Lads durability tooltip probe END: 34 passed, 0 failed`. It exercises actual transformed ItemStack tooltips and native text extraction for all three formats, checks filtering/hidden metadata/duplicate removal, and restores module options and recency before reporting success. A failure logs `Lads durability tooltip probe FAILED`.

All 34 checks passed in both [native world checkpoints](NATIVE_RUNTIME_CHECKPOINT_26_2.md), with the exact tested JARs and logs preserved. These checks exercise transformed tooltip output and actual text extraction; final desktop visual review remains separate.
