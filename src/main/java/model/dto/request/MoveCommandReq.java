package model.dto.request;

import lombok.Data;
import model.entity.Point;

@Data
public class MoveCommandReq {
    private String truckId;      // 控制哪辆车
    private Point targetPoint;   // 单次移动的目标点 (点对点)
    private Double speed;        // 外部算法指定的本次移动速度 (空则使用设备速度
}