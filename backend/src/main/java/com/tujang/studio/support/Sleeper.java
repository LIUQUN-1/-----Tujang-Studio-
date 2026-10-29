package com.tujang.studio.support;

/**
 * 让"等一会儿"这件事可以注入。
 *
 * <p>生产实现就是 {@code Thread.sleep}；测试里换成记录调用的空实现，
 * 这样单测不会真的等 3 秒，也不会因为机器慢而 flaky。
 */
@FunctionalInterface
public interface Sleeper {

    void sleep(long millis) throws InterruptedException;
}
