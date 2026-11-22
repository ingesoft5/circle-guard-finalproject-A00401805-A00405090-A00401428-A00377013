"""Materialize allowlisted cg-dev manifests from a Jenkins-approved Form image.

No Kubernetes API calls. The output is under build/ and must be reviewed before use.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

from verify import main as verify, render, ROOT


def write_json(path, objects):
    payload = json.dumps({"apiVersion": "v1", "kind": "List", "items": list(objects.values())},
                         indent=2, sort_keys=True) + "\n"
    path.write_text(payload, encoding="utf-8")
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True, help="Semantic image version recorded by Jenkins")
    parser.add_argument("--commit", required=True, help="Full Git commit SHA built by Jenkins")
    parser.add_argument("--digest", required=True, help="Registry sha256 digest recorded by Jenkins")
    parser.add_argument("--jenkins-build-url", required=True)
    parser.add_argument("--output-dir", type=Path, default=ROOT.parents[2] / "build" / "form06-release")
    args = parser.parse_args()
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[a-zA-Z0-9]+(?:[.-][a-zA-Z0-9]+)*)?", args.tag):
        parser.error("Tag must be a semantic image version")
    if not re.fullmatch(r"[0-9a-f]{40}", args.commit):
        parser.error("Commit must be a full Git SHA-1")
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", args.digest):
        parser.error("Digest must be a sha256 registry digest")
    if not args.jenkins_build_url.startswith(("https://", "http://")):
        parser.error("Jenkins build URL is required")
    repository = ROOT.parents[2]
    head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=repository,
                          capture_output=True, text=True, check=True).stdout.strip()
    if head != args.commit:
        parser.error("Jenkins source commit differs from the checked-out commit")
    status = subprocess.run(["git", "status", "--porcelain"], cwd=repository,
                            capture_output=True, text=True, check=True).stdout.strip()
    if status:
        parser.error("Working tree must be clean before preparing release artifacts")

    verify()
    canary = render(ROOT / "canary")
    monitoring = render(ROOT / "monitoring")
    image = f"circleguard-docker/form:{args.tag}@{args.digest}"
    deployment = canary[("Deployment", "cg-dev", "form-hu6")]
    deployment["spec"]["template"]["spec"]["containers"][0]["image"] = image

    output = args.output_dir
    output.mkdir(parents=True, exist_ok=True)
    monitoring_hash = write_json(output / "monitoring.json", monitoring)
    canary_hash = write_json(output / "canary.json", canary)
    evidence = {
        "tag": args.tag,
        "source_commit": args.commit,
        "registry_digest": args.digest,
        "jenkins_build_url": args.jenkins_build_url,
        "monitoring_sha256": monitoring_hash,
        "canary_sha256": canary_hash,
        "monitoring_resources": sorted(f"{kind}/{namespace or 'cluster'}/{name}"
                                       for kind, namespace, name in monitoring),
        "canary_resources": sorted(f"{kind}/{namespace}/{name}" for kind, namespace, name in canary),
    }
    (output / "evidence.json").write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print("Prepared reviewed manifests under", output)
    print("Canary image:", image)
    print("Commit:", args.commit, "Digest:", args.digest)


if __name__ == "__main__":
    main()
