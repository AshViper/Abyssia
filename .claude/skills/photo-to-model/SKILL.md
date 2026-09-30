---
name: photo-to-model
description: Build an Abyssia mob model (.bbmodel + Forge Java) from a creature photo by writing a "parts" definition for tools/bbmodel-generator, then generating and visually checking it against the photo. Use when the user pastes/attaches a creature image and wants a model from it.
---

# Photo → parts model

You (Claude Code) are the "AI" of the generator: read the photo, decompose the creature into parts,
write `tools/bbmodel-generator/definitions/<id>.json` with `"template": "parts"`, generate, compare, refine.
No API calls. Spec: `tools/bbmodel-generator/README.md` section "parts Template（AI が部位を組む）" (read only that section).
Example: `definitions/stalked_sea_squirt_ai.json`.

## Steps

1. **Measure the photo.** Pick one individual. Estimate its total height (or length) in pixels and choose a
   model size: small sessile animals 16–32 units tall, fish 16–40 long (16 units = 1 block). Derive px-per-unit
   and convert each part's width / height / length. Note colours as `#rrggbb` (the photo's, slightly lifted for
   dark deep-sea shots).
2. **Decompose.** List parts from the root outward: body or base first (no parent), then children with `parent` +
   `at` (`tip` for things on the end of a stalk or body chain). Choose shapes: bulbs and caps → `profile`, `dome` or
   `ellipsoid`; stalks, tentacles and arms → `cylinder` with `segments` 2–4 (so they animate); fins, veils,
   frills, teeth and hair → `plane` (one size is 0, with an `outline`). Model plane rule: never use thin boxes
   for those. Use `mirror` for pairs and `count` + `arrange: ring` for radial repeats.
   `orientation`: upright for sessile or bell-shaped animals, horizontal for swimmers.
3. **Write and generate.**
   ```bash
   cd tools/bbmodel-generator && node cli.js <id>
   ```
   Fix every error it prints (errors name the part).
4. **Compare visually.** Open the GUI preview (launch config `bbmodel-generator`), select the creature, check
   the Front / Right views against the photo: proportions, where parts attach, and colours. Adjust the sizes,
   profile values, `at` or `offset`, and regenerate. 2–3 rounds is usually enough.
5. **Finish.** Run `npm test` in `tools/bbmodel-generator`. Report the file, the cube and bone counts, and what
   was approximated. Syncing into the mod goes through `gen_fauna.py` (see the vault note fauna-system); only do
   that when the user asks.

## Tips

- If a part's size quantises to 1–2 units it disappears at low detail; keep important parts ≥ 2 units.
- A widest-near-the-bottom cap is `profile: [0.7, 1, 1, 0.95, 0.85, 0.7, 0.45]`. A bell is `dome` with
  tentacles `at: bottom`.
- Translucent animals: use light, desaturated colours. Avoid the `dome` material (its alpha needs a translucent
  render type in the mod).
- For a pixel-exact silhouette instead of an interpretation, use Import Image (`photo` template).
