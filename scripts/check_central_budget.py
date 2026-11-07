"""Estimate Maven Central quota usage from a fresh unsigned ReleaseCheck repository."""

import argparse
from collections import defaultdict
from pathlib import Path

# The Central upload keeps one .asc, one .md5 and one .sha1 per payload.
SIDECAR_SUFFIXES = {".asc", ".md5", ".sha1", ".sha256", ".sha512"}
SIGNATURE_RESERVE = 4096
CHECKSUM_BYTES = 32 + 40


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repository", type=Path)
    parser.add_argument("version")
    parser.add_argument("--max-files", type=int)
    parser.add_argument("--max-bytes", type=int)
    parser.add_argument("--require-budget", action="store_true")
    args = parser.parse_args()
    limits = (args.max_files, args.max_bytes)
    if any(limit is not None and limit < 0 for limit in limits):
        parser.error("Budgets must be non-negative")
    group = args.repository / "io/github/roman-n1/mothlight"
    payloads = [
        (path, path.stat().st_size)
        for path in sorted(group.glob(f"*/{args.version}/*"))
        if path.is_file() and path.suffix not in SIDECAR_SUFFIXES
        and "maven-metadata" not in path.name
    ]
    if not payloads:
        parser.error(f"No Mothlight {args.version} payloads in {group}")

    by_artifact = defaultdict(list)
    for path, size in payloads:
        by_artifact[path.parent.parent.name].append(size)

    files = len(payloads) * 4
    payload_bytes = sum(size for _, size in payloads)
    estimated_bytes = payload_bytes + len(payloads) * (SIGNATURE_RESERVE + CHECKSUM_BYTES)
    print(f"### Maven Central preflight: Mothlight {args.version}\n")
    print(f"- {len(by_artifact)} coordinates, {len(payloads)} unsigned payloads "
          f"({payload_bytes:,} bytes).")
    print(f"- Estimated upload: **{files:,} files**, **{estimated_bytes:,} bytes** "
          "before ZIP compression (includes 4 KiB reserved per signature).")
    print("- Compare with the remaining monthly allowance in Central Portal Usage Center.\n")

    failed = False
    for label, used, limit in zip(("files", "bytes"), (files, estimated_bytes), limits):
        if limit is not None:
            remaining = limit - used
            if remaining < 0:
                print(f"- **Over budget by {-remaining:,} {label}** (budget: {limit:,}).")
                failed = True
            else:
                print(f"- Budget: {limit:,} {label}; headroom: {remaining:,} {label}.")
        else:
            print(f"- No remaining {label} budget configured.")
            failed |= args.require_budget

    print("\n| Artifact | Estimated files | Payload bytes |")
    print("| --- | ---: | ---: |")
    for artifact, sizes in by_artifact.items():
        print(f"| `{artifact}` | {len(sizes) * 4:,} | {sum(sizes):,} |")

    print("\nLargest payloads:")
    for path, size in sorted(payloads, key=lambda item: -item[1])[:5]:
        print(f"- `{path.relative_to(group)}`: {size:,} bytes")

    if failed:
        parser.exit(1, "Maven Central preflight failed: missing or exceeded quota budget.\n")


if __name__ == "__main__":
    main()
