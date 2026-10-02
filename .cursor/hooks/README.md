# Cursor hook notes

`session-init.py` performs read-only wireless-ADB identity checks and maintains
`.agent-local/device-state.json`. It never connects, disconnects, restarts, or
reconfigures ADB. SystemUI and MIUISystemUIPlugin hashes are reused while the
build fingerprint, package version/path, and APK size remain unchanged.

`filter-noisy-mcp.py` is intentionally limited to oversized Android/JADX MCP
results. It writes exact, deterministically selected excerpts under
`.agent-local/evidence/mcp-filtered/`; it does not call an LLM.

Cursor 3.23.12's installed `postToolUse` response mapper accepts only
`additional_context` and does not forward `updated_mcp_tool_output`, despite the
newer public schema documenting that field. Therefore this workspace does not
attempt unsafe output replacement. The original MCP result remains intact, and
the hook adds only a short pointer to the filtered companion. Re-check the
installed validator before enabling replacement after a Cursor upgrade.
