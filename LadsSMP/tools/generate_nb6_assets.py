import os
import struct
import zlib
import json

BASE = r"C:\Users\Arash\AppData\Local\Temp\w1911nb6\LadsSMP"
TEX_DIR = os.path.join(BASE, r"src\main\resources\assets\ladssmp\textures\item")
MODELS_DIR = os.path.join(BASE, r"src\main\resources\assets\ladssmp\models\item")
ITEMS_DIR = os.path.join(BASE, r"src\main\resources\assets\ladssmp\items")
LANG_DIR = os.path.join(BASE, r"src\main\resources\assets\ladssmp\lang")
SMELTING_DIR = os.path.join(BASE, r"src\main\resources\data\ladssmp\recipe\smelting")
SMOKING_DIR = os.path.join(BASE, r"src\main\resources\data\ladssmp\recipe\smoking")
CAMPFIRE_DIR = os.path.join(BASE, r"src\main\resources\data\ladssmp\recipe\campfire_cooking")
CRAFTING_DIR = os.path.join(BASE, r"src\main\resources\data\ladssmp\recipe\crafting")

for d in [TEX_DIR, MODELS_DIR, ITEMS_DIR, LANG_DIR, SMELTING_DIR, SMOKING_DIR, CAMPFIRE_DIR, CRAFTING_DIR]:
    os.makedirs(d, exist_ok=True)

def write_png(path, width, height, pixels):
    """Writes a 16x16 RGBA PNG using standard zlib and struct."""
    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0) # filter type none
        for x in range(width):
            r, g, b, a = pixels[y][x]
            raw_data.extend([r, g, b, a])
    
    compressed = zlib.compress(bytes(raw_data), 9)
    
    ihdr_data = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    ihdr_crc = struct.pack(">I", zlib.crc32(b"IHDR" + ihdr_data))
    ihdr_chunk = struct.pack(">I", len(ihdr_data)) + b"IHDR" + ihdr_data + ihdr_crc
    
    idat_crc = struct.pack(">I", zlib.crc32(b"IDAT" + compressed))
    idat_chunk = struct.pack(">I", len(compressed)) + b"IDAT" + compressed + idat_crc
    
    iend_chunk = struct.pack(">I", 0) + b"IEND" + struct.pack(">I", zlib.crc32(b"IEND"))
    
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(ihdr_chunk)
        f.write(idat_chunk)
        f.write(iend_chunk)

