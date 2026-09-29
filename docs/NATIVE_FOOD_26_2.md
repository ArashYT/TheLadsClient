# Built-in food overlays for Minecraft 26.2

The AppleSkin catalog entry is implemented inside Lads Core for 26.2. It no longer requires an AppleSkin jar. The implementation adapts AppleSkin 3.0.10's public-domain (Unlicense) 26.2 source at commit `62513191f6a3497447595c2215d466ad1d2bdb92`, with relocated classes and assets, Lads settings, and revised connection/lifecycle handling. The license is embedded at `assets/theladscore/licenses/AppleSkin-Unlicense.txt`.

Reference: https://github.com/squeek502/AppleSkin/tree/62513191f6a3497447595c2215d466ad1d2bdb92

## Behavior and settings

- Food tooltips show hunger and saturation bars, including partial values, harmful-food icons, large-value multipliers, and data-component values. Hidden tooltips and non-edible items remain respected.
- Tooltips can always appear or require Shift. The food component factory coexists with the separate native shulker tooltip factory.
- Current saturation, exhaustion, held-food hunger and saturation restoration, estimated health recovery, offhand food, vanilla icon movement, and overlay opacity have live Lads controls.
- The existing Show Saturation option controls current saturation. Show Saturation Overlay independently controls the extra saturation from held food. Show Food Values controls held-food restoration. Food Tooltips controls tooltip bars.
- Food statistics are available in Minecraft's configurable debug screen while the module is enabled.
- The master module switch disables all food visuals without modifying player hunger, saturation, health, or server gameplay.

## Server data and compatibility

The integrated server sends exact saturation, exhaustion and natural-regeneration state using the AppleSkin wire protocol. The receiver also works with remote servers that supply that protocol. Payloads are sent only to clients advertising all three channels, changes are tracked per actual server-player instance, and state is cleared when a server stops. Client sync state resets on connection changes, and invalid numeric payloads are ignored.

Vanilla remote servers do not continuously synchronize saturation or exhaustion. As with the reference client-only mod, overlays on such servers use the limited client state and health recovery is an estimate. This client cannot make an unmodified remote server reveal exact values. Lads does not change server hunger rules.

A manually installed upstream AppleSkin jar keeps ownership: the built-in overlay and network registration stay inactive, preventing duplicate overlays/channel registration. The managed installer retires only the old Lads-owned, hash-matching AppleSkin jar. The 1.21.11 dependency set remains unchanged. The pinned JEI renderer retains the native food component through its own tooltip conversion, preserving surrounding text and other image components. The relocated implementation does not publish the upstream third-party API namespace.

## Verification

The native runtime probe exercises transformed item tooltip methods, both tooltip factories, actual food data, main/offhand selection, all visual toggles, connection/player lifecycle and packet codec round trips. A separate in-world check waits for actual integrated-server payloads. Temporary probe preferences, inventory, abilities and food values are restored in `finally`.

Minecraft 26.2 binds item components after joining a world, so item extraction checks run there. The verified world checkpoint passed 47 food behavior checks, 14 sprite/factory/master-toggle checks and 6 pinned JEI conversion checks. The integrated server delivered saturation, exhaustion and natural-regeneration payloads through the actual connection. This caught and fixed a native tooltip conflict: cancellable return injections could prevent later callbacks; the food and detail hooks now compose return values. Connection-scoped regeneration state survives player replacement while player-scoped food state resets. Evidence is preserved in `artifacts/verification/native-ports-checkpoint/world-pass.log` and repeated in the TabTweaks checkpoint. See [the runtime report](NATIVE_RUNTIME_CHECKPOINT_26_2.md) for exact JAR hashes and limits.
