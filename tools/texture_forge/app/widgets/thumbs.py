"""Thumbnail lists: variation grid, history and layer views."""
from __future__ import annotations

from PIL import Image
from PySide6.QtCore import QSize, Qt, Signal
from PySide6.QtGui import QColor, QIcon, QPainter, QPixmap
from PySide6.QtWidgets import QListView, QListWidget, QListWidgetItem, QWidget

from app import theme

from .pixel_view import pil_to_qimage


def pixel_icon(img: Image.Image, size: int, checker: bool = True) -> QIcon:
    """Nearest-neighbour icon (never smoothed)."""
    q = pil_to_qimage(img)
    scale = max(1, size // max(q.width(), q.height()))
    pm = QPixmap(q.width() * scale, q.height() * scale)
    pm.fill(QColor(theme.SUBPANEL))
    p = QPainter(pm)
    if checker:
        s = max(4, scale * 2)
        for y in range(0, pm.height(), s):
            for x in range(0, pm.width(), s):
                if (x // s + y // s) % 2:
                    p.fillRect(x, y, s, s, QColor("#2A2F37"))
    p.setRenderHint(QPainter.SmoothPixmapTransform, False)
    p.drawImage(pm.rect(), q)
    p.end()
    return QIcon(pm)


class ThumbGrid(QListWidget):
    """Grid of textures with captions; emits the row index."""
    picked = Signal(int)
    activatedIndex = Signal(int)

    def __init__(self, icon_size: int = 96, parent: QWidget | None = None):
        super().__init__(parent)
        self.setViewMode(QListView.IconMode)
        self.setResizeMode(QListView.Adjust)
        self.setMovement(QListView.Static)
        self.setIconSize(QSize(icon_size, icon_size))
        self.setGridSize(QSize(icon_size + 20, icon_size + 30))
        self.setSpacing(4)
        self.setUniformItemSizes(True)
        self.setWordWrap(True)
        self.currentRowChanged.connect(lambda r: r >= 0 and self.picked.emit(r))
        self.itemDoubleClicked.connect(lambda it: self.activatedIndex.emit(self.row(it)))

    def setImages(self, items: list[tuple[str, Image.Image, str]]) -> None:
        """``items`` = [(caption, image, tooltip)]."""
        self.blockSignals(True)
        self.clear()
        size = self.iconSize().width()
        for caption, img, tip in items:
            it = QListWidgetItem(pixel_icon(img, size), caption)
            it.setToolTip(tip)
            it.setTextAlignment(Qt.AlignHCenter)
            self.addItem(it)
        self.blockSignals(False)


class HistoryList(QListWidget):
    picked = Signal(int)

    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self.setIconSize(QSize(32, 32))
        self.setAlternatingRowColors(False)
        self.itemClicked.connect(lambda it: self.picked.emit(self.row(it)))
        self.itemActivated.connect(lambda it: self.picked.emit(self.row(it)))

    def setEntries(self, entries: list[tuple[str, Image.Image, str]]) -> None:
        self.blockSignals(True)
        self.clear()
        for label, img, tip in entries:
            it = QListWidgetItem(pixel_icon(img, 32, checker=False), label)
            it.setToolTip(tip)
            self.addItem(it)
        self.blockSignals(False)
