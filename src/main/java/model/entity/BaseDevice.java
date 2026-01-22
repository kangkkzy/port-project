package model.entity;

import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备基类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseDevice {
    private String id;              // 设备编号
    private DeviceTypeEnum type;    // 设备类型
    private DeviceStateEnum state;  // 状态

    // 物理信息
    private Double posX;            // X坐标
    private Double posY;            // Y坐标
    private Double speed;           // 速度

    // 初始化 List 防止空指针
    private List<String> inFenceIds = new ArrayList<>(); // 所在的栅栏列表

    // 指令关联
    private String currWiRefNo;         // 当前指令
    private List<String> notDoneWiList = new ArrayList<>(); // 未完成指令list

    // 常用构造函数
    public BaseDevice(String id, DeviceTypeEnum type) {
        this.id = id;
        this.type = type;
    }
}