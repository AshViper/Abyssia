"""Texture Studio <-> Texture Forge: settings form for a "forge" layer, variation picker, pack (batch) generation.

Only Forge's procedural generators are used; its AI bridge is not touched.
"""
from __future__ import annotations

import numpy as np
from PySide6.QtCore import QSize, Qt
from PySide6.QtGui import QIcon
from PySide6.QtWidgets import (QCheckBox, QComboBox, QDialog, QDialogButtonBox, QGridLayout, QHBoxLayout, QInputDialog,
                               QLineEdit, QMessageBox, QPushButton, QSpinBox, QToolButton, QVBoxLayout, QWidget)

import texture_engine as E
from gui import ColorButton, IntRow, SourceButton, label, thumb

SLIDER_JP = {
    "hue": "色相", "saturation": "彩度", "brightness": "明るさ", "contrast": "コントラスト", "temperature": "色温度",
    "tint": "ティント", "roughness": "粗さ", "noise": "ノイズ", "cracks": "ひび", "layering": "層理",
    "moisture": "湿り", "rock": "岩", "crystal": "結晶", "organic": "有機", "metallic": "金属", "density": "密度",
    "mineral": "鉱物", "cluster_size": "塊の大きさ", "glow": "発光", "alteration": "変質", "stem": "茎",
    "leaf": "葉", "branch": "枝", "height": "高さ", "facet": "面", "transparency": "透明度",
}
GROUP_JP = {"Color": "色調整 (50 = 変化なし)", "Detail": "ディテール", "Material": "素材の混ぜ具合", "Feature": "カテゴリ固有"}
LAYER_JP = {"base": "ベース", "material": "素材", "large_detail": "大きな模様", "medium_detail": "中くらいの模様",
            "small_detail": "細かい模様", "cracks": "ひび", "highlights": "ハイライト", "shadows": "影", "accent": "アクセント"}
FILTER_JP = {"cluster_cleanup": "ピクセル塊の整理", "noise_reduction": "ノイズ除去", "edge_cleanup": "輪郭の整理",
             "palette_limit": "色数制限", "quantize": "量子化", "contrast_normalize": "コントラスト補正"}


