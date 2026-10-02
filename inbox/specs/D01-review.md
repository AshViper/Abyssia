# D01 review (ChatGPT, 2026-10-02)
総合判定: PASS (1回目)
- 4点構成 / 空気補助 (helmet 80→140 CD40, tank +40 CD60, 耐久-1) / 泳速 +3 +15 +5(set) / 暗視なし / バニラ素材: OK
- 差異: tank の部位 (ChatGPT 原案 特殊/携行 → CHEST)、tank レシピ (` I `/`IKI`/`III`) → 実装版を正式採用 (D01-entry-diving-gear.md に反映済み)
- 実機: inbox/designs/D01-ingame.png (防具スタンド正面/背面、ホットバー、簡易ヘルムのみで水中20秒 体力満タン・外すと溺水)
- 追加 (ユーザー要望): 全防具素材の装着テクスチャを tools/armor_layers.py で新規生成 (abyssal_alloy / diving_alloy / pressure_alloy / entry_diving)
- 修正: onInventoryTick を override した防具 (deep_diver_helmet / pressure_diver_helmet / dive_tank / entry helmet・tank) が super を呼ばず
  ItemStack.inventoryTick (popTime) が止まり、ホットバーでアイコンが潰れていた → super 呼び出し追加
