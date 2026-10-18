# ACH01 実績（進捗 / advancements）

依頼（2026-10-10）: 「実績周りの実装。楽しみやすいように、いろんな判定を使って様々な実装を」
ベース NeoForge 1.21.1（`F:\Java\Abyssia-NeoForge`）→ Forge 1.20.1（main）へ移植。

## 方針
- バニラの advancement 機構を使う。3 タブ: `dive`（深淵への潜行）、`life`（深海の生命）、`engineering`（海底工学）。計 70 前後。
- 判定は **独自トリガー 7 種 + バニラトリガー 8 種**。独自トリガーの条件 JSON は文字列・真偽・数値範囲だけにして、両ローダーで同じ JSON にする。
- 独自トリガー `scan` / `research` は**状態判定**（プレイヤーの記録全体を見る）。スキャン完了・技術解放のたびとログイン時に発火 → 既存セーブも次のログインで追いつく。
- advancement JSON・lang は生成ツール `tools/advancement_assets.py` で作る（手編集しない）。1.21 形式だけを出し、Forge 用は `tools/mc_format.py --to-forge` に advancement の 1.20 変換を足して作る。
- 報酬は経験値だけ（challenge に 50〜500）。root はトースト・チャット告知なし。

## 独自トリガー（id `abyssia:<name>`、全フィールド省略可、数値範囲はバニラ MinMaxBounds 形式）
| trigger | 発火 | 条件 |
|---|---|---|
| `dive` | 20 tick ごと、サーバーの各プレイヤー（観戦者除く） | `y` (Doubles), `submerged` (bool: 目が水中), `in_submarine` (bool), `submarine_upgrades` (Ints: 乗っている艇の装着数 = bitCount(upgradeMask)、乗っていなければ不一致), `suit_tier` (Ints: PressureGear.wornTier), `suit_pieces` (Ints: 防具 4 枠のうち潜水具（PressureGear.pieceTier>0）の数), `health` (Doubles)。submerged は PressureGear と同じ isUnderWater() |
| `scan` | ResearchManager.scan の完了後 + ログイン時 | `target` (RL、記録済み), `group` (文字列: target パス `scan_target/` の後ろの接頭辞, 例 `fauna_`), `count` (Ints: 記録済み数、group 指定時はその中), `complete` (bool: 登録済み target（group 内）を全部記録) |
| `research` | 技術解放のたび（evaluate の新規分・/research unlock）+ ログイン時 | `technology` (RL、解放済み), `count` (Ints), `complete` (bool: TechnologyRegistry.all() 全部) |
| `build` | HabitatBuilder Job 完了（entry.complete の直後） | `build` (文字列 BuildEntry.id(): foundation/room/corridor/entrance/moon_pool/scan_room …) |
| `capture` | 捕獲カニスターで捕獲した直後 | `entity` (RL エンティティ種) |
| `aquarium` | 水槽に放流成功した直後 | `species` (文字列), `count` (Ints: 放流後の水槽内の数), `species_count` (Ints: 水槽内の種類数) |
| `event` | 単発イベント | `event` (文字列), `key` (文字列) |

`event` の種類: `pressure_damage`（PressureGear で水圧ダメージ）、`hazard_damage` + key `heat|toxic|cold`（HazardZone のダメージ）、`submarine_crushed`（Submarine.crush のダメージで艇が breakApart した時、乗員がプレイヤー。定格深度超えの毎秒ダメージでは発火しない）、`habitat_blackout`（HabitatLifeSupport の窒息ダメージ）、`aquarium_birth`（水槽で子が生まれた時、半径 16 内の全プレイヤー）。

## バニラトリガー
`tick`（dive の root）、`location`（バイオーム）、`inventory_changed`、`consume_item`、`placed_block`、`effects_changed`、`entity_hurt_player`、`player_killed_entity`。

## 一覧（書式: id | 親 | アイコン | 枠・オプション | 条件 | EN 題 — EN 説明 | JA 題 — JA 説明）
略記: `each(...)`=1 項目 1 条件で全部必要、`any(...)`=どれか 1 つ。scan の target は `abyssia:scan_target/<名>`、research は `abyssia:technology/<名>`。アイテム・ブロック・エンティティ・バイオームは明記なしなら `abyssia:`。

