package model.entity;

import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备基类
 */
@Data
public abstract class BaseDevice {
    private String id;              // 设备编号
    private DeviceTypeEnum type;    // 设备类型
    private DeviceStateEnum state;  // 状态

    // 物理信息
    private Double posX;            // X坐标
    private Double posY;            // Y坐标
    private Double speed;           // 速度
    private List<String> inFenceIds;; // 所在的栅栏列表

    // 指令关联
    private String currWiRefNo;         // 当前指令
    private List<String> notDoneWiList; // 未完成指令list
}