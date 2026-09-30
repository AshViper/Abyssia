"""Batch generation: a folder of references or a batch preset → many textures."""
from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import QUrl
from PySide6.QtGui import QDesktopServices
from PySide6.QtWidgets import (QCheckBox, QComboBox, QDialog, QFileDialog, QFormLayout, QHBoxLayout,
                               QLabel, QLineEdit, QListWidget, QProgressBar, QPushButton, QSpinBox,
                               QTabWidget, QVBoxLayout, QWidget)

from app.worker import TaskRunner
from core.batch import jobs_from_folder, jobs_from_preset, list_images, run_batch
from core.config import AppConfig
from core.generator import TextureGenerator
from core.presets import list_batch_presets, load_batch_preset, preset_label
from core.settings import TextureSettings


class BatchDialog(QDialog):
    def __init__(self, cfg: AppConfig, runner: TaskRunner, generator: TextureGenerator,
                 base: TextureSettings, parent: QWidget | None = None, preset: str | None = None):
        super().__init__(parent)
        self.setWindowTitle("Batch Generation")
        self.resize(640, 560)
        self.cfg = cfg
        self.runner = runner
        self.generator = generator
        self.base = base
        self._running = False

        lay = QVBoxLayout(self)
        self.tabs = QTabWidget()
        self.tabs.addTab(self._folder_tab(), "Input Folder")
        self.tabs.addTab(self._preset_tab(), "Batch Preset")
        lay.addWidget(self.tabs)

        common = QFormLayout()
        out_row = QHBoxLayout()
        self.output = QLineEdit(str(Path(cfg.output_dir) / "batch"))
        browse = QPushButton("…")
        browse.setFixedWidth(32)
        browse.clicked.connect(lambda: self._browse(self.output))
        out_row.addWidget(self.output, 1)
        out_row.addWidget(browse)
        self.variations = QSpinBox()
        self.variations.setRange(1, 64)
        self.variations.setValue(1)
        self.variations.setToolTip("Variants per texture: name.png, name_01.png, name_02.png …")
        self.reseed = QCheckBox("New seeds (based on the current seed)")
        self.pack = QCheckBox("Also export a resource pack")
        self.zip = QCheckBox("as .zip")
        pack_row = QHBoxLayout()
        pack_row.addWidget(self.pack)
        pack_row.addWidget(self.zip)
        pack_row.addStretch(1)
        common.addRow("Output folder", out_row)
        common.addRow("Variations", self.variations)
        common.addRow("", self.reseed)
        common.addRow("", pack_row)
        lay.addLayout(common)

        self.progress = QProgressBar()
        self.progress.setRange(0, 1000)
        self.progress.setFormat("%p%")
        self.status = QLabel("")
        self.status.setObjectName("Sub")
        self.log = QListWidget()
        lay.addWidget(self.progress)
        lay.addWidget(self.status)
        lay.addWidget(self.log, 1)

        btns = QHBoxLayout()
        self.open_btn = QPushButton("Open Output Folder")
        self.run_btn = QPushButton("Run Batch")
        self.run_btn.setObjectName("Primary")
        self.close_btn = QPushButton("Close")
        btns.addWidget(self.open_btn)
        btns.addStretch(1)
        btns.addWidget(self.close_btn)
        btns.addWidget(self.run_btn)
        lay.addLayout(btns)
        self.run_btn.clicked.connect(self.run)
        self.close_btn.clicked.connect(self._close)
        self.open_btn.clicked.connect(self._open_output)
        if preset:
            self.tabs.setCurrentIndex(1)
            i = self.preset_combo.findData(preset)
            if i >= 0:
                self.preset_combo.setCurrentIndex(i)

    # ---------------------------------------------------------------- tabs
    def _folder_tab(self) -> QWidget:
        w = QWidget()
        f = QFormLayout(w)
        row = QHBoxLayout()
        self.input = QLineEdit()
        self.input.setPlaceholderText("Folder with reference PNG / JPG / WEBP textures")
        b = QPushButton("…")
        b.setFixedWidth(32)
        b.clicked.connect(lambda: self._browse(self.input, self._scan))
        row.addWidget(self.input, 1)
        row.addWidget(b)
        self.auto_cat = QCheckBox("Pick category from file name (ore, kelp, crystal …)")
        self.auto_cat.setChecked(True)
        self.prefix = QLineEdit()
        self.prefix.setPlaceholderText("e.g. abyssal_")
        self.found = QLabel("")
        self.found.setObjectName("Sub")
        f.addRow("Input folder", row)
        f.addRow("", self.auto_cat)
        f.addRow("Name prefix", self.prefix)
        f.addRow("", self.found)
        note = QLabel("Each reference is analysed; the current settings (palette, sliders) are the base.")
        note.setObjectName("Sub")
        note.setWordWrap(True)
        f.addRow(note)
        self.input.editingFinished.connect(self._scan)
        return w

    def _preset_tab(self) -> QWidget:
        w = QWidget()
        f = QFormLayout(w)
        self.preset_combo = QComboBox()
        for p in list_batch_presets():
            self.preset_combo.addItem(preset_label(p), str(p))
        self.preset_desc = QLabel("")
        self.preset_desc.setObjectName("Sub")
        self.preset_desc.setWordWrap(True)
        self.preset_items = QListWidget()
        f.addRow("Preset", self.preset_combo)
        f.addRow(self.preset_desc)
        f.addRow(self.preset_items)
        self.preset_combo.currentIndexChanged.connect(self._show_preset)
        self._show_preset()
        return w

    # ------------------------------------------------------------- helpers
    def _browse(self, edit: QLineEdit, then=None) -> None:
        d = QFileDialog.getExistingDirectory(self, "Choose folder", edit.text() or self.cfg.output_dir)
        if d:
            edit.setText(d)
            if then:
                then()

    def _scan(self) -> None:
        p = Path(self.input.text())
        if p.is_dir():
            n = len(list_images(p))
            self.found.setText(f"{n} image(s) found")
        else:
            self.found.setText("folder not found")

    def _show_preset(self) -> None:
        self.preset_items.clear()
        path = self.preset_combo.currentData()
        if not path:
            self.preset_desc.setText("No batch presets in presets/batch/")
            return
        bp = load_batch_preset(path)
        self.preset_desc.setText(bp.description)
        for t in bp.textures:
            self.preset_items.addItem(f"{t.get('name', t.get('preset', '?'))}   ·   {t.get('preset', t.get('category', ''))}")

    def _jobs(self):
        base = self.base.copy()
        if self.tabs.currentIndex() == 0:
            folder = Path(self.input.text())
            if not folder.is_dir():
                raise ValueError("Choose an input folder first.")
            jobs = jobs_from_folder(folder, base, self.auto_cat.isChecked(), self.prefix.text().strip())
            if not jobs:
                raise ValueError("No images in the input folder.")
            return jobs
        path = self.preset_combo.currentData()
        if not path:
            raise ValueError("No batch preset selected.")
        return jobs_from_preset(load_batch_preset(path), None if not self.reseed.isChecked() else base,
                                self.reseed.isChecked())

    # ------------------------------------------------------------------ run
    def run(self) -> None:
        if self._running:
            self.runner.cancel("batch")
            self.status.setText("Cancelling…")
            return
        try:
            jobs = self._jobs()
        except ValueError as e:
            self.status.setText(str(e))
            return
        out = Path(self.output.text())
        variations = self.variations.value()
        pack = None
        if self.pack.isChecked():
            name = self.preset_combo.currentText() if self.tabs.currentIndex() == 1 else "batch_pack"
            pack = {"mod_id": self.cfg.mod_id, "pack_name": name, "description": self.cfg.pack_description,
                    "pack_format": self.cfg.pack_format, "folder": self.cfg.texture_folder,
                    "as_zip": self.zip.isChecked()}
        gen = self.generator
        self.log.clear()
        self._running = True
        self.run_btn.setText("Cancel")
        self.status.setText(f"Generating {len(jobs)} texture(s) × {variations}…")

        def work(progress, cancelled):
            return run_batch(jobs, out, variations, gen, progress, cancelled, pack)

        self.runner.submit("batch", work, self._done, self._failed, self._progress)

    def _progress(self, value: float, msg: str) -> None:
        self.progress.setValue(int(value * 1000))
        self.status.setText(msg)

    def _done(self, outputs) -> None:
        self._running = False
        self.run_btn.setText("Run Batch")
        self.progress.setValue(1000)
        ok = 0
        for o in outputs:
            if o.error:
                self.log.addItem(f"✗ {o.job.settings.name}: {o.error}")
            else:
                ok += 1
                for f in o.files:
                    self.log.addItem(f"✓ {f}")
        self.status.setText(f"Done: {ok}/{len(outputs)} texture(s) → {self.output.text()}")
        self.finished_outputs = outputs

    def _failed(self, msg: str) -> None:
        self._running = False
        self.run_btn.setText("Run Batch")
        self.status.setText(msg.split("\n")[0])

    def _open_output(self) -> None:
        p = Path(self.output.text())
        p.mkdir(parents=True, exist_ok=True)
        QDesktopServices.openUrl(QUrl.fromLocalFile(str(p)))

    def _close(self) -> None:
        if self._running:
            self.runner.cancel("batch")
        self.reject()

    def closeEvent(self, e) -> None:
        if self._running:
            self.runner.cancel("batch")
        super().closeEvent(e)
