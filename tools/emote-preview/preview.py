"""Draws sampled emote frames (EmoteAnimTest.writesPreviewFrames -> common/build/emote-preview/<name>.json) as a contact sheet:
front, three-quarter and side views plus the first-person view of the arms, for tuning a dance before the in-game check.
Usage: python preview.py <frames.json> <out.png> [rows] [first beat] [last beat]"""
import json, math, sys
import numpy as np
from PIL import Image, ImageDraw

REST = [(0, 0, 0), (0, 0, 0), (-5, 2, 0), (5, 2, 0), (-1.9, 12, 0), (1.9, 12, 0)]
BOXES = [((-4, -8, -4), (4, 0, 4)), ((-4, 0, -2), (4, 12, 2)), ((-3, -2, -2), (1, 10, 2)), ((-1, -2, -2), (3, 10, 2)),
         ((-2, 0, -2), (2, 12, 2)), ((-2, 0, -2), (2, 12, 2))]
COLORS = [(222, 170, 130), (40, 160, 170), (225, 172, 132), (190, 140, 105), (60, 70, 170), (45, 55, 135)]
FACES = (((0, 1, 3, 2), (-1, 0, 0)), ((4, 5, 7, 6), (1, 0, 0)), ((0, 1, 5, 4), (0, -1, 0)), ((2, 3, 7, 6), (0, 1, 0)),
         ((0, 2, 6, 4), (0, 0, -1)), ((1, 3, 7, 5), (0, 0, 1)))
EYE_Y = -3.632
LIGHT = np.array([-0.4, -0.8, -0.45]); LIGHT /= np.linalg.norm(LIGHT)

def rot(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]); Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx

FP_SHIFT = [1.0, 0.5, -6.0]
FP_RAISE = -0.21  # EmotePose.FIRST_PERSON_RAISE  # first person: arm pivots moved by this (pixels, model axes) at full weight

def faces(v, parts=range(6), root=True, fp=False):
    out = []
    for p in parts:
        b = p * 6
        R = rot(v[b] + (FP_RAISE if fp else 0), v[b + 1], v[b + 2])
        piv = np.array(REST[p]) + np.array(v[b + 3:b + 6]) + (np.array(v[36:39]) if root else 0) + (np.array(FP_SHIFT) * np.array([1 if p == 3 else -1, 1, 1]) if fp else 0)
        (x0, y0, z0), (x1, y1, z1) = BOXES[p]
        c = np.array([[x, y, z] for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)], float)
        w = (R @ c.T).T + piv
        for idx, n in FACES:
            col = (250, 215, 175) if p == 0 and n == (0, 0, -1) else COLORS[p]
            out.append((w[list(idx)], R @ np.array(n, float), col))
    return out

def shade(col, n):
    k = 0.5 + 0.5 * max(0.0, float(np.dot(n, LIGHT)))
    return tuple(int(c * k) for c in col)

def ortho(fs, w, h, scale, yaw, ground):
    img = Image.new("RGB", (w, h), (236, 236, 236)); d = ImageDraw.Draw(img)
    gy = h - 20
    d.line([(8, gy), (w - 8, gy)], fill=(80, 140, 60), width=2)
    cy, sy = math.cos(yaw), math.sin(yaw)
    polys = []
    for pts, n, col in fs:
        x, z = pts[:, 0] * cy - pts[:, 2] * sy, pts[:, 0] * sy + pts[:, 2] * cy
        nx, nz = n[0] * cy - n[2] * sy, n[0] * sy + n[2] * cy
        if nz > 0.01:
            continue
        polys.append((z.mean(), [(w / 2 + xx * scale, gy + (yy - ground) * scale) for xx, yy in zip(x, pts[:, 1])], shade(col, n)))
    for _, pp, c in sorted(polys, key=lambda t: -t[0]):
        d.polygon(pp, fill=c, outline=(25, 25, 25))
    return img

def fp(fs, w, h, pitch):
    img = Image.new("RGB", (w, h), (170, 200, 235)); d = ImageDraw.Draw(img)
    f = (h / 2) / math.tan(math.radians(35))
    s, c = math.sin(pitch), math.cos(pitch)
    polys = []
    for pts, n, col in fs:
        p = pts - np.array([0, EYE_Y, 0])
        cam = [np.array([-q[0], q[1] * c + q[2] * s, q[1] * s - q[2] * c]) for q in p]  # right, down, depth
        clipped = []  # Sutherland-Hodgman against the near plane (0.05 blocks = 0.85 px)
        for i in range(len(cam)):
            a, b = cam[i], cam[(i + 1) % len(cam)]
            if a[2] >= 0.85: clipped.append(a)
            if (a[2] >= 0.85) != (b[2] >= 0.85):
                t = (0.85 - a[2]) / (b[2] - a[2]); clipped.append(a + (b - a) * t)
        if len(clipped) < 3:
            continue
        polys.append((np.mean([q[2] for q in clipped]), [(w / 2 + f * q[0] / q[2], h / 2 + f * q[1] / q[2]) for q in clipped], shade(col, n)))
    for _, pp, col in sorted(polys, key=lambda t: -t[0]):
        d.polygon(pp, fill=col, outline=(25, 25, 25))
    d.line([(w / 2 - 4, h / 2), (w / 2 + 4, h / 2)], fill=(255, 255, 255)); d.line([(w / 2, h / 2 - 4), (w / 2, h / 2 + 4)], fill=(255, 255, 255))
    return img

def main():
    global FP_SHIFT
    if len(sys.argv) > 6: FP_SHIFT = [float(x) for x in sys.argv[6].split(',')]
    data = json.load(open(sys.argv[1]))
    frames = data["frames"]  # 60 samples per loop of 4 beats, two loops
    rows = int(sys.argv[3]) if len(sys.argv) > 3 else 8
    b0 = float(sys.argv[4]) if len(sys.argv) > 4 else 0
    b1 = float(sys.argv[5]) if len(sys.argv) > 5 else 4 - 4 / rows
    cw, ch, fw = 150, 190, 240
    sheet = Image.new("RGB", (cw * 3 + fw * 2, ch * rows), (255, 255, 255))
    for r in range(rows):
        beat = b0 + (b1 - b0) * r / max(1, rows - 1)
        v = frames[int(round(beat * 15)) % 120]
        fs = faces(v)
        for col, yaw in enumerate((0, math.radians(35), math.radians(90))):
            sheet.paste(ortho(fs, cw, ch, 5.0, yaw, 24), (col * cw, r * ch))
        arms = faces(v, parts=(2, 3), root=False, fp=True)
        sheet.paste(fp(arms, fw, ch, 0), (3 * cw, r * ch))
        sheet.paste(fp(arms, fw, ch, math.radians(25)), (3 * cw + fw, r * ch))
        d = ImageDraw.Draw(sheet)
        d.text((4, r * ch + 4), f"beat {beat:.2f}", fill=(0, 0, 0))
        d.rectangle([0, r * ch, sheet.width - 1, r * ch + ch - 1], outline=(120, 120, 120))
    ImageDraw.Draw(sheet).text((3 * cw + 4, 4), "FP pitch 0", fill=(0, 0, 0))
    ImageDraw.Draw(sheet).text((3 * cw + fw + 4, 4), "FP 25 down", fill=(0, 0, 0))
    sheet.save(sys.argv[2])

main()