# Color palettes: (R, G, B, A)
FISH_SPECIES = {
    "bass": {
        "name": "Striped Bass",
        "primary": (74, 98, 56, 255),
        "belly": (180, 190, 150, 255),
        "stripe": (35, 48, 25, 255),
        "fin": (90, 115, 70, 255)
    },
    "bluegill": {
        "name": "Bluegill Sunfish",
        "primary": (38, 112, 120, 255),
        "belly": (220, 150, 45, 255),
        "stripe": (20, 60, 75, 255),
        "fin": (45, 140, 130, 255)
    },
    "carp": {
        "name": "Golden Carp",
        "primary": (210, 145, 40, 255),
        "belly": (240, 210, 120, 255),
        "stripe": (145, 90, 20, 255),
        "fin": (190, 80, 25, 255)
    },
    "trout": {
        "name": "Rainbow Trout",
        "primary": (110, 135, 95, 255),
        "belly": (210, 110, 140, 255), # rosy pink lateral band
        "stripe": (50, 65, 40, 255),
        "fin": (140, 155, 120, 255)
    },
    "glacier_cod": {
        "name": "Glacier Cod",
        "primary": (140, 210, 235, 255),
        "belly": (230, 245, 255, 255),
        "stripe": (90, 160, 200, 255),
        "fin": (180, 230, 250, 255)
    },
    "frostbite_flounder": {
        "name": "Frostbite Flounder",
        "primary": (120, 155, 185, 255),
        "belly": (200, 225, 240, 255),
        "stripe": (70, 95, 130, 255),
        "fin": (150, 180, 205, 255)
    },
    "tuna": {
        "name": "Bluefin Tuna",
        "primary": (30, 55, 95, 255),
        "belly": (175, 195, 215, 255),
        "stripe": (230, 190, 40, 255), # gold finlet accents
        "fin": (45, 80, 135, 255)
    },
    "snapper": {
        "name": "Red Snapper",
        "primary": (210, 50, 50, 255),
        "belly": (245, 160, 160, 255),
        "stripe": (150, 30, 30, 255),
        "fin": (235, 75, 75, 255)
    },
    "sailfish": {
        "name": "Sunlit Sailfish",
        "primary": (40, 75, 130, 255),
        "belly": (160, 210, 235, 255),
        "stripe": (20, 40, 85, 255),
        "fin": (25, 130, 190, 255) # cobalt sail
    },
    "catfish": {
        "name": "Mud Catfish",
        "primary": (85, 75, 60, 255),
        "belly": (175, 165, 140, 255),
        "stripe": (50, 40, 30, 255),
        "fin": (110, 95, 80, 255)
    },
    "bullhead": {
        "name": "Mangrove Bullhead",
        "primary": (65, 70, 50, 255),
        "belly": (145, 150, 115, 255),
        "stripe": (40, 45, 30, 255),
        "fin": (85, 95, 65, 255)
    },
    "abyssal_angler": {
        "name": "Abyssal Angler",
        "primary": (40, 30, 50, 255),
        "belly": (70, 55, 85, 255),
        "stripe": (0, 240, 210, 255), # glowing bioluminescent lure
        "fin": (55, 45, 65, 255)
    },
    "cave_tetra": {
        "name": "Blind Cave Tetra",
        "primary": (220, 195, 205, 255),
        "belly": (245, 230, 235, 255),
        "stripe": (180, 155, 165, 255),
        "fin": (230, 215, 220, 255)
    },
    "lunar_guppy": {
        "name": "Lunar Guppy",
        "primary": (45, 50, 85, 255),
        "belly": (210, 225, 255, 255),
        "stripe": (140, 175, 240, 255),
        "fin": (80, 95, 150, 255)
    },
    "storm_perch": {
        "name": "Storm Perch",
        "primary": (60, 110, 130, 255),
        "belly": (235, 215, 65, 255), # lightning gold
        "stripe": (30, 60, 75, 255),
        "fin": (90, 165, 195, 255)
    },
    "magma_mackerel": {
        "name": "Magma Mackerel",
        "primary": (175, 45, 20, 255),
        "belly": (245, 140, 30, 255), # molten core
        "stripe": (70, 15, 10, 255),
        "fin": (220, 80, 25, 255)
    },
    "crimson_fangjaw": {
        "name": "Crimson Fangjaw",
        "primary": (140, 25, 40, 255),
        "belly": (205, 60, 75, 255),
        "stripe": (65, 10, 20, 255),
        "fin": (180, 35, 55, 255)
    },
    "warped_guppy": {
        "name": "Warped Guppy",
        "primary": (22, 125, 130, 255),
        "belly": (65, 205, 185, 255),
        "stripe": (12, 60, 70, 255),
        "fin": (35, 165, 160, 255)
    },
    "void_tetra": {
        "name": "Void Tetra",
        "primary": (30, 20, 45, 255),
        "belly": (160, 75, 210, 255), # void glow
        "stripe": (15, 10, 25, 255),
        "fin": (75, 45, 105, 255)
    },
    "chorus_driftfish": {
        "name": "Chorus Driftfish",
        "primary": (135, 95, 155, 255),
        "belly": (210, 175, 225, 255),
        "stripe": (75, 45, 90, 255),
        "fin": (165, 125, 185, 255)
    }
}

def generate_fish_sprite(colors, is_cooked=False):
    """Draws a clean-room 16x16 pixel fish sprite with shading."""
    grid = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]
    
    p = colors["primary"]
    b = colors["belly"]
    s = colors["stripe"]
    f = colors["fin"]
    
    if is_cooked:
        # Caramelized / roasted golden baked palette
        p = (175, 115, 60, 255)
        b = (225, 185, 130, 255)
        s = (110, 65, 30, 255) # grill marks
        f = (145, 85, 40, 255)
        
    # Main fish body outline & fill (horizontal swimming fish facing left)
    # Head / snout
    grid[7][2] = p
    grid[8][2] = b
    grid[6][3] = p
    grid[7][3] = (255, 255, 255, 255) if not is_cooked else (60, 35, 20, 255) # eye
    grid[8][3] = b
    grid[9][3] = b
    
    # Mid-body
    for col in range(4, 11):
        grid[5][col] = p
        grid[6][col] = p
        grid[7][col] = s if (col % 2 == 0) else p
        grid[8][col] = b
        grid[9][col] = b
        grid[10][col] = f
        
    # Top dorsal fin
    for col in range(6, 10):
        grid[4][col] = f
        
    # Tail taper
    grid[6][11] = p
    grid[7][11] = p
    grid[8][11] = b
    grid[7][12] = p
    grid[8][12] = b
    
    # Tail fin (forked)
    grid[5][13] = f
    grid[6][13] = f
    grid[7][13] = f
    grid[8][13] = f
    grid[9][13] = f
    grid[4][14] = f
    grid[5][14] = f
    grid[9][14] = f
    grid[10][14] = f
    
    # Pectoral fin
    grid[9][5] = f
    grid[9][6] = f
    
    return grid

