package com.tujang.studio.domain;

/** 任务整体状态。{@code SUCCEEDED}/{@code FAILED} 是吸收态，任何写操作都不能改回去。 */
public enum TaskStatus {
    /** 已受理，等后台线程接手 */
    PENDING,
    /** 执行中，具体走到哪一步看 current_step */
    PROCESSING,
    /** 三步全部成功，result_url 有值 */
    SUCCEEDED,
    /** 某一步失败，failed_step/error_code 有值，后续步骤不再执行 */
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
