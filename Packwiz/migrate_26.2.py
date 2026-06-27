import os
import re
import urllib.request
import json
import time

MODS_DIR = "mods"
MC_VERSION = "26.2"

def search_modrinth(query):
    # Try to find a project that matches exactly or very closely.
    url = f"https://api.modrinth.com/v2/search?query={urllib.parse.quote(query)}&facets=[[%22categories:fabric%22]]&limit=1"
    req = urllib.request.Request(url, headers={'User-Agent': 'ArashYT/TheLadsClient'})
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode())
            if data["hits"]:
                return data["hits"][0]["project_id"]
    except Exception as e:
        print(f"Error searching for {query}: {e}")
    return None

def get_26_2_version(project_id):
    url = f"https://api.modrinth.com/v2/project/{project_id}/version?game_versions=[%22{MC_VERSION}%22]&loaders=[%22fabric%22]"
    req = urllib.request.Request(url, headers={'User-Agent': 'ArashYT/TheLadsClient'})
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode())
            if data:
                return data[0] # Returns the latest version object
    except Exception as e:
        pass
    return None

def main():
    if not os.path.exists(MODS_DIR):
        print("mods/ directory not found!")
        return

    jars = [f for f in os.listdir(MODS_DIR) if f.endswith(".jar")]
    print(f"Found {len(jars)} jars to process.")

    for jar in jars:
        # Avoid removing TheLadsCore because we ported it ourselves
        if "TheLadsCore" in jar:
            continue
            
        old_path = os.path.join(MODS_DIR, jar)
        
        # Heuristic to get mod name: take everything before the first hyphen followed by a digit
        # e.g. "fabric-api-0.151.0" -> "fabric-api"
        # "sodium-fabric-0.8.12" -> "sodium-fabric" (we can search this)
        m = re.match(r"^([a-zA-Z0-9_\-]+?)(?:-[\d\.+]+| \(| v?[\d\.+]+)", jar)
        mod_name = m.group(1) if m else jar.replace(".jar", "")
        
        # Strip trailing hyphens and "fabric" keywords to improve search
        mod_name = re.sub(r"-fabric$", "", mod_name, flags=re.IGNORECASE)
        mod_name = re.sub(r"_fabric$", "", mod_name, flags=re.IGNORECASE)
        mod_name = mod_name.strip("-")

        print(f"Processing '{jar}' (Extracted name: {mod_name})...")
        
        project_id = search_modrinth(mod_name)
        if not project_id:
            print(f"  -> Could not find project on Modrinth for {mod_name}. Deleting incompatible old jar.")
            os.remove(old_path)
            continue
            
        version_data = get_26_2_version(project_id)
        if not version_data:
            print(f"  -> Mod '{mod_name}' has no {MC_VERSION} build! Deleting incompatible old jar.")
            os.remove(old_path)
            continue
            
        # Download the new jar!
        file_info = version_data["files"][0]
        download_url = file_info["url"]
        new_filename = file_info["filename"]
        new_path = os.path.join(MODS_DIR, new_filename)
        
        if old_path != new_path:
            print(f"  -> Downloading 26.2 update: {new_filename}...")
            try:
                urllib.request.urlretrieve(download_url, new_path)
                os.remove(old_path)
            except Exception as e:
                print(f"  -> Download failed: {e}")
        else:
            print(f"  -> Already up to date (somehow)?")
        
        time.sleep(0.5) # respect rate limit

if __name__ == "__main__":
    main()
