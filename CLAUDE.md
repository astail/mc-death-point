# CLAUDE.md

Claude がこのリポジトリで作業する際の開発メモ（Paper プラグイン）。

## プラグインの目的

DeathPoint は、**プレイヤーが死亡した瞬間にその座標（ワールド・次元・x/y/z）を全体チャットへ流す**。
併せて各プレイヤーの**最後の死亡地点を記録**し、本人は `/deathpoint` で確認できる。
死亡地点へ戻ってアイテムを回収しやすくするためのプラグイン。サーバー側のみで動き、クライアント MOD は不要。

## ビルド要件

- Java 25 + Maven。生成物は `DeathPoint-1.1.0.jar`。
- 唯一の依存は `io.papermc.paper:paper-api:26.2.build.40-alpha`（provided）。
- ローカルビルドは `./deploy.sh`（Homebrew `openjdk@25` を想定）。

## アーキテクチャ構成

- **DeathPointPlugin**: 本体。設定読込、`PlayerDeathEvent`（MONITOR）の監視、死亡座標の組み立て・全体通知、
  プレイヤーごとの最後の死亡地点（`lastDeath`）の管理と `deaths.yml` への永続化（`loadDeaths` / `saveDeaths`）。
- **DeathPointCommand**: `/deathpoint <status|reload>` の実処理とタブ補完。
- **DiscordNotifier**: DiscordSRV 連携。DiscordSRV が導入・有効なとき、死亡座標ログを Discord チャンネルへ送る。

## 設計上の要点

- **死亡の検知は `PlayerDeathEvent`（`EventPriority.MONITOR`）**: イベントを書き換えず観測するだけなので
  MONITOR を使う（他プラグインの処理後に動く）。`event.getEntity().getLocation()` で死亡座標を取得する。
- **全体通知は受信者を絞れる**: `getOnlinePlayers()` を走査し、`deathpoint.receive`（既定 true）を持つ
  プレイヤーにだけ `sendMessage` する。`notify-sound: true` なら各受信者にベル音（`block.note_block.bell`）を鳴らす。
  単純な一斉 `broadcast` ではなくループにしているのは、権限フィルタと本人ごとの音再生を両立するため。
- **本文はプレースホルダ式テンプレート**: `message`（config）内の `%player% / %world% / %dimension% / %x% / %y% / %z%`
  を `substitute()` で Component へ置換する。座標は AQUA、プレイヤー名・ワールド名は WHITE、地の文は GRAY で着色し、
  先頭に `[DeathPoint]`（RED）+ `☠`（RED）を付ける。未知のトークンは地の文として残す。
- **次元ラベル**: `World#getEnvironment()` を `オーバーワールド / ネザー / ジ・エンド` にマップする（`dimensionLabel`）。
- **DiscordSRV 連携はリフレクション**: `DiscordNotifier` が `github.scarsz.discordsrv.DiscordSRV`（`getPlugin` →
  `getDestinationTextChannelForGameChannelName(name)` / `getMainTextChannel` → `sendMessage(...).queue()`）を
  **すべてリフレクションで呼ぶ**。DiscordSRV（同梱 JDA）をコンパイル依存に含めないので、未導入でもそのまま動き、
  JDA のシェード差異にも強い。`plugin.yml` の `softdepend: [DiscordSRV]` で居れば先にロードさせる。送信失敗は
  握りつぶして警告ログのみ（死亡通知本体を壊さない）。Discord 用本文は色を持たないプレーンテキストで、
  Component 版 `substitute()` とは別の純粋関数 `substitutePlain()`（テスト対象）で組み立て、先頭に `☠` を付ける。
  config は `discord.enabled`（既定 true）/ `discord.channel`（既定 `global`、空欄でメイン）。
- **死亡地点は永続**: `lastDeath`（`UUID -> DeathRecord`）は死亡の都度 `deaths.yml`（`<dataFolder>/deaths.yml`）へ
  保存し、`onEnable` で復元する。`DeathRecord` は `World` 参照を持たずワールド名 + ブロック座標だけを持つ
  （ワールド未ロードでも壊れない）。表示時に `getServer().getWorld(name)` で次元ラベルを補う。
- **権限は 2 段階**: `status` は `deathpoint.use`（既定 true）。サーバー全体に影響する `reload` は
  `deathpoint.manage`（既定 op）を別途要求（`DeathPointCommand#requireManage`）。受信可否は `deathpoint.receive`（既定 true）。
- **`enabled` は全体スイッチ**: `config.yml` の `enabled` が false の間は通知も記録も行わない。`/deathpoint reload` で
  config を読み直す（死亡地点 `lastDeath` はメモリのまま保持）。

## 既知の制限 / 注意

- 死亡地点は「最後の 1 件」のみ保持（履歴なし）。新たに死ぬと上書きされる。
- `lastDeath` は `deaths.yml` に永続化され、サーバー再起動後も維持される。
- 通知は座標の整数（ブロック単位）を表示する。小数点以下は丸めない（`getBlockX/Y/Z`）。
- DiscordSRV 連携は API をリフレクション解決するため、DiscordSRV 側の大きな API 変更時は `DiscordNotifier` の
  メソッド名（`getMainTextChannel` など）の追従が必要。連携状態は `/deathpoint status` の「Discord」欄で確認できる。

## リリース手順

- セマンティックバージョニング。`v*` タグの push で GitHub Actions（`.github/workflows/build.yml`）がビルドし、
  `gh release create --generate-notes` で jar を添付する。
- サーバーへの配置（Releases から DL、または Docker `itzg/minecraft-server` の `PLUGINS` 環境変数で自動 DL）は
  README の「サーバーへの配置」を参照。
