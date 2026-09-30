"""Preferences: Mod ID, export options and the local AI connection."""
from __future__ import annotations

from PySide6.QtWidgets import (QComboBox, QDialog, QDialogButtonBox, QDoubleSpinBox, QFileDialog,
                               QFormLayout, QHBoxLayout, QLabel, QLineEdit, QPushButton, QTabWidget,
                               QVBoxLayout, QWidget)

from ai.ollama import PROVIDERS, LLMError, make_client
from app.worker import TaskRunner
from core.config import PACK_FORMATS, AIConfig, AppConfig
from export.resource_pack import TEXTURE_FOLDERS

DEFAULT_HOSTS = {"ollama": "http://127.0.0.1:11434", "llamacpp": "http://127.0.0.1:8080/v1",
                 "openai": "http://127.0.0.1:1234/v1", "none": ""}


class PreferencesDialog(QDialog):
    def __init__(self, cfg: AppConfig, runner: TaskRunner, parent: QWidget | None = None):
        super().__init__(parent)
        self.setWindowTitle("Preferences")
        self.setMinimumWidth(520)
        self.cfg = cfg
        self.runner = runner
        tabs = QTabWidget()
        tabs.addTab(self._general_tab(), "General")
        tabs.addTab(self._ai_tab(), "AI (Local LLM)")
        buttons = QDialogButtonBox(QDialogButtonBox.Ok | QDialogButtonBox.Cancel)
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)
        lay = QVBoxLayout(self)
        lay.addWidget(tabs)
        lay.addWidget(buttons)

    # ---------------------------------------------------------------- tabs
    def _general_tab(self) -> QWidget:
        w = QWidget()
        f = QFormLayout(w)
        self.mod_id = QLineEdit(self.cfg.mod_id)
        self.mod_id.setPlaceholderText("abyssalworld")
        self.texture_name = QLineEdit(self.cfg.texture_name)
        out_row = QHBoxLayout()
        self.output_dir = QLineEdit(self.cfg.output_dir)
        browse = QPushButton("…")
        browse.setFixedWidth(32)
        browse.clicked.connect(self._browse_output)
        out_row.addWidget(self.output_dir, 1)
        out_row.addWidget(browse)
        self.mc_version = QComboBox()
        for v, fmt in PACK_FORMATS.items():
            self.mc_version.addItem(f"{v}  (pack_format {fmt})", v)
        self.mc_version.setCurrentIndex(max(0, self.mc_version.findData(self.cfg.minecraft_version)))
        self.folder = QComboBox()
        self.folder.addItems(TEXTURE_FOLDERS)
        self.folder.setCurrentText(self.cfg.texture_folder)
        self.description = QLineEdit(self.cfg.pack_description)
        f.addRow("Mod ID", self.mod_id)
        f.addRow("Default texture name", self.texture_name)
        f.addRow("Output folder", out_row)
        f.addRow("Minecraft version", self.mc_version)
        f.addRow("Texture folder", self.folder)
        f.addRow("Pack description", self.description)
        return w

    def _ai_tab(self) -> QWidget:
        w = QWidget()
        f = QFormLayout(w)
        ai = self.cfg.ai
        self.provider = QComboBox()
        for k, v in PROVIDERS.items():
            self.provider.addItem(v, k)
        self.provider.setCurrentIndex(max(0, self.provider.findData(ai.provider)))
        self.host = QLineEdit(ai.host)
        model_row = QHBoxLayout()
        self.model = QComboBox()
        self.model.setEditable(True)
        self.model.addItem(ai.model)
        self.model.setCurrentText(ai.model)
        self.connect_btn = QPushButton("Connect")
        self.connect_btn.setToolTip("Fetch the list of installed models")
        model_row.addWidget(self.model, 1)
        model_row.addWidget(self.connect_btn)
        self.api_key = QLineEdit(ai.api_key)
        self.api_key.setEchoMode(QLineEdit.Password)
        self.api_key.setPlaceholderText("usually empty for local servers")
        self.timeout = QDoubleSpinBox()
        self.timeout.setRange(5, 600)
        self.timeout.setValue(ai.timeout)
        self.timeout.setSuffix(" s")
        self.test_btn = QPushButton("Test")
        self.status = QLabel("The LLM only turns text into parameters; images are always generated locally.")
        self.status.setWordWrap(True)
        self.status.setObjectName("Sub")
        f.addRow("Provider", self.provider)
        f.addRow("Host", self.host)
        f.addRow("Model", model_row)
        f.addRow("API key", self.api_key)
        f.addRow("Timeout", self.timeout)
        f.addRow("", self.test_btn)
        f.addRow(self.status)
        self.provider.currentIndexChanged.connect(self._provider_changed)
        self.connect_btn.clicked.connect(self._connect)
        self.test_btn.clicked.connect(self._test)
        return w

    # ------------------------------------------------------------- actions
    def _browse_output(self) -> None:
        d = QFileDialog.getExistingDirectory(self, "Output folder", self.output_dir.text())
        if d:
            self.output_dir.setText(d)

    def _provider_changed(self) -> None:
        prov = self.provider.currentData()
        if self.host.text().strip() in DEFAULT_HOSTS.values() or not self.host.text().strip():
            self.host.setText(DEFAULT_HOSTS.get(prov, ""))

    def _ai_config(self) -> AIConfig:
        return AIConfig(self.provider.currentData(), self.host.text().strip(), self.model.currentText().strip(),
                        self.api_key.text(), float(self.timeout.value()), self.cfg.ai.temperature)

    def _set_status(self, text: str, kind: str = "Sub") -> None:
        self.status.setObjectName(kind)
        self.status.setStyleSheet("")  # re-polish for the new object name
        self.status.style().unpolish(self.status)
        self.status.style().polish(self.status)
        self.status.setText(text)

    def _connect(self) -> None:
        client = make_client(self._ai_config())
        if client is None:
            self._set_status("AI is switched off.", "Warning")
            return
        self._set_status("Connecting…")

        def work(_p, _c):
            return client.list_models()

        def done(models):
            current = self.model.currentText()
            self.model.clear()
            self.model.addItems(models or [current])
            self.model.setCurrentText(current if current in models or not models else models[0])
            self._set_status(f"Connected · {len(models)} model(s) available", "Success")

        self.runner.submit("ai-prefs", work, done, lambda e: self._set_status(e.split("\n")[0], "Error"))

    def _test(self) -> None:
        client = make_client(self._ai_config())
        if client is None:
            self._set_status("AI is switched off – the keyword parser will be used.", "Warning")
            return
        self._set_status("Testing…")

        def work(_p, _c):
            ok, msg = client.test()
            if ok:
                try:
                    reply = client.chat('Reply with JSON {"ok": true}.', "ping")
                    msg += f" · model replied ({len(reply)} chars)"
                except LLMError as e:
                    return False, f"server reachable but chat failed: {e}"
            return ok, msg

        def done(res):
            ok, msg = res
            self._set_status(msg, "Success" if ok else "Error")

        self.runner.submit("ai-prefs", work, done, lambda e: self._set_status(e.split("\n")[0], "Error"))

    def apply(self) -> AppConfig:
        c = self.cfg
        from export.png import sanitize_name
        c.mod_id = sanitize_name(self.mod_id.text()).replace("/", "_") or "modid"
        c.texture_name = sanitize_name(self.texture_name.text())
        c.output_dir = self.output_dir.text().strip() or c.output_dir
        c.minecraft_version = self.mc_version.currentData()
        c.texture_folder = self.folder.currentText()
        c.pack_description = self.description.text()
        c.ai = self._ai_config()
        return c
