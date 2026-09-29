# Native-only Lads menu and red theme — 2026-09-10

The Lads settings catalog now contains only modules registered by an active native integration through `ModuleSupport.registerBuiltIn`. Installed third-party modules and unavailable placeholders are excluded before categories, enabled-only, favorites, or search run. The installed mods and their Minecraft Mod Menu entry remain available; the pack manifest is unchanged by this UI change.

The common menu no longer has an Engines category, Working/Show all switch, external engine cards, adapter settings page, or Open mod settings action. A native module becoming unavailable or external invalidates cached entries and closes its stale details page. This does not change the module's saved enabled state, favorites, options, or metadata. Returning from a native action editor refreshes catalog filtering. `LadsSettingsScreen.visibleModuleNames()` supplies an immutable read-only snapshot for runtime verification.

Ownership comes from the actual integration, rather than module names, saved toggles, or the presence of a settings adapter. Minecraft 26.2 and 1.21.11 already register their native hooks. The old 1.21.1 initializer does not register these capabilities, so its catalog remains limited until actual hooks are verified and registered; this change does not claim feature parity for that legacy target.

## Theme

`LadsPalette` is shared by the menu, HUD editor, title theme, More screen, and account screen. The primary colors match `TheLadsLauncher/MainWindow.axaml`: #8B0000 normal, #B00000 hover, #600000 pressed. The background is #070709; panels and cards use dark red tones, #FF6666 accents, and pale text for contrast. The existing elapsed-time animation and reduced-motion behavior is preserved. Account operations and launcher handoff behavior are unchanged.

## Verification

Targeted common suites passed on 2026-09-10: 10 ModsMenu tests, 12 IntegratedSettings tests, 4 TitleScreenTheme tests, and 1 AccountSwitcher test; 27 passed, zero failures/errors. Tests include hidden external modules across search/favorites/enabled/categories, preservation of hidden module preferences, ownership changes invalidating cached results and stale actions, working native detail controls after unrelated registrations, Unicode/clipboard search, scroll bounds, and small-window layouts. Existing engine adapter tests remain to validate retained services independently of the removed Lads UI route. Title drawing stays bounded at 4K and does not fetch network/skin data.

Full target build/deploy and the actual 26.2 red-menu frame/count are pending the parent task's combined QA. Previous native feature runtime evidence remains preserved in its existing checkpoint directories; these common tests are not a substitute for that fresh runtime check.
