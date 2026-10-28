# Publishing The Lads Client

The launcher version in `TheLadsLauncher/TheLadsLauncher.csproj` is the single release version. Windows x64 packages use Velopack 1.2.158 (pinned in both NuGet and `.config/dotnet-tools.json`). The update source is the public `ArashYT/TheLadsClient` GitHub repository; no token ships in the launcher.

## Each release

1. Increase the csproj version (for example 1.2.1 to 1.2.2).
2. Write `docs/releases/1.2.2.md`. Explain actual changes, fixes, installation changes, and known limitations. Keep all previous notes. The release script refuses missing or placeholder notes.
3. Commit the intended source and notes, and push the commit. Review the worktree before committing; it may include unrelated work.
4. Create and push a matching tag: `git tag v1.2.2`, then `git push origin v1.2.2`.
5. The Windows release workflow builds the core and launcher, runs launcher tests, packages all runtime and mod files, and creates a **draft** GitHub release with the same notes bundled in the launcher (`Publish-Release.ps1 -Draft`; the workflow never publishes on its own).
6. Verify the draft installer on Windows and run the update smoke test. Review the notes and complete asset list.
7. Sign the draft on the owner's PC: `pwsh ./tools/Sign-Release.ps1 -Version 1.2.2`. It downloads the draft's full package and uploads `lads-update.json` (version, package name, SHA-256) with its signature `lads-update.json.sig`, made with the release key in `$HOME\Documents\TheLads-release-signing-key.pem` (or `$env:LADS_SIGNING_KEY_PATH`). Launchers from 1.9.0 on install nothing without a valid signature, so a release made by anyone else (a stolen GitHub login, a compromised CI action) never reaches players. The key never goes to GitHub or CI; keep an offline backup, because without it no launcher can be updated again. The manifest also carries `contentSha256`, the package's content hash (`tools/Get-PackageContentHash.ps1`), which a launcher checks after rebuilding a delta. When the draft has a delta package, the script first rebuilds it from the previous release's full package (`artifacts/release-check-<prev>` if present, otherwise downloaded) and refuses to sign unless the result has that content hash.
8. Publish the draft as the latest stable release: run `pwsh ./Publish-Release.ps1 -Publish` (which verifies signature assets against pinned keys before un-drafting) or `gh release edit v1.2.2 --draft=false --latest`. This is the point at which users receive it automatically.
9. Post the release patch notes to the Discord `#changelog` channel (`1556193307766427750`): write a concise, player-facing `<version>.md` (with `## Highlights`, `## Added`, `## Changed`, and `## Fixed`, each section under 900 characters so it posts as a single embed) to the bot's `releases/<version>.md` directory, then verify the message appears in `#changelog` and that `<version>` is recorded in `posted_versions` in `discord-bot/state.json`.

Alternative local route (do not run alongside the tag workflow): after a clean committed and pushed tag, sign in using `gh auth login`, then run `pwsh ./Publish-Release.ps1`. It creates a draft by default, never immediately pushes an update to everyone. After signing (step 7), publish with `pwsh ./Publish-Release.ps1 -Publish`. Source, tag, and version must match. Every native build failure stops the release.

For a local review build, run `pwsh ./Publish-Release.ps1 -PrepareOnly`. `-SkipCoreBuild` may additionally reuse local core jars for packaging tests only; it cannot upload a release. The output prints a unique directory under `artifacts/releases` so stale build files cannot leak into a new package. Every route, `-PrepareOnly` included, needs `gh` signed in: the script reads the latest published release to decide on a delta package.

## Required release assets

- `TheLadsClient-win-Setup.exe`: full Windows installer and the download for new users.
- `TheLadsClient-<version>-full.nupkg`: full automatic update package.
- `TheLadsClient-<version>-delta.nupkg`: small update from the previous release. Built only when the latest published release's `release-build.json` has `verifiesContentHash: true`, meaning its launcher checks a rebuilt package by content hash; `Publish-Release.ps1` writes that flag for this release. Otherwise every update is the full package.
- `releases.win.json`: update feed with package hashes, sizes, versions, and notes. Each release's feed lists only its own version; launchers more than one version behind fall back to the full package.
- Additional Velopack-generated assets (including portable ZIP and legacy feed).
- `SHA256SUMS.txt`, `RELEASE_NOTES.md`, and `release-build.json`.
- `lads-update.json`, `lads-update.json.sig`, and `SHA256SUMS.txt.sig`: added by `tools/Sign-Release.ps1` (step 7).

Upload all generated files together to a draft before publishing. Never upload a lone launcher EXE and never replace an already-published version. Fix a bad release by publishing a higher version. Drafts and prereleases are excluded from the stable updater. Network errors may delay checking; failures are logged and retried.

## Automatic update behavior

The installed launcher checks at startup and every ten minutes, downloads packages, verifies their release signature (`TheLadsLauncher/UpdateSignature.cs`; an unsigned or wrongly signed package is deleted and never installed), and restarts itself automatically. It waits while Minecraft, a launch, sign-in, or release notes are active, then retries the idle condition every fifteen seconds. Failed checks/downloads/applies have a two-minute retry delay. Development builds do not self-update; `LADS_SKIP_UPDATE=1` also disables checks. Accounts, worlds, profiles, and settings remain outside the replaced application directory. Launchers read the feed from `github.com/ArashYT/TheLadsClient/releases/latest/download/` (static files, no api.github.com call and so no API rate limit). While Update.exe installs, the update bridge (`LadsUpdateBridge.exe`) holds the launcher's installing screen in place until the new launcher has drawn. 1.10.0 has no bridge, so the first update from 1.10.0 to 1.11.0 still shows Velopack's own install window once.

Older EXE-only installations need this new Setup once. The old updater cannot reliably install the new multi-file package. The Setup creates the new shortcuts; users should launch those afterwards. There is no release asset named `TheLadsLauncher.exe`, intentionally preventing the old updater from overwriting itself with a bare apphost. Old in-app patch notes are preserved verbatim where available; 1.2.0 was a local development version, not a public release.

## Validation and limits

`dotnet test TheLadsLauncher.Tests/TheLadsLauncher.Tests.csproj -c Release` covers updater coordination and offline note history along with existing launcher tests. `pwsh ./tools/Test-AutomaticUpdate.ps1` performs an isolated real package replacement/restart test without touching the user's installed client. This does not replace testing a published GitHub update on a separate Windows installation. Packages are not Authenticode-signed unless signing is configured separately, so Windows may show an initial installer reputation prompt.
