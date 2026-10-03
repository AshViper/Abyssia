# PLR 植物テクスチャ総作り直し (ChatGPT ImageGen 用)

ユーザー依頼 2026-10-03「実装中の植物(planter_* / oil_kelp_*)以外のすべての植物のテクスチャを作り直す」。
12 シート (PLR1..PLR12、各 9 個以内)。ペア (下半身+_top / _tip、通常+_ripe) は必ず同じシートの隣り合う 2 セル。
取込は `python tools/agentflow/sheets.py import auto --preset PLRn` (後処理は PLR-postprocess.md)。
Claude は画像を生成しない。1 シート 1 枚の画像を生成して `inbox/textures/sheets/PLRn.png` に保存。

## STYLE-SPRITE 共通 (十字モデルの植物スプライト)

```
Minecraft 1.20.1 plant sprites in the exact style of vanilla Minecraft plant textures (seagrass, kelp, fern, tube coral, crimson roots, glow lichen): hand-drawn 16x16 pixel art, drawn large with chunky square pixels, every sprite is ONE tile in its own square cell. Flat solid magenta (#FF00FF) background that never appears inside a sprite, wide magenta gutters between cells, no cell borders, no labels, no text, no watermark.
Each sprite uses a limited palette of 5-8 colours: a dark base shade, 2-3 mid tones, 1 light highlight, and shading done by clear colour steps (no outline needed, but the silhouette must read clearly against the background). Hard pixel edges, no anti-aliasing, no gradients, no blur, no dithering noise, no drop shadow, no perspective, no 3D render, no ground, no soil. Readable silhouette at 16px: avoid blobs and speckle; use clean 1-2px wide shapes with sparse, slightly asymmetric detail.
Deep-sea cave palette: dark teal, deep green, indigo, violet, rust, amber. Bright bioluminescent accents (cyan, pale green, white, yellow) ONLY on the parts described as glowing; keep everything else dark and matte.
The plant fills the tile height: floor plants grow from the bottom edge of the tile, hanging plants hang from the top edge of the tile. The sprite must not be cropped by the cell. Two items marked as a PAIR are drawn on the same grid scale and must line up exactly at the seam described.
```

## STYLE-FLAT 共通 (不透明の面)

```
Minecraft 1.20.1 block-face textures in the exact style of vanilla Minecraft (moss block, kelp block, dried kelp, leaves): hand-drawn 16x16 pixel art, drawn large with chunky square pixels, each texture is ONE fully opaque square tile filling its whole cell edge to edge, seamlessly tileable on all four edges, in its own square cell. Flat solid magenta (#FF00FF) between cells with wide gutters; no cell borders, no labels, no text, no watermark.
Limited palette of 5-8 colours per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no dithering noise, no drop shadow, no perspective, no 3D render, no frame, no scene. Calm large areas with sparse irregular detail, slightly asymmetric. Deep-sea cave palette: dark teal, deep green, indigo, rust, amber; bright accents only where described.
```

## STYLE-WALL 共通 (壁に貼る透過パッチ)

```
Minecraft 1.20.1 wall-overlay plant textures in the style of vanilla Minecraft vines, glow lichen and moss carpets: hand-drawn 16x16 pixel art, drawn large with chunky square pixels, each texture is ONE tile in its own square cell: a flat front view of plant growth spread over a wall, with transparent gaps (shown as flat magenta #FF00FF that never appears in the art) between clumps, and the growth reaching all four edges of the tile so the sprite is not cropped smaller than the cell. Wide magenta gutters between cells; no cell borders, no labels, no text, no watermark.
Limited palette of 5-8 colours per tile, hard pixel edges, no anti-aliasing, no gradients, no blur, no dithering noise, no drop shadow, no perspective, no 3D render, no wall or stone behind the plant. Readable silhouette at 16px, sparse slightly asymmetric detail. Deep-sea cave palette: dark teal, deep green, indigo, rust, amber; bright bioluminescent accents only where described.
```

## abyssal_bloom
Floor plant, glowing. Low flower ~12px tall: 5 rounded deep-navy-blue petals with lighter blue rims cupped around a bright cyan-white core (the core is the glow), short dark teal stem with one small leaf. Compact, nearly symmetric.

