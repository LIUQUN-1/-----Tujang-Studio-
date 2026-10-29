package com.tujang.studio.support;

import java.util.Locale;

/** 把字节数格式化成人能读的大小，用于错误文案（"文件 12.3 MB，超过上限 5 MB"）。 */
public final class Sizes {

    private Sizes() {
    }

    public static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return Math.round(bytes / 1024.0) + " KB";
        }
        double mb = bytes / (1024.0 * 1024.0);
        if (Math.abs(mb - Math.rint(mb)) < 1e-9) {
            return String.format(Locale.ROOT, "%.0f MB", mb);
        }
        return String.format(Locale.ROOT, "%.1f MB", mb);
    }
}
