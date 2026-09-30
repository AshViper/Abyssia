"""Texture Studio GUI (PySide6).  Start it with ``python tools/texture_studio/studio.py`` or run.bat."""
from __future__ import annotations

import json
import os
import sys
import time

import numpy as np
from PIL import Image
from PySide6.QtCore import QPoint, QRect, QSize, Qt, QTimer, Signal
from PySide6.QtGui import (QAction, QColor, QFont, QIcon, QImage, QKeySequence, QPainter, QPen, QPixmap)
from PySide6.QtWidgets import (QAbstractItemView, QApplication, QCheckBox, QColorDialog, QComboBox, QDialog,
                               QDialogButtonBox, QFileDialog, QFormLayout, QFrame, QHBoxLayout, QInputDialog,
                               QLabel, QLineEdit, QListWidget, QListWidgetItem, QMainWindow, QMenu, QMessageBox,
                               QPushButton, QScrollArea, QSlider, QSpinBox, QSplitter, QTabBar, QTabWidget, QToolButton,
                               QVBoxLayout, QWidget, QButtonGroup)

import texture_engine as E

# ---------------------------------------------------------------- theme
BG, PANEL, SUB, INPUT, BORDER = "#0F1114", "#171A1F", "#1E2228", "#262B33", "#323843"
TEXT, DIM, ACCENT, WARN, OK, ERR = "#E6EAF0", "#8F98A5", "#4EA1FF", "#E5B84B", "#4CC38A", "#E05A5A"

STYLE = f"""
* {{ color:{TEXT}; font-size:9.5pt; outline:none; }}
QMainWindow, QDialog {{ background:{BG}; }}
QWidget#panel {{ background:{PANEL}; border:1px solid {BORDER}; border-radius:6px; }}
QLabel {{ background:transparent; }}
QLabel#title {{ color:{DIM}; font-size:8pt; font-weight:700; letter-spacing:1px; }}
QLabel#dim {{ color:{DIM}; }}
QLabel#hint {{ color:{DIM}; font-size:8.5pt; }}
QPushButton, QToolButton {{ background:{INPUT}; border:1px solid {BORDER}; border-radius:4px; padding:4px 10px; }}
QPushButton:hover, QToolButton:hover {{ border-color:{ACCENT}; }}
QPushButton:pressed, QToolButton:pressed, QToolButton:checked {{ background:#2F5F94; border-color:{ACCENT}; }}
QPushButton#primary {{ background:{ACCENT}; color:#06121F; font-weight:700; border-color:{ACCENT}; }}
QPushButton#primary:hover {{ background:#6BB2FF; }}
QPushButton:disabled, QToolButton:disabled {{ color:#5A626E; }}
QLineEdit, QSpinBox, QComboBox {{ background:{INPUT}; border:1px solid {BORDER}; border-radius:4px; padding:3px 6px; }}
QLineEdit:focus, QSpinBox:focus, QComboBox:focus {{ border-color:{ACCENT}; }}
QComboBox QAbstractItemView {{ background:{INPUT}; selection-background-color:#2F5F94; }}
QListWidget {{ background:{SUB}; border:1px solid {BORDER}; border-radius:4px; }}
QListWidget::item {{ padding:3px; border-radius:3px; }}
QListWidget::item:selected {{ background:#2F5F94; }}
QListWidget::item:hover:!selected {{ background:{INPUT}; }}
QScrollArea, QScrollArea > QWidget > QWidget {{ border:none; background:transparent; }}
QFormLayout {{ background:transparent; }}
QScrollBar:vertical {{ background:{SUB}; width:10px; }}
QScrollBar::handle:vertical {{ background:{BORDER}; border-radius:4px; min-height:24px; }}
QScrollBar::add-line, QScrollBar::sub-line {{ height:0; width:0; }}
QSlider::groove:horizontal {{ height:4px; background:{BORDER}; border-radius:2px; }}
QSlider::handle:horizontal {{ width:12px; margin:-5px 0; background:{ACCENT}; border-radius:6px; }}
QSlider::sub-page:horizontal {{ background:#2F5F94; border-radius:2px; }}
QTabWidget::pane {{ border:1px solid {BORDER}; border-radius:4px; }}
QTabBar::tab {{ background:{SUB}; padding:5px 12px; border:1px solid {BORDER}; border-bottom:none; }}
QTabBar::tab:selected {{ background:{INPUT}; color:{ACCENT}; }}
QCheckBox::indicator {{ width:14px; height:14px; }}
QToolTip {{ background:{INPUT}; color:{TEXT}; border:1px solid {BORDER}; }}
QStatusBar {{ background:{PANEL}; color:{DIM}; }}
QMenu {{ background:{INPUT}; border:1px solid {BORDER}; }}
QMenu::item {{ padding:5px 22px; }}
QMenu::item:selected {{ background:#2F5F94; }}
"""

_SRC = E.Sources()
_ICON_CACHE: dict = {}


# ---------------------------------------------------------------- helpers

def to_qimage(arr: np.ndarray) -> QImage:
    arr = np.ascontiguousarray(arr, np.uint8)
    h, w = arr.shape[:2]
    return QImage(arr.data, w, h, 4 * w, QImage.Format_RGBA8888).copy()


