#!/usr/bin/env python3
"""Pairs each still kill banner skin with the Valorant preview videos that show its banner (G:\\Val skins Previews):
writes videos.json {skin: [candidate video names, best first]} for measure_video.py. Developer tool.

Names are "<Skin line> <Weapon> Level N[ (Variant K Name)].mp4"; a line can have several banners (one a weapon group,
such as Bubblegum Deathwish 1-4, ORA by OneTap's five, R.E.S's three, the Ion/Magepunk/Reaver re-releases), so every
video of the line is a candidate and measure_video.py --pick keeps the one whose banner art matches best.
"""
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
FOLDER = Path('G:/Val skins Previews')
BANNERS = json.loads((HERE / 'banners.json').read_text(encoding='utf-8'))
GUNS = ['Vandal', 'Phantom', 'Operator', 'Sheriff', 'Guardian', 'Bulldog', 'Spectre', 'Odin', 'Ares', 'Judge', 'Bucky', 'Marshal',
        'Outlaw', 'Ghost', 'Classic', 'Frenzy', 'Shorty', 'Stinger', 'Bandit']
# Video name prefix (before the weapon) for each skin line; several skins can share a line.
LINES = {
    'aemondir': ['Aemondir'], 'aeris': ['Aeris'], 'araxys': ['Araxys'], 'araxysep9': ['Araxys'],
    'arcanecollectorsset': ['Arcane'], 'ayakashi': ['Ayakashi'], 'blackspyre': ['Blackspyre'], 'blackthorn': ['Blackthorn'],
    'blastx': ['BlastX'], 'bolt': ['Bolt'],
    'bubblegumdeathwish': ['Bubblegum Deathwish'], 'bubblegumdeathwish2': ['Bubblegum Deathwish'],
    'bubblegumdeathwish3': ['Bubblegum Deathwish'], 'bubblegumdeathwish4': ['Bubblegum Deathwish'],
    'champions2021': ['Champions 2021'], 'champions2022': ['Champions 2022'], 'champions2023': ['Champions 2023'],
    'champions2024': ['Champions 2024'], 'champions2025': ['Champions 2025'],
    'chronovoid': ['ChronoVoid'], 'cryostasis': ['Cryostasis'], 'cyrax': ['CYRAX'], 'divergence': ['Divergence'],
    'dolmirsrevenge': ["Dolmir's Revenge"], 'doombringer': ['Doombringer'], 'elderflame': ['Elderflame'],
    'evoridreamwings': ['Evori Dreamwings'], 'exo': ['EX.O'], 'forsaken': ['Forsaken'],
    'gaiasvengeance': ["Gaia's Vengeance"], 'gaiasvengeanceep7': ["Gaia's Vengeance"],
    'glitchpop': ['Glitchpop'], 'glitchpop20': ['Glitchpop'], 'helix': ['Helix'], 'holomeridian': ['Holo Meridian'],
    'imperium': ['Imperium'], 'ion': ['Ion'], 'ionep5': ['Ion'], 'kuronami': ['Kuronami'],
    'magepunk': ['Magepunk'], 'magepunkep3': ['Magepunk'], 'magepunkep6': ['Magepunk'],
    'mystbloom': ['Mystbloom'], 'mystbloom-v25': ['Mystbloom'], 'neofrontier': ['Neo Frontier'],
    'neptune': ['Neptune'], 'neptunev25': ['Neptune'], 'nocturnum': ['Nocturnum'], 'oni': ['Oni'], 'oniep6': ['Oni'],
    'orabyonetap-ignition': ['ORA by OneTap'], 'orabyonetap-lawyer': ['ORA by OneTap'], 'orabyonetap-raja': ['ORA by OneTap'],
    'orabyonetap-renegade': ['ORA by OneTap'], 'orabyonetap-watch': ['ORA by OneTap'],
    'origin': ['Origin'], 'overdrive': ['Overdrive'], 'phaseguard': ['Phaseguard'],
    'preludetochaos': ['Prelude to Chaos'], 'preludetochaosv25': ['Prelude to Chaos'],
    'prime': ['Prime'], 'prime20': ['Prime 2.0'], 'primordium': ['Primordium'], 'protocol781-a': ['Protocol 781-A'],
    'radiantcrisis001': ['Radiant Crisis 001'], 'reaverep5': ['Reaver'], 'reaverv26': ['Reaver'], 'recon': ['Recon'],
    'res-bazookabadger': ['Radiant Entertainment System'], 'res-dancefever': ['Radiant Entertainment System'],
    'res-knockout': ['Radiant Entertainment System'],
    'rgx11zpro': ['RGX 11z Pro'], 'rgx11zproep4': ['RGX 11z Pro'], 'rgx11zproep9': ['RGX 11z Pro'],
    'ruination': ['Ruination'], 'sentinelsoflight': ['Sentinels of Light'], 'sentinelsoflightep7': ['Sentinels of Light'],
    'singularity': ['Singularity'], 'singularityep9': ['Singularity'], 'solarstride': ['Solarstride'],
    'sovereign': ['Sovereign'], 'sovereignep8': ['Sovereign'], 'spectrum': ['Spectrum'], 'splashx': ['SplashX'],
    'valianthero': ['Valiant Hero'], 'vct': ['VCT x ', 'VCT LOCK IN', 'VCT 2025'], 'vct2025': ['VCT25 x ', 'VCT 2025'],
    'xerofang': ['XER'],
}


def main():
    files = sorted(p.name for p in FOLDER.glob('*.mp4'))
    meta = {}
    meta_path = Path(r'C:\Users\Arash\Desktop\The Lads Client Dev\Lads Client\artifacts\1.7.4\qa\play\previews-meta.json')
    if meta_path.exists():
        for name, fps, w, h, dur in json.loads(meta_path.read_text()):
            meta[name] = (fps, w, h, dur)
    out = {}
    for skin in BANNERS:
        if skin in ('reaver', 'rogue', 'default'):
            continue
        prefixes = LINES.get(skin)
        if not prefixes:
            out[skin] = []
            continue
        cands = []
        for name in files:
            stem = name[:-4]
            if not any(stem.startswith(p) for p in prefixes):
                continue
            if 'Prime 2.0' in stem and skin == 'prime':
                continue
            if '(Variant' in stem:
                continue
            m = re.search(r' (\S+) Level (\d)$', stem)
            weapon = m.group(1) if m else ''
            fps, w, h, dur = meta.get(name, (0, 0, 0, 0))
            # 1080p60 guns first, then 720p60, then 30 fps; long enough to hold the five kills.
            rank = (0 if weapon in GUNS else 1, 0 if (h == 1080 and fps == 60) else 1 if fps == 60 else 2, -dur)
            cands.append((rank, name))
        cands.sort()
        out[skin] = [n for _, n in cands]
    (HERE / 'videos.json').write_text(json.dumps(out, indent=1), encoding='utf-8')
    missing = [s for s, v in out.items() if not v]
    print(f'{len(out)} skins, {sum(1 for v in out.values() if v)} with videos; none for: {missing}')
    for s, v in out.items():
        print(f'{s}: {len(v)} candidates, first {v[0] if v else None}')


if __name__ == '__main__':
    main()
