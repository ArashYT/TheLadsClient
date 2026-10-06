"""Folds the same-line episode duplicates into one kill banner skin each: the later episode (or the sibling skin of a
bundle) becomes extra variants of the base skin, with its own emblem, pips, frame and ring where they differ, its colours,
its FX and its sounds. The folded constants stay in KillBannerStyle (saved configs pick skins by their order) as hidden
aliases: killbanner/merged.properties maps each to its target and variant offset, and lists the variants' sound skins
where they differ from the target's.

Run after game_data.py (which rewrites accent.properties and fx-skins.properties from the game data).

python merge_skins.py
"""
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[1] / 'TheLadsCore/common/src/main/resources/assets/theladscore/killbanner'
STYLE_JAVA = HERE.parents[1] / 'TheLadsCore/common/src/main/java/com/thelads/core/client/killbanner/KillBannerStyle.java'

# target: [(source, variant name prefix or None for a pure alias (same art and sounds), display name override)]
MERGES = {
    'araxys': [('araxysep9', None)],
    'gaiasvengeance': [('gaiasvengeanceep7', 'EP 7')],
    'ion': [('ionep5', 'EP 5')],
    'magepunk': [('magepunkep3', 'EP 3'), ('magepunkep6', 'EP 6')],
    'mystbloom': [('mystbloom-v25', None)],
    'neptune': [('neptunev25', None)],
    'oni': [('oniep6', None)],
    'preludetochaos': [('preludetochaosv25', 'V25')],
    'reaver': [('reaverep5', 'EP 5'), ('reaverv26', None)],
    'rgx11zpro': [('rgx11zproep4', 'EP 4'), ('rgx11zproep9', 'EP 9')],
    'sentinelsoflight': [('sentinelsoflightep7', 'EP 7')],
    'singularity': [('singularityep9', 'EP 9')],
    'sovereign': [('sovereignep8', 'EP 8')],
    'glitchpop': [('glitchpop20', '2.0')],
    'prime': [('prime20', '2.0')],
    'vct': [('vct2025', None)],
    'champions2021': [('champions2022', '2022'), ('champions2023', '2023')],
    'orabyonetap-ignition': [('orabyonetap-lawyer', 'LAWYER'), ('orabyonetap-raja', 'RAJA'), ('orabyonetap-renegade', 'RENEGADE'), ('orabyonetap-watch', 'WATCH')],
    'bubblegumdeathwish': [('bubblegumdeathwish2', 'VARIANT 1'), ('bubblegumdeathwish3', 'VARIANT 2'), ('bubblegumdeathwish4', 'VARIANT 3')],
    'res-bazookabadger': [('res-dancefever', 'DANCE FEVER'), ('res-knockout', 'K.NOCK O.UT!!')],
}
# a bundle's first skin becomes the bundle: its display name and its own variant's name
RENAMES = {'orabyonetap-ignition': ('ORA BY ONETAP', 'IGNITION'), 'res-bazookabadger': ('R.E.S', 'BAZOOKA BADGER'), 'champions2021': ('CHAMPIONS', '2021')}
ART = ['emblem', 'pip', 'pip_up', 'emblem_hs']
SKIN_ART = ['frame', 'ring']


def props(path):
    return [l for l in path.read_text(encoding='utf-8').splitlines()]


def kv(lines):
    return dict(l.split('=', 1) for l in lines if '=' in l and not l.startswith('#'))


