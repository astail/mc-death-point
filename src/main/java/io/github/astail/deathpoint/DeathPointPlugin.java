package io.github.astail.deathpoint;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DeathPoint 本体。
 * プレイヤーが死亡した瞬間に、その座標（ワールド・次元・x/y/z）を全体チャットへ流す。
 * 併せて各プレイヤーの「最後の死亡地点」を記録し、/deathpoint で本人が確認できるようにする。
 * サーバー側のみで動作（クライアント MOD 不要）。
 */
public final class DeathPointPlugin extends JavaPlugin implements Listener {

    /** 死亡通知のメッセージ本文の既定値。利用可能なプレースホルダは config.yml を参照。 */
    private static final String DEFAULT_MESSAGE = "%player% が %dimension%（%world%）の座標 (%x%, %y%, %z%) で力尽きました";

    /** 死亡通知時に鳴らす音（低めのベル）。 */
    private static final Sound DEATH_SOUND =
            Sound.sound(Key.key("block.note_block.bell"), Sound.Source.MASTER, 0.8f, 0.7f);

    /** 本文中の %placeholder% を検出する正規表現。 */
    private static final Pattern TOKEN = Pattern.compile("%[a-z_]+%");

    /** discord.channel の既定値。DiscordSRV の標準チャンネル名。 */
    private static final String DEFAULT_DISCORD_CHANNEL = "global";

    /** プレイヤーごとの「最後の死亡地点」。deaths.yml に永続化し、再起動後も保持する。 */
    private final Map<UUID, DeathRecord> lastDeath = new HashMap<>();

    private boolean active;
    private boolean notifySound;
    private String message;

    /** discord.enabled: DiscordSRV が動作中のとき、死亡座標ログを Discord にも流すか。 */
    private boolean discordEnabled;
    /** discord.channel: 送信先の DiscordSRV チャンネル名（空欄でメインチャンネル）。 */
    private String discordChannel;

    /** DiscordSRV 連携。未導入の環境では何もしない。onEnable で生成する。 */
    private DiscordNotifier discord;

    /** 死亡地点の永続化先（&lt;dataFolder&gt;/deaths.yml）。loadDeaths() で初期化される。 */
    private File deathsFile;

    /** 永続化用の死亡地点レコード（ワールド名 + ブロック座標）。World 参照は持たず再起動に強い。 */
    public record DeathRecord(String world, int x, int y, int z) {
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        if (!register()) {
            // コマンド登録に失敗した場合は disablePlugin 済み。成功ログも出さずに離脱する。
            return;
        }
        loadDeaths();
        discord = new DiscordNotifier(this);
        getLogger().info("DeathPoint を有効化しました（状態: " + (active ? "ON" : "OFF")
                + " / 通知音: " + (notifySound ? "あり" : "なし")
                + " / Discord連携: " + discordStatusLabel() + "）。");
    }

    @Override
    public void onDisable() {
        // メモリ上の死亡地点を最後に保存しておく（基本は死亡の都度に保存済み）。
        saveDeaths();
        lastDeath.clear();
    }

