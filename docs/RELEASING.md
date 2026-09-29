# Publishing The Lads Client

The launcher version in `TheLadsLauncher/TheLadsLauncher.csproj` is the single release version. Windows x64 packages use Velopack 1.2.158 (pinned in both NuGet and `.config/dotnet-tools.json`). The update source is the public `ArashYT/TheLadsClient` GitHub repository; no token ships in the launcher.

## Each release

1. Increase the csproj version (for example 1.2.1 to 1.2.2).
2. Write `docs/releases/1.2.2.md`. Explain actual changes, fixes, installation changes, and known limitations. Keep all previous notes. The release script refuses missing or placeholder notes.
3. Commit the intended source and notes, and push the commit. Review the worktree before committing; it may include unrelated work.
4. Create and push a matching tag: `git tag v1.2.2`, then `git push origin v1.2.2`.
5. The Windows release workflow builds the core and launcher, runs launcher tests, packages all runtime and mod files, and creates a **draft** GitHub release with the same notes bundled in the launcher.
6. Verify the draft installer on Windows and run the update smoke test. Review the notes and complete asset list, then publish the draft as the latest stable release in GitHub Releases. This is the point at which users receive it automatically.

Alternative local route (do not run alongside the tag workflow): after a clean committed and pushed tag, sign in using `gh auth login`, then run `pwsh ./Publish-Release.ps1`. It creates a draft, never immediately pushes an update to everyone. Source, tag, and version must match. Every native build failure stops the release.

For a local review build, run `pwsh ./Publish-Release.ps1 -PrepareOnly`. `-SkipCoreBuild` may additionally reuse local core jars for packaging tests only; it cannot upload a release. The output prints a unique directory under `artifacts/releases` so stale build files cannot leak into a new package.

## Required release assets

- `TheLadsClient-win-Setup.exe`: full Windows installer and the download for new users.
- `TheLadsClient-<version>-full.nupkg`: full automatic update package.
- `releases.win.json`: update feed with package hashes, sizes, versions, and notes.
- Additional Velopack-generated assets (including portable ZIP and legacy feed).
- `SHA256SUMS.txt`, `RELEASE_NOTES.md`, and `release-build.json`.

Upload all generated files together to a draft before publishing. Never upload a lone launcher EXE and never replace an already-published version. Fix a bad release by publishing a higher version. Drafts and prereleases are excluded from the stable updater. GitHub anonymous rate limits or network errors may delay checking; failures are logged and retried.

## Automatic update behavior

The installed launcher checks at startup and every ten minutes, downloads and verifies packages, and restarts itself automatically. It waits while Minecraft, a launch, sign-in, or release notes are active, then retries the idle condition every fifteen seconds. Failed checks/downloads/applies have a two-minute retry delay. Development builds do not self-update; `LADS_SKIP_UPDATE=1` also disables checks. Accounts, worlds, profiles, and settings remain outside the replaced application directory.

Older EXE-only installations need this new Setup once. The old updater cannot reliably install the new multi-file package. The Setup creates the new shortcuts; users should launch those afterwards. There is no release asset named `TheLadsLauncher.exe`, intentionally preventing the old updater from overwriting itself with a bare apphost. Old in-app patch notes are preserved verbatim where available; 1.2.0 was a local development version, not a public release.

## Validation and limits

`dotnet test TheLadsLauncher.Tests/TheLadsLauncher.Tests.csproj -c Release` covers updater coordination and offline note history along with existing launcher tests. `pwsh ./tools/Test-AutomaticUpdate.ps1` performs an isolated real package replacement/restart test without touching the user's installed client. This does not replace testing a published GitHub update on a separate Windows installation. Packages are not Authenticode-signed unless signing is configured separately, so Windows may show an initial installer reputation prompt.
