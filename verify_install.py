import os
import sys
import glob
import hashlib
import subprocess

def get_shortcut_target(lnk_path):
    ps_cmd = f"(New-Object -ComObject WScript.Shell).CreateShortcut('{lnk_path}').TargetPath"
    res = subprocess.run(["powershell", "-Command", ps_cmd], capture_output=True, text=True)
    return res.stdout.strip()

def calculate_sha256(file_path):
    sha256 = hashlib.sha256()
    with open(file_path, "rb") as f:
        while chunk := f.read(8192):
            sha256.update(chunk)
    return sha256.hexdigest().upper()

def main():
    setup_path = os.path.abspath(os.path.join("Output", "TheLadsClient-Setup-BETA-0.14.exe"))
    if not os.path.exists(setup_path):
        print(f"Error: Setup installer not found at {setup_path}")
        sys.exit(1)
        
    print(f"Running installer silently: {setup_path}")
    result = subprocess.run([setup_path, "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART"], check=True)
    print(f"Installer completed with exit code: {result.returncode}")
    
    # Target directory paths
    local_app_data = os.environ.get("LOCALAPPDATA")
    launcher_dir = os.path.join(local_app_data, "The Lads Client")
    launcher_exe = os.path.join(launcher_dir, "TheLadsLauncher.exe")
    
    instance_dir = r"C:\The Lads Client"
    pack_toml = os.path.join(instance_dir, "Packwiz", "pack.toml")
    sync_marker = os.path.join(instance_dir, ".packwiz-synced")
    
    # Assert existence
    print("Verifying installation files...")
    assert os.path.exists(launcher_exe), f"Missing launcher executable: {launcher_exe}"
    assert os.path.exists(pack_toml), f"Missing pack.toml: {pack_toml}"
    assert os.path.exists(sync_marker), f"Missing sync marker: {sync_marker}"
    
    # Verify mod jar exists
    mods_glob = os.path.join(instance_dir, "mods", "TheLadsCore-*.jar")
    matching_jars = glob.glob(mods_glob)
    assert len(matching_jars) > 0, f"No TheLadsCore jar found in mods folder: {mods_glob}"
    print(f"Found mod jar: {matching_jars[0]}")
    
    # Verify sync marker hash matches pack.toml
    print("Verifying sync marker hash...")
    calculated_hash = calculate_sha256(pack_toml)
    with open(sync_marker, "r") as f:
        stored_hash = f.read().strip()
    
    print(f"Calculated pack.toml hash: {calculated_hash}")
    print(f"Stored sync marker hash:   {stored_hash}")
    assert calculated_hash == stored_hash, f"Hash mismatch! Calculated: {calculated_hash}, Stored: {stored_hash}"
    
    # Verify shortcuts
    print("Verifying shortcuts...")
    user_profile = os.environ.get("USERPROFILE")
    app_data = os.environ.get("APPDATA")
    
    desktop_lnk = os.path.join(user_profile, "Desktop", "The Lads Client.lnk")
    startmenu_lnk = os.path.join(app_data, "Microsoft", "Windows", "Start Menu", "Programs", "The Lads Client", "The Lads Client.lnk")
    
    assert os.path.exists(desktop_lnk), f"Desktop shortcut missing at: {desktop_lnk}"
    assert os.path.exists(startmenu_lnk), f"Start Menu shortcut missing at: {startmenu_lnk}"
    
    desktop_target = get_shortcut_target(desktop_lnk)
    startmenu_target = get_shortcut_target(startmenu_lnk)
    
    print(f"Desktop shortcut target:    {desktop_target}")
    print(f"Start Menu shortcut target: {startmenu_target}")
    
    assert desktop_target.lower() == launcher_exe.lower(), f"Desktop shortcut target mismatch! Expected: {launcher_exe}, Found: {desktop_target}"
    assert startmenu_target.lower() == launcher_exe.lower(), f"Start Menu shortcut target mismatch! Expected: {launcher_exe}, Found: {startmenu_target}"
    
    print("All installation checks passed successfully!")
    sys.exit(0)

if __name__ == "__main__":
    main()