def thumb(arr: np.ndarray, size: int = 40) -> QPixmap:
    """Nearest-neighbour thumbnail on a checkerboard."""
    img = Image.fromarray(arr, "RGBA").resize((size, size), Image.NEAREST)
    chk = Image.new("RGBA", (size, size), (52, 56, 64, 255))
    step = max(4, size // 8)
    for y in range(0, size, step):
        for x in range(0, size, step):
            if (x // step + y // step) % 2:
                chk.paste((70, 76, 86, 255), (x, y, x + step, y + step))
    chk.alpha_composite(img)
    return QPixmap.fromImage(to_qimage(np.array(chk)))


def file_thumb(ref: str, size: int = 40) -> QPixmap | None:
    key = (ref, size)
    if key not in _ICON_CACHE:
        arr = _SRC.load(ref)
        _ICON_CACHE[key] = thumb(arr, size) if arr is not None else None
    return _ICON_CACHE[key]


def color_of(hexs: str) -> QColor:
    r, g, b, a = E.parse_color(hexs)
    return QColor.fromRgbF(r, g, b, a)


def hex_of(c: QColor) -> str:
    return E.to_hex((c.redF(), c.greenF(), c.blueF(), c.alphaF()), alpha=True)


def label(text: str, name: str = "") -> QLabel:
    l = QLabel(text)
    if name:
        l.setObjectName(name)
    return l


def panel(title: str) -> tuple[QWidget, QVBoxLayout]:
    w = QWidget()
    w.setObjectName("panel")
    lay = QVBoxLayout(w)
    lay.setContentsMargins(10, 8, 10, 10)
    lay.setSpacing(6)
    lay.addWidget(label(title, "title"))
    return w, lay


# ---------------------------------------------------------------- small widgets

class ColorButton(QWidget):
    changed = Signal(str)

    def __init__(self, value: str, optional: bool = False, allow_alpha: bool = True):
        super().__init__()
        self.value = value or ""
        self.optional = optional
        lay = QHBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        self.check = QCheckBox()
        self.check.setVisible(optional)
        self.check.setChecked(bool(self.value))
        self.check.toggled.connect(self._toggled)
        self.btn = QPushButton()
        self.btn.setMinimumHeight(24)
        self.btn.clicked.connect(self._pick)
        lay.addWidget(self.check)
        lay.addWidget(self.btn, 1)
        self._paint()

    def _paint(self):
        on = bool(self.value)
        self.btn.setEnabled(on or not self.optional)
        if not on:
            self.btn.setText("なし")
            self.btn.setStyleSheet("")
            return
        c = color_of(self.value)
        lum = 0.3 * c.redF() + 0.6 * c.greenF() + 0.1 * c.blueF()
        self.btn.setText(self.value.upper())
        self.btn.setStyleSheet(f"background:{c.name()}; color:{'#000' if lum > 0.55 else '#fff'};"
                               f" border:1px solid {BORDER}; font-family:Consolas;")

    def _toggled(self, on):
        self.value = (self.value or "#888888") if on else ""
        if on and not self.value:
            self.value = "#888888"
        self._paint()
        self.changed.emit(self.value)

    def _pick(self):
        c = QColorDialog.getColor(color_of(self.value or "#888888"), self, "色を選択", QColorDialog.ShowAlphaChannel)
        if c.isValid():
            self.value = hex_of(c)
            self._paint()
            self.changed.emit(self.value)

    def set_value(self, v: str):
        self.value = v or ""
        self.check.blockSignals(True)
        self.check.setChecked(bool(self.value))
        self.check.blockSignals(False)
        self._paint()


class IntRow(QWidget):
    changed = Signal(int)

    def __init__(self, value: int, lo: int, hi: int):
        super().__init__()
        lay = QHBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        self.slider = QSlider(Qt.Horizontal)
        self.slider.setRange(lo, hi)
        self.spin = QSpinBox()
        self.spin.setRange(lo, hi)
        self.spin.setFixedWidth(62)
        self.slider.setValue(int(value))
        self.spin.setValue(int(value))
        self.slider.valueChanged.connect(self._from_slider)
        self.spin.valueChanged.connect(self._from_spin)
        lay.addWidget(self.slider, 1)
        lay.addWidget(self.spin)

    def _from_slider(self, v):
        self.spin.blockSignals(True)
        self.spin.setValue(v)
        self.spin.blockSignals(False)
        self.changed.emit(v)

    def _from_spin(self, v):
        self.slider.blockSignals(True)
        self.slider.setValue(v)
        self.slider.blockSignals(False)
        self.changed.emit(v)


class SourceButton(QWidget):
    changed = Signal(str)

    def __init__(self, value: str, optional: bool = False, exclude: str | None = None):
        super().__init__()
        self.value = value or ""
        self.exclude = exclude
        lay = QHBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        self.btn = QPushButton()
        self.btn.setMinimumHeight(40)
        self.btn.setIconSize(QSize(32, 32))
        self.btn.setStyleSheet("text-align:left;")
        self.btn.clicked.connect(self._pick)
        lay.addWidget(self.btn, 1)
        self.clear = QToolButton()
        self.clear.setText("×")
        self.clear.setVisible(optional)
        self.clear.clicked.connect(lambda: self._set(""))
        lay.addWidget(self.clear)
        self._paint()

    def _paint(self):
        pm = file_thumb(self.value, 32) if self.value else None
        self.btn.setIcon(QIcon(pm) if pm else QIcon())
        self.btn.setText(self.value.replace(":", " : ") if self.value else "（なし）")
        self.clear.setEnabled(bool(self.value))

    def _set(self, v):
        self.value = v
        self._paint()
        self.changed.emit(v)

    def _pick(self):
        dlg = SourcePicker(self, self.value, self.exclude)
        if dlg.exec() == QDialog.Accepted and dlg.result_ref:
            self._set(dlg.result_ref)


class SourcePicker(QDialog):
    """Pick a vanilla or mod texture with thumbnails and search."""

    def __init__(self, parent, current: str = "", exclude: str | None = None):
        super().__init__(parent)
        self.setWindowTitle("元テクスチャを選択")
        self.resize(760, 620)
        self.result_ref = ""
        self.exclude = exclude
        lay = QVBoxLayout(self)
        top = QHBoxLayout()
        self.group = QComboBox()
        for text, scheme, kind in [("バニラ ブロック", "vanilla", "block"), ("バニラ アイテム", "vanilla", "item"),
                                   ("Mod ブロック", "mod", "block"), ("Mod アイテム", "mod", "item")]:
            self.group.addItem(text, (scheme, kind))
        if current.startswith("mod:"):
            self.group.setCurrentIndex(2 if "/item/" not in current and not current.startswith("mod:item/") else 3)
        elif current.startswith("vanilla:item/"):
            self.group.setCurrentIndex(1)
        self.search = QLineEdit()
        self.search.setPlaceholderText("検索 (例: deepslate, ore, brick)")
        self.search.setClearButtonEnabled(True)
        top.addWidget(self.group)
        top.addWidget(self.search, 1)
        lay.addLayout(top)
        self.list = QListWidget()
        self.list.setViewMode(QListWidget.IconMode)
        self.list.setIconSize(QSize(48, 48))
        self.list.setGridSize(QSize(104, 80))
        self.list.setResizeMode(QListWidget.Adjust)
        self.list.setMovement(QListWidget.Static)
        self.list.setWordWrap(True)
        self.list.setUniformItemSizes(True)
        lay.addWidget(self.list, 1)
        self.info = label("", "hint")
        lay.addWidget(self.info)
        if not _SRC.has_vanilla:
            lay.addWidget(label("バニラのクライアントjarが見つかりません（ABYSSIA_MC_JAR で指定できます）", "hint"))
        bb = QDialogButtonBox(QDialogButtonBox.Ok | QDialogButtonBox.Cancel)
        bb.accepted.connect(self._ok)
        bb.rejected.connect(self.reject)
        lay.addWidget(bb)
        self.list.itemDoubleClicked.connect(lambda _: self._ok())
        self.list.currentItemChanged.connect(lambda cur, _: self.info.setText(cur.data(Qt.UserRole) if cur else ""))
        self.group.currentIndexChanged.connect(self._fill)
        self.search.textChanged.connect(self._fill)
        self.timer = QTimer(self)
        self.timer.setInterval(5)
        self.timer.timeout.connect(self._icons)
        self._current = current
        self._fill()

    def _fill(self):
        self.timer.stop()
        scheme, kind = self.group.currentData()
        names = _SRC.vanilla_names(kind) if scheme == "vanilla" else _SRC.mod_names(kind)
        q = self.search.text().strip().lower()
        self.list.clear()
        for n in names:
            ref = f"{scheme}:{n}"
            if ref == self.exclude or (q and q not in n.lower()):
                continue
            it = QListWidgetItem(n.split("/", 1)[1])
            it.setData(Qt.UserRole, ref)
            it.setToolTip(ref)
            self.list.addItem(it)
            if ref == self._current:
                self.list.setCurrentItem(it)
        self._cursor = 0
        self.timer.start()

    def _icons(self):
        end = min(self._cursor + 40, self.list.count())
        for i in range(self._cursor, end):
            it = self.list.item(i)
            pm = file_thumb(it.data(Qt.UserRole), 48)
            if pm:
                it.setIcon(QIcon(pm))
        self._cursor = end
        if end >= self.list.count():
            self.timer.stop()
            cur = self.list.currentItem()
            if cur:
                self.list.scrollToItem(cur)

    def _ok(self):
        it = self.list.currentItem()
        if it:
            self.result_ref = it.data(Qt.UserRole)
            self.accept()


# ---------------------------------------------------------------- preview canvas

class Preview(QWidget):
    stroke = Signal(int, int, int, str)  # x, y, button(1 left / 2 right), phase press|move|release
    hover = Signal(int, int)
    zoomed = Signal(int)

    def __init__(self):
        super().__init__()
        self.setMinimumSize(320, 320)
        self.setMouseTracking(True)
        self.arr: np.ndarray | None = None
        self.qimg: QImage | None = None
        self.fit = True
        self.zoom = 16
        self.grid = True
        self.tile = False
        self.bg = "checker"
        self.tool = "view"
        self._btn = 0
        self._last = None

    def set_array(self, arr):
        self.arr = arr
        self.qimg = to_qimage(arr) if arr is not None else None
        self.update()

    def _scale(self) -> int:
        if self.arr is None:
            return 1
        n = self.arr.shape[0] * (3 if self.tile else 1)
        if self.fit:
            return max(1, min((self.width() - 24) // n, (self.height() - 24) // n))
        return self.zoom

    def _origin(self):
        s = self._scale()
        n = self.arr.shape[0]
        span = n * s * (3 if self.tile else 1)
        ox, oy = (self.width() - span) // 2, (self.height() - span) // 2
        if self.tile:
            ox += n * s
            oy += n * s
        return ox, oy, s, n

    def paintEvent(self, _):
        p = QPainter(self)
        p.fillRect(self.rect(), QColor(BG))
        if self.qimg is None:
            return
        ox, oy, s, n = self._origin()
        reps = range(-1, 2) if self.tile else range(0, 1)
        left, top = ox - (n * s if self.tile else 0), oy - (n * s if self.tile else 0)
        span = n * s * (3 if self.tile else 1)
        area = QRect(left, top, span, span)
        if self.bg == "checker":
            cs = 8
            for y in range(0, span, cs):
                for x in range(0, span, cs):
                    p.fillRect(left + x, top + y, cs, cs, QColor("#3A3F49" if (x // cs + y // cs) % 2 else "#2B3038"))
        else:
            p.fillRect(area, QColor({"dark": "#101418", "light": "#D8DCE2", "mid": "#7A828E"}.get(self.bg, "#101418")))
        for ty in reps:
            for tx in reps:
                p.drawImage(QRect(ox + tx * n * s, oy + ty * n * s, n * s, n * s), self.qimg)
        if self.grid and s >= 8:
            p.setPen(QPen(QColor(255, 255, 255, 34), 1))
            for i in range(0, n * (3 if self.tile else 1) + 1):
                p.drawLine(left + i * s, top, left + i * s, top + span)
                p.drawLine(left, top + i * s, left + span, top + i * s)
        if self.tile:
            p.setPen(QPen(QColor(ACCENT), 1))
            p.drawRect(QRect(ox, oy, n * s, n * s))
        else:
            p.setPen(QPen(QColor(BORDER), 1))
            p.drawRect(area.adjusted(-1, -1, 0, 0))

    def _texel(self, pos: QPoint):
        if self.arr is None:
            return None
        ox, oy, s, n = self._origin()
        x, y = (pos.x() - ox) // s, (pos.y() - oy) // s
        if self.tile:
            return x % n, y % n
        return (x, y) if 0 <= x < n and 0 <= y < n else None

    def mousePressEvent(self, e):
        if self.tool == "view":
            return
        t = self._texel(e.position().toPoint())
        if t:
            self._btn = 1 if e.button() == Qt.LeftButton else 2
            self._last = t
            self.stroke.emit(t[0], t[1], self._btn, "press")

    def mouseMoveEvent(self, e):
        t = self._texel(e.position().toPoint())
        if t:
            self.hover.emit(*t)
        if self._btn and t and t != self._last:
            self._last = t
            self.stroke.emit(t[0], t[1], self._btn, "move")

    def mouseReleaseEvent(self, e):
        if self._btn:
            self.stroke.emit(-1, -1, self._btn, "release")
        self._btn = 0

    def wheelEvent(self, e):
        self.fit = False
        self.zoom = max(2, min(48, self.zoom + (2 if e.angleDelta().y() > 0 else -2)))
        self.zoomed.emit(self.zoom)
        self.update()

    def leaveEvent(self, _):
        self.hover.emit(-1, -1)


# ---------------------------------------------------------------- document model

class Doc:
    def __init__(self, key: str, spec: dict, managed: bool):
        self.key = key
        self.spec = spec
        self.managed = managed
        self.saved = self.snap() if managed else None
        self.undo: list[str] = []
        self.redo: list[str] = []
        self._last = ("", 0.0)

    def snap(self) -> str:
        return json.dumps(self.spec, sort_keys=True)

    @property
    def dirty(self) -> bool:
        return self.managed and self.snap() != self.saved or (not self.managed and self.snap() != self.original)

    original = ""

    def push(self, tag: str = ""):
        now = time.time()
        if tag and tag == self._last[0] and now - self._last[1] < 0.8:
            self._last = (tag, now)
            return
        self.undo.append(self.snap())
        if len(self.undo) > 100:
            self.undo.pop(0)
        self.redo.clear()
        self._last = (tag, now)


# ---------------------------------------------------------------- layer list with drag reorder

class LayerList(QListWidget):
    reordered = Signal()

    def __init__(self):
        super().__init__()
        self.setDragDropMode(QAbstractItemView.InternalMove)
        self.setDefaultDropAction(Qt.MoveAction)
        self.setIconSize(QSize(32, 32))

    def dropEvent(self, e):
        super().dropEvent(e)
        self.reordered.emit()


# ---------------------------------------------------------------- dialogs

class NewDialog(QDialog):
    def __init__(self, parent, existing: set[str]):
        super().__init__(parent)
        self.setWindowTitle("新規テクスチャ")
        self.existing = existing
        form = QFormLayout(self)
        self.kind = QComboBox()
        self.kind.addItem("ブロック (block)", "block")
        self.kind.addItem("アイテム (item)", "item")
        self.name = QLineEdit()
        self.name.setPlaceholderText("例: abyssal_glass  (小文字・数字・_)")
        self.size = QComboBox()
        for s in (16, 32, 64):
            self.size.addItem(f"{s} × {s}", s)
        self.base = SourceButton("vanilla:block/stone", optional=True)
        form.addRow("種類", self.kind)
        form.addRow("名前", self.name)
        form.addRow("サイズ", self.size)
        form.addRow("ベース (バニラ)", self.base)
        form.addRow(label("ベースを空にすると透明な状態から始めます。レイヤーは後から何枚でも重ねられます。", "hint"))
        bb = QDialogButtonBox(QDialogButtonBox.Ok | QDialogButtonBox.Cancel)
        bb.accepted.connect(self._ok)
        bb.rejected.connect(self.reject)
        form.addRow(bb)

    def _ok(self):
        n = self.name.text().strip()
        ok = n and all(c.islower() or c.isdigit() or c == "_" for c in n)
        if not ok:
            QMessageBox.warning(self, "名前", "名前は小文字英数字と _ のみで入力してください。")
            return
        if f"{self.kind.currentData()}/{n}" in self.existing:
            QMessageBox.warning(self, "名前", "同名のテクスチャが既にあります。")
            return
        self.accept()

    def result_key(self) -> str:
        return f"{self.kind.currentData()}/{self.name.text().strip()}"


class BulkDialog(QDialog):
    """Recolour / add layer / remove layer on many textures at once, with a live before/after strip."""

    def __init__(self, win, keys: list[str]):
        super().__init__(win)
        self.win, self.keys = win, keys
        self.setWindowTitle(f"一括編集 ({len(keys)} 枚)")
        self.resize(640, 560)
        lay = QVBoxLayout(self)
        unm = sum(1 for k in keys if not win.is_managed(k))
        lay.addWidget(label(f"対象 {len(keys)} 枚" + (f"（うち未管理 {unm} 枚は、現在のPNGを取り込んで管理下に置きます）" if unm else ""), "hint"))
        self.tabs = QTabWidget()
        lay.addWidget(self.tabs, 1)

        # -- colour tab
        t1 = QWidget()
        f1 = QFormLayout(t1)
        self.hue, self.sat, self.val = IntRow(0, -180, 180), IntRow(0, -100, 100), IntRow(0, -100, 100)
        self.scope = QComboBox()
        self.scope.addItems(["すべてのレイヤー", "画像/手描きレイヤーのみ", "名前に次を含むレイヤー"])
        self.scope_name = QLineEdit()
        self.scope_name.setPlaceholderText("レイヤー名の一部")
        f1.addRow("色相", self.hue)
        f1.addRow("彩度", self.sat)
        f1.addRow("明るさ", self.val)
        f1.addRow("対象", self.scope)
        f1.addRow("", self.scope_name)
        self.strip = QLabel()
        self.strip.setMinimumHeight(110)
        f1.addRow(label("プレビュー (上: 現在 / 下: 変更後)", "hint"))
        f1.addRow(self.strip)
        for w in (self.hue, self.sat, self.val):
            w.changed.connect(self._preview)
        self.scope.currentIndexChanged.connect(self._preview)
        self.scope_name.textChanged.connect(self._preview)
        self.tabs.addTab(t1, "色をずらす")

        # -- add layer tab
        t2 = QWidget()
        f2 = QFormLayout(t2)
        self.preset = QComboBox()
        self.presets = {**E.builtin_presets(), **{f"★{k}": v for k, v in E.user_presets().items()}}
        self.preset.addItems(list(self.presets))
        self.pos = QComboBox()
        self.pos.addItems(["一番上に追加", "一番下に追加"])
        f2.addRow("レイヤープリセット", self.preset)
        f2.addRow("位置", self.pos)
        f2.addRow(label("シードは各テクスチャごとに自動でずらします（同じ模様にならないように）。", "hint"))
        self.tabs.addTab(t2, "レイヤーを追加")

        # -- remove layer tab
        t3 = QWidget()
        f3 = QFormLayout(t3)
        self.rm_name = QLineEdit()
        self.rm_name.setPlaceholderText("この文字を名前に含むレイヤーを削除")
        f3.addRow("レイヤー名", self.rm_name)
        self.tabs.addTab(t3, "レイヤーを削除")

        row = QHBoxLayout()
        b1 = QPushButton("適用（未保存）")
        b2 = QPushButton("適用して保存・反映")
        b2.setObjectName("primary")
        bc = QPushButton("閉じる")
        row.addStretch(1)
        for b in (b1, b2, bc):
            row.addWidget(b)
        lay.addLayout(row)
        b1.clicked.connect(lambda: self._apply(False))
        b2.clicked.connect(lambda: self._apply(True))
        bc.clicked.connect(self.reject)
        self._preview()

    def _match(self, L) -> bool:
        s = self.scope.currentIndex()
        if s == 1:
            return L["type"] in ("image", "paint")
        if s == 2:
            return bool(self.scope_name.text()) and self.scope_name.text() in L.get("name", "")
        return True

    def _shift(self, spec):
        for L in spec["layers"]:
            if self._match(L):
                E.shift_layer_hsv(L, self.hue.spin.value(), self.sat.spin.value(), self.val.spin.value())

    def _preview(self, *_):
        import copy
        keys = self.keys[:8]
        before, after = [], []
        for k in keys:
            d = self.win.peek_spec(k)
            if not d:
                continue
            before.append(E.render(d, _SRC, k))
            d2 = copy.deepcopy(d)
            self._shift(d2)
            after.append(E.render(d2, _SRC, k))
        if not before:
            return
        sz = 64
        row = lambda arrs: np.concatenate([np.array(Image.fromarray(a, "RGBA").resize((sz, sz), Image.NEAREST)) for a in arrs], 1)
        img = np.concatenate([row(before), row(after)], 0)
        self.strip.setPixmap(QPixmap.fromImage(to_qimage(img)))

    def _apply(self, save: bool):
        tab = self.tabs.currentIndex()
        n = 0
        for i, k in enumerate(self.keys):
            doc = self.win.get_doc(k)
            doc.push()
            if tab == 0:
                self._shift(doc.spec)
            elif tab == 1:
                L = E.clone(self.presets[self.preset.currentText()])
                if "seed" in L:
                    L["seed"] = int(L["seed"]) + i * 17
                if self.pos.currentIndex() == 0:
                    doc.spec["layers"].append(L)
                else:
                    doc.spec["layers"].insert(0, L)
            else:
                t = self.rm_name.text()
                if not t:
                    continue
                doc.spec["layers"] = [L for L in doc.spec["layers"] if t not in L.get("name", "")]
            n += 1
            if save:
                self.win.save_doc(doc, confirm=False)
        self.win.after_bulk()
        QMessageBox.information(self, "一括編集", f"{n} 枚に適用しました" + ("（保存・反映済み）" if save else "（未保存：左の●が付いた項目を Ctrl+Shift+S で保存）"))


# ---------------------------------------------------------------- main window

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Abyssia Texture Studio")
        self.resize(1560, 940)
        self.docs: dict[str, Doc] = {}
        self.cur: Doc | None = None
        self.layer_idx = -1
        self.layer_clip: dict | None = None
        self.pen = "#FFFFFF"
        self._items: dict[str, QListWidgetItem] = {}
        self._building = False

        # left: texture browser --------------------------------------------------
        left, ll = panel("テクスチャ一覧")
        self.kind_tabs = QTabBar()
        self.kind_tabs.setDrawBase(False)
        for t in ("すべて", "ブロック", "アイテム"):
            self.kind_tabs.addTab(t)
        self.kind_tabs.currentChanged.connect(lambda _: self.refresh_list())
        ll.addWidget(self.kind_tabs)
        self.search = QLineEdit()
        self.search.setPlaceholderText("名前で検索…")
        self.search.setClearButtonEnabled(True)
        self.search.textChanged.connect(lambda _: self.refresh_list())
        ll.addWidget(self.search)
        self.filter = QComboBox()
        self.filter.addItems(["すべて表示", "管理中のみ", "未管理のみ", "未保存 (●)", "未反映 (PNGと差あり)", "ロック中"])
        self.filter.currentIndexChanged.connect(lambda _: self.refresh_list())
        ll.addWidget(self.filter)
        self.tex_list = QListWidget()
        self.tex_list.setIconSize(QSize(36, 36))
        self.tex_list.setSelectionMode(QAbstractItemView.ExtendedSelection)
        self.tex_list.currentItemChanged.connect(self.on_pick)
        self.tex_list.itemSelectionChanged.connect(self.on_selection)
        ll.addWidget(self.tex_list, 1)
        self.sel_label = label("", "hint")
        ll.addWidget(self.sel_label)
        r1 = QHBoxLayout()
        b_new = QPushButton("＋ 新規")
        b_new.clicked.connect(self.act_new)
        self.b_bulk = QPushButton("一括編集…")
        self.b_bulk.clicked.connect(self.act_bulk)
        r1.addWidget(b_new)
        r1.addWidget(self.b_bulk)
        ll.addLayout(r1)
        r2 = QHBoxLayout()
        b_all = QPushButton("全て保存")
        b_all.clicked.connect(self.act_save_all)
        b_exp = QPushButton("管理中を全て書き出し")
        b_exp.clicked.connect(self.act_export_all)
        r2.addWidget(b_all)
        r2.addWidget(b_exp)
        ll.addLayout(r2)
        b_fb = QPushButton("Forgeパック一括生成…")
        b_fb.setToolTip("Texture Forge のパックプリセット(鉱石/地形/植物/噴出孔)から、まとめて新規テクスチャを作ります")
        b_fb.clicked.connect(self.act_forge_batch)
        ll.addWidget(b_fb)

        # centre: preview -----------------------------------------------------------
        centre, cl = panel("プレビュー")
        bar = QHBoxLayout()
        self.tool_group = QButtonGroup(self)
        self.tool_btns = {}
        for key, text, tip in [("view", "閲覧", "表示のみ"), ("pencil", "鉛筆", "左クリックで描画 / 右クリックで消去 (手描きレイヤーに描きます)"),
                               ("eraser", "消しゴム", "手描きレイヤーのピクセルを消す"), ("pick", "スポイト", "合成結果から色を取る")]:
            b = QToolButton()
            b.setText(text)
            b.setCheckable(True)
            b.setToolTip(tip)
            b.clicked.connect(lambda _, k=key: self.set_tool(k))
            self.tool_group.addButton(b)
            self.tool_btns[key] = b
            bar.addWidget(b)
        self.tool_btns["view"].setChecked(True)
        self.pen_btn = ColorButton(self.pen, allow_alpha=False)
        self.pen_btn.setFixedWidth(110)
        self.pen_btn.changed.connect(lambda v: setattr(self, "pen", v))
        bar.addWidget(self.pen_btn)
        bar.addSpacing(16)
        self.c_grid = QCheckBox("グリッド")
        self.c_grid.setChecked(True)
        self.c_tile = QCheckBox("3×3 タイル")
        self.c_disk = QCheckBox("出力済みPNGを表示")
        self.c_disk.setToolTip("ゲームに反映済みの現在のPNGと見比べます")
        self.bg = QComboBox()
        self.bg.addItems(["チェック", "暗", "灰", "明"])
        self.c_fit = QCheckBox("自動フィット")
        self.c_fit.setChecked(True)
        self.zoom = QSlider(Qt.Horizontal)
        self.zoom.setRange(2, 48)
        self.zoom.setValue(16)
        self.zoom.setFixedWidth(90)
        for w in (self.c_grid, self.c_tile, self.c_disk, self.bg, self.c_fit, self.zoom):
            bar.addWidget(w)
        bar.addStretch(1)
        cl.addLayout(bar)
        self.preview = Preview()
        cl.addWidget(self.preview, 1)
        self.info = label("", "hint")
        cl.addWidget(self.info)
        self.c_grid.toggled.connect(lambda v: (setattr(self.preview, "grid", v), self.preview.update()))
        self.c_tile.toggled.connect(lambda v: (setattr(self.preview, "tile", v), self.preview.update()))
        self.c_disk.toggled.connect(lambda _: self.render_current())
        self.bg.currentIndexChanged.connect(lambda i: (setattr(self.preview, "bg", ["checker", "dark", "mid", "light"][i]), self.preview.update()))
        self.c_fit.toggled.connect(lambda v: (setattr(self.preview, "fit", v), self.preview.update()))
        self.zoom.valueChanged.connect(lambda v: (self.c_fit.setChecked(False), setattr(self.preview, "zoom", v), self.preview.update()))
        self.preview.stroke.connect(self.on_stroke)
        self.preview.hover.connect(self.on_hover)
        self.preview.zoomed.connect(self.zoom.setValue)

        # right: layers + properties --------------------------------------------------
        right, rl = panel("レイヤー（上ほど手前）")
        self.layer_list = LayerList()
        self.layer_list.setMinimumHeight(190)
        self.layer_list.currentRowChanged.connect(self.on_layer_row)
        self.layer_list.itemChanged.connect(self.on_layer_check)
        self.layer_list.reordered.connect(self.on_reorder)
        rl.addWidget(self.layer_list, 2)
        lb = QHBoxLayout()
        self.b_add = QToolButton()
        self.b_add.setText("＋ 追加")
        self.b_add.setPopupMode(QToolButton.InstantPopup)
        self.b_add.setMenu(self.build_add_menu())
        lb.addWidget(self.b_add)
        for text, fn, tip in [("複製", self.act_dup_layer, "レイヤーを複製"), ("削除", self.act_del_layer, "レイヤーを削除"),
                              ("▲", lambda: self.move_layer(1), "手前へ"), ("▼", lambda: self.move_layer(-1), "奥へ"),
                              ("コピー", self.act_copy_layer, "レイヤーをコピー（他のテクスチャに貼り付け可）"),
                              ("貼り付け", self.act_paste_layer, "コピーしたレイヤーを貼り付け"),
                              ("プリセット保存", self.act_save_preset, "このレイヤーを自分のプリセットに保存")]:
            b = QToolButton()
            b.setText(text)
            b.setToolTip(tip)
            b.clicked.connect(fn)
            lb.addWidget(b)
        lb.addStretch(1)
        rl.addLayout(lb)
        rl.addWidget(label("レイヤーの設定", "title"))
        self.form_scroll = QScrollArea()
        self.form_scroll.setWidgetResizable(True)
        self.form_host = QWidget()
        self.form_scroll.setWidget(self.form_host)
        rl.addWidget(self.form_scroll, 5)

        split = QSplitter()
        split.addWidget(left)
        split.addWidget(centre)
        split.addWidget(right)
        split.setSizes([300, 660, 420])
        split.setStretchFactor(1, 1)
        split.setChildrenCollapsible(False)
        wrap = QWidget()
        wl = QVBoxLayout(wrap)
        wl.setContentsMargins(8, 8, 8, 4)
        wl.addWidget(self.build_toolbar())
        wl.addWidget(split, 1)
        self.setCentralWidget(wrap)
        self.statusBar().showMessage("バニラ・Modのテクスチャを選んで編集。Ctrl+S で保存してゲームのPNGに反映します。")
        self.copied: dict | None = None
        self.refresh_list()
        first = self.tex_list.item(0)
        if first:
            self.tex_list.setCurrentItem(first)

    # ------------------------------------------------------------ toolbar / menu
    def build_toolbar(self) -> QWidget:
        w = QWidget()
        h = QHBoxLayout(w)
        h.setContentsMargins(0, 0, 0, 0)
        title = QLabel("Texture Studio")
        title.setStyleSheet(f"font-size:13pt; font-weight:700; color:{ACCENT};")
        h.addWidget(title)
        h.addSpacing(14)
        self.b_save = QPushButton("保存して反映  Ctrl+S")
        self.b_save.setObjectName("primary")
        self.b_save.clicked.connect(self.act_save)
        h.addWidget(self.b_save)
        self.b_undo = QPushButton("元に戻す")
        self.b_undo.clicked.connect(self.act_undo)
        self.b_redo = QPushButton("やり直し")
        self.b_redo.clicked.connect(self.act_redo)
        b_rev = QPushButton("保存済みに戻す")
        b_rev.clicked.connect(self.act_revert)
        b_dupt = QPushButton("別名で複製")
        b_dupt.clicked.connect(self.act_dup_texture)
        b_unm = QPushButton("管理から外す")
        b_unm.setToolTip("スペックを削除します（PNGは残ります）")
        b_unm.clicked.connect(self.act_unmanage)
        for b in (self.b_undo, self.b_redo, b_rev, b_dupt, b_unm):
            h.addWidget(b)
        h.addStretch(1)
        for seq, fn in [("Ctrl+S", self.act_save), ("Ctrl+Shift+S", self.act_save_all), ("Ctrl+Z", self.act_undo),
                        ("Ctrl+Y", self.act_redo), ("Ctrl+N", self.act_new)]:
            a = QAction(self)
            a.setShortcut(QKeySequence(seq))
            a.triggered.connect(fn)
            self.addAction(a)
        return w

    def build_add_menu(self) -> QMenu:
        m = QMenu(self)
        for t, d in E.LAYER_TYPES.items():
            a = m.addAction(d["label"])
            a.triggered.connect(lambda _, t=t: self.add_layer(E.new_layer(t)))
        m.addSeparator()
        fp = m.addMenu("Forgeプリセットから")
        try:
            for name, data in E.forge_presets().items():
                fp.addAction(name).triggered.connect(
                    lambda _, n=name, d=data: self.add_layer(E.new_layer("forge", name=n, forge=dict(d))))
        except Exception as exc:  # texture_forge missing
            fp.setEnabled(False)
            print("forge presets unavailable:", exc)
        sub = m.addMenu("プリセットから")
        for name, L in E.builtin_presets().items():
            sub.addAction(name).triggered.connect(lambda _, L=L: self.add_layer(E.clone(L)))
        users = E.user_presets()
        if users:
            sub2 = m.addMenu("★ 自分のプリセット")
            for name, L in users.items():
                sub2.addAction(name).triggered.connect(lambda _, L=L: self.add_layer(E.clone(L)))
        return m

    # ------------------------------------------------------------ documents
    def is_managed(self, key: str) -> bool:
        d = self.docs.get(key)
        return d.managed if d else os.path.isfile(E.spec_path(key))

    def get_doc(self, key: str) -> Doc:
        d = self.docs.get(key)
        if d:
            return d
        spec = E.load_spec(key)
        managed = spec is not None
        if spec is None:
            spec = E.spec_from_png(key) or E.new_spec(16, [])
        d = Doc(key, spec, managed)
        d.original = d.snap()
        self.docs[key] = d
        return d

    def src_load(self, ref: str):
        return _SRC.load(ref)

    def add_forge_doc(self, key: str, data: dict):
        d = Doc(key, E.new_spec(int(data.get("size", 16)), [E.new_layer("forge", name="Forge", forge=dict(data))]), False)
        d.original = ""
        self.docs[key] = d

    def act_forge_batch(self):
        import gui_forge
        gui_forge.forge_batch(self)

    def peek_spec(self, key: str) -> dict | None:
        d = self.docs.get(key)
        if d:
            return d.spec
        return E.load_spec(key) or E.spec_from_png(key)

    def all_keys(self) -> list[str]:
        keys = set(E.managed_keys()) | set(self.docs)
        for k in E.KINDS:
            keys |= set(_SRC.mod_names(k))
        return sorted(keys)

    def status(self, key: str) -> tuple[str, str]:
        """(second line text, colour)"""
        d = self.docs.get(key)
        parts, colour = [], DIM
        managed = d.managed if d else os.path.isfile(E.spec_path(key))
        parts.append("管理中" if managed else "未管理")
        if managed:
            colour = TEXT
        if d and d.dirty:
            parts.append("未保存")
            colour = WARN
        elif managed:
            spec = d.spec if d else E.load_spec(key)
            if spec and E.is_outdated(key, spec, _SRC):
                parts.append("未反映")
                colour = WARN
        if E.is_locked(key):
            parts.append("ロック")
        return " · ".join(parts), colour

    def refresh_list(self, keep: str | None = None):
        cur = keep or (self.cur.key if self.cur else None)
        kind = [None, "block", "item"][self.kind_tabs.currentIndex()]
        q = self.search.text().strip().lower()
        f = self.filter.currentIndex()
        self.tex_list.blockSignals(True)
        self.tex_list.clear()
        self._items.clear()
        for key in self.all_keys():
            if kind and not key.startswith(kind + "/"):
                continue
            if q and q not in key.lower():
                continue
            st, col = self.status(key)
            if (f == 1 and "管理中" not in st) or (f == 2 and "未管理" not in st) or (f == 3 and "未保存" not in st) \
                    or (f == 4 and "未反映" not in st and "未保存" not in st) or (f == 5 and "ロック" not in st):
                continue
            it = QListWidgetItem()
            self.decorate(it, key, st, col)
            self.tex_list.addItem(it)
            self._items[key] = it
            if key == cur:
                self.tex_list.setCurrentItem(it)
        self.tex_list.blockSignals(False)
        self.on_selection()

    def decorate(self, it: QListWidgetItem, key: str, st: str | None = None, col: str | None = None):
        if st is None:
            st, col = self.status(key)
        d = self.docs.get(key)
        arr = None
        if d and (d.managed or d.dirty):
            arr = E.render(d.spec, _SRC, key)
        else:
            spec = E.load_spec(key)
            arr = E.render(spec, _SRC, key) if spec else _SRC.load(f"mod:{key}")
        if arr is not None:
            it.setIcon(QIcon(thumb(arr, 36)))
        it.setText(f"{key.split('/', 1)[1]}\n{key.split('/', 1)[0]} · {st}")
        it.setData(Qt.UserRole, key)
        it.setForeground(QColor(col))
        it.setSizeHint(QSize(100, 46))

    def update_item(self, key: str):
        it = self._items.get(key)
        if it:
            self.decorate(it, key)

    def on_pick(self, item, _prev):
        if item is None:
            return
        self.open_doc(item.data(Qt.UserRole))

    def on_selection(self):
        n = len(self.tex_list.selectedItems())
        self.sel_label.setText(f"{n} 枚選択中 — 「一括編集…」で色替え・レイヤー追加を一括適用" if n > 1 else "Ctrl/Shift+クリックで複数選択")
        self.b_bulk.setEnabled(n >= 1)

    def open_doc(self, key: str):
        self.cur = self.get_doc(key)
        self.layer_idx = min(len(self.cur.spec["layers"]) - 1, max(self.layer_idx, 0)) if self.cur.spec["layers"] else -1
        self.refresh_layers(select=len(self.cur.spec["layers"]) - 1 if self.cur.spec["layers"] else -1)
        self.render_current()

    # ------------------------------------------------------------ rendering
    def render_current(self):
        d = self.cur
        if not d:
            return
        if self.c_disk.isChecked():
            arr = _SRC.load(f"mod:{d.key}")
            self.preview.set_array(arr if arr is not None else np.zeros((16, 16, 4), np.uint8))
        else:
            self.preview.set_array(E.render(d.spec, _SRC, d.key))
        st, _ = self.status(d.key)
        n = d.spec.get("size", 16)
        self.info.setText(f"{d.key}    {n}×{n}    {st}    → textures/{d.key}.png")
        self.b_undo.setEnabled(bool(d.undo))
        self.b_redo.setEnabled(bool(d.redo))
        self.update_item(d.key)
        self.setWindowTitle(f"Abyssia Texture Studio — {d.key}{' ●' if d.dirty else ''}")

    def changed(self, rebuild_layers: bool = False, rebuild_form: bool = False):
        self.render_current()
        if rebuild_layers:
            self.refresh_layers(select=self.layer_idx)
        else:
            self.refresh_layer_icon(self.layer_idx)
        if rebuild_form:
            self.build_form()

    # ------------------------------------------------------------ layers
    def layers(self) -> list:
        return self.cur.spec["layers"] if self.cur else []

    def refresh_layers(self, select: int = -1):
        self._building = True
        self.layer_list.clear()
        n = self.cur.spec.get("size", 16) if self.cur else 16
        for i in range(len(self.layers()) - 1, -1, -1):
            L = self.layers()[i]
            it = QListWidgetItem(L.get("name", L["type"]))
            it.setFlags(it.flags() | Qt.ItemIsUserCheckable)
            it.setCheckState(Qt.Checked if L.get("visible", True) else Qt.Unchecked)
            it.setData(Qt.UserRole, i)
            it.setToolTip(E.LAYER_TYPES[L["type"]]["label"])
            arr = np.round(E.render_layer(L, n, _SRC, self.cur.key).clip(0, 1) * 255).astype(np.uint8)
            it.setIcon(QIcon(thumb(arr, 32)))
            self.layer_list.addItem(it)
            if i == select:
                self.layer_list.setCurrentItem(it)
        self._building = False
        self.layer_idx = select if 0 <= select < len(self.layers()) else -1
        if self.layer_idx < 0 and self.layer_list.count():
            self.layer_list.setCurrentRow(0)
        self.build_form()

    def refresh_layer_icon(self, idx: int):
        if idx < 0 or idx >= len(self.layers()):
            return
        row = len(self.layers()) - 1 - idx
        it = self.layer_list.item(row)
        if it:
            L = self.layers()[idx]
            arr = np.round(E.render_layer(L, self.cur.spec.get("size", 16), _SRC, self.cur.key).clip(0, 1) * 255).astype(np.uint8)
            self._building = True  # setIcon/setText fire itemChanged
            it.setIcon(QIcon(thumb(arr, 32)))
            it.setText(L.get("name", L["type"]))
            self._building = False

    def on_layer_row(self, row: int):
        if self._building:
            return
        n = len(self.layers())
        self.layer_idx = n - 1 - row if 0 <= row < n else -1
        self.build_form()

    def on_layer_check(self, item):
        if self._building or not self.cur:
            return
        idx = item.data(Qt.UserRole)
        self.cur.push()
        self.layers()[idx]["visible"] = item.checkState() == Qt.Checked
        self.changed()

    def on_reorder(self):
        old = self.layers()
        order = [self.layer_list.item(r).data(Qt.UserRole) for r in range(self.layer_list.count())]
        sel = self.layer_list.currentItem().data(Qt.UserRole) if self.layer_list.currentItem() else -1
        self.cur.push()
        new = [old[i] for i in reversed(order)]
        self.cur.spec["layers"] = new
        self.layer_idx = len(new) - 1 - order.index(sel) if sel in order else -1
        self.changed(rebuild_layers=True)

    def add_layer(self, L: dict):
        if not self.cur:
            return
        self.cur.push()
        i = self.layer_idx + 1 if self.layer_idx >= 0 else len(self.layers())
        self.layers().insert(i, L)
        self.layer_idx = i
        self.changed(rebuild_layers=True)

    def act_dup_layer(self):
        if self.cur and self.layer_idx >= 0:
            L = E.clone(self.layers()[self.layer_idx])
            L["name"] += " コピー"
            self.add_layer(L)

    def act_del_layer(self):
        if self.cur and self.layer_idx >= 0:
            self.cur.push()
            del self.layers()[self.layer_idx]
            self.layer_idx = min(self.layer_idx, len(self.layers()) - 1)
            self.changed(rebuild_layers=True)

    def move_layer(self, d: int):
        i, j = self.layer_idx, self.layer_idx + d
        if self.cur and 0 <= i < len(self.layers()) and 0 <= j < len(self.layers()):
            self.cur.push()
            self.layers()[i], self.layers()[j] = self.layers()[j], self.layers()[i]
            self.layer_idx = j
            self.changed(rebuild_layers=True)

    def act_copy_layer(self):
        if self.cur and self.layer_idx >= 0:
            self.copied = E.clone(self.layers()[self.layer_idx])
            self.statusBar().showMessage("レイヤーをコピーしました。別のテクスチャを開いて「貼り付け」。")

    def act_paste_layer(self):
        if self.copied:
            self.add_layer(E.clone(self.copied))

    def act_save_preset(self):
        if not (self.cur and self.layer_idx >= 0):
            return
        name, ok = QInputDialog.getText(self, "プリセット保存", "プリセット名:")
        if ok and name.strip():
            E.save_user_preset(name.strip(), self.layers()[self.layer_idx])
            self.b_add.setMenu(self.build_add_menu())
            self.statusBar().showMessage(f"プリセット「{name}」を保存しました。")

    # ------------------------------------------------------------ property form
    def build_form(self):
        old = self.form_scroll.takeWidget()
        if old:
            old.deleteLater()
        host = QWidget()
        self.form_host = host
        lay = QVBoxLayout(host)
        lay.setContentsMargins(2, 2, 8, 2)
        form = QFormLayout()
        form.setLabelAlignment(Qt.AlignLeft)
        form.setFieldGrowthPolicy(QFormLayout.ExpandingFieldsGrow)
        form.setHorizontalSpacing(10)
        form.setVerticalSpacing(7)
        lay.addLayout(form)
        lay.addStretch(1)
        self.form_scroll.setWidget(host)
        if not self.cur or self.layer_idx < 0:
            form.addRow(label("レイヤーを選ぶか、「＋ 追加」で作成します。", "hint"))
            return
        L = self.layers()[self.layer_idx]
        tdef = E.LAYER_TYPES[L["type"]]
        dep_keys = {f["show_if"][0] for f in tdef["fields"] if f.get("show_if")}

        def setf(key, val, rebuild=False):
            self.cur.push(f"{id(L)}:{key}")
            L[key] = val
            self.changed(rebuild_form=rebuild or key in dep_keys)

        name = QLineEdit(L.get("name", ""))
        name.textEdited.connect(lambda v: (self.cur.push(f"{id(L)}:name"), L.__setitem__("name", v), self.refresh_layer_icon(self.layer_idx), self.update_item(self.cur.key)))
        form.addRow("名前", name)
        blend = QComboBox()
        labels = {"normal": "通常", "multiply": "乗算", "screen": "スクリーン", "overlay": "オーバーレイ", "add": "加算",
                  "darken": "比較(暗)", "lighten": "比較(明)", "erase": "消去 (透明に抜く)"}
        for b in E.BLENDS:
            blend.addItem(labels[b], b)
        blend.setCurrentIndex(E.BLENDS.index(L.get("blend", "normal")))
        blend.currentIndexChanged.connect(lambda _: setf("blend", blend.currentData()))
        form.addRow("合成", blend)
        op = IntRow(round(L.get("opacity", 1.0) * 100), 0, 100)
        op.changed.connect(lambda v: setf("opacity", v / 100.0))
        form.addRow("不透明度", op)
        clip = QCheckBox("下のレイヤーがある所だけ")
        clip.setChecked(L.get("clip", False))
        clip.toggled.connect(lambda v: setf("clip", v))
        form.addRow("", clip)
        sep = QFrame()
        sep.setFrameShape(QFrame.HLine)
        sep.setStyleSheet(f"color:{BORDER};")
        form.addRow(sep)

        if L["type"] == "paint":
            form.addRow(label("上の「鉛筆」「消しゴム」ツールでプレビュー上に直接描けます。\n右クリックでも消せます。", "hint"))
            b1 = QPushButton("全消去")
            b1.clicked.connect(lambda: (self.cur.push(), L.__setitem__("data", ""), self.changed(rebuild_layers=True)))
            b2 = QPushButton("PNGファイルを読み込む…")
            b2.clicked.connect(self.act_import_paint)
            form.addRow(b1)
            form.addRow(b2)
            return

        if L["type"] == "forge":
            import gui_forge
            gui_forge.build_forge_form(
                self, form, L, setf,
                lambda k, v, rebuild=False: (self.cur.push(f"{id(L)}:f:{k}"), L["forge"].__setitem__(k, v),
                                             self.changed(rebuild_form=rebuild)))
            return
        for f in tdef["fields"]:
            if not E.field_visible(L, f):
                continue
            k, kind = f["key"], f["kind"]
            if kind == "heading":
                form.addRow(label(f["label"], "title"))
                continue
            val = L.get(k, f["default"])
            if kind == "int":
                w = IntRow(val, f["min"], f["max"])
                w.changed.connect(lambda v, k=k: setf(k, v))
            elif kind == "check":
                w = QCheckBox()
                w.setChecked(bool(val))
                w.toggled.connect(lambda v, k=k: setf(k, v))
            elif kind == "combo":
                w = QComboBox()
                for v, t in f["options"]:
                    w.addItem(t, v)
                idx = [v for v, _ in f["options"]].index(val) if val in [v for v, _ in f["options"]] else 0
                w.setCurrentIndex(idx)
                w.currentIndexChanged.connect(lambda _, k=k, w=w: setf(k, w.currentData()))
            elif kind == "color":
                w = ColorButton(val, optional=f.get("optional", False))
                w.changed.connect(lambda v, k=k: setf(k, v))
            elif kind == "source":
                w = SourceButton(val, optional=f.get("optional", False), exclude=f"mod:{self.cur.key}")
                w.changed.connect(lambda v, k=k: setf(k, v))
            elif kind == "seed":
                w = QWidget()
                h = QHBoxLayout(w)
                h.setContentsMargins(0, 0, 0, 0)
                sp = QSpinBox()
                sp.setRange(0, 999999)
                sp.setValue(int(val))
                dice = QPushButton("ランダム")
                sp.valueChanged.connect(lambda v, k=k: setf(k, v))
                dice.clicked.connect(lambda _, sp=sp: sp.setValue(int(np.random.randint(0, 999999))))
                h.addWidget(sp, 1)
                h.addWidget(dice)
            else:
                continue
            if f.get("tip"):
                w.setToolTip(f["tip"])
            form.addRow(f["label"], w)

    # ------------------------------------------------------------ painting
    def set_tool(self, tool: str):
        self.preview.tool = tool
        self.preview.setCursor(Qt.ArrowCursor if tool == "view" else Qt.CrossCursor)

    def paint_layer(self) -> dict | None:
        if not self.cur:
            return None
        if self.layer_idx >= 0 and self.layers()[self.layer_idx]["type"] == "paint":
            return self.layers()[self.layer_idx]
        L = E.new_layer("paint", name="手描き")
        i = self.layer_idx + 1 if self.layer_idx >= 0 else len(self.layers())
        self.layers().insert(i, L)
        self.layer_idx = i
        self.refresh_layers(select=i)
        return L

    def on_stroke(self, x: int, y: int, btn: int, phase: str):
        if not self.cur:
            return
        tool = self.preview.tool
        if tool == "pick" and phase == "press":
            px = E.render(self.cur.spec, _SRC, self.cur.key)[y, x]
            if px[3]:
                self.pen = E.to_hex(px / 255.0)
                self.pen_btn.set_value(self.pen)
            self.tool_btns["pencil"].click()
            return
        if tool not in ("pencil", "eraser"):
            return
        if phase == "release":
            self.refresh_layers(select=self.layer_idx)
            return
        if phase == "press":
            self.cur.push()
            self._stroke_layer = self.paint_layer()
        L = getattr(self, "_stroke_layer", None)
        if L is None:
            return
        n = self.cur.spec.get("size", 16)
        arr = E.decode_paint(L.get("data", ""))
        arr = np.zeros((n, n, 4), np.uint8) if arr is None else E._resize(arr, n).copy()
        erase = tool == "eraser" or btn == 2
        arr[y, x] = (0, 0, 0, 0) if erase else [int(round(c * 255)) for c in E.parse_color(self.pen)]
        L["data"] = E.encode_paint(arr)
        self.render_current()

    def act_import_paint(self):
        path, _ = QFileDialog.getOpenFileName(self, "PNGを選択", "", "PNG (*.png)")
        if path and self.cur and self.layer_idx >= 0:
            arr = np.array(Image.open(path).convert("RGBA"))
            self.cur.push()
            self.layers()[self.layer_idx]["data"] = E.encode_paint(arr)
            self.changed(rebuild_layers=True)

    def on_hover(self, x: int, y: int):
        if x < 0 or not self.cur or self.preview.arr is None:
            return
        px = self.preview.arr[y, x]
        self.statusBar().showMessage(f"({x}, {y})   {E.to_hex(px / 255.0, alpha=True)}   alpha {px[3]}")

    # ------------------------------------------------------------ actions
    def act_undo(self):
        d = self.cur
        if d and d.undo:
            d.redo.append(d.snap())
            d.spec = json.loads(d.undo.pop())
            self.refresh_layers(select=min(self.layer_idx, len(d.spec["layers"]) - 1))
            self.render_current()

    def act_redo(self):
        d = self.cur
        if d and d.redo:
            d.undo.append(d.snap())
            d.spec = json.loads(d.redo.pop())
            self.refresh_layers(select=min(self.layer_idx, len(d.spec["layers"]) - 1))
            self.render_current()

    def act_revert(self):
        d = self.cur
        if not d:
            return
        d.push()
        d.spec = json.loads(d.saved if d.managed and d.saved else d.original)
        self.refresh_layers(select=len(d.spec["layers"]) - 1)
        self.render_current()

    def save_doc(self, d: Doc, confirm: bool = True) -> bool:
        exists = os.path.isfile(E.png_path(d.key))
        if confirm and exists and not d.managed and E.is_outdated(d.key, d.spec, _SRC):
            msg = "このPNGは現在ジェネレータ/手作業で作られたものです。Studioの内容で上書きしますか？\n（以後、ジェネレータを再実行してもStudioの内容が優先されます）"
            if E.is_locked(d.key):
                msg += "\n\nロック済みテクスチャです。ロックファイルも更新されます。"
            if QMessageBox.question(self, "上書き確認", msg) != QMessageBox.Yes:
                return False
        E.save_spec(d.key, d.spec)
        d.managed = True
        d.saved = d.snap()
        E.export(d.key, d.spec, _SRC)
        d.original = d.saved
        self.update_item(d.key)
        return True

    def act_save(self):
        if self.cur and self.save_doc(self.cur):
            self.render_current()
            self.statusBar().showMessage(f"保存して反映しました: textures/{self.cur.key}.png", 6000)

    def act_save_all(self):
        dirty = [d for d in self.docs.values() if d.dirty]
        if dirty and QMessageBox.question(self, "全て保存", f"未保存の {len(dirty)} 枚を保存して反映します。") == QMessageBox.Yes:
            for d in dirty:
                self.save_doc(d, confirm=False)
            self.refresh_list()
            self.render_current()
            self.statusBar().showMessage(f"{len(dirty)} 枚を保存しました。", 6000)

    def act_export_all(self):
        n = E.apply_all(quiet=True)
        self.refresh_list()
        self.statusBar().showMessage(f"管理中の全テクスチャを書き出しました（変更 {n} 枚）", 6000)

    def act_new(self):
        dlg = NewDialog(self, set(self.all_keys()))
        if dlg.exec() != QDialog.Accepted:
            return
        key = dlg.result_key()
        layers = [E.new_layer("image", name="ベース", source=dlg.base.value)] if dlg.base.value else []
        d = Doc(key, E.new_spec(dlg.size.currentData(), layers), False)
        d.original = ""  # never saved -> dirty
        self.docs[key] = d
        self.kind_tabs.setCurrentIndex(0)
        self.filter.setCurrentIndex(0)
        self.search.setText("")
        self.refresh_list(keep=key)
        self.open_doc(key)

    def act_dup_texture(self):
        if not self.cur:
            return
        name, ok = QInputDialog.getText(self, "別名で複製", "新しい名前 (小文字英数字と _):", text=self.cur.key.split("/")[1] + "_2")
        if not ok or not name.strip():
            return
        key = f"{self.cur.key.split('/')[0]}/{name.strip()}"
        if key in set(self.all_keys()):
            QMessageBox.warning(self, "複製", "同名のテクスチャがあります。")
            return
        import copy
        d = Doc(key, copy.deepcopy(self.cur.spec), False)
        d.original = ""
        self.docs[key] = d
        self.refresh_list(keep=key)
        self.open_doc(key)

    def act_unmanage(self):
        d = self.cur
        if not d or not d.managed:
            return
        if QMessageBox.question(self, "管理から外す", f"{d.key} のスペック(JSON)を削除します。PNGは残ります。") == QMessageBox.Yes:
            E.delete_spec(d.key)
            self.docs.pop(d.key, None)
            self.refresh_list(keep=d.key)
            self.open_doc(d.key)

    def act_bulk(self):
        keys = [i.data(Qt.UserRole) for i in self.tex_list.selectedItems()]
        if keys:
            BulkDialog(self, keys).exec()

    def after_bulk(self):
        self.refresh_list()
        if self.cur:
            self.open_doc(self.cur.key)

    def closeEvent(self, e):
        dirty = [d for d in self.docs.values() if d.dirty]
        if dirty:
            r = QMessageBox.question(self, "終了", f"未保存の {len(dirty)} 枚があります。保存して終了しますか？",
                                     QMessageBox.Save | QMessageBox.Discard | QMessageBox.Cancel)
            if r == QMessageBox.Cancel:
                e.ignore()
                return
            if r == QMessageBox.Save:
                for d in dirty:
                    self.save_doc(d, confirm=False)
        e.accept()


def main(argv: list[str]) -> int:
    app = QApplication(argv)
    app.setStyleSheet(STYLE)
    f = QFont("Yu Gothic UI", 9)
    app.setFont(f)
    win = MainWindow()
    win.show()
    return app.exec()
