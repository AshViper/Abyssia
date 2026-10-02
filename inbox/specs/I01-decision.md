# I01 Decision Agent verdict (2026-10-02): MODIFY, risk medium, tier heavy

Approved design: com.abyssia.industry package; EnergyStorage subclass + ForgeCapabilities.ENERGY; cables = plain blocks (no BE) + per-level CableNetworkManager cache; generic machine BE/Menu/Screen; machine recipes derived at runtime from RecipeManager (cached per instance, weak key); vent scan for generators/high-temp furnace.

Required modifications:
- ModIndustry registry lives in com.abyssia.registry (ModItems.blockItem is package-private). BE/Menu/Screen in com.abyssia.industry. All capability lookups through one helper (EnergyLookup) for the NeoForge port.
- ContainerData: split energy, capacity and fuel into lo/hi 16-bit pairs, rebuild (hi<<16)|(lo&0xFFFF) — values > 32767 are truncated otherwise.
- Network BFS / endpoint resolution: check level.isLoaded(pos) before getBlockState/getBlockEntity (no sync chunk loading).
- Cache endpoints as (pos, side); re-resolve the capability each tick (or LazyOptional.addListener → mark dirty). Never hold a raw IEnergyStorage.
- Mark networks dirty on ChunkEvent.Load/Unload for chunks with cables; clear the manager on LevelEvent.Unload; transient map, not SavedData.
- Transfers: extract(simulate) → receive → extract(actual accepted). Redistribute leftovers of the even split. Never create energy.
- BE: invalidateCaps/reviveCaps; setChanged on energy change. onRemove drops inventory only when !state.is(newState.getBlock()) (LIT/charge/waterlogged changes keep the BE).
- Waterlogged: getFluidState, scheduleTick water in updateShape, set in getStateForPlacement. Don't extend BaseEntityBlock without overriding getRenderShape → MODEL.
- Refinery: tungsten_ingot_from_powder_blasting belongs to the high temp furnace only (exclude from refinery). Filter recipes by namespace abyssia (iron outputs are minecraft:iron_ingot).
- Crusher: find hammer via Ingredient.test(new ItemStack(CRUSHING_HAMMER)); require exactly 2 ingredients.
- Alloy output: count + count/2.

Verify: build + check_recipes + check_textures; vent activity 0/40/80/120/160 FE/t; generator/cable/device/machine underwater; break a mid-network cable and unload/reload a chunk at one end (no crash, no forced load); GUI shows 1,000,000 FE; recipes resolve after /reload; thermal_vent drops nothing (also silk touch).
