# AB02 環境ギミック（HazardZone）メモ

`com.abyssia.hazard.HazardZone`（サーバー、PlayerTickEvent.Post、`pulse_ticks` ごと = 既定 1 秒）。
対象: オーバーワールドの Y < -376、サバイバル/アドベンチャー、潜水艦外、居住区モジュール外（`HabitatBases.moduleAt`）。
危険の判定はバイオームタグ `abyssia:hazard/toxic|heat|cold`（gen_worldgen.py が生成）。毒は目の位置、熱/寒は足元。強度は帯: B' 1 / C 2 / D 3 / E 4（`intensity_override` で固定可）。ダメージは毎秒換算（pulse_ticks を変えても同じ）。

| 危険 | 対策 | 効果（未対策） |
|---|---|---|
| 毒ガス | ヘルメット(tag `hazard/toxic_proof_head`) + 胸(tag `hazard/toxic_proof_chest`) の両方 | Poison(強度-1) 毎パルス、強度3以上で Wither I、+魔法ダメージ。対策中はタンクの耐久が 3 パルスごとに 1 減る |
| 灼熱 | tag `hazard/heat_proof` の防具 1 点につき 25% 軽減（4 点で無効）、火炎耐性で完全無効 | 炎ダメージ（発火しない）= heat_damage_per_level × 強度 |
| 極寒 | tag `hazard/cold_proof` の防具 1 点につき 25% 軽減、熱源（thermal_vent / molten_volcanic_rock / magma_block）が 6 ブロック以内なら停止 | cold_shock(強度-1) + 凍結ダメージ。連続パルスで最大 +75% まで累積 |

タグ編集: `data/abyssia/tags/item/hazard/{toxic_proof_head,toxic_proof_chest,heat_proof,cold_proof}.json`（既存アイテム ID のみ）。
Config `[hazard]`: enabled, pulse_ticks(20), toxic_damage_per_level(0.5), heat_damage_per_level(1.0), cold_damage_per_level(0.75), intensity_override(0)。
警告: 種別に入ったとき（と未対策のまま 10 秒ごと）にアクションバー `message.abyssia.hazard.<type>[_protected]`。文言は tools/lang_parts/ab02_hazards.json。
未実装/制限: タンクの空気残量の概念は無い（装着タグのみ）。毒は水中でも判定（洞窟は全て水のため）。耐熱専用の新装備・新アイテムは無し（タグ拡張で対応）。