def main():
    java = STYLE_JAVA.read_text(encoding='utf-8')
    shared_lines = props(ASSETS / 'shared.properties')
    shared = kv(shared_lines)
    accent_lines = props(ASSETS / 'accent.properties')
    accent = kv(accent_lines)
    fx_lines = props(ASSETS / 'fx-skins.properties')
    fx = kv(fx_lines)

    def entry(skin):
        m = re.search(r'^    ([A-Z0-9_]+)\("' + re.escape(skin) + r'", "([^"]*)", (.*?), new String\[\] \{([^}]*)\}, (null|new int\[\]\[\] \{.*?\})\),$', java, re.M)
        assert m, skin
        names = [v.strip().strip('"') for v in m.group(4).split(',')]
        return m, names

    def resolve(skin, name):
        """The file a skin's art name resolves to (through shared.properties), or None when the skin has none."""
        key = f'{skin}/{name}.png'
        target = shared.get(key, key)
        return target if (ASSETS / target).exists() else None

    merged_lines = ['# Skins folded into another (tools/killbanner/merge_skins.py): <hidden skin>=<skin it is now a variant of>,<variant offset>.',
                    '# <skin>.sounds=<sound skin per variant> where a variant keeps the sounds of the skin it came from.']
    new_shared = []
    for target, sources in MERGES.items():
        m, names = entry(target)
        colours = accent[target].split(',')
        fx_per_variant = fx.get(target, '').split('|') if target in fx else [''] * len(names)
        sounds = [target] * len(names)
        if target in RENAMES:
            display, own = RENAMES[target]
            names = [own] + names[1:]
        else:
            display = m.group(2)
        for source, prefix in sources:
            sm, snames = entry(source)
            if prefix is None:
                merged_lines.append(f'{source}={target},0')
                continue
            offset = len(names)
            merged_lines.append(f'{source}={target},{offset}')
            scolours = accent[source].split(',')
            sfx = fx.get(source, '').split('|') if source in fx else [''] * len(snames)
            for v, sname in enumerate(snames):
                n = offset + v
                names.append(prefix if len(snames) == 1 or v == 0 and sname.upper() in ('DEFAULT', 'BASE') else f'{prefix} {sname.upper()}')
                colours.append(scolours[min(v, len(scolours) - 1)])
                fx_per_variant.append(sfx[min(v, len(sfx) - 1)])
                sounds.append(source)
                for art in ART:
                    src = resolve(source, art if v == 0 else f'{art}_v{v}') or resolve(source, art)
                    if src:
                        new_shared.append(f'{target}/{art}_v{n}.png={src}')
                for art in SKIN_ART:
                    src, own = resolve(source, art), resolve(target, art)
                    if src and src != own:
                        new_shared.append(f'{target}/{art}_v{n}.png={src}')
        new_entry = f'    {m.group(1)}("{target}", "{display}", {m.group(3)}, new String[] {{{", ".join(chr(34) + n + chr(34) for n in names)}}}, {m.group(5)}),'
        java = java.replace(m.group(0), new_entry)
        accent[target] = ','.join(colours)
        if any(fx_per_variant):
            fx[target] = '|'.join(fx_per_variant)
        if any(s != target for s in sounds):
            merged_lines.append(f'{target}.sounds=' + ','.join(sounds))
        print(f'{target}: {len(names)} variants {names}')
    STYLE_JAVA.write_text(java, encoding='utf-8')
    (ASSETS / 'merged.properties').write_text('\n'.join(merged_lines) + '\n', encoding='utf-8')
    (ASSETS / 'accent.properties').write_text('\n'.join(l if '=' not in l or l.startswith('#') else f"{l.split('=', 1)[0]}={accent[l.split('=', 1)[0]]}" for l in accent_lines) + '\n', encoding='utf-8')
    kept = [l for l in fx_lines if l.startswith('#')]
    (ASSETS / 'fx-skins.properties').write_text('\n'.join(kept + [f'{k}={v}' for k, v in fx.items()]) + '\n', encoding='utf-8')
    existing = set(shared)
    added = [l for l in new_shared if l.split('=')[0] not in existing]
    (ASSETS / 'shared.properties').write_text('\n'.join(shared_lines + sorted(set(added))) + '\n', encoding='utf-8')
    print(f'{len(added)} shared art lines, {len(merged_lines) - 2} merged lines')


if __name__ == '__main__':
    main()
