"""GB01 guide book background: downscale the ChatGPT sheet (inbox/textures/sheets/GB01-book.png) to 256x180.
Writes textures/gui/guide/book_bg.png and tools/texture_locks/assets/textures/gui/guide/book_bg.png.
Run:  python tools/guide_bg.py
"""
import os
import shutil

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SRC = os.path.join(ROOT, "inbox", "textures", "sheets", "GB01-book.png")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "abyssia", "textures", "gui", "guide", "book_bg.png")
LOCK = os.path.join(ROOT, "tools", "texture_locks", "assets", "textures", "gui", "guide", "book_bg.png")

img = Image.open(SRC).convert("RGB").resize((256, 180), Image.BOX)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.convert("RGBA").save(OUT)
os.makedirs(os.path.dirname(LOCK), exist_ok=True)
shutil.copyfile(OUT, LOCK)
print("wrote", OUT)
