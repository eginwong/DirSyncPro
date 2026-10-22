param(
    [string]$AppVersion = ""
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $AppVersion) {
    $AppVersion = (& mvn -q help:evaluate -Dexpression=project.version -DforceStdout 2>$null | Out-String).Trim()
}
if (-not $AppVersion -or $AppVersion -eq 'null') {
    $AppVersion = '0.1'
}

$AppVersion = ($AppVersion -replace '[^0-9.]', '.').Trim('.')
$AppVersion = [regex]::Replace($AppVersion, '\.+', '.')
$parts = @($AppVersion.Split('.')) | Where-Object { $_ -ne '' }
if (-not $parts -or $parts.Count -eq 0) {
    $AppVersion = '1.0'
} else {
    if ($parts[0] -eq '0') { $parts[0] = '1' }
    if ($parts.Count -gt 3) {
        $AppVersion = "$($parts[0]).$($parts[1]).$($parts[$parts.Count - 1])"
    } else {
        $AppVersion = ($parts -join '.')
    }
}
if ($AppVersion -match '^0\.|^0$') {
    $AppVersion = "1$($AppVersion.Substring(1))"
}

New-Item -Path "$root\target" -ItemType Directory -Force | Out-Null
New-Item -Path "$root\dist\staging" -ItemType Directory -Force | Out-Null
New-Item -Path "$root\dist\windows" -ItemType Directory -Force | Out-Null
Remove-Item -Recurse -Force "$root\dist\staging\*", "$root\dist\windows\*" -ErrorAction SilentlyContinue

mvn -q test package

$jarFile = Get-ChildItem -Path "$root\target" -Filter 'dirSyncPro-*.jar' | Sort-Object Name | Select-Object -First 1
if (-not $jarFile) {
    throw "No built JAR found in target/"
}

Copy-Item "$($jarFile.FullName)" "$root\dist\staging\"
Copy-Item -Recurse "$root\target\lib" "$root\dist\staging\"
Copy-Item -Recurse "$root\src\main\resources" "$root\dist\staging\resources"

jpackage `
  --type exe `
  --input dist\staging `
  --name DirSyncPro `
  --main-jar $jarFile.Name `
  --main-class dirsyncpro.DirSyncPro `
  --dest dist\windows `
  --app-version $AppVersion `
  --vendor "DirSyncPro" `
  --icon src\main\resources\icons\dirsyncpro_multi_16_22_32_40_48_64.ico `
  --win-shortcut `
  --win-menu `
  --java-options "-Xmx512m"
