# Abyssia Industrial System Summary

## Overview
The industrial system (I01, I02) provides machines, generators, energy cables, and fluid pipes for underwater base automation.

## Machine Kinds (`MachineKind.java`)

| Machine | Inputs | Reagent | Outputs | Capacity | Receive Rate | Layout |
|---------|--------|---------|---------|----------|--------------|--------|
| Crusher | 1 | No | 1 | 32,000 FE | 256 FE/t | MACHINE |
| Refinery Furnace | 1 | No | 1 | 32,000 FE | 256 FE/t | MACHINE |
| Alloy Furnace | 3 | No | 1 | 32,000 FE | 256 FE/t | ALLOY |
| High-Temp Furnace | 3 | No | 1 | 32,000 FE | 256 FE/t | ALLOY |
| Hydrothermal Generator | 0 | No | 0 | 32,000 FE | 0 | ENERGY |
| Auxiliary Generator | 1 (fuel) | No | 0 | 16,000 FE | 0 | GENERATOR |
| Energy Device | 0 | No | 0 | 32,000 FE | 256 FE/t | ENERGY |
| Selective Leaching Separator | 1 | Yes (Acidic Reagent) | 4 | 32,000 FE | 256 FE/t | LEACHING |

**Special mechanics:**
- High-Temp Furnace requires live thermal vent within 6 blocks
- Reagent machines (Leaching Separator) run 10% faster when in/near water
- Rare outputs from Leaching Separator queue in overflow if slots full

## Energy System

### `IndustryEnergyStorage` (`IndustryEnergyStorage.java`)
- Extends Forge's `EnergyStorage`
- Callbacks on every change (`onChanged` → `BlockEntity#setChanged`)
- `generate(amount)` - bypasses receive limit (for generators)
- `consume(amount)` - bypasses extract limit (for machines)
- `room()` - available capacity

### Cable Networks (`CableNetworkManager.java`)
- **Network = connected cable group** (both cable types), max 4096 cables
- **Endpoints** = neighbouring blocks with `IEnergyStorage` capability
- **Throughput** = slowest cable's rate in the network
- **Rebuild triggers**: cable placed/removed, chunk load/unload, industrial block entity "touch"
- **Distribution per tick** (priority order):
  1. Producers → Consumers (even split, leftovers redistributed)
  2. Producers → Buffers (storages with both in/out)
  3. Buffers → Consumers
- Never loads chunks during network building

### Energy Lookup (`EnergyLookup.java`)
- Resolves `IEnergyStorage` at a position+face
- Handles Habitat base external receivers (H08 integration)

## Block Entities

### `IndustryBlockEntity` (base class)
- Common fields: `energy` (IndustryEnergyStorage), `items` (MachineInventory), `kind`, `status`, `rate`, `progress`, `maxProgress`, `burn`, `burnMax`
- `work()` called each server tick - overridden by subtypes
- Inventory sync via `ContainerData` (energy as 16-bit lo/hi pairs)

### `ProcessingMachineBlockEntity` (`ProcessingMachineBlockEntity.java`)
- Crusher, Refinery, Alloy, High-Temp, Leaching Separator
- One recipe at a time, energy spread over ticks (pauses if short)
- Status bits: 1=no vent, 2=no reagent, 4=rare full, 8=water bonus
- Rare outputs roll per operation, overflow stored in NBT
- Water check every 20 ticks, vent check every 20 ticks (High-Temp only)

### `GeneratorBlockEntity` (`GeneratorBlockEntity.java`)
- **Hydrothermal**: 80 FE/t × vent activity multiplier, 32k buffer, pushes to neighbours
- **Auxiliary**: 40 FE/t from fuels (coal/charcoal=80k, bio oil=120k, refined oil=200k), 16k buffer
- Neither accepts energy, both push to neighbours + pulled by cables
- Status = vent activity ordinal + 1 (hydrothermal), 0 = no vent

