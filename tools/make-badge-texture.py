#!/usr/bin/env python3
"""
Generate the Lads Client badge texture (64x64 RGBA) from window_256.png.

Generates:
1. Primary texture: lads_badge.png in TheLadsCore/common/src/main/resources/assets/theladscore/textures/gui/
   (Cropped candidate picked by Claude for crisp rendering at 8-9 px in tab/nametag)
2. Candidate comparison and preview files in LadsClientAPI/docs/lanes/badge/ (so they do not ship in the mod jar)
"""

import os
import sys
from PIL import Image

def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    src_icon = os.path.join(root, "TheLadsCore", "common", "src", "main", "resources", "assets", "theladscore", "icons", "window_256.png")
    texture_dir = os.path.join(root, "TheLadsCore", "common", "src", "main", "resources", "assets", "theladscore", "textures", "gui")
    docs_badge_dir = os.path.join(root, "LadsClientAPI", "docs", "lanes", "badge")
    
    if not os.path.exists(src_icon):
        print(f"Error: source icon not found at {src_icon}", file=sys.stderr)
        sys.exit(1)
        
    os.makedirs(texture_dir, exist_ok=True)
    os.makedirs(docs_badge_dir, exist_ok=True)
    
    im = Image.open(src_icon).convert("RGBA")
    
    # Candidate A: Full downscale
    cand_a = im.resize((64, 64), Image.Resampling.LANCZOS)
    path_a = os.path.join(docs_badge_dir, "lads_badge_candidate_full.png")
    cand_a.save(path_a, "PNG")
    print(f"Candidate A (full): {path_a}")
    
    # Candidate B: Crop to face and upper body (192x192 square crop centered at x=128, y=112)
    crop_box = (32, 16, 224, 208)
    cropped = im.crop(crop_box)
    cand_b = cropped.resize((64, 64), Image.Resampling.LANCZOS)
    path_b = os.path.join(docs_badge_dir, "lads_badge_candidate_cropped.png")
    cand_b.save(path_b, "PNG")
    print(f"Candidate B (cropped): {path_b}")
    
    # Save active texture as Candidate B (crisp at 8-9 px) into game assets
    active_path = os.path.join(texture_dir, "lads_badge.png")
    cand_b.save(active_path, "PNG")
    print(f"Active badge texture (64x64 RGBA): {active_path}")
    
    # Previews at 8x8 (tab) and 9x9 (nametag) saved to docs
    a_8 = cand_a.resize((8, 8), Image.Resampling.LANCZOS).resize((64, 64), Image.Resampling.NEAREST)
    b_8 = cand_b.resize((8, 8), Image.Resampling.LANCZOS).resize((64, 64), Image.Resampling.NEAREST)
    a_8.save(os.path.join(docs_badge_dir, "lads_badge_cand_a_8px_preview.png"), "PNG")
    b_8.save(os.path.join(docs_badge_dir, "lads_badge_cand_b_8px_preview.png"), "PNG")

    a_9 = cand_a.resize((9, 9), Image.Resampling.LANCZOS).resize((72, 72), Image.Resampling.NEAREST)
    b_9 = cand_b.resize((9, 9), Image.Resampling.LANCZOS).resize((72, 72), Image.Resampling.NEAREST)
    a_9.save(os.path.join(docs_badge_dir, "lads_badge_cand_a_9px_preview.png"), "PNG")
    b_9.save(os.path.join(docs_badge_dir, "lads_badge_cand_b_9px_preview.png"), "PNG")
    print("Generated 8px and 9px previews in docs/lanes/badge/.")

if __name__ == "__main__":
    main()
