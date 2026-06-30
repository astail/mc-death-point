# DeathPoint

**プレイヤーが死亡した瞬間に、その座標を全体チャットへ流す**プラグインです（Paper 用 / サーバー側のみ）。

「どこで死んだか分からなくなってアイテムを回収できない」をなくします。誰かが死ぬと、ワールド・次元・座標 (x, y, z) を全員のチャットに表示します。さらに各プレイヤーの**最後の死亡地点を記録**し、本人は `/deathpoint` でいつでも確認できます。

> **クライアント MOD は不要です。** サーバーにこのプラグインを入れるだけで、バニラのクライアントでもそのまま動作します。

## 解決する課題

「装備一式を持ったまま死んだのに、どこで死んだか思い出せない」。
DeathPoint は死亡の瞬間に座標を全体へ知らせ、本人にも記録を残すので、**デスポイントへの帰還とアイテム回収が楽になります**。

## 主な機能

- **死亡座標を全体チャットへ通知**: 誰かが死ぬと、`%player% が %dimension%（%world%）の座標 (x, y, z) で力尽きました` のように全員へ流します。
- **次元名つき**: オーバーワールド / ネザー / ジ・エンドを日本語で表示します。
- **最後の死亡地点を記録**: 各プレイヤーの直近の死亡地点を `deaths.yml` に保存し、再起動後も `/deathpoint` で確認できます。
- **通知音**: 死亡通知が流れたときにベル音を鳴らせます（`config.yml` で ON/OFF）。
- **メッセージ変更**: 通知文をプレースホルダ付きで自由に差し替え可能。
- **DiscordSRV 連携**: [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV) が導入されていれば、同じ死亡座標ログを Discord にも自動で流します（未導入なら何もしません）。

## 動作要件

- サーバー: Paper 26.1.2（build 69 以上）
- Java: 25
- クライアント: バニラで可（MOD 不要・サーバー側のみ）

## 導入

1. `DeathPoint-1.0.0.jar` を `plugins/` に置いてサーバーを再起動します。
2. 以降、プレイヤーが死亡すると全体チャットに死亡座標が流れます。設定は不要です。

例:

```text
[DeathPoint] ☠ astail が ネザー（world_nether）の座標 (128, 72, -340) で力尽きました
```

## 使い方

導入するだけで全体への死亡通知が有効になります。挙動を変えたいときは `config.yml`（後述）か、ゲーム内コマンドで調整します。

- 現在の設定や自分の最後の死亡地点を見たい → `/deathpoint`（または `/deathpoint status`）
- 設定（`config.yml`）を変更して反映したい → `/deathpoint reload`

## コマンド

| コマンド | 説明 | 権限 |
|---|---|---|
| `/deathpoint status` | 現在の設定と、自分の最後の死亡地点を表示 | `deathpoint.use` |
| `/deathpoint reload` | 設定を再読み込み | `deathpoint.manage` |

エイリアス: `/dp`

## 権限

| 権限ノード | 説明 | 既定 |
|---|---|---|
| `deathpoint.receive` | 死亡通知を全体チャットで受け取れるか（受信可否） | `true`（全員） |
| `deathpoint.use` | `/deathpoint status` の自分向け操作 | `true`（全員） |
| `deathpoint.manage` | `reload` などサーバー全体に影響する操作 | `op` |

## 設定（`config.yml`）

```yaml
enabled: true              # サーバー全体で死亡通知を有効にするか（編集後 /deathpoint reload で反映）
notify-sound: true         # 死亡通知が流れたときに受信者へベル音を鳴らすか
message: "%player% が %dimension%（%world%）の座標 (%x%, %y%, %z%) で力尽きました"
discord:
  enabled: true            # DiscordSRV 導入時、死亡座標ログを Discord にも流すか
  channel: "global"        # 送信先チャンネル名（DiscordSRV の Channels で定義した名前。空欄でメイン）
```

`message` で使えるプレースホルダ:

| プレースホルダ | 内容 |
|---|---|
| `%player%` | 死亡したプレイヤー名 |
| `%world%` | ワールド名（例: `world`, `world_nether`） |
| `%dimension%` | 次元の日本語名（オーバーワールド / ネザー / ジ・エンド） |
| `%x%` `%y%` `%z%` | 死亡した座標（ブロック単位の整数） |

## DiscordSRV 連携

