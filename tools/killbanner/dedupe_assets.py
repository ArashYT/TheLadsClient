"""Kill banner assets: share identical copies instead of shipping them twice.

PNGs with identical bytes go into killbanner/shared.properties ("skin/file.png=other/file.png", read by
KillBannerStyle.asset), sounds with identical decoded audio point their sounds.json event at one file. The copies no
longer referenced are written to the list file given as the argument, to remove by literal path
(git rm --pathspec-from-file=<list>). Run after download_all_assets.py.
"""
import collections
import hashlib
import json
import subprocess
import sys
from pathlib import Path

import imageio_ffmpeg

ASSETS = Path(__file__).resolve().parents[2] / 'TheLadsCore/common/src/main/resources/assets/theladscore'
BANNERS, SOUNDS = ASSETS / 'killbanner', ASSETS / 'sounds/killbanner'
SHARED, SOUNDS_JSON = BANNERS / 'shared.properties', ASSETS / 'sounds.json'


def groups(files, key):
    by = collections.defaultdict(list)
    for f in files:
        by[key(f)].append(f)
    return [sorted(g) for g in by.values() if len(g) > 1]


def pcm(f):
    return hashlib.sha1(subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-v', 'error', '-i', str(f), '-f', 's16le', '-'],
                                       capture_output=True, check=True).stdout).hexdigest()


def main(remove_list):
    shared = {}
    if SHARED.exists():
        for line in SHARED.read_text(encoding='utf-8').splitlines():
            if '=' in line and not line.startswith('#'):
                k, v = line.split('=', 1)
                shared[k] = v
    removed = []
    pngs = sorted(p for p in BANNERS.glob('*/*.png'))
    for keep, *copies in groups(pngs, lambda p: hashlib.sha1(p.read_bytes()).hexdigest()):
        for c in copies:
            shared[c.relative_to(BANNERS).as_posix()] = keep.relative_to(BANNERS).as_posix()
            removed.append(c)
    SHARED.write_text('# Identical kill banner art kept once: skin/file=the copy shipped (tools/killbanner/dedupe_assets.py)\n'
                      + ''.join(f'{k}={v}\n' for k, v in sorted(shared.items())), encoding='utf-8')

    sounds = json.loads(SOUNDS_JSON.read_text(encoding='utf-8'))
    for keep, *copies in groups(sorted(SOUNDS.glob('*.ogg')), pcm):
        for c in copies:
            for event in sounds.values():
                event['sounds'] = [f'theladscore:killbanner/{keep.stem}' if s == f'theladscore:killbanner/{c.stem}' else s
                                   for s in event['sounds']]
            removed.append(c)
    with open(SOUNDS_JSON, 'w', encoding='utf-8', newline='\r\n') as out:
        json.dump(sounds, out, indent=2)

    Path(remove_list).write_text(''.join(p.relative_to(ASSETS.parents[6]).as_posix() + '\n' for p in removed), encoding='utf-8')
    print(f'{len(removed)} copies now shared; remove them with: git rm --pathspec-from-file={remove_list}')


if __name__ == '__main__':
    main(sys.argv[1])
