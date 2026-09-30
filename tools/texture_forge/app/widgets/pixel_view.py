"""Nearest-neighbour texture viewer with pixel grid, tiling and drag & drop."""
from __future__ import annotations

from pathlib import Path

from PIL import Image
from PySide6.QtCore import QPoint, QRect, QSize, Qt, Signal
from PySide6.QtGui import QColor, QImage, QPainter, QPen
from PySide6.QtWidgets import QSizePolicy, QWidget

from app import theme

IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".webp"}
BACKGROUNDS = {"checker": "Checkerboard", "dark": "Dark", "light": "Light"}


def pil_to_qimage(img: Image.Image) -> QImage:
    rgba = img.convert("RGBA")
    data = rgba.tobytes("raw", "RGBA")
    q = QImage(data, rgba.width, rgba.height, rgba.width * 4, QImage.Format_RGBA8888)
    return q.copy()  # own the buffer


class PixelView(QWidget):
    fileDropped = Signal(str)
    pixelHovered = Signal(int, int, object)   # x, y, (r, g, b, a) or None
    zoomChanged = Signal(int)

    def __init__(self, parent: QWidget | None = None, accept_drops: bool = False, placeholder: str = ""):
        super().__init__(parent)
        self.setMinimumSize(120, 120)
        self.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        self.setMouseTracking(True)
        self.setAcceptDrops(accept_drops)
        self._pil: Image.Image | None = None
        self._img: QImage | None = None
        self._zoom = 0          # 0 = fit
        self._grid = True
        self._tile = False
        self._bg = "checker"
        self._pan = QPoint(0, 0)
        self._drag_from: QPoint | None = None
        self._drop_hover = False
        self.placeholder = placeholder

    # ------------------------------------------------------------- API
    def setImage(self, img: Image.Image | None) -> None:
        size_changed = (img is None) != (self._pil is None) or (
            img is not None and self._pil is not None and img.size != self._pil.size)
        self._pil = img
        self._img = pil_to_qimage(img) if img is not None else None
        if size_changed:
            self._pan = QPoint(0, 0)
        self.update()

    def image(self) -> Image.Image | None:
        return self._pil

    def setGrid(self, on: bool) -> None:
        self._grid = on
        self.update()

    def setTile(self, on: bool) -> None:
        self._tile = on
        self._pan = QPoint(0, 0)
        self.update()

    def setBackground(self, mode: str) -> None:
        self._bg = mode if mode in BACKGROUNDS else "checker"
        self.update()

    def zoom(self) -> int:
        return self._effective_zoom()

    def setZoom(self, z: int) -> None:
        self._zoom = max(0, min(64, int(z)))
        self.zoomChanged.emit(self._effective_zoom())
        self.update()

    def zoomIn(self) -> None:
        z = self._effective_zoom()
        self.setZoom(z + (1 if z < 8 else max(2, z // 4)))

    def zoomOut(self) -> None:
        z = self._effective_zoom()
        self.setZoom(max(1, z - (1 if z <= 8 else max(2, z // 4))))

    def fit(self) -> None:
        self._pan = QPoint(0, 0)
        self.setZoom(0)

    def sizeHint(self) -> QSize:
        return QSize(320, 320)

    # --------------------------------------------------------- geometry
    def _content_size(self) -> tuple[int, int]:
        if self._img is None:
            return 0, 0
        n = 3 if self._tile else 1
        return self._img.width() * n, self._img.height() * n

    def _effective_zoom(self) -> int:
        cw, ch = self._content_size()
        if cw == 0:
            return 1
        if self._zoom > 0:
            return self._zoom
        margin = 16
        return max(1, min((self.width() - margin) // cw, (self.height() - margin) // ch))

    def _origin(self) -> QPoint:
        z = self._effective_zoom()
        cw, ch = self._content_size()
        return QPoint((self.width() - cw * z) // 2, (self.height() - ch * z) // 2) + self._pan

    # ------------------------------------------------------------ paint
    def paintEvent(self, _event) -> None:
        p = QPainter(self)
        p.fillRect(self.rect(), QColor(theme.SUBPANEL))
        if self._img is None:
            p.setPen(QColor(theme.SUBTEXT))
            p.drawText(self.rect(), Qt.AlignCenter | Qt.TextWordWrap, self.placeholder)
            self._paint_drop_hint(p)
            return
        p.setRenderHint(QPainter.SmoothPixmapTransform, False)
        p.setRenderHint(QPainter.Antialiasing, False)
        z = self._effective_zoom()
        o = self._origin()
        cw, ch = self._content_size()
        area = QRect(o.x(), o.y(), cw * z, ch * z)
        self._paint_background(p, area)
        w, h = self._img.width(), self._img.height()
        n = 3 if self._tile else 1
        for ty in range(n):
            for tx in range(n):
                p.drawImage(QRect(o.x() + tx * w * z, o.y() + ty * h * z, w * z, h * z), self._img)
        if self._tile:
            p.setPen(QPen(QColor(theme.ACCENT), 1, Qt.DashLine))
            p.drawRect(QRect(o.x() + w * z, o.y() + h * z, w * z - 1, h * z - 1))
        if self._grid and z >= 5 and max(w, h) <= 128:
            self._paint_grid(p, area, z)
        self._paint_drop_hint(p)

    def _paint_background(self, p: QPainter, area: QRect) -> None:
        if self._bg == "dark":
            p.fillRect(area, QColor(theme.BACKGROUND))
        elif self._bg == "light":
            p.fillRect(area, QColor("#D8DCE2"))
        else:
            c1, c2 = QColor("#2A2F37"), QColor("#363C46")
            s = 8
            p.fillRect(area, c1)
            y = area.top()
            row = 0
            while y < area.bottom():
                x = area.left() + (s if row % 2 else 0)
                while x < area.right():
                    p.fillRect(QRect(x, y, s, s).intersected(area), c2)
                    x += 2 * s
                y += s
                row += 1

    def _paint_grid(self, p: QPainter, area: QRect, z: int) -> None:
        minor = QColor(0, 0, 0, 70) if self._bg != "light" else QColor(0, 0, 0, 50)
        major = QColor(theme.ACCENT)
        major.setAlpha(110)
        w = self._img.width()
        h = self._img.height()
        cols = area.width() // z
        rows = area.height() // z
        p.setPen(QPen(minor, 1))
        for i in range(cols + 1):
            if i % w:
                x = area.left() + i * z
                p.drawLine(x, area.top(), x, area.bottom())
        for j in range(rows + 1):
            if j % h:
                y = area.top() + j * z
                p.drawLine(area.left(), y, area.right(), y)
        if self._tile:
            p.setPen(QPen(major, 1))
            for i in range(0, cols + 1, w):
                x = area.left() + i * z
                p.drawLine(x, area.top(), x, area.bottom())
            for j in range(0, rows + 1, h):
                y = area.top() + j * z
                p.drawLine(area.left(), y, area.right(), y)

    def _paint_drop_hint(self, p: QPainter) -> None:
        if self._drop_hover:
            pen = QPen(QColor(theme.ACCENT), 2, Qt.DashLine)
            p.setPen(pen)
            p.drawRect(self.rect().adjusted(2, 2, -3, -3))

    # ------------------------------------------------------------ input
    def wheelEvent(self, e) -> None:
        if self._img is None:
            return
        self.zoomIn() if e.angleDelta().y() > 0 else self.zoomOut()

    def mousePressEvent(self, e) -> None:
        if e.button() in (Qt.LeftButton, Qt.MiddleButton):
            self._drag_from = e.position().toPoint()

    def mouseReleaseEvent(self, _e) -> None:
        self._drag_from = None

    def mouseDoubleClickEvent(self, _e) -> None:
        self.fit()

    def mouseMoveEvent(self, e) -> None:
        pos = e.position().toPoint()
        if self._drag_from is not None:
            self._pan += pos - self._drag_from
            self._drag_from = pos
            self.update()
        if self._img is None:
            return
        z = self._effective_zoom()
        o = self._origin()
        cx = (pos.x() - o.x()) // z
        cy = (pos.y() - o.y()) // z
        cw, ch = self._content_size()
        if 0 <= cx < cw and 0 <= cy < ch:
            x, y = cx % self._img.width(), cy % self._img.height()
            self.pixelHovered.emit(x, y, self._pil.getpixel((x, y)))
        else:
            self.pixelHovered.emit(-1, -1, None)

    def leaveEvent(self, _e) -> None:
        self.pixelHovered.emit(-1, -1, None)

    # ------------------------------------------------------ drag & drop
    @staticmethod
    def _dropped_file(e) -> str | None:
        md = e.mimeData()
        if not md.hasUrls():
            return None
        for url in md.urls():
            path = url.toLocalFile()
            if path and Path(path).suffix.lower() in IMAGE_EXTS:
                return path
        return None

    def dragEnterEvent(self, e) -> None:
        if self._dropped_file(e):
            self._drop_hover = True
            self.update()
            e.acceptProposedAction()

    def dragLeaveEvent(self, _e) -> None:
        self._drop_hover = False
        self.update()

    def dropEvent(self, e) -> None:
        self._drop_hover = False
        self.update()
        path = self._dropped_file(e)
        if path:
            e.acceptProposedAction()
            self.fileDropped.emit(path)
