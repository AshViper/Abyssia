"""Natural-language prompt → generation parameters (via local LLM or keywords)."""
from __future__ import annotations

import json

from PySide6.QtCore import Signal
from PySide6.QtWidgets import (QCheckBox, QDialog, QHBoxLayout, QLabel, QPlainTextEdit, QPushButton,
                               QVBoxLayout, QWidget)

from ai.ollama import PROVIDERS, make_client
from ai.prompt_parser import ParseResult, PromptParser
from app.worker import TaskRunner
from core.config import AppConfig
from core.settings import TextureSettings

EXAMPLE = ("深海の熱水噴出孔周辺にある黒い岩。\n硫黄が少し付着していて、\n"
           "ところどころ青緑色の鉱物が露出している。\nMinecraft風16×16。")


class AIPromptDialog(QDialog):
    """Emits ``applied(settings, generate)`` when the user accepts the parameters."""
    applied = Signal(object, bool)

    def __init__(self, cfg: AppConfig, runner: TaskRunner, base: TextureSettings,
                 parent: QWidget | None = None):
        super().__init__(parent)
        self.setWindowTitle("AI Prompt → Parameters")
        self.resize(640, 560)
        self.cfg = cfg
        self.runner = runner
        self.base = base
        self.result: ParseResult | None = None

        lay = QVBoxLayout(self)
        prov = PROVIDERS.get(cfg.ai.provider, cfg.ai.provider)
        info = QLabel(f"Provider: {prov} · {cfg.ai.model if cfg.ai.provider != 'none' else ''}"
                      "  —  the model only produces parameters; pixels are generated locally.")
        info.setObjectName("Sub")
        info.setWordWrap(True)
        lay.addWidget(info)
        lay.addWidget(QLabel("Describe the texture"))
        self.prompt = QPlainTextEdit()
        self.prompt.setPlaceholderText(EXAMPLE)
        self.prompt.setPlainText("")
        self.prompt.setFixedHeight(110)
        lay.addWidget(self.prompt)
        row = QHBoxLayout()
        self.keywords_only = QCheckBox("Keyword parser only (offline)")
        self.keywords_only.setChecked(cfg.ai.provider == "none")
        self.from_defaults = QCheckBox("Start from defaults (keep seed && size)")
        self.from_defaults.setChecked(True)
        self.from_defaults.setToolTip("Unchecked: the AI only changes what it mentions and keeps your current sliders")
        self.interpret_btn = QPushButton("Interpret")
        self.interpret_btn.setObjectName("Primary")
        row.addWidget(self.keywords_only)
        row.addWidget(self.from_defaults)
        row.addStretch(1)
        row.addWidget(self.interpret_btn)
        lay.addLayout(row)
        lay.addWidget(QLabel("Parameters"))
        self.output = QPlainTextEdit()
        self.output.setReadOnly(True)
        lay.addWidget(self.output, 1)
        self.status = QLabel("")
        self.status.setObjectName("Sub")
        self.status.setWordWrap(True)
        lay.addWidget(self.status)
        buttons = QHBoxLayout()
        self.apply_btn = QPushButton("Apply")
        self.apply_gen_btn = QPushButton("Apply && Generate")
        self.apply_gen_btn.setObjectName("Primary")
        close = QPushButton("Close")
        for b in (self.apply_btn, self.apply_gen_btn):
            b.setEnabled(False)
        buttons.addStretch(1)
        buttons.addWidget(close)
        buttons.addWidget(self.apply_btn)
        buttons.addWidget(self.apply_gen_btn)
        lay.addLayout(buttons)

        self.interpret_btn.clicked.connect(self.interpret)
        self.apply_btn.clicked.connect(lambda: self._apply(False))
        self.apply_gen_btn.clicked.connect(lambda: self._apply(True))
        close.clicked.connect(self.reject)

    def interpret(self) -> None:
        text = self.prompt.toPlainText().strip() or EXAMPLE
        if not self.prompt.toPlainText().strip():
            self.prompt.setPlainText(EXAMPLE)
        client = None if self.keywords_only.isChecked() else make_client(self.cfg.ai)
        parser = PromptParser(client)
        cur = self.base
        base = (TextureSettings(seed=cur.seed, size=cur.size, name=cur.name, source_influence=cur.source_influence)
                if self.from_defaults.isChecked() else cur.copy())
        self.interpret_btn.setEnabled(False)
        self.status.setText("Asking the model…" if client else "Parsing keywords…")

        def work(_p, _c):
            return parser.parse(text, base)

        self.runner.submit("ai", work, self._done, self._failed)

    def _done(self, res: ParseResult) -> None:
        self.interpret_btn.setEnabled(True)
        self.result = res
        body = {"source": res.source, "parameters": res.mapped.applied}
        if res.params and res.params != res.mapped.applied:
            body["raw_model_output"] = res.params
        self.output.setPlainText(json.dumps(body, indent=2, ensure_ascii=False))
        notes = list(res.notes)
        self.status.setText(("; ".join(notes)) if notes else
                            f"{len(res.mapped.applied)} parameter(s) from "
                            f"{'the LLM' if res.source == 'llm' else 'the keyword parser'}.")
        for b in (self.apply_btn, self.apply_gen_btn):
            b.setEnabled(True)

    def _failed(self, msg: str) -> None:
        self.interpret_btn.setEnabled(True)
        self.status.setText(msg.split("\n")[0])

    def _apply(self, generate: bool) -> None:
        if self.result is None:
            return
        s = self.result.settings.copy()
        if "name" not in self.result.mapped.applied:
            s.name = suggest_name(s)
        self.applied.emit(s, generate)
        self.accept()


def suggest_name(s: TextureSettings) -> str:
    """A texture name from the interpreted parameters, e.g. ``sulfur_rock`` or ``abyss_rock``."""
    from export.png import sanitize_name
    if s.variant and s.variant not in ("auto", "cluster", "single"):
        name = s.variant if s.category in s.variant or "_" in s.variant else f"{s.variant}_{s.category}"
    else:
        noun = s.material if s.category == "terrain" else s.category
        name = f"{s.palette}_{noun}"
    return sanitize_name(name)
