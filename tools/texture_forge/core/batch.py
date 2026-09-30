"""Batch generation: Input Folder → Analyze → Generate → Output Folder.

Runs without any GUI so it can also be scripted::

    jobs = jobs_from_folder(Path("refs"), TextureSettings(palette="abyss"))
    run_batch(jobs, Path("out"), variations=4)
"""
from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

from PIL import Image

from export.png import export_series, sanitize_name
from export.resource_pack import PackTexture, export_resource_pack

from .generator import GenerationCancelled, GenerationResult, TextureGenerator
from .presets import BatchPreset
from .seed import variation_seed
from .settings import DEFAULT_MATERIAL, VARIANTS, TextureSettings

IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".webp"}

# filename keyword -> category (first match wins)
CATEGORY_HINTS: list[tuple[tuple[str, ...], str]] = [
    (("pointed_dripstone", "stalactite", "stalagmite"), "dripstone"),
    (("ingot", "nugget", "dust"), "item"),
    (("planks", "_log", "_stem", "_wood", "door", "hyphae"), "wood"),
    (("kelp", "seaweed"), "kelp"),
    (("ore",), "ore"),
    (("amethyst", "crystal", "bud", "geode"), "crystal"),
    (("coral", "sponge", "sculk", "moss", "fungus", "wart"), "organic"),
    (("grass", "fern", "seagrass", "flower", "sapling", "roots", "bush", "vine", "sprouts"), "plant"),
    (("magma", "vent", "smoker", "sulfur", "basalt"), "thermal"),
    (("raw_", "calcite", "dripstone", "mineral", "quartz"), "mineral"),
    (("brick", "tile", "polished", "chiseled", "pillar", "lantern", "lamp", "prismarine"), "decoration"),
    (("iron_block", "copper_block", "gold_block", "metal", "plate", "grate"), "metal"),
]


def guess_category(name: str, has_alpha: bool = False) -> str:
    n = name.lower()
    for keys, cat in CATEGORY_HINTS:
        if any(k in n for k in keys):
            return cat
    return "plant" if has_alpha else "terrain"


@dataclass
class BatchJob:
    settings: TextureSettings
    source: Path | None = None
    label: str = ""

    def load_source(self) -> Image.Image | None:
        if self.source is None:
            return None
        with Image.open(self.source) as im:
            return im.convert("RGBA")


@dataclass
class BatchOutput:
    job: BatchJob
    results: list[GenerationResult] = field(default_factory=list)
    files: list[Path] = field(default_factory=list)
    error: str = ""


def list_images(folder: Path) -> list[Path]:
    return sorted(p for p in Path(folder).iterdir() if p.suffix.lower() in IMAGE_EXTS and p.is_file())


def jobs_from_folder(folder: Path, base: TextureSettings, auto_category: bool = True,
                     prefix: str = "", suffix: str = "") -> list[BatchJob]:
    jobs = []
    for i, path in enumerate(list_images(folder)):
        s = base.copy()
        if auto_category:
            try:
                with Image.open(path) as im:
                    has_alpha = im.convert("RGBA").getextrema()[3][0] < 128
            except OSError:
                continue
            cat = guess_category(path.stem, has_alpha)
            if cat != s.category:
                s.category = cat
                s.material = DEFAULT_MATERIAL[cat]
                s.variant = next(iter(VARIANTS.get(cat, {"auto": ""})))
        s.name = sanitize_name(f"{prefix}{path.stem}{suffix}")
        s.seed = variation_seed(base.seed, i + 1)
        jobs.append(BatchJob(s.clamp(), path, path.name))
    return jobs


def jobs_from_preset(preset: BatchPreset, base: TextureSettings | None = None,
                     reseed: bool = False) -> list[BatchJob]:
    jobs = []
    for i, s in enumerate(preset.jobs(base)):
        if reseed and base is not None:
            s.seed = variation_seed(base.seed, i + 1)
        jobs.append(BatchJob(s, None, s.name))
    return jobs


ProgressFn = Callable[[float, str], None]


def run_batch(jobs: list[BatchJob], output_dir: Path, variations: int = 1,
              generator: TextureGenerator | None = None, progress: ProgressFn | None = None,
              cancelled: Callable[[], bool] | None = None, resource_pack: dict | None = None,
              write_png: bool = True) -> list[BatchOutput]:
    """Generate every job (``variations`` each) and save PNGs.

    ``resource_pack`` (optional) = ``{"mod_id", "pack_name", "description",
    "pack_format", "folder", "as_zip"}`` also bundles everything into a pack.
    """
    gen = generator or TextureGenerator()
    outputs: list[BatchOutput] = []
    total = max(1, len(jobs))
    for j, job in enumerate(jobs):
        if cancelled is not None and cancelled():
            raise GenerationCancelled()
        out = BatchOutput(job)

        def sub(p: float, msg: str, j=j, job=job) -> None:
            if progress:
                progress((j + p) / total, f"[{j + 1}/{total}] {job.settings.name}: {msg}")

        try:
            src = job.load_source()
            out.results = gen.generate_variations(src, job.settings, max(1, variations), sub, cancelled)
            if write_png:
                out.files = export_series([r.image for r in out.results], Path(output_dir),
                                          job.settings.name, [r.settings.to_dict() for r in out.results])
        except GenerationCancelled:
            raise
        except Exception as exc:  # keep going with the other jobs
            out.error = f"{type(exc).__name__}: {exc}"
        outputs.append(out)
    if resource_pack:
        textures = []
        for o in outputs:
            for i, r in enumerate(o.results):
                name = r.settings.name if i == 0 else f"{r.settings.name}_{i:02d}"
                textures.append(PackTexture(name, r.image, resource_pack.get("folder", "block"),
                                            r.settings.to_dict()))
        if textures:
            path = export_resource_pack(
                textures, Path(output_dir), resource_pack.get("mod_id", "modid"),
                resource_pack.get("pack_name", "texture_forge_pack"),
                resource_pack.get("description", ""), resource_pack.get("pack_format", 15),
                resource_pack.get("as_zip", False))
            if outputs:
                outputs[-1].files.append(path)
    if progress:
        progress(1.0, "batch complete")
    return outputs
