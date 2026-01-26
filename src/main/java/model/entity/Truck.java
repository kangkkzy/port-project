package model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 集卡实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Truck extends BaseDevice {
    private Double powerLevel;      // 剩余电量 (百分比)
    private Double consumeRate;     // 耗电率

    private Integer laneNo;         // 车道号

    // Truck220 双箱限制 (0-关/1-装/2-卸/3-装卸都允许)
    private Integer dualCarryMode;

    // 充电相关
    private boolean needCharge;     // 是否需要充电
    private String targetStationId; // 目标充电桩ID
}