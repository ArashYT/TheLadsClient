[Setup]
AppName=The Lads Client
AppVersion=0.14.14
DefaultDirName={localappdata}\The Lads Client
DefaultGroupName=The Lads Client
UninstallDisplayIcon={app}\TheLadsLauncher.exe
Compression=lzma2
SolidCompression=yes
OutputDir=.
OutputBaseFilename=LadsClient_Installer_BETA_0.14.14
PrivilegesRequired=lowest

[Files]
Source: "TheLadsLauncher\bin\Release\net8.0-windows\win-x64\publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autodesktop}\The Lads Client"; Filename: "{app}\TheLadsLauncher.exe"
Name: "{group}\The Lads Client"; Filename: "{app}\TheLadsLauncher.exe"

[Run]
Filename: "{app}\TheLadsLauncher.exe"; Description: "Launch The Lads Client"; Flags: nowait postinstall skipifsilent
