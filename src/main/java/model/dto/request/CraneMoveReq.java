package model.dto.request;

import common.consts.DeviceStateEnum;
import lombok.Data;

/**
 * 龙门吊/桥吊移动请求
 * 修改注：外部算法必须指定速度，内部不再读取默认配置
 */
@Data
public class CraneMoveReq {
    private String craneId;          // QC 或 ASC 的 ID
    private DeviceStateEnum moveType;// 移动类型 (水平/垂直)
    private Double distance;         // 移动距离 (米)

    // 由外部算法指定
    private Double speed;            // 移动速度 (米/秒)
}