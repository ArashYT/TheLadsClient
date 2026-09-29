# Native HUD editor and red Lads settings

The 26.2 development update makes Lads settings native-only and uses the launcher's dark red/red palette. Retained engines are still installed and reachable from Minecraft's ordinary Mod Menu. They are not fully recreated by hiding their cards.

## Editing

- Open Lads with Right Shift, then Edit HUD.
- Click a HUD to select it. Ctrl/Shift-click adds or removes HUDs. Select multiple also enables click-based multiple selection. Drag a box over HUDs to select them together.
- Group connects the selection; dragging any member moves the whole group with its relative spacing intact. Ungroup separates its members. Keyboard alternatives are Ctrl-G and Ctrl-Shift-G.
- Lock position prevents the selected HUD/group from moving. Locked HUDs remain selectable, and Unlock makes them movable again. Grouping and position locking are separate actions.
- Arrows move the selection one GUI pixel; Shift-arrows move it ten. Snap controls the grid and alignment snapping. The entire group clamps against the screen edge.
- Controls: top/bottom moves the toolbar away from a HUD beneath it. Done or Escape finishes an active drag and saves.
- All previews shows disabled HUDs without enabling them. Sample armor/scoreboard content stays confined to the editor.

## Placement fixes

HUD text and multiline lists are measured before their anchor, border and hit region are calculated. Rendering uses that same frame's measurement, and fractional scales round bounds outward. The native graphics adapter uses the active GUI extractor's dimensions. Minecraft does not draw the ordinary Lads HUD a second time behind its editor, and the editor avoids the default blur layer.

The live renderer and editor apply one translation to each group's shared bounds. Hidden group members retain their offsets, and viewport resizing does not overwrite stored positions. Groups and position locks are serialized with the existing HUD preferences.

## Verification

The opt-in QA path runs only in the existing repository save under `artifacts/verification/26.2-title`. After required world/GPU probes pass, `LADS_VERIFY_CAPTURE_HUD=1` requests real native mouse/key handler checks and a completed framebuffer image. It uses an in-memory HUD fixture with a no-op disk persistence callback, then restores the original module options, positions, groups and locks. It does not inject desktop input or modify the user's game/profile files.

Current run evidence is recorded in `artifacts/verification/hud-red-client-checkpoint`. The screenshot is an actual Minecraft frame of the isolated fixture, not a UI mockup. Physical input and online authentication remain separate checks.
