# D02 review (ChatGPT, 2026-10-03)

Result: no required fixes. Implementation matches request + spec.

| ChatGPT point | Check against the code | Action |
|---|---|---|
| Durability timer must advance only when air is actually consumed | Already true: `before <= 0 \|\| delta <= 0` returns before the timer | none |
| Tank-first, one item only | Chest piece if it gives a stage, else helmet; one item | none |
| `fullSetBonusEnabled` / `maxAir` not in config | Intentional (no effect) | spec note added |
| NeoForge 2421 ticks vs 2400 | Test setup: eye briefly out of water after tp; drain rate itself = spec | none |
| Client-side rescale | Needed for HUD prediction; client/server diff <= 1 | none |

Tests (scratch clients, both loaders): stage 0 / 1 / 2 drain 0.125 / 0.05 / 0.03125 air per tick, durability 1 per 5 s / 10 s (tank first),
Water Breathing potion = no drain, deep helmet night vision kept and no Water Breathing effect, 300 -> 0 in ~120 s unequipped, 10 bubbles.
Bug found and fixed during testing: one shared state map doubled the drain in singleplayer (Entity.equals/hashCode = entity id,
same for the integrated server player and the client player) -> one map per logical side.
