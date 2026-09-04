import sys
import clr
import os
sys.path.append(os.path.expanduser(r'~/.nuget/packages/xboxauthnet/3.0.1/lib/netstandard2.0'))
clr.AddReference('XboxAuthNet')
from XboxAuthNet.OAuth import MicrosoftOAuthBuilder
print([m for m in dir(MicrosoftOAuthBuilder) if not m.startswith('_')])
