# ORE01 recipe inventory (read-only)

Scope: F:\Java\Abyssia-NeoForge\src\main\resources\data\abyssia\recipe (Grep, not full reads) + src\main\java\com\abyssia\industry\recipe\MachineRecipes.java.
Counts: shaped = key letters in the pattern; shapeless = listed ingredients; `[n]` = result count. `per craft` = mineral units consumed by one craft of the product (fractions = share of a multi-output craft). Smelting/blasting-from-ore and stonecutting variants are summarised, not listed one by one.

## Hardest to make now (top 10, approx. mineral units per 1 craft, chain expanded)
Unit = one ingot / powder / shard / sulfur / concentrate. Multi-output crafts count per item (abyssal alloy = 1.5 units per ingot).

| # | product | ~units | main chain |
|---|---|---|---|
| 1 | abyssal_drill | 21 | drill_head (tungsten_tip x3 ~6, high_strength x2 ~2), advanced_energy_cell ~8 (platinum 1, thermal_core 3 shards, abyssal_energy_cell ~4), thermal_component ~4 |
| 2 | abyssal_power_core | 14 | advanced_energy_cell ~8, thermal_alloy_ingot ~2, luminous_crystal ~2, abyssal_light_core ~2 (platinum 1, yttrium via lumen cell) |
| 3 | electric_abyssal_cutter | 12 | thermal_component x2 ~8, abyssal_alloy_ingot x2 (cobalt 1, nickel 1, vanadium 1), conductive_component ~0.7 |
| 4 | electric_abyssal_drill | 12 | tungsten_tip x2 ~4, thermal_component ~4, abyssal_alloy_ingot x2 (3), conductive_component ~0.7 |
| 5 | selective_leaching_separator | 11 | corrosion_alloy x2 (2), pressure_valve x2 ~4, thermal_component ~4, acidic_leaching_reagent ~1 (sulfur) |
| 6 | hydrothermal_generator | 10 | corrosion_alloy x2 (2), pressure_valve x2 ~4, thermal_component ~4 |
| 7 | abyssal_cutter | 9 | tungsten_tip x2 ~4, abyssal_energy_cell ~4 (tellurium 1 + crystal_core 3 shards), conductive ~0.7, abyssal_composite ~0.5 |
| 8 | deep_diver_helmet | 9 | abyssal_alloy_ingot x5 = 3 crafts (cobalt 3, nickel 3, vanadium 3) |
| 9 | energy_device | 9 | abyssal_alloy_ingot x2 (3), abyssal_energy_cell ~4, conductive_alloy x2 (tellurium 1), conductive_component x2 ~1.4 |
| 10 | alloy_furnace | 9 | corrosion_alloy x2 (2), pressure_valve x1 ~2, thermal_component ~4, conductive_component ~0.7 |
Next: submarine ~8 (abyssal x1 + propulsion_screw), drill_head ~8, dive_tank ~8 (corrosion x6 + pressure_valve).

