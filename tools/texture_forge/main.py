"""Texture Forge – Minecraft-style original texture generator.

    python main.py [reference.png]
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))


def main() -> int:
    from PySide6.QtWidgets import QApplication

    from app.theme import apply_theme
    from app.window import MainWindow

    app = QApplication(sys.argv)
    app.setApplicationName("Texture Forge")
    app.setOrganizationName("Abyssia")
    apply_theme(app)
    source = sys.argv[1] if len(sys.argv) > 1 and Path(sys.argv[1]).exists() else None
    win = MainWindow(source)
    win.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
