param(
    [switch]$rel,
    [switch]$v8a,
    [switch]$v7a,
    [switch]$both
)

$ErrorActionPreference = "Stop"

$globalStart = Get-Date

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

function Get-LatestApk {
    param([string]$BuildRoot)

    Get-ChildItem -Path "$BuildRoot\app\build" -Recurse -Filter *.apk -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

function Install-Apk {
    param([string]$apkPath)

    $device = & adb devices | Select-String "device$"

    if ($device) {
        $installStart = Get-Date

        Write-Host "Installing to device..."
        & adb install -r -t -d $apkPath

        $installEnd = Get-Date
        $installTime = ($installEnd - $installStart).TotalSeconds

        Write-Host "Install time: $installTime sec"
        return $installTime
    }

    return 0
}

$abi = (& adb shell getprop ro.product.cpu.abi).Trim()

if ([string]::IsNullOrWhiteSpace($abi)) {
    $abi = "arm64-v8a"
}

Write-Host "Device ABI: $abi"

& adb kill-server
& adb start-server

$gradleAbi = ""

if ($v8a) {
    $gradleAbi = "arm64-v8a"
}
elseif ($v7a) {
    $gradleAbi = "armeabi-v7a"
}
elseif ($both) {
    $gradleAbi = "arm64-v8a,armeabi-v7a"
}

# ---------------- BUILD ----------------

$buildStart = Get-Date

if ($rel) {

    if ($gradleAbi -ne "") {
        $arg = "-Pandroid.injected.build.abi=$gradleAbi"
    } else {
        $arg = ""
    }

    & .\gradlew.bat :app:assembleRelease $arg -x lint -x test --parallel

    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    $apk = Get-LatestApk -BuildRoot $root
}
else {

    & .\gradlew.bat :app:installDebug "-Pandroid.injected.build.abi=$abi" -x lint -x test --parallel

    $code = $LASTEXITCODE

    $apk = Get-LatestApk -BuildRoot $root

    if ($code -ne 0) {

        & .\gradlew.bat :app:assembleDebug "-Pandroid.injected.build.abi=$abi" -x lint -x test --parallel

        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }

        $apk = Get-LatestApk -BuildRoot $root

        if (-not $apk) {
            throw "Debug APK not found"
        }

        Install-Apk $apk.FullName
    }

    & adb shell monkey -p com.glassbox.hello -c android.intent.category.LAUNCHER 1

    $apk = Get-LatestApk -BuildRoot $root
}

$buildEnd = Get-Date
$buildTime = ($buildEnd - $buildStart).TotalSeconds

# ---------------- INSTALL (release path) ----------------

$installTime = 0

if ($rel -and $apk) {
    Write-Host $apk.FullName
    $installTime = Install-Apk $apk.FullName
}

# ---------------- TOTAL ----------------

$globalEnd = Get-Date
$totalTime = ($globalEnd - $globalStart).TotalSeconds

Write-Host ""
Write-Host "=============================="
Write-Host "BUILD TIME   : $buildTime sec"
Write-Host "INSTALL TIME : $installTime sec"
Write-Host "TOTAL TIME   : $totalTime sec"
Write-Host "APK          : $($apk.FullName)"
Write-Host "=============================="