## Cobalt (cobalt_ingot, cobalt_powder, raw_cobalt, cobalt_crust)
| product (id) | recipe type | cobalt per craft | chain |
|---|---|---|---|
| abyssal_alloy_ingot [2] | shapeless | 1 cobalt_ingot | + nickel_ingot + vanadium_ingot |
| abyssal_alloy_pickaxe / axe | shaped | 2 each | 3 abyssal ingots = 2 alloy crafts (1 spare) |
| abyssal_alloy_sword / hoe / shovel | shaped | 1 / 1 / 1 | 2 / 2 / 1 abyssal ingots |
| abyssal_flippers | shaped | 1 | 2 abyssal ingots |
| deep_diver_helmet | shaped | 3 | 5 abyssal ingots = 3 crafts |
| electric_abyssal_drill / electric_abyssal_cutter / energy_device | shaped | 1 each | abyssal_alloy_ingot x2 |
| habitat_constructor | shaped | 1 | abyssal_alloy_ingot x1 |
| submarine | shaped | 1 | abyssal_alloy_ingot x1 + propulsion_screw (0.5 via tip) |
| submarine_upgrade_pressure_hull / sonar_scanner | shaped | 1 each | abyssal x1 (sonar also pressure_shell) |
| submarine_upgrade_maneuver_thruster | shaped | 1.5 | abyssal x1 + propulsion_screw |
| reinforced_energy_cable [6] | shaped | 1 per 6 cables | abyssal x1 + conductive_alloy x1 |
| cobalt_pickaxe | shaped | 2.5 | cobalt_ingot x2 + hardened_tip (0.5) |
| cobalt_shovel | shaped | 1.5 | cobalt_ingot x1 + hardened_tip (0.5) |
| hardened_tip [2] | shapeless | 1 | cobalt_ingot + manganese_powder + iron_plate |
| tungsten_tip [2] | shapeless | 0.5 | hardened_tip x1 per craft (no cobalt-free route) |
| drill_head / abyssal_drill | shaped | 0.75 / 0.75 | tungsten_tip x3 / x3 (via drill_head) |
| abyssal_cutter / propulsion_screw | shaped | 0.5 / 0.25 | tungsten_tip x2 / x1 |
| tungsten_pickaxe / tungsten_axe | shaped | 0.25 each | tungsten_tip x1 |
| corrosion_alloy_ingot [2] | shapeless | 1 cobalt_powder | + nickel_ingot + iron_powder |
| dive_tank | shaped | 3 cobalt_powder | corrosion_alloy x6 + pressure_valve |
| diving_suit_leggings | shaped | 2 cobalt_powder | corrosion_alloy x3 (2 crafts) + sea_cloth |
| pressure_valve | shaped | 1 cobalt_powder | corrosion_alloy x2 |
| pressure_shell | shaped | 1 cobalt_powder | corrosion_alloy x1 + pressure_valve |
| alloy_furnace / selective_leaching_separator | shaped | 2 / 3 cobalt_powder | corrosion x2 + pressure_valve x1 / x2 |
| hydrothermal_generator | shaped | 3 cobalt_powder | corrosion x2 + pressure_valve x2 |
| blue_dye_from_deep_pigment [4] | shapeless | 1 raw_cobalt | + deep_pigment |
| cobalt_crust_* (bricks, slab, stairs, wall, polished, ~45 recipes) | shaped / stonecutting | 0 (crust only) | cobalt_crust block; smelting to cobalt_ingot [2] |
| cobalt_concentrate / cobalt_powder / cobalt_ingot from raw, ore, powder, crust | smelting / blasting | source | excavator-only input |

## Nickel (nickel_ingot, nickel_powder, raw_nickel, nickel_crust, nickel_concentrate)
| product (id) | recipe type | nickel per craft | chain |
|---|---|---|---|
| abyssal_alloy_ingot [2] and all its consumers | shapeless / shaped | 1 per 2 abyssal ingots | every abyssal-alloy product above (0.5 nickel per ingot) |
| corrosion_alloy_ingot [2] | shapeless | 1 nickel_ingot | + cobalt_powder |
| heat_resistant_alloy_ingot [2] | shapeless | 1 nickel_powder | + molybdenum_ingot + iron_powder |
| thermal_component | shapeless | 0.5 | heat_resistant x1 item |
| molybdenum_pickaxe | shaped | 1.5 | heat_resistant x2 + thermal_component |
| high_temp_furnace | shaped | 1 | heat_resistant x2 |
| tungsten_alloy_ingot [2] | shapeless | 1 nickel_powder | + tungsten_ingot |
| tungsten_tip | shapeless | 0.5 | tungsten_alloy x1 per craft |
| tungsten_pickaxe / tungsten_axe | shaped | 1 each | tungsten_alloy_ingot x2 |
| nickel_crust bricks / slabs / stairs / walls (~40 recipes) | shaped / stonecutting | 0 | crust only |

## Manganese (manganese_ingot, manganese_powder, raw_manganese, manganese_crust)
| product (id) | recipe type | manganese per craft | chain |
|---|---|---|---|
| high_strength_alloy_ingot [2] | shapeless | 1 manganese_ingot | + vanadium_powder + iron_powder |
| hardened_tip [2] | shapeless | 1 manganese_powder | + cobalt_ingot |
| manganese_axe | shaped | 2 | manganese_ingot x2 + high_strength x1 (0.5 manganese) |
| manganese_sword | shaped | 1.5 | manganese_ingot x1 + high_strength x1 (0.5) + abyssal_composite |
| drill_head | shaped | 0.5 x2 items = 1 | high_strength_alloy x2 (+ tungsten_tip x3) |
| tungsten_tip [2] | shapeless | 0.5 | hardened_tip x1 per craft |
| black_dye_from_deep_pigment [4] | shapeless | 1 raw_manganese | + deep_pigment |
| manganese_crust bricks / slabs / stairs / walls (~45 recipes) | shaped / stonecutting | 0 | crust only |

