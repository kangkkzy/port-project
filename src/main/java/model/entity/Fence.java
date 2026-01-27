package model.entity;

import common.consts.FenceStateEnum;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 栅栏实体
 * 修改注：彻底移除主动逻辑。
 * 栅栏只负责记录状态和当前被阻挡的车辆ID，绝不负责"自动唤醒"它们。
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

    // 阻塞队列：仅用于记录当前被该栅栏挡住的车辆ID
    // 外部算法可通过查询此列表获知拥堵情况
    private List<String> waitingTrucks = new ArrayList<>();

    // [已删除] releaseHeadway (自动释放间隔) - 所有的调度间隔由外部算法决定

    /**
     * 判断坐标是否在栅栏范围内
     */
    public boolean contains(Point target) {
        if (posX == null || posY == null || target == null) return false;
        double distance = Math.hypot(posX - target.getX(), posY - target.getY());
        return distance <= radius;
    }

    // [已删除] onOpen 方法。
    // 栅栏开启后，内部实体不做任何反应。外部算法需自行监测状态并下发指令唤醒车辆。
}