def generate_journal_sprite():
    """Draws a clean-room 16x16 leather-bound journal with gold fish emblem."""
    grid = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]
    # Dark blue leather cover
    c = (28, 45, 82, 255)
    g = (220, 180, 50, 255) # gold trim
    r = (185, 30, 40, 255)  # red ribbon
    p = (235, 220, 190, 255) # page edges
    
    for y in range(2, 14):
        for x in range(3, 13):
            grid[y][x] = c
            
    # Gold border corners
    grid[2][3] = g
    grid[2][4] = g
    grid[3][3] = g
    grid[2][11] = g
    grid[2][12] = g
    grid[3][12] = g
    grid[13][3] = g
    grid[13][4] = g
    grid[12][3] = g
    grid[13][11] = g
    grid[13][12] = g
    grid[12][12] = g
    
    # Gold fish emblem in center
    grid[7][7] = g
    grid[7][8] = g
    grid[8][6] = g
    grid[8][7] = g
    grid[8][8] = g
    grid[8][9] = g
    grid[9][8] = g
    
    # Page edges on right
    for y in range(3, 13):
        grid[y][13] = p
        
    # Ribbon bookmark hanging from bottom
    grid[13][8] = r
    grid[14][8] = r
    grid[15][8] = r
    
    return grid

def generate_crate_sprite():
    """Draws a clean-room 16x16 wooden supply crate with iron bands."""
    grid = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]
    w = (145, 105, 60, 255) # wood plank
    d = (100, 70, 35, 255)  # dark wood
    m = (180, 185, 195, 255) # iron band
    
    for y in range(2, 14):
        for x in range(2, 14):
            grid[y][x] = w
            
    # Dark border
    for x in range(2, 14):
        grid[2][x] = d
        grid[13][x] = d
    for y in range(2, 14):
        grid[y][2] = d
        grid[y][13] = d
        
    # Metal reinforcement cross/bands
    for x in range(2, 14):
        grid[5][x] = m
        grid[10][x] = m
    for y in range(2, 14):
        grid[y][5] = m
        grid[y][10] = m
        
    return grid

def generate_line_sprite():
    """Draws a clean-room 16x16 spool of fireproof fishing line."""
    grid = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]
    o = (30, 25, 35, 255)   # obsidian spool
    f = (235, 95, 25, 255)  # fiery orange line
    b = (255, 180, 50, 255) # bright core
    
    # Spool flanges
    for x in range(4, 12):
        grid[3][x] = o
        grid[4][x] = o
        grid[11][x] = o
        grid[12][x] = o
        
    # Wound line
    for y in range(5, 11):
        for x in range(5, 11):
            grid[y][x] = f if (x + y) % 2 == 0 else b
            
    # Loose line filament trailing off
    grid[10][11] = f
    grid[11][12] = f
    grid[12][13] = f
    grid[13][13] = f
    
    return grid

# Write all fish sprites
lang_entries = {
    "itemGroup.ladssmp.fishing": "The Lads SMP Fishing",
    "item.ladssmp.fishing_journal": "Fishing Journal",
    "item.ladssmp.supply_crate": "Fishing Supply Crate",
    "item.ladssmp.fireproof_line": "Fireproof Fishing Line"
}

all_item_ids = []