## abyssal_mushroom
Floor plant, glowing. One dome-cap mushroom ~13px tall: indigo-violet cap 10px wide with 2-3 pale cyan glowing spots under a lighter rim, thin pale grey-lilac stalk, tiny second sprout beside it.

## cave_bloom
Floor plant, glowing. Three slender dark-teal stems of different heights each ending in a round pale-cyan bulb (the bulbs glow, white highlight pixel on each), a couple of tiny leaves at the base.

## floating_bloom
Floor plant, glowing. Small buoyant flower: violet-blue petals around a glowing cyan-white centre on a thin stem topped by a round float bulb, two thin dangling leaves left and right, airy and light.

## glow_anemone
Floor plant, glowing. Sea anemone: dense fan of 8-10 thin tentacles radiating up from a short dark blue base, tentacles shade from mid blue to pale glowing cyan-white tips.

## hadal_bloom
Floor plant, glowing. Dark indigo cup flower with 4 pointed petals opening upward around a violet-magenta glowing core, short thick stem with two small pointed leaves. The darkest, most mysterious flower.

## glowtip_grass
Floor plant, glowing. Tuft of 5-6 thin dark teal blades of varying height, each blade ends in a single bright cyan-green glowing dot (the tips glow, the blades stay dark).

## glow_coral
Floor plant, glowing. Fan coral: flat branching fan of teal-cyan twigs forking 2-3 times, white-cyan glowing tips, dark blue-teal base, symmetrical fan outline about 13px tall.

## soul_coral
Floor plant, glowing. Ghostly coral: 3-4 tall pale lavender-white spires of different heights with small pale-green glowing dots near the tips, dark slate-violet base; cold and ethereal.

## cave_coral
Floor plant, matte. Branching coral in red-orange: three forked branches with darker red shading on one side and light salmon tips, short thick trunk at the bottom.

## black_coral
Floor plant, matte. Very dark charcoal-blue branching coral with thin forked twigs and a few grey-blue highlight pixels; delicate tree-like silhouette ~14px tall.
FIX: in dark water the dark tones merge — raise the base one step brighter and add 2-3px violet-blue highlights on the branch tips so the outline reads at 16x16.

## cave_fern
Floor plant, matte. One curved fern frond rising from the bottom and drooping at the tip, dark green with lighter leaflet pairs along the rib.

## sea_fern
Floor plant, matte. Upright feather-like sea fern: teal-blue central rib with 6 pairs of short leaflets angled upward, lighter leaflet tips, narrowing at the top.

## cave_sponge
Floor plant, matte. Cluster of 3 tube sponges of different heights in warm orange-ochre with darker orange shading and dark openings at the tops, pale rim pixels.
FIX: the three lobes looked like separate balls — join them at one common base so it reads as a single clump, lobes a little closer together.

## sponge_plant
Floor plant, matte. Cluster of 3 rounded lobes of dusty slate-blue sponge with lilac-pink pores on the tops and darker blue shading on one side.

## cinder_stalk
Floor plant, matte (no glow layer). Three charred near-black brown stalks of different heights with orange-red ember cracks and smoky grey tips, split like burnt reeds.

## thermal_plant
Floor plant, matte body with a faint glow on the warm veins. Heat-loving plant: copper-brown stems with two broad lobed leaves in rust orange and warm amber flecks along the veins, dark brown base.

## vent_grass
Floor plant, matte. Tuft of stiff pale grey-green blades with rust-orange sulphur tips, a few blades bent, hardy look.

## void_kelp
PAIR with the next item (void_kelp_plant is the stem below it). Floor kelp HEAD: starts at the bottom edge with the same stem width as the next item, then splits into thin forked fronds ending in points near the top of the tile. Near-black violet-black kelp with deep purple highlights.

## void_kelp_plant
PAIR with the previous item. Kelp STEM segment spanning the full tile height: the stem touches the top edge and the bottom edge at the same x position as the previous item's bottom, small leaf pairs on both sides. Same near-black violet palette.

## ancient_cave_plant
Opaque bark of the thick trunk of an ancient cave tube plant (the block is a 12x16 trunk box, this texture covers its sides and top): fills the WHOLE tile, vertical fibrous pale brown-green bark strands with 2 knotted horizontal growth rings, moss-green shading in the grooves, no transparency. It sits under the crown sprite ancient_cave_plant_top, so the colours match its stem.

