# blockbench-mcp

Blockbench デスクトップ版を MCP サーバー化して、Claude Code からモデルを直接操作するための**サードパーティ製プラグインの固定コピー置き場**。

| | |
|---|---|
| 配布元 | https://github.com/jasonjgardner/blockbench-mcp-plugin (GPL-3.0) |
| 取得元URL | https://jasonjgardner.github.io/blockbench-mcp-plugin/mcp.js |
| バージョン | v1.10.0 (2026-10-02 取得) |
| SHA-256 | `1D164D43BA6C938AA1B4F3A5D00A5AEB1A4739477FCA4B76102BCB2958E2EE65` |
| 対象 | Blockbench 5.2.1 (desktop) |

`mcp.js` は 900KB のバンドルなので git 管理しない (`.gitignore`)。無ければ取得元URLから落として SHA-256 を照合する。
**URL読み込みはしない**: 起動のたびに外部コードを実行することになるため、必ずこの固定コピーをファイルから読み込む。更新するときはハッシュを更新してから読み込み直す。

## 有効化 (Blockbench 側・初回のみ)

1. Blockbench: `File > Plugins > Load Plugin from File` → `tools/blockbench-mcp/mcp.js`
2. ネイティブモジュール利用の許可ダイアログが出たら内容を確認して許可
3. `Settings > General` (MCP の項目):
   - **Enable risky_eval を OFF** (受け取った JS を無条件に実行するツール。既定は ON)
   - MCP Server Host は `localhost` のまま (`0.0.0.0` にしない)。Port 3000 / Endpoint `/bb-mcp`

## Claude Code 側

登録済み (local scope、このプロジェクトのみ):

```bash
claude mcp add --transport http blockbench http://localhost:3000/bb-mcp
claude mcp list        # Blockbench でプラグインを読み込んだ後なら Connected になる
```

Blockbench を閉じている間は `ECONNREFUSED` で正常。サーバーはプラグインが読み込まれている間だけ存在する。
ツールは Claude Code の**新しいセッション**から見える。

## 注意

- 保存した `.bbmodel` には、AI が編集したことを示すメタデータ (`ai_used` / `ai_agents`) が付く (設定 `mcp_disclose_ai_usage`)。
- 生成モデルは `tools/bbmodel-generator` が正。手で作り込んだ変更は generator に戻らないので、再生成で消える点に注意。
