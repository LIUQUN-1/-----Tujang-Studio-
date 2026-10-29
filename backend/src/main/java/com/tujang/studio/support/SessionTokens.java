package com.tujang.studio.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 会话 token 的生成与摘要。
 *
 * <p>为什么库里存哈希而不是 token 本身：数据库被读走时，攻击者拿到的是
 * {@code sha256(token)}，没法反推出可用的 Cookie 值。token 本身是 32 字节
 * {@link SecureRandom}，不需要加盐（没有"低熵口令"的问题）。
 */
public final class SessionTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private SessionTokens() {
    }

    /** 新的随机 token（base64url，无填充），直接放进 Cookie。 */
    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 入库/查库用的摘要（十六进制小写，64 字符，正好对上 CHAR(64)）。 */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // 每个 JVM 都必须支持 SHA-256；不支持说明运行环境坏了
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }
}
