package model.entity;

import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import common.util.GisUtil;
import engine.SimEvent;
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

    // 水平移动速度 由外部配置接口注入
    private Double speed;

    private List<String> inFenceIds = new ArrayList<>(); // 所在的栅栏列表

    private String currWiRefNo;         // 当前指令
    private List<String> notDoneWiList = new ArrayList<>(); // 未完成指令list

    private Queue<Point> waypoints = new LinkedList<>();

    /**
     * 构造函数
     */
    @SuppressWarnings("unused")
    public BaseDevice(String id, DeviceTypeEnum type) {
        this.id = id;
        this.type = type;
    }

    // 离散事件驱动逻辑 (通过了 parentEventId 进行事件溯源

    public void onMoveStart(long now, SimulationEngine engine, String parentEventId) {
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

        // 栅栏限速检测 (没有则使用设备自身的水平速度
        double currentSpeed = applyFenceSpeedLimit(this.speed, nextTarget);

        // 计算物理到达时间
        this.state = DeviceStateEnum.MOVING;
        Point currentPos = new Point(this.posX, this.posY);
        long travelTimeMS = GisUtil.calculateTravelTimeMS(currentPos, nextTarget, currentSpeed);

        // 创建到达事件 绑定上游的 MoveStart 事件ID
        SimEvent arrivalEvent = engine.scheduleEvent(parentEventId, now + travelTimeMS, EventTypeEnum.ARRIVAL, nextTarget);
        if (this.type == DeviceTypeEnum.ASC || this.type == DeviceTypeEnum.QC) {
            arrivalEvent.addSubject("CRANE", this.id);
        } else {
            arrivalEvent.addSubject("TRUCK", this.id);
        }
    }

    public void onArrival(Point reachedPoint, long now, SimulationEngine engine, String parentEventId) {
        Point currentPos = new Point(this.posX, this.posY);
        double distance = GisUtil.getDistance(currentPos, reachedPoint);

        this.posX = reachedPoint.getX();
        this.posY = reachedPoint.getY();
        waypoints.poll();
        if (this instanceof Truck truck && this.type == DeviceTypeEnum.ELECTRIC_TRUCK) {
            double consume = distance * truck.getConsumeRate();
            double newPower = Math.max(0.0, truck.getPowerLevel() - consume);
            truck.setPowerLevel(newPower);
        }

        // 递归调用继续移动 绑定 Arrival事件ID作为上游
        onMoveStart(now, engine, parentEventId);
    }

    // 辅助方法
    private Fence getBlockingFence(Point target) {
        for (Fence fence : GlobalContext.getInstance().getFenceMap().values()) {
            // 字符串匹配
            if (fence.contains(target) && FenceStateEnum.BLOCKED.getCode().equals(fence.getStatus())) {
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