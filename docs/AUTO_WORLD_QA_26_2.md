# Isolated world verification for 26.2

`-Dthelads.verifyAutoWorld=true` enables a game-API QA harness. It is inactive in ordinary launches. It requires the resolved game directory to be the repository's `artifacts/verification/26.2-title` with `TheLadsCore/settings.gradle` present. The exact existing save `saves/Client QA 26_2/level.dat` must resolve inside this isolated directory. No production save is selected or created.

After the title is ready for five seconds, the harness calls Minecraft's `createWorldOpenFlows().openWorld` for that existing save. It does not automate desktop input, change window focus, dismiss upgrade/error dialogs or bypass the Windows lock screen. It removes only Minecraft's ordinary pause screen once the requested world loads. NativeWorldVerification.worldReady() requires the world, a living player, no overlay or screen, and unpaused state.

The harness overrides `pauseOnLostFocus` only in memory. The original value is reinstated around every options save and on close. Render-scale QA may use this validated world readiness without requiring window focus; production input behavior and `NativeFeatures.interactive()` are unchanged. Renderer checks still require actual frame completion, usable GPU attachments and GPU readbacks.

Useful flags with this harness are `thelads.verifyBackgroundPolicies`, `thelads.verifyNativeFeatures`, `thelads.verifyIntegrations` and `thelads.verifyRenderScale`. Omit `thelads.verifyInput` when there is no physical input session. Expected harness markers are `Lads auto-world QA BEGIN`, `OPEN`, and `READY`. A failure logs `Lads auto-world QA FAILED`; an unexpected startup screen times out after 90 seconds without being accepted automatically.

The owning runner can request graceful termination by creating `.lads-qa-stop` directly in the verified game directory. Presence is checked every 250 ms before world readiness and even after a harness error. The game logs `Lads auto-world QA STOP`, restores the pause option and calls `Minecraft.stop()`. The runner removes only its stale request before launching, waits for normal integrated-world saving and shutdown, and may terminate its own process after its bounded timeout. Saves are retained.

After the world has been ready for 15 seconds, a completed frame is read back through Minecraft's Screenshot API and saved as `screenshots/native-world-<timestamp>.png` within the isolated game directory. The runner requires the successful capture marker as well as each requested world probe; it does not equate opening a window with passing these checks.

Run with `$env:LADS_VERIFY_AUTO_WORLD='1'` and `dotnet run --project TheLadsLauncher.Verification -c Release -- 26.2 '<repository root>' --title`. The runner stages a QA-only manifest without the replaced upstream feature JARs, preserving the production manifest, enables the native world probes, and reports any missing or failed marker.

Optional `$env:LADS_VERIFY_CAPTURE_MENU='1'` requests an actual mods-menu frame only after every world/title probe passes. The runner creates `.lads-qa-capture-menu`; the harness consumes that explicit request, opens the real Lads settings screen, waits at least 1.5 seconds across completed frames, captures `native-menu-<timestamp>.png`, and restores the prior screen. It requires the menu-capture success marker before shutdown. It does not simulate a physical Right Shift press.

These checks establish background execution through the actual Minecraft pipeline. They do not establish physical RSHIFT handling, visible display cadence or visual approval of the resulting scene.
