"""Diagnostic: measure one preview video in the foreground, printing the measurer's notes.

python try_one.py <skin> "<video name without .mp4>" [check_dir]
"""
import sys
import measure_video as mv

skin, name = sys.argv[1], sys.argv[2]
check = sys.argv[3] if len(sys.argv) > 3 else 'out/motion/dbg'
r = mv.measure(skin, r'G:\Val skins Previews' + '\\' + name + '.mp4', check_dir=check)
print('result', None if r is None else {k: r[k] for k in ('kills', 'counts', 'marks', 'measured', 'intro', 'exit', 'scale')})
