$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

& .\gradlew.bat :app:assembleDebug --offline
if ($LASTEXITCODE -ne 0) {
    & .\gradlew.bat :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) {
    throw "Debug APK not found: $apk"
}

& adb install -r -d $apk
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& adb shell monkey -p com.glassbox.hello -c android.intent.category.LAUNCHER 1
