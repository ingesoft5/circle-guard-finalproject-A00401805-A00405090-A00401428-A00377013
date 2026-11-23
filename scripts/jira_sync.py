#!/usr/bin/env python3
# CG-DOC: Bidirectional GitHub <-> Jira (DCG) sync without an org-level GitHub app.
# Uses Jira Cloud REST v3 with an Atlassian API token stored as GitHub Actions secrets.
# Design decisions:
# - ADF (Atlassian Document Format) comments, not wiki markup.
# - Idempotent: a marker hash is stored in the comment so re-runs do not duplicate.
# - --dry-run validates mapping, payload generation and exit codes with NO network.
"""Sync GitHub PR events into Jira DCG issues.

Usage:
  python3 scripts/jira_sync.py --event pull_request --dry-run --payload examples/pr_payload.json
  python3 scripts/jira_sync.py --event pull_request --payload "$GITHUB_EVENT_PATH"

Required environment (when not --dry-run):
  JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import sys
from pathlib import Path
from typing import Any
from urllib.error import HTTPError
from urllib.parse import quote

try:
    import yaml  # PyYAML is bundled with GitHub Actions ubuntu runners
except ImportError:  # pragma: no cover - local fallback
    yaml = None

SYNC_MARKER = "circleguard-jira-sync-v1"
DEFAULT_MAPPING = Path(__file__).resolve().parents[1] / "docs/agil/jira-mapping.yml"
DEFAULT_EVIDENCE = Path(__file__).resolve().parents[1] / "docs/agil/jira-evidence-queue.json"
DEFAULT_ISSUES = Path(__file__).resolve().parents[1] / "docs/agil/jira-issues-to-create.json"
DEFAULT_ASSIGNMENTS = Path(__file__).resolve().parents[1] / "docs/agil/jira-sprint-assignment.json"

# Jira Cloud workflow transition names (case-insensitive). Adjust in the mapping file
# if the DCG board renames them.
TRANSITIONS = {
    "opened": "In Progress",
    "reopened": "In Progress",
    "synchronize": "In Progress",
    "ready_for_review": "In Review",
    "closed_merged": "Done",
    "closed_unmerged": "To Do",
}


class SyncError(RuntimeError):
    pass


def load_mapping(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise SyncError(f"Mapping file not found: {path}")
    text = path.read_text(encoding="utf-8")
    if yaml is not None:
        data = yaml.safe_load(text)
    else:  # minimal fallback for the restricted local environment
        data = json.loads(text) if text.lstrip().startswith("{") else _parse_simple_yaml(text)
    if not isinstance(data, dict) or "issues" not in data:
        raise SyncError("Mapping must be a YAML object with an 'issues' key")
    return data


def _parse_simple_yaml(text: str) -> dict[str, Any]:
    """Very small YAML subset parser for the generated mapping (no PyYAML installed)."""
    result: dict[str, Any] = {"issues": {}}
    current: str | None = None
    for raw in text.splitlines():
        line = raw.rstrip()
        if not line or line.lstrip().startswith("#"):
            continue
        if line.startswith("  ") and current:
            key, _, value = line.strip().partition(":")
            result["issues"][current][key.strip()] = value.strip().strip('"\'')
        elif not line.startswith(" ") and line.rstrip().endswith(":"):
            current = line.rstrip()[:-1]
            result["issues"][current] = {}
    return result


def find_issue_key(mapping: dict[str, Any], *texts: str) -> str | None:
    """Resolve a Jira key from PR title/branch/body via the mapping file."""
    combined = "\n".join(t for t in texts if t)
    # Direct DCG-### reference wins.
    direct = re.search(r"\bDCG-\d+\b", combined, flags=re.IGNORECASE)
    if direct:
        return direct.group(0).upper()
    issues: dict[str, Any] = mapping.get("issues", {})
    for hu_key, meta in issues.items():
        aliases = [hu_key] + list(meta.get("aliases", []) or [])
        for alias in aliases:
            if re.search(rf"\b{re.escape(alias)}\b", combined, flags=re.IGNORECASE):
                return str(meta.get("jira"))
    return None


def adf_comment(event: str, action: str, payload: dict[str, Any]) -> tuple[str, list[dict[str, Any]]]:
    pr = payload.get("pull_request", {})
    repo = payload.get("repository", {}).get("full_name", "unknown/unknown")
    number = pr.get("number") or payload.get("number") or "?"
    url = pr.get("html_url") or payload.get("html_url") or ""
    title = pr.get("title") or ""
    actor = payload.get("sender", {}).get("login") or "unknown"
    merge_state = "merged" if pr.get("merged") else action

    body = (
        f"{SYNC_MARKER} | {repo}#{number} | {event}:{merge_state} | by {actor}\n"
        f"Title: {title}\nURL: {url}"
    )
    digest = hashlib.sha256(body.encode("utf-8")).hexdigest()[:12]

    def para(text: str, mark: bool = False) -> dict[str, Any]:
        node: dict[str, Any] = {"type": "paragraph", "content": [{"type": "text", "text": text}]}
        if mark:
            node["content"][0]["marks"] = [{"type": "code"}]
        return node

    doc = {
        "version": 1,
        "type": "doc",
        "content": [
            para(f"{SYNC_MARKER} GitHub sync [{digest}] — {event}/{merge_state}", mark=True),
            para(f"Repository: {repo}  |  PR #{number}"),
            para(f"Title: {title}"),
            para(f"Author: {actor}  |  URL: {url}"),
        ],
    }
    return digest, doc


def jira_request(method: str, path: str, payload: dict[str, Any] | None, dry_run: bool) -> dict[str, Any]:
    if dry_run:
        print(f"[dry-run] {method} {path} payload={json.dumps(payload, ensure_ascii=False)[:180]}")
        return {"dry_run": True}
    base = os.environ.get("JIRA_BASE_URL", "").rstrip("/")
    email = os.environ.get("JIRA_EMAIL", "")
    token = os.environ.get("JIRA_API_TOKEN", "")
    if not (base and email and token):
        raise SyncError("Missing JIRA_BASE_URL / JIRA_EMAIL / JIRA_API_TOKEN")
    auth = base64.b64encode(f"{email}:{token}".encode()).decode()
    body = json.dumps(payload).encode() if payload is not None else None
    try:
        req = urllib_request(base + path, method, body, auth)
        with req as response:
            raw = response.read()
            return json.loads(raw) if raw else {}
    except HTTPError as exc:  # pragma: no cover
        detail = exc.read().decode("utf-8", "replace")[:500]
        raise SyncError(f"Jira API error {exc.code} on {method} {path}: {detail}") from exc
    except Exception as exc:  # pragma: no cover
        raise SyncError(f"Jira API error: {exc}") from exc


def urllib_request(url: str, method: str, body: bytes | None, auth: str):
    from urllib.request import Request, urlopen

    headers = {
        "Authorization": f"Basic {auth}",
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    return urlopen(Request(url, data=body, headers=headers, method=method), timeout=30)


def available_transitions(key: str, dry_run: bool) -> list[str]:
    data = jira_request("GET", f"/rest/api/3/issue/{key}/transitions", None, dry_run)
    if dry_run:
        return ["In Progress", "In Review", "Done"]
    return [t.get("name", "") for t in data.get("transitions", [])]


def do_transition(key: str, action: str, dry_run: bool, transitions: dict[str, str] | None = None) -> None:
    desired = (transitions or TRANSITIONS).get(action)
    if not desired:
        return
    names = available_transitions(key, dry_run)
    match = next((n for n in names if n.lower() == desired.lower()), None)
    if match is None:
        print(f"[warn] transition '{desired}' not available for {key}; available={names}")
        return
    # Resolve the real transition id in live mode.
    if dry_run:
        jira_request("POST", f"/rest/api/3/issue/{key}/transitions", {"transition": {"name": match}}, True)
        return
    data = jira_request("GET", f"/rest/api/3/issue/{key}/transitions", None, False)
    tid = next((t["id"] for t in data.get("transitions", []) if t.get("name") == match), None)
    if tid:
        jira_request("POST", f"/rest/api/3/issue/{key}/transitions", {"transition": {"id": tid}}, False)


def comment_once(key: str, digest: str, doc: dict[str, Any], dry_run: bool) -> None:
    if not dry_run:
        start_at = 0
        while True:
            data = jira_request(
                "GET",
                f"/rest/api/3/issue/{key}/comment?startAt={start_at}&maxResults=100",
                None,
                dry_run,
            )
            comments = data.get("comments", [])
            for existing in comments:
                body_json = json.dumps(existing.get("body", {}))
                if SYNC_MARKER in body_json and digest in body_json:
                    print(f"[skip] comment {digest} already present on {key}")
                    return
            if start_at + len(comments) >= data.get("total", len(comments)) or not comments:
                break
            start_at += len(comments)
    else:
        jira_request("GET", f"/rest/api/3/issue/{key}/comment?startAt=0&maxResults=100", None, True)
    jira_request("POST", f"/rest/api/3/issue/{key}/comment", {"body": doc}, dry_run)


def post_evidence_queue(path: Path, dry_run: bool) -> None:
    """Post queued paragraph-by-paragraph evidence comments (idempotent)."""
    if not path.exists():
        return
    queue = json.loads(path.read_text(encoding="utf-8"))
    for item in queue.get("evidence", []):
        key = item.get("key", "")
        text = item.get("text", "")
        if not re.fullmatch(r"DCG-\d+", key) or not text:
            continue
        paragraph = item.get("paragraph", "")
        seed = f"{SYNC_MARKER} | evidence | {paragraph}\n{text}"
        digest = hashlib.sha256(seed.encode("utf-8")).hexdigest()[:12]
        body = f"{SYNC_MARKER} | evidence | {digest} | {paragraph}\n{text}"
        doc = {"version": 1, "type": "doc", "content": [
            {"type": "paragraph", "content": [{"type": "text", "text": line}]}
            for line in body.splitlines() if line.strip()
        ]}
        comment_once(key, digest, doc, dry_run)
        print(f"[ok] evidence {paragraph} -> {key} ({digest})")


def adf_document(lines: list[str]) -> dict[str, Any]:
    """Build a minimal ADF document from plain text lines."""
    return {
        "version": 1,
        "type": "doc",
        "content": [
            {"type": "paragraph", "content": [{"type": "text", "text": line}]}
            for line in lines
            if line.strip()
        ],
    }


def find_existing_issue(summary: str, dry_run: bool) -> str | None:
    """Return an existing DCG issue key with the same summary (idempotence)."""
    if dry_run:
        return None
    escaped = summary.replace("\\", "\\\\").replace('"', '\\"')
    jql = f'project = DCG AND summary ~ "{escaped}"'
    data = jira_request(
        "GET",
        f"/rest/api/3/search/jql?jql={quote(jql)}&maxResults=10&fields=summary",
        None,
        dry_run,
    )
    for issue in data.get("issues", []):
        fields = issue.get("fields", {}) or {}
        if (fields.get("summary") or "").strip() == summary.strip():
            return issue.get("key")
    return None


def create_issues(path: Path, dry_run: bool) -> None:
    """Create DCG issues declared in an evidence-backed JSON file."""
    if not path.exists():
        print(f"[warn] issues file not found: {path}")
        return
    data = json.loads(path.read_text(encoding="utf-8"))
    for item in data.get("issues", []):
        summary = item.get("summary", "").strip()
        if not summary:
            continue
        existing = find_existing_issue(summary, dry_run)
        if existing:
            print(f"[skip] issue already exists: {existing} - {summary}")
            continue
        fields: dict[str, Any] = {
            "project": {"key": "DCG"},
            "summary": summary,
            "issuetype": {"name": item.get("issue_type", "Task")},
            "description": adf_document(item.get("description", [])),
        }
        labels = item.get("labels") or []
        if labels:
            fields["labels"] = labels
        created = jira_request("POST", "/rest/api/3/issue", {"fields": fields}, dry_run)
        key = created.get("key") if isinstance(created, dict) else None
        if key:
            print(f"[ok] created {key} - {summary}")
        else:
            print(f"[ok] would create - {summary}")


def resolve_sprint_ids(board_id: int, names: list[str], dry_run: bool) -> dict[str, int]:
    """Map sprint names to real Jira Agile IDs from board DCG (67)."""
    if dry_run:
        return {name: idx + 1 for idx, name in enumerate(names)}
    data = jira_request(
        "GET",
        f"/rest/agile/1.0/board/{board_id}/sprint?maxResults=50&state=active,closed,future",
        None,
        dry_run,
    )
    result: dict[str, int] = {}
    for sprint in data.get("values", []):
        name = sprint.get("name")
        sid = sprint.get("id")
        if name and sid is not None:
            result[str(name)] = int(sid)
    return result


def resolve_sprint_field(dry_run: bool) -> str | None:
    """Discover the Jira custom field id whose name is Sprint."""
    if dry_run:
        return "customfield_SPRINT"
    fields = jira_request("GET", "/rest/api/3/field", None, dry_run)
    if isinstance(fields, list):
        for field in fields:
            if str(field.get("name", "")).lower() == "sprint" and field.get("custom"):
                return field.get("id")
    return None


def current_issue_state(key: str, field_id: str, dry_run: bool) -> tuple[set[int], list[str]]:
    """Return the issue's current sprint ids and labels."""
    if dry_run:
        return set(), []
    data = jira_request(
        "GET", f"/rest/api/3/issue/{key}?fields={field_id},labels", None, dry_run
    )
    fields = data.get("fields") or {}
    value = fields.get(field_id)
    if not value:
        value = []
    if isinstance(value, (str, int)):
        value = [value]
    ids: set[int] = set()
    for entry in value:
        raw = entry.get("id") if isinstance(entry, dict) else entry
        try:
            ids.add(int(raw))
        except (TypeError, ValueError):
            continue
    labels = [str(label) for label in (fields.get("labels") or [])]
    return ids, labels


