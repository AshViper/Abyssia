# OL02 油昆布の油嚢を発光
依頼: inbox/requests/20261003-211056.json。ChatGPT 原本: `PL02-OL02-chatgpt-raw.md` (OL02 節)。
tier: light
goal: 熟した oil_kelp (油嚢が付いた状態 = 現在 oil_kelp_ripe モデル) だけ、油嚢部分が暗くても光って見え、光レベル 4 で周囲を淡く照らす。収穫で消える。
- 光レベル: 熟した状態の blockstate だけ lightLevel 4 (他は 0)。
- 見た目: oil_kelp_ripe の十字モデルに油嚢ピクセルだけのエミッシブ重ね層 (`oil_kelp_ripe_glow`, forge_data emissive / NeoForge は neoforge_data、既存の発光重ね層と同じ方式)。色は黄緑〜琥珀。
- テクスチャ (Claude 判断): 油嚢のマスクはピクセル単位で現行テクスチャと一致させる必要があるので、ChatGPT 製の oil_kelp_ripe (ロック済み) から油嚢 (橙) ピクセルを抜き出して明るい琥珀〜黄緑に塗った派生テクスチャとして生成する (derive 方式、ロックする)。
- シェーダー: 既存のエミッシブ層と同じ扱い (Iris/Oculus で黒くならない)。
accept: 未熟は光らない / 熟すと油嚢だけ光る・周囲光 4 / 収穫で消える / 成長・収穫・再生は変わらない / 両ローダー。
