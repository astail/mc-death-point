package io.github.astail.deathpoint;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /deathpoint コマンドの実処理とタブ補完。 */
public final class DeathPointCommand implements CommandExecutor, TabCompleter {

    private final DeathPointPlugin plugin;

    public DeathPointCommand(DeathPointPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendStatus(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> sendStatus(sender);
            case "reload" -> {
                if (requireManage(sender)) {
                    doReload(sender);
                }
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    // ───────────── サブコマンド ─────────────

    private void doReload(CommandSender sender) {
        plugin.reloadAll();
        sender.sendMessage(ok("設定を再読み込みしました（状態: " + (plugin.isActive() ? "ON" : "OFF") + "）。"));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(info("DeathPoint: " + (plugin.isActive() ? "ON" : "OFF")
                + " / 通知音 " + (plugin.isNotifySound() ? "あり" : "なし")
                + " / Discord " + discordStatusLabel()));
        if (sender instanceof Player player) {
            DeathPointPlugin.DeathRecord record = plugin.getLastDeath(player);
            if (record != null) {
                sender.sendMessage(info("あなたの最後の死亡地点: ").append(plugin.formatRecord(record)));
            } else {
                sender.sendMessage(info("あなたの死亡記録はまだありません。"));
            }
        }
        sendUsage(sender);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(info("/deathpoint status          - 現在の設定と自分の最後の死亡地点を表示"));
        if (sender.hasPermission("deathpoint.manage")) {
            sender.sendMessage(info("/deathpoint reload          - 設定を再読み込み"));
        }
    }

    // ───────────── 補助 ─────────────

    /** Discord 連携の状態ラベル（設定 OFF / 連携中 / 待機）。 */
    private String discordStatusLabel() {
        if (!plugin.isDiscordEnabled()) {
            return "OFF";
        }
        return plugin.isDiscordAvailable() ? "連携中" : "待機（DiscordSRV 未検出）";
    }

    private boolean requireManage(CommandSender sender) {
        if (sender.hasPermission("deathpoint.manage")) {
            return true;
        }
        sender.sendMessage(error("この操作（reload）はサーバー管理者のみ実行できます。"));
        return false;
    }

    private static Component ok(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    private static Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    private static Component info(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    // ───────────── タブ補完 ─────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("status"));
            if (sender.hasPermission("deathpoint.manage")) {
                subs.add("reload");
            }
            return prefix(subs, args[0]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> options, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
