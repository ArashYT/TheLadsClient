"""Writes each kill banner skin's data from the game's own KillBannerData (out/game-map.json from game_map.py):
  killbanner/accent.properties   PrimaryColor per variant: the colour the pips' hover, the FX and the HEADSHOT box take
  killbanner/<skin>/pip_up.png   the pip's Up texture (KillWheel_Slice_Default) per variant, under the coloured hover
  killbanner/<skin>/emblem_hs.png  the headshot badge (HeadShot_Badge) per variant, where the skin has one
and out/game-data.md, the table of what each skin got. Variants are matched by the emblem texture; the few the art
cannot tell apart are listed in OVERRIDES.

python game_data.py --export <KillBanner export dir>
"""
import argparse
import json
import re
import shutil
from pathlib import Path

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[1] / 'TheLadsCore/common/src/main/resources/assets/theladscore/killbanner'
STYLE_JAVA = HERE.parents[1] / 'TheLadsCore/common/src/main/java/com/thelads/core/client/killbanner/KillBannerStyle.java'
# client skin -> the game's data class per variant, where the emblem does not tell (same emblem for every variant, a
# swap skin with no emblem, a strip, or a class in another folder).
OVERRIDES = {
    'default': ['KillBannerData_Parent', 'KillBannerData_Parent'],
    'reaver': ['KillBannerData_Soulstealer', 'KillBannerData_Soulstealer3_v1', 'KillBannerData_Soulstealer3_v2', 'KillBannerData_Soulstealer3_v3'],
    'rogue': ['KillBannerData_Rogue', 'KillBannerData_Rogue_v1', 'KillBannerData_Rogue_v2', 'KillBannerData_Rogue_v3'],
    'champions2024': ['KillBannerData_Champs24'],
    'champions2025': ['KillBannerData_Champs26_Dragon'],
    'phaseguard': ['KillBannerData_Commando', 'KillBannerData_Commando_v1', 'KillBannerData_Commando_v2', 'KillBannerData_Commando_v3'],
    'preludetochaos': ['KillBannerData_Demonstone'],
    'preludetochaosv25': ['KillBannerData_Demonstone', 'KillBannerData_Demonstone_v1', 'KillBannerData_Demonstone_v2', 'KillBannerData_Demonstone_v3'],
    'bolt': ['KillBannerData_Bolt', 'KillBannerData_Bolt_v1', 'KillBannerData_Bolt_v2', 'KillBannerData_Bolt_v3'],
    'araxys': ['KillBannerData_Antares_Standard', 'KillBannerData_Antares_v1', 'KillBannerData_Antares_v2', 'KillBannerData_Antares_v3'],
    'araxysep9': ['KillBannerData_Antares_Standard', 'KillBannerData_Antares_v1', 'KillBannerData_Antares_v2', 'KillBannerData_Antares_v3'],
    'sovereign': ['KillBannerData_Sovereign1', 'KillBannerData_Sovereign1_V1', 'KillBannerData_Sovereign1_V2', 'KillBannerData_Sovereign1_V3'],
    'sovereignep8': ['KillBannerData_SOV2', 'KillBannerData_SOV2_v1', 'KillBannerData_SOV2_v2', 'KillBannerData_SOV2_v3'],
    'rgx11zpro': ['KillBannerData_Afterglow'],
    'rgx11zproep9': ['KillBannerData_Afterglow2', 'KillBannerData_Afterglow2_V1', 'KillBannerData_Afterglow2_V2', 'KillBannerData_Afterglow2_V3'],
    'reaverep5': ['KillBannerData_Soulstealer'],
    'reaverv26': ['KillBannerData_Soulstealer3', 'KillBannerData_Soulstealer3_v1', 'KillBannerData_Soulstealer3_v2', 'KillBannerData_Soulstealer3_v3'],
    'champions2021': ['KillBannerData_Esports'],
    'ionep5': ['KillBannerData_Oblivion', 'KillBannerData_Oblivion_v1', 'KillBannerData_Oblivion_v2', 'KillBannerData_Oblivion_v3'],
    'magepunkep6': ['KillBannerData_Magepunk', 'KillBannerData_Magepunk_v1', 'KillBannerData_Magepunk_v2', 'KillBannerData_Magepunk_v3'],
    'sentinelsoflightep7': ['KillBannerData_SOL', 'KillBannerData_SOL_v1', 'KillBannerData_SOL_v2', 'KillBannerData_SOL_v3'],
    'singularityep9': ['KillBannerData_Edge', 'KillBannerData_Edge_V1', 'KillBannerData_Edge_V2', 'KillBannerData_Edge_V3'],
    'gaiasvengeanceep7': ['KillBannerData_Ashen', 'KillBannerData_Ashen_v1', 'KillBannerData_Ashen_v2', 'KillBannerData_Ashen_v3'],
}


