import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OUTPUT_DIR = ROOT / ".agent-local" / "evidence" / "mcp-filtered"
THRESHOLD_CHARS = 80_000
MAX_EXCERPT_LINES = 700
CONTEXT_LINES = 2
ANDROID_TERMS = re.compile(
    r"HyperPop|HyperBridge|libxposed|LSPosed|DynamicIsland|MIUISystemUI|com\.android\.systemui|"
    r"com\.xiaomi\.xmsf|AndroidRuntime|FATAL EXCEPTION|"
    r"(?:Exception|Error|ANR|not responding).*?(?:SystemUI|DynamicIsland|HyperPop|HyperBridge|libxposed|LSPosed)|"
    r"(?:SystemUI|DynamicIsland|HyperPop|HyperBridge|libxposed|LSPosed).*?(?:Exception|Error|ANR|not responding)",
    re.IGNORECASE,
)
JADX_STRUCTURE = re.compile(
    r"\b(package|class|interface|enum|object|extends|implements|fun|void|public|private|protected)\b|"
    r"\b(callers?|callees?|subtypes?|overrides?|relations?)\b|\b(Exception|Error)\b",
    re.IGNORECASE,
)


def strings_from(value):
    if isinstance(value, str):
        yield value
    elif isinstance(value, dict):
        for key, item in value.items():
            yield str(key)
            yield from strings_from(item)
    elif isinstance(value, list):
        for item in value:
            yield from strings_from(item)


def parse_json(value, fallback):
    if isinstance(value, (dict, list)):
        return value
    if isinstance(value, str):
        try:
            return json.loads(value)
        except Exception:
            return fallback
    return fallback


def target_terms(tool_input):
    values = []
    parsed = parse_json(tool_input, {})
    for text in strings_from(parsed):
        for token in re.findall(r"[A-Za-z_$][A-Za-z0-9_.$]{3,}", text):
            if token.lower() not in {"true", "false", "class", "method", "query", "keyword"}:
                values.append(token)
    return list(dict.fromkeys(values))[:30]


def select_with_context(lines, predicate):
    indexes = set()
    for index, line in enumerate(lines):
        if predicate(line):
            indexes.update(range(max(0, index - CONTEXT_LINES), min(len(lines), index + CONTEXT_LINES + 1)))
    selected = []
    previous = -2
    for index in sorted(indexes):
        if len(selected) >= MAX_EXCERPT_LINES:
            break
        if index > previous + 1:
            selected.append("... omitted unrelated output ...")
        selected.append(lines[index])
        previous = index
    return selected


try:
    request = json.load(sys.stdin)
except Exception:
    print("{}")
    raise SystemExit(0)

tool_name = str(request.get("tool_name", ""))
lower_name = tool_name.lower()
is_jadx = any(
    term in lower_name
    for term in (
        "jadx", "decomp", "bytecode", "class", "method", "caller", "callee", "load_apk",
        "xrefs", "hierarchy", "override", "manifest", "resource", "smali", "component",
    )
)
is_android = any(
    term in lower_name
    for term in (
        "android", "adb", "logcat", "device", "shell", "package", "activity", "screenshot", "snapshot",
    )
)
if not (is_jadx or is_android):
    print("{}")
    raise SystemExit(0)

raw_output = request.get("tool_output", "")
serialized = raw_output if isinstance(raw_output, str) else json.dumps(raw_output, ensure_ascii=False)
if len(serialized) < THRESHOLD_CHARS:
    print("{}")
    raise SystemExit(0)

parsed_output = parse_json(serialized, serialized)
lines = []
for text in strings_from(parsed_output):
    lines.extend(text.splitlines() or [text])

if is_jadx:
    terms = target_terms(request.get("tool_input", {}))
    term_pattern = re.compile("|".join(re.escape(term) for term in terms), re.IGNORECASE) if terms else None
    selected = select_with_context(
        lines,
        lambda line: bool(JADX_STRUCTURE.search(line) or (term_pattern and term_pattern.search(line))),
    )
    kind = "jadx"
else:
    selected = select_with_context(lines, lambda line: bool(ANDROID_TERMS.search(line)))
    kind = "android"

OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
tool_id = re.sub(r"[^A-Za-z0-9_.-]+", "-", str(request.get("tool_use_id", "tool")))[:50]
path = OUTPUT_DIR / f"{stamp}-{kind}-{tool_id}.txt"
header = [
    "Deterministic companion excerpt for an oversized MCP result.",
    f"Tool: {tool_name}",
    f"Original characters: {len(serialized)}",
    f"Excerpt lines: {len(selected)} (limit {MAX_EXCERPT_LINES})",
    "The original MCP result was not modified. Ellipses mark omitted unrelated output.",
    "",
]
path.write_text("\n".join(header + selected) + "\n", encoding="utf-8")
relative = path.relative_to(ROOT).as_posix()
print(
    json.dumps(
        {
            "additional_context": (
                f"Oversized {kind} MCP output detected. This installed Cursor build cannot replace "
                f"postToolUse output; an exact filtered companion is at {relative}. Prefer that file "
                "and narrower follow-up MCP queries."
            )
        }
    )
)