def build_forge_form(win, form, L: dict, lset, fset) -> None:
    """Fill ``form`` (QFormLayout) with every Forge setting.  lset(key, val) edits the layer, fset(key, val, rebuild)
    edits its Forge settings dict."""
    E.forge_modules()  # puts texture_forge on sys.path
    import core.settings as S
    from core.palette import accent_names, theme_names
    D = S.TextureSettings()
    f = L.setdefault("forge", {})
    get = lambda k: f.get(k, getattr(D, k))
    cat = get("category")

    # -- presets / output --------------------------------------------------
    pre = QComboBox()
    pre.addItem("Forgeプリセットを適用…")
    presets = E.forge_presets()
    for name in presets:
        pre.addItem(name)

    def apply_preset(i):
        if i > 0:
            win.cur.push()
            L["forge"] = dict(presets[pre.itemText(i)])
            L["name"] = pre.itemText(i)
            win.changed(rebuild_layers=True)
    pre.activated.connect(apply_preset)
    form.addRow("プリセット", pre)
    save = QPushButton("いまの設定をForgeプリセットに保存")
    save.clicked.connect(lambda: _save_preset(win, L))
    form.addRow(save)
    out = QComboBox()
    out.addItem("本体の色", "color")
    out.addItem("発光部分のみ (_glow用)", "emission")
    out.setCurrentIndex(0 if L.get("output", "color") == "color" else 1)
    out.currentIndexChanged.connect(lambda _: lset("output", out.currentData()))
    form.addRow("出力", out)

    # -- identity --------------------------------------------------------------
    form.addRow(label("種類", "title"))
    c_cat = QComboBox()
    for k, v in S.CATEGORIES.items():
        c_cat.addItem(v, k)
    c_cat.setCurrentIndex(list(S.CATEGORIES).index(cat) if cat in S.CATEGORIES else 0)

    def cat_changed():
        k = c_cat.currentData()
        win.cur.push()
        f["category"] = k
        f["material"] = S.DEFAULT_MATERIAL.get(k, "rock")
        f.pop("variant", None)
        f.pop("part", None)
        win.changed(rebuild_form=True)
    c_cat.activated.connect(lambda _: cat_changed())
    form.addRow("カテゴリ", c_cat)
    variants = S.VARIANTS.get(cat, {})
    if variants:
        c_var = QComboBox()
        for k, v in variants.items():
            c_var.addItem(v, k)
        cur = get("variant")
        c_var.setCurrentIndex(list(variants).index(cur) if cur in variants else 0)
        c_var.activated.connect(lambda _: fset("variant", c_var.currentData(), False))
        form.addRow("バリエーション", c_var)
    parts = S.PARTS.get(cat, {})
    if parts:
        c_part = QComboBox()
        for k, v in parts.items():
            c_part.addItem(v, k)
        cur = get("part")
        c_part.setCurrentIndex(list(parts).index(cur) if cur in parts else 0)
        c_part.activated.connect(lambda _: fset("part", c_part.currentData(), False))
        form.addRow("パーツ", c_part)
    for key, lab, table in (("material", "素材", S.MATERIALS), ("style", "スタイル", S.STYLES)):
        c = QComboBox()
        for k, v in table.items():
            c.addItem(v, k)
        cur = get(key)
        c.setCurrentIndex(list(table).index(cur) if cur in table else 0)
        c.activated.connect(lambda _, c=c, key=key: fset(key, c.currentData(), False))
        form.addRow(lab, c)

    # -- seed ----------------------------------------------------------------------
    seedw = QWidget()
    h = QHBoxLayout(seedw)
    h.setContentsMargins(0, 0, 0, 0)
    sp = QSpinBox()
    sp.setRange(0, 2147483647)
    sp.setValue(int(get("seed")))
    sp.valueChanged.connect(lambda v: fset("seed", v, False))
    dice = QPushButton("ランダム")
    dice.clicked.connect(lambda: sp.setValue(int(np.random.randint(0, 999999))))
    var = QPushButton("バリエーション…")
    var.clicked.connect(lambda: _variations(win, L, sp))
    h.addWidget(sp, 1)
    h.addWidget(dice)
    h.addWidget(var)
    form.addRow("シード", seedw)

    # -- palette ---------------------------------------------------------------------
    form.addRow(label("パレット", "title"))
    c_pal = QComboBox()
    for t in theme_names():
        c_pal.addItem(t, t)
    cur = get("palette")
    c_pal.setCurrentIndex(theme_names().index(cur) if cur in theme_names() else 0)
    c_pal.activated.connect(lambda _: fset("palette", c_pal.currentData(), False))
    form.addRow("テーマ", c_pal)
    acc = QComboBox()
    acc.setEditable(True)
    acc.addItems([""] + accent_names())
    acc.setCurrentText(get("accent"))
    acc.setToolTip("アクセント色: プリセット名か #RRGGBB。空欄でテーマ既定")
    acc.currentTextChanged.connect(lambda v: fset("accent", v.strip(), False))
    form.addRow("アクセント", acc)
    color_rows = [("base_color", "ベース色 (任意)"), ("secondary_color", "サブ色 (任意)")]
    if cat == "terrain":
        color_rows.append(("crack_color", "ひびの色 (任意)"))
    for key, lab in color_rows:
        cb = ColorButton(get(key), optional=True)
        cb.changed.connect(lambda v, key=key: fset(key, v[:7] if v else "", False))
        form.addRow(lab, cb)
    lim = QComboBox()
    for v in S.COLOR_LIMITS:
        lim.addItem("無制限" if v == 0 else f"{v} 色", v)
    lim.setCurrentIndex(list(S.COLOR_LIMITS).index(get("color_limit")) if get("color_limit") in S.COLOR_LIMITS else 2)
    lim.activated.connect(lambda _: fset("color_limit", lim.currentData(), False))
    form.addRow("色数", lim)

    # -- sliders ---------------------------------------------------------------------
    feats = set(S.CATEGORY_FEATURES.get(cat, ()))
    last = None
    for key, _, group in S.SLIDERS:
        if group == "Feature" and key not in feats:
            continue
        if group != last:
            form.addRow(label(GROUP_JP[group], "title"))
            last = group
        w = IntRow(round(get(key) * 100), 0, 100)
        w.changed.connect(lambda v, key=key: fset(key, v / 100.0, False))
        form.addRow(SLIDER_JP.get(key, key), w)

    # -- reference + pixel-art control -------------------------------------------------
    form.addRow(label("参考画像・仕上げ", "title"))
    src = SourceButton(L.get("source", ""), optional=True, exclude=f"mod:{win.cur.key}")
    src.setToolTip("バニラ等の見た目の統計(階調・粒の大きさ・向き)を参考にします。下の「参考を再着色」で画素そのものを使います。")
    src.changed.connect(lambda v: lset("source", v))
    form.addRow("参考テクスチャ", src)
    infl = IntRow(round(get("source_influence") * 100), 0, 100)
    infl.changed.connect(lambda v: fset("source_influence", v / 100.0, False))
    form.addRow("参考の影響度", infl)
    for key, lab, tip in (("recolor_source", "参考を再着色して使う (画素を流用)", "terrain/ore向け。出力は参考画像の派生物になります"),
                          ("tileable", "タイル可能 (端でループ)", "")):
        c = QCheckBox(lab)
        c.setChecked(bool(get(key)))
        c.setToolTip(tip)
        c.toggled.connect(lambda v, key=key: fset(key, v, key == "recolor_source"))
        form.addRow("", c)
    if get("recolor_source"):
        host = QLineEdit(get("source_host"))
        host.setPlaceholderText("ホスト色 #hex,#hex (空欄=彩度で判定)")
        host.textEdited.connect(lambda v: fset("source_host", v, False))
        form.addRow("ホスト色", host)

    # -- internal layers / filters -----------------------------------------------------
    form.addRow(label("内部レイヤー (ON/OFF)", "title"))
    form.addRow(_toggles("layers", S.LAYER_ORDER, LAYER_JP, get, fset))
    form.addRow(label("仕上げフィルタ (ON/OFF)", "title"))
    form.addRow(_toggles("filters", tuple(S.FILTER_STEPS), FILTER_JP, get, fset))


