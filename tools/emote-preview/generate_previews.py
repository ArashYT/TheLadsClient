import math, json, os, subprocess, sys
from pathlib import Path

HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG = 0, 1, 2, 3, 4, 5
RX, RY, RZ, X, Y, Z = 0, 1, 2, 3, 4, 5
ROOT = 36
CHANNELS = ROOT + 3
REST = [0, 0, 0, 0, 0, 0, -5, 2, 0, 5, 2, 0, -1.9, 12, 0, 1.9, 12, 0]
GROUND = 24.0
LEG = 12.0

def ch(part, axis): return part * 6 + axis
def rest(part, axis): return REST[part * 3 + axis]

class Emote:
    def __init__(self, id_str, name, bpm, beats, ground_lock=True):
        self.id = id_str
        self.name = name
        self.beat_ms = 60000.0 / bpm
        self.period_ms = beats * self.beat_ms
        self.ground_lock = ground_lock
        self.keys = [{} for _ in range(CHANNELS)]

    def track(self, channel, *beat_values, ease='SMOOTH'):
        for i in range(0, len(beat_values), 2):
            self.key(channel, beat_values[i], beat_values[i+1], ease)
        return self

    def key(self, channel, beat, value, ease='SMOOTH'):
        rotation = channel < ROOT and channel % 6 < 3
        val = math.radians(value) if rotation else float(value)
        t = (beat * self.beat_ms) % self.period_ms
        if t < 0: t += self.period_ms
        self.keys[channel][round(t, 4)] = (val, ease)
        return self

    def compile(self):
        self.times = [None] * CHANNELS
        self.values = [None] * CHANNELS
        self.eases = [None] * CHANNELS
        self.tangents = [None] * CHANNELS
        for c in range(CHANNELS):
            kdict = self.keys[c]
            if not kdict: continue
            sorted_items = sorted(kdict.items(), key=lambda x: x[0])
            n = len(sorted_items)
            self.times[c] = [k[0] for k in sorted_items]
            self.values[c] = [k[1][0] for k in sorted_items]
            self.eases[c] = [k[1][1] for k in sorted_items]
            self.tangents[c] = [0.0] * n
            for k in range(n):
                p = (k + n - 1) % n
                q = (k + 1) % n
                tp = self.times[c][p] - (self.period_ms if p >= k else 0)
                tq = self.times[c][q] + (self.period_ms if q <= k else 0)
                self.tangents[c][k] = (self.values[c][q] - self.values[c][p]) / (tq - tp)
        return self

    def sample_track(self, c, t):
        ts = self.times[c]
        if ts is None: return 0.0
        n = len(ts)
        v = self.values[c]
        if n == 1: return v[0]
        i = n - 1
        for k in range(n):
            if ts[k] <= t: i = k
            else: break
        j = (i + 1) % n
        t0 = ts[i]
        t1 = ts[0] + self.period_ms if j == 0 else ts[j]
        tt = t + self.period_ms if t < t0 else t
        dt = t1 - t0
        u = (tt - t0) / dt
        p0 = v[i]
        p1 = v[j]
        ease = self.eases[c][i]
        if ease == 'LINEAR': return p0 + (p1 - p0) * u
        elif ease == 'EASE': return p0 + (p1 - p0) * u * u * (3 - 2 * u)
        elif ease == 'OUT':
            r = 1 - u
            return p0 + (p1 - p0) * (1 - r * r * r)
        elif ease == 'IN': return p0 + (p1 - p0) * u * u * u
        else: # SMOOTH
            u2 = u * u
            u3 = u2 * u
            return ((2 * u3 - 3 * u2 + 1) * p0 + (u3 - 2 * u2 + u) * dt * self.tangents[c][i]
                    + (-2 * u3 + 3 * u2) * p1 + (u3 - u2) * dt * self.tangents[c][j])

    def sample(self, t_ms):
        t = t_ms % self.period_ms
        if t < 0: t += self.period_ms
        out = [self.sample_track(c, t) for c in range(CHANNELS)]
        self.parent_to_body(out)
        if self.ground_lock:
            out[ROOT + 1] += GROUND - self.lowest_foot(out)
        return out

    def parent_to_body(self, o):
        bx, by, bz = o[ch(BODY, RX)], o[ch(BODY, RY)], o[ch(BODY, RZ)]
        if bx == 0 and by == 0 and bz == 0: return
        px, py, pz = o[ch(BODY, X)], o[ch(BODY, Y)], o[ch(BODY, Z)]
        for part in range(HEAD, LEFT_LEG + 1):
            if part == BODY: continue
            b = part * 6
            rx = rest(part, 0) + o[b + X] - px
            ry = rest(part, 1) + o[b + Y] - py
            rz = rest(part, 2) + o[b + Z] - pz
            cx, sx = math.cos(bx), math.sin(bx)
            cy, sy = math.cos(by), math.sin(by)
            cz, sz = math.cos(bz), math.sin(bz)
            y1 = ry * cx - rz * sx
            z1 = ry * sx + rz * cx
            x2 = rx * cy + z1 * sy
            z2 = -rx * sy + z1 * cy
            x3 = x2 * cz - y1 * sz
            y3 = x2 * sz + y1 * cz
            o[b + X] += x3 - rx
            o[b + Y] += y3 - ry
            o[b + Z] += z2 - rz
            if part != RIGHT_LEG and part != LEFT_LEG:
                o[b + RX] += bx
                o[b + RY] += by
                o[b + RZ] += bz

    def lowest_foot(self, o):
        lowest = -1e9
        for part in (RIGHT_LEG, LEFT_LEG):
            b = part * 6
            cx, sx = math.cos(o[b + RX]), math.sin(o[b + RX])
            cy, sy = math.cos(o[b + RY]), math.sin(o[b + RY])
            cz, sz = math.cos(o[b + RZ]), math.sin(o[b + RZ])
            for corner in range(4):
                fx = -2.0 if (corner & 1) == 0 else 2.0
                fz = -2.0 if (corner & 2) == 0 else 2.0
                y1 = LEG * cx - fz * sx
                z1 = LEG * sx + fz * cx
                x2 = fx * cy + z1 * sy
                y3 = x2 * sz + y1 * cz
                lowest = max(lowest, rest(part, 1) + o[b + Y] + y3)
        return lowest

