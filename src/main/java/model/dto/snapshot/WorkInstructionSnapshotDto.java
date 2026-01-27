package model.dto.snapshot;

import common.consts.BizTypeEnum;
import lombok.Data;

/**
 * 作业指令快照
 */
@Data
public class WorkInstructionSnapshotDto {
    private String wiRefNo;  // 作业号
    private String containerId;  // 货物id
    private BizTypeEnum moveKind;  // 作业类型
    private String fromPos;  // 起始位置
    private String toPos;  // 终点位置
    private String wiStatus;  // 当前状态
    private String dispatchCheId;  // 执行设备id
}