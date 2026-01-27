package model.dto.snapshot;

import common.consts.BizTypeEnum;
import lombok.Data;

/**
 * 作业指令快照
 */
@Data
public class WorkInstructionSnapshotDto {
    private String wiRefNo;
    private String containerId;
    private BizTypeEnum moveKind;
    private String fromPos;
    private String toPos;
    private String wiStatus;
    private String dispatchCheId;
}