def pump(b, arm, up_beat, side):
    for k in range(2):
        up = up_beat + 2 * k
        down = up + 1
        b.key(ch(arm, RX), up, -110, 'EASE').key(ch(arm, RX), down, -42, 'OUT')
        b.key(ch(arm, RY), up, 16 * side, 'EASE').key(ch(arm, RY), down, 8 * side, 'OUT')
        b.key(ch(arm, RZ), up, 2 * side, 'EASE').key(ch(arm, RZ), down, -8 * side, 'OUT')

def groove():
    b = Emote("lads:groove", "Groove", 115, 4, True)
    b.track(ch(BODY, RZ), 0, 5, 1, 4, 2, -5, 3, -4)
    b.track(ch(BODY, RY), 0, 8, 1, 5, 2, -8, 3, -5)
    b.track(ch(BODY, RX), 0, 6, .5, 1.5, 1, 6, 1.5, 1.5, 2, 6, 2.5, 1.5, 3, 6, 3.5, 1.5)
    b.track(ROOT, 0, -1.1, 1, -1.3, 2, 1.1, 3, 1.3)
    b.track(ROOT + 1, 0, .9, .5, -.3, 1, .9, 1.5, -.3, 2, .9, 2.5, -.3, 3, .9, 3.5, -.3)
    b.track(ch(HEAD, RX), 0, 9, .5, -4, 1, 9, 1.5, -4, 2, 9, 2.5, -4, 3, 9, 3.5, -4)
    b.track(ch(HEAD, RZ), 0, -4, 1, -3, 2, 4, 3, 3)
    b.track(ch(HEAD, RY), 0, -5, 1, -3, 2, 5, 3, 3)
    pump(b, RIGHT_ARM, 0, -1)
    pump(b, LEFT_ARM, 1, 1)
    for leg in (RIGHT_LEG, LEFT_LEG):
        b.track(ch(leg, RX), 0, -11, .5, -2, 1, -11, 1.5, -2, 2, -11, 2.5, -2, 3, -11, 3.5, -2)
    b.track(ch(RIGHT_LEG, RZ), 3.5, 17, 0, 6, 1, 3, 2, 1, 2.5, -1, 3, -2)
    b.track(ch(LEFT_LEG, RZ), 1.5, -17, 2, -6, 3, -3, 0, -1, .5, 1, 1, 2)
    return b.compile()