## ancient_cave_plant_top
PAIR with the previous item. Crown: the same stem at the bottom edge (same width and x position), spreading at the top into a cluster of broad dark-green frond leaves.

## silt_comb
Floor plant, matte. Comb-shaped silt formation: flat fan of 5 vertical grey-beige ridges of different heights, slightly darker bottom, pale highlight on each ridge.
FIX: branches were too fine and dense (reads as noise) — make the central axis thicker and use fewer, clearer branches.

## ancient_sapling
Floor plant, matte. Young sapling: thin brown trunk with 2 small branches carrying 4-5 small dark-green leaves with lighter edges, one tiny pale-cyan bud; ~14px tall.

## crystal_plant
Floor plant, glowing. Small crystal-sprout plant: 3 pointed faceted crystal buds in teal-cyan of different heights rising from a few dark green leaves, light cyan facet highlight on each bud (buds glow).

## cave_crystal_plant
Floor plant, glowing. Cave variant: a single taller crystal bud in pale cyan-white with a bright tip (glows), flanked by two curved dark teal-green fern-like leaves.

## amber_fan
PAIR with the next item (same silhouette). Sea fan: flat fan of golden amber-orange branches forking 2-3 times, dark brown-orange base, ~13px tall, no fruit yet, small closed buds at the tips.

## amber_fan_ripe
PAIR with the previous item: identical fan silhouette and branch layout, but with 5-6 plump round glossy bright yellow-amber fruit beads at the branch tips.

## glasslace
PAIR with the next item (same silhouette). Delicate lace-like bush: thin pale cyan-teal branching lines forming an open web with gaps, dark teal base stem, airy, ~13px tall, no fruit.

## glasslace_ripe
PAIR with the previous item: identical web silhouette, with 4-5 bright cyan-white pearl-like berries sitting in the mesh (the berries glow).

## lumen_quill
PAIR with the next item (same silhouette). Quill plant: 3 stalks of dark blue stems carrying slender feather-like pale blue-green leaf blades, tips dull blue-grey.

## lumen_quill_ripe
PAIR with the previous item: identical shape, but every quill tip carries a bright cyan-white glowing bead (the beads glow).

## oil_bladder_weed
PAIR with the next item (same silhouette). Olive-brown strand weed with 5-6 small round gas bladders along the stems, dull olive-green, ~14px tall.

## oil_bladder_weed_ripe
PAIR with the previous item: identical shape, but the bladders are swollen larger and translucent amber-orange (oil-filled) with a light highlight pixel each.

## pressure_gourd
PAIR with the next item (same vine and leaves). Low vine with 2 leaves and one small round ridged blue-grey gourd on a short curl of stem.

## pressure_gourd_ripe
PAIR with the previous item: identical vine and leaves, but the gourd is larger with ochre-orange skin and dark ridges.

## strandweed
PAIR with the next item (same strands). Tuft of thin olive yellow-green strands of different heights, gently curved, thin.

## strandweed_ripe
PAIR with the previous item: identical strands, but small pale pink-white seed pods sit along the strands at 4-5 points.

## knotstalk
PAIR with the next item (knotstalk_top continues it upward). Stalk segment spanning the full tile height touching the top and bottom edges at the same x position: dark olive stalk 4px wide with 2-3 swollen knots, two short side shoots.

## knotstalk_top
PAIR with the previous item. Starts at the bottom edge with the same stalk width and x position, one knot, then ends in a small bud and 2-3 little leaves at the top.

## mineral_vine
PAIR with the next item (mineral_vine_top continues it upward). Climbing vine segment spanning the full tile height touching the top and bottom edges at the same x position: rust-orange vine with small mineral crust nodules in lighter tan.
FIX: the stem got too thin at the top/bottom edges — keep the stem the same width (3px) at both edges so stacked blocks join.

## mineral_vine_top
PAIR with the previous item. Starts at the bottom edge with the same vine width and x position and ends in a small sprout bud with two tiny leaves at the top.
FIX: bottom edge stem must be exactly the same width and x position as mineral_vine.

## abyssal_grass
PAIR with the next item (abyssal_grass_top is the upper half). LOWER half of tall grass: 5-6 vertical blades of deep teal-blue with a lighter cyan ridge, blades run from the bottom edge to the TOP edge of the tile (cut straight at the top edge, same x positions as the bottom of the next item).

