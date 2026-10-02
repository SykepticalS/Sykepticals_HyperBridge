import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CACHE_PATH = ROOT / ".agent-local" / "device-state.json"
VERIFIED_PATH = ROOT / ".cursor" / "skills" / "xiaomi-systemui" / "references" / "verified-behavior.md"
HASHED_PACKAGES = {"com.android.systemui", "miui.systemui.plugin"}
PACKAGES = (
    "com.android.systemui",
    "miui.systemui.plugin",
    "com.sykeptical.hyperpop",
    # Previous application ids. They may stay installed beside HyperPop.
    "com.d4viddf.hyperbridge",
    "com.sykeptical.hyperbridge",
)


def run(args, timeout=8):
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=timeout, check=False)
        return result.returncode, result.stdout.strip(), result.stderr.strip()
    except Exception as exc:
        return -1, "", str(exc)


def find_adb():
    from_path = shutil.which("adb")
    if from_path:
        return from_path
    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        sdk_adb = Path(local_app_data) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
        if sdk_adb.is_file():
            return str(sdk_adb)
    return None


def connected_wireless_devices(adb):
    _, output, _ = run([adb, "devices", "-l"])
    devices = []
    for line in output.splitlines():
        match = re.match(r"^(\S+)\s+device(?:\s+(.*))?$", line.strip())
        if not match:
            continue
        serial = match.group(1)
        details = match.group(2) or ""
        is_tcp = ":" in serial
        is_mdns_tls = serial.startswith("adb-") and "._adb-tls-connect._tcp" in serial
        if (is_tcp or is_mdns_tls) and not serial.startswith("emulator-"):
            devices.append((serial, details))
    return devices


def load_json(path):
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        return value if isinstance(value, dict) else {}
    except Exception:
        return {}


