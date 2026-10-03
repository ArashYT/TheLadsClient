# CurseForge API key

CurseForge's API (api.curseforge.com) answers every request without a valid `x-api-key` header with
`403 Forbidden: API Key missing or invalid`. Modrinth needs no key. Keys are issued per project at
https://console.curseforge.com (an application for "CurseForge for Studios"); the launcher never ships a scraped or
borrowed key.

The launcher uses, in this order:

1. The key the player enters in **Mods > Mod Manager Settings > CurseForge API Key** (stored in their `settings.json`).
2. The key built into the launcher at build time, if any.

Without either, choosing CurseForge on the Browse Mods, Resource Packs, Shader Packs or Data Packs tabs shows
"CurseForge needs an API key, and none is set..." instead of an empty list, and Update resource packs checks Modrinth only.

## Building a key in

Set `LADS_CURSEFORGE_API_KEY` when building (MSBuild reads environment variables as properties):

```powershell
$env:LADS_CURSEFORGE_API_KEY = '<key>'
dotnet publish TheLadsLauncher -c Release -r win-x64 --self-contained
```

`TheLadsLauncher.csproj` turns it into `[assembly: AssemblyMetadata("CurseForgeApiKey", ...)]`, read by
`ContentCatalog.BuiltInCurseForgeKey`. The release workflow (`.github/workflows/release.yml`) passes the repository secret
`LADS_CURSEFORGE_API_KEY` to `Publish-Release.ps1`; with no secret the build simply has no key.

A key built into a published exe can be read out of it by anyone. CurseForge can revoke a key that is misused, which
would stop CurseForge browsing for every installed launcher until an update ships a new one.