### dive（背景 `abyssia:textures/block/abyssal_rock.png`）
- dive/root | — | abyss_guide_book | task, no toast/announce | tick | Abyssia — The ocean is deeper than you think | アビシア — 海は、思っているよりずっと深い
- dive/first_dive | root | entry_diving_flippers | task | dive{submerged, y<=50} | Take the Plunge — Dive below Y 50 | 最初のひと潜り — Y 50 より深く潜る
- dive/suit_up | first_dive | entry_diver_helmet | task | dive{suit_tier>=1} | Suited Up — Wear a full set of diving gear | 潜水士見習い — 潜水装備を一式そろえて身につける
- dive/deep_diver | suit_up | deep_diver_helmet | task | dive{suit_tier>=2} | Deep Diver — Wear a full set of deep diving gear | 深海潜水士 — 深海用の潜水装備を一式身につける
- dive/pressure_proof | deep_diver | pressure_diver_helmet | goal | dive{suit_tier>=3} | Pressure Proof — Wear a full pressure suit | 耐圧潜水士 — 耐圧潜水服を一式身につける
- dive/twilight_zone | first_dive | minecraft:glow_ink_sac | task | dive{submerged, y<=0} | Twilight Zone — Dive below Y 0, where sunlight fades | 薄明の層 — 日の光が消えていく Y 0 より深く潜る
- dive/free_diver | twilight_zone | minecraft:kelp | challenge, xp 50 | dive{submerged, y<=0, suit_pieces<=0, in_submarine=false} | Free Diver — Reach Y 0 underwater without any diving gear or a submarine | 素潜り名人 — 潜水具も潜水艇も使わずに Y 0 まで潜る
- dive/wreck_diver | twilight_zone | minecraft:chest | task | scan{target=wreck_core} | Wreck Diver — Scan the core of a sunken wreck | 沈没船ダイバー — 沈没船の中枢をスキャンする
- dive/seafloor_surveyor | twilight_zone | deep_sea_map | goal, xp 50 | location each(海底バイオーム 17) | Seafloor Surveyor — Visit every ocean-floor biome | 海底踏査 — 海底のバイオームをすべて訪れる
- dive/current_blessing | first_dive | minecraft:heart_of_the_sea | task | effects_changed abyssia:abyssal_current | Blessing of the Current — Gain Abyssal Current from a potion or a dish | 海流の加護 — ポーションや料理で深海の海流の加護を得る
- dive/into_the_deep | twilight_zone | abyssal_rock | task | dive{y<=-64} | Into the Deep — Go below Y -64 into the deep layer | 深層へ — Y -64 より下の深層に入る
- dive/feel_the_pressure | into_the_deep | pressure_valve | task | event{pressure_damage} | Feel the Pressure — Take damage from water pressure. Better gear raises your safe depth | 水圧の洗礼 — 水圧でダメージを受ける。装備が良いほど安全に潜れる深さが増す
- dive/deep_sight | into_the_deep | minecraft:ender_eye | task | effects_changed abyssia:deep_sight | Eyes of the Deep — Gain Deep Sight | 深海の瞳 — 深海視の効果を得る
- dive/lightless | into_the_deep | abyssal_cave_rock | task | dive{y<=-300} | Lightless Depths — Reach Y -300 | 光なき深み — Y -300 に到達する
- dive/close_call | lightless | minecraft:golden_apple | challenge, hidden, xp 50 | dive{submerged, y<=-376, health<=2} | Close Call — Drop to one heart in the abyss | 九死に一生 — 深淵で体力がハート 1 つ以下になる
- dive/lost_civilization | lightless | ancient_pressure_plate | task | location any(sunken_ruins, abyss_ruins) | Lost Civilization — Find ancient ruins in the deep | 失われた文明 — 深海の古代遺跡を見つける
- dive/hot_water | lightless | thermal_crystal_shard | task, hidden | event{hazard_damage, key=heat} | Hot Water — Get scalded in a volcanic cavern | 茹でダコ — 火山性の洞窟で熱にやられる
- dive/toxic_shock | lightless | minecraft:fermented_spider_eye | task, hidden | event{hazard_damage, key=toxic} | Toxic Shock — Breathe in the poison of a toxic cavern | 毒の海 — 有毒な洞窟の毒にやられる
- dive/cold_shock | lightless | minecraft:blue_ice | task, hidden | effects_changed abyssia:cold_shock | Cold Shock — Get chilled to the bone by freezing water | 骨まで凍える — 凍える海水で寒冷ショックを受ける
- dive/the_abyss | lightless | deep_crystal_shard | goal | dive{y<=-376} | The Abyss Gazes Back — Reach the abyss below Y -376 | 深淵もまたこちらを覗く — Y -376 より下の深淵に到達する
- dive/reckless_descent | the_abyss | minecraft:skeleton_skull | task, hidden | dive{submerged, y<=-376, suit_tier<=2, in_submarine=false} | Reckless Descent — Enter the abyss without a pressure suit or a submarine | 無謀な潜行 — 耐圧潜水服も潜水艇もなしで深淵に入る
- dive/abyss_cartographer | the_abyss | abyss_chart | challenge, xp 200 | location each(深淵バイオーム 12) | Abyssal Cartographer — Visit every abyss cavern biome | 深淵の地図職人 — 深淵の洞窟バイオームをすべて訪れる
- dive/hadal | the_abyss | pressure_crystal_shard | goal | dive{y<=-860} | Hadal Depths — Reach Y -860 | 超深海 — Y -860 に到達する
- dive/rock_bottom | hadal | abyssal_crystal_shard | challenge, xp 100 | dive{y<=-980} | Rock Bottom — Reach the very bottom of the ocean (Y -980) | どん底 — 海のいちばん底（Y -980）にたどり着く
- dive/submarine | first_dive | submarine | goal | dive{in_submarine=true} | Dive, Dive, Dive! — Pilot a submarine | 潜航開始！ — 潜水艇に乗り込む
- dive/hull_breach | submarine | submarine_upgrade_pressure_hull | task, hidden | event{submarine_crushed} | Hull Breach — Have your submarine crushed by water pressure | 船体圧壊 — 水圧で潜水艇がつぶれる
- dive/fully_loaded | submarine | submarine_upgrade_sonar_scanner | goal | dive{in_submarine=true, submarine_upgrades>=5} | Fully Loaded — Pilot a submarine with all five upgrade slots filled | フル装備 — アップグレード 5 枠すべてを埋めた潜水艇に乗る
- dive/captain_of_the_abyss | fully_loaded | submarine_upgrade_depth_mk3 | challenge, xp 150 | dive{in_submarine=true, y<=-860} | Captain of the Abyss — Take a submarine below Y -860 | 深淵の艦長 — 潜水艇で Y -860 より深く潜る

