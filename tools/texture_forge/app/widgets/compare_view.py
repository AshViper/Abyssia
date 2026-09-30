"""Side-by-side comparison of the reference and the generated texture."""
from __future__ import annotations

from PIL import Image
from PySide6.QtWidgets import QComboBox, QHBoxLayout, QLabel, QVBoxLayout, QWidget

from core import compare

from .pixel_view import PixelView

MODES = {
    "side": "Source ↔ Generated",
    "difference": "Difference",
    "palette": "Palette",
    "brightness": "Brightness",
}


class CompareView(QWidget):
    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self.source: Image.Image | None = None
        self.generated: Image.Image | None = None
        lay = QVBoxLayout(self)
        lay.setContentsMargins(6, 6, 6, 6)
        bar = QHBoxLayout()
        self.mode = QComboBox()
        for k, v in MODES.items():
            self.mode.addItem(v, k)
        self.info = QLabel()
        self.info.setObjectName("Sub")
        bar.addWidget(QLabel("View"))
        bar.addWidget(self.mode)
        bar.addStretch(1)
        bar.addWidget(self.info)
        lay.addLayout(bar)
        views = QHBoxLayout()
        self.left_title = QLabel("SOURCE")
        self.left_title.setObjectName("PanelTitle")
        self.right_title = QLabel("GENERATED")
        self.right_title.setObjectName("PanelTitle")
        self.left = PixelView(placeholder="No source loaded")
        self.right = PixelView(placeholder="Nothing generated yet")
        lcol = QVBoxLayout()
        lcol.addWidget(self.left_title)
        lcol.addWidget(self.left, 1)
        rcol = QVBoxLayout()
        rcol.addWidget(self.right_title)
        rcol.addWidget(self.right, 1)
        views.addLayout(lcol, 1)
        views.addLayout(rcol, 1)
        lay.addLayout(views, 1)
        self.mode.currentIndexChanged.connect(self.refresh)
        self.left.zoomChanged.connect(lambda z: self._sync(self.right, z))
        self.right.zoomChanged.connect(lambda z: self._sync(self.left, z))

    def _sync(self, other: PixelView, z: int) -> None:
        if other.zoom() != z:
            other.blockSignals(True)
            other.setZoom(z)
            other.blockSignals(False)

    def setImages(self, source: Image.Image | None, generated: Image.Image | None) -> None:
        self.source = source
        self.generated = generated
        self.refresh()

    def setGrid(self, on: bool) -> None:
        self.left.setGrid(on)
        self.right.setGrid(on)

    def setBackground(self, mode: str) -> None:
        self.left.setBackground(mode)
        self.right.setBackground(mode)

    def refresh(self) -> None:
        mode = self.mode.currentData()
        src, gen = self.source, self.generated
        self.info.setText("")
        if mode == "side":
            self.left_title.setText("SOURCE")
            self.right_title.setText("GENERATED")
            self.left.setImage(src)
            self.right.setImage(gen)
            if src is not None and gen is not None:
                sim = compare.similarity(src, gen)
                self.info.setText(f"identical pixels vs. source: {sim * 100:.1f}%")
        elif mode == "difference":
            self.left_title.setText("GENERATED")
            self.right_title.setText("DIFFERENCE (bright = different)")
            self.left.setImage(gen)
            self.right.setImage(compare.difference_map(src, gen) if src is not None and gen is not None else None)
            if src is not None and gen is not None:
                self.info.setText(f"identical pixels: {compare.similarity(src, gen) * 100:.1f}%")
        elif mode == "palette":
            self.left_title.setText("SOURCE PALETTE")
            self.right_title.setText("GENERATED PALETTE")
            self.left.setImage(compare.palette_image(src) if src is not None else None)
            self.right.setImage(compare.palette_image(gen) if gen is not None else None)
        else:
            self.left_title.setText("SOURCE BRIGHTNESS")
            self.right_title.setText("GENERATED BRIGHTNESS")
            self.left.setImage(compare.brightness_map(src) if src is not None else None)
            self.right.setImage(compare.brightness_map(gen) if gen is not None else None)
