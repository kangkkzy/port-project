package model.dto.request;

import lombok.Data;
import model.entity.Point;
import java.util.List;

@Data
public class MoveCommandReq {
    private String truckId;      // 控制哪辆车
    private List<Point> points;  // 途径点列表
}