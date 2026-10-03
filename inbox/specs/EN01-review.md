# EN01 review (ChatGPT 検査 2026-10-03, round 1)
添付: inbox/designs/EN01-ingame.png (霧の比較 y-164/-300 + アイコン 5 種) と自動テストのログ。
ChatGPT の結論: 要修正 2 点、他は仕様どおり (速度・デバフ付与源・醸造・料理・アイコン・Murk/Deep Sight の相殺)。

1. 「deep_swimmer に Frost Walker 排他が無い」→ 誤指摘。DeepSwimmerEnchantment.checkCompatibility で DEPTH_STRIDER と FROST_WALKER の両方を排他済み (ログに出していなかっただけ)。
2. 「Deep Sight が +64/+144 相当でなく x2.5/x4」→ 仕様の前提誤り。仕様は深海霧 144 を前提にしたが、実際の霧距離は 96 (浅い)〜10 (最深)。+64 を足すと最深部でも 74 になり深海の暗さが消える。倍率 (I x2.5, II x4, 上限 96) を維持 (Claude 判断、flow log に記録)。実測: y-164 17.5→42.5→68、y-300 9.9→24.1→38.7。
結果: 修正なしで合格扱い。

## NeoForge 1.21.1 (2026-10-03)
自動テスト (inbox/designs/EN01-neo-ingame.png): 速度・醸造・料理・排他・宝物タグ・被弾デバフ・熱処理 (6 回ずつ) すべて Forge と一致。
差分: NeoForge の深海霧は距離固定 144 (VD01) で深さを霧の開始位置と暗さで表すため、Forge の倍率 (上限 96) では効かなかった。NeoForge は仕様原文どおり far +64/+144、霧の開始を表層側へ I 35% / II 65% 寄せる。実測 y-160/-332: 144 → 208 → 288、Murk 72、Murk+II 287。
