# AGENTS.md — Abyssia NeoForge Mod

## 1. Project Overview

Abyssia is a Minecraft deep-sea exploration, resource, habitat, vehicle, and industrial automation mod.

- Minecraft: **1.21.1**
- NeoForge: **21.1.252**
- Java: **21**
- Mod ID: `abyssia`
- Main package: `com.abyssia`
- Main class: `com.abyssia.Abyssia`

Abyssia focuses on:

- deep-sea exploration
- biome-specific geology and mineral resources
- ore veins and deposits
- underwater habitats
- industrial processing
- power generation and distribution
- submarines and docking systems
- deep-sea equipment
- automated resource extraction

---

# 2. Development Philosophy

The most important rule when modifying Abyssia is:

> **Extend existing systems instead of unnecessarily replacing them.**

Before implementing a feature:

1. Read the existing implementation.
2. Search for related systems.
3. Reuse existing APIs, registries, data structures, and utilities.
4. Identify the smallest appropriate integration point.
5. Only create a new system when the existing architecture cannot reasonably support the feature.

Prefer:

```text
Existing System
      ↓
Small Integration Point
      ↓
New Functionality
```

over:

```text
Existing System
      ↓
Large Rewrite
      ↓
New System
```

Do not perform unrelated refactoring while implementing a feature.

Do not change gameplay balance, world generation behavior, or public APIs unless explicitly requested.

---

# 3. Before Editing Code

Before modifying code, inspect the relevant existing implementation.

For a new feature, determine:

- where the existing behavior is implemented
- which classes own the relevant state
- which registry owns the relevant object
- how similar existing features are implemented
- whether an existing utility/API can be reused
- whether the feature requires client, server, or common code
- whether persistent world data is involved
- whether networking is required

Do not guess the project architecture when the source code can answer the question.

If documentation exists for the affected system, read it before making architectural changes.

---

# 4. Project Structure

Major packages:

```text
com.abyssia
├── Abyssia.java
├── Config.java
│
├── block/
│   └── Custom blocks, rocks, plants, vents, etc.
│
├── worldgen/
│   ├── ModWorldgen.java
│   ├── OreVeinFeature.java
│   ├── deposit/
│   │   ├── OreDeposit.java
│   │   ├── OreDepositData.java
│   │   └── OreDepositManager.java
│   ├── cave/
│   └── structure/
│
├── registry/
│   └── DeferredRegister systems
│
├── habitat/
│   └── Underwater habitat and power systems
│
├── vehicle/
│   └── Submarines, docking, upgrades
│
├── fauna/
│   └── Deep-sea animals
│
├── network/
│   └── Network packets
│
├── waypoint/
│   └── HUD waypoint/beacon systems
│
└── thermal/
    └── Thermal vent systems
```

Use the existing package structure unless there is a strong architectural reason to change it.

---

# 5. Build and Run

| Task | Command |
|---|---|
| Compile | `./gradlew compileJava` |
| Build JAR | `./gradlew build` |
| Run client | `./gradlew runClient` |
| Run server | `./gradlew runServer` |
| Run data generation | `./gradlew runData` |
| Generate IntelliJ runs | `./gradlew genIntellijRuns` |
| Generate Eclipse runs | `./gradlew genEclipseRuns` |
| Clean | `./gradlew clean` |

Gradle daemon is disabled by default:

```properties
org.gradle.daemon=false
```

Use `--no-daemon` if necessary.

After meaningful code changes, run:

```bash
./gradlew compileJava
```

Do not claim that code compiles unless compilation was actually performed.

---

# 6. Minecraft / NeoForge Conventions

Target:

- Minecraft 1.21.1
- NeoForge 21.1.252
- Java 21

Use Mojang names / Parchment mappings.

SRG names are not used.

Access transformers:

```text
src/main/resources/META-INF/accesstransformer.cfg
```

Data generators output to:

```text
src/generated/resources/
```

Generated resources are automatically included through the project's source sets.

---

# 7. Configuration

Abyssia uses:

- `Config` for common/synced configuration
- `ClientConfig` for client-only configuration
- NeoForge `ModConfigSpec`

Do not create a second configuration system for a feature that can use the existing configuration system.

---

# 8. Networking

Abyssia uses:

```text
AbyssiaNetwork
```

for network packets.

Network packets are registered from the `Abyssia` initialization path.

When implementing client/server synchronization:

