"""Undo / redo and the generation history (GUI independent)."""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

from PIL import Image

from .settings import TextureSettings


@dataclass
class HistoryEntry:
    settings: TextureSettings
    image: Image.Image
    timestamp: datetime = field(default_factory=datetime.now)
    source_path: str = ""
    file: str = ""  # PNG path when persisted
    result: object | None = field(default=None, repr=False, compare=False)  # in-memory GenerationResult

    @property
    def name(self) -> str:
        return self.settings.name

    def label(self) -> str:
        return f"{self.timestamp:%H:%M} {self.name}"

    def same_as(self, other: "HistoryEntry") -> bool:
        return self.settings.to_dict() == other.settings.to_dict()


class UndoStack:
    """Linear undo/redo over generation states."""

    def __init__(self, limit: int = 200):
        self.limit = limit
        self._items: list[HistoryEntry] = []
        self._index = -1

    def push(self, entry: HistoryEntry) -> None:
        if self.current is not None and self.current.same_as(entry):
            self._items[self._index] = entry
            return
        del self._items[self._index + 1:]
        self._items.append(entry)
        if len(self._items) > self.limit:
            self._items.pop(0)
        self._index = len(self._items) - 1

    @property
    def current(self) -> HistoryEntry | None:
        return self._items[self._index] if 0 <= self._index < len(self._items) else None

    def can_undo(self) -> bool:
        return self._index > 0

    def can_redo(self) -> bool:
        return self._index < len(self._items) - 1

    def undo(self) -> HistoryEntry | None:
        if not self.can_undo():
            return None
        self._index -= 1
        return self._items[self._index]

    def redo(self) -> HistoryEntry | None:
        if not self.can_redo():
            return None
        self._index += 1
        return self._items[self._index]

    def clear(self) -> None:
        self._items.clear()
        self._index = -1

    def __len__(self) -> int:
        return len(self._items)


_SAFE = re.compile(r"[^a-z0-9_\-]+")


class History:
    """Generation history, newest first, optionally persisted to a folder.

    Each entry is stored as ``<stamp>_<name>.png`` plus a JSON sidecar with
    the exact settings, so any entry can be restored or regenerated later.
    """

    def __init__(self, folder: Path | None = None, limit: int = 300):
        self.folder = Path(folder) if folder else None
        self.limit = limit
        self.entries: list[HistoryEntry] = []

    def add(self, entry: HistoryEntry) -> HistoryEntry:
        if self.entries and self.entries[0].same_as(entry):
            return self.entries[0]
        self.entries.insert(0, entry)
        if self.folder is not None:
            self._persist(entry)
        while len(self.entries) > self.limit:
            old = self.entries.pop()
            self._delete_files(old)
        return entry

    def _persist(self, entry: HistoryEntry) -> None:
        try:
            self.folder.mkdir(parents=True, exist_ok=True)
            safe = _SAFE.sub("_", entry.name.lower()) or "texture"
            stem = f"{entry.timestamp:%Y%m%d_%H%M%S_%f}_{safe}"
            png = self.folder / f"{stem}.png"
            entry.image.save(png)
            meta = {"timestamp": entry.timestamp.isoformat(), "source": entry.source_path,
                    "settings": entry.settings.to_dict()}
            png.with_suffix(".json").write_text(json.dumps(meta, indent=1), encoding="utf-8")
            entry.file = str(png)
        except OSError:
            entry.file = ""

    def _delete_files(self, entry: HistoryEntry) -> None:
        if not entry.file:
            return
        for p in (Path(entry.file), Path(entry.file).with_suffix(".json")):
            try:
                p.unlink(missing_ok=True)
            except OSError:
                pass

    def load(self) -> None:
        """Load persisted entries (newest first)."""
        self.entries.clear()
        if self.folder is None or not self.folder.is_dir():
            return
        for meta_path in sorted(self.folder.glob("*.json"), reverse=True)[: self.limit]:
            png = meta_path.with_suffix(".png")
            if not png.exists():
                continue
            try:
                meta = json.loads(meta_path.read_text(encoding="utf-8"))
                with Image.open(png) as im:
                    img = im.convert("RGBA")
                self.entries.append(HistoryEntry(
                    TextureSettings.from_dict(meta.get("settings", {})), img,
                    datetime.fromisoformat(meta.get("timestamp")), meta.get("source", ""), str(png)))
            except (OSError, ValueError, TypeError):
                continue

    def clear(self) -> None:
        for e in self.entries:
            self._delete_files(e)
        self.entries.clear()
