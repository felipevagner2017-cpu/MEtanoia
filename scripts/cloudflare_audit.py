#!/usr/bin/env python3
"""Read-only Cloudflare account inventory and Workers Builds diagnostics."""
import json, os, re, urllib.error, urllib.request

token = os.environ.get("CLOUDFLARE_API_TOKEN", "").strip()
account = os.environ.get("CLOUDFLARE_ACCOUNT_ID", "").strip()
if not token or not account:
    print("Cloudflare audit skipped: GitHub Actions secrets CLOUDFLARE_API_TOKEN and/or CLOUDFLARE_ACCOUNT_ID are missing.")
    print("Required read scopes: Workers Scripts Read, Workers CI Read, D1 Read.")
    raise SystemExit(0)

BASE = "https://api.cloudflare.com/client/v4"
HEADERS = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
SUMMARY = []

def get(path):
    req = urllib.request.Request(BASE + path, headers=HEADERS)
    try:
        with urllib.request.urlopen(req, timeout=25) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        try: body = json.loads(exc.read().decode("utf-8"))
        except Exception: body = {}
        return exc.code, body
    except Exception as exc:
        return 0, {"error": type(exc).__name__}

def add(title, lines):
    SUMMARY.append("## " + title)
    SUMMARY.extend(lines or ["- No records returned."])

status, data = get(f"/accounts/{account}/workers/scripts")
workers = data.get("result", []) if status == 200 and data.get("success") else []
add(f"Workers inventory (HTTP {status})", [
    f"- {w.get('id')} — tag {w.get('tag', 'not returned')} — modified {w.get('modified_on', 'unknown')}"
    for w in workers
] if workers else [f"- Inventory unavailable or empty. Check Workers Scripts Read. API message: {data.get('errors', data.get('error', 'none'))}"])

for worker_name in ("metanoia", "metanoia-preview", "metonoia"):
    match = next((w for w in workers if w.get("id") == worker_name), None)
    if not match or not match.get("tag"):
        SUMMARY.append("## Builds for " + worker_name + "\n- Worker not found in this account.")
        continue
    build_status, build_data = get(f"/accounts/{account}/builds/workers/{match['tag']}/builds")
    builds = build_data.get("result", []) if build_status == 200 and build_data.get("success") else []
    add(f"Build history for {worker_name} (HTTP {build_status})", [
        f"- {b.get('build_uuid')} — outcome {b.get('build_outcome', b.get('status', 'unknown'))} — branch {(b.get('build_trigger_metadata') or {}).get('branch', 'unknown')} — commit {(b.get('build_trigger_metadata') or {}).get('commit_hash', 'unknown')}"
        for b in builds[:8]
    ] if builds else ["- Build history unavailable or empty. Check Workers CI Read."])

for worker_name in ("metanoia", "metanoia-preview", "metonoia"):
    status, data = get(f"/accounts/{account}/workers/scripts/{worker_name}/settings")
    settings = data.get("result", {}) if status == 200 and data.get("success") else {}
    bindings = settings.get("bindings", [])
    binding_lines = [
        f"- {b.get('type', 'unknown')} binding {b.get('name', 'unnamed')}" +
        (f" — database {b.get('database_name', b.get('id', 'not returned'))}" if b.get("type", "").lower() == "d1" else "")
        for b in bindings
    ]
    add(f"Bindings for {worker_name} (HTTP {status})", [
        f"- workers_dev={settings.get('workers_dev', 'not returned')}; compatibility_date={settings.get('compatibility_date', 'not returned')}"
    ] + (binding_lines if binding_lines else ["- No bindings returned or Worker settings are not accessible."]))

status, data = get(f"/accounts/{account}/workers/domains")
domains = data.get("result", []) if status == 200 and data.get("success") else []
add(f"Worker domain mapping (HTTP {status})", [
    f"- {d.get('hostname')} -> Worker {d.get('service')} — enabled {d.get('enabled')} — zone {d.get('zone_name')}"
    for d in domains
] if domains else ["- Domain inventory unavailable or empty. Check Workers Scripts Read."])

status, data = get(f"/accounts/{account}/workers/subdomain")
subdomain = data.get("result", {}).get("subdomain") if status == 200 and data.get("success") else None
add(f"Workers.dev account subdomain (HTTP {status})", [
    f"- Account subdomain: {subdomain}"
] if subdomain else ["- Subdomain unavailable."])

status, data = get(f"/accounts/{account}/d1/database")
databases = data.get("result", []) if status == 200 and data.get("success") else []
add(f"D1 database inventory (HTTP {status})", [
    f"- {d.get('name')} — ID {d.get('uuid', d.get('id', 'unknown'))} — created {d.get('created_at', 'unknown')}"
    for d in databases
] if databases else ["- D1 inventory unavailable or empty. Check D1 Read."])

