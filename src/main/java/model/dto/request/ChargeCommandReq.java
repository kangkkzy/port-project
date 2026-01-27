package model.dto.request;

import lombok.Data;

/**
 * 外部算法发起充电的请求
 */
@Data
public class ChargeCommandReq {
    private String truckId;     // 需要充电的集卡编号
    private String stationId;   // 目标充电桩的编号
}