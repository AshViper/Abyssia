"""Deterministic seeding.

Every random decision in the generator pulls from a stream derived from
``(seed, key...)``.  Each layer uses its own key, so switching one layer
off never changes what the other layers draw: the same seed + settings
always reproduce the same texture.
"""
from __future__ import annotations

import secrets
import zlib

import numpy as np

MAX_SEED = 2**31 - 1


def _key_hash(key: object) -> int:
    # zlib.crc32 is stable across processes, unlike built-in hash()
    return zlib.crc32(str(key).encode("utf-8"))


def derive_rng(seed: int, *keys: object) -> np.random.Generator:
    """Return an independent generator for ``seed`` namespaced by ``keys``."""
    entropy = [int(seed) & 0xFFFFFFFF] + [_key_hash(k) for k in keys]
    return np.random.default_rng(np.random.SeedSequence(entropy))


def random_seed() -> int:
    return secrets.randbelow(MAX_SEED) + 1


def variation_seed(seed: int, index: int) -> int:
    """Seed for variation ``index`` (index 0 is the base seed itself)."""
    if index == 0:
        return int(seed)
    rng = derive_rng(seed, "variation", index)
    return int(rng.integers(1, MAX_SEED))


class SeedStreams:
    """Convenience wrapper handing out per-purpose generators for one seed."""

    def __init__(self, seed: int):
        self.seed = int(seed)

    def __call__(self, *keys: object) -> np.random.Generator:
        return derive_rng(self.seed, *keys)
