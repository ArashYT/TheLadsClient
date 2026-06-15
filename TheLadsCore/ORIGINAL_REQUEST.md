# Original User Request

## Initial Request — 2026-06-05T15:59:33Z

Build an integrated, custom GUI settings menu within the `TheLadsCore` mod, accessible via the Pause Menu and Right Shift keybind. The UI should be highly stylized, resembling Lunar Client. In this iteration, recreate the functionality of three specific mods—BetterF3, Dynamic FPS, and PingView—as toggleable modules directly within the new GUI, allowing their original standalone `.jar` files to be deleted.

Working directory: `C:\Users\Arash\curseforge\minecraft\Instances\TheLadsCore`
Integrity mode: development

## Requirements

### R1. Lunar-Style Mod Menu GUI
Create a completely custom Minecraft `Screen` that serves as the "The Lads Settings" menu. It must be accessible via a button in the Pause Menu and by pressing Right Shift in-game. It should have a modern, highly stylized aesthetic (e.g. rounded rectangles, blur effects if possible, organized tabs, toggles, sliders) similar to Lunar Client. Include a "Credits" tab to attribute original mod authors.

### R2. Centralized Module System
Implement a backend module system in `TheLadsCore` where features can be registered as "Modules." Each module must have a toggle state (enabled/disabled) and optional individual settings. The settings must be saved persistently to a JSON configuration file.

### R3. Reimplement 'BetterF3', 'Dynamic FPS', and 'PingView'
Recreate the core functionality of these three mods natively within `TheLadsCore`:
1. **BetterF3 Module**: A cleaner, customizable debug screen.
2. **Dynamic FPS Module**: Reduces game framerate when the Minecraft window is unfocused to save resources.
3. **PingView Module**: Displays exact latency numbers (ping) next to player names in the multiplayer tab list.

## Acceptance Criteria

### UI Framework
- [ ] In-game, pressing Right Shift opens the new custom "The Lads Settings" GUI.
- [ ] The Pause Menu contains a functional button to open the same settings GUI.
- [ ] The GUI has a "Credits" section that attributes the authors of BetterF3, Dynamic FPS, and PingView.

### Module Functionality
- [ ] Mod settings persist across game restarts in a config file.
- [ ] Disabling the BetterF3 module completely reverts the F3 screen to vanilla behavior.
- [ ] Unfocusing the game window (Alt+Tab) drops the framerate significantly when the Dynamic FPS module is enabled.
- [ ] The multiplayer player list displays numerical ping values when PingView module is enabled.
- [ ] The original standalone `.jar` files for BetterF3, Dynamic FPS, and PingView can be deleted from the `mods` folder without losing this functionality.