# Public, read-only smoke checks for the isolated Preview Worker. The login probe
# uses a synthetic address and an invalid password; it never creates or mutates data.
PREVIEW_URL = "https://metanoia-preview.restaura-lar.workers.dev"
def public_probe(path, method="GET", payload=None):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"User-Agent": "Mozilla/5.0 (compatible; MEtanoia-Preview-Audit/1.0)", "Accept": "text/html,application/json"}
    if body is not None:
        headers.update({"Content-Type": "application/json", "Origin": PREVIEW_URL})
    req = urllib.request.Request(PREVIEW_URL + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            return response.status, response.headers.get("Access-Control-Allow-Origin", ""), response.read(1024).decode("utf-8", "replace"), dict(response.headers)
    except urllib.error.HTTPError as exc:
        return exc.code, exc.headers.get("Access-Control-Allow-Origin", ""), exc.read(1024).decode("utf-8", "replace"), dict(exc.headers)
    except Exception as exc:
        return 0, "", type(exc).__name__, {}

root_status, _, root_body, root_headers = public_probe("/")
SUMMARY.append(f"## Preview static PWA smoke check (HTTP {root_status})")
SUMMARY.append(f"- Response server={root_headers.get('Server', 'not returned')}; cf-ray={root_headers.get('CF-RAY', 'not returned')}")
SUMMARY.append(f"- Response diagnostic={root_body[:240].replace(chr(10), ' ')}")
login_status, cors_origin, login_body, login_headers = public_probe("/api/auth/login", "POST", {
    "email": "preview-readonly-probe@example.invalid",
    "password": "invalid-probe-credential",
    "platform": "pwa",
})
try:
    login_error = json.loads(login_body).get("error", "")
except Exception:
    login_error = "non-JSON response"
SUMMARY.extend([
    f"## Preview D1 API smoke check (HTTP {login_status})",
    f"- CORS origin accepted: {cors_origin == PREVIEW_URL}",
    f"- Diagnostic: {login_error or login_body[:240].replace(chr(10), ' ') or 'no error message'}",
    f"- Response server={login_headers.get('Server', 'not returned')}; cf-ray={login_headers.get('CF-RAY', 'not returned')}",
    "- Probe is read-only: synthetic invalid login only; no account or row is created.",
])

for build_id in ("b32cf052-dba3-4f71-a794-c7c91cceaa5a", "d08878b1-c4bf-4e7a-9c58-f745332e23cd", "ff732fe1-14d1-493f-bd97-658253f49053", "acb6fba7-4e02-4a40-991d-873faabe8eba", "99a49e20-ae4d-424d-8bb2-2a328fbd31fc"):
    status, data = get(f"/accounts/{account}/builds/builds/{build_id}")
    result = data.get("result", {}) if status == 200 and data.get("success") else {}
    meta = result.get("build_trigger_metadata") or {}
    SUMMARY.append("## Build details " + build_id + " (HTTP " + str(status) + ")")
    if result:
        SUMMARY.extend([
            "- Outcome: " + str(result.get("build_outcome", "unknown")),
            "- Status: " + str(result.get("status", "unknown")),
            "- Branch: " + str(meta.get("branch", "unknown")),
            "- Build command: " + str(meta.get("build_command", "default")),
            "- Deploy command: " + str(meta.get("deploy_command", "default")),
            "- Root directory: " + str(meta.get("root_directory", "root")),
            "- Commit: " + str(meta.get("commit_hash", "unknown")),
        ])
        log_status, log_data = get(f"/accounts/{account}/builds/builds/{build_id}/logs")
        lines = []
        if log_status == 200 and log_data.get("success"):
            for item in log_data.get("result", {}).get("lines", []):
                line = str(item[-1] if isinstance(item, list) and item else item)
                if re.search(r"error|failed|denied|invalid|missing|not found|exception|timed out", line, re.I):
                    line = re.sub(r"(?i)Bearer\s+[A-Za-z0-9._~+/-]+", "Bearer [REDACTED]", line)
                    line = re.sub(r"(?i)((?:TOKEN|SECRET|PASSWORD|API_KEY)[A-Z0-9_]*=)[^\s]+", r"\1[REDACTED]", line)
                    lines.append(line[:500])
        SUMMARY.append("### Error excerpts (HTTP " + str(log_status) + ")")
        SUMMARY.extend(["- " + line for line in lines[-40:]] if lines else ["- No error lines returned; verify Workers CI Read scope and log availability."])
    else:
        SUMMARY.append("- Build details unavailable. Check Workers CI Read.")

output = "\n".join(SUMMARY) + "\n"
print(output)
summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
if summary_path:
    with open(summary_path, "a", encoding="utf-8") as handle: handle.write(output)
