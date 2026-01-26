package common.consts;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 作业指令状态
 */
@Getter
@AllArgsConstructor
public enum WiStatusEnum {
    PENDING("01", "接受指令等待处理"),
    SKIPPED("02", "跳过指令"),
    EXECUTING("03", "执行指令"),
    COMPLETED("04", "完成指令");

    private final String code;
    private final String desc;
}