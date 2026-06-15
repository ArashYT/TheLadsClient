import sys
import clr
sys.path.append(r'C:\Users\Arash\.nuget\packages\xboxauthnet\3.0.1\lib\netstandard2.0')
clr.AddReference('XboxAuthNet')
from XboxAuthNet.OAuth import MicrosoftOAuthBuilder
print([m for m in dir(MicrosoftOAuthBuilder) if not m.startswith('_')])
