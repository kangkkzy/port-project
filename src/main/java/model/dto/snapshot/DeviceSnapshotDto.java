package model.dto.snapshot;

import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import lombok.Data;

/**
 * 设备状态快照
 * 包含前端插值所需的完整运动信息，让前端可以自行计算平滑动画
 */
@Data
public class DeviceSnapshotDto {
    private String id;
    private DeviceTypeEnum type;
    private DeviceStateEnum state;

    // 当前位置（离散事件触发时的真实位置）
    private Double posX;
    private Double posY;

    // 电量相关（仅电集卡有效）
    private Double powerLevel;
    private Boolean needCharge;

    // 当前绑定的作业指令
    private String currWiRefNo;

    // ==================== 前端插值所需字段 ====================
    /** 移动起步时间（仿真时间戳） */
    private Long moveStartTime;
    /** 移动起步X坐标 */
    private Double moveStartPosX;
    /** 移动起步Y坐标 */
    private Double moveStartPosY;
    /** 目标X坐标（意图） */
    private Double targetX;
    /** 目标Y坐标（意图） */
    private Double targetY;
    /** 预计到达时间（仿真时间戳） */
    private Long expectedArrivalTime;
}