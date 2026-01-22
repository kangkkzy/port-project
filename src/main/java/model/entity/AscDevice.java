package model.entity;

import common.consts.DeviceTypeEnum;
import java.util.ArrayList;
import java.util.List;

/**
 * 龙门吊 (ASC) 实体
 */
public class AscDevice extends BaseDevice {
    private String currentBlockBay;      // 当前位置
    private String targetBlockBay;       // 目标移动位置

    // List 负责的堆区
    private List<String> enabledRangeList = new ArrayList<>();

    private Double hoistSpeed;           // 移动速度

    //  无参构造函数
    public AscDevice() {
        super();
    }

    //  带参构造函数
    public AscDevice(String id) {
        super(id, DeviceTypeEnum.ASC);
    }

    // Getter 和 Setter 方法

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

    public List<String> getEnabledRangeList() {
        return enabledRangeList;
    }

    public void setEnabledRangeList(List<String> enabledRangeList) {
        this.enabledRangeList = enabledRangeList;
    }

    public Double getHoistSpeed() {
        return hoistSpeed;
    }

    public void setHoistSpeed(Double hoistSpeed) {
        this.hoistSpeed = hoistSpeed;
    }
}