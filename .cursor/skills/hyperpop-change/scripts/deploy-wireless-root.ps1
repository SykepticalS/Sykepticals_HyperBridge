[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,

    [string]$Serial,

    [ValidatePattern('^[A-Za-z0-9_.]+$')]
    [string]$PackageName = "com.sykeptical.hyperpop",

    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Find-Adb {
    $fromPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }

    if ($env:LOCALAPPDATA) {
        $sdkAdb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
        if (Test-Path -LiteralPath $sdkAdb -PathType Leaf) { return $sdkAdb }
    }

    throw "ADB executable not found. No connectivity changes were attempted."
}

function Get-WirelessSerials([string]$AdbPath) {
    $lines = @(& $AdbPath devices -l 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices failed. No connectivity changes were attempted."
    }

    @(
        foreach ($line in $lines) {
            if ($line -notmatch '^(\S+)\s+device(?:\s|$)') { continue }
            $candidate = $Matches[1]
            $isTcp = $candidate.Contains(":")
            $isMdnsTls = $candidate.StartsWith("adb-") -and
                $candidate.Contains("._adb-tls-connect._tcp")
            if (($isTcp -or $isMdnsTls) -and -not $candidate.StartsWith("emulator-")) {
                $candidate
            }
        }
    )
}

function Invoke-Adb([string[]]$Arguments) {
    # adb writes progress to stderr. Keep that from becoming a terminating error.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = @(& $adb -s $selectedSerial @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    [pscustomobject]@{
        ExitCode = $exitCode
        Output = $output
        Text = ($output -join [Environment]::NewLine)
    }
}

$resolvedApk = (Resolve-Path -LiteralPath $ApkPath -ErrorAction Stop).Path
if (-not (Test-Path -LiteralPath $resolvedApk -PathType Leaf)) {
    throw "APK path is not a file: $resolvedApk"
}
if ([System.IO.Path]::GetExtension($resolvedApk) -ine ".apk") {
    throw "APK path must end in .apk: $resolvedApk"
}

$adb = Find-Adb
$wirelessSerials = @(Get-WirelessSerials $adb)
if ($Serial) {
    if ($Serial -notin $wirelessSerials) {
        throw "Requested serial '$Serial' is not an already-connected online wireless ADB target."
    }
    $selectedSerial = $Serial
} elseif ($wirelessSerials.Count -eq 1) {
    $selectedSerial = $wirelessSerials[0]
} elseif ($wirelessSerials.Count -eq 0) {
    throw "No already-connected wireless ADB target is online. USB devices and emulators were ignored; no connectivity changes were attempted."
} else {
    throw "Multiple wireless ADB targets are online. Pass the intended existing target with -Serial."
}

$remoteApk = "/data/local/tmp/__codex_install.apk"

Write-Output "Wireless target: $selectedSerial"
Write-Output "Local APK: $resolvedApk"
Write-Output "Remote APK: $remoteApk"

if ($DryRun) {
    Write-Output "DRY RUN: path and target validation passed; no push, install, cleanup, or device mutation was performed."
    exit 0
}

$push = Invoke-Adb @("push", $resolvedApk, $remoteApk)
$install = $null
$cleanup = $null
try {
    if ($push.ExitCode -eq 0) {
        Write-Output ($push.Text)
        $installCommand = "su -c `"pm install -r -g --user 0 $remoteApk`""
        $install = Invoke-Adb @("shell", $installCommand)
    }
} finally {
    $cleanupCommand = "su -c `"rm -f $remoteApk`""
    $cleanup = Invoke-Adb @("shell", $cleanupCommand)
}

if ($push.ExitCode -ne 0) {
    $cleanupNote = if ($cleanup.ExitCode -eq 0) {
        "Temporary APK cleanup: success"
    } else {
        "Temporary APK cleanup also failed:`n$($cleanup.Text)"
    }
    throw "ADB push failed:`n$($push.Text)`n$cleanupNote"
}

if ($install.ExitCode -ne 0 -or $install.Text -notmatch '(?m)^\s*Success\s*$') {
    $cleanupNote = if ($cleanup.ExitCode -eq 0) {
        "Temporary APK cleanup: success"
    } else {
        "Temporary APK cleanup also failed:`n$($cleanup.Text)"
    }
    throw "Package Manager did not report Success:`n$($install.Text)`n$cleanupNote"
}

if ($cleanup.ExitCode -ne 0) {
    throw "Package Manager: Success`nTemporary APK cleanup failed:`n$($cleanup.Text)"
}
Write-Output "Temporary APK cleanup: success"
Write-Output "Package Manager: Success"

$packagePath = Invoke-Adb @("shell", "pm path $PackageName")
if ($packagePath.ExitCode -ne 0 -or $packagePath.Text -notmatch '(?m)^package:') {
    throw "Installed package verification failed for ${PackageName}:`n$($packagePath.Text)"
}
$packageDump = Invoke-Adb @("shell", "dumpsys package $PackageName")
if ($packageDump.ExitCode -ne 0) {
    throw "Installed package version query failed for ${PackageName}:`n$($packageDump.Text)"
}
$versionNameMatch = [regex]::Match($packageDump.Text, '\bversionName=([^\s]+)')
$versionCodeMatch = [regex]::Match($packageDump.Text, '\bversionCode=(\d+)')
$versionName = if ($versionNameMatch.Success) { $versionNameMatch.Groups[1].Value } else { "unknown" }
$versionCode = if ($versionCodeMatch.Success) { $versionCodeMatch.Groups[1].Value } else { "unknown" }
Write-Output "Installed package: $PackageName versionName=$versionName versionCode=$versionCode"
