"""Small reusable controls: slider rows, collapsible sections, palette strips."""
from __future__ import annotations

from PySide6.QtCore import QRect, QSize, Qt, Signal
from PySide6.QtGui import QColor, QGuiApplication, QPainter, QPen
from PySide6.QtWidgets import (QHBoxLayout, QLabel, QSizePolicy, QSlider, QToolButton, QVBoxLayout,
                               QWidget)

from app import theme

BIPOLAR = {"hue", "saturation", "brightness", "contrast", "temperature", "tint", "hue_shift"}


class SliderRow(QWidget):
    """``Label ───●─── 65%``  (double-click the label to reset)."""
    valueChanged = Signal(str, float)

    def __init__(self, field: str, label: str, value: float = 0.5, default: float | None = None,
                 tooltip: str = "", parent: QWidget | None = None):
        super().__init__(parent)
        self.field = field
        self.default = value if default is None else default
        lay = QHBoxLayout(self)
        lay.setContentsMargins(0, 1, 0, 1)
        lay.setSpacing(8)
        self.label = QLabel(label)
        self.label.setFixedWidth(86)
        self.label.setToolTip((tooltip + "\n" if tooltip else "") + "Double-click to reset")
        self.slider = QSlider(Qt.Horizontal)
        self.slider.setRange(0, 100)
        self.slider.setSingleStep(1)
        self.slider.setPageStep(10)
        self.value_label = QLabel()
        self.value_label.setObjectName("Value")
        self.value_label.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        self.value_label.setFixedWidth(42)
        lay.addWidget(self.label)
        lay.addWidget(self.slider, 1)
        lay.addWidget(self.value_label)
        self.slider.valueChanged.connect(self._changed)
        self.label.mouseDoubleClickEvent = lambda _e: self.setValue(self.default, emit=True)
        self.setValue(value)

    def value(self) -> float:
        return self.slider.value() / 100.0

    def setValue(self, v: float, emit: bool = False) -> None:
        self.slider.blockSignals(not emit)
        self.slider.setValue(int(round(max(0.0, min(1.0, v)) * 100)))
        self.slider.blockSignals(False)
        self._update_label()

    def _update_label(self) -> None:
        v = self.value()
        if self.field == "hue":
            text = f"{(v - 0.5) * 360:+.0f}°"
        elif self.field in BIPOLAR:
            text = f"{(v - 0.5) * 200:+.0f}"
        else:
            text = f"{v * 100:.0f}%"
        self.value_label.setText(text)

    def _changed(self, _v: int) -> None:
        self._update_label()
        self.valueChanged.emit(self.field, self.value())


class Section(QWidget):
    """Collapsible section with a caps header."""

    def __init__(self, title: str, parent: QWidget | None = None, expanded: bool = True):
        super().__init__(parent)
        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 4)
        lay.setSpacing(2)
        self.header = QToolButton()
        self.header.setObjectName("SectionHeader")
        self.header.setText(title.upper())
        self.header.setCheckable(True)
        self.header.setChecked(expanded)
        self.header.setToolButtonStyle(Qt.ToolButtonTextBesideIcon)
        self.header.setArrowType(Qt.DownArrow if expanded else Qt.RightArrow)
        self.header.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
        self.body = QWidget()
        self.body_layout = QVBoxLayout(self.body)
        self.body_layout.setContentsMargins(4, 0, 0, 0)
        self.body_layout.setSpacing(3)
        self.body.setVisible(expanded)
        lay.addWidget(self.header)
        lay.addWidget(self.body)
        self.header.toggled.connect(self._toggle)

    def _toggle(self, on: bool) -> None:
        self.header.setArrowType(Qt.DownArrow if on else Qt.RightArrow)
        self.body.setVisible(on)

    def add(self, w: QWidget) -> QWidget:
        self.body_layout.addWidget(w)
        return w

    def add_layout(self, layout) -> None:
        self.body_layout.addLayout(layout)


def panel_title(text: str) -> QLabel:
    lab = QLabel(text.upper())
    lab.setObjectName("PanelTitle")
    return lab


class PaletteStrip(QWidget):
    """Rows of colour swatches.  Hover shows hex; click copies it."""
    colorClicked = Signal(str)

    def __init__(self, parent: QWidget | None = None, swatch: int = 18):
        super().__init__(parent)
        self.rows: list[tuple[str, list[str]]] = []
        self.swatch = swatch
        self.label_w = 64
        self.setMouseTracking(True)
        self.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)

    def setRows(self, rows: list[tuple[str, list[str]]]) -> None:
        self.rows = rows
        self.setFixedHeight(max(1, len(rows)) * (self.swatch + 4) + 2)
        self.update()

    def sizeHint(self) -> QSize:
        return QSize(240, max(1, len(self.rows)) * (self.swatch + 4) + 2)

    def _rect(self, r: int, i: int) -> QRect:
        s = self.swatch
        return QRect(self.label_w + i * (s + 2), 1 + r * (s + 4), s, s)

    def _hit(self, pos) -> str | None:
        for r, (_, cols) in enumerate(self.rows):
            for i, c in enumerate(cols):
                if self._rect(r, i).contains(pos):
                    return c
        return None

    def paintEvent(self, _e) -> None:
        p = QPainter(self)
        p.setPen(QColor(theme.SUBTEXT))
        for r, (label, cols) in enumerate(self.rows):
            rr = self._rect(r, 0)
            p.setPen(QColor(theme.SUBTEXT))
            p.drawText(QRect(0, rr.top(), self.label_w - 6, rr.height()), Qt.AlignVCenter | Qt.AlignLeft, label)
            for i, c in enumerate(cols):
                rect = self._rect(r, i)
                p.fillRect(rect, QColor(c))
                p.setPen(QPen(QColor(theme.BORDER), 1))
                p.drawRect(rect.adjusted(0, 0, -1, -1))

    def mouseMoveEvent(self, e) -> None:
        c = self._hit(e.position().toPoint())
        self.setToolTip(c or "")
        self.setCursor(Qt.PointingHandCursor if c else Qt.ArrowCursor)

    def mousePressEvent(self, e) -> None:
        c = self._hit(e.position().toPoint())
        if c:
            QGuiApplication.clipboard().setText(c)
            self.colorClicked.emit(c)


class ColorButton(QToolButton):
    """Shows a colour swatch (or 'Auto' when empty)."""

    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self._hex = ""
        self.setFixedHeight(24)
        self._refresh()

    def hex(self) -> str:
        return self._hex

    def setHex(self, value: str) -> None:
        self._hex = value or ""
        self._refresh()

    def _refresh(self) -> None:
        if self._hex:
            parts = [p.strip() for p in self._hex.split(",") if p.strip()]
            swatch = parts[len(parts) // 2] if parts else self._hex   # a ramp list shows its middle colour
            fg = "#0B1220" if QColor(swatch).lightness() > 140 else theme.TEXT
            self.setText(self._hex if len(parts) <= 1 else f"{parts[0]} … {parts[-1]}")
            self.setStyleSheet(f"QToolButton {{ background: {swatch}; color: {fg}; }}")
        else:
            self.setText("Theme")
            self.setStyleSheet("")
