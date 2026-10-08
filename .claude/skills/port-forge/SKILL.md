---
name: port-forge
description: Port a finished NeoForge 1.21.1 feature to Forge 1.20.1 (main). Use after a feature builds and is tested on the NeoForge worktree.
---

Base = NeoForge `F:\Java\Abyssia-NeoForge`; target = Forge `F:\Java\Abyssia` (main). Same packages, class names, modid.
Port one feature at a time, with absolute paths, from the files the feature changed (`git -C <neo> status --short`).

1. **Data** (recipes, loot, tags, models): `python tools/mc_format.py --to-forge F:/Java/Abyssia --dry-run` in the NeoForge tree, then without `--dry-run`. `errors` = hand port; `unconverted_diffs` (worldgen, dimension types) = hand port. Never edit generated data by hand: change the generator and rerun.
2. **Java**: copy the changed files, then apply the table below. Do not read whole files: Grep the NeoForge-only names.
3. **Build** in the Forge tree (`./gradlew compileJava`), then the client smoke test in a scratch copy. Skip the test only for pure data changes.
4. Commit each branch separately with only that feature's files (NeoForge `NeoForge1.21.1`; Forge `main`).

| NeoForge 1.21.1 | Forge 1.20.1 |
|---|---|
| `net.neoforged.neoforge.*`, `net.neoforged.bus.api.*`, `net.neoforged.fml.*` | `net.minecraftforge.*`, `net.minecraftforge.eventbus.api.*`, `net.minecraftforge.fml.*` |
| `@EventBusSubscriber(modid, value)` (bus inferred) | `@Mod.EventBusSubscriber(modid, bus = Mod.EventBusSubscriber.Bus.FORGE or MOD, value)` |
| `consumer.addVertex(..).setColor(..).setUv(..).setOverlay(..).setLight(..).setNormal(..)` | `vertex(..).color(..).uv(..).overlayCoords(..).uv2(..).normal(..).endVertex()` |
| `ByteBufferBuilder` + `new BufferBuilder(bytes, mode, format)`, `MeshData data = builder.build()` | `new BufferBuilder(size)`, `begin(mode, format)`, `endOrDiscardIfEmpty()` -> `RenderedBuffer` |
| `putBulkData(pose, quad, r, g, b, a, light, overlay)` | add a trailing `false` (`readExistingColor`) |
| `level.getModelData(pos)` | `level.getModelDataManager().getAt(pos)` (nullable) |
| `RenderLevelStageEvent#getModelViewMatrix()`, `getPartialTick().getGameTimeDeltaPartialTick(false)` | `getPoseStack().last().pose()`, `getPartialTick()` (float) |
| shader `fog_distance(pos, shape)` | `fog_distance(ModelViewMat, pos, shape)` |
| item data components (`abyssia:energy`) | item NBT |
| enchantments = data JSON | enchantments = registered Java (`mc_format` skips `data/*/enchantment`) |
| `neoforge_data` in models | `forge_data` (mc_format converts) |