海底バイオーム 17: deep_sea abyssal_ocean abyssal_forest deep_forest abyssal_trench hadal_zone volcanic_deep thermal_vents deep_crystal_fields sunken_ruins bone_graveyard brine_lakes glow_gardens frost_abyss twilight_reef abyssal_rift deep_fissure
深淵バイオーム 12: abyss_plain abyss_garden abyss_crystal abyss_toxic abyss_toxic_vents abyss_volcanic abyss_magma abyss_geothermal abyss_frozen abyss_cryo abyss_anomaly abyss_ruins
（両ワールド型 normal / ocean_world の両方で生成されるものだけを残す。実装結果: twilight_reef と abyssal_rift は normal で生成されないため外し、海底は 15 種）

### life（背景 `abyssia:textures/block/aquarium_sand.png`）
- life/root | — | lidar_scanner | task, no toast/announce | inventory_changed lidar_scanner | Life of the Deep — Get a lidar scanner and start recording the deep | 深海の生命 — ライダースキャナーを手に入れ、深海の記録を始める
- life/first_scan | root | lidar_scanner | task | scan{count>=1} | First Entry — Complete your first scan | はじめての記録 — はじめてスキャンを完了する
- life/data_hoarder | first_scan | minecraft:writable_book | task | scan{count>=50} | Data Hoarder — Record 50 scan targets | データ収集家 — スキャン対象を 50 件記録する
- life/archivist | data_hoarder | minecraft:bookshelf | goal, xp 100 | scan{count>=100} | Archivist — Record 100 scan targets | 深海アーカイブ — スキャン対象を 100 件記録する
- life/neighbours | first_scan | anglerfish_spawn_egg | task | scan{group=fauna_, count>=10} | Neighbourhood Watch — Record 10 kinds of deep-sea creature | ご近所さん — 深海生物を 10 種記録する
- life/living_encyclopedia | neighbours | giant_squid_spawn_egg | challenge, xp 300 | scan{group=fauna_, complete} | Living Encyclopedia — Record every deep-sea creature | 生きた百科事典 — 深海生物をすべて記録する
- life/kraken | neighbours | giant_squid_spawn_egg | goal | scan{target=fauna_giant_squid} | Release the Kraken — Scan a giant squid | クラーケン現る — ダイオウイカをスキャンする
- life/living_fossils | neighbours | frilled_shark_spawn_egg | goal | scan each(fauna_frilled_shark, fauna_goblin_shark, fauna_chimaera, fauna_sea_lily) | Living Fossils — Scan a frilled shark, a goblin shark, a chimaera and a sea lily | 生きた化石 — ラブカ・ミツクリザメ・ギンザメ・ウミユリをスキャンする
- life/light_show | neighbours | minecraft:glow_ink_sac | task | scan each(fauna_firefly_squid, fauna_lanternfish, fauna_atolla_jelly, fauna_stoplight_loosejaw) | Light Show — Scan four glowing creatures | 光のショー — 光る生き物を 4 種スキャンする
- life/vent_community | neighbours | yunohana_crab_spawn_egg | goal | scan each(fauna_scaly_foot_snail, fauna_yunohana_crab, fauna_satsuma_tubeworm, fauna_vent_eelpout, fauna_ohara_shrimp) | Life Without Sunlight — Scan five creatures of the hydrothermal vents | 太陽のいらない生態系 — 熱水噴出孔の生き物を 5 種スキャンする
- life/deepest_fish | neighbours | minecraft:cod | goal | scan{target=fauna_mariana_snailfish} | The Deepest Fish — Scan a Mariana snailfish | いちばん深い魚 — マリアナスネイルフィッシュをスキャンする
- life/ugly_cute | neighbours | minecraft:pink_dye | task, hidden | scan{target=fauna_blobfish} | Ugly-Cute — Scan a blobfish | ブサかわ — ニュウドウカジカをスキャンする
- life/vampire_squid | neighbours | minecraft:ink_sac | task, hidden | scan{target=fauna_vampire_squid} | Vampire Squid from Hell — Scan a vampire squid | 地獄の吸血イカ — コウモリダコをスキャンする
- life/botanist | first_scan | minecraft:kelp | task | scan{group=plant_, count>=15} | Seafloor Botanist — Record 15 kinds of deep-sea plant | 海底の植物学者 — 深海植物を 15 種記録する
- life/herbarium | botanist | minecraft:glow_berries | challenge, xp 200 | scan{group=plant_, complete} | Herbarium of the Dark — Record every deep-sea plant | 闇の植物標本館 — 深海植物をすべて記録する
- life/prospector | first_scan | minecraft:raw_iron | task | scan{group=mineral_deposit_, count>=10} | Prospector — Record 10 kinds of mineral deposit | 鉱脈探査士 — 鉱床を 10 種記録する
- life/geologist | prospector | iridium_ingot | challenge, xp 200 | scan{group=mineral_deposit_, complete} | Master Geologist — Record every kind of mineral deposit | 地質学の大家 — 鉱床をすべて記録する
- life/eureka | first_scan | minecraft:redstone_torch | task | research{count>=1} | Eureka! — Unlock your first technology | ひらめいた！ — はじめて技術を解放する
- life/researcher | eureka | minecraft:book | task | research{count>=10} | Researcher — Unlock 10 technologies | 研究員 — 技術を 10 個解放する
- life/chief_scientist | researcher | minecraft:enchanted_book | goal | research{count>=30} | Chief Scientist — Unlock 30 technologies | 主任研究員 — 技術を 30 個解放する
- life/sage_of_the_deep | chief_scientist | abyss_guide_book | challenge, xp 500 | research{complete} | Sage of the Deep — Unlock every technology | 深海の賢者 — すべての技術を解放する
- life/catch_and_keep | root | creature_capture_canister_filled | task | capture{} | Catch and Keep — Capture a creature in a capture canister | 生け捕り — 捕獲カニスターで生き物を捕まえる
- life/home_aquarium | catch_and_keep | creature_capture_canister_filled | task | aquarium{} | Home Aquarium — Release a creature into an aquarium | マイ水族館 — 水槽に生き物を放す
- life/public_aquarium | home_aquarium | minecraft:tropical_fish_bucket | goal | aquarium{species_count>=4} | Public Aquarium — Keep four different species in one aquarium | みんなの水族館 — 1 つの水槽で 4 種類の生き物を飼う
- life/full_house | home_aquarium | creature_capture_canister | task | aquarium{count>=8} | Full House — Fill an aquarium to capacity | 満員御礼 — 水槽を満員にする
- life/new_life | home_aquarium | minecraft:turtle_egg | goal | event{aquarium_birth} | New Life — Witness a birth in an aquarium | 新しい命 — 水槽で生き物が生まれるのを見届ける
- life/taste_of_the_deep | root | cooked_abyssal_fish | task | consume_item any(料理 25) | Taste of the Deep — Eat a dish made from deep-sea ingredients | 深海の味 — 深海の食材の料理を食べる
- life/sorry_blobby | taste_of_the_deep | cooked_blobfish | task, hidden | consume_item cooked_blobfish | Sorry, Blobby — Eat a cooked blobfish | ごめんね、ブロブ — ニュウドウカジカの料理を食べる
- life/deep_sea_gourmet | taste_of_the_deep | jellyfish_stew | challenge, xp 150 | consume_item each(料理 25) | Deep-Sea Gourmet — Eat every deep-sea dish | 深海グルメ — 深海の料理をすべて食べる
- life/lured_in | root | minecraft:lantern | task, hidden | entity_hurt_player source anglerfish | Lured In — Get bitten by an anglerfish | 提灯に誘われて — チョウチンアンコウにかまれる
- life/stung | root | minecraft:slime_ball | task, hidden | entity_hurt_player any(クラゲ: 実際に刺すエンティティ) | Stung — Get stung by a jellyfish | クラゲに刺された — クラゲに刺される
- life/lights_out | lured_in | minecraft:iron_sword | task | player_killed_entity anglerfish | Lights Out — Defeat an anglerfish | 提灯を消せ — チョウチンアンコウを倒す

