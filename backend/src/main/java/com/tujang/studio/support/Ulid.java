package com.tujang.studio.support;

import java.security.SecureRandom;

/**
 * ULID 生成器：26 个 Crockford Base32 字符 = 48 位毫秒时间 + 80 位随机。
 *
 * <p>选它而不是 UUID，是因为前缀按时间递增，主键在 InnoDB 里插入更局部，
 * 顺带也便于日志里按时间排序阅读。不保证同毫秒内单调递增——本题不需要。
 */
public final class Ulid {

    private static final char[] ENC = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulid() {
    }

    public static String next() {
        char[] out = new char[26];

        // 前 10 字符：48 位时间戳（高位补 0）
        long time = System.currentTimeMillis();
        for (int i = 9; i >= 0; i--) {
            out[i] = ENC[(int) (time & 0x1F)];
            time >>>= 5;
        }

        // 后 16 字符：80 位随机（16 × 5 位）
        for (int i = 10; i < 26; i++) {
            out[i] = ENC[RANDOM.nextInt(32)];
        }

        return new String(out);
    }
}
