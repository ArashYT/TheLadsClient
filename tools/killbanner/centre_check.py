"""Diagnostic: where the banner really sits in a preview video. Finds the ring circle (Hough) in settled frames of the
first banner and the emblem's best match, both relative to the measurer's assumed centre (W/2, 0.794 H).

python centre_check.py skin "video name" [skin "video name" ...]
"""
import sys
import cv2
import numpy as np
import measure_video as mv

args = sys.argv[1:]
for i in range(0, len(args), 2):
    skin, name = args[i], args[i + 1]
    frames, fps = mv.load_video(r'G:\Val skins Previews' + '\\' + name + '.mp4')
    kills = mv.find_kills(mv.strobe_signal(frames), fps)
    m = mv.Measurer(skin, 0, frames, fps)
    s = mv.ART_NOMINAL * mv.CELL * 1.04
    ring_r = (m.ring_r_art or 60) * s
    out = []
    for k in kills[:2]:
        for f in range(k + 30, k + 60, 6):
            g = cv2.GaussianBlur(m.gray[f].astype(np.uint8), (3, 3), 0)
            circles = cv2.HoughCircles(g, cv2.HOUGH_GRADIENT, dp=1, minDist=50, param1=120, param2=25,
                                       minRadius=int(ring_r * .85), maxRadius=int(ring_r * 1.15))
            score, rel, dy, _ = m.emblem(f, s)
            # The emblem's dx too (the measurer keeps dy only): best match over a small window.
            layer = m._emblem_layer(s * rel)
            sc, dx2, dy2 = mv.match(m.edge[f], layer, mv.CX, mv.CY, (dy - 3, dy + 3), (-12, 12))
            c = None
            if circles is not None:
                best = min(circles[0], key=lambda c: np.hypot(c[0] - mv.CX, c[1] - mv.CY))
                c = (round(float(best[0] - mv.CX), 1), round(float(best[1] - mv.CY), 1), round(float(best[2]), 1))
            out.append((f, c, (dx2, dy2), round(score, 2)))
    print(f'{skin}: ring radius expected {ring_r:.0f}px; (frame, ring circle dx/dy/r vs assumed centre, emblem dx/dy, score)')
    for row in out:
        print('   ', row)
