"""Shared helpers for the Python side (training / prediction). Prompts come from the dataset's
sft files, which are built by lib/prompts.js, so Python never re-implements prompt text."""

import json
import random


def load_sft(path):
    with open(path, encoding="utf8") as f:
        return [json.loads(line) for line in f if line.strip()]


def write_json(path, obj):
    with open(path, "w", encoding="utf8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def sample_rows(rows, limit, seed=1):
    """Deterministic subsample that keeps the task mix."""
    if not limit or limit >= len(rows):
        return rows
    rng = random.Random(seed)
    by_task = {}
    for r in rows:
        by_task.setdefault(r.get("task", "-"), []).append(r)
    out = []
    for task, items in sorted(by_task.items()):
        k = max(1, round(limit * len(items) / len(rows)))
        out += rng.sample(items, min(k, len(items)))
    return sorted(out, key=lambda r: r["id"])
