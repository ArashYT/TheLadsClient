"""Diagnostic: where a skin's emblem shows in its preview video (emblem match score every few frames over the whole
video), to find banners the strobe detector missed.

python probe_emblem.py <skin> "video name" [stride]
"""
import sys
import numpy as np
import measure_video as mv

skin, name = sys.argv[1], sys.argv[2]
stride = int(sys.argv[3]) if len(sys.argv) > 3 else 10
frames, fps = mv.load_video(r'G:\Val skins Previews' + '\\' + name + '.mp4', stop=False)
m = mv.Measurer(skin, 0, frames, fps)
s = mv.ART_NOMINAL * mv.CELL
scores = [(f, m.emblem(f, s)[0]) for f in range(0, len(frames), stride)]
vals = np.array([x[1] for x in scores])
bg = float(np.percentile(vals, 30))
print(f'{name}: {len(frames)} frames; emblem score p30 {bg:.3f} p90 {np.percentile(vals, 90):.3f} max {vals.max():.3f}')
runs, start = [], None
for f, v in scores:
    on = v >= bg + .2
    if on and start is None:
        start = f
    if not on and start is not None:
        runs.append((start, f))
        start = None
if start is not None:
    runs.append((start, len(frames)))
print('  emblem seen (from, to):', runs[:30])
strobe = mv.strobe_signal(frames)
print('  strobes at:', mv.find_kills(strobe, fps)[:20])
