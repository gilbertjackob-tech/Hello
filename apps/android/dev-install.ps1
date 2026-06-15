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

function Get-VariantApk {
    param(
        [string]$BuildRoot,
        [ValidateSet('debug', 'release')]
        [string]$BuildType
    )

    $separator = [IO.Path]::DirectorySeparatorChar
    $variantSegment = "${separator}apk${separator}${BuildType}${separator}"
    Get-ChildItem -Path "$BuildRoot\app\build" -Recurse -Filter *.apk -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -like "*$variantSegment*" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

function Get-ReadyDeviceSerial {
    $line = & adb devices | Select-String -Pattern '^\S+\s+device$' | Select-Object -First 1
    if (-not $line) {
        return $null
    }

    return ($line.ToString() -split '\s+')[0]
}

function Wait-ForReadyDevice {
    param(
        [int]$TimeoutSeconds = 30,
        [switch]$RestartServer
    )

    if ($RestartServer) {
        & adb kill-server | Out-Null
        Start-Sleep -Milliseconds 750
        & adb start-server | Out-Null
    }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $serial = Get-ReadyDeviceSerial
        if ($serial) {
            return $serial
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)

    $states = (& adb devices -l) -join [Environment]::NewLine
    throw "No authorized ADB device became ready within $TimeoutSeconds seconds.`n$states"
}

function Install-Apk {
    param(
        [string]$apkPath,
        [int]$MaxAttempts = 3
    )

    if (-not (Test-Path -LiteralPath $apkPath)) {
        throw "APK not found: $apkPath"
    }

    $installStart = Get-Date
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $serial = Wait-ForReadyDevice -TimeoutSeconds 30 -RestartServer:($attempt -gt 1)
        Write-Host "Installing to $serial (attempt $attempt/$MaxAttempts)..."

        if ($attempt -eq 1) {
            & adb -s $serial install -r -t -d $apkPath | ForEach-Object { Write-Host $_ }
        } else {
            # Non-streaming is slower but avoids EOF/transport loss on unstable USB links.
            & adb -s $serial install --no-streaming -r -t -d $apkPath | ForEach-Object { Write-Host $_ }
        }

        if ($LASTEXITCODE -eq 0) {
            $installTime = ((Get-Date) - $installStart).TotalSeconds
            Write-Host "Install time: $installTime sec"
            return $installTime
        }

        Write-Warning "ADB install attempt $attempt failed. Re-establishing the device transport."
    }

    throw "APK installation failed after $MaxAttempts attempts: $apkPath"
}

$serial = Wait-ForReadyDevice -TimeoutSeconds 30
$abi = (& adb -s $serial shell getprop ro.product.cpu.abi).Trim()

if ([string]::IsNullOrWhiteSpace($abi)) {
    $abi = "arm64-v8a"
}

Write-Host "Device ABI: $abi"

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

    $buildEnd = Get-Date
    $buildTime = ($buildEnd - $buildStart).TotalSeconds
    $apk = Get-VariantApk -BuildRoot $root -BuildType release
}
else {
    & .\gradlew.bat :app:assembleDebug "-Pandroid.injected.build.abi=$abi" -x lint -x test --parallel

    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    $apk = Get-VariantApk -BuildRoot $root -BuildType debug
    if (-not $apk) {
        throw "Debug APK not found below $root\app\build"
    }
    $buildEnd = Get-Date
    $buildTime = ($buildEnd - $buildStart).TotalSeconds
    $installTime = Install-Apk $apk.FullName

    $serial = Wait-ForReadyDevice -TimeoutSeconds 20
    & adb -s $serial shell monkey -p com.glassbox.hello -c android.intent.category.LAUNCHER 1 |
        ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) {
        throw "APK installed, but the app launch command failed"
    }
}

# ---------------- INSTALL (release path) ----------------

if (-not $installTime) {
    $installTime = 0
}

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
