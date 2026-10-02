$ErrorActionPreference = "Stop"

function Find-Adb {
    $fromPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($fromPath) {
        return $fromPath.Source
    }

    if ($env:LOCALAPPDATA) {
        $sdkAdb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
        if (Test-Path -LiteralPath $sdkAdb -PathType Leaf) {
            return $sdkAdb
        }
    }

    throw "ADB executable not found. Android-MCP was not started."
}

function Find-Uvx {
    $fromPath = Get-Command uvx -ErrorAction SilentlyContinue
    if ($fromPath) {
        return $fromPath.Source
    }

    if ($env:USERPROFILE) {
        $localUvx = Join-Path $env:USERPROFILE ".local\bin\uvx.exe"
        if (Test-Path -LiteralPath $localUvx -PathType Leaf) {
            return $localUvx
        }
    }

    throw "uvx executable not found. Android-MCP was not started."
}

$adb = Find-Adb
$deviceLines = & $adb devices -l 2>$null
$wirelessSerials = @(
    foreach ($line in $deviceLines) {
        if ($line -notmatch '^(\S+)\s+device(?:\s|$)') {
            continue
        }

        $serial = $Matches[1]
        $isTcp = $serial.Contains(":")
        $isMdnsTls = $serial.StartsWith("adb-") -and $serial.Contains("._adb-tls-connect._tcp")
        if (($isTcp -or $isMdnsTls) -and -not $serial.StartsWith("emulator-")) {
            $serial
        }
    }
)

if ($wirelessSerials.Count -eq 0) {
    throw "No already-connected wireless ADB target is online. USB devices and emulators are intentionally ignored; no ADB connection changes were attempted."
}

$selectedSerial = $wirelessSerials[0]
$adbDirectory = Split-Path -Parent $adb
$env:PATH = "$adbDirectory;$env:PATH"

# --device uses Android-MCP's existing-device path. In contrast, --wifi and
# ANDROID_MCP_CONNECTION=wifi call `adb connect`, which this workspace forbids.
$env:ANDROID_MCP_DEVICE = $selectedSerial
$env:ANDROID_MCP_CONNECTION = "auto"

$uvx = Find-Uvx
& $uvx --python 3.13 android-mcp --device $selectedSerial
exit $LASTEXITCODE
