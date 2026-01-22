package model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 集卡实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Truck extends BaseDevice {
    private Double powerLevel;      // 剩余电量
    private Integer laneNo;         // 车道号

    // Truck220 双箱限制 (0-关/1-装/2-卸/3-装卸都允许)
    private Integer dualCarryMode;

    // 充电相关
    private boolean needCharge;
}