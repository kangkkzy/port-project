package model.dto.request;

import common.consts.DeviceTypeEnum;
import lombok.Data;

/**
 * 任务指派请求
 * 支持对 油集卡(OIL_TRUCK)、电集卡(ELECTRIC_TRUCK)、龙门吊(ASC) 和 岸桥(QC) 下发工单
 */
@Data
public class AssignTaskReq {
    // 设备ID
    private String deviceId;

    // 设备类型
    private DeviceTypeEnum deviceType;

    // 工单编号
    private String wiRefNo;
}