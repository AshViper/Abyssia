@echo off
rem Abyssia Texture Studio launcher.  Extra args run the CLI instead (e.g. run.bat list).
cd /d "%~dp0"
python -c "import PySide6, numpy, PIL" 2>nul || (
    echo Installing requirements...
    python -m pip install PySide6 numpy Pillow || exit /b 1
)
if "%~1"=="" (start "" pythonw studio.py) else (python studio.py %*)
