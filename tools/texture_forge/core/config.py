"""Application paths and persistent user configuration."""
from __future__ import annotations

import dataclasses
import json
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRESET_DIR = ROOT / "presets"
BATCH_PRESET_DIR = PRESET_DIR / "batch"
OUTPUT_DIR = ROOT / "output"
HISTORY_DIR = OUTPUT_DIR / "history"
ASSET_DIR = ROOT / "assets"
CONFIG_PATH = ROOT / "config.json"

# Resource pack format per Minecraft version (Java Edition).
PACK_FORMATS: dict[str, int] = {
    "1.20.1": 15,
    "1.20.2": 18,
    "1.20.4": 22,
    "1.20.6": 32,
    "1.21.1": 34,
    "1.21.4": 46,
}


@dataclass
class AIConfig:
    provider: str = "ollama"          # ollama | openai | none
    host: str = "http://127.0.0.1:11434"
    model: str = "qwen3.5:4b"
    api_key: str = ""
    timeout: float = 60.0
    temperature: float = 0.2


@dataclass
class AppConfig:
    mod_id: str = "abyssalworld"
    texture_name: str = "abyssal_rock"
    output_dir: str = str(OUTPUT_DIR)
    minecraft_version: str = "1.20.1"
    pack_description: str = "Generated with Texture Forge"
    texture_folder: str = "block"
    live_preview: bool = True
    show_grid: bool = True
    preview_background: str = "checker"
    variation_count: int = 8
    recent_sources: list[str] = field(default_factory=list)
    ai: AIConfig = field(default_factory=AIConfig)

    @property
    def pack_format(self) -> int:
        return PACK_FORMATS.get(self.minecraft_version, 15)

    def to_dict(self) -> dict:
        return dataclasses.asdict(self)

    @classmethod
    def from_dict(cls, d: dict) -> "AppConfig":
        cfg = cls()
        for f in dataclasses.fields(cls):
            if f.name == "ai":
                ai = d.get("ai", {})
                cfg.ai = AIConfig(**{k: v for k, v in ai.items() if k in AIConfig.__dataclass_fields__})
            elif f.name in d:
                setattr(cfg, f.name, d[f.name])
        return cfg

    @classmethod
    def load(cls, path: Path = CONFIG_PATH) -> "AppConfig":
        try:
            return cls.from_dict(json.loads(Path(path).read_text(encoding="utf-8")))
        except (OSError, ValueError, TypeError):
            return cls()

    def save(self, path: Path = CONFIG_PATH) -> None:
        Path(path).write_text(json.dumps(self.to_dict(), indent=2, ensure_ascii=False), encoding="utf-8")

    def add_recent(self, path: str, limit: int = 10) -> None:
        if path in self.recent_sources:
            self.recent_sources.remove(path)
        self.recent_sources.insert(0, path)
        del self.recent_sources[limit:]
