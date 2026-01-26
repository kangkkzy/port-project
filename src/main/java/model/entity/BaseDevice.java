package model.entity;

import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import common.util.GisUtil;
import engine.SimulationEngine;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import model.bo.GlobalContext;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * 设备基类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseDevice {
    private String id;              // 设备编号
    private DeviceTypeEnum type;    // 设备类型
    private DeviceStateEnum state = DeviceStateEnum.IDLE;  // 状态

    // 物理信息
    private Double posX = 0.0;      // X坐标
    private Double posY = 0.0;      // Y坐标

    // 水平移动速度 (米/秒) 由外部配置接口注入
    private Double speed;

    private List<String> inFenceIds = new ArrayList<>(); // 所在的栅栏列表

    private String currWiRefNo;         // 当前指令
    private List<String> notDoneWiList = new ArrayList<>(); // 未完成指令list

    private Queue<Point> waypoints = new LinkedList<>();

    /**
     * 构造函数
     */
    public BaseDevice(String id, DeviceTypeEnum type) {
        this.id = id;
        this.type = type;
    }

    // 离散事件驱动逻辑

    public void onMoveStart(long now, SimulationEngine engine) {
        if (waypoints.isEmpty()) {
            this.state = DeviceStateEnum.IDLE;
            return;
        }

        Point nextTarget = waypoints.peek();

        // 栅栏阻挡检测
        Fence blockingFence = getBlockingFence(nextTarget);
        if (blockingFence != null) {
            this.state = DeviceStateEnum.WAITING;
            blockingFence.getWaitingTrucks().add(this.id);
            return;
        }

        // 栅栏限速检测 (如果没有限速，则使用设备自身的水平速度)
        double currentSpeed = applyFenceSpeedLimit(this.speed, nextTarget);

        // 计算物理到达时间
        this.state = DeviceStateEnum.MOVING;
        Point currentPos = new Point(this.posX, this.posY);
        long travelTimeMS = GisUtil.calculateTravelTimeMS(currentPos, nextTarget, currentSpeed);

        engine.scheduleEvent(now + travelTimeMS, EventTypeEnum.ARRIVAL, this.id, nextTarget);
    }

    public void onArrival(Point reachedPoint, long now, SimulationEngine engine) {
        Point currentPos = new Point(this.posX, this.posY);
        double distance = GisUtil.getDistance(currentPos, reachedPoint);

        this.posX = reachedPoint.getX();
        this.posY = reachedPoint.getY();
        waypoints.poll();

        if (this instanceof Truck && this.type == DeviceTypeEnum.ELECTRIC_TRUCK) {
            Truck truck = (Truck) this;
            double consume = distance * truck.getConsumeRate();
            double newPower = Math.max(0.0, truck.getPowerLevel() - consume);
            truck.setPowerLevel(newPower);
        }

        onMoveStart(now, engine);
    }

    // 辅助方法...
    private Fence getBlockingFence(Point target) {
        for (Fence fence : GlobalContext.getInstance().getFenceMap().values()) {
            if (fence.contains(target) && FenceStateEnum.BLOCKED.equals(fence.getStatus())) {
                return fence;
            }
        }
        return null;
    }

    private double applyFenceSpeedLimit(double defaultSpeed, Point target) {
        for (Fence fence : GlobalContext.getInstance().getFenceMap().values()) {
            if (fence.contains(target) && fence.getSpeedLimit() != null) {
                return Math.min(defaultSpeed, fence.getSpeedLimit());
            }
        }
        return defaultSpeed;
    }
}