def _toggles(field: str, keys, jp, get, fset) -> QWidget:
    w = QWidget()
    g = QGridLayout(w)
    g.setContentsMargins(0, 0, 0, 0)
    cur = dict(get(field))
    for i, k in enumerate(keys):
        c = QCheckBox(jp.get(k, k))
        c.setChecked(cur.get(k, True))

        def toggled(v, k=k):
            d = dict(get(field))
            d[k] = v
            fset(field, d, False)
        c.toggled.connect(toggled)
        g.addWidget(c, i // 2, i % 2)
    return w


def _save_preset(win, L):
    name, ok = QInputDialog.getText(win, "Forgeプリセット保存", "プリセット名:")
    if ok and name.strip():
        E.save_forge_preset(name.strip(), L["forge"])
        win.statusBar().showMessage(f"Forgeプリセット「{name}」を texture_forge/presets に保存しました。")


class VariationDialog(QDialog):
    """12 seeds of the same settings; click one to use it."""

    def __init__(self, win, L: dict, n: int):
        super().__init__(win)
        E.forge_modules()
        from core.seed import variation_seed
        self.setWindowTitle("バリエーション（クリックでそのシードを採用）")
        self.seed = None
        lay = QVBoxLayout(self)
        grid = QGridLayout()
        lay.addLayout(grid)
        base = int(L["forge"].get("seed", 1234))
        src = win.src_load(L["source"]) if L.get("source") else None
        for i in range(12):
            seed = variation_seed(base, i)
            arr = E.forge_generate(dict(L["forge"], seed=seed), n, src, L.get("output", "color"))
            b = QToolButton()
            b.setIcon(QIcon(thumb(arr, 96)))
            b.setIconSize(QSize(96, 96))
            b.setText(str(seed))
            b.setToolButtonStyle(Qt.ToolButtonTextUnderIcon)
            b.clicked.connect(lambda _, s=seed: self._pick(s))
            grid.addWidget(b, i // 4, i % 4)
        bb = QDialogButtonBox(QDialogButtonBox.Cancel)
        bb.rejected.connect(self.reject)
        lay.addWidget(bb)

    def _pick(self, s):
        self.seed = s
        self.accept()


def _variations(win, L, spin):
    dlg = VariationDialog(win, L, win.cur.spec.get("size", 16))
    if dlg.exec() == QDialog.Accepted and dlg.seed is not None:
        spin.setValue(dlg.seed)


def forge_batch(win) -> None:
    """Create one texture per entry of a Forge pack preset (unsaved until 全て保存)."""
    packs = E.forge_batch_presets()
    if not packs:
        return
    names = [f"{k} ({len(v)}枚)" for k, v in packs.items()]
    name, ok = QInputDialog.getItem(win, "Forgeパック生成", "パックプリセット:", names, 0, False)
    if not ok:
        return
    jobs = list(packs.values())[names.index(name)]
    existing = set(win.all_keys())
    made, skipped = [], []
    for data in jobs:
        key = f"{'item' if data.get('category') == 'item' else 'block'}/{data.get('name', 'texture')}"
        if key in existing:
            skipped.append(key)
            continue
        win.add_forge_doc(key, data)
        made.append(key)
    win.after_bulk()
    QMessageBox.information(win, "Forgeパック生成", f"{len(made)} 枚を作成しました（未保存: 内容を確認して「全て保存」）。\n"
                            + (f"既存のためスキップ: {', '.join(skipped)}" if skipped else ""))
