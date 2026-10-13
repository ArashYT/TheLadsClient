import os
import sys
import zipfile
import json

def create_pack_mcmeta(description, pack_format=1):
    return json.dumps({
        "pack": {
            "pack_format": pack_format,
            "description": description
        }
    }, indent=2)

def main():
    target_dir = sys.argv[1] if len(sys.argv) > 1 else "sandbox-packs"
    os.makedirs(target_dir, exist_ok=True)

    # 1. Zip Pack 1: AlphaPack.zip
    alpha_zip = os.path.join(target_dir, "AlphaPack.zip")
    with zipfile.ZipFile(alpha_zip, "w") as zf:
        zf.writestr("pack.mcmeta", create_pack_mcmeta("Alpha PVP Texture Pack"))

    # 2. Zip Pack 2: BetaPack.zip
    beta_zip = os.path.join(target_dir, "BetaPack.zip")
    with zipfile.ZipFile(beta_zip, "w") as zf:
        zf.writestr("pack.mcmeta", create_pack_mcmeta("Beta Default Enhancement"))

    # 3. Folder Pack: FolderPack
    folder_pack = os.path.join(target_dir, "FolderPack")
    os.makedirs(folder_pack, exist_ok=True)
    with open(os.path.join(folder_pack, "pack.mcmeta"), "w") as f:
        f.write(create_pack_mcmeta("Folder Pack Clean 16x"))

    # 4. Folder: PvP (no pack.mcmeta!)
    pvp_dir = os.path.join(target_dir, "PvP")
    os.makedirs(pvp_dir, exist_ok=True)

    # Pack 1 in PvP: PvPPackOne.zip
    pvp_one = os.path.join(pvp_dir, "PvPPackOne.zip")
    with zipfile.ZipFile(pvp_one, "w") as zf:
        zf.writestr("pack.mcmeta", create_pack_mcmeta("PvP Bedwars Diamond Pack"))

    # Pack 2 in PvP: PvPPackTwo.zip
    pvp_two = os.path.join(pvp_dir, "PvPPackTwo.zip")
    with zipfile.ZipFile(pvp_two, "w") as zf:
        zf.writestr("pack.mcmeta", create_pack_mcmeta("PvP Skywars Faithful Pack"))

    print(f"Created test packs in {target_dir}")

if __name__ == "__main__":
    main()