def write_json_atomic(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(path)


def parse_package_dump(text):
    name = re.search(r"\bversionName=([^\s]+)", text)
    code = re.search(r"\bversionCode=(\d+)", text)
    return {
        "versionName": name.group(1) if name else None,
        "versionCode": code.group(1) if code else None,
    }


def shell(adb, *args, timeout=8):
    return run(adb + ["shell", *args], timeout=timeout)


def package_snapshot(adb, package_name, previous, build_fingerprint):
    _, dump, _ = shell(adb, "dumpsys", "package", package_name, timeout=10)
    version = parse_package_dump(dump)
    _, path_output, _ = shell(adb, "pm", "path", package_name)
    paths = sorted(
        line.partition(":")[2].strip()
        for line in path_output.splitlines()
        if line.startswith("package:") and line.partition(":")[2].strip()
    )

    sizes = {}
    for apk_path in paths:
        code, output, _ = shell(adb, "stat", "-c", "%s", apk_path)
        lines = output.splitlines()
        if code == 0 and lines:
            sizes[apk_path] = lines[-1].strip()

    cheap_material = {
        "buildFingerprint": build_fingerprint,
        "versionName": version["versionName"],
        "versionCode": version["versionCode"],
        "paths": paths,
        "sizes": sizes,
    }
    cheap_identity = hashlib.sha256(
        json.dumps(cheap_material, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()

    previous_hashes = previous.get("apkSha256", {}) if isinstance(previous, dict) else {}
    reuse_hashes = (
        previous.get("cheapIdentity") == cheap_identity
        and bool(paths)
        and all(apk_path in previous_hashes for apk_path in paths)
    )
    hashes = dict(previous_hashes) if reuse_hashes else {}
    hash_state = "cached" if reuse_hashes else "not-requested"
    if package_name in HASHED_PACKAGES and paths and not reuse_hashes:
        hash_state = "refreshed"
        for apk_path in paths:
            code, output, _ = shell(adb, "sha256sum", apk_path, timeout=25)
            match = re.match(r"^([0-9a-fA-F]{64})\b", output)
            if code == 0 and match:
                hashes[apk_path] = match.group(1).upper()
            else:
                hash_state = "unavailable"

    return {
        "installed": bool(version["versionName"] or version["versionCode"] or paths),
        **version,
        "paths": paths,
        "sizes": sizes,
        "cheapIdentity": cheap_identity,
        "apkSha256": hashes,
        "hashState": hash_state,
    }


def verified_knowledge_match(device, packages):
    try:
        verified = VERIFIED_PATH.read_text(encoding="utf-8")
    except Exception:
        return None, "verified reference unavailable"

    fingerprint = device.get("buildFingerprint")
    required = [fingerprint] if fingerprint else []
    for package_name in ("com.android.systemui", "miui.systemui.plugin"):
        package = packages.get(package_name, {})
        if package.get("versionName"):
            required.append(package["versionName"])
        if package.get("versionCode"):
            required.append(package["versionCode"])
    if not required or any(value not in verified for value in required):
        return False, "build or package identity differs from verified reference"

    package_hashes = {
        package_name: list(packages.get(package_name, {}).get("apkSha256", {}).values())
        for package_name in ("com.android.systemui", "miui.systemui.plugin")
    }
    if any(not hashes for hashes in package_hashes.values()):
        return None, "cheap identity matches; one or more APK hashes are unavailable"
    hashes = [digest for values in package_hashes.values() for digest in values]
    if all(digest.upper() in verified.upper() for digest in hashes):
        return True, "build, package versions, and APK hashes match verified reference"
    return False, "APK hash differs from verified reference"


def package_label(package):
    if not package or not package.get("installed"):
        return "not installed"
    name = package.get("versionName") or "unknown"
    code = package.get("versionCode")
    return f"{name} (code {code})" if code else name


# Consume Cursor's hook input. The hook is observational and never changes ADB state.
try:
    sys.stdin.read()
except Exception:
    pass

adb_path = find_adb()
wireless_devices = connected_wireless_devices(adb_path) if adb_path else []
serial = wireless_devices[0][0] if wireless_devices else None
context = ["HyperPop device identity (read-only session check):"]

if not adb_path:
    context.append("- Wireless ADB: unavailable (adb executable not found)")
elif not serial:
    context.append("- Wireless ADB: no already-connected target; USB/emulators intentionally ignored")
else:
    adb = [adb_path, "-s", serial]

    def prop(name):
        return shell(adb, "getprop", name)[1]

    device = {
        "manufacturer": prop("ro.product.manufacturer"),
        "model": prop("ro.product.model"),
        "device": prop("ro.product.device"),
        "androidRelease": prop("ro.build.version.release"),
        "sdk": prop("ro.build.version.sdk"),
        "buildId": prop("ro.build.id"),
        "incremental": prop("ro.build.version.incremental"),
        "buildFingerprint": prop("ro.build.fingerprint"),
    }
    previous = load_json(CACHE_PATH)
    previous_packages = previous.get("packages", {}) if isinstance(previous.get("packages"), dict) else {}
    packages = {
        name: package_snapshot(adb, name, previous_packages.get(name, {}), device["buildFingerprint"])
        for name in PACKAGES
    }
    knowledge_match, knowledge_reason = verified_knowledge_match(device, packages)
    had_previous = bool(previous.get("observedAt"))
    identity_changed = had_previous and (
        previous.get("device", {}).get("buildFingerprint") != device["buildFingerprint"]
        or any(
            previous_packages.get(name, {}).get("cheapIdentity") != packages[name].get("cheapIdentity")
            for name in PACKAGES
        )
    )
    rehashed = sorted(name for name, value in packages.items() if value.get("hashState") == "refreshed")

    cache = {
        "schemaVersion": 1,
        "observedAt": datetime.now(timezone.utc).isoformat(),
        "wirelessSerial": serial,
        "device": device,
        "packages": packages,
        "status": {
            "hadPreviousCache": had_previous,
            "cheapIdentityChanged": identity_changed,
            "rehashedPackages": rehashed,
            "matchesVerifiedKnowledge": knowledge_match,
            "verifiedKnowledgeReason": knowledge_reason,
        },
        "hashPolicy": (
            "SystemUI and MIUISystemUIPlugin APKs are SHA-256 hashed only when the build, "
            "package version/path, or APK size identity changes; otherwise cached hashes are reused."
        ),
    }
    try:
        write_json_atomic(CACHE_PATH, cache)
        cache_note = ".agent-local/device-state.json"
    except Exception:
        cache_note = "cache write failed"

    current = packages["com.sykeptical.hyperpop"]
    previous = [
        f"{name} {package_label(packages[name])}"
        for name in ("com.sykeptical.hyperbridge", "com.d4viddf.hyperbridge")
        if packages[name].get("installed")
    ]
    match_label = {True: "MATCH", False: "STALE", None: "UNVERIFIED"}[knowledge_match]
    change_label = "changed" if identity_changed else ("initialized" if not had_previous else "unchanged")
    hash_label = ", ".join(rehashed) if rehashed else "cached/no refresh"
    context.extend(
        [
            f"- Target: {device['manufacturer']} {device['model']} via wireless ADB ({serial})",
            f"- Verified Xiaomi knowledge: {match_label} ({knowledge_reason})",
            f"- Device/APK cheap identity: {change_label}; SHA-256: {hash_label}",
            f"- Installed HyperPop: {package_label(current)}",
            f"- Previous packages still installed: {', '.join(previous) if previous else 'none'}",
            f"- Full local identity: {cache_note}",
        ]
    )
    if knowledge_match is not True:
        context.append("- Treat stored Xiaomi reverse-engineering details as stale until re-verified.")

print(json.dumps({"additional_context": "\n".join(context)}))
