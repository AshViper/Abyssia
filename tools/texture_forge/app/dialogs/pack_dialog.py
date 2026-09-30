"""Resource pack export options."""
from __future__ import annotations

from PySide6.QtWidgets import (QCheckBox, QComboBox, QDialog, QDialogButtonBox, QFileDialog, QFormLayout,
                               QHBoxLayout, QLabel, QLineEdit, QPushButton, QVBoxLayout, QWidget)

from core.config import PACK_FORMATS, AppConfig
from export.png import sanitize_name
from export.resource_pack import TEXTURE_FOLDERS


class PackDialog(QDialog):
    def __init__(self, cfg: AppConfig, texture_name: str, variation_count: int, has_emission: bool,
                 parent: QWidget | None = None):
        super().__init__(parent)
        self.setWindowTitle("Export Resource Pack")
        self.setMinimumWidth(500)
        self.cfg = cfg
        f = QFormLayout()
        self.pack_name = QLineEdit(f"{cfg.mod_id}_textures")
        self.mod_id = QLineEdit(cfg.mod_id)
        self.name = QLineEdit(texture_name)
        self.folder = QComboBox()
        self.folder.addItems(TEXTURE_FOLDERS)
        self.folder.setCurrentText(cfg.texture_folder)
        self.version = QComboBox()
        for v, fmt in PACK_FORMATS.items():
            self.version.addItem(f"{v}  (pack_format {fmt})", v)
        self.version.setCurrentIndex(max(0, self.version.findData(cfg.minecraft_version)))
        self.description = QLineEdit(cfg.pack_description)
        out_row = QHBoxLayout()
        self.out_dir = QLineEdit(cfg.output_dir)
        b = QPushButton("…")
        b.setFixedWidth(32)
        b.clicked.connect(self._browse)
        out_row.addWidget(self.out_dir, 1)
        out_row.addWidget(b)
        self.variations = QCheckBox(f"Include variations ({variation_count})")
        self.variations.setEnabled(variation_count > 1)
        self.variations.setChecked(variation_count > 1)
        self.emission = QCheckBox("Write emissive mask (<name>_e.png)")
        self.emission.setEnabled(has_emission)
        self.zip = QCheckBox("Write as .zip")
        f.addRow("Pack name", self.pack_name)
        f.addRow("Mod ID", self.mod_id)
        f.addRow("Texture name", self.name)
        f.addRow("Texture folder", self.folder)
        f.addRow("Minecraft", self.version)
        f.addRow("Description", self.description)
        f.addRow("Output folder", out_row)
        f.addRow("", self.variations)
        f.addRow("", self.emission)
        f.addRow("", self.zip)
        self.preview = QLabel()
        self.preview.setObjectName("Sub")
        f.addRow(self.preview)
        for w in (self.pack_name, self.mod_id, self.name):
            w.textChanged.connect(self._update_preview)
        self.folder.currentIndexChanged.connect(self._update_preview)
        self._update_preview()
        buttons = QDialogButtonBox(QDialogButtonBox.Ok | QDialogButtonBox.Cancel)
        buttons.button(QDialogButtonBox.Ok).setText("Export")
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)
        lay = QVBoxLayout(self)
        lay.addLayout(f)
        lay.addWidget(buttons)

    def _browse(self) -> None:
        d = QFileDialog.getExistingDirectory(self, "Output folder", self.out_dir.text())
        if d:
            self.out_dir.setText(d)

    def _update_preview(self) -> None:
        pack = sanitize_name(self.pack_name.text()) or "pack"
        mod = sanitize_name(self.mod_id.text()) or "modid"
        name = sanitize_name(self.name.text())
        self.preview.setText(f"{pack}/\n├── pack.mcmeta\n└── assets/{mod}/textures/"
                             f"{self.folder.currentText()}/{name}.png")

    def values(self) -> dict:
        return {
            "pack_name": self.pack_name.text().strip() or "texture_forge_pack",
            "mod_id": self.mod_id.text().strip() or self.cfg.mod_id,
            "name": sanitize_name(self.name.text()),
            "folder": self.folder.currentText(),
            "pack_format": PACK_FORMATS.get(self.version.currentData(), 15),
            "description": self.description.text(),
            "out_dir": self.out_dir.text().strip() or self.cfg.output_dir,
            "variations": self.variations.isChecked(),
            "emission": self.emission.isChecked(),
            "as_zip": self.zip.isChecked(),
        }