料理 25: cooked_abyssal_fish cooked_alfonsino cooked_angler_flesh cooked_black_scabbardfish cooked_blobfish cooked_blue_ling cooked_deepwater_redfish cooked_eelpout_flesh cooked_greenland_halibut cooked_orange_roughy cooked_patagonian_toothfish cooked_sablefish cooked_shark_flesh cooked_viper_flesh abyssal_vegetable_stew fish_soup gourd_fish_stew gourd_soup jellyfish_stew kelp_fish_stew kelp_soup mushroom_fish_stew mushroom_stew abyssal_survival_ration thermal_ration
（食べられて入手手段（レシピ・ルート）があるものだけ残す。外したものは報告）
クラゲ: atolla_jelly helmet_jelly（実際に刺すのはこの 2 種。damage source は mobAttack なので source_entity.type で判定）。
entity_hurt_player / player_killed_entity の entity は素の EntityPredicate オブジェクト `{"type": ...}` で書く（両ローダーで通る）。

### engineering（背景 `abyssia:textures/block/abyssal_rock_bricks.png`）
- engineering/root | — | habitat_constructor | task, no toast/announce | inventory_changed any(habitat_constructor, abyssal_drill, cobalt_ingot, nickel_ingot, zinc_ingot) | Seabed Engineering — Build, power and mine at the bottom of the sea | 海底工学 — 海の底で建て、電気を起こし、掘る
- engineering/deep_metallurgy | root | cobalt_ingot | task | inventory_changed any(金属インゴット 16) | Deep Metallurgy — Obtain a metal refined from deep-sea ore | 深海冶金 — 深海の鉱石から金属を精錬する
- engineering/alloy_smith | deep_metallurgy | tungsten_alloy_ingot | goal | inventory_changed each(合金 7) | Alloy Smith — Obtain every kind of alloy | 合金職人 — すべての合金を手に入れる
- engineering/rare_metals | deep_metallurgy | neodymium_ingot | challenge, xp 150 | inventory_changed each(neodymium_ingot, yttrium_ingot, tellurium_ingot, iridium_ingot, platinum_ingot, molybdenum_ingot, vanadium_ingot) | Rare Metal Collector — Obtain seven rare metals | レアメタル収集家 — 7 種のレアメタルを手に入れる
- engineering/dont_lick_it | deep_metallurgy | uranium_ingot | task, hidden | inventory_changed uranium_ingot | Don't Lick It — Obtain a uranium ingot | なめちゃダメ — ウランのインゴットを手に入れる
- engineering/crystal_collector | root | abyssal_crystal_shard | goal | inventory_changed each(deep_crystal_shard, pressure_crystal_shard, thermal_crystal_shard, abyssal_crystal_shard) | Crystal Collector — Collect all four kinds of crystal shard | 結晶コレクター — 4 種類の結晶の欠片を集める
- engineering/first_module | root | habitat_constructor | task | build{} | Home, Wet Home — Complete your first habitat module | 海底のわが家 — はじめて居住モジュールを完成させる
- engineering/airlock | first_module | minecraft:iron_trapdoor | task | build{build=entrance} | Airlock — Build an entrance module | エアロック — 出入口モジュールを建てる
- engineering/moon_pool | first_module | minecraft:water_bucket | goal | build{build=moon_pool} | Moon Pool — Build a moon pool | ムーンプール — ムーンプールを建てる
- engineering/scan_room | first_module | lidar_scanner | task | build{build=scan_room} | Eyes on the Seafloor — Build a scan room | 海底を見通す部屋 — スキャンルームを建てる
- engineering/seabase_architect | first_module | minecraft:glass_pane | challenge, xp 100 | build each(foundation, room, corridor, entrance, moon_pool, scan_room) | Seabase Architect — Build every type of habitat module | 海底基地の建築家 — すべての種類の居住モジュールを建てる
- engineering/power_on | first_module | minecraft:redstone | task | any(build{biofuel_generator, geothermal_generator, current_turbine}, placed_block{hydrothermal_generator, auxiliary_generator}) | Power On — Build or place a generator | 電源オン — 発電機を建てる、または設置する
- engineering/clean_energy | power_on | minecraft:lightning_rod | goal | requirements [[build current_turbine], [build geothermal_generator, placed_block hydrothermal_generator]] | Clean Energy — Build a current turbine and a geothermal or hydrothermal generator | 海のクリーンエネルギー — 海流タービンと、地熱か熱水の発電機を建てる
- engineering/blackout | power_on | minecraft:redstone_lamp | task, hidden | event{habitat_blackout} | Blackout — Run out of air in an unpowered habitat | 停電 — 電気の止まった居住区で息が苦しくなる
- engineering/excavation | root | drill_head | task | build{build=abyssal_excavator} | Deep Excavation — Build an abyssal excavator | 海底採掘 — 深海掘削機を建てる
- engineering/heavy_industry | excavation | electric_abyssal_drill | goal | build{build=abyssal_excavator_mk2} | Heavy Industry — Build an abyssal excavator Mk2 | 重工業 — 深海掘削機 Mk2 を建てる
- engineering/breadcrumbs | root | waypoint_beacon | task | placed_block waypoint_beacon | Breadcrumbs — Place a waypoint beacon to find your way home | 帰り道の灯 — 帰り道の目印にウェイポイントビーコンを置く
- engineering/lantern_in_the_dark | breadcrumbs | waypoint_beacon | goal | placed_block waypoint_beacon + location_check position.y<=-860 | Lantern in the Dark — Place a waypoint beacon below Y -860 | 闇の灯台 — Y -860 より深い場所にウェイポイントビーコンを置く
- engineering/shipwright | root | submarine | task | inventory_changed submarine | Shipwright — Build a submarine | 造船技師 — 潜水艇を造る
- engineering/dry_dock | shipwright | submarine | task | build{build=submarine_dock} | Dry Dock — Build a submarine dock | 潜水艇ドック — 潜水艇ドックを建てる

