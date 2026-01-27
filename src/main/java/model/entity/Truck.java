package model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 集卡实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Truck extends BaseDevice {

    // 常量 最大电量百分比
    public static final double MAX_POWER_LEVEL = 100.0;

    private Double powerLevel;      // 剩余电量 (百分比)
    private Double consumeRate;     // 耗电率

    private Integer laneNo;         // 车道号
    private Integer dualCarryMode;  // 双箱限制

    // 充电相关
    private boolean needCharge;     // 状态标记：是否需要充电 (仅作信息展示)
    private String targetStationId; // 目标充电桩ID (用于充电时的校验)
}