package io.github.astail.deathpoint;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Discord 連携で使うプレーンテキスト置換（{@link DeathPointPlugin#substitutePlain}）の単体テスト。
 * Bukkit に依存しない純粋な文字列処理なので、サーバーなしで検証できる。
 */
class SubstitutePlainTest {

    private static Map<String, String> deathTokens() {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("%player%", "astail");
        tokens.put("%world%", "world_nether");
        tokens.put("%dimension%", "ネザー");
        tokens.put("%x%", "128");
        tokens.put("%y%", "72");
        tokens.put("%z%", "-340");
        return tokens;
    }

    @Test
    void replacesAllDeathPlaceholders() {
        String result = DeathPointPlugin.substitutePlain(
                "%player% が %dimension%（%world%）の座標 (%x%, %y%, %z%) で力尽きました", deathTokens());
        assertEquals("astail が ネザー（world_nether）の座標 (128, 72, -340) で力尽きました", result);
    }

    @Test
    void keepsUnknownTokensAsIs() {
        String result = DeathPointPlugin.substitutePlain("%player% / %unknown% / 終", deathTokens());
        assertEquals("astail / %unknown% / 終", result);
    }

    @Test
    void handlesTemplateWithoutTokens() {
        String result = DeathPointPlugin.substitutePlain("プレースホルダなし", deathTokens());
        assertEquals("プレースホルダなし", result);
    }

    @Test
    void handlesAdjacentAndEdgeTokens() {
        String result = DeathPointPlugin.substitutePlain("%x%%y%%z%", deathTokens());
        assertEquals("12872-340", result);
    }

    @Test
    void handlesEmptyTemplate() {
        assertEquals("", DeathPointPlugin.substitutePlain("", deathTokens()));
    }
}
