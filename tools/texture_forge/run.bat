@echo off
rem Texture Forge launcher (Windows). Pass a reference texture to open it directly:
rem   run.bat path\to\stone.png
cd /d "%~dp0"
python -c "import PySide6, numpy, PIL" 2>nul || (
    echo Installing requirements...
    python -m pip install -r requirements.txt || exit /b 1
)
start "" pythonw main.py %*