## abyssal_grass_top
PAIR with the previous item. UPPER half: the same blades start at the bottom edge at the same x positions and end in pointed tips of different heights near the top.

## ashen_abyssal_grass
PAIR with the next item. LOWER half of tall grass, 5-6 vertical blades of grey-green with ash-white ridge, running from the bottom edge to the TOP edge (cut straight at the top).

## ashen_abyssal_grass_top
PAIR with the previous item. UPPER half: same blades continuing from the bottom edge at the same x positions, ending in ash-white pointed tips.

## teal_abyssal_grass
PAIR with the next item. LOWER half of tall grass, 5-6 vertical blades of bright teal-green with a lighter ridge, from the bottom edge to the TOP edge (cut straight at the top).

## teal_abyssal_grass_top
PAIR with the previous item. UPPER half: the same blades continuing from the bottom edge at the same x positions, ending in pointed tips.

## violet_abyssal_grass
PAIR with the next item. LOWER half of tall grass, 5-6 vertical blades of blue-violet with a lighter lilac ridge, from the bottom edge to the TOP edge (cut straight at the top).

## violet_abyssal_grass_top
PAIR with the previous item. UPPER half: the same blades continuing from the bottom edge at the same x positions, ending in pointed lilac tips.

## deep_kelp
PAIR with the next item. Kelp STEM spanning the full tile height touching the top and bottom edges: olive-green stem 3px wide with wavy leaf blades alternating on both sides, darker green shading.

## deep_kelp_top
PAIR with the previous item. Starts at the bottom edge with the same stem width and x position, continues up and ends in a single frond cluster with a free pointed tip.

## giant_kelp
PAIR with the next item. Thick dark green-brown kelp STEM spanning the full tile height touching the top and bottom edges, stem 4px wide with large wide leaf blades on both sides, lighter ribs.

## giant_kelp_top
PAIR with the previous item. Starts at the bottom edge with the same stem width and x position, ends in a crown of wide leaves with a free tip.

## tube_plant
PAIR with the next item. Two or three vertical tubular stalks spanning the full tile height touching the top and bottom edges: rosy maroon-pink tubes with a lighter highlight stripe and darker ring bands.

## tube_plant_top
PAIR with the previous item. Same tubes starting at the bottom edge at the same x positions, ending in open tube mouths with pale pink rims and dark insides near the top.

## cave_tube_plant
PAIR with the next item. Two or three vertical ringed tubes spanning the full tile height touching the top and bottom edges: purple-violet with segmented ring bands and a lilac highlight stripe.

## cave_tube_plant_top
PAIR with the previous item. Same tubes starting at the bottom edge at the same x positions, ending in flared open mouths with pale lilac rims.

## thermal_tube
PAIR with the next item. Two vertical tubes spanning the full tile height touching the top and bottom edges: copper-orange with sooty dark ring bands.

## thermal_tube_top
PAIR with the previous item. Same tubes starting at the bottom edge at the same x positions, ending in open mouths with a warm amber-yellow glowing inside and sooty rims (the inside glows).

## crystal_kelp
PAIR with the next item. Crystalline kelp STEM spanning the full tile height touching the top and bottom edges: faceted teal stem with pale cyan facets and small crystal leaves on both sides.

## crystal_kelp_top
PAIR with the previous item. Starts at the bottom edge with the same stem width and x position and ends in a bright cyan-white crystal bulb at the top (the bulb glows).

## resin_root
PAIR with the next item. HANGING root: strands hang from the TOP edge of the tile and run to the bottom edge, 2-3 amber-brown roots with small resin droplets in golden yellow.

## resin_root_tip
PAIR with the previous item. HANGING end piece: starts at the top edge at the same x positions, strands narrow downward and end at the bottom of the tile in a tapering tip with one golden resin drop.

## cave_vine
PAIR with the next item. HANGING vine: 3 green vine strands hang from the TOP edge and run to the bottom edge, small leaves along them, dark green with light green highlights.

## cave_vine_tip
PAIR with the previous item. HANGING end piece: strands start at the top edge at the same x positions and end at the bottom in small bright yellow-green berry bulbs (the bulbs glow).

## cave_root
PAIR with the next item. HANGING root: 3 thin brown roots hang from the TOP edge to the bottom edge, slightly tangled, darker brown shading with tan highlights.

