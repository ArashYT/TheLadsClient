# Original User Request

## Initial Request — 2026-06-06T08:34:38Z

Add a unified Mod and Resource Pack browser and installer supporting Modrinth and CurseForge APIs, revamp the Mods menu to look premium and fancy, show the Minecraft version on the Launch button and home page, and implement a slow-drifting red nebula/particle mesh network background animation in the launcher.

Working directory: C:\Users\Arash\Desktop\Lads Client\TheLadsLauncher_Clean
Integrity mode: development

## Requirements

### R1. Unified Mods & Packs Manager
Add a unified "Mods & Packs" page to replace the simple "Installed Mods" view. Users should be able to:
- Select categories: "Installed Mods", "Browse Mods", "Browse Resourcepacks", and "Mod Manager Settings".
- Search and browse mods/resource packs from Modrinth (by default) and CurseForge (if API key is provided).
- Filter search results by target Minecraft version, automatically defaulting to the version currently configured in the launcher (with a manual override dropdown).
- Download, install, update, enable/disable, or delete mods/resourcepacks. Mods must install to the instance's `mods` folder and resource packs to `resourcepacks`.

### R2. CurseForge API Key Field
Add a setting field in the Settings page for the user to input their own CurseForge API Key. The launcher should securely save this key to `settings.json` and use it for CurseForge API queries, falling back gracefully if no key is supplied.

### R3. Premium UI & Menu Enhancement
Redesign the Mods page interface to look premium and modern (e.g. including mod icon images, description text, download counts, and category badges).

### R4. Red Nebula / Particle Mesh Background Animation
Implement a slow-drifting red nebula or particle mesh network background animation inside the launcher Window. This animation should run smoothly, respect the user's particle setting, and fit the red/black theme.

### R5. Minecraft Version Indicator
Show the targeted Minecraft version in two key places on the home page:
1. Directly on or next to the Launch button (e.g., `▶ LAUNCH (1.21.2)`).
2. Inside a dedicated status pill/label on the home page (e.g., `Minecraft: 1.21.2`).

## Acceptance Criteria

### API Integration & Installation
- [ ] Searching Modrinth returns valid results, downloads the correct jar/zip file, and stores it in the correct target folder (`mods/` or `resourcepacks/`).
- [ ] Searching CurseForge functions correctly when a valid API key is entered in the Settings page.
- [ ] Users can browse, download, install, update, enable/disable, and delete both mods and resourcepacks from the new UI.
- [ ] Version filtering automatically defaults to the launcher's active Minecraft version, with dropdown options to change it.

### Background Animation & UI styling
- [ ] A smooth, low-overhead, slow-drifting red nebula or connecting-line particle mesh network animation plays in the background.
- [ ] The "Mods & Packs" page is fancy, displaying mod titles, descriptions, icons, authors, and download states.
- [ ] Target Minecraft version (e.g. `1.21.2`) is clearly displayed on the Launch button and in a home page status pill.
