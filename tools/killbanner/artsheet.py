"""Diagnostic: our banner art next to a preview video's settled frame, at the nominal scale.

python artsheet.py out.png <skin> "<video name>" <frame> [<frame> ...]
"""
import sys
import cv2
import numpy as np
from PIL import Image
import measure_video as mv
from framesheet import crops

skin, name = sys.argv[2], sys.argv[3]
frames = [int(x) for x in sys.argv[4:]]
got = crops(r'G:\Val skins Previews' + '\\' + name + '.mp4', frames)
b = mv.BANNERS[skin]
s = mv.ART_NOMINAL * mv.CELL
tiles = []
for f in frames:
    base = Image.fromarray(got[f], 'RGB').convert('RGBA')
    over = base.copy()
    for layer in ('frame', 'ring', 'emblem'):
        a = mv.art(skin, f'{layer}.png')
        if a is None:
            continue
        im = Image.fromarray(mv.scaled(a, s), 'RGBA')
        over.alpha_composite(im, (round(mv.CX - im.width / 2), round(mv.CY - im.height / 2)))
    pip = mv.art(skin, 'pip.png')
    if pip is not None:
        im = Image.fromarray(mv.scaled(pip, s), 'RGBA')
        r = float(b['radius']) * s
        for ang in mv.pip_layout(5):
            px, py = mv.CX + r * np.sin(np.radians(ang)), mv.CY - r * np.cos(np.radians(ang))
            over.alpha_composite(im, (round(px - im.width / 2), round(py - im.height / 2)))
    pair = np.hstack([np.asarray(base.convert('RGB')), np.asarray(over.convert('RGB'))])
    cv2.putText(pair, f'{skin} {f}', (4, 16), cv2.FONT_HERSHEY_SIMPLEX, .5, (255, 255, 0), 1)
    tiles.append(pair)
cv2.imwrite(sys.argv[1], cv2.cvtColor(np.vstack(tiles), cv2.COLOR_RGB2BGR))
print(sys.argv[1])
