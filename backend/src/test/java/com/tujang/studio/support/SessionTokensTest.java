package com.tujang.studio.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionTokensTest {

    @Test
    @DisplayName("每次生成的 token 都不同且足够长")
    void tokensAreUniqueAndLongEnough() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            tokens.add(SessionTokens.newToken());
        }
        assertThat(tokens).hasSize(200);
        assertThat(SessionTokens.newToken()).hasSizeGreaterThanOrEqualTo(43); // 32 字节 base64url
    }

    @Test
    @DisplayName("token 是 URL 安全的，能直接放进 Cookie")
    void tokenIsUrlSafe() {
        String token = SessionTokens.newToken();
        assertThat(token).matches("[A-Za-z0-9_-]+");
    }

    @Test
    @DisplayName("摘要稳定、长度为 64、且不含原始 token")
    void hashIsStableSha256Hex() {
        String token = "a-fixed-token-value";
        String hash = SessionTokens.hash(token);

        assertThat(hash).isEqualTo(SessionTokens.hash(token));
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hash).doesNotContain(token);
    }
}