def wave():
    b = Emote("lads:wave", "Wave", 120, 4, True)
    # Right arm: raised high beside the head (close to vertical RX ~ -170°), hand sweeping side-to-side across 50°
    # 2 waves per beat cycle (16 half-swings across 4 beats, alternating every 0.25 beats between outward -48° and inward +2°).
    for k in range(16):
        beat = k * 0.25
        out = (k % 2 == 0)
        rz = -48.0 if out else 2.0
        ry = -14.0 if out else 8.0
        rx = -172.0 if out else -168.0
        b.key(ch(RIGHT_ARM, RX), beat, rx)
        b.key(ch(RIGHT_ARM, RZ), beat, rz)
        b.key(ch(RIGHT_ARM, RY), beat, ry)

    # Left arm: relaxed at side with subtle natural motion
    b.track(ch(LEFT_ARM, RZ), 0, -8, 2, -6)
    b.track(ch(LEFT_ARM, RX), 0, 2, 2, 6)

    # Torso: gentle rhythmic lean toward the waving side (lean right = positive RZ)
    b.track(ch(BODY, RZ), 0, 3.5, 2, 1.5)
    b.track(ch(BODY, RY), 0, -3.5, 2, -1.5)
    b.track(ROOT, 0, -0.6, 2, -0.2)
    b.track(ROOT + 1, 0, 0.1, 1, -0.1, 2, 0.1, 3, -0.1)

    # Head: friendly tilt toward waving side
    b.track(ch(HEAD, RZ), 0, -4.5, 2, -2.5)
    b.track(ch(HEAD, RY), 0, -4.0, 2, -2.0)
    b.track(ch(HEAD, RX), 0, -2.0, 2, 1.0)

    # Legs: grounded stance supporting the lean
    b.track(ch(RIGHT_LEG, RZ), 0, 2.5, 2, 1.0)
    b.track(ch(LEFT_LEG, RZ),  0, 1.0, 2, -1.0)

    return b.compile()

def clap():
    b = Emote("lads:clap", "Clap", 120, 4, True)
    for k in range(4):
        beat, opn = float(k), k + 0.5
        b.key(ch(RIGHT_ARM, RX), beat, -88, 'OUT').key(ch(RIGHT_ARM, RX), opn, -82, 'EASE')
        b.key(ch(LEFT_ARM, RX), beat, -88, 'OUT').key(ch(LEFT_ARM, RX), opn, -82, 'EASE')
        b.key(ch(RIGHT_ARM, RY), beat, -32, 'OUT').key(ch(RIGHT_ARM, RY), opn, -14, 'EASE')
        b.key(ch(LEFT_ARM, RY), beat, 32, 'OUT').key(ch(LEFT_ARM, RY), opn, 14, 'EASE')
        b.key(ch(RIGHT_ARM, RZ), beat, -6, 'OUT').key(ch(RIGHT_ARM, RZ), opn, -2, 'EASE')
        b.key(ch(LEFT_ARM, RZ), beat, 6, 'OUT').key(ch(LEFT_ARM, RZ), opn, 2, 'EASE')
        b.key(ch(HEAD, RX), beat, 7, 'OUT').key(ch(HEAD, RX), opn, -2, 'EASE')
        b.key(ch(BODY, RX), beat, 3, 'OUT').key(ch(BODY, RX), opn, 0, 'EASE')
        b.key(ROOT + 1, beat, 0.25, 'OUT').key(ROOT + 1, opn, -0.1, 'EASE')
        b.key(ch(RIGHT_LEG, RX), beat, -3, 'OUT').key(ch(RIGHT_LEG, RX), opn, -0.5, 'EASE')
        b.key(ch(LEFT_LEG, RX), beat, -3, 'OUT').key(ch(LEFT_LEG, RX), opn, -0.5, 'EASE')
    return b.compile()

