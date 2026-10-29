package com.tujang.studio.service;

import com.tujang.studio.domain.TaskStep;

/**
 * 某一步失败。携带稳定错误码与面向用户的原因，由执行器统一落库。
 *
 * <p>{@code step} 由抛出方给全，避免执行器还要自己记"我刚才在哪一步"——
 * 那个状态一旦记错，失败阶段就会写错。
 */
public class StepFailure extends RuntimeException {

    private final TaskStep step;
    private final String code;

    public StepFailure(TaskStep step, String code, String message) {
        super(message);
        this.step = step;
        this.code = code;
    }

    public StepFailure(TaskStep step, String code, String message, Throwable cause) {
        super(message, cause);
        this.step = step;
        this.code = code;
    }

    public TaskStep step() {
        return step;
    }

    public String code() {
        return code;
    }
}
