"""Builds the kill banner FX flipbooks from the game export (PaperFlipbook + PaperSprite JSON and their PNG frames):
one sprite-sheet atlas a flipbook in killbanner/fx/<name>.png and killbanner/fx.properties with, per flipbook, the frame
rate, the cell size, the columns and the cell each played frame shows (-1: blank). The game plays a flipbook one frame
per 1/fps seconds, one-shot, each frame's whole texture stretched into the widget's box (AnimatedSpriteWidget).

python game_fx.py --export <KillBanner export dir>
"""
import argparse
import json
import math
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[1] / 'TheLadsCore/common/src/main/resources/assets/theladscore/killbanner'
MAX_WIDTH = 2048
MAX_CELL = 256   # the widgets' boxes are 256 px or less: larger frames (ORA by OneTap's 1000 px circles) are scaled down


def obj_path(ref, export):
    """'/Game/UI/InGame/KillBanner/CommonAssets/X/Y.1' -> export/CommonAssets/X/Y.json"""
    p = ref['ObjectPath'].rsplit('.', 1)[0]
    p = p.split('/KillBanner/', 1)[1]
    return export / (p + '.json')


def sprite_texture(sprite_json, export):
    if not sprite_json.exists():
        print('  no sprite json', sprite_json.name)
        return None
    for o in json.loads(sprite_json.read_text(encoding='utf-8')):
        if o.get('Type') == 'PaperSprite':
            tex = o['Properties'].get('BakedSourceTexture') or o['Properties'].get('SourceTexture')
            if not tex:
                return None
            name = tex['ObjectName'].split("'")[1].rsplit('.', 1)[-1]
            hits = list(sprite_json.parent.glob(name + '.png')) or list(export.rglob(name + '.png'))
            if not hits:
                print('  no texture', name, 'for', sprite_json.name)
            return hits[0] if hits else None
    return None


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--export', required=True)
    args = ap.parse_args()
    export = Path(args.export)
    out_dir = ASSETS / 'fx'
    out_dir.mkdir(exist_ok=True)
    lines = ['# The game\'s kill banner FX flipbooks (tools/killbanner/game_fx.py): per flipbook the frame rate, the atlas cell',
             '# size, the atlas columns and the cell of each played frame (-1 blank). Played one-shot, a frame per 1/fps s.']
    for fb in sorted(export.rglob('*.json')):
        objs = json.loads(fb.read_text(encoding='utf-8'))
        flip = next((o for o in objs if o.get('Type') == 'PaperFlipbook'), None)
        if flip is None or fb.stem == 'Fb_BlankEmpty':
            continue
        p = flip['Properties']
        fps = float(p.get('FramesPerSecond', 15))
        textures, frames = [], []
        for key in p.get('KeyFrames', []):
            run = int(key.get('FrameRun', 1))
            sprite = key.get('Sprite')
            tex = sprite_texture(obj_path(sprite, export), export) if sprite else None
            if tex is None:
                frames += [-1] * run
                continue
            if tex not in textures:
                textures.append(tex)
            frames += [textures.index(tex)] * run
        images = [Image.open(t).convert('RGBA') for t in textures]
        images = [im if max(im.size) <= MAX_CELL else im.resize((round(im.width * MAX_CELL / max(im.size)), round(im.height * MAX_CELL / max(im.size))), Image.LANCZOS) for im in images]
        if not images:
            print(f'{fb.stem}: no frames found, skipped')
            continue
        cw, ch = max(i.width for i in images), max(i.height for i in images)
        cols = max(1, min(len(images), MAX_WIDTH // cw))
        rows = math.ceil(len(images) / cols)
        atlas = Image.new('RGBA', (cols * cw, rows * ch), (0, 0, 0, 0))
        for i, im in enumerate(images):
            atlas.paste(im, ((i % cols) * cw + (cw - im.width) // 2, (i // cols) * ch + (ch - im.height) // 2))
        name = fb.stem.lower()
        atlas.save(out_dir / f'{name}.png', optimize=True)
        lines.append(f'{name}.fps={fps:g}')
        lines.append(f'{name}.cell={cw},{ch}')
        lines.append(f'{name}.cols={cols}')
        lines.append(f'{name}.frames=' + ','.join(str(f) for f in frames))
        print(f'{name}: {len(images)} cells {cw}x{ch}, {len(frames)} frames at {fps:g} fps ({len(frames) / fps:.2f} s), atlas {atlas.size}')
    (ASSETS / 'fx.properties').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print('wrote', ASSETS / 'fx.properties')


if __name__ == '__main__':
    main()
