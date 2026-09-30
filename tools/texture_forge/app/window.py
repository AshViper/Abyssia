"""Main window.

The window only collects settings, hands them to :class:`TextureGenerator`
on a worker thread and displays the returned image:

    Settings ─► TaskRunner(TextureGenerator.generate) ─► PIL.Image ─► Preview
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image
from PySide6.QtCore import QSize, Qt, QTimer, QUrl
from PySide6.QtGui import QAction, QDesktopServices, QGuiApplication, QIcon, QKeySequence
from PySide6.QtWidgets import (QCheckBox, QColorDialog, QComboBox, QFileDialog, QFormLayout, QFrame,
                               QGridLayout, QHBoxLayout, QInputDialog, QLabel, QLineEdit, QMainWindow,
                               QMessageBox, QProgressBar, QPushButton, QScrollArea, QSpinBox, QSplitter,
                               QTabWidget, QToolButton, QTreeWidget, QTreeWidgetItem, QVBoxLayout, QWidget)

from app import theme
from app.dialogs import AIPromptDialog, BatchDialog, PackDialog, PreferencesDialog
from app.widgets import (BACKGROUNDS, ColorButton, CompareView, HistoryList, PaletteStrip, PixelView, Section,
                         SliderRow, ThumbGrid, panel_title)
from app.worker import TaskRunner
from core.analyzer import TextureAnalysis
from core.config import ASSET_DIR, HISTORY_DIR, PRESET_DIR, AppConfig
from core.generator import GenerationResult, TextureGenerator
from core.history import History, HistoryEntry, UndoStack
from core.palette import ACCENT_PRESETS, THEMES, Palette, extract_palette, rgb_to_hex
from core.pixel_art import unique_colors
from core.presets import list_batch_presets, list_presets, load_preset, preset_label, save_preset
from core.seed import MAX_SEED, random_seed
from core.settings import (CATEGORIES, CATEGORY_FEATURES, COLOR_LIMITS, FILTER_STEPS, LAYER_LABELS,
                           LAYER_ORDER, MATERIALS, PARTS, SIZES, SLIDERS, STYLES, VARIANTS, TextureSettings)
from export.png import export_png, export_series, read_embedded_settings, sanitize_name
from export.resource_pack import PackTexture, export_resource_pack

SUPPORTED = "Images (*.png *.jpg *.jpeg *.webp)"


def _panel(title: str | None = None) -> tuple[QFrame, QVBoxLayout]:
    f = QFrame()
    f.setObjectName("Panel")
    lay = QVBoxLayout(f)
    lay.setContentsMargins(10, 8, 10, 10)
    lay.setSpacing(6)
    if title:
        lay.addWidget(panel_title(title))
    return f, lay


class MainWindow(QMainWindow):
    def __init__(self, source_path: str | None = None):
        super().__init__()
        self.setWindowTitle("Texture Forge")
        self.resize(1480, 920)
        icon = ASSET_DIR / "icon.png"
        if icon.exists():
            self.setWindowIcon(QIcon(str(icon)))

        self.cfg = AppConfig.load()
        self.generator = TextureGenerator()
        self.runner = TaskRunner(self)
        self.settings = TextureSettings(name=self.cfg.texture_name)
        self.source_image: Image.Image | None = None
        self.source_path = ""
        self.current: GenerationResult | None = None
        self.variations: list[GenerationResult] = []
        self.undo_stack = UndoStack()
        self.history = History(HISTORY_DIR)
        self.history.load()
        self._updating = False
        self._pending_record = False

        self.sliders: dict[str, SliderRow] = {}
        self.layer_checks: dict[str, QCheckBox] = {}
        self.filter_checks: dict[str, QCheckBox] = {}

        self._live_timer = QTimer(self)
        self._live_timer.setSingleShot(True)
        self._live_timer.setInterval(160)
        self._live_timer.timeout.connect(lambda: self.generate(record=False))

        self._build_menu()
        self._build_ui()
        self._build_status()
        self._apply_settings_to_ui(self.settings)
        self._refresh_history()
        self._update_undo_actions()
        self.runner.busyChanged.connect(self._busy_changed)

        if source_path:
            self.load_source(source_path)
        QTimer.singleShot(50, lambda: self.generate(record=True))

    # =============================================================== menu
    def _action(self, menu, text: str, slot, shortcut: str | QKeySequence | None = None,
                checkable: bool = False, checked: bool = False) -> QAction:
        a = QAction(text, self)
        if shortcut:
            a.setShortcut(QKeySequence(shortcut))
        a.setCheckable(checkable)
        if checkable:
            a.setChecked(checked)
            a.toggled.connect(slot)
        else:
            a.triggered.connect(slot)
        menu.addAction(a)
        return a

    def _build_menu(self) -> None:
        mb = self.menuBar()
        m = mb.addMenu("&File")
        self._action(m, "Open Texture…", self.open_source, QKeySequence.Open)
        self.recent_menu = m.addMenu("Open Recent")
        self._action(m, "Clear Source", self.clear_source)
        m.addSeparator()
        self._action(m, "Export PNG", self.export_png, QKeySequence.Save)
        self._action(m, "Export PNG As…", self.export_png_as, "Ctrl+Shift+S")
        self._action(m, "Export Variations", self.export_variations)
        self._action(m, "Export Resource Pack…", self.export_pack, "Ctrl+E")
        m.addSeparator()
        self._action(m, "Load Settings from PNG…", self.load_settings_from_png)
        self._action(m, "Open Output Folder", lambda: self._open_folder(Path(self.cfg.output_dir)))
        m.addSeparator()
        self._action(m, "Exit", self.close, QKeySequence.Quit)

        m = mb.addMenu("&Edit")
        self.undo_action = self._action(m, "Undo", self.undo, QKeySequence.Undo)
        self.redo_action = self._action(m, "Redo", self.redo, "Ctrl+Y")
        self.redo_action.setShortcuts([QKeySequence("Ctrl+Y"), QKeySequence("Ctrl+Shift+Z")])
        m.addSeparator()
        self._action(m, "Copy Image", self.copy_image, "Ctrl+Shift+C")

        m = mb.addMenu("&Generate")
        self._action(m, "Generate", lambda: self.generate(record=True), "F5")
        self._action(m, "Randomize", self.randomize, "Ctrl+R")
        self._action(m, "Generate Variations", self.generate_variations, "Ctrl+Shift+G")
        m.addSeparator()
        self._action(m, "AI Prompt…", self.open_ai_dialog, "Ctrl+I")
        self._action(m, "Batch Generation…", lambda: self.open_batch(), "Ctrl+B")

        self.presets_menu = mb.addMenu("&Presets")
        self.presets_menu.aboutToShow.connect(self._fill_presets_menu)

        m = mb.addMenu("&Settings")
        self._action(m, "Preferences…", self.open_preferences, "Ctrl+,")
        m.addSeparator()
        self.live_action = self._action(m, "Live Preview", self._set_live, None, True, self.cfg.live_preview)
        self.grid_action = self._action(m, "Show Pixel Grid", self._set_grid, "G", True, self.cfg.show_grid)
        self._fill_recent_menu()

    def _fill_presets_menu(self) -> None:
        m = self.presets_menu
        m.clear()
        presets = list_presets()
        if not presets:
            a = m.addAction("(no presets)")
            a.setEnabled(False)
        for p in presets:
            a = m.addAction(preset_label(p))
            a.triggered.connect(lambda _=False, path=p: self.apply_preset(path))
        m.addSeparator()
        batch = m.addMenu("Batch Presets")
        for p in list_batch_presets():
            a = batch.addAction(preset_label(p))
            a.triggered.connect(lambda _=False, path=p: self.open_batch(str(path)))
        m.addSeparator()
        m.addAction("Save Current as Preset…").triggered.connect(self.save_current_preset)
        m.addAction("Open Presets Folder").triggered.connect(lambda: self._open_folder(PRESET_DIR))

    def _fill_recent_menu(self) -> None:
        self.recent_menu.clear()
        for path in self.cfg.recent_sources:
            a = self.recent_menu.addAction(path)
            a.triggered.connect(lambda _=False, p=path: self.load_source(p))
        self.recent_menu.setEnabled(bool(self.cfg.recent_sources))

    # ================================================================= UI
    def _build_ui(self) -> None:
        root = QSplitter(Qt.Horizontal)
        root.setChildrenCollapsible(False)
        root.addWidget(self._build_left())
        root.addWidget(self._build_center())
        root.addWidget(self._build_right())
        root.setStretchFactor(0, 0)
        root.setStretchFactor(1, 1)
        root.setStretchFactor(2, 0)
        root.setSizes([330, 760, 390])
        wrap = QWidget()
        lay = QVBoxLayout(wrap)
        lay.setContentsMargins(8, 8, 8, 4)
        lay.addWidget(root)
        self.setCentralWidget(wrap)

    # ---------------------------------------------------------- left column
    def _build_left(self) -> QWidget:
        col = QSplitter(Qt.Vertical)
        col.setChildrenCollapsible(False)

        src, lay = _panel("Source")
        self.source_view = PixelView(accept_drops=True,
                                     placeholder="Drop a texture here\n(PNG · JPG · WEBP)\n\nor use Open Texture")
        self.source_view.setMinimumHeight(180)
        self.source_view.fileDropped.connect(self.load_source)
        self.source_view.pixelHovered.connect(self._pixel_info)
        lay.addWidget(self.source_view, 1)
        self.source_label = QLabel("No source · built-in style profile")
        self.source_label.setObjectName("Sub")
        self.source_label.setWordWrap(True)
        lay.addWidget(self.source_label)
        row = QHBoxLayout()
        open_btn = QPushButton("Open Texture")
        open_btn.clicked.connect(self.open_source)
        clear_btn = QPushButton("Clear")
        clear_btn.clicked.connect(self.clear_source)
        row.addWidget(open_btn, 1)
        row.addWidget(clear_btn)
        lay.addLayout(row)
        lay.addWidget(panel_title("Analysis"))
        self.analysis_tree = QTreeWidget()
        self.analysis_tree.setColumnCount(2)
        self.analysis_tree.setHeaderHidden(True)
        self.analysis_tree.setRootIsDecorated(False)
        self.analysis_tree.setMinimumHeight(150)
        self.analysis_tree.setColumnWidth(0, 110)
        lay.addWidget(self.analysis_tree, 1)
        col.addWidget(src)

        pal, lay = _panel("Palette")
        self.palette_strip = PaletteStrip()
        self.source_palette_strip = PaletteStrip(swatch=14)
        lay.addWidget(QLabel("Generated"))
        lay.addWidget(self.palette_strip)
        lay.addWidget(QLabel("Extracted from source (reference only)"))
        lay.addWidget(self.source_palette_strip)
        lay.addStretch(1)
        col.addWidget(pal)

        hist, lay = _panel("History")
        self.history_list = HistoryList()
        self.history_list.picked.connect(self._history_picked)
        lay.addWidget(self.history_list, 1)
        clear_hist = QPushButton("Clear History")
        clear_hist.clicked.connect(self._clear_history)
        lay.addWidget(clear_hist)
        col.addWidget(hist)
        col.setSizes([430, 170, 260])
        return col

    # -------------------------------------------------------- center column
    def _build_center(self) -> QWidget:
        panel, lay = _panel("Preview")
        self.tabs = QTabWidget()
        # generated
        gen_tab = QWidget()
        gl = QVBoxLayout(gen_tab)
        gl.setContentsMargins(6, 6, 6, 6)
        self.preview = PixelView(placeholder="Press Generate (F5)")
        self.preview.pixelHovered.connect(self._pixel_info)
        self.preview.zoomChanged.connect(lambda z: self.zoom_label.setText(f"{z}×"))
        gl.addWidget(self.preview, 1)
        self.tabs.addTab(gen_tab, "Generated")
        # compare
        self.compare = CompareView()
        self.tabs.addTab(self.compare, "Compare")
        # variations
        var_tab = QWidget()
        vl = QVBoxLayout(var_tab)
        vl.setContentsMargins(6, 6, 6, 6)
        self.var_grid = ThumbGrid(112)
        self.var_grid.picked.connect(self._variation_picked)
        self.var_grid.activatedIndex.connect(self._variation_activated)
        hint = QLabel("Click a variation to preview it · double-click to keep it (adds to History)")
        hint.setObjectName("Sub")
        vl.addWidget(hint)
        vl.addWidget(self.var_grid, 1)
        self.tabs.addTab(var_tab, "Variations")
        # layers
        layer_tab = QWidget()
        ll = QVBoxLayout(layer_tab)
        ll.setContentsMargins(6, 6, 6, 6)
        lh = QLabel("State of the texture after each layer (disabled layers are skipped)")
        lh.setObjectName("Sub")
        self.layer_grid = ThumbGrid(112)
        ll.addWidget(lh)
        ll.addWidget(self.layer_grid, 1)
        self.tabs.addTab(layer_tab, "Layers")
        self.tabs.currentChanged.connect(lambda _i: self._refresh_compare())
        lay.addWidget(self.tabs, 1)

        bar = QHBoxLayout()
        zin = QToolButton()
        zin.setText("Zoom +")
        zin.clicked.connect(self._zoom_in)
        zout = QToolButton()
        zout.setText("Zoom −")
        zout.clicked.connect(self._zoom_out)
        fit = QToolButton()
        fit.setText("Fit")
        fit.clicked.connect(self._zoom_fit)
        self.zoom_label = QLabel("")
        self.zoom_label.setObjectName("Value")
        self.grid_btn = QToolButton()
        self.grid_btn.setText("Grid")
        self.grid_btn.setCheckable(True)
        self.grid_btn.setChecked(self.cfg.show_grid)
        self.grid_btn.toggled.connect(self._set_grid)
        self.tile_btn = QToolButton()
        self.tile_btn.setText("Tile 3×3")
        self.tile_btn.setCheckable(True)
        self.tile_btn.setToolTip("Repeat the texture to check seamless tiling")
        self.tile_btn.toggled.connect(self.preview.setTile)
        self.bg_combo = QComboBox()
        for k, v in BACKGROUNDS.items():
            self.bg_combo.addItem(v, k)
        self.bg_combo.setCurrentIndex(max(0, self.bg_combo.findData(self.cfg.preview_background)))
        self.bg_combo.currentIndexChanged.connect(self._set_background)
        self.info_label = QLabel("")
        self.info_label.setObjectName("Sub")
        for w in (zin, zout, fit, self.zoom_label):
            bar.addWidget(w)
        bar.addSpacing(12)
        bar.addWidget(self.grid_btn)
        bar.addWidget(self.tile_btn)
        bar.addWidget(QLabel("Background"))
        bar.addWidget(self.bg_combo)
        bar.addStretch(1)
        bar.addWidget(self.info_label)
        lay.addLayout(bar)
        self._set_grid(self.cfg.show_grid)
        self._set_background()
        return panel

    # --------------------------------------------------------- right column
    def _build_right(self) -> QWidget:
        col = QWidget()
        col.setMinimumWidth(360)
        cl = QVBoxLayout(col)
        cl.setContentsMargins(0, 0, 0, 0)
        cl.setSpacing(8)

        panel, lay = _panel("Generation")
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        inner = QWidget()
        il = QVBoxLayout(inner)
        il.setContentsMargins(0, 0, 6, 0)
        il.setSpacing(2)

        # --- general
        sec = Section("General")
        form = QFormLayout()
        form.setLabelAlignment(Qt.AlignLeft)
        form.setHorizontalSpacing(10)
        form.setVerticalSpacing(4)
        self.style_combo = self._combo(STYLES)
        self.category_combo = self._combo(CATEGORIES)
        self.variant_combo = QComboBox()
        self.part_combo = QComboBox()
        self.part_label = QLabel("Part")
        self.material_combo = self._combo(MATERIALS)
        self.size_combo = QComboBox()
        for s in SIZES:
            self.size_combo.addItem(f"{s} × {s}", s)
        seed_row = QHBoxLayout()
        self.seed_spin = QSpinBox()
        self.seed_spin.setRange(0, MAX_SEED)
        self.seed_spin.setAccelerated(True)
        dice = QToolButton()
        dice.setText("🎲")
        dice.setToolTip("New random seed (Ctrl+R)")
        dice.clicked.connect(self.randomize)
        seed_row.addWidget(self.seed_spin, 1)
        seed_row.addWidget(dice)
        form.addRow("Style", self.style_combo)
        form.addRow("Category", self.category_combo)
        form.addRow("Variant", self.variant_combo)
        form.addRow(self.part_label, self.part_combo)
        form.addRow("Material", self.material_combo)
        form.addRow("Size", self.size_combo)
        form.addRow("Seed", seed_row)
        sec.add_layout(form)
        il.addWidget(sec)

        # --- palette
        sec = Section("Palette")
        form = QFormLayout()
        form.setVerticalSpacing(4)
        self.theme_combo = QComboBox()
        for k, t in THEMES.items():
            self.theme_combo.addItem(t.label, k)
            self.theme_combo.setItemData(self.theme_combo.count() - 1, t.description, Qt.ToolTipRole)
        self.accent_combo = QComboBox()
        self.accent_combo.addItem("Theme default", "")
        for k in ACCENT_PRESETS:
            self.accent_combo.addItem(k.replace("_", " ").title(), k)
        base_row = QHBoxLayout()
        self.base_color_btn = ColorButton()
        self.base_color_btn.clicked.connect(self._pick_base_color)
        clear_base = QToolButton()
        clear_base.setText("×")
        clear_base.setToolTip("Use the theme's base colors")
        clear_base.clicked.connect(lambda: (self.base_color_btn.setHex(""), self._on_changed()))
        base_row.addWidget(self.base_color_btn, 1)
        base_row.addWidget(clear_base)
        second_row = QHBoxLayout()
        self.secondary_color_btn = ColorButton()
        self.secondary_color_btn.clicked.connect(self._pick_secondary_color)
        clear_second = QToolButton()
        clear_second.setText("×")
        clear_second.setToolTip("Use the theme's / category's secondary colors (bark, stems...)")
        clear_second.clicked.connect(lambda: (self.secondary_color_btn.setHex(""), self._on_changed()))
        second_row.addWidget(self.secondary_color_btn, 1)
        second_row.addWidget(clear_second)
        self.limit_combo = QComboBox()
        for n in COLOR_LIMITS:
            self.limit_combo.addItem("Unlimited" if n == 0 else f"{n} colors", n)
        self.levels_spin = QSpinBox()
        self.levels_spin.setRange(0, 12)
        self.levels_spin.setSpecialValueText("Auto (from source)")
        form.addRow("Theme", self.theme_combo)
        form.addRow("Accent", self.accent_combo)
        form.addRow("Base color", base_row)
        form.addRow("Secondary color", second_row)
        form.addRow("Color limit", self.limit_combo)
        form.addRow("Tone levels", self.levels_spin)
        sec.add_layout(form)
        sec.add(self._slider("hue_shift", "Hue Shift", "Shift shadows toward blue and highlights toward yellow"))
        il.addWidget(sec)

        groups: dict[str, Section] = {}
        for field, label, group in SLIDERS:
            if group not in groups:
                groups[group] = Section(group)
                il.addWidget(groups[group])
            groups[group].add(self._slider(field, label))
        self.feature_section = groups.get("Feature")

        # --- pixel art
        sec = Section("Pixel Art", expanded=False)
        sec.add(self._slider("source_influence", "Source Infl.", "How strongly the reference's style statistics steer the result"))
        form = QFormLayout()
        self.alpha_spin = QSpinBox()
        self.alpha_spin.setRange(1, 255)
        self.alpha_spin.setToolTip("Pixels below this alpha become fully transparent")
        self.tile_check = QCheckBox("Seamless tiling (blocks)")
        form.addRow("Alpha threshold", self.alpha_spin)
        form.addRow("", self.tile_check)
        self.recolor_check = QCheckBox("Recolour the reference (terrain / ore)")
        self.recolor_check.setToolTip("Use the reference's own pixel layout as the base and only recolour and decorate it.\n"
                                      "The texture is then a derivative of the reference image.")
        form.addRow("", self.recolor_check)
        sec.add_layout(form)
        sec.add(QLabel("Minecraft Style Filter"))
        grid = QGridLayout()
        for i, (k, label) in enumerate(FILTER_STEPS.items()):
            cb = QCheckBox(label)
            self.filter_checks[k] = cb
            cb.toggled.connect(self._on_changed)
            grid.addWidget(cb, i // 2, i % 2)
        sec.add_layout(grid)
        il.addWidget(sec)

        # --- layers
        sec = Section("Layers", expanded=False)
        grid = QGridLayout()
        for i, k in enumerate(LAYER_ORDER):
            cb = QCheckBox(LAYER_LABELS[k])
            if k == "base":
                cb.setEnabled(False)
                cb.setToolTip("The base layer is always generated")
            self.layer_checks[k] = cb
            cb.toggled.connect(self._on_changed)
            grid.addWidget(cb, i // 2, i % 2)
        sec.add_layout(grid)
        il.addWidget(sec)
        il.addStretch(1)
        scroll.setWidget(inner)
        lay.addWidget(scroll, 1)

        # --- actions
        act = QHBoxLayout()
        self.generate_btn = QPushButton("Generate")
        self.generate_btn.setObjectName("Primary")
        self.generate_btn.clicked.connect(lambda: self.generate(record=True))
        rnd = QPushButton("🎲 Randomize")
        rnd.clicked.connect(self.randomize)
        ai_btn = QPushButton("AI Prompt…")
        ai_btn.clicked.connect(self.open_ai_dialog)
        act.addWidget(self.generate_btn, 2)
        act.addWidget(rnd, 1)
        act.addWidget(ai_btn, 1)
        lay.addLayout(act)
        var_row = QHBoxLayout()
        self.var_spin = QSpinBox()
        self.var_spin.setRange(2, 32)
        self.var_spin.setValue(self.cfg.variation_count)
        self.var_spin.setPrefix("Generate ")
        self.var_spin.setSuffix(" Variations")
        var_btn = QPushButton("Go")
        var_btn.clicked.connect(self.generate_variations)
        var_row.addWidget(self.var_spin, 1)
        var_row.addWidget(var_btn)
        lay.addLayout(var_row)
        cl.addWidget(panel, 1)

        # --- output
        out, lay = _panel("Output")
        form = QFormLayout()
        self.name_edit = QLineEdit()
        self.name_edit.setPlaceholderText("abyssal_rock")
        self.name_edit.editingFinished.connect(self._name_changed)
        self.modid_edit = QLineEdit(self.cfg.mod_id)
        self.modid_edit.editingFinished.connect(self._modid_changed)
        form.addRow("Texture Name", self.name_edit)
        form.addRow("Mod ID", self.modid_edit)
        lay.addLayout(form)
        row = QHBoxLayout()
        png_btn = QPushButton("Export PNG")
        png_btn.clicked.connect(self.export_png)
        pack_btn = QPushButton("Export Resource Pack")
        pack_btn.clicked.connect(self.export_pack)
        folder_btn = QToolButton()
        folder_btn.setText("📁")
        folder_btn.setToolTip("Open output folder")
        folder_btn.clicked.connect(lambda: self._open_folder(Path(self.cfg.output_dir)))
        row.addWidget(png_btn, 1)
        row.addWidget(pack_btn, 1)
        row.addWidget(folder_btn)
        lay.addLayout(row)
        cl.addWidget(out)

        # signals
        for combo in (self.style_combo, self.variant_combo, self.part_combo, self.material_combo,
                      self.size_combo, self.theme_combo, self.accent_combo, self.limit_combo):
            combo.currentIndexChanged.connect(self._on_changed)
        self.category_combo.currentIndexChanged.connect(self._category_changed)
        self.seed_spin.valueChanged.connect(self._on_changed)
        self.levels_spin.valueChanged.connect(self._on_changed)
        self.alpha_spin.valueChanged.connect(self._on_changed)
        self.tile_check.toggled.connect(self._on_changed)
        self.recolor_check.toggled.connect(self._on_changed)
        return col

    def _combo(self, items: dict[str, str]) -> QComboBox:
        c = QComboBox()
        for k, v in items.items():
            c.addItem(v, k)
        return c

    def _slider(self, field: str, label: str, tip: str = "") -> SliderRow:
        default = getattr(TextureSettings(), field)
        row = SliderRow(field, label, default, default, tip)
        row.valueChanged.connect(lambda _f, _v: self._on_changed())
        self.sliders[field] = row
        return row

    def _build_status(self) -> None:
        sb = self.statusBar()
        self.status_label = QLabel("Ready")
        self.pixel_label = QLabel("")
        self.pixel_label.setObjectName("Value")
        self.progress = QProgressBar()
        self.progress.setRange(0, 1000)
        self.progress.setFixedWidth(220)
        self.progress.setVisible(False)
        sb.addWidget(self.status_label, 1)
        sb.addPermanentWidget(self.pixel_label)
        sb.addPermanentWidget(self.progress)

    # ============================================================ settings
    def _apply_settings_to_ui(self, s: TextureSettings) -> None:
        self._updating = True
        try:
            self._set_combo(self.style_combo, s.style)
            self._set_combo(self.category_combo, s.category)
            self._fill_variants(s.category)
            self._set_combo(self.variant_combo, s.variant)
            self._set_combo(self.part_combo, s.part)
            self._set_combo(self.material_combo, s.material)
            self._set_combo(self.size_combo, s.size)
            self.seed_spin.setValue(s.seed)
            self._set_combo(self.theme_combo, s.palette)
            self._set_combo(self.accent_combo, s.accent)
            self.base_color_btn.setHex(s.base_color)
            self.secondary_color_btn.setHex(s.secondary_color)
            self._set_combo(self.limit_combo, s.color_limit)
            self.levels_spin.setValue(s.levels)
            for f, row in self.sliders.items():
                row.setValue(getattr(s, f))
            self.alpha_spin.setValue(s.alpha_threshold)
            self.tile_check.setChecked(s.tileable)
            self.recolor_check.setChecked(s.recolor_source)
            for k, cb in self.layer_checks.items():
                cb.setChecked(s.layer_enabled(k))
            for k, cb in self.filter_checks.items():
                cb.setChecked(s.filter_enabled(k))
            self.name_edit.setText(s.name)
            self._update_feature_visibility(s.category)
        finally:
            self._updating = False

    @staticmethod
    def _set_combo(combo: QComboBox, data) -> None:
        i = combo.findData(data)
        if i >= 0:
            combo.setCurrentIndex(i)

    def _fill_variants(self, category: str) -> None:
        self.variant_combo.blockSignals(True)
        self.variant_combo.clear()
        for k, v in VARIANTS.get(category, {}).items():
            self.variant_combo.addItem(v, k)
        self.variant_combo.blockSignals(False)
        parts = PARTS.get(category, {})
        self.part_combo.blockSignals(True)
        self.part_combo.clear()
        for k, v in parts.items():
            self.part_combo.addItem(v, k)
        self.part_combo.blockSignals(False)
        show = len(parts) > 1
        self.part_combo.setVisible(show)
        self.part_label.setVisible(show)

    def _update_feature_visibility(self, category: str) -> None:
        wanted = set(CATEGORY_FEATURES.get(category, ()))
        for field, _label, group in SLIDERS:
            if group == "Feature":
                self.sliders[field].setVisible(field in wanted)

    def _read_settings(self) -> TextureSettings:
        s = TextureSettings()
        s.style = self.style_combo.currentData()
        s.category = self.category_combo.currentData()
        s.variant = self.variant_combo.currentData() or "auto"
        s.part = self.part_combo.currentData() or "stalk"
        s.material = self.material_combo.currentData()
        s.size = int(self.size_combo.currentData())
        s.seed = int(self.seed_spin.value())
        s.palette = self.theme_combo.currentData()
        s.accent = self.accent_combo.currentData() or ""
        s.base_color = self.base_color_btn.hex()
        s.secondary_color = self.secondary_color_btn.hex()
        s.color_limit = int(self.limit_combo.currentData())
        s.levels = int(self.levels_spin.value())
        for f, row in self.sliders.items():
            setattr(s, f, row.value())
        s.alpha_threshold = int(self.alpha_spin.value())
        s.tileable = self.tile_check.isChecked()
        s.recolor_source = self.recolor_check.isChecked()
        s.layers = {k: (cb.isChecked() or k == "base") for k, cb in self.layer_checks.items()}
        s.filters = {k: cb.isChecked() for k, cb in self.filter_checks.items()}
        s.name = sanitize_name(self.name_edit.text() or self.cfg.texture_name)
        return s.clamp()

    def _on_changed(self, *_args) -> None:
        if self._updating:
            return
        self.settings = self._read_settings()
        if self.cfg.live_preview:
            self._live_timer.start()

    def _category_changed(self, *_args) -> None:
        if self._updating:
            return
        cat = self.category_combo.currentData()
        self._updating = True
        self._fill_variants(cat)
        from core.settings import DEFAULT_MATERIAL
        self._set_combo(self.material_combo, DEFAULT_MATERIAL.get(cat, "rock"))
        self._update_feature_visibility(cat)
        self._updating = False
        self._on_changed()

    def _name_changed(self) -> None:
        name = sanitize_name(self.name_edit.text())
        self.name_edit.setText(name)
        self.settings.name = name

    def _modid_changed(self) -> None:
        mod = sanitize_name(self.modid_edit.text()).replace("/", "_")
        self.modid_edit.setText(mod)
        self.cfg.mod_id = mod
        self.cfg.save()

    def _pick_base_color(self) -> None:
        from PySide6.QtGui import QColor
        c = QColorDialog.getColor(QColor(self.base_color_btn.hex() or "#2B3340"), self, "Base colour")
        if c.isValid():
            self.base_color_btn.setHex(c.name().upper())
            self._on_changed()

    def _pick_secondary_color(self) -> None:
        from PySide6.QtGui import QColor
        c = QColorDialog.getColor(QColor(self.secondary_color_btn.hex() or "#2C5C62"), self, "Secondary colour")
        if c.isValid():
            self.secondary_color_btn.setHex(c.name().upper())
            self._on_changed()

    # ============================================================== source
    def open_source(self) -> None:
        start = self.cfg.recent_sources[0] if self.cfg.recent_sources else ""
        path, _ = QFileDialog.getOpenFileName(self, "Open reference texture", str(Path(start).parent) if start else "",
                                              SUPPORTED)
        if path:
            self.load_source(path)

    def load_source(self, path: str) -> None:
        try:
            with Image.open(path) as im:
                img = im.convert("RGBA")
        except OSError as e:
            QMessageBox.warning(self, "Open texture", f"Cannot open {path}:\n{e}")
            return
        if img.width > 512 or img.height > 1024:
            QMessageBox.information(self, "Open texture",
                                    "This image is large for a pixel-art reference; only its statistics are used, "
                                    "but analysis may take a moment.")
        self.source_image = img
        self.source_path = path
        self.source_view.setImage(img)
        p = Path(path)
        note = "" if p.suffix.lower() == ".png" else " · PNG recommended for Minecraft"
        self.source_label.setText(f"{p.name} · {img.width}×{img.height}{note}")
        self.cfg.add_recent(path)
        self.cfg.save()
        self._fill_recent_menu()
        self._show_analysis(self.generator.analyze(img, p.stem))
        ext = extract_palette(np.asarray(img), 8)
        self.source_palette_strip.setRows([("Source", ext.hex())])
        self._refresh_compare()
        self.status_label.setText(f"Loaded reference {p.name} – its style statistics now drive generation")
        self.generate(record=True)

    def clear_source(self) -> None:
        self.source_image = None
        self.source_path = ""
        self.source_view.setImage(None)
        self.source_label.setText("No source · built-in style profile")
        self.source_palette_strip.setRows([])
        self._show_analysis(TextureAnalysis.default_for(self.settings.category, self.settings.size))
        self._refresh_compare()
        self.generate(record=False)

    def _show_analysis(self, a: TextureAnalysis | None) -> None:
        self.analysis_tree.clear()
        if a is None:
            return
        for k, v in a.summary():
            QTreeWidgetItem(self.analysis_tree, [k, v])
        if a.palette:
            QTreeWidgetItem(self.analysis_tree, ["Palette", " ".join(a.palette[:6])])

    # ========================================================== generation
    def generate(self, record: bool = True) -> None:
        self._live_timer.stop()
        s = self._read_settings()
        self.settings = s
        src = self.source_image
        gen = self.generator
        self._pending_record = self._pending_record or record

        def work(progress, cancelled):
            return gen.generate(src, s, progress, cancelled, capture_layers=True)

        self.status_label.setText("Generating…")
        self.runner.submit("generate", work, self._on_generated, self._on_error, self._on_progress)

    def _on_generated(self, result: GenerationResult) -> None:
        record = self._pending_record
        self._pending_record = False
        self._show_result(result)
        entry = HistoryEntry(result.settings, result.image, source_path=self.source_path, result=result)
        self.undo_stack.push(entry)
        if record:
            self.history.add(entry)
            self._refresh_history()
        self._update_undo_actions()
        self.status_label.setText(
            f"Generated {result.name} · seed {result.seed} · {result.colors_used()} colors · "
            f"{result.elapsed * 1000:.0f} ms")

    def _show_result(self, result: GenerationResult) -> None:
        self.current = result
        self.preview.setImage(result.image)
        self._refresh_compare()
        pal = result.palette
        rows = [(role.title(), pal.ramps[role].hex()) for role in ("base", "secondary", "accent", "accent2", "glow")
                if role in pal.ramps]
        self.palette_strip.setRows(rows)
        s = result.settings
        self.info_label.setText(f"{s.size}×{s.size} · {result.colors_used()} colors · seed {s.seed}")
        self.layer_grid.setImages([(f"{i + 1}. {LAYER_LABELS[l.name]}" + ("" if l.enabled else " (off)"),
                                    l.snapshot, l.name) for i, l in enumerate(result.layers) if l.snapshot])
        if self.source_image is None:
            self._show_analysis(result.analysis)

    def _on_error(self, msg: str) -> None:
        self._pending_record = False
        self.status_label.setText("Generation failed: " + msg.split("\n")[0])
        QMessageBox.critical(self, "Generation failed", msg)

    def _on_progress(self, value: float, msg: str) -> None:
        self.progress.setValue(int(value * 1000))
        self.progress.setFormat(f"Generating… %p%")
        self.status_label.setText(f"Generating… {msg}")

    def _busy_changed(self, busy: bool) -> None:
        self.progress.setVisible(busy)
        if not busy:
            self.progress.setValue(0)

    def randomize(self) -> None:
        self._updating = True
        self.seed_spin.setValue(random_seed())
        self._updating = False
        self.generate(record=True)

    def generate_variations(self) -> None:
        s = self._read_settings()
        n = int(self.var_spin.value())
        self.cfg.variation_count = n
        src = self.source_image
        gen = self.generator

        def work(progress, cancelled):
            return gen.generate_variations(src, s, n, progress, cancelled)

        self.status_label.setText(f"Generating {n} variations…")
        self.runner.submit("variations", work, self._on_variations, self._on_error, self._on_progress)

    def _on_variations(self, results: list[GenerationResult]) -> None:
        self.variations = results
        self.var_grid.setImages([(f"[{i + 1:02d}]", r.image, f"seed {r.seed}") for i, r in enumerate(results)])
        self.tabs.setCurrentIndex(2)
        self.status_label.setText(f"{len(results)} variations – click to preview, double-click to keep")

    def _variation_picked(self, index: int) -> None:
        if 0 <= index < len(self.variations):
            r = self.variations[index]
            self.preview.setImage(r.image)
            self.current = r
            self.info_label.setText(f"variation {index + 1:02d} · seed {r.seed} · {r.colors_used()} colors")
            self._refresh_compare()

    def _variation_activated(self, index: int) -> None:
        if not 0 <= index < len(self.variations):
            return
        r = self.variations[index]
        self._apply_settings_to_ui(r.settings)
        self.settings = r.settings.copy()
        self._show_result(r)
        entry = HistoryEntry(r.settings, r.image, source_path=self.source_path, result=r)
        self.undo_stack.push(entry)
        self.history.add(entry)
        self._refresh_history()
        self._update_undo_actions()
        self.tabs.setCurrentIndex(0)
        self.status_label.setText(f"Kept variation {index + 1:02d} (seed {r.seed})")

    # =========================================================== undo/redo
    def _restore(self, entry: HistoryEntry) -> None:
        self._apply_settings_to_ui(entry.settings)
        self.settings = entry.settings.copy()
        result = entry.result
        if result is not None:
            self._show_result(result)
        else:
            self.preview.setImage(entry.image)
            self.current = None
            self._pending_record = False
            self.generate(record=False)
        self._update_undo_actions()

    def undo(self) -> None:
        e = self.undo_stack.undo()
        if e:
            self._restore(e)
            self.status_label.setText(f"Undo → {e.label()}")

    def redo(self) -> None:
        e = self.undo_stack.redo()
        if e:
            self._restore(e)
            self.status_label.setText(f"Redo → {e.label()}")

    def _update_undo_actions(self) -> None:
        self.undo_action.setEnabled(self.undo_stack.can_undo())
        self.redo_action.setEnabled(self.undo_stack.can_redo())

    # ============================================================= history
    def _refresh_history(self) -> None:
        self.history_list.setEntries([(e.label(), e.image, f"{e.settings.category} · seed {e.settings.seed}\n{e.file}")
                                      for e in self.history.entries])

    def _history_picked(self, index: int) -> None:
        if not 0 <= index < len(self.history.entries):
            return
        e = self.history.entries[index]
        self._apply_settings_to_ui(e.settings)
        self.settings = e.settings.copy()
        self.tabs.setCurrentIndex(0)
        if e.result is not None:
            self._show_result(e.result)
        elif e.source_path == self.source_path:
            # same reference → regenerating reproduces the image exactly (and restores palette/layers)
            self.preview.setImage(e.image)
            self._pending_record = False
            self.generate(record=False)
        else:
            # made from another reference: show the stored image as-is
            self._show_result(GenerationResult(e.image, e.settings.copy(), Palette("history"),
                                               TextureAnalysis.default_for(e.settings.category, e.settings.size)))
            self.palette_strip.setRows([("Used", [rgb_to_hex(c) for c in unique_colors(np.asarray(e.image))[:16]])])
        self.status_label.setText(f"Restored {e.label()}"
                                  + ("" if e.source_path == self.source_path or not e.source_path
                                     else f" (made from {Path(e.source_path).name})"))

    def _clear_history(self) -> None:
        if QMessageBox.question(self, "Clear history", "Delete all history entries (and their saved files)?") \
                == QMessageBox.Yes:
            self.history.clear()
            self._refresh_history()

    # ============================================================== presets
    def apply_preset(self, path: Path) -> None:
        try:
            s = load_preset(path, TextureSettings(name=self.settings.name))
        except (OSError, ValueError) as e:
            QMessageBox.warning(self, "Preset", f"Cannot load preset:\n{e}")
            return
        self._apply_settings_to_ui(s)
        self.settings = s
        self.status_label.setText(f"Preset: {preset_label(path)}")
        self.generate(record=True)

    def save_current_preset(self) -> None:
        name, ok = QInputDialog.getText(self, "Save preset", "Preset name:", text=self.settings.name)
        if ok and name.strip():
            path = save_preset(self._read_settings(), name, PRESET_DIR, label=name.strip())
            self.status_label.setText(f"Saved preset {path.name}")

    # =============================================================== export
    def _require_result(self) -> GenerationResult | None:
        if self.current is None:
            QMessageBox.information(self, "Export", "Generate a texture first.")
        return self.current

    def export_png(self) -> None:
        r = self._require_result()
        if r is None:
            return
        path = export_png(r.image, Path(self.cfg.output_dir), self._texture_name(), r.settings.to_dict())
        self.status_label.setText(f"Saved {path}")

    def export_png_as(self) -> None:
        r = self._require_result()
        if r is None:
            return
        default = str(Path(self.cfg.output_dir) / f"{self._texture_name()}.png")
        path, _ = QFileDialog.getSaveFileName(self, "Export PNG", default, "PNG (*.png)")
        if path:
            from export.png import save_png
            save_png(r.image, Path(path), r.settings.to_dict())
            self.status_label.setText(f"Saved {path}")

    def export_variations(self) -> None:
        if not self.variations:
            QMessageBox.information(self, "Export", "Generate variations first.")
            return
        paths = export_series([v.image for v in self.variations], Path(self.cfg.output_dir), self._texture_name(),
                              [v.settings.to_dict() for v in self.variations])
        self.status_label.setText(f"Saved {len(paths)} variations to {Path(paths[0]).parent}")

    def export_pack(self) -> None:
        r = self._require_result()
        if r is None:
            return
        dlg = PackDialog(self.cfg, self._texture_name(), len(self.variations), r.emission is not None, self)
        if dlg.exec() != PackDialog.Accepted:
            return
        v = dlg.values()
        textures = [PackTexture(v["name"], r.image, v["folder"], r.settings.to_dict(),
                                r.emission if v["emission"] else None)]
        if v["variations"]:
            others = [x for x in self.variations if x is not r]
            for i, x in enumerate(others, start=1):
                textures.append(PackTexture(f"{v['name']}_{i:02d}", x.image, v["folder"], x.settings.to_dict(),
                                            x.emission if v["emission"] else None))
        try:
            path = export_resource_pack(textures, Path(v["out_dir"]), v["mod_id"], v["pack_name"],
                                        v["description"], v["pack_format"], v["as_zip"])
        except OSError as e:
            QMessageBox.critical(self, "Export resource pack", str(e))
            return
        self.status_label.setText(f"Resource pack written: {path}")
        box = QMessageBox(self)
        box.setWindowTitle("Resource pack")
        box.setText(f"Exported {len(textures)} texture(s) to\n{path}")
        open_btn = box.addButton("Open Folder", QMessageBox.ActionRole)
        box.addButton(QMessageBox.Ok)
        box.exec()
        if box.clickedButton() is open_btn:
            self._open_folder(Path(path).parent if Path(path).is_file() else Path(path))

    def _texture_name(self) -> str:
        return sanitize_name(self.name_edit.text() or self.settings.name or self.cfg.texture_name)

    def load_settings_from_png(self) -> None:
        path, _ = QFileDialog.getOpenFileName(self, "PNG exported by Texture Forge", self.cfg.output_dir, "PNG (*.png)")
        if not path:
            return
        data = read_embedded_settings(Path(path))
        if not data:
            QMessageBox.information(self, "Load settings", "This PNG has no embedded Texture Forge settings.")
            return
        s = TextureSettings.from_dict(data)
        self._apply_settings_to_ui(s)
        self.settings = s
        self.generate(record=True)

    def copy_image(self) -> None:
        if self.current is None:
            return
        from app.widgets import pil_to_qimage
        QGuiApplication.clipboard().setImage(pil_to_qimage(self.current.image))
        self.status_label.setText("Image copied to clipboard")

    # ============================================================== dialogs
    def open_ai_dialog(self) -> None:
        dlg = AIPromptDialog(self.cfg, self.runner, self._read_settings(), self)
        dlg.applied.connect(self._ai_applied)
        dlg.exec()

    def _ai_applied(self, s: TextureSettings, generate: bool) -> None:
        self._apply_settings_to_ui(s)
        self.settings = s
        if generate:
            self.generate(record=True)
        else:
            self.status_label.setText("AI parameters applied")

    def open_batch(self, preset: str | None = None) -> None:
        dlg = BatchDialog(self.cfg, self.runner, self.generator, self._read_settings(), self, preset)
        dlg.exec()

    def open_preferences(self) -> None:
        dlg = PreferencesDialog(self.cfg, self.runner, self)
        if dlg.exec() == PreferencesDialog.Accepted:
            self.cfg = dlg.apply()
            self.cfg.save()
            self.modid_edit.setText(self.cfg.mod_id)
            self.status_label.setText("Preferences saved")

    # ================================================================= view
    def _set_live(self, on: bool) -> None:
        self.cfg.live_preview = on
        self.cfg.save()

    def _set_grid(self, on: bool) -> None:
        self.cfg.show_grid = on
        for v in (self.preview, self.source_view):
            v.setGrid(on)
        self.compare.setGrid(on)
        for w in (getattr(self, "grid_btn", None), getattr(self, "grid_action", None)):
            if w is not None and w.isChecked() != on:
                w.blockSignals(True)
                w.setChecked(on)
                w.blockSignals(False)

    def _set_background(self, *_a) -> None:
        mode = self.bg_combo.currentData()
        self.cfg.preview_background = mode
        for v in (self.preview, self.source_view):
            v.setBackground(mode)
        self.compare.setBackground(mode)

    def _active_view(self) -> PixelView:
        return self.preview

    def _zoom_in(self) -> None:
        self.preview.zoomIn()

    def _zoom_out(self) -> None:
        self.preview.zoomOut()

    def _zoom_fit(self) -> None:
        self.preview.fit()

    def _refresh_compare(self) -> None:
        if self.tabs.currentWidget() is self.compare:
            self.compare.setImages(self.source_image, self.current.image if self.current else None)

    def _pixel_info(self, x: int, y: int, px) -> None:
        if x < 0 or px is None:
            self.pixel_label.setText("")
            return
        r, g, b, a = px if len(px) == 4 else (*px, 255)
        self.pixel_label.setText(f"({x}, {y})  #{r:02X}{g:02X}{b:02X}  α{a}")

    def _open_folder(self, path: Path) -> None:
        path.mkdir(parents=True, exist_ok=True)
        QDesktopServices.openUrl(QUrl.fromLocalFile(str(path)))

    def closeEvent(self, e) -> None:
        self.cfg.texture_name = self._texture_name()
        self.cfg.variation_count = int(self.var_spin.value())
        try:
            self.cfg.save()
        except OSError:
            pass
        self.runner.wait(2000)
        super().closeEvent(e)
