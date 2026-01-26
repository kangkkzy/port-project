package model.entity;

import common.consts.DeviceStateEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 充电桩实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChargingStation {
    private String stationCode; // 充电桩编号
    private String status;      // 充电桩状态
    private String powName;     // 所属工作点
    private String blockCode;   // 所属箱区
    private Integer rowPosition;// 所属贝位

    private Double posX;        // x坐标
    private Double posY;        // y坐标

    private String truckId;     // 当前正在充电的集卡编号
    private Integer portCode;   // 充电接口数

    // 充电速率
    private Double chargeRate;

    /**
     * 判断充电桩是否空闲可用
     */
    public boolean isAvailable() {
        return DeviceStateEnum.IDLE.getCode().equals(status) && truckId == null;
    }
}