- keep authoritative world state on the server
- send only required data to clients
- do not access client-only classes from common/server code
- reuse existing packet patterns

---

# 9. World Generation

World generation is a core part of Abyssia.

Important systems include:

```text
ModWorldgen
OreVeinFeature
cave/
structure/
thermal/
```

### Critical rule

Do not modify existing world-generation behavior unless explicitly requested.

This includes:

- generation algorithms
- vein sizes
- vein shapes
- biome distributions
- generation frequency
- placement rules
- ore composition
- purity behavior
- surface exposure behavior
- configuration defaults

When adding metadata to generated content, prefer observing the existing generation result rather than changing how the generation works.

Preferred pattern:

```text
Existing Generation
       ↓
Actual Result
       ↓
Metadata Registration
```

Do not replace it with:

```text
New Generation System
       ↓
Estimated Result
```

---

# 10. Ore Vein System

Abyssia generates its own deep-sea geology and does not rely exclusively on vanilla stone/ore generation.

Ore veins are generated by:

```text
OreVeinFeature
```

The existing system supports:

### Vein sizes

```text
SMALL   : 5–15
MEDIUM  : 15–40
LARGE   : 40–100
HUGE    : 100–300
```

Large and huge veins may be affected by the existing configuration.

### Vein shapes

```text
HORIZONTAL
VERTICAL
DIAGONAL
VEIN
CLUSTER
STRATUM
```

The existing generation system uses noise-warped shapes and purity gradients.

Do not recreate or approximate the existing vein generation logic when adding features that need information about a generated vein.

---

# 11. Ore Deposit System

A persistent ore-deposit metadata system has been added to the existing ore vein generation system.

Package:

```text
com.abyssia.worldgen.deposit
```

Classes:

```text
OreDeposit
OreDepositData
OreDepositManager
```

## OreDeposit

Represents one generated ore deposit.

Current data includes:

```text
depositId
mineralId
center
bounds
size
shape
totalOre
minedAmount
```

The deposit also provides operations such as:

- NBT save/load
- mined amount updates
- remaining ore calculation
- depletion ratio
- bounds containment
- mineral block state lookup

## OreDepositData

`SavedData` used to persist deposits.

Storage:

```text
Map<UUID, OreDeposit>
```

World storage key:

```text
abyssia_ore_deposits
```

The data must survive:

- world saves
- server shutdown
- server restart
- world reload

Use `SavedData#setDirty()` when persistent state changes.

Do not store persistent world state only in static fields.

## OreDepositManager

Provides the public API for deposit operations.

Current responsibilities include:

```text
register()
get()
getAll()
remove()
incrementMined()
findIntersecting()
findNearby()
findByMineral()
```

These APIs are intended to be reusable by future systems such as:

- deep-sea scanner
- mining platforms
- mining automation
- deposit HUD/markers
- resource mapping

---

# 12. Ore Deposit Registration

`OreVeinFeature.place()` registers an `OreDeposit` after the existing vein generation completes.

The registration uses:

> **The actual number of ore blocks placed during generation.**

Do not replace this with the configured target range.

For example:

```text
Configured LARGE vein:
40–100 blocks

Actual generation:
73 ore blocks

Deposit:
totalOre = 73
```

The existing generation algorithm must remain unchanged.

Registration occurs only when:

```text
actual ore count > 0
```

and on the server side.

Each generated deposit receives a unique UUID.

---

# 13. Persistent Data Rules

When adding or modifying persistent world data:

- use `SavedData` or the existing persistence system
- keep data server-side
- mark data dirty when modified
- verify reload behavior
- avoid per-tick disk writes
- avoid static-only persistence
- keep serialization backward-compatible where practical

If a persistent data format changes, consider versioning or migration if required by the scope of the change.

---

# 14. Performance Rules

Avoid unnecessary work every tick.

Do not:

- scan the entire world every tick
- scan all loaded chunks every tick
- iterate over every deposit every tick without need
- perform expensive block searches every tick
- repeatedly rebuild cached data

Prefer:

- event-driven updates
- cached information
- bounded searches
- lazy queries
- server-side persistence
- explicit invalidation

For worldgen-related systems, do not force-load chunks unless explicitly required.

---

# 15. Client / Server Separation

Abyssia is a multiplayer mod.

Always distinguish:

```text
Common
Server
Client
```

World state should be authoritative on the server.

Client code may display or interpolate state but must not become the authoritative source of persistent gameplay data.

Do not import client-only classes into common or server code.