def floss():
    b = Emote("lads:floss", "Floss", 120, 4, True)
    # Hips / Torso: decisive, energetic hip swing opposite to arms.
    b.track(ch(BODY, RZ), 0, 11.0, 1, -11.0, 2, 11.0, 3, -11.0)
    b.track(ch(BODY, RY), 0, -8.0, 1, 8.0, 2, -8.0, 3, 8.0)
    b.track(ch(BODY, RX), 0, 4.0, 1, 4.0, 2, 4.0, 3, 4.0)
    b.track(ROOT, 0, -2.4, 1, 2.4, 2, -2.4, 3, 2.4)
    # Subtle vertical dip at center of swing
    b.track(ROOT + 1, 0, -0.3, 0.5, 0.4, 1, -0.3, 1.5, 0.4, 2, -0.3, 2.5, 0.4, 3, -0.3, 3.5, 0.4)

    # Head: stays upright, looking straight ahead at the viewer
    b.track(ch(HEAD, RZ), 0, -8.0, 1, 8.0, 2, -8.0, 3, 8.0)
    b.track(ch(HEAD, RY), 0, 7.0, 1, -7.0, 2, 7.0, 3, -7.0)
    b.track(ch(HEAD, RX), 0, 2.0, 1, 2.0, 2, 2.0, 3, 2.0)

    # Arm side-to-side swing (RZ):
    b.track(ch(RIGHT_ARM, RZ), 0, -50.0, 1, 36.0, 2, -50.0, 3, 36.0)
    b.track(ch(LEFT_ARM, RZ),  0, -36.0, 1, 50.0, 2, -36.0, 3, 50.0)

    # Front vs Behind (RX):
    b.track(ch(RIGHT_ARM, RX), 0, -50.0, 1, -50.0, 2, 22.0, 3, 22.0)
    b.track(ch(LEFT_ARM, RX),  0, 22.0,  1, -50.0, 2, -50.0, 3, 22.0)

    # Arm yaw (RY):
    b.track(ch(RIGHT_ARM, RY), 0, 18.0, 1, -18.0, 2, 18.0, 3, -18.0)
    b.track(ch(LEFT_ARM, RY),  0, 18.0, 1, -18.0, 2, 18.0, 3, -18.0)

    # Legs: wide stance, weight shifting with hip sway
    b.track(ch(RIGHT_LEG, RZ), 0, 7.0, 1, -3.0, 2, 7.0, 3, -3.0)
    b.track(ch(LEFT_LEG, RZ),  0, 3.0, 1, -7.0, 2, 3.0, 3, -7.0)
    b.track(ch(RIGHT_LEG, RX), 0, -8.0, 1, -2.0, 2, -8.0, 3, -2.0)
    b.track(ch(LEFT_LEG, RX),  0, -2.0, 1, -8.0, 2, -2.0, 3, -8.0)

    return b.compile()

