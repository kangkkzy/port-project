package model.entity;

import common.consts.DeviceTypeEnum;

/**
 * 岸桥 (QC) 实体
 */
public class QcDevice extends BaseDevice {
    private String currentBlockBay; // 当前位置
    private String targetBlockBay;  // 目标移动位置
    private Double hoistSpeed;      // 吊箱速度

    //  无参构造函数
    public QcDevice() {
        super();
    }

    //  带参构造函数
    public QcDevice(String id) {
        super(id, DeviceTypeEnum.QC);
    }

    //Getter 和 Setter 方法

    public String getCurrentBlockBay() {
        return currentBlockBay;
    }

    public void setCurrentBlockBay(String currentBlockBay) {
        this.currentBlockBay = currentBlockBay;
    }

    public String getTargetBlockBay() {
        return targetBlockBay;
    }

    public void setTargetBlockBay(String targetBlockBay) {
        this.targetBlockBay = targetBlockBay;
    }

    public Double getHoistSpeed() {
        return hoistSpeed;
    }

    public void setHoistSpeed(Double hoistSpeed) {
        this.hoistSpeed = hoistSpeed;
    }
}