## Sulfur (sulfur; sources: cave_mineral_crust smelting [2], crushing_hammer + cave_mineral_crust [3])
| product (id) | recipe type | sulfur per craft | chain |
|---|---|---|---|
| acidic_leaching_reagent [4] | shapeless | 2 | + thermal_reagent x1 + water_bucket |
| thermal_reagent | shapeless | 1 | + thermal_crystal_shard x1 + molten_volcanic_rock + salt_crust |
| thermal_alloy_ingot [2] | shapeless | 0.5 | thermal_reagent x1 per 2 items |
| selective_leaching_separator | shaped | 0.25 (via acidic x1 = 4 outputs) | acidic_leaching_reagent x1 |
| fire_charge [3] | shapeless | 1 | + refined_oil + charcoal |
| gunpowder [2] | shapeless | 1 | + charcoal + organic_matter |
| yellow_dye [3] | shapeless | 1 | + deep_pigment |

## Thermal crystal shard (thermal_crystal_shard)
| product (id) | recipe type | shards per craft | chain |
|---|---|---|---|
| thermal_core | shaped | 3 | pattern SS/SA, + marine_adhesive x1 |
| thermal_reagent | shapeless | 1 | see sulfur |
| thermal_component | shapeless | 3 | via thermal_core x1 (+ heat_resistant x1, thermal_felt) |
| advanced_energy_cell | shapeless | 3 | via thermal_core x1 |
| abyssal_power_core / alloy_furnace / high_temp_furnace / hydrothermal_generator / selective_leaching_separator / electric_abyssal_cutter / electric_abyssal_drill / abyssal_drill | shaped / shapeless | 3 each (via thermal_component) | thermal_component x1 (x2 in electric_abyssal_cutter) |

## Abyssal crystal shard (abyssal_crystal_shard)
| product (id) | recipe type | shards per craft | chain |
|---|---|---|---|
| crystal_core | shaped | 3 | pattern SS/SA, + marine_adhesive x1 |
| abyssal_energy_cell | shapeless | 3 | via crystal_core x1 (+ tellurium 1, lumen_cell) |
| abyssal_composite [2] | shapeless | 1 | + reinforced_fiber + marine_resin |
| luminous_crystal | shapeless | 1 | + yttrium_powder + crystal_lens |
| crystal_pickaxe | shaped | 0 direct | crystal_core x1 + luminous_crystal x1 + abyssal_composite x1 (~5 shards) |
| abyssal_power_core | shapeless | 1 (via luminous_crystal) | + advanced_energy_cell + abyssal_light_core + thermal_alloy |

## Platinum (raw_platinum, platinum_ingot, platinum_ore)
| product (id) | recipe type | platinum per craft | chain |
|---|---|---|---|
| advanced_energy_cell | shapeless | 1 | + abyssal_energy_cell + thermal_core |
| abyssal_light_core | shapeless | 1 | + advanced_lumen_cell + crystal_lens |
| abyssal_power_core / abyssal_drill / electric drills via advanced_energy_cell | shaped / shapeless | 1 (indirect) | advanced_energy_cell x1 |

## Tellurium (raw_tellurium, tellurium_ingot, tellurium_powder, tellurium_ore)
| product (id) | recipe type | tellurium per craft | chain |
|---|---|---|---|
| abyssal_energy_cell | shapeless | 1 | + lumen_cell + crystal_core |
| conductive_alloy_ingot [2] | shapeless | 1 tellurium_powder | + copper_ingot |
| conductive_component / reinforced_energy_cable [6] / energy_device | shaped / shapeless | 0.5 / ~0.2 / 1 (energy_device x2 alloy) | via conductive_alloy |

## Molybdenum (raw_molybdenum, molybdenum_ingot, molybdenum_ore)
| product (id) | recipe type | molybdenum per craft | chain |
|---|---|---|---|
| heat_resistant_alloy_ingot [2] | shapeless | 1 molybdenum_ingot | + nickel_powder + iron_powder |
| thermal_alloy_ingot [2] | shapeless | 1 molybdenum_ingot | + thermal_reagent + tungsten_powder |
| molybdenum_pickaxe | shaped | 1.5 | heat_resistant x2 (1) + thermal_component (0.5) |
| high_temp_furnace | shaped | 1 | heat_resistant x2 |
| thermal_component | shapeless | 0.5 | heat_resistant x1 |

