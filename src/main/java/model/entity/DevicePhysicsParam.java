package model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备物理参数实体
 * 从外部接口获取，存储设备级别的物理属性
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DevicePhysicsParam {

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 水平移动速度 (米/秒)
     */
    private Double horizontalSpeed;

    /**
     * 垂直起升/下降速度 (米/秒)
     */
    private Double verticalSpeed;

    /**
     * 能耗率 (每米消耗的电量百分比)
     */
    private Double powerConsumeRate;

    /**
     * 重载能耗系数
     */
    private Double loadedConsumeCoefficient;

    /**
     * 安全电量冗余阈值 (百分比)
     */
    private Double safePowerThreshold;

    /**
     * 最大电量 (千瓦时)
     */
    private Double maxPower;
}
