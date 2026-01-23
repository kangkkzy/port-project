package common.consts;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 栅栏状态枚举
 */
@Getter
@AllArgsConstructor
public enum FenceStateEnum {
    BLOCKED("01", "禁止通行/提前锁死"),
    PASSABLE("02", "通行");

    private final String code;
    private final String desc;
}