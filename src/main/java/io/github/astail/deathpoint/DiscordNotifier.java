package io.github.astail.deathpoint;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * DiscordSRV 連携。DiscordSRV が導入・有効なときに、死亡座標ログを Discord チャンネルへ流す。
 *
 * <p>DiscordSRV（および同梱の JDA）をコンパイル依存に加えず、すべてリフレクション経由で呼び出す。
 * これにより JDA のシェード差異やバージョン差異に強く、DiscordSRV が無い環境でもそのまま動く
 * （クラスが見つからなければ単にスキップする）。送信失敗は死亡通知本体を壊さないよう握りつぶし、
 * 警告ログだけ残す。</p>
 */
final class DiscordNotifier {

    private final DeathPointPlugin plugin;

    DiscordNotifier(DeathPointPlugin plugin) {
        this.plugin = plugin;
    }

    /** DiscordSRV プラグインが導入され、かつ有効化されているか。 */
    boolean isAvailable() {
        Plugin discordSrv = plugin.getServer().getPluginManager().getPlugin("DiscordSRV");
        return discordSrv != null && discordSrv.isEnabled();
    }

    /**
     * 指定チャンネル（空欄ならメインチャンネル）へプレーンテキストを送る。
     * DiscordSRV が無い・チャンネル未設定・送信失敗のいずれでも例外は投げない。
     *
     * @param channelName DiscordSRV の Channels で定義したゲーム内チャンネル名。空欄でメインチャンネル。
     * @param message     送信する本文（プレーンテキスト）。
     */
    void send(String channelName, String message) {
        if (message == null || message.isBlank() || !isAvailable()) {
            return;
        }
        try {
            Class<?> discordSrvClass = Class.forName("github.scarsz.discordsrv.DiscordSRV");
            Object discordSrv = discordSrvClass.getMethod("getPlugin").invoke(null);
            Object channel = resolveChannel(discordSrvClass, discordSrv, channelName);
            if (channel == null) {
                plugin.getLogger().warning("DiscordSRV の送信先チャンネルが見つかりませんでした（channel: "
                        + (channelName == null || channelName.isBlank() ? "(メイン)" : channelName)
                        + "）。Discord への送信をスキップします。");
                return;
            }
            Object action = invokeSendMessage(channel, message);
            if (action != null) {
                // JDA の RestAction#queue()（非ブロッキング送信）を呼ぶ。
                invokeNoArg(action, "queue");
            }
        } catch (ClassNotFoundException ex) {
            // DiscordSRV は居るが API クラスが見当たらない（バージョン差異など）。連携を諦める。
            plugin.getLogger().warning("DiscordSRV の API が見つからないため、Discord 連携をスキップしました。");
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.WARNING, "Discord への送信に失敗しました。", ex);
        }
    }

    /**
     * 送信先チャンネルを解決する。
     * チャンネル名が指定されていればそれを優先し、見つからなければメインチャンネルへフォールバックする。
     */
    private Object resolveChannel(Class<?> discordSrvClass, Object discordSrv, String channelName)
            throws ReflectiveOperationException {
        if (channelName != null && !channelName.isBlank()) {
            Object channel = discordSrvClass
                    .getMethod("getDestinationTextChannelForGameChannelName", String.class)
                    .invoke(discordSrv, channelName);
            if (channel != null) {
                return channel;
            }
        }
        return discordSrvClass.getMethod("getMainTextChannel").invoke(discordSrv);
    }

    /**
     * JDA の {@code MessageChannel#sendMessage(CharSequence)} をリフレクションで呼ぶ。
     * 戻り値は RestAction（後で queue() する）。CharSequence を受ける 1 引数版だけを選ぶ。
     */
    private static Object invokeSendMessage(Object channel, String message)
            throws ReflectiveOperationException {
        for (Method method : channel.getClass().getMethods()) {
            if (method.getName().equals("sendMessage")
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(String.class)) {
                method.setAccessible(true);
                return method.invoke(channel, message);
            }
        }
        throw new NoSuchMethodException("sendMessage(CharSequence) が見つかりませんでした");
    }

    /** 公開された無引数メソッドをリフレクションで呼ぶ（JDA 実装クラスのアクセス制約を回避）。 */
    private static void invokeNoArg(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }
}