[DiscordSRV](https://github.com/DiscordSRV/DiscordSRV) を導入しているサーバーでは、ゲーム内チャットへ流すのと**同じ死亡座標ログを Discord にも自動送信**します。MOD も追加設定もほぼ不要で、DiscordSRV が無い環境ではこの機能は自動的に無効になります（プラグインはそのまま動作します）。

- **有効/無効**: `config.yml` の `discord.enabled`（既定 `true`）。
- **送信先**: `discord.channel` に DiscordSRV 側の `Channels` で定義したチャンネル名を指定します（既定 `global`）。空欄にするとメインチャンネルへ送ります。
- **本文**: ゲーム内と同じ `message` をプレーンテキスト（先頭に `☠`）にして送ります。色は付きません。
- **状態確認**: `/deathpoint status` の「Discord」欄、または起動ログで連携状態（`連携中` / `待機` / `OFF`）を確認できます。

Discord 側の送信例:

```text
☠ astail が ネザー（world_nether）の座標 (128, 72, -340) で力尽きました
```

## 仕組み / 技術メモ

- `PlayerDeathEvent`（`EventPriority.MONITOR`）を監視し、死亡時に座標を取得します。
- 取得した座標を本文テンプレートのプレースホルダへ差し込み、`deathpoint.receive` を持つオンライン全員のチャットへ流します（`notify-sound` でベル音を併用可）。
- 同時に各プレイヤーの最後の死亡地点を `deaths.yml`（`<dataFolder>/deaths.yml`）へ保存し、`/deathpoint` で本人へ表示できるようにします。
- DiscordSRV が導入されていれば、同じ死亡座標をプレーンテキストにして DiscordSRV の API（リフレクション経由）で Discord チャンネルへ送ります。DiscordSRV はコンパイル依存に含めていないため、未導入でもそのまま動きます。

### 制限

- 死亡地点は「最後の 1 件」のみ保持します（履歴は残しません）。
- `enabled: false` の間は全体通知も死亡地点の記録も行いません（Discord 送信も止まります）。

## ビルド

```bash
./deploy.sh        # Mac ネイティブ（JDK 25 + Maven）。生成物: target/DeathPoint-1.0.0.jar
# または
mvn -B clean package
```

`v*` タグを push すると GitHub Actions（`.github/workflows/build.yml`）がビルドし、リリースに jar を添付します。

## サーバーへの配置

サーバーの `plugins/` に jar を置いてサーバーを再起動します。jar の入手は次の 2 通り（A・B）です。Docker（itzg/minecraft-server）を使う場合は、後述の「Docker Compose で自動ダウンロード」も利用できます。

### A. リリース版を使う（ビルド不要・推奨）

[Releases](https://github.com/astail/mc-death-point/releases) から最新の `DeathPoint-<version>.jar` をダウンロードします。JDK や Maven は不要です。

```bash
# 最新リリースの jar をダウンロード（gh CLI を使う場合）
gh release download --repo astail/mc-death-point --pattern '*.jar'
```

### B. 自分でビルドする

[ビルド](#ビルド) の手順で `target/DeathPoint-1.0.0.jar` を生成します。

### 配置

入手した jar をサーバーの `plugins/` に置いてサーバーを再起動します。

```bash
# バインドマウントしている場合（ホスト側 plugins ディレクトリへコピー）
cp target/DeathPoint-1.0.0.jar /path/to/data/plugins/
docker restart <コンテナ名>

# 名前付きボリューム等の場合（コンテナへ直接コピー）
docker cp target/DeathPoint-1.0.0.jar <コンテナ名>:/data/plugins/
docker restart <コンテナ名>
```

### Docker Compose（itzg/minecraft-server）で自動ダウンロード

[`itzg/minecraft-server`](https://github.com/itzg/docker-minecraft-server) イメージを使う場合は、jar を手元に用意しなくても **`PLUGINS` 環境変数にリリースの URL を並べるだけ**で、起動時に自動ダウンロードして `plugins/` に配置できます。

```yaml
services:
  mc:
    image: itzg/minecraft-server
    tty: true
    stdin_open: true
    ports:
      - "25565:25565"
    environment:
      EULA: "TRUE"
      TYPE: "PAPER"
      VERSION: "26.2"
      PAPER_CHANNEL: "experimental"
      PLUGINS: |
        https://github.com/astail/mc-death-point/releases/download/v1.0.0/DeathPoint-1.0.0.jar
    volumes:
      - ./data:/data
    restart: unless-stopped
```

`PLUGINS` は改行区切りで複数指定できます。バージョンを更新したら、URL の `v1.0.0` とファイル名を新しいリリースに合わせて変更してください（例: `.../download/v1.0.0/DeathPoint-1.0.0.jar`）。

起動ログに以下が出れば成功です。

```text
[DeathPoint] DeathPoint を有効化しました（状態: ON / 通知音: あり / Discord連携: 連携中）。
```

## ライセンス

MIT License — [LICENSE](LICENSE) を参照。
