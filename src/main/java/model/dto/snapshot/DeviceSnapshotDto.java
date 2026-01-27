package model.dto.snapshot;

import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import lombok.Data;

/**
 * 对外暴露的设备状态快照 DTO
 * 避免直接依赖内部实体，实现接口层与领域模型解耦。
 */
@Data
public class DeviceSnapshotDto {
    private String id;
    private DeviceTypeEnum type;
    private DeviceStateEnum state;

    // 位置
    private Double posX;
    private Double posY;

    // 电量相关（仅电集卡有效）
    private Double powerLevel;
    private Boolean needCharge;

    // 当前绑定的作业指令
    private String currWiRefNo;
}