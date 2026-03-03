package model.dto.snapshot;

import lombok.Data;

/**
 * 集装箱快照 DTO
 * 用于前端渲染集装箱位置
 */
@Data
public class ContainerSnapshotDto {

    private String containerId;

    private String currentPos;    // 当前位置信息

    private String status;        // 状态

    private Double posX;          // X坐标
    private Double posY;          // Y坐标
}
