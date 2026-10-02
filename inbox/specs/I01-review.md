# I01 review (ChatGPT, 2026-10-02)

Inputs: request「工業ブロックの実装」, I01-industrial-blocks.md (+追補), inbox/designs/I01.png, in-game shots inbox/designs/I01-ingame-scene2.png / I01-ingame-gui2.png.

Model review (Blockbench, 2 rounds): round 1 = valve was a red box, machines too cube-like → revised parts; round 2 = machines pass, valve redesigned as an inline pipe segment with a top handwheel. Then the user switched machines/generators/energy_device to plain cube blocks (models kept only for pipe / valve / cables / lights).

In-game review: 建材・機械7種・ケーブル・水中設置・永続性すべて合格。指摘 P2「SUPERHEATED ×2.0 の確認」→ Claude が実測 160 FE/t (16,000 FE / 100 tick) で確認済み → 合格。

Claude-side verification (scratch server via RCON + AutoShot client):
- generator 0/40/80/120/160 FE/t by vent activity; auxiliary coal 2000 t burn
- crusher raw→powder ×3 (abyssia + vanilla raw_iron), refinery concentrate→ingot / powder→ingot, alloy ×3 (conductive 2 inputs), high temp tungsten (stops > 6 blocks from a live vent)
- cable network aux gen → alloy furnace, surplus → energy device; underwater (waterlogged) cables/machines; chunk unload/reload; /reload
- GUIs: 1,000,000 FE readout OK; thermal_vent drops nothing; /fill clears contents, breaking drops block + contents
Bugs found and fixed during testing: shaped recipes with unused key symbols (7 machine recipes failed to load; check_recipes now flags this), generator pushed all energy to the first neighbour (now even split), machines ignored inventories replaced by load()/data merge (recipe re-check on load), contents spilled on /fill (Clearable).

## Machine textures v2 (2026-10-02, user request: redo for plain cube blocks)
ChatGPT redrew the 7 machines as cube blocks with one shared casing (same frame / corner bolts / light direction), identity in a big front mechanism + one accent colour, calm sides, role-revealing tops, front_on = same pixels with brighter emissive ones, plus a shared machine_bottom. Sheets IND2A / IND2B (square cells) replace IND2 (v1 kept as inbox/textures/sheets/IND2.png, files backed up under inbox/backup/gui-import-*). In-game shots inbox/designs/I01-ingame-scene3.png → ChatGPT: 合格.

## GUI + cable restyle (2026-10-02, user: GUI to fit the theme, cables looked bad)
- Cables: v1 put a thick bright cyan line in the centre of the 16px texture; the 4/6px cable models showed only that → glowing bars. ChatGPT supplied pixel parts (strip / hub / ring, tools/industrial_models/cable_parts.json); cable_textures.py assembles them into a cross (side faces = horizontal band, top/bottom = transposed band, core = hub), ring band element maps to the 4x4 ring swatch.
- GUI: ChatGPT mockup inbox/designs/I01-gui-mockup.png + colour sheet inbox/specs/I01-gui-spec.md → tools/industrial_gui.py (gunmetal console, riveted plates, cyan side strips, recessed title bar, dark slots, tube energy bar); IndustryScreen text colours.
- In-game: inbox/designs/I01-ingame-gui3.png, I01-ingame-cable2.png → ChatGPT: 合格.
