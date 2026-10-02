[CmdletBinding()]
param(
    [ValidateRange(1, 60)]
    [int]$DurationSeconds = 20,

    [ValidateRange(100, 4000)]
    [int]$MaxLines = 900,

    [string]$OutputPath,

    [string]$Serial,

    [string]$ExtraPattern
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path

function Find-Adb {
    $fromPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }
    if ($env:LOCALAPPDATA) {
        $sdkAdb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
        if (Test-Path -LiteralPath $sdkAdb -PathType Leaf) { return $sdkAdb }
    }
    throw "ADB executable not found."
}

function Get-WirelessSerials([string]$AdbPath) {
    @(
        & $AdbPath devices -l 2>$null | ForEach-Object {
            if ($_ -notmatch '^(\S+)\s+device(?:\s|$)') { return }
            $candidate = $Matches[1]
            $wireless = $candidate.Contains(":") -or
                ($candidate.StartsWith("adb-") -and $candidate.Contains("._adb-tls-connect._tcp"))
            if ($wireless -and -not $candidate.StartsWith("emulator-")) { $candidate }
        }
    )
}

function Invoke-Adb([string[]]$Arguments) {
    $result = & $adb -s $selectedSerial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($Arguments -join ' ') failed: $($result -join [Environment]::NewLine)"
    }
    return @($result)
}

function Get-PackageFacts([string]$PackageName) {
    $dump = @(& $adb -s $selectedSerial shell dumpsys package $PackageName 2>$null)
    $path = @(& $adb -s $selectedSerial shell pm path $PackageName 2>$null)
    $versionName = ($dump | Select-String -Pattern '\bversionName=([^\s]+)' | Select-Object -First 1)
    $versionCode = ($dump | Select-String -Pattern '\bversionCode=(\d+)' | Select-Object -First 1)
    [pscustomobject]@{
        Package = $PackageName
        VersionName = if ($versionName) { $versionName.Matches[0].Groups[1].Value } else { "not-installed" }
        VersionCode = if ($versionCode) { $versionCode.Matches[0].Groups[1].Value } else { "-" }
        Paths = (($path | Where-Object { $_ -like 'package:*' }) -replace '^package:', '') -join '; '
    }
}

$adb = Find-Adb
$wirelessSerials = @(Get-WirelessSerials $adb)
if ($Serial) {
    if ($Serial -notin $wirelessSerials) {
        throw "Requested serial '$Serial' is not an already-connected wireless ADB target."
    }
    $selectedSerial = $Serial
} elseif ($wirelessSerials.Count -eq 1) {
    $selectedSerial = $wirelessSerials[0]
} elseif ($wirelessSerials.Count -eq 0) {
    throw "No already-connected wireless ADB target is online. No connection changes were attempted."
} else {
    throw "Multiple wireless ADB targets are online; pass -Serial explicitly."
}

