package model.dto.request;

import common.consts.DeviceStateEnum;
import lombok.Data;

/**
 * 龙门吊/桥吊移动请求 (支持区分横向移动与垂直起升)
 */
@Data
public class CraneMoveReq {
    private String craneId;          // QC 或 ASC 的 ID
    // 移动类型 (使用DeviceStateEnum.MOVE_HORIZONTAL 或 MOVE_VERTICAL)
    private DeviceStateEnum moveType;
    private Double distance;         // 移动距离 (米)
}