def variant_counts():
    """Client skin id -> how many variants its KillBannerStyle constant lists (Banner Swap: one accent)."""
    out = {}
    for m in re.finditer(r'^    [A-Z0-9_]+\("([^"]+)", "[^"]*", Type\.(\w+),.*?new String\[\] \{([^}]*)\}', STYLE_JAVA.read_text(encoding='utf-8'), re.M):
        out[m.group(1)] = 1 if m.group(2) == 'BANNER_SWAP' else len([v for v in m.group(3).split(',') if v.strip()])
    return out


def hex_of(color):
    if isinstance(color, dict):
        if 'Hex' in color:
            return color['Hex'][:6].upper()
        return '%02X%02X%02X' % tuple(min(255, max(0, round(float(color.get(c, 0)) ** (1 / 2.2) * 255))) for c in 'RGB')
    return None


def find_texture(export, name):
    hits = list(export.rglob(name + '.png'))
    return hits[0] if hits else None


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--export', required=True)
    ap.add_argument('--map', default=str(HERE / 'out/game-map.json'))
    args = ap.parse_args()
    export = Path(args.export)
    gm = json.loads(Path(args.map).read_text(encoding='utf-8'))
    data, mapping = gm['data'], gm['mapping']
    counts = variant_counts()
    shared = dict(l.split('=', 1) for l in (ASSETS / 'shared.properties').read_text(encoding='utf-8').splitlines()
                  if '=' in l and not l.startswith('#'))
    # a skin with no art folder of its own draws another skin's art (shared.properties): it takes that skin's data
    donor = {skin: shared[f'{skin}/emblem.png'].split('/')[0] for skin in counts if not (ASSETS / skin).is_dir() and f'{skin}/emblem.png' in shared}
    shared_lines = []
    accents, table, missing = [], ['| skin | variant | game data | colour | radius | headshot offset | pip Up | headshot badge |', '|---|---|---|---|---|---|---|---|'], []
    for skin, n in counts.items():
        classes = []
        for v in range(n):
            cls = None
            if skin in OVERRIDES and v < len(OVERRIDES[skin]):
                cls = OVERRIDES[skin][v]
            else:
                source = donor.get(skin, skin)
                hits = mapping.get(source, {}).get('variants', {}).get(str(v)) or []
                folders = mapping.get(source, {}).get('folders', {})
                main_folder = max(folders, key=folders.get) if folders else None
                hits = sorted(hits, key=lambda c: 0 if data[c]['folder'] == main_folder else 1)
                cls = hits[0] if hits else None
            if cls is None or cls not in data:
                missing.append(f'{skin} v{v} ({cls})')
                classes.append(None)
                continue
            classes.append(cls)
        colours = []
        last = None
        for v, cls in enumerate(classes):
            r = data[cls]['resolved'] if cls else {}
            colour = hex_of(r.get('PrimaryColor')) or last or 'FFFFFF'
            last = colour
            colours.append(colour)
            up = r.get('KillWheel_Slice_Default')
            badge = r.get('HeadShot_Badge')
            radius = r.get('KillWheel_Slice_Radius')
            off = r.get('Badge_HeadshotOffset')
            copied = []
            for tex, name in ((up, 'pip_up'), (badge, 'emblem_hs')):
                if not tex or not isinstance(tex, str):
                    continue
                src = find_texture(export, tex)
                if src is None:
                    continue
                file = f'{name}.png' if v == 0 else f'{name}_v{v}.png'
                dst = ASSETS / donor.get(skin, skin) / file
                if dst.parent.is_dir():
                    shutil.copyfile(src, dst)
                    copied.append(dst.name)
                    if skin in donor:
                        shared_lines.append(f'{skin}/{file}={donor[skin]}/{file}')
            table.append(f'| {skin} | {v} | {cls} | {colour} | {radius} | {off} | {up if up else ""} | {badge if badge else ""} |')
        accents.append(f'{skin}={",".join(colours)}')
    (ASSETS / 'accent.properties').write_text(
        '# Each kill banner skin\'s PrimaryColor per variant, from the game\'s own KillBannerData (tools/killbanner/game_data.py):\n'
        '# the colour of its pips\' hover, its FX and its HEADSHOT box.\n' + '\n'.join(accents) + '\n', encoding='utf-8')
    text = (ASSETS / 'shared.properties').read_text(encoding='utf-8').rstrip('\n')
    new = [l for l in shared_lines if l.split('=')[0] not in shared]
    if new:
        (ASSETS / 'shared.properties').write_text(text + '\n' + '\n'.join(sorted(new)) + '\n', encoding='utf-8')
    (HERE / 'out/game-data.md').write_text('# Kill banner skins: the game data each one plays with\n\n' + '\n'.join(table) + '\n'
                                           + ('\nMissing: ' + ', '.join(missing) + '\n' if missing else ''), encoding='utf-8')
    print(f'{len(accents)} skins; missing: {missing}')


if __name__ == '__main__':
    main()