$evidenceDir = [System.IO.Path]::GetFullPath((Join-Path $repoRoot ".agent-local\evidence"))
if (-not $OutputPath) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputPath = Join-Path $evidenceDir "runtime-$stamp.txt"
}
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$evidencePrefix = $evidenceDir.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
if (-not $resolvedOutput.StartsWith($evidencePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "OutputPath must stay under $evidenceDir"
}
$outputDirectory = Split-Path -Parent $resolvedOutput
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null

$facts = [System.Collections.Generic.List[string]]::new()
$facts.Add("HyperPop compact runtime evidence")
$facts.Add("Captured: $([DateTimeOffset]::Now.ToString('o'))")
$facts.Add("Wireless ADB serial: $selectedSerial")
$facts.Add("Duration: $DurationSeconds seconds; log line limit: $MaxLines")
$facts.Add("ADB state was observed only; it was not connected, restarted, or reconfigured.")
$facts.Add("")

$properties = @(
    "ro.product.manufacturer",
    "ro.product.model",
    "ro.product.device",
    "ro.build.version.release",
    "ro.build.version.sdk",
    "ro.build.version.incremental",
    "ro.build.fingerprint"
)
$facts.Add("[BUILD]")
foreach ($property in $properties) {
    $value = (Invoke-Adb @("shell", "getprop", $property)) -join ""
    $facts.Add("$property=$value")
}

$facts.Add("")
$facts.Add("[PACKAGES]")
foreach ($packageName in @("com.android.systemui", "miui.systemui.plugin", "com.sykeptical.hyperpop", "com.sykeptical.hyperpop")) {
    try {
        $package = Get-PackageFacts $packageName
        $facts.Add("$($package.Package) versionName=$($package.VersionName) versionCode=$($package.VersionCode) path=$($package.Paths)")
    } catch {
        $facts.Add("$packageName unavailable: $($_.Exception.Message)")
    }
}

$facts.Add("")
$facts.Add("[PROCESSES]")
$processLines = Invoke-Adb @("shell", "ps", "-A")
$processPattern = 'systemui|xmsf|hyperpop|lspd|lsposed'
@($processLines | Select-String -Pattern $processPattern -CaseSensitive:$false) | ForEach-Object { $facts.Add($_.Line) }

$rawTemp = Join-Path ([System.IO.Path]::GetTempPath()) ("hyperpop-logcat-" + [guid]::NewGuid().ToString("N") + ".txt")
$errTemp = "$rawTemp.err"
try {
    $argumentList = @("-s", $selectedSerial, "logcat", "-v", "threadtime", "-T", "1")
    $process = Start-Process -FilePath $adb -ArgumentList $argumentList -NoNewWindow -PassThru `
        -RedirectStandardOutput $rawTemp -RedirectStandardError $errTemp
    try {
        Start-Sleep -Seconds $DurationSeconds
    } finally {
        if (-not $process.HasExited) {
            $process.Kill()
            $process.WaitForExit()
        }
    }

    $basePattern = 'HyperPop|libxposed|LSPosed|DynamicIsland|MIUISystemUI|com\.android\.systemui|com\.xiaomi\.xmsf|AndroidRuntime|FATAL EXCEPTION|(?:Exception|Error|ANR|not responding).*?(?:SystemUI|DynamicIsland|HyperPop|libxposed|LSPosed)|(?:SystemUI|DynamicIsland|HyperPop|libxposed|LSPosed).*?(?:Exception|Error|ANR|not responding)'
    $pattern = if ($ExtraPattern) { "(?:$basePattern)|(?:$ExtraPattern)" } else { $basePattern }
    $raw = @(Get-Content -LiteralPath $rawTemp -ErrorAction SilentlyContinue)
    $indexes = [System.Collections.Generic.SortedSet[int]]::new()
    for ($i = 0; $i -lt $raw.Count; $i++) {
        if ($raw[$i] -match $pattern) {
            for ($j = [Math]::Max(0, $i - 2); $j -le [Math]::Min($raw.Count - 1, $i + 2); $j++) {
                [void]$indexes.Add($j)
            }
        }
    }

    $facts.Add("")
    $facts.Add("[FILTERED LOGCAT]")
    $facts.Add("Pattern: $pattern")
    if ($indexes.Count -eq 0) {
        $facts.Add("No matching lines captured.")
    } else {
        $written = 0
        $previous = -2
        foreach ($index in $indexes) {
            if ($written -ge $MaxLines) { break }
            if ($index -gt $previous + 1) {
                $facts.Add("... omitted unrelated logcat ...")
                $written++
            }
            if ($written -ge $MaxLines) { break }
            $facts.Add($raw[$index])
            $written++
            $previous = $index
        }
        if ($indexes.Count -gt $written) {
            $facts.Add("... filtered output capped at $MaxLines lines ...")
        }
    }
} finally {
    Remove-Item -LiteralPath $rawTemp, $errTemp -Force -ErrorAction SilentlyContinue
}

[System.IO.File]::WriteAllLines($resolvedOutput, $facts, [System.Text.UTF8Encoding]::new($false))
Write-Output $resolvedOutput