    /**
     * plugin.yml のコマンドとリスナーを登録する。
     * 成功時は true、コマンド未定義でプラグインを無効化した場合は false を返す。
     */
    private boolean register() {
        PluginCommand command = getCommand("deathpoint");
        if (command == null) {
            getLogger().severe("plugin.yml に deathpoint コマンドが定義されていません。プラグインを無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
        DeathPointCommand handler = new DeathPointCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        getServer().getPluginManager().registerEvents(this, this);
        return true;
    }

    /** config.yml を読み直して設定値を反映する。 */
    private void loadSettings() {
        reloadConfig();
        FileConfiguration config = getConfig();
        active = config.getBoolean("enabled", true);
        notifySound = config.getBoolean("notify-sound", true);
        message = config.getString("message", DEFAULT_MESSAGE);
        discordEnabled = config.getBoolean("discord.enabled", true);
        discordChannel = config.getString("discord.channel", DEFAULT_DISCORD_CHANNEL);
    }

    // ───────────────────────── 死亡地点の永続化 ─────────────────────────

    /** deaths.yml から各プレイヤーの最後の死亡地点を読み込む。ファイルが無ければ空のまま。 */
    private void loadDeaths() {
        deathsFile = new File(getDataFolder(), "deaths.yml");
        lastDeath.clear();
        if (!deathsFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(deathsFile);
        ConfigurationSection section = yaml.getConfigurationSection("deaths");
        if (section == null) {
            return;
        }
        for (String raw : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(raw);
            if (entry == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(raw);
                String world = entry.getString("world");
                if (world == null) {
                    continue;
                }
                lastDeath.put(id, new DeathRecord(world, entry.getInt("x"), entry.getInt("y"), entry.getInt("z")));
            } catch (IllegalArgumentException ex) {
                getLogger().warning("deaths.yml の不正な UUID をスキップします: " + raw);
            }
        }
    }

    /** 各プレイヤーの死亡地点を deaths.yml へ保存する。初期化前（loadDeaths 未実行）は何もしない。 */
    private void saveDeaths() {
        if (deathsFile == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, DeathRecord> e : lastDeath.entrySet()) {
            String base = "deaths." + e.getKey();
            DeathRecord record = e.getValue();
            yaml.set(base + ".world", record.world());
            yaml.set(base + ".x", record.x());
            yaml.set(base + ".y", record.y());
            yaml.set(base + ".z", record.z());
        }
        try {
            yaml.save(deathsFile);
        } catch (IOException ex) {
            getLogger().warning("deaths.yml の保存に失敗しました: " + ex.getMessage());
        }
    }

    // ───────────────────────── イベント ─────────────────────────

    /**
     * 死亡時に座標を記録し、全体チャットへ流す。
     * MONITOR にしているのはイベントを書き換えず観測するだけのため（他プラグインの処理後に動く）。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!active) {
            return;
        }
        Player victim = event.getEntity();
        Location loc = victim.getLocation();
        if (loc.getWorld() == null) {
            return;
        }
        lastDeath.put(victim.getUniqueId(),
                new DeathRecord(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        saveDeaths();
        announceDeath(victim, loc);
    }

    // ───────────────────────── 通知ロジック ─────────────────────────

    /**
     * 死亡地点を全体へ流す。deathpoint.receive を持つオンライン全員に届き、必要なら音も鳴らす。
     * さらに DiscordSRV が動作中で discord.enabled が true なら、同じ死亡座標を Discord にも送る。
     */
    private void announceDeath(Player victim, Location loc) {
        Component broadcast = renderBroadcast(victim, loc);
        for (Player viewer : getServer().getOnlinePlayers()) {
            if (!viewer.hasPermission("deathpoint.receive")) {
                continue;
            }
            viewer.sendMessage(broadcast);
            if (notifySound) {
                viewer.playSound(DEATH_SOUND, Sound.Emitter.self());
            }
        }
        if (discordEnabled && discord != null) {
            // Discord は色やコンポーネントを解釈しないため、プレーンテキストで送る。
            discord.send(discordChannel, renderPlain(victim, loc));
        }
    }

    /** 設定の本文テンプレートにプレースホルダを差し込み、プレフィックス + ☠ を付けた通知行を組み立てる。 */
    private Component renderBroadcast(Player victim, Location loc) {
        World world = loc.getWorld();
        Map<String, Component> tokens = new HashMap<>();
        tokens.put("%player%", Component.text(victim.getName(), NamedTextColor.WHITE));
        tokens.put("%world%", Component.text(world.getName(), NamedTextColor.WHITE));
        tokens.put("%dimension%", Component.text(dimensionLabel(world), NamedTextColor.WHITE));
        tokens.put("%x%", Component.text(loc.getBlockX(), NamedTextColor.AQUA));
        tokens.put("%y%", Component.text(loc.getBlockY(), NamedTextColor.AQUA));
        tokens.put("%z%", Component.text(loc.getBlockZ(), NamedTextColor.AQUA));
        return prefix()
                .append(Component.text("☠ ", NamedTextColor.RED))
                .append(substitute(message, tokens, NamedTextColor.GRAY));
    }

    /**
     * テンプレート文字列の %placeholder% を対応 Component へ置換する。
     * 未知のトークンとプレーンな文字列は baseColor の文字として扱う。
     */
    private static Component substitute(String template, Map<String, Component> tokens, NamedTextColor baseColor) {
        Component result = Component.empty();
        Matcher matcher = TOKEN.matcher(template);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                result = result.append(Component.text(template.substring(last, matcher.start()), baseColor));
            }
            Component replacement = tokens.get(matcher.group());
            result = result.append(replacement != null
                    ? replacement
                    : Component.text(matcher.group(), baseColor));
            last = matcher.end();
        }
        if (last < template.length()) {
            result = result.append(Component.text(template.substring(last), baseColor));
        }
        return result;
    }

    /** Discord 送信用に、本文テンプレートをプレーンテキストへ整形する（先頭に ☠ を付ける）。 */
    private String renderPlain(Player victim, Location loc) {
        World world = loc.getWorld();
        Map<String, String> tokens = new HashMap<>();
        tokens.put("%player%", victim.getName());
        tokens.put("%world%", world.getName());
        tokens.put("%dimension%", dimensionLabel(world));
        tokens.put("%x%", Integer.toString(loc.getBlockX()));
        tokens.put("%y%", Integer.toString(loc.getBlockY()));
        tokens.put("%z%", Integer.toString(loc.getBlockZ()));
        return "☠ " + substitutePlain(message, tokens);
    }

    /**
     * テンプレート文字列の %placeholder% をプレーン文字列へ置換する（Component を使わない版）。
     * 未知のトークンはそのまま残す。Discord 連携やテストから利用する。
     */
    static String substitutePlain(String template, Map<String, String> tokens) {
        Matcher matcher = TOKEN.matcher(template);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            sb.append(template, last, matcher.start());
            String replacement = tokens.get(matcher.group());
            sb.append(replacement != null ? replacement : matcher.group());
            last = matcher.end();
        }
        sb.append(template, last, template.length());
        return sb.toString();
    }

