package model.entity;

import common.consts.FenceStateEnum;
import lombok.Data;

@Data
public class Fence {
    private String nodeId;      // 栅栏id
    private String blockCode;   // 箱区号

    // 圆心坐标与半径
    private Double posX;
    private Double posY;
    private Double radius;
    private Double speedLimit;  // 该区域限速

    // 状态控制 02-默认通行
    private String status = FenceStateEnum.PASSABLE.getCode();

    /**
     * 判断某个点是否在这条栅栏里面
     */
    public boolean contains(Point target) {
        double distance = Math.hypot(posX - target.getX(), posY - target.getY());
        return distance <= radius; // 距离小于半径 就在圈内
    }
}