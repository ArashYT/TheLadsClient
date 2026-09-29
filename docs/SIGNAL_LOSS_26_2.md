# Native SignalLoss — 26.2

## 2026-09-10 full feature expansion

Audited and adapted the exact [SignalLoss](https://github.com/hexandcube/SignalLoss)
tag `v1.2.1+26.2`, commit `77ebf0128c0381a5c7151de8c4d5346d4670a8d3`,
matching the supplied reference artifact. Apache-2.0 license and original
Hexandcube notice are bundled with modified source in the Core JAR. The old
fixed five-second warning remains historical evidence, not full-reference parity.

All reference preferences now have native Lads controls: enabled, timeout
threshold, minimum warning time, recovery linger, background visibility,
singleplayer support, left/center/right placement and text/background ARGB colors.
Timing fields accept whole milliseconds across the reference's nonnegative Java
int range. Invalid text uses the documented default while the user edits.

The native policy preserves the five-second join grace period, strict timeout
comparison, minimum visible duration, and recovery linger with final lag duration.
It uses the actual connection's last received packet time. Local servers are
eligible only with Show in Singleplayer enabled. Pause, disable, disconnect and
connection changes reset the relevant state. Pause/resume uses a fresh baseline
so an old pre-pause packet cannot immediately trigger a warning.

Toast placement and quadratic entrance/exit match the reference design; elapsed
monotonic frame time replaces tick-derived animation increments. Reduced-motion
preferences skip sliding. The recovered duration stays visible through exit
instead of briefly becoming zero. Lads also supports its global color controls.

`/signalloss reload` reloads only the SignalLoss section of native Lads settings.
`/signalloss config reset` resets this module. All reference set commands are
available: `enabled`, `timeoutThreshold`, `minWarningTime`, `lingerTime`,
`drawBackground`, `showInSingleplayer`, `position`, `textColor`, `backgroundColor`.
Commands are client-only and their feedback is local. No server chat is sent.

On first initialization, existing `config/signalloss.json` is imported read-only
when native preferences remain at defaults. A migration marker prevents repeated
import. Customized native preferences take precedence. Saves use normal Lads
configuration; reload does not reload or reset unrelated modules. Invalid legacy
input cannot partially apply an import, and the original file remains intact.

Validation:

- `:v26_2:compileJava` passed with the full native policy/options/commands/probe.
- **13** targeted `SignalLossPolicyTest` tests passed: join grace, exact threshold,
  minimum/linger interaction, recovered duration, relapse, pause/resume, disable,
  connection isolation, disconnect, reduced motion, equal 30/60/240-FPS animation,
  clock wrap/negative origin and complete integer timing input range.
- Existing world nametag/connection probe now checks the new two-second default
  policy and recovery linger, preserving actual inbound-connection instrumentation
  checks and restoring the singleplayer option. It no longer treats immediate
  disappearance on recovery as the expected behavior.
- New `NativeSignalLossProbe` runs at the isolated QA title screen with
  `-Dthelads.verifyIntegrations=true`. It checks actual toast geometry, all three
  placements, colors, background toggle, animation position, safe configuration
  import/reload, colors and the full command tree. Both
  [native world checkpoints](NATIVE_RUNTIME_CHECKPOINT_26_2.md) recorded
  `Lads native SignalLoss probe END: 26 checks passed, 0 failed`.

These checks do not claim a controlled outage of a real remote server. A silence
warning reports missing inbound traffic, not the cause of a network problem.