for species_id, info in FISH_SPECIES.items():
    raw_id = species_id
    cooked_id = f"cooked_{species_id}"
    all_item_ids.extend([raw_id, cooked_id])
    
    # 1. Textures
    raw_pix = generate_fish_sprite(info, is_cooked=False)
    write_png(os.path.join(TEX_DIR, f"{raw_id}.png"), 16, 16, raw_pix)
    
    cooked_pix = generate_fish_sprite(info, is_cooked=True)
    write_png(os.path.join(TEX_DIR, f"{cooked_id}.png"), 16, 16, cooked_pix)
    
    # 2. Lang
    lang_entries[f"item.ladssmp.{raw_id}"] = info["name"]
    lang_entries[f"item.ladssmp.{cooked_id}"] = f"Cooked {info['name']}"
    
    # 3. Recipes (Smelting, Smoking, Campfire)
    smelt_json = {
        "type": "minecraft:smelting",
        "category": "food",
        "ingredient": f"ladssmp:{raw_id}",
        "result": { "id": f"ladssmp:{cooked_id}" },
        "experience": 0.35,
        "cookingtime": 200
    }
    with open(os.path.join(SMELTING_DIR, f"{cooked_id}.json"), "w") as f:
        json.dump(smelt_json, f, indent=2)
        
    smoke_json = {
        "type": "minecraft:smoking",
        "category": "food",
        "ingredient": f"ladssmp:{raw_id}",
        "result": { "id": f"ladssmp:{cooked_id}" },
        "experience": 0.35,
        "cookingtime": 100
    }
    with open(os.path.join(SMOKING_DIR, f"{cooked_id}.json"), "w") as f:
        json.dump(smoke_json, f, indent=2)
        
    camp_json = {
        "type": "minecraft:campfire_cooking",
        "category": "food",
        "ingredient": f"ladssmp:{raw_id}",
        "result": { "id": f"ladssmp:{cooked_id}" },
        "experience": 0.35,
        "cookingtime": 600
    }
    with open(os.path.join(CAMPFIRE_DIR, f"{cooked_id}.json"), "w") as f:
        json.dump(camp_json, f, indent=2)

# Special item sprites
write_png(os.path.join(TEX_DIR, "fishing_journal.png"), 16, 16, generate_journal_sprite())
write_png(os.path.join(TEX_DIR, "supply_crate.png"), 16, 16, generate_crate_sprite())
write_png(os.path.join(TEX_DIR, "fireproof_line.png"), 16, 16, generate_line_sprite())
all_item_ids.extend(["fishing_journal", "supply_crate", "fireproof_line"])

# Special item recipes
journal_recipe = {
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "key": {
        "B": "minecraft:book",
        "S": "minecraft:string",
        "C": "minecraft:cod"
    },
    "pattern": [
        " S ",
        "SBC",
        " S "
    ],
    "result": {
        "id": "ladssmp:fishing_journal"
    }
}
with open(os.path.join(CRAFTING_DIR, "fishing_journal.json"), "w") as f:
    json.dump(journal_recipe, f, indent=2)

line_recipe = {
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "key": {
        "B": "minecraft:blaze_powder",
        "S": "minecraft:string",
        "I": "minecraft:iron_nugget"
    },
    "pattern": [
        " I ",
        "SBS",
        " I "
    ],
    "result": {
        "id": "ladssmp:fireproof_line",
        "count": 2
    }
}
with open(os.path.join(CRAFTING_DIR, "fireproof_line.json"), "w") as f:
    json.dump(line_recipe, f, indent=2)

# Models and Item definitions for all items
for item_id in all_item_ids:
    model_json = {
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": f"ladssmp:item/{item_id}"
        }
    }
    with open(os.path.join(MODELS_DIR, f"{item_id}.json"), "w") as f:
        json.dump(model_json, f, indent=2)
        
    item_def = {
        "model": {
            "type": "minecraft:model",
            "model": f"ladssmp:item/{item_id}"
        }
    }
    with open(os.path.join(ITEMS_DIR, f"{item_id}.json"), "w") as f:
        json.dump(item_def, f, indent=2)

# Write en_us.json
lang_file = os.path.join(LANG_DIR, "en_us.json")
existing_lang = {}
if os.path.exists(lang_file):
    try:
        with open(lang_file, "r") as f:
            existing_lang = json.load(f)
    except:
        pass
existing_lang.update(lang_entries)
with open(lang_file, "w") as f:
    json.dump(existing_lang, f, indent=2)

print(f"Generated {len(all_item_ids)} items, models, textures, recipes, and lang entries!")
