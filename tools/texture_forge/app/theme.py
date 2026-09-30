"""Dark theme: colour tokens, Qt palette and style sheet."""
from __future__ import annotations

from pathlib import Path

from PySide6.QtGui import QColor, QFont, QPalette
from PySide6.QtWidgets import QApplication

BACKGROUND = "#0D0F12"
PANEL = "#15181D"
SUBPANEL = "#1C2026"
INPUT = "#242932"
BORDER = "#303640"
TEXT = "#E6EAF0"
SUBTEXT = "#9AA3AF"
ACCENT = "#4EA1FF"
SUCCESS = "#4CC38A"
WARNING = "#E5B84B"
ERROR = "#E05A5A"

ACCENT_HOVER = "#6BB2FF"
ACCENT_PRESSED = "#3A86DB"
DISABLED = "#5A626E"

STYLE_SHEET = f"""
* {{
    color: {TEXT};
    font-size: 9pt;
    outline: none;
}}
QMainWindow, QDialog {{
    background: {BACKGROUND};
}}
QWidget#Panel, QFrame#Panel {{
    background: {PANEL};
    border: 1px solid {BORDER};
    border-radius: 6px;
}}
QWidget#SubPanel, QFrame#SubPanel {{
    background: {SUBPANEL};
    border: 1px solid {BORDER};
    border-radius: 4px;
}}
QLabel {{
    background: transparent;
}}
QLabel#PanelTitle {{
    color: {SUBTEXT};
    font-size: 8pt;
    font-weight: 700;
    letter-spacing: 1.5px;
    padding: 2px 0 4px 0;
}}
QLabel#Sub, QLabel[sub="true"] {{
    color: {SUBTEXT};
}}
QLabel#Value {{
    color: {SUBTEXT};
    font-family: "Consolas", "Cascadia Mono", monospace;
    min-width: 34px;
}}
QLabel#Success {{ color: {SUCCESS}; }}
QLabel#Warning {{ color: {WARNING}; }}
QLabel#Error {{ color: {ERROR}; }}

QMenuBar {{
    background: {PANEL};
    border-bottom: 1px solid {BORDER};
    padding: 2px;
}}
QMenuBar::item {{
    background: transparent;
    padding: 4px 10px;
    border-radius: 4px;
}}
QMenuBar::item:selected {{
    background: {INPUT};
}}
QMenu {{
    background: {SUBPANEL};
    border: 1px solid {BORDER};
    padding: 4px;
}}
QMenu::item {{
    padding: 5px 24px 5px 14px;
    border-radius: 3px;
}}
QMenu::item:selected {{
    background: {ACCENT};
    color: #0B1220;
}}
QMenu::item:disabled {{
    color: {DISABLED};
}}
QMenu::separator {{
    height: 1px;
    background: {BORDER};
    margin: 4px 6px;
}}

QPushButton, QToolButton {{
    background: {INPUT};
    border: 1px solid {BORDER};
    border-radius: 4px;
    padding: 5px 12px;
}}
QToolButton {{
    padding: 4px 8px;
}}
QPushButton:hover, QToolButton:hover {{
    border-color: {ACCENT};
}}
QPushButton:pressed, QToolButton:pressed {{
    background: {SUBPANEL};
}}
QPushButton:checked, QToolButton:checked {{
    background: #1E3450;
    border-color: {ACCENT};
}}
QPushButton:disabled, QToolButton:disabled {{
    color: {DISABLED};
    border-color: {SUBPANEL};
}}
QPushButton#Primary {{
    background: {ACCENT};
    color: #0B1220;
    border: 1px solid {ACCENT};
    font-weight: 700;
    padding: 7px 18px;
}}
QPushButton#Primary:hover {{
    background: {ACCENT_HOVER};
}}
QPushButton#Primary:pressed {{
    background: {ACCENT_PRESSED};
}}
QPushButton#Primary:disabled {{
    background: #24384F;
    color: #6F8096;
    border-color: #24384F;
}}

QLineEdit, QSpinBox, QDoubleSpinBox, QComboBox, QPlainTextEdit, QTextEdit {{
    background: {INPUT};
    border: 1px solid {BORDER};
    border-radius: 4px;
    padding: 3px 6px;
    selection-background-color: {ACCENT};
    selection-color: #0B1220;
}}
QLineEdit:focus, QSpinBox:focus, QComboBox:focus, QPlainTextEdit:focus, QTextEdit:focus {{
    border-color: {ACCENT};
}}
QComboBox::drop-down {{
    border: none;
    width: 18px;
}}
QComboBox::down-arrow {{
    image: url(@UI@/arrow_down.png);
    width: 10px;
    height: 6px;
    margin-right: 6px;
}}
QComboBox::down-arrow:hover {{
    image: url(@UI@/arrow_down_hover.png);
}}
QComboBox QAbstractItemView {{
    background: {SUBPANEL};
    border: 1px solid {BORDER};
    selection-background-color: {ACCENT};
    selection-color: #0B1220;
    padding: 2px;
}}
QSpinBox::up-button, QSpinBox::down-button,
QDoubleSpinBox::up-button, QDoubleSpinBox::down-button {{
    background: {SUBPANEL};
    border: none;
    width: 16px;
}}
QSpinBox::up-arrow, QDoubleSpinBox::up-arrow {{
    image: url(@UI@/arrow_up.png);
    width: 8px;
    height: 5px;
}}
QSpinBox::down-arrow, QDoubleSpinBox::down-arrow {{
    image: url(@UI@/arrow_down.png);
    width: 8px;
    height: 5px;
}}
QSpinBox::up-arrow:hover, QDoubleSpinBox::up-arrow:hover {{
    image: url(@UI@/arrow_up_hover.png);
}}
QSpinBox::down-arrow:hover, QDoubleSpinBox::down-arrow:hover {{
    image: url(@UI@/arrow_down_hover.png);
}}

QSlider::groove:horizontal {{
    height: 4px;
    background: {INPUT};
    border-radius: 2px;
}}
QSlider::sub-page:horizontal {{
    background: #2F5E93;
    border-radius: 2px;
}}
QSlider::handle:horizontal {{
    background: {ACCENT};
    width: 12px;
    height: 12px;
    margin: -5px 0;
    border-radius: 6px;
}}
QSlider::handle:horizontal:hover {{
    background: {ACCENT_HOVER};
}}
QSlider:disabled::handle:horizontal {{
    background: {DISABLED};
}}

QCheckBox {{
    spacing: 6px;
    background: transparent;
}}
QCheckBox::indicator {{
    width: 14px;
    height: 14px;
    border: 1px solid {BORDER};
    border-radius: 3px;
    background: {INPUT};
}}
QCheckBox::indicator:checked {{
    background: {ACCENT};
    border-color: {ACCENT};
    image: url(@UI@/check.png);
}}
QCheckBox::indicator:hover {{
    border-color: {ACCENT};
}}

QProgressBar {{
    background: {INPUT};
    border: 1px solid {BORDER};
    border-radius: 3px;
    text-align: center;
    height: 14px;
    color: {TEXT};
}}
QProgressBar::chunk {{
    background: {ACCENT};
    border-radius: 2px;
}}

QTabWidget::pane {{
    border: 1px solid {BORDER};
    border-radius: 4px;
    background: {PANEL};
    top: -1px;
}}
QTabBar::tab {{
    background: {SUBPANEL};
    border: 1px solid {BORDER};
    padding: 5px 12px;
    margin-right: 2px;
    border-top-left-radius: 4px;
    border-top-right-radius: 4px;
    color: {SUBTEXT};
}}
QTabBar::tab:selected {{
    background: {PANEL};
    color: {TEXT};
    border-bottom-color: {PANEL};
}}
QTabBar::tab:hover {{
    color: {TEXT};
}}

QListWidget, QTreeWidget, QTableWidget {{
    background: {SUBPANEL};
    border: 1px solid {BORDER};
    border-radius: 4px;
    alternate-background-color: {PANEL};
}}
QListWidget::item, QTreeWidget::item {{
    padding: 3px;
    border-radius: 3px;
}}
QListWidget::item:selected, QTreeWidget::item:selected, QTableWidget::item:selected {{
    background: #1E3450;
    color: {TEXT};
}}
QListWidget::item:hover {{
    background: {INPUT};
}}
QHeaderView::section {{
    background: {PANEL};
    border: none;
    border-bottom: 1px solid {BORDER};
    padding: 4px;
    color: {SUBTEXT};
}}

QScrollArea {{
    background: transparent;
    border: none;
}}
QScrollArea > QWidget > QWidget {{
    background: transparent;
}}
QScrollBar:vertical {{
    background: {PANEL};
    width: 10px;
    margin: 0;
}}
QScrollBar::handle:vertical {{
    background: {BORDER};
    border-radius: 4px;
    min-height: 24px;
    margin: 2px;
}}
QScrollBar::handle:vertical:hover {{
    background: #46505E;
}}
QScrollBar:horizontal {{
    background: {PANEL};
    height: 10px;
}}
QScrollBar::handle:horizontal {{
    background: {BORDER};
    border-radius: 4px;
    min-width: 24px;
    margin: 2px;
}}
QScrollBar::add-line, QScrollBar::sub-line {{
    width: 0; height: 0;
}}
QScrollBar::add-page, QScrollBar::sub-page {{
    background: none;
}}

QSplitter::handle {{
    background: {BACKGROUND};
}}
QSplitter::handle:horizontal {{ width: 6px; }}
QSplitter::handle:vertical {{ height: 6px; }}

QStatusBar {{
    background: {PANEL};
    border-top: 1px solid {BORDER};
    color: {SUBTEXT};
}}
QStatusBar::item {{ border: none; }}

QToolTip {{
    background: {SUBPANEL};
    color: {TEXT};
    border: 1px solid {BORDER};
    padding: 4px;
}}

QGroupBox {{
    border: 1px solid {BORDER};
    border-radius: 4px;
    margin-top: 10px;
    padding-top: 6px;
}}
QGroupBox::title {{
    subcontrol-origin: margin;
    left: 8px;
    padding: 0 4px;
    color: {SUBTEXT};
}}

QToolButton#SectionHeader {{
    background: transparent;
    border: none;
    color: {SUBTEXT};
    font-weight: 700;
    font-size: 8pt;
    letter-spacing: 1px;
    text-align: left;
    padding: 6px 2px 2px 0;
}}
QToolButton#SectionHeader:hover {{
    color: {TEXT};
}}
"""


