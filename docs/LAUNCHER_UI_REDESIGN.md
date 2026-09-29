# Launcher interface redesign

The launcher now uses a charcoal desktop layout with a narrow, grouped sidebar, vector navigation icons, restrained theme accents, and a dedicated Play page. The block landscape is drawn in Avalonia; it needs no image download. Settings are grouped into General, Memory & Java, Paths & services, and Microsoft sign-in, with a persistent save action and confirmation.

The existing account, profile, launch, skin/cape editor, mod/resource-pack, file, gallery, and log handlers remain connected. Theme presets now update the shared control accents and owned dialogs. Particles remain configurable, with lower opacity and confinement to the Play illustration. Window resizing, aspect lock, reset, tray behavior, and UI scale remain available.

## Validation, September 28, 2026

- Initial layout audit retained all 154 named controls and 69 distinct XAML event hookups. The concurrent updater work subsequently replaced the old installation overlay and three manual install/update handlers with the package updater and release-notes action. Those removals are not part of the UI redesign.
- Launcher tests: 133 passed, zero failed. Results: `artifacts/verification/launcher-ui/tests/launcher-ui-final.trx`.
- Final self-contained Windows x64 publication succeeded with zero warnings/errors, to `artifacts/launcher-ui`.
- Used the actual desktop application with an isolated `THELADS_DIR` and updates disabled. Inspected Play, profiles, accounts, skin-editor controls, grouped settings, installed mods, live Modrinth search results, gallery, files, and logs. Verified theme selection/save feedback and 125% scale. Inspected the published build at its minimum window width and its game-start splash using the dedicated preview mode.
- No live Microsoft authentication, game launch, mod installation, skin upload, or Imgur upload was performed for this visual redesign. Existing service coverage and retained event handlers are not a substitute for those end-to-end checks.

Run `artifacts/launcher-ui/TheLadsLauncher.exe` to use the redesigned build. A normal launch uses the existing user data directory; QA used a separate directory. The already-installed launcher is not overwritten by this publication.
