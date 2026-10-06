"""Diagnostic: a contact sheet of a preview video's banner crops at given frames.

python framesheet.py out.png video.mp4 start:stop:step [start:stop:step ...]
"""
import sys
import av
import cv2
import numpy as np

CW, CH = 520, 320


def crops(path, wanted):
    wanted = sorted(set(wanted))
    out = {}
    with av.open(str(path)) as c:
        st = c.streams.video[0]
        W, H = st.width, st.height
        k = H / 1080
        cx, cy = W / 2, .794 * H
        hw, hh = (CW / 2) * k, (CH / 2) * k
        x0, y0, x1, y1 = round(cx - hw), round(cy - hh), round(cx + hw), round(cy + hh)
        for i, fr in enumerate(c.decode(video=0)):
            if i > wanted[-1]:
                break
            if i in wanted:
                arr = fr.to_ndarray(format='rgb24')[max(0, y0):y1, max(0, x0):x1]
                if arr.shape[0] != CH or arr.shape[1] != CW:
                    arr = cv2.resize(arr, (CW, CH), interpolation=cv2.INTER_AREA)
                out[i] = arr
    return out


if __name__ == '__main__':
    dest, video = sys.argv[1], sys.argv[2]
    wanted = []
    for spec in sys.argv[3:]:
        a, b, s = (int(x) for x in spec.split(':'))
        wanted += list(range(a, b, s))
    got = crops(video, wanted)
    tiles = []
    for i in sorted(got):
        t = cv2.resize(got[i], (260, 160), interpolation=cv2.INTER_AREA).copy()
        cv2.putText(t, str(i), (4, 14), cv2.FONT_HERSHEY_SIMPLEX, .45, (255, 255, 0), 1)
        tiles.append(t)
    cols = 6
    while len(tiles) % cols:
        tiles.append(np.zeros((160, 260, 3), np.uint8))
    rows = [np.hstack(tiles[r:r + cols]) for r in range(0, len(tiles), cols)]
    cv2.imwrite(dest, cv2.cvtColor(np.vstack(rows), cv2.COLOR_RGB2BGR))
    print(dest, len(got), 'frames')
