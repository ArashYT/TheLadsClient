# Native screenshots gallery for Minecraft 26.2

Implementation checkpoint: 2026-09-10. The final native gallery passed **38 checks**
at 19:27:38, including real image textures and render-state extraction. The production
26.2 manifest now retires Screenshot Viewer in favor of the built-in gallery.
The combined final core hash is
`52D16AD48F6776945F790288D79496EA2B05AFC66E4A78F7462A79D2C0395DB5`.
Older Minecraft targets retain their existing routes.

## Provenance and ownership

The native gallery is a relocated, adapted MIT port of **Screenshot Viewer
1.3.6-fabric-mc26.2**, as actually supplied by the pinned production jar
(`screenshot_viewer`; jar SHA-256
`759BEDDF3BF3AFAD22997F247A21F639B057D17338A1414C67F313DD35CE9958`).
Its [upstream repository](https://github.com/LGatodu47/ScreenshotViewer) exposes the
26.1 Fabric source at commit `75de5e2b1921ca61ec9cb1e063f668d6f31f9c5a` (1.3.5),
but no matching 26.2 branch. The supplied MIT binary was therefore inspected and
recovered with Vineflower 1.11.1, then compared with the published source. The
26.2 ARGB controls, GUI input APIs, and screenshot callback are retained. The
builder's static initialization order was reconstructed from bytecode, rather
than accepting the decompiler's field-hoisting output.

The source lives in
`TheLadsCore/v26_2/src/main/java/com/thelads/core/v26_2/feature/screenshots` and is
included in the distributed core jar under `META-INF/lads-sources/screenshots`.
Assets are relocated to `assets/lads_screenshots`; original MIT notices accompany
them. The Mac clipboard helper preserves comp500's separate MIT notice.

The **gallery engine itself is inside Lads Core**. CatConfig-MC 26.2/0.2.1 and its
CatConfig 0.3.0 child remain explicit MIT configuration libraries, unchanged from
the pinned jar. CatConfig-MC SHA-256 is
`E99962B3E18B1F86665BAA9DA9B94175AAA219BF602AF5E7DE376A5478BE6E86`.
Both library licenses are packaged. The Apache-2.0 Java Objective-C bridge 1.0.0
is retained for Mac image clipboard support, using Minecraft's JNA runtime.
These libraries are declared as nested jars; they are not separate gameplay mods.

Native registration and all screenshot mixins yield if `screenshot_viewer` or
`decentscreenshot` is installed. Lads does not delete unmanaged copies.

## Feature mapping

| Reference capability | Native implementation |
| --- | --- |
| Gallery and full-size viewer | Native PNG/JPG/JPEG list; actual file-backed images; enlarged view, previous/next buttons and arrow keys, parent-screen restoration. |
| DecentScreenshot entry convention | Rebindable F10 default, plus title and pause entries and RSHIFT BetterScreenshots > Browse Screenshots. F10 opens from gameplay, title, or pause; it does not interrupt text editors. |
| Gallery layout and ordering | Ascending/descending file order, refresh button/F5, configured 2–8 thumbnails per row, Ctrl-scroll density, scroll speed and inversion. |
| Viewer navigation and zoom | Ordinary wheel/arrow keys navigate images. Ctrl-wheel zooms 1–8x; left-drag pans. Aspect ratio fits both screen dimensions. |
| File properties | Open original in OS, copy image to clipboard, rename, delete, and close context menu; same actions in the enlarged viewer. |
| Renaming | Actual file move, original PNG/JPG/JPEG extension retained, resize-safe name draft, invalid/path-traversal/reserved names rejected, existing files never overwritten. |
| Single and batch delete | Confirmation enabled by default; explicit fast-delete selection and selected-count button; cancellation preserves files; optional no-prompt behavior honored. Failed deletion does not silently remove its row. |
| Screenshot chat links | Required 26.2 screenshot callback decorates the real saved-file link; optional gallery redirect, Shift bypass to the normal file action. Vanilla screenshot writing is unchanged. |
| Thumbnail cache | NONE/HALF/QUARTER/EIGHTH compression; configured cache folder; path/size/modification-time/ratio stamped cache; asynchronous generation and reuse. Cache files use a reserved `lads-cache-v1` subfolder so source and cache folders can never overwrite one another. |
| Appearance | Independent ARGB background/text colors, text visibility and font shadow, contextual hints, wide properties buttons, animation option. |
| Entry placement | Title and pause visibility controls, relative-position editor, reset/default/confirm/cancel. Lads' custom title layout may place the entry in More, consistently with other native secondary widgets. |
| Configuration UI | All 20 pinned options are available through BetterScreenshots > Gallery Settings and the gallery's Configure button. UI draft/save/cancel is supplied by the retained config library. |
| Existing preferences | Read-only first-run import from `config/screenshot_viewer-client.json`; native writes `config/theladscore/screenshots-client.json`. Existing native settings win. |

No bitmap paint editor exists in the pinned Screenshot Viewer reference; no such
capability is claimed here.

## Lifecycle and behavior changes

Image decoding, clipboard decode and cache work share a two-thread daemon executor.
Thumbnail-to-image loading uses future composition; workers do not block on other
jobs in that executor. Preview images decode lazily when visible, with source subsampling to at most
1024 pixels on the longer side. Full-resolution images load when the viewer uses
them. Scrolled-out images release CPU/GPU resources, except the image currently
shown in the full viewer. Each texture has a unique identifier; closing a gallery
releases its textures and pending image ownership. Cache metadata uses a bounded
128-entry memory map. Disk thumbnails persist across sessions; old files for an
explicitly deleted/renamed screenshot are retired only in the reserved cache.

PNG and JPEG decoding is local to this gallery. The upstream global bypass of
Minecraft's PNG header validation is deliberately unnecessary here. A corrupt file
can remain visible as an empty tile while the other files continue working.
Image reads reject files above 256 MiB, dimensions above 16,384 per edge, or more
than 64 million pixels. These limits bound individual decodes.

Hover and enlargement animations use elapsed time; they do not advance a fixed
amount per rendered frame. Setting Minecraft's screen effects to zero suppresses
enlargement animation. Copy completion/error toasts return to the client thread.
The config editor writes on close; the native port creates no perpetual watcher
thread.

## Verification

- `ScreenshotFileIOTest`: **22 checks passed**, using generated fixtures. Tests
  cover uppercase extensions, actual PNG pixels/JPEG decoding, preview
  subsampling, same-folder rename with extension preservation, invalid and reserved
  names, overwrite collision preservation, malformed-image failure, explicit
  deletion, and rejection of non-image deletion.
- Required `TheLadsCore/gradlew.bat build deploy -x test` passed. The combined
  jar produced at this checkpoint has SHA-256
  `1866A5EDDEF308AA43DC712F395AE2C2941D4A5160E2731C3661ABD9A221A421`.
  Its final Fabric metadata and actual nested CatConfig/CatConfig-MC/Objective-C
  bridge bytes were inspected. Later source changes in other native engines may
  produce a different final release hash.
- `NativeScreenshotsProbe` runs only with `-Dthelads.verifyIntegrations=true` in
  `artifacts/verification`, after 320 loaded-world, screen-null ticks. It generates
  files in a fresh sibling QA directory, opens a pause screen, drives synthetic
  GLFW F10 through the real keyboard handler, extracts real gallery/viewer/config
  render state, uploads actual image textures, and checks cache dimensions,
  ordering/zoom, rename/delete UI, menu dispatch, cleanup, module disable and parent
  restoration. The exact fixture directory is logged. It restores prior options,
  module metadata, and screen in `finally`.
- Runtime success marker:
  `Lads native screenshots probe END: ... checks passed, 0 failed`.
  The 19:25:01 run passed **38 checks, 0 failed**. The preserved full log is
  [screenshots-38-checks-2026-09-10-1925.log](../artifacts/verification/native-mods-26.2/screenshots-38-checks-2026-09-10-1925.log).
  Its two corrupt-image warnings are the intentionally malformed generated PNG,
  demonstrating recoverable decode failure while the gallery continued.

After that successful run, the final two-thread executor improvement was applied.
Required build/deploy passed with core SHA-256
`52D16AD48F6776945F790288D79496EA2B05AFC66E4A78F7462A79D2C0395DB5`;
all 22 file IO checks passed again. The fresh combined runtime run at **19:27:38**
passed **38 gallery checks, 0 failed** with `Lads screenshot IO` worker names in
the log, confirming the final executor was active. The frozen proof is
[screenshots-final-38-checks-2026-09-10-1927.log](../artifacts/verification/native-mods-26.2/screenshots-final-38-checks-2026-09-10-1927.log). [File IO result](../artifacts/verification/native-mods-26.2/screenshots-file-22-checks.log).

The QA menu dispatch mocks OS open and clipboard side effects. It does not claim
an actual clipboard round trip on Windows or macOS, physical F10 input, or manual
visual review. Synthetic keyboard events and GPU/render-state checks are reported
as those checks specifically.


Final combined proof is frozen in
[`native-final-checkpoint/world-pass.log`](../artifacts/verification/native-final-checkpoint/world-pass.log),
with the exact [tested core](../artifacts/verification/native-final-checkpoint/theladscore.jar),
[27-jar pack manifest](../artifacts/verification/native-final-checkpoint/client-mods.json),
[world frame](../artifacts/verification/native-final-checkpoint/world-frame.png), and
[actual Lads mods-menu frame](../artifacts/verification/native-final-checkpoint/mods-menu.png).
The frame captures establish the world and mods menu; they are not a claim of manual
visual review of every gallery control. Production retirement follows this complete
combined QA, while explicit unmanaged external copies still cause native yielding.