## Vanadium (raw_vanadium, vanadium_ingot, vanadium_powder, vanadium_ore)
| product (id) | recipe type | vanadium per craft | chain |
|---|---|---|---|
| abyssal_alloy_ingot [2] and consumers | shapeless / shaped | 1 per 2 ingots | every abyssal-alloy product (0.5 per ingot) |
| high_strength_alloy_ingot [2] | shapeless | 1 vanadium_powder | + manganese_ingot + iron_powder |
| manganese_axe / manganese_sword / drill_head / habitat_constructor | shaped | 0.5 each via high_strength | high_strength x1 (x2 in drill_head) |

## Tungsten (raw_tungsten, tungsten_ingot, tungsten_powder, tungsten_ore; high-temp furnace path)
| product (id) | recipe type | tungsten per craft | chain |
|---|---|---|---|
| tungsten_alloy_ingot [2] | shapeless | 1 tungsten_ingot | + nickel_powder |
| tungsten_tip [2] | shapeless | 1 (+ tungsten_powder 1 = 2 total) | tungsten_alloy x1 + tungsten_powder x1 + hardened_tip x1 |
| tungsten_pickaxe / tungsten_axe | shaped | 2 + 1 (tip) | tungsten_alloy_ingot x2 + tungsten_tip x1 |
| abyssal_cutter / electric_abyssal_drill / electric_abyssal_cutter / propulsion_screw | shaped | 2 / 2 / 0 / 1 via tip (x2 = 4 units) | tungsten_tip x N |
| drill_head / abyssal_drill | shaped | 3 tips = ~3 tungsten | tungsten_tip x3 |
| thermal_alloy_ingot [2] | shapeless | 1 tungsten_powder | high-temp route (MachineRecipes) |

## Yttrium (raw_yttrium, yttrium_ingot, yttrium_powder, yttrium_ore)
| product (id) | recipe type | yttrium per craft | chain |
|---|---|---|---|
| advanced_lumen_cell | shapeless | 1 yttrium_ingot | + lumen_cell + lumen_gel |
| luminous_crystal | shapeless | 1 yttrium_powder | + abyssal shard + crystal_lens |
| abyssal_light_core | shapeless | 0 direct | via advanced_lumen_cell (1 yttrium) |

## Machine-derived recipes (MachineRecipes.java)
- Leaching (cobalt/manganese/nickel crust and concentrate -> powder x3) emits rare side-outputs at 8% (raw_platinum, raw_tellurium, raw_yttrium for cobalt; raw_molybdenum, raw_vanadium, raw_yttrium for manganese; raw_tungsten, raw_yttrium for nickel), 2% yttrium on iron/copper crust.
- High-temp furnace: tungsten_powder -> tungsten_ingot (60 t, 4000 FE); thermal and tungsten alloy x1.5 (100 t, 8000 FE).
- Any rare metal that only comes from crust leaching is tied to the same crust source as the main metal.

## Products with no alternative path
Single producing recipe and no non-mined ingredient (checked by file-name grep; tags under data/*/tags were not checked):
- abyssal_alloy_ingot and all abyssal-alloy tools/armor/machines listed above (no other route to abyssal_alloy_ingot).
- tungsten_tip, hardened_tip, tungsten_alloy_ingot, high_strength_alloy_ingot, heat_resistant_alloy_ingot, thermal_alloy_ingot, corrosion_alloy_ingot, conductive_alloy_ingot.
- crystal_core, thermal_core, abyssal_energy_cell, advanced_energy_cell, abyssal_light_core, advanced_lumen_cell, luminous_crystal, abyssal_composite, thermal_reagent, acidic_leaching_reagent.
- Final products: drill_head, abyssal_drill, electric_abyssal_drill, electric_abyssal_cutter, abyssal_cutter, propulsion_screw, submarine, submarine_upgrade_* (pressure_hull, sonar_scanner, maneuver_thruster, high_capacity_battery uses abyssal_energy_cell), dive_tank, diving_suit_leggings, deep_diver_helmet, pressure_valve, pressure_shell, hydrothermal_generator, alloy_furnace, high_temp_furnace, selective_leaching_separator, habitat_constructor, energy_device, reinforced_energy_cable, conductive_component, tungsten_pickaxe/axe, cobalt_pickaxe/shovel, manganese_axe/sword, molybdenum_pickaxe, crystal_pickaxe.
- Not in this list (alternative exists): blue/black/yellow dyes (vanilla dye items), fire_charge/gunpowder (vanilla items with other ingredients).