def assign_sprints(path: Path, dry_run: bool) -> None:
    """Assign evidence issues to the Jira sprints declared in a JSON file."""
    if not path.exists():
        print(f"[warn] sprint assignment file not found: {path}")
        return
    config = json.loads(path.read_text(encoding="utf-8"))
    board_id = int(config.get("board_id", 67))
    default_names = config.get("sprints", [])
    field_id = resolve_sprint_field(dry_run)
    if not field_id:
        raise SyncError("Jira Sprint custom field not found")
    all_names = list(default_names)
    for item in config.get("issues", []):
        all_names.extend(item.get("sprints") or [])
    sprint_map = resolve_sprint_ids(board_id, all_names, dry_run)

    for item in config.get("issues", []):
        key = item.get("key", "")
        if not re.fullmatch(r"DCG-\d+", key):
            continue
        names = item.get("sprints") or default_names
        resolved: dict[str, int] = {}
        for name in names:
            sid = sprint_map.get(name)
            if sid is None:
                needle = name.lower()
                match = next(
                    (sid for sname, sid in sprint_map.items() if needle in sname.lower()), None
                )
                if match is not None:
                    print(f"[ok] sprint '{name}' matched Jira sprint id {match}")
                    sid = match
            if sid is None:
                print(f"[warn] sprint '{name}' not found on board {board_id}")
                continue
            resolved[name] = sid
        if not resolved:
            continue

        current_name = item.get("current_sprint") or names[-1]
        also_names = item.get("also_sprints") or [n for n in names if n != current_name]
        primary = resolved.get(current_name) or list(resolved.values())[-1]
        current_ids, current_labels = current_issue_state(key, field_id, dry_run)

        also_labels: list[str] = []
        for name in also_names:
            if name not in resolved:
                continue
            number = re.search(r"\d+", name)
            also_labels.append(f"sprint-{number.group(0)}" if number else "sprint-secondary")
        merged_labels = sorted(set(current_labels) | set(also_labels))

        if current_ids == {primary} and set(current_labels) == set(merged_labels):
            print(f"[skip] sprint already assigned on {key}: {current_name} (id={primary})")
        else:
            jira_request(
                "PUT",
                f"/rest/api/3/issue/{key}",
                {"fields": {field_id: primary, "labels": merged_labels}},
                dry_run,
            )
            print(
                f"[ok] assigned {key} -> current={current_name} id={primary} "
                f"also={also_names} labels={also_labels}"
            )

        note = item.get("note", "")
        seed = f"{SYNC_MARKER} | sprint | {key} | {', '.join(names)}\n{note}"
        digest = hashlib.sha256(seed.encode("utf-8")).hexdigest()[:12]
        body = f"{SYNC_MARKER} | sprint | {digest} | {key} | {', '.join(names)}\n{note}"
        comment_once(key, digest, adf_document(body.splitlines()), dry_run)
        print(f"[ok] sprint note {key} ({digest})")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event", help="GitHub event name (pull_request, pull_request_review, issues)")
    parser.add_argument("--payload", help="Path to the GitHub event JSON payload")
    parser.add_argument("--mapping", default=str(DEFAULT_MAPPING))
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--verify", action="store_true", help="Verify Jira credentials and DCG project access")
    parser.add_argument("--evidence-queue", default=str(DEFAULT_EVIDENCE))
    parser.add_argument("--apply-evidence", action="store_true", help="Post queued paragraph evidence comments")
    parser.add_argument("--create-issues", action="store_true", help="Create missing DCG issues from the issues file")
    parser.add_argument("--issues-file", default=str(DEFAULT_ISSUES))
    parser.add_argument("--assign-sprints", action="store_true", help="Assign DCG issues to Jira sprints")
    parser.add_argument("--assignments-file", default=str(DEFAULT_ASSIGNMENTS))
    args = parser.parse_args()

    if args.verify:
        me = jira_request("GET", "/rest/api/3/myself", None, False)
        project = jira_request("GET", "/rest/api/3/project/DCG", None, False)
        print(f"[ok] Jira auth: {me.get('displayName')} <{me.get('emailAddress')}>")
        print(f"[ok] Project: {project.get('key')} - {project.get('name')}")
        return 0
    if args.assign_sprints:
        assign_sprints(Path(args.assignments_file), args.dry_run)
        if not args.event or not args.payload:
            return 0
    if args.create_issues:
        create_issues(Path(args.issues_file), args.dry_run)
        if not args.event or not args.payload:
            return 0
    if args.apply_evidence:
        post_evidence_queue(Path(args.evidence_queue), args.dry_run)
        if not args.event or not args.payload:
            return 0
    if not args.event or not args.payload:
        parser.error("--event and --payload are required unless --verify is used")

    payload = json.loads(Path(args.payload).read_text(encoding="utf-8"))
    mapping = load_mapping(Path(args.mapping))
    pr = payload.get("pull_request", {})
    action = payload.get("action", "")
    text = " ".join([pr.get("title", ""), pr.get("head", {}).get("ref", ""),
                     (pr.get("body") or ""), payload.get("issue", {}).get("title", "")])
    key = find_issue_key(mapping, text)
    if key is None:
        print(f"[skip] no DCG key found in event {args.event}/{action}; add it to the PR title")
        return 0

    digest, doc = adf_comment(args.event, action, payload)
    if args.event == "pull_request":
        state = "closed_merged" if action == "closed" and pr.get("merged") else (
            "closed_unmerged" if action == "closed" else action
        )
        do_transition(key, state, args.dry_run, mapping.get("transitions"))
    comment_once(key, digest, doc, args.dry_run)
    post_evidence_queue(Path(args.evidence_queue), args.dry_run)
    print(f"[ok] {args.event}/{action} -> {key} ({digest})")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except SyncError as exc:
        print(f"[error] {exc}", file=sys.stderr)
        sys.exit(1)