### `EnergyDeviceBlockEntity` (`EnergyDeviceBlockEntity.java`)
- Simple energy buffer (32k, 256 FE/t receive), no processing
- GUI shows energy only

## Recipes (`ProcessRecipe.java`, `MachineRecipes.java`)

### `ProcessRecipe` record
```java
ProcessRecipe(
  List<Ingredient> inputs,    // shapeless, up to 3, order-independent
  int count,                  // items consumed per input per operation
  ItemStack result,           // main output
  List<Rare> rares,           // independent chance rolls
  int ticks,                  // operation duration
  int energy                  // total FE cost
)
```
- `energyAt(step, ticks)` - spreads energy evenly over ticks (no rounding loss)
- `matches(stacks)` - backtracking assignment of stacks to ingredients

### `MachineRecipes` (derived at runtime)
- Built from JSON/data-driven recipes
- `find(kind, inputs)` - returns matching recipe or null
- Re-derived on `/reload`

## Blocks

| Block | Purpose |
|-------|---------|
| `MachineBlock` | Base for all processing machines |
| `EnergyCableBlock` | Energy transport, has `rate()` for throughput |
| `IndustrialPipeBlock` | Fluid transport (connects to tanks/machines) |
| `ValveBlock` | Pipe I/O control |
| `EnergyDeviceBlock` | Simple energy buffer block |
| `GeneratorBlock` | Hydrothermal / Auxiliary generators |
| `FacingDecorBlock` | Decorative directional blocks |
| `BeamBlock`, `GratingBlock`, `IndustrialLightBlock` | Base building blocks |

## GUI (`IndustryScreen.java`, `GuiLayout.java`)

### Layouts
- **MACHINE**: Inputs left, progress center, output right, energy bar left
- **ALLOY**: 3 inputs top, progress, output, energy
- **ENERGY**: Large energy bar, capacity display
- **GENERATOR**: Fuel slot, energy bar, output rate
- **LEACHING**: Input, reagent, 4 output slots (main + 3 rare), energy

### Tooltip
- Energy: "X / Y FE"
- Progress: percentage
- Generator output: FE/t
- Machine status icons (heat, reagent, water bonus, rare full)

## Integration Points

### Worldgen
- `VentHeat` - thermal vent detection for hydrothermal generator & high-temp furnace
- `VentActivity` enum: NONE, LOW, MEDIUM, HIGH, EXTREME
- Multipliers: 0.0, 0.5, 1.0, 1.5, 2.0

### Habitat (H08)
- `HabitatPower.externalReceiver()` - habitat base shells act as cable endpoints
- `CableNetworkManager.markAllDirty()` - called when habitat bases change

### Submarine Dock
- `SubmarineDockBlockEntity` - charges submarines from cable network
- Uses `IndustryEnergyStorage` + `CableNetworkManager.touchAround()`

### Wall Workbench
- `WallWorkbenchBlockEntity` - 20k FE buffer, charges held items (256 FE/t)
- Only receives energy, shows powered state on block

## Key Constants

```java
// MachineKind
MACHINE_CAPACITY = 32_000
MACHINE_RECEIVE = 256

// GeneratorBlockEntity
HYDRO_RATE = 80          // base FE/t per vent activity
AUX_RATE = 40            // FE/t while burning fuel

// ProcessingMachineBlockEntity
VENT_RADIUS = 6          // high-temp furnace vent search
STATUS_NO_HEAT = 1
STATUS_NO_REAGENT = 2
STATUS_RARE_FULL = 4
STATUS_WATER = 8

// CableNetworkManager
MAX_CABLES = 4096        // per network cap
```

## Data Flow Summary

```
Generators (hydro/aux) → generate FE → push to neighbours
                                                          ↓
Cable Networks ← discover endpoints ← Industrial BlockEntities
                                                          ↓
                                    distribute per tick (producers→consumers→buffers)
                                                          ↓
Processing Machines ← consume FE per recipe.tick → produce outputs
```