def render_wave_overlay(w, out_path):
    from PIL import Image, ImageDraw
    import preview as pv
    v0 = w.sample(0) # beat 0 (outward extreme: RZ = -48 deg)
    v1 = w.sample(w.beat_ms * 0.25) # beat 0.25 (inward extreme: RZ = +2 deg)

    fs_body = pv.faces(v0, parts=(0, 1, 3, 4, 5))
    fs_arm0 = pv.faces(v0, parts=(2,))
    fs_arm1 = []
    for pts, n, col in pv.faces(v1, parts=(2,)):
        tinted = (min(255, col[0] + 35), max(0, col[1] - 30), max(0, col[2] - 30))
        fs_arm1.append((pts, n, tinted))

    fs_all = fs_body + fs_arm0 + fs_arm1
    fp_arm0 = pv.faces(v0, parts=(2, 3), root=False, fp=True)
    fp_arm1 = []
    for pts, n, col in pv.faces(v1, parts=(2,), root=False, fp=True):
        tinted = (min(255, col[0] + 35), max(0, col[1] - 30), max(0, col[2] - 30))
        fp_arm1.append((pts, n, tinted))
    fp_all = fp_arm0 + fp_arm1

    cw, ch, fw = 150, 190, 240
    strip = Image.new("RGB", (cw * 3 + fw * 2, ch), (255, 255, 255))
    for col, yaw in enumerate((0, math.radians(35), math.radians(90))):
        strip.paste(pv.ortho(fs_all, cw, ch, 5.0, yaw, 24), (col * cw, 0))
    strip.paste(pv.fp(fp_all, fw, ch, 0), (3 * cw, 0))
    strip.paste(pv.fp(fp_all, fw, ch, math.radians(25)), (3 * cw + fw, 0))

    d = ImageDraw.Draw(strip)
    d.rectangle([0, 0, strip.width - 1, strip.height - 1], outline=(0, 0, 0), width=2)
    d.text((4, 4), "Overlay: beat 0.00 (brown) vs beat 0.25 (reddish) | ~50 deg swing", fill=(0, 0, 0))
    d.text((3 * cw + 4, 4), "FP pitch 0", fill=(0, 0, 0))
    d.text((3 * cw + fw + 4, 4), "FP 25 down", fill=(0, 0, 0))
    strip.save(out_path)
    print(f"Rendered overlay strip: {out_path}")

def verify_and_save(emotes, json_dir, out_dir):
    json_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)
    for emote in emotes:
        # 1. Check seamlessness:
        a = emote.sample(0)
        b = emote.sample(emote.period_ms)
        diff = max(abs(x - y) for x, y in zip(a, b))
        assert diff < 1e-4, f"{emote.id}: t=0 and t=period differ by {diff}"

        prev = emote.sample(-0.5)
        t = 0.0
        while t <= emote.period_ms + 1.0:
            curr = emote.sample(t)
            for c in range(CHANNELS):
                d = abs(curr[c] - prev[c])
                assert d < 0.015, f"{emote.id}: channel {c} jumps by {d} at t={t} ms"
            prev = curr
            t += 0.5

        print(f"PASS {emote.id}: seamless loop verified!")

        # 2. Export json frames (60 per loop, 2 loops = 120 frames):
        frames = []
        for f in range(121):
            v = emote.sample(f * emote.period_ms / 60.0)
            frames.append([round(x, 4) for x in v])

        name = emote.id.split(':')[-1]
        json_path = json_dir / f"{name}.json"
        with open(json_path, 'w') as fp:
            json.dump({"periodMs": emote.period_ms, "frames": frames}, fp)

        # 3. Call preview.py to generate PNG sheet
        png_path = out_dir / f"{name}-preview.png"
        cmd = [sys.executable, "tools/emote-preview/preview.py", str(json_path), str(png_path)]
        if name == "wave":
            cmd += ["8", "0", "1.75"]
        subprocess.check_call(cmd)
        print(f"Rendered preview: {png_path}")

        if name == "wave":
            render_wave_overlay(emote, out_dir / "wave-overlay.png")

if __name__ == '__main__':
    emotes = [groove(), wave(), clap(), floss()]
    json_dir = Path("TheLadsCore/common/build/emote-preview")
    out_dir = Path("artifacts/cosmetics")
    verify_and_save(emotes, json_dir, out_dir)
