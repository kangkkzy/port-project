package model.entity;

import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import engine.SimulationEngine;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 栅栏/电子围栏
 */
@Data
public class Fence {
    private String nodeId;      // 栅栏id
    private String blockCode;   // 箱区号
    private Double posX;        // x坐标
    private Double posY;        // y坐标
    private Double radius;
    private Double speedLimit;  // 速度限制

    // 默认通行 (02)
    private String status = FenceStateEnum.PASSABLE.getCode();

    //  被这个栅栏挡住的车辆 ID 列表
    private List<String> waitingTrucks = new ArrayList<>();

    public boolean contains(Point target) {
        double distance = Math.hypot(posX - target.getX(), posY - target.getY());
        return distance <= radius;
    }

    /**
     * 响应事件：栅栏开启
     */
    public void onOpen(long now, SimulationEngine engine) {
        this.status = FenceStateEnum.PASSABLE.getCode(); // [cite: 280]

        //  唤醒所有等待的集卡 恢复它们的移动
        for (String truckId : waitingTrucks) {
            engine.scheduleEvent(now, EventTypeEnum.MOVE_START, truckId, null);
        }
        waitingTrucks.clear();
    }
}