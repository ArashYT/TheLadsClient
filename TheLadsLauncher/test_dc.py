import urllib.request
import urllib.parse
import json

def get_device_code(client_id, scope):
    print(f"Testing {client_id}")
    data = urllib.parse.urlencode({
        "client_id": client_id,
        "scope": scope
    }).encode("utf-8")
    
    req = urllib.request.Request("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode", data=data)
    try:
        with urllib.request.urlopen(req) as response:
            print(json.loads(response.read().decode()))
    except Exception as e:
        print(e.read().decode())

get_device_code("b7c59a74-d4b8-4c1d-8b01-fb264fc8654a", "XboxLive.signin offline_access")
get_device_code("b7c59a74-d4b8-4c1d-8b01-fb264fc8654a", "service::user.auth.xboxlive.com::MBI_SSL")
