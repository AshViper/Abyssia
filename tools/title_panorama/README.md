# title_panorama — T02 deep-sea title screen panorama

Output: `src/main/resources/assets/abyssia/textures/gui/title/background/panorama_0..5.png` (used by `client/title/AbyssPanorama`).

1. `python gen_scene.py <out.txt>` — scene build commands (water box + floor, 3 vent chimneys, glow plants, kelp, ruins, industrial platform, far tower/arch, NoAI creatures) around (0, -201, 0); camera at (0.5, -188, 0.5).
2. Scratch copy only (never in the project): the AutoShot harness (see vault solutions/autoshot-screenshot-harness). Commands file as `run/t02_scene.txt` (`cmd<TAB>waitTicks` per line); the harness reference with the panorama steps is `AutoShot.title.java.txt`. In the harness config set `enable_fog = false`, no night vision.
   - Capture = square 960x960 window, fov 90, fovEffectScale 0, gamma 1.0, `tp` to the six faces (yaw, yaw+90, yaw+180, yaw-90, pitch -90, pitch 90) + plain screenshots. `Minecraft#grabPanoramixScreenshot` gives blank faces with Embeddium/Oculus (graphicsChanged drops all chunk meshes), don't use it.
3. `python grade.py <screenshots dir> <out dir>` — darkens to the deep-sea look (keeps bright emissives, darker toward the top, up face near black), resizes to 1024. Copy to the assets folder.
