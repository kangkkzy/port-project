package model.entity;

import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import engine.SimEvent;
import engine.SimulationEngine;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 栅栏
 */
@Data
public class Fence {
    private String nodeId;
    private String blockCode;
    private Double posX;
    private Double posY;
    private Double radius;
    private Double speedLimit;

    private String status = FenceStateEnum.PASSABLE.getCode();

    // 被挡住的车辆ID列表
    private List<String> waitingTrucks = new ArrayList<>();

    private long releaseHeadway = 2000L;

    public boolean contains(Point target) {
        double distance = Math.hypot(posX - target.getX(), posY - target.getY());
        return distance <= radius;
    }

    public void onOpen(long now, SimulationEngine engine, String parentEventId) {
        this.status = FenceStateEnum.PASSABLE.getCode();

        long delay = 0;
        for (String truckId : waitingTrucks) {
            SimEvent moveEvent = engine.scheduleEvent(parentEventId, now + delay, EventTypeEnum.MOVE_START, null);
            moveEvent.addSubject("TRUCK", truckId);
            delay += releaseHeadway;
        }
        waitingTrucks.clear();
    }
}