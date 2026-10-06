"""Diagnostic: the pip-art shape scores of accent blobs (true pips against effects) with the current matcher, to set
Measurer.shape_least.

python calib_shape.py <skin> "<video name>"
Prints the shape scores of the blobs that sit on a settled pip position against all the others, per banner.
"""
import sys
import numpy as np
import measure_video as mv

skin, name = sys.argv[1], sys.argv[2]
mv.Measurer.shape_least = -1  # keep every blob
log = []
mv.Measurer.shape_log = log
r = mv.measure(skin, r'G:\Val skins Previews' + '\\' + name + '.mp4', check_dir='out/motion/dbg', quiet=False)
print('result', None if r is None else {k: r[k] for k in ('kills', 'counts', 'marks', 'measured', 'intro', 'exit', 'scale')})
scores = np.array([b[3] for b in log])
print(f'{len(log)} blobs; shape score percentiles 10/25/50/75/90: {np.percentile(scores, [10, 25, 50, 75, 90]).round(3)}')
hist, edges = np.histogram(scores, bins=np.arange(-.2, 1.01, .1))
for h, e in zip(hist, edges):
    print(f'  {e:+.1f}..{e + .1:+.1f}: {h}')
