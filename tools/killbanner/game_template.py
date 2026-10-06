"""Builds killbanner/template.properties, the kill banner motion every still skin plays, from the game's own widget
animations and Blueprint logic (FModel export of KillBanner_Base, KillBanner_Wheel and KillBanner_PieSlice, with
Serialize Script Bytecode / Decompiled Blueprints): the sequencer curves and the code's timers evaluated frame by frame
at 60 fps into the client's channels, for each kill count.

What the game does (KillBanner_Base.cpp, KillBanner_Wheel.cpp, KillBanner_PieSlice.cpp):
- StartAnimation plays IntroAnimation at speed 1, or 0.3 for an ace (5+ kills); the banner is removed at its end.
  Its events (sequencer seconds, tick resolution 60000): 0.15 s slices light up + FX + headshot flicker, 0.75 s wheel
  spin (2+ kills), 1.803 s emblem fade-out, 1.903 s general fade-out; the holder's opacity reaches 0 at 2.26 s.
- Pips: every slice plays State2_CurrentlyLit at the 0.15 s event, in real time (not scaled by the ace's 0.3): hover
  0 -> 1 in 0.15 s, held to 0.3 s, -> 0.6 at 0.75 s; the pip sits 15 px out and 1.2x large until 0.3 s, then slides in
  and shrinks by 0.75 s. Before that the Up texture shows at 0.3 opacity (NotLit).
- Spin (DelayedSpin at the 0.75 s event, Tick): FInterpTo from 0 to -360/kills degrees (2 to 4 kills, speed 8/s), or
  to +720 for an ace (speed 5/s); snaps when within 0.1 degrees. ReverseSpin is not used.
- Materials (real time): the frame dissolves in over 0.3 s; the emblem in over 0.02 s and out over 0.3 s from the
  emblem fade-out event; the ring over 0.5 s.
- Headshot: HeadshotFlicker at the 0.15 s event (KillBannerPlayer.STROBE / FLICKER_SCALE, from the same export).

python game_template.py --common <CommonAssets export dir> [--out ../../TheLadsCore/.../killbanner/template.properties]
"""
import argparse
import json
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[1] / 'TheLadsCore/common/src/main/resources/assets/theladscore/killbanner'
CHANNELS = ['icon.alpha', 'icon.scale', 'icon.y', 'icon.shade', 'ring.alpha', 'ring.scale', 'frame.alpha', 'frame.scale',
            'pip.alpha', 'pip.up', 'pip.scale', 'pip.radius', 'pip.flare', 'pip.spin']
ART_SCALE = .76   # cell px per art (UMG) px: KillBannerStyle.ART_SCALE
FPS = 60


# ---- UE rich curves ---------------------------------------------------------------------------------------------
class Curve:
    """An FMovieSceneFloatChannel: keys (time in seconds, value, interp, arrive/leave tangent per second)."""

    def __init__(self, raw, ticks, default=None):
        self.keys = []
        times = raw.get('Times') or []
        values = raw.get('Values') or []
        if raw.get('Keys'):
            for k in raw['Keys']:
                t = k['Time']['Value'] if isinstance(k.get('Time'), dict) else k.get('Time', 0)
                self.keys.append(self._key(t, k.get('Value', {}), ticks))
        else:
            for t, v in zip(times, values):
                self.keys.append(self._key(t['Value'] if isinstance(t, dict) else t, v, ticks))
        self.default = raw.get('DefaultValue', default)

    @staticmethod
    def _key(t, v, ticks):
        if isinstance(v, dict):
            tan = v.get('Tangent', {})
            return (t / ticks, float(v.get('Value', 0.0)), int(v.get('InterpMode', 0)),
                    float(tan.get('ArriveTangent', 0.0)), float(tan.get('LeaveTangent', 0.0)))
        return (t / ticks, float(v), 0, 0.0, 0.0)

    def at(self, t):
        """Value at t seconds (UE semantics: Linear, Constant or Cubic Hermite with the stored tangents)."""
        if not self.keys:
            return self.default if self.default is not None else 0.0
        if t <= self.keys[0][0]:
            return self.keys[0][1]
        if t >= self.keys[-1][0]:
            return self.keys[-1][1]
        for i in range(len(self.keys) - 1):
            t0, v0, mode, _, leave = self.keys[i]
            t1, v1, _, arrive, _ = self.keys[i + 1]
            if t0 <= t <= t1:
                if mode == 1:
                    return v0
                u = (t - t0) / (t1 - t0) if t1 > t0 else 1.0
                if mode == 0:
                    return v0 + (v1 - v0) * u
                dt = t1 - t0
                h00, h10, h01, h11 = 2 * u ** 3 - 3 * u ** 2 + 1, u ** 3 - 2 * u ** 2 + u, -2 * u ** 3 + 3 * u ** 2, u ** 3 - u ** 2
                return h00 * v0 + h10 * dt * leave + h01 * v1 + h11 * dt * arrive
        return self.keys[-1][1]


