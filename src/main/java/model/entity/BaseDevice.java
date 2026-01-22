package model.entity;

import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备基类
 */
public abstract class BaseDevice {
    private String id;              // 设备编号
    private DeviceTypeEnum type;    // 设备类型
    private DeviceStateEnum state;  // 状态

    // 物理信息
    private Double posX;            // X坐标
    private Double posY;            // Y坐标
    private Double speed;           // 速度

    private List<String> inFenceIds = new ArrayList<>(); // 所在的栅栏列表

    // 指令关联
    private String currWiRefNo;         // 当前指令号
    private List<String> notDoneWiList = new ArrayList<>(); // 未完成指令list

    // 无参构造函数
    public BaseDevice() {
    }

    // 构造函数
    public BaseDevice(String id, DeviceTypeEnum type) {
        this.id = id;
        this.type = type;
    }
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DeviceTypeEnum getType() {
        return type;
    }

    public void setType(DeviceTypeEnum type) {
        this.type = type;
    }

    public DeviceStateEnum getState() {
        return state;
    }

    public void setState(DeviceStateEnum state) {
        this.state = state;
    }

    public Double getPosX() {
        return posX;
    }

    public void setPosX(Double posX) {
        this.posX = posX;
    }

    public Double getPosY() {
        return posY;
    }

    public void setPosY(Double posY) {
        this.posY = posY;
    }

    public Double getSpeed() {
        return speed;
    }

    public void setSpeed(Double speed) {
        this.speed = speed;
    }

    public List<String> getInFenceIds() {
        return inFenceIds;
    }

    public void setInFenceIds(List<String> inFenceIds) {
        this.inFenceIds = inFenceIds;
    }

    public String getCurrWiRefNo() {
        return currWiRefNo;
    }

    public void setCurrWiRefNo(String currWiRefNo) {
        this.currWiRefNo = currWiRefNo;
    }

    public List<String> getNotDoneWiList() {
        return notDoneWiList;
    }

    public void setNotDoneWiList(List<String> notDoneWiList) {
        this.notDoneWiList = notDoneWiList;
    }
}
