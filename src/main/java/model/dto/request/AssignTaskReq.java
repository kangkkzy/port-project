package model.dto.request;

import common.consts.DeviceTypeEnum;
import lombok.Data;

/**
 * 任务指派请求
 * 支持对 油集卡(OIL_TRUCK)、电集卡(ELECTRIC_TRUCK)、龙门吊(ASC) 和 岸桥(QC) 下发工单
 */
@Data
public class AssignTaskReq {
    // 设备ID（单设备下发时使用）
    private String deviceId;

    // 设备类型（单设备下发时使用）
    private DeviceTypeEnum deviceType;

    // 工单编号
    private String wiRefNo;

    // 外部算法分配的集卡ID
    private String truckId;

    // 外部算法分配的岸桥或龙门吊ID
    private String craneId;
}