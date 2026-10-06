"""Diagnostic: full-size video | ours pairs for one skin, kill count and frames (the preview tool's alignment).

python zoom_pairs.py skin count f,f,f [out.png]
"""
import sys
import cv2
import numpy as np
import audit_motion as am
import measure_video as mv
import preview_motion as pm

skin, count, frames_wanted = sys.argv[1], int(sys.argv[2]), [int(x) for x in sys.argv[3].split(',')]
dest = sys.argv[4] if len(sys.argv) > 4 else f'out/motion/audit/zoom-{skin}-k{count}.png'
import json
entry = json.loads((pm.HERE / 'out' / 'motion' / 'summary.json').read_text(encoding='utf-8'))[skin]
data, orbit, _ = am.load(pm.MOTION / f'{skin}.properties')
frames, fps = mv.load_video(pm.FOLDER / entry['video'])
starts = mv.find_kills(mv.strobe_signal(frames), fps)
r60 = fps / 60
counts = []
for i, k in enumerate(starts):
    counts.append(1 if i == 0 or k - starts[i - 1] > 300 * r60 else min(5, counts[-1] + 1))
i = counts.index(count)
begin = starts[i] + int(round(mv.MARK_FRAME * r60)) - int(round(data[count].get('mark', 11) * r60))
art = pm.Art(skin)
rows = []
for f in frames_wanted:
    v = frames[begin + int(round(f * r60))]
    v = np.vstack([v[pm.DY:], np.repeat(v[-1:], pm.DY, axis=0)])
    o = pm.draw(art, data[count], count, orbit, f)
    pair = np.hstack([pm.label(v, f'{skin} k{count} f{f} video'), pm.label(o, 'ours')])
    rows.append(pair[40:280, 130:910])  # the banner and its surroundings
cv2.imwrite(dest, cv2.cvtColor(np.vstack(rows), cv2.COLOR_RGB2BGR))
print(dest)