def apply_theme(app: QApplication) -> None:
    app.setStyle("Fusion")
    pal = QPalette()
    roles = {
        QPalette.Window: BACKGROUND,
        QPalette.WindowText: TEXT,
        QPalette.Base: INPUT,
        QPalette.AlternateBase: SUBPANEL,
        QPalette.ToolTipBase: SUBPANEL,
        QPalette.ToolTipText: TEXT,
        QPalette.Text: TEXT,
        QPalette.Button: INPUT,
        QPalette.ButtonText: TEXT,
        QPalette.BrightText: ERROR,
        QPalette.Highlight: ACCENT,
        QPalette.HighlightedText: "#0B1220",
        QPalette.Link: ACCENT,
        QPalette.PlaceholderText: SUBTEXT,
        QPalette.Mid: BORDER,
        QPalette.Dark: BACKGROUND,
        QPalette.Light: SUBPANEL,
    }
    for role, color in roles.items():
        pal.setColor(role, QColor(color))
    for role in (QPalette.WindowText, QPalette.Text, QPalette.ButtonText):
        pal.setColor(QPalette.Disabled, role, QColor(DISABLED))
    app.setPalette(pal)
    font = QFont("Segoe UI", 9)
    app.setFont(font)
    ui_dir = (Path(__file__).resolve().parents[1] / "assets" / "ui").as_posix()
    app.setStyleSheet(STYLE_SHEET.replace("@UI@", ui_dir))