---

# 16. Data-Driven Content

Abyssia uses data-driven content under:

```text
src/main/resources/data/abyssia/
```

and generated resources under:

```text
src/generated/resources/
```

Examples include:

- world generation
- fauna
- caves
- structures
- enchantments
- recipes
- tags

Do not hard-code data that already has an established data-driven representation.

---

# 17. JEI / Optional Dependencies

JEI is optional.

Compile against the available API and preserve the existing optional-dependency behavior.

The project supports:

```text
-PnoJei
```

for excluding JEI runtime integration.

Do not make optional integrations mandatory.

---

# 18. Development Workflow

For a normal implementation task:

```text
1. Understand the request
        ↓
2. Search existing code
        ↓
3. Read relevant implementation
        ↓
4. Check docs
        ↓
5. Identify smallest integration point
        ↓
6. Implement
        ↓
7. Compile
        ↓
8. Run relevant manual test
        ↓
9. Report changes and verification
```

If the task is architectural, explain the proposed architecture before making large changes.

If the task is small, avoid unnecessary architectural expansion.

---

# 19. Documentation Structure

Use documentation files for detailed system knowledge.

Recommended structure:

```text
docs/
├── minerals.md
├── industry.md
├── worldgen.md
├── scanner.md
├── mining.md
├── submarine.md
├── habitat.md
└── progression.md
```

Recommended responsibilities:

### `README.md`

Human-facing project introduction.

### `AGENTS.md`

Project-wide AI development rules and important architecture.

### `docs/*.md`

Detailed system specifications and design information.

### `MEMORY.md`

Long-term project memory, design history, and important decisions.

### `CLAUDE.md`

Detailed agent-specific operating instructions if maintained by the project.

### `changelog.txt`

Version/change history.

Do not duplicate large specifications unnecessarily between these files.

---

# 20. Code Quality

Prefer code that is:

- readable
- explicit
- maintainable
- consistent with existing project style
- appropriately scoped
- easy to debug

Avoid:

- unnecessary abstractions
- speculative generic frameworks
- duplicate registries
- duplicate managers
- premature optimization
- unrelated refactoring

Do not introduce a new abstraction merely because it is theoretically cleaner if the existing project architecture already has a suitable solution.

---

# 21. Testing and Verification

Abyssia currently has no formal unit/integration test suite.

Verification is primarily:

```text
./gradlew compileJava
```

followed by manual testing where appropriate.

### For gameplay changes

Use:

```text
./gradlew runClient
```

or:

```text
./gradlew runServer
```

### For world-generation changes

Verify:

- new world generation
- affected biomes
- generated structures/features
- no obvious generation regressions
- persistence if applicable

### For data-generation changes

Use:

```text
./gradlew runData
```

After testing, report:

- build result
- manual test result
- known limitations
- unresolved issues

---

# 22. Common Gotchas

### Minecraft / NeoForge version

Do not mix APIs from other Minecraft/NeoForge versions.

Always target:

```text
Minecraft 1.21.1
NeoForge 21.1.252
Java 21
```

### Client-only code

Do not reference client-only classes from common/server initialization.

### World generation

Do not accidentally change existing worldgen behavior while adding metadata or integrations.

### SavedData

Remember to mark modified data dirty.

### Data generation

Generated resources belong under:

```text
src/generated/resources/
```

### Fauna

Fauna data is generated by:

```text
tools/gen_fauna.py
```

Run the generator after modifying fauna definitions.

### Existing systems

Before creating a new utility, manager, registry, or data structure, search for an existing equivalent.

---

# 23. Important Reference Files

The project currently contains additional documentation and operational references:

```text
CLAUDE.md
MEMORY.md
changelog.txt
```

When a task requires historical context, design decisions, or agent-specific instructions, inspect the relevant reference.

For system-specific technical information, prefer the appropriate `docs/*.md` document and the actual implementation code.

**Source code is authoritative when documentation and implementation disagree.**

---

# 24. Final Implementation Rule

When asked to implement a feature, do not simply make the code compile.

The implementation should fit the existing Abyssia architecture.

Before finishing, ask:

```text
Does this reuse an existing system?
Does this preserve existing behavior?
Does this keep server/client responsibilities correct?
Does this persist data correctly if required?
Does this avoid unnecessary per-tick work?
Does this follow the project's existing naming and package structure?
Can a future feature build on this API?
```

If the answer to any of these is no, reconsider the implementation before finishing.