金属インゴット 16: cobalt iridium lead manganese molybdenum neodymium nickel platinum tellurium thorium titanium tungsten uranium vanadium yttrium zinc（各 `_ingot`）
合金 7: abyssal_alloy conductive_alloy corrosion_alloy heat_resistant_alloy high_strength_alloy thermal_alloy tungsten_alloy（各 `_ingot`）

## 名前・キー
- advancement id: `abyssia:<tab>/<name>`。lang: `advancements.abyssia.<tab>.<name>.title` / `.description`。
- 生成ツールは参照する id をすべて検証する（アイテム/ブロック=lang の item./block. キー、エンティティ=entity. キー、バイオーム=worldgen/biome、scan target=data/abyssia/scan_targets、技術=data/abyssia/technologies）。無いものはエラーで止める。アイコンが無ければ近いものに替えて報告。日本語の生物名は lang の既存訳に合わせる。

## 1.20.1 変換（mc_format.py --to-forge に追加）
フォルダ `advancement/`→`advancements/`、icon `{"id"}`→`{"item"}`、アイテム述語の `items` は常に配列、location の `biomes`→`biome`（単数。生成側は 1 条件 1 バイオームにする）、`structures`→`structure`。placed_block の書式は 1.20.1 のバニラ jar 内の advancement JSON（例 husbandry/plant_seed.json）で確かめて合わせる（推測で決めない）。

## 完了条件
1. NeoForge と Forge の両方でビルドが通る。
2. scratch サーバーの起動ログに advancement の読み込みエラーがなく、`abyssia:` の advancement が全件読み込まれる（両ローダー）。
3. 実プレイヤーで独自トリガーが発火する（dive の深度・scan・research・build を最低限）。確認できない項目は「未検証」と報告する。
4. ja_jp / en_us に全タイトル・説明がある。
