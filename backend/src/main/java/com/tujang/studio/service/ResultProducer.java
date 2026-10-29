package com.tujang.studio.service;

import com.tujang.studio.domain.TaskRow;

/**
 * 第三步「结果整理」：把一次任务的产物落成可访问的结果。
 *
 * <p>抽象成接口有两个原因：
 * 一是测试执行器时可以直接替换掉，专注验证"顺序与中断"；
 * 二是将来真做图像合成，只换实现，任务状态机不动。
 */
public interface ResultProducer {

    /** @return 可访问的结果地址与尺寸 */
    ProducedResult produce(TaskRow task);

    record ProducedResult(String url, int width, int height) {
    }
}
