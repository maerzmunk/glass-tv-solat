# Windows version of theme.sh: push a theme folder (config.json, optionally wallpaper.jpg and the azan sounds) to the
# launcher on the TV and restart Home. See themes/README.md for the keys.
#
# Usage: tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
#        tools\theme.ps1 -TvHost 192.168.1.50 -Reset
param(
    [Parameter(Position = 0)][string]$Theme,
    [switch]$Reset,
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
# Resolve the theme folder against the caller's directory before moving into tools\.
if ($Theme) { $Theme = (Resolve-Path $Theme).Path }
Set-Location $PSScriptRoot
$py = 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'com.gathin.tvhome'
$dir = "/sdcard/Android/data/$pkg/files"
# Restart Home so it reads the theme. Android 11 also switches off a force-stopped app's accessibility
# services (HomeKeyService, InputKeyService), so they are switched back on as they were.
$restart = 's=$(settings get secure enabled_accessibility_services); am force-stop ' + $pkg + '; am start -n ' + $pkg + '/.MainActivity >/dev/null; case "$s" in *' + $pkg + '*) settings put secure enabled_accessibility_services "$s"; settings put secure accessibility_enabled 1 ;; esac'
# Some Android 11+ firmwares (Xiaomi Mi Box S 2nd gen) keep adb out of Android/data; the launcher then
# reads its theme from its media folder, which adb can write (Config.dir).
$probe = & $py -I tv.py "mkdir -p $dir 2>/dev/null; touch $dir/.probe 2>/dev/null && rm $dir/.probe && echo writable"
if (-not ($probe -match 'writable')) {
    $dir = "/sdcard/Android/media/$pkg"
    Write-Host "Android/data is closed to adb on this TV; using $dir"
}

if ($Reset) {
    & $py -I tv.py "rm -f $dir/config.json $dir/wallpaper.jpg $dir/azan.mp3 $dir/azan_subuh.mp3; $restart; echo reset"
    exit 0
}
if (-not $Theme) { throw 'usage: theme.ps1 <theme folder> | -Reset' }
$config = Join-Path $Theme 'config.json'
if (-not (Test-Path $config)) { throw "no config.json in $Theme" }
try { Get-Content $config -Raw | ConvertFrom-Json | Out-Null } catch { throw "$config is not valid JSON" }

& $py -I tv.py "mkdir -p $dir" | Out-Null
& $py -I tv.py --push $config "$dir/config.json"
$wall = Join-Path $Theme 'wallpaper.jpg'
if (Test-Path $wall) { & $py -I b64push.py $wall "$dir/wallpaper.jpg" } else { & $py -I tv.py "rm -f $dir/wallpaper.jpg" | Out-Null }
# The azan sounds (themes\README.md, azan): pushed when the theme has them, removed when it does not.
foreach ($f in 'azan.mp3', 'azan_subuh.mp3') {
    $sound = Join-Path $Theme $f
    if (Test-Path $sound) { & $py -I b64push.py $sound "$dir/$f" | Select-Object -Last 1 } else { & $py -I tv.py "rm -f $dir/$f" | Out-Null }
}
& $py -I tv.py "$restart; echo applied"
Write-Host "Theme $(Split-Path $Theme -Leaf) is on the TV. Undo with: tools\theme.ps1 -TvHost $TvHost -Reset"
