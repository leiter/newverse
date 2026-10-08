#!/usr/bin/env python3
"""Rewrite a database export so the seller is a different auth uid.

The seller's uid is the key of /articles, /orders and /seller_profile, and is
repeated in each order's `sellerId` and in the seller profile's `id`/`sellerId`.
Because no other value embeds it, a whole-string swap is enough -- but this
checks that assumption on the actual file instead of trusting it, and refuses
when the old uid turns up as a substring of something larger.

    python3 rename_seller_uid.py <in.json> <out.json> <old-uid> <new-uid>

The new uid must be the uid Firebase Auth has ALREADY assigned to the seller's
account in the target project: the rules gate writes on `auth.uid === $sellerId`,
so an invented value locks the seller out of their own data. Sign the seller in
against that project once and read the uid off their Auth record.

Reads and writes files only. It never contacts a Firebase project.
"""
import json
import sys


def main() -> int:
    if len(sys.argv) != 5:
        print(__doc__)
        return 2
    src, dst, old, new = sys.argv[1:5]

    if old == new:
        print("Old and new uid are the same; nothing to do.")
        return 2
    raw = open(src, encoding="utf-8").read()
    if new in raw:
        print(f"Refusing: the new uid {new!r} already occurs in {src}.")
        print("Pick the right target, or the rename would merge two sellers.")
        return 1

    # Verify the swap is safe before doing it: every occurrence must be a whole
    # key or a whole value, never part of a longer string.
    data = json.loads(raw)
    exact, substring = [0], []

    def walk(node, path):
        if isinstance(node, dict):
            for k, v in node.items():
                if old in k:
                    if k == old:
                        exact[0] += 1
                    else:
                        substring.append(f"{path}/<key {k!r}>")
                walk(v, f"{path}/{k}")
        elif isinstance(node, list):
            for i, v in enumerate(node):
                walk(v, f"{path}[{i}]")
        elif isinstance(node, str) and old in node:
            if node == old:
                exact[0] += 1
            else:
                substring.append(f"{path} = {node!r}")

    walk(data, "")

    print(f"{exact[0]} whole-value occurrence(s) of {old}")
    if substring:
        print(f"\n{len(substring)} occurrence(s) are PART of a longer string:")
        for s in substring[:20]:
            print(f"  {s}")
        print("\nRefusing: a blind replace would corrupt these. Handle them by hand.")
        return 1
    if exact[0] == 0:
        print(f"Refusing: {old} does not appear in {src}.")
        return 1

    # Safe: rewrite structurally rather than by text, so formatting stays intact.
    def swap(node):
        if isinstance(node, dict):
            return {(new if k == old else k): swap(v) for k, v in node.items()}
        if isinstance(node, list):
            return [swap(v) for v in node]
        if node == old:
            return new
        return node

    out = swap(data)
    json.dump(out, open(dst, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    open(dst, "a", encoding="utf-8").write("\n")

    check = open(dst, encoding="utf-8").read()
    remaining = check.count(old)
    print(f"\nWrote {dst}")
    print(f"  occurrences of the new uid: {check.count(new)}")
    print(f"  occurrences of the old uid: {remaining}")
    if remaining:
        print("  WARNING: the old uid survives somewhere; inspect before importing.")
        return 1
    print("\nRemember to update PROD_SELLER_ID in shared/build.gradle.kts to match,")
    print("or release builds will read the old, now non-existent seller node.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
