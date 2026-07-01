# DeathPoint

A Paper plugin that **broadcasts the coordinates of a player's death to the whole server chat the moment they die** (server-side only).

No more "I have no idea where I died, so I can't get my stuff back." When someone dies, the world, dimension, and coordinates (x, y, z) are shown to everyone in chat. Each player's **last death point is also recorded**, so they can check it any time with `/deathpoint`.

> **No client mod required.** Just install the plugin on the server — vanilla clients work as-is.

## The problem it solves

"I died with a full set of gear and can't remember where it happened."
DeathPoint announces the coordinates to everyone the instant you die and keeps a record for you, **making it much easier to return to your death point and recover your items.**

## Features

- **Broadcasts death coordinates to everyone**: when a player dies, it announces something like `astail died at (128, 72, -340) in the Nether (world_nether)` to all players.
- **Dimension labels**: shows Overworld / Nether / The End (in Japanese by default; fully customizable).
- **Records the last death point**: each player's most recent death is saved to `deaths.yml` and survives restarts, viewable with `/deathpoint`.
- **Sound**: optionally play a bell sound to receivers when a death is announced (toggle in `config.yml`).
- **Custom message**: change the announcement text freely with placeholders.
- **DiscordSRV integration**: if [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV) is installed, the same death-coordinate log is relayed to Discord automatically (does nothing when it isn't installed).

## Requirements

- Server: Paper 26.2 (experimental channel)
- Java: 25
- Clients: vanilla (no mods, server-side only)

## Installation

1. Drop `DeathPoint-1.1.0.jar` into `plugins/` and restart.
2. From then on, whenever a player dies, the death coordinates are broadcast to chat. No configuration needed.

Example:

```text
[DeathPoint] ☠ astail が ネザー（world_nether）の座標 (128, 72, -340) で力尽きました
```

## Usage

Installing it is enough to enable death broadcasts. To change behavior, use `config.yml` (below) or in-game commands.

- View current settings and your own last death point → `/deathpoint` (or `/deathpoint status`)
- Apply changes you made to `config.yml` → `/deathpoint reload`

## Commands

| Command | Description | Permission |
|---|---|---|
| `/deathpoint status` | Show current settings and your last death point | `deathpoint.use` |
| `/deathpoint reload` | Reload the configuration | `deathpoint.manage` |

Alias: `/dp`

## Permissions

| Permission node | Description | Default |
|---|---|---|
| `deathpoint.receive` | Whether the player receives death broadcasts in chat | `true` (everyone) |
| `deathpoint.use` | Self-facing `/deathpoint status` | `true` (everyone) |
| `deathpoint.manage` | Server-wide operations such as `reload` | `op` |

## Configuration (`config.yml`)

```yaml
enabled: true              # Enable death broadcasts server-wide (apply with /deathpoint reload after editing)
notify-sound: true         # Play a bell sound to receivers when a death is announced
message: "%player% が %dimension%（%world%）の座標 (%x%, %y%, %z%) で力尽きました"
discord:
  enabled: true            # When DiscordSRV is installed, relay the death-coordinate log to Discord too
  channel: "global"        # Destination channel name (defined in DiscordSRV's Channels; empty = main channel)
```

Placeholders available in `message`:

| Placeholder | Meaning |
|---|---|
| `%player%` | Name of the player who died |
| `%world%` | World name (e.g. `world`, `world_nether`) |
| `%dimension%` | Dimension label (Overworld / Nether / The End) |
| `%x%` `%y%` `%z%` | Death coordinates (integer block coordinates) |

## DiscordSRV integration

On servers that run [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV), DeathPoint relays **the same death-coordinate log it sends to in-game chat to Discord as well**. No client mod and almost no extra setup is required; when DiscordSRV is absent the feature is automatically disabled (the plugin still works as usual).

- **Toggle**: `discord.enabled` in `config.yml` (default `true`).
- **Destination**: set `discord.channel` to a channel name defined in DiscordSRV's `Channels` (default `global`). Leave it empty to use the main channel.
- **Body**: the same `message` is sent as plain text (prefixed with `☠`); no colors.
- **Status**: check the linkage state (`連携中` / `待機` / `OFF`) in the `Discord` field of `/deathpoint status` or in the startup log.

Example sent to Discord:

```text
☠ astail が ネザー（world_nether）の座標 (128, 72, -340) で力尽きました
```

## How it works / technical notes

- Listens to `PlayerDeathEvent` (`EventPriority.MONITOR`) and reads the location at death.
- Substitutes the coordinates into the message template and sends it to every online player who has `deathpoint.receive` (with an optional bell sound via `notify-sound`).
- At the same time it saves each player's last death point to `deaths.yml` (`<dataFolder>/deaths.yml`) so it can be shown to the player via `/deathpoint`.
- If DiscordSRV is installed, the same coordinates are sent as plain text to a Discord channel through the DiscordSRV API (via reflection). DiscordSRV is not a compile-time dependency, so the plugin runs fine without it.

### Limitations

- Only the single most recent death point is kept per player (no history).
- While `enabled: false`, neither broadcasts nor death-point recording happen (Discord relaying stops too).

## Build

```bash
./deploy.sh        # Native on Mac (JDK 25 + Maven). Output: target/DeathPoint-1.1.0.jar
# or
mvn -B clean package
```

Pushing a `v*` tag triggers GitHub Actions (`.github/workflows/build.yml`) to build and attach the jar to the release.

## Deploying to a server

Place the jar in the server's `plugins/` and restart. Get the jar one of two ways (A or B). With Docker (itzg/minecraft-server), you can also use the auto-download approach below.

### A. Use a release build (no build required, recommended)

Download the latest `DeathPoint-<version>.jar` from [Releases](https://github.com/astail/mc-death-point/releases). No JDK or Maven needed.

```bash
# Download the latest release jar (using the gh CLI)
gh release download --repo astail/mc-death-point --pattern '*.jar'
```

### B. Build it yourself

Follow [Build](#build) to produce `target/DeathPoint-1.1.0.jar`.

### Placement

Put the jar in the server's `plugins/` and restart.

```bash
# Bind mount (copy to the host-side plugins directory)
cp target/DeathPoint-1.1.0.jar /path/to/data/plugins/
docker restart <container-name>

# Named volume etc. (copy directly into the container)
docker cp target/DeathPoint-1.1.0.jar <container-name>:/data/plugins/
docker restart <container-name>
```

### Docker Compose (itzg/minecraft-server) auto-download

With the [`itzg/minecraft-server`](https://github.com/itzg/docker-minecraft-server) image, you don't need the jar locally — just **list the release URL in the `PLUGINS` environment variable** and it is downloaded into `plugins/` at startup.

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
        https://github.com/astail/mc-death-point/releases/download/v1.1.0/DeathPoint-1.1.0.jar
    volumes:
      - ./data:/data
    restart: unless-stopped
```

`PLUGINS` accepts multiple newline-separated entries. When you update the version, change `v1.1.0` and the filename in the URL to match the new release (e.g. `.../download/v1.1.0/DeathPoint-1.1.0.jar`).

You'll see this in the startup log on success:

```text
[DeathPoint] DeathPoint を有効化しました（状態: ON / 通知音: あり / Discord連携: 連携中）。
```

## License

MIT License — see [LICENSE](LICENSE).
