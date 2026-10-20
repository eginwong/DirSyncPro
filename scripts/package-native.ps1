$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

New-Item -Path distuild -ItemType Directory -Force | Out-Null
New-Item -Path dist
elease -ItemType Directory -Force | Out-Null
New-Item -Path distinal -ItemType Directory -Force | Out-Null

Remove-Item -Recurse -Force dist\staging, dist\windows -ErrorAction SilentlyContinue
New-Item -Path dist\staging -ItemType Directory -Force | Out-Null
New-Item -Path dist\windows -ItemType Directory -Force | Out-Null

mvn -q test package

Copy-Item target\dirSyncPro-0.1.jar dist\staging\
Copy-Item -Recurse target\lib dist\staging\
Copy-Item -Recurse src\main\resources dist\staging\resources

jpackage `
  --type exe `
  --input dist\staging `
  --name DirSyncPro `
  --main-jar dirSyncPro-0.1.jar `
  --main-class dirsyncpro.DirSyncPro `
  --dest dist\windows `
  --app-version 1.0 `
  --vendor "DirSyncPro" `
  --icon src\main\resources\icons\dirsyncpro_multi_16_22_32_40_48_64.ico `
  --win-shortcut `
  --win-menu `
  --java-options "-Xmx512m"
