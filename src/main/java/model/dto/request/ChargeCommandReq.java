package model.dto.request;

import lombok.Data;
import model.entity.Point;
import java.util.List;

/**
 * 外部算法发起充电的请求
 */
@Data
public class ChargeCommandReq {
    private String truckId;     // 需要充电的集卡编号
    private String stationId;   // 目标充电桩的编号

    //  路径点列表 必须由外部算法规划并下发
    private List<Point> points;
}