def load(path):
    objs = json.loads(Path(path).read_text(encoding='utf-8'))
    return objs, {i: o for i, o in enumerate(objs)}


def ref(idx, r):
    try:
        return idx[int(r['ObjectPath'].rsplit('.', 1)[1])]
    except (KeyError, ValueError, TypeError, IndexError):
        return {}


def tick_resolution(ms):
    for o in [ms] + [ms.get('Properties', {})]:
        tr = o.get('TickResolution')
        if isinstance(tr, dict):
            return tr.get('Numerator', 60000) / max(1, tr.get('Denominator', 1))
    return 60000.0


def animation(objs, idx, label):
    """{binding name: {property: {channel: Curve}}}, [(seconds, event function name)] of one widget animation."""
    for o in objs:
        if o.get('Type') == 'WidgetAnimation' and (o.get('Properties', {}).get('DisplayLabel') == label or o.get('Name') == label + '_INST'):
            ms = ref(idx, o['Properties']['MovieScene'])
            ticks = tick_resolution(ms)
            out, events = {}, []
            for ob in ms.get('Properties', {}).get('ObjectBindings', []):
                props = out.setdefault(ob['BindingName'], {})
                for tref in ob.get('Tracks', []):
                    tr = ref(idx, tref)
                    pb = tr.get('Properties', {}).get('PropertyBinding', {})
                    name = pb.get('PropertyName') or pb.get('PropertyPath') or tr['Name']
                    chans = props.setdefault(name, {})
                    for sref in tr.get('Properties', {}).get('Sections', []):
                        sec = ref(idx, sref)
                        for key, val in sec.get('Properties', {}).items():
                            if isinstance(val, dict) and ('Keys' in val or 'Times' in val or 'Values' in val):
                                chans[key] = Curve(val, ticks)
            for tref in ms.get('Properties', {}).get('Tracks', []) or []:
                tr = ref(idx, tref)
                for sref in tr.get('Properties', {}).get('Sections', []):
                    sec = ref(idx, sref)
                    ch = sec.get('Properties', {}).get('EventChannel', {})
                    for k, v in zip(ch.get('KeyTimes', []) or [], ch.get('KeyValues', []) or []):
                        fn = str(v.get('Ptrs', {}).get('Function', {}).get('ObjectName', ''))
                        events.append(((k['Value'] if isinstance(k, dict) else k) / ticks, fn.rsplit(':', 1)[-1].strip("'")))
            return out, sorted(events)
    raise KeyError(label)


