import os
import sys
import json
import time
import subprocess
import threading
import http.server
import urllib.parse

PROJECT_ROOT = os.path.abspath(os.path.dirname(__file__))
TEST_INSTANCE_PATH = os.path.join(PROJECT_ROOT, "test_instance")
LAUNCHER_LOG_FILE = r"C:\Users\Arash\Desktop\launcher_debug.txt"

recorded_requests = []
recorded_requests_lock = threading.Lock()

class MockAPIRequestHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

    def do_GET(self):
        parsed_url = urllib.parse.urlparse(self.path)
        query_params = urllib.parse.parse_qs(parsed_url.query)

        with recorded_requests_lock:
            recorded_requests.append({
                'method': 'GET',
                'path': parsed_url.path,
                'headers': {k.lower(): v for k, v in self.headers.items()},
                'query': query_params
            })

        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(b'{"hits":[], "data":[]}')

# Start server
server = http.server.HTTPServer(('127.0.0.1', 0), MockAPIRequestHandler)
port = server.server_address[1]
mock_url = f"http://127.0.0.1:{port}"

thread = threading.Thread(target=server.serve_forever, daemon=True)
thread.start()

# Write settings
settings_paths = [
    os.path.join(PROJECT_ROOT, "settings.json"),
    os.path.join(PROJECT_ROOT, "bin", "Debug", "net8.0-windows", "settings.json")
]

settings_dict = {
    "ModrinthApiUrl": mock_url,
    "CurseForgeApiUrl": mock_url,
    "CurseForgeApiKey": "test_cf_key_12345",
    "FabricVersion": "fabric-loader-0.19.2-1.20.1",
    "InstancePath": TEST_INSTANCE_PATH
}

for path in settings_paths:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(settings_dict, f, indent=4)

# Run launcher
cmd = ["dotnet", "run", "--project", os.path.join(PROJECT_ROOT, "TheLadsLauncher.csproj"), "--", "--auto-launch-offline"]
proc = subprocess.Popen(cmd, cwd=PROJECT_ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

# Wait 4 seconds
time.sleep(4)

# Kill
subprocess.run(['taskkill', '/F', '/T', '/PID', str(proc.pid)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
proc.wait()

print("Recorded requests:")
for r in recorded_requests:
    print(f"Path: {r['path']}")
    print(f"Query: {r['query']}")
    print(f"Headers: {r['headers']}")
    print("-" * 40)
