package com.tujang.studio.domain;

/**
 * 后台执行的三步，**顺序即声明顺序**（见 {@link #ORDER}）。
 *
 * <p>当前阶段与整体状态是两个独立维度：{@code PROCESSING + GENERATE} 才表示"正在生成"。
 */
public enum TaskStep {
    /** 素材检查：文件在不在、类型还对不对、能否解码 */
    ASSET_CHECK,
    /** 模拟生成：固定耗时，可注入失败 */
    GENERATE,
    /** 结果整理：把预置产物落成这次任务的结果 */
    FINALIZE;

    /** 唯一权威的执行顺序，执行器与测试都读它。 */
    public static final TaskStep[] ORDER = {ASSET_CHECK, GENERATE, FINALIZE};

    public String label() {
        return switch (this) {
            case ASSET_CHECK -> "素材检查";
            case GENERATE -> "模拟生成";
            case FINALIZE -> "结果整理";
        };
    }
}