def smooth(u):
    u = min(1.0, max(0.0, u))
    return u * u * (3 - 2 * u)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--common', required=True)
    ap.add_argument('--out', default=str(ASSETS / 'template.properties'))
    args = ap.parse_args()
    common = Path(args.common)
    base, bidx = load(common / 'KillBanner_Base.json')
    pie, pidx = load(common / 'KillBanner_PieSlice.json')
    intro, events = animation(base, bidx, 'IntroAnimation')
    lit, _ = animation(pie, pidx, 'State2_CurrentlyLit')
    # The sequence events by what they call (KillBanner_Base.cpp SequenceEvent__ENTRYPOINT...): _ = pie slices (and
    # _1 = FX, same time), _0 = wheel spin, _3 = emblem fade-out, _2 = general fade-out.
    by = {fn: t for t, fn in events}
    t_lit = by['SequenceEvent__ENTRYPOINTKillBanner_Base']
    t_spin = by['SequenceEvent__ENTRYPOINTKillBanner_Base_0']
    t_emblem_out = by['SequenceEvent__ENTRYPOINTKillBanner_Base_3']
    t_general_out = by['SequenceEvent__ENTRYPOINTKillBanner_Base_2']
    print('intro events (sequencer s):', {fn.replace('SequenceEvent__ENTRYPOINTKillBanner_Base', 'event'): round(t, 4) for t, fn in events})

    w_scale = intro['KillBanner_Wheel']['RenderTransform']['Scale']
    w_alpha = intro['KillBanner_Wheel']['RenderOpacity']['FloatCurve']
    g_y = intro['GlobalHolder']['RenderTransform']['Translation[1]']
    g_alpha = intro['GlobalHolder']['RenderOpacity']['FloatCurve']
    e_scale = intro['KillBadgeMaterial']['RenderTransform']['Scale']
    t_end = max(k[0] for k in g_alpha.keys)
    pip_hover = lit['hover']['RenderOpacity']['FloatCurve']
    pip_y = lit['Pips']['RenderTransform']['Translation[1]']
    pip_scale = lit['Pips']['RenderTransform']['Scale']
    print(f'holder slides {g_y.at(0):.0f} -> {g_y.at(9):.0f} px; wheel scale {w_scale.at(0)} -> {w_scale.at(1)}; end {t_end:.3f} s')

    lines = ['# The game\'s own kill banner animation (KillBanner_Base IntroAnimation and its Blueprint, KillBanner_Wheel\'s spin,',
             '# KillBanner_PieSlice State2_CurrentlyLit), evaluated at 60 fps: tools/killbanner/game_template.py. Every still skin',
             '# plays this; the ace at the game\'s 0.3 speed. Per kill count, frames 0..introEnd play and introEnd holds, then exit',
             '# frames play the way out. pip.radius is art pixels outward from the pip\'s place; pip.up is the Up texture\'s opacity,',
             '# pip.flare the hover texture\'s.', 'orbit=1.000']
    for count in range(1, 6):
        speed = .3 if count == 5 else 1.0
        f_lit = int(round(t_lit / speed * FPS))
        f_spin = int(round(t_spin / speed * FPS))
        f_emblem_out = int(round(t_emblem_out / speed * FPS))
        n = int(round(t_end / speed * FPS)) + 1
        intro_end = f_emblem_out - 1
        exit_f = n - intro_end - 1
        rows = {c: np.zeros(n) for c in CHANNELS}
        angle, goal, rate = 0.0, (720.0 if count == 5 else -360.0 / count), (5.0 if count == 5 else 8.0)
        for f in range(n):
            t = f / FPS * speed            # sequencer seconds
            real = f / FPS                  # real seconds (materials, the pips' state animation, the spin)
            holder = g_alpha.at(t)
            rows['ring.alpha'][f] = w_alpha.at(t) * holder * min(1.0, real / .5)
            rows['ring.scale'][f] = w_scale.at(t)
            rows['frame.alpha'][f] = holder * min(1.0, real / .3)
            rows['frame.scale'][f] = 1.0
            out = max(0.0, (f - f_emblem_out) / FPS)  # seconds into the emblem's fade-out
            rows['icon.alpha'][f] = holder * min(1.0, real / .02) * max(0.0, 1 - out / .3)
            rows['icon.scale'][f] = e_scale.at(t)
            rows['icon.y'][f] = g_y.at(t) * ART_SCALE
            rows['icon.shade'][f] = 1.0
            ta = (f - f_lit) / FPS
            rows['pip.alpha'][f] = w_alpha.at(t) * holder
            rows['pip.up'][f] = .3 if ta < 0 else 1.0
            rows['pip.flare'][f] = 0.0 if ta < 0 else pip_hover.at(ta)
            rows['pip.scale'][f] = 1.0 if ta < 0 else pip_scale.at(ta)
            rows['pip.radius'][f] = 0.0 if ta < 0 else -pip_y.at(ta)
            if count > 1 and f > f_spin:
                angle += (goal - angle) * min(1.0, rate / FPS)
                if abs(goal - angle) < .1:
                    angle = goal
            rows['pip.spin'][f] = angle
        lines.append(f'k{count}.introEnd={intro_end}')
        lines.append(f'k{count}.exit={exit_f}')
        lines.append(f'k{count}.mark={f_lit}')
        lines.append(f'k{count}.spray={"0,0" if count == 1 else f"{f_lit},34"}')
        for c in CHANNELS:
            lines.append(f'k{count}.{c}=' + ','.join(f'{v:.3f}'.rstrip('0').rstrip('.') if abs(v) > 5e-4 else '0' for v in rows[c]))
        settle = next((f for f in range(n) if count > 1 and f > f_spin and rows['pip.spin'][f] == goal), None)
        print(f'k{count}: introEnd {intro_end} exit {exit_f} mark {f_lit} spin from {f_spin} to {goal:.0f} deg'
              + (f' (settled at frame {settle})' if settle else '') + f'; {n / FPS:.2f} s')
    Path(args.out).write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(f'wrote {args.out}')

    # The headshot flicker (HeadshotFlicker, real time from the 0.15 s event), for KillBannerPlayer.
    flick, _ = animation(base, bidx, 'HeadshotFlicker')
    emblem_g = flick['BadgeDefault']['ColorAndOpacity']['GreenCurve']
    emblem_s = flick['KillBadgeMaterial']['RenderTransform']['Scale']
    reticle_s = flick['HeadshotReticleHolder']['RenderTransform']['Scale']
    frames = int(round(max(k[0] for k in emblem_g.keys) * FPS)) + 1
    print('STROBE (the emblem\'s redness a frame from the mark):', ', '.join(f'{1 - emblem_g.at(f / FPS):.2f}f' for f in range(frames)))
    print('FLICKER_SCALE (the emblem\'s size):', ', '.join(f'{emblem_s.at(f / FPS):.3f}f' for f in range(frames)))
    print('reticle scale 2 -> 1 over', max(k[0] for k in reticle_s.keys), 's')


if __name__ == '__main__':
    main()
