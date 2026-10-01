# P01 NeoForge 1.21.1 への移植（ブランチ NeoForge1.21.1）
tier: heavy
goal: Forge 1.20.1 版（main / Forge1.20.1 ブランチ）の機能を減らさずに、NeoForge 1.21.1（Java 21）で動くようにする。作業フォルダは F:\Java\Abyssia-NeoForge（git worktree）。

constraints:
- 運用（ユーザー決定）:
  - main は Forge 版のまま。新機能は Forge で作ってから NeoForge へ移植する。
  - そのため、移植は「機械的に追従しやすい」形にする。不要な再設計や命名変更はしない。パッケージ構成、クラス名、modid はそのまま。
- ビルド:
  - ModDevGradle（NeoForge 公式 MDK の方式）を使い、Java 21 にする。NeoForge 21.1.x の最新安定版と、対応する parchment（任意）を maven で確認する。
  - mods.toml は META-INF/neoforge.mods.toml。
  - 開発用の Embeddium / Oculus（libs/）は外す。NeoForge 用の Sodium / Iris を足すのは任意で、後回しでよい。
- 反射（ObfuscationReflectionHelper の SRG 名、たとえば f_188607_）は、Access Transformer（META-INF/accesstransformer.cfg）かフィールド名に置き換える。NeoForge 1.21.1 は実行時も Mojang の名前。
- データ:
  - 1.21 のフォルダ名に変える: tags/blocks→block、items→item、entity_types→entity_type、fluids→fluid、loot_tables→loot_table、recipes→recipe、advancements→advancement、structures→structure。
  - レシピ JSON の形式を変える（result は {"id","count"}、など）。
  - `forge:` タグは `c:` に移す。biome modifier は neoforge: の形式にする。
  - pack.mcmeta の pack_format を 1.21.1 用にする。
- 生成スクリプト（tools/*.py）が書き出す先と書式も 1.21 用に直す。直したら、再実行しても同じ出力になること。
  - 手で直した JSON を生成スクリプトがあとで上書きしないようにする。
  - gen_deep_assets.py はテクスチャを上書きするので、実行しないか、テクスチャの部分を外して実行する。
- 不具合の扱い: 1.20.1 で動いていた挙動（ワールド生成、M01 と M02 の深海層、既定ワールドのプリセット、ocean_world、生物、演出）を変えない。
- gradle の実行と runServer は、この worktree の中だけで行う（F:\Java\Abyssia の run/ と gradle には触らない）。

## 段階
1. P01a（Opus）ビルド環境:
   - build.gradle、settings.gradle、gradle.properties、wrapper、neoforge.mods.toml、AT を用意する。
   - Java 21 で compileJava を回し、エラーをパッケージ別に数えて一覧（inbox/specs/P01-errors.md）を作る。
   - 全体に共通する機械的な置換（import、登録 API、イベントバスなど）は、この段階でまとめて済ませる。
2. P01b〜（並列）パッケージ別にコンパイルエラーを直す。worldgen と描画は Opus、その他は Sonnet。
3. P01x データとスクリプト: リソースのフォルダ名と書式の移行、tools の 1.21 対応。
4. 検証:
   - runServer で起動し、既定ワールド（バニラ＋割れ目＋深海層）と ocean_world を確かめる。M02 と同じ手順で、/abyssia map、割れ目の水の連続、深海層に空気や溶岩がないこと。
   - 可能なら runClient も起動する。