## cave_root_tip
PAIR with the previous item. HANGING end piece: roots start at the top edge at the same x positions, become thinner and end in tapering points near the bottom.

## abyssal_vine
PAIR with the next item. HANGING vine: 2 twisting purple strands hang from the TOP edge to the bottom edge, small pale-violet leaves.

## abyssal_vine_tip
PAIR with the previous item. HANGING end piece: strands start at the top edge at the same x positions and end at the bottom in glowing violet-white buds (the buds glow).

## deep_root
PAIR with the next item. HANGING root: 2 thick dark-brown roots with knobs hang from the TOP edge to the bottom edge, tan highlights.
FIX: keep the main root 3px wide at the bottom edge (same as the tip top).

## deep_root_tip
PAIR with the previous item. HANGING end piece: roots start at the top edge at the same x positions, taper and end in points near the bottom.
FIX: the central root at the TOP edge was narrower than deep_root — make the top 2-3px exactly the same width and x position as deep_root's bottom, then taper toward the tip.

## cave_grass
ANIMATED later (draw one still frame; the game will sway it, so keep every blade within 2px of its base line). PAIR with the next item. LOWER half of tall grass: 5-6 pale green-teal blades from the bottom edge to the TOP edge (cut straight at the top).

## cave_grass_top
ANIMATED later. PAIR with the previous item. UPPER half: same blades from the bottom edge at the same x positions, ending in pointed tips.

## cave_kelp
ANIMATED later (one still frame). PAIR with the next item. Vivid green kelp STEM spanning the full tile height touching the top and bottom edges, wavy leaf blades on both sides.

## cave_kelp_top
ANIMATED later. PAIR with the previous item. Starts at the bottom edge with the same stem width and x position, ends in a frond tuft with a free tip.

## giant_cave_kelp
ANIMATED later (one still frame). PAIR with the next item. Thick blue-green kelp STEM spanning the full tile height touching the top and bottom edges, 4px wide, broad leaf blades.

## giant_cave_kelp_top
ANIMATED later. PAIR with the previous item. Starts at the bottom edge with the same stem width and x position, ends in a crown of broad leaves.

## hanging_kelp
ANIMATED later (one still frame). PAIR with the next item. HANGING kelp: 2-3 olive-green ribbon strands hang from the TOP edge to the bottom edge, with small leaf frills.

## hanging_kelp_tip
ANIMATED later. PAIR with the previous item. HANGING end piece: strands start at the top edge at the same x positions and end in frayed pointed tips near the bottom.

## abyssal_moss
Opaque seamless top-down moss mat: dense dark teal-green moss with small tufts in 3 greens and a few lighter flecks, calm and even.

## fallen_kelp
Opaque seamless top-down view of fallen kelp lying on the seabed: several wavy olive-brown and dark green blades overlapping diagonally, lighter midribs.

## heat_moss
Opaque seamless moss mat in dark red-brown with scattered small orange ember flecks and a few rust patches.

## ancient_frond
Opaque seamless pattern of overlapping broad dark-green fronds seen from above, each with a lighter central vein and darker edge shade.

## giant_tube
PAIR with the next item. Opaque seamless side face of a giant vertical tube: pale teal-violet body with vertical ribs and two horizontal ring bands, repeats vertically.

## giant_tube_top
PAIR with the previous item. Opaque top cap of the same giant tube seen from above: same teal-violet rim colour with a dark round opening in the middle and a lighter ring around it.

## cave_moss
Wall patch (transparent gaps): irregular clumps of dark green moss covering about 60% of the tile, three greens, lighter tufted edges, reaches all four tile edges.

## luminous_moss
Wall patch (transparent gaps): similar moss clumps in dark teal, with 6-8 small bioluminescent cyan-white spots scattered in them (the spots glow), reaches all four tile edges.

## wall_fern
Wall patch (transparent gaps): 2-3 small ferns radiating up and out from near the bottom centre, dark green fronds with lighter leaflets, reaching all four tile edges.

## wall_mineral_vine
Wall patch (transparent gaps): thin rust-orange vine lines branching across the tile with small tan mineral nodules, reaching all four tile edges.
FIX: strong red-orange blobs made the repeat obvious — spread the bright spots, no hard colour change at the left/right/top/bottom edges so tiles join seamlessly.
