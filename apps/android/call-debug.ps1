param(
    [switch]$FreshInstall,
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$device = & adb devices | Select-String "device$"
if (-not $device) {
    throw "No Android device is connected. Reconnect USB debugging and run this script again."
}

if (-not $NoBuild) {
    & .\gradlew.bat :app:assembleDebug -x lint -x test --parallel
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

$apk = Get-ChildItem -Path "$root\app\build\outputs\apk\debug" -Filter *.apk -ErrorAction Stop |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $apk) {
    throw "Debug APK not found."
}

if ($FreshInstall) {
    & adb uninstall com.glassbox.hello | Out-Host
}

& adb install -r -t -d $apk.FullName
if ($LASTEXITCODE -ne 0) {
    throw "Install failed. If this is a signature mismatch, rerun with -FreshInstall."
}

& adb logcat -c
& adb shell monkey -p com.glassbox.hello -c android.intent.category.LAUNCHER 1 | Out-Host

Write-Host ""
Write-Host "Reproduce the call failure now, then press Enter here to save the filtered call log."
Read-Host | Out-Null

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outDir = Join-Path $root "logs"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$outFile = Join-Path $outDir "call-$stamp.log"

& adb logcat -d |
    Select-String "HelloCallEngine|HelloCallViewModel|HelloDebug|CALL_TRACE|CALL_DEBUG|sdp_op|SessionDescription" |
    ForEach-Object { $_.Line } |
    Set-Content -Path $outFile -Encoding UTF8

Write-Host "Saved call log: $outFile"
