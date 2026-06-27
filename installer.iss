#define MyAppName "The Lads Client"
#define MyAppVersion "1.0.16"
#define MyAppPublisher "ArashYT"
#define MyAppURL "https://github.com/ArashYT/TheLadsClient"
#define MyAppExeName "TheLadsLauncher.exe"

[Setup]
AppName={#MyAppName}
AppVersion={#MyAppVersion}
DefaultDirName={localappdata}\{#MyAppName}
DefaultGroupName={#MyAppName}
UninstallDisplayIcon={app}\{#MyAppExeName}
Compression=lzma2
SolidCompression=yes
OutputDir=.
OutputBaseFilename=LadsClient_Installer_BETA_{#MyAppVersion}
PrivilegesRequired=lowest

[Files]
Source: "TheLadsLauncher\bin\Release\net8.0-windows\win-x64\publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autodesktop}\The Lads Client"; Filename: "{app}\TheLadsLauncher.exe"
Name: "{group}\The Lads Client"; Filename: "{app}\TheLadsLauncher.exe"

[Run]
Filename: "{app}\TheLadsLauncher.exe"; Description: "Launch The Lads Client"; Flags: nowait postinstall skipifsilent
