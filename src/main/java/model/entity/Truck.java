package model.entity;

import common.consts.DeviceTypeEnum;

/**
 * 集卡实体
 */
public class Truck extends BaseDevice {
    private Double powerLevel;      // 剩余电量
    private Integer laneNo;         // 车道号

    // Truck220 双箱限制 (0-关/1-装/2-卸/3-装卸都允许)
    private Integer dualCarryMode;

    // 充电相关
    private boolean needCharge;

    // 无参构造函数
    public Truck() {
        super();
    }

    // 带参构造函数
    public Truck(String id, DeviceTypeEnum type) {
        super(id, type);
    }

    // Getter 和 Setter 方法

    public Double getPowerLevel() {
        return powerLevel;
    }

    public void setPowerLevel(Double powerLevel) {
        this.powerLevel = powerLevel;
    }

    public Integer getLaneNo() {
        return laneNo;
    }

    public void setLaneNo(Integer laneNo) {
        this.laneNo = laneNo;
    }

    public Integer getDualCarryMode() {
        return dualCarryMode;
    }

    public void setDualCarryMode(Integer dualCarryMode) {
        this.dualCarryMode = dualCarryMode;
    }

    public boolean isNeedCharge() {
        return needCharge;
    }

    public void setNeedCharge(boolean needCharge) {
        this.needCharge = needCharge;
    }
}
