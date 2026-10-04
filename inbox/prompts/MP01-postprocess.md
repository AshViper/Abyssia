# MP01 アイコンの後処理

ChatGPT のシート (inbox/textures/sheets/MP01.png) は 1 マス約 32px・アイコン約 22 マスで、16x16 にちょうど合っていない。
sheets.py の平均縮小では海図の赤い ✕ が消えたので、bbox を 16 分割して次の方法で 1 ピクセルずつ取った:
- deep_sea_map: 各セル中心の画素 (nearest)。背景ならセルの中央値
- abyss_chart: セルの中央値。ただしセルの 25% 以上が赤 (R>150, G<90) なら赤の中央値 (✕ を残す)
出力は textures/item と tools/texture_locks の両方にロック済み。
