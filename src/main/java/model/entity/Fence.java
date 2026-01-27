package model.entity;

import common.consts.FenceStateEnum;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 栅栏实体
 */
@Data
public class Fence {
    private String nodeId;
    private String blockCode;
    private Double posX;
    private Double posY;
    private Double radius;
    private Double speedLimit;

    // 默认状态
    private String status = FenceStateEnum.PASSABLE.getCode();

    // 阻塞队列 用于记录当前被该栅栏挡住的车辆ID
    // 外部算法可通过查询此列表获知拥堵情况
    private List<String> waitingTrucks = new ArrayList<>();

    /**
     * 判断坐标是否在栅栏范围内
     */
    public boolean contains(Point target) {
        if (posX == null || posY == null || target == null) return false;
        double distance = Math.hypot(posX - target.getX(), posY - target.getY());
        return distance <= radius;
    }
}