    /** ワールドの次元を日本語ラベルにする（オーバーワールド / ネザー / ジ・エンド）。 */
    private static String dimensionLabel(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> "ネザー";
            case THE_END -> "ジ・エンド";
            default -> "オーバーワールド";
        };
    }

    private Component prefix() {
        return Component.text("[DeathPoint] ", NamedTextColor.RED);
    }

    // ───────────────────────── 公開ヘルパー（コマンドから利用） ─────────────────────────

    /** config を再読み込みして設定値を反映する。死亡地点（deaths.yml）はメモリのまま保持する。 */
    public void reloadAll() {
        loadSettings();
    }

    public boolean isActive() {
        return active;
    }

    public boolean isNotifySound() {
        return notifySound;
    }

    /** discord.enabled の設定値（Discord への送信が config で有効か）。 */
    public boolean isDiscordEnabled() {
        return discordEnabled;
    }

    /** DiscordSRV プラグインが導入・有効化されているか（実際に送信できる状態か）。 */
    public boolean isDiscordAvailable() {
        return discord != null && discord.isAvailable();
    }

    /** Discord 連携の状態ラベル（ログ・status 表示用）。設定 OFF / 連携中 / 待機 の 3 状態。 */
    private String discordStatusLabel() {
        if (!discordEnabled) {
            return "OFF";
        }
        return isDiscordAvailable() ? "連携中" : "待機（DiscordSRV 未検出）";
    }

    /** 本人の最後の死亡地点（無ければ null）。 */
    public DeathRecord getLastDeath(Player player) {
        return lastDeath.get(player.getUniqueId());
    }

    /** 死亡地点レコードを表示用 Component に整形する（次元・ワールド名 + 座標）。 */
    public Component formatRecord(DeathRecord record) {
        World world = getServer().getWorld(record.world());
        String dimension = world != null ? dimensionLabel(world) : record.world();
        return Component.text(dimension + "（" + record.world() + "） ", NamedTextColor.WHITE)
                .append(Component.text("(" + record.x() + ", " + record.y() + ", " + record.z() + ")",
                        NamedTextColor.AQUA));
    }
}
