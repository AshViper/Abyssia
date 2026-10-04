SUB04 dock model: parts JSON + 6 tiles are the source of truth; submarine_dock.bbmodel is generated (open it in Blockbench to tweak).
Regenerate (repo root; re-bakes DockMesh.java + the atlas, drops the .anim.json sidecar):

    python tools/anim_model.py parts tools/vehicle_models/dock/submarine_dock.parts.json --tiles tools/vehicle_models/dock/tiles --bbmodel tools/vehicle_models/dock/submarine_dock.bbmodel --java src/main/java/com/abyssia/vehicle/client/DockMesh.java --package com.abyssia.vehicle.client --name Dock --texture src/main/resources/assets/abyssia/textures/entity/submarine_dock.png && rm -f src/main/java/com/abyssia/vehicle/client/DockMesh.anim.json

After tweaking the .bbmodel in Blockbench use the `bbmodel` mode instead of `parts` (same --java/--package/--name/--texture).
DockRenderer.LIGHT_* assume the atlas layout: 3 columns, sorted tile names, dock_light = tile 4.

Tiles (tiles/dock_*.png, 16x16, grating/rail have alpha-0 holes -> cutout render type) are produced by tools/dock_tiles.py
(outputs inbox/textures/dock_*.png); after rerunning it, copy them into tiles/ and regenerate as above.
