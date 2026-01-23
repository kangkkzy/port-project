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

    // 速度
    private Double speed;           // 速度 (米/秒)

    // 初始化 List 防止空指针
    private List<String> inFenceIds = new ArrayList<>(); // 所在的栅栏列表

    // 指令关联
    private String currWiRefNo;         // 当前指令
    private List<String> notDoneWiList = new ArrayList<>(); // 未完成指令list

    // 外部算法规划好的路径点队列
    private Queue<Point> waypoints = new LinkedList<>();

    /**
     *  常用构造函数：创建设备时 自动根据其类型赋予默认速度
     */
    public BaseDevice(String id, DeviceTypeEnum type) {
        this.id = id;
        this.type = type;
        // 直接从枚举元数据中获取该类设备的速度
        this.speed = type.getDefaultSpeed();
    }

    // 离散事件驱动逻辑

    public void onMoveStart(long now, SimulationEngine engine) {
        if (waypoints.isEmpty()) {
            this.state = DeviceStateEnum.IDLE;
            return;
        }

        Point nextTarget = waypoints.peek();

        //  栅栏阻挡检测
        Fence blockingFence = getBlockingFence(nextTarget);
        if (blockingFence != null) {
            this.state = DeviceStateEnum.WAITING;
            blockingFence.getWaitingTrucks().add(this.id);
            return;
        }

        //  栅栏限速检测：获取当前路段的限速
        double currentSpeed = applyFenceSpeedLimit(this.speed, nextTarget);

        //   计算物理到达时间
        this.state = DeviceStateEnum.MOVING;
        Point currentPos = new Point(this.posX, this.posY);
        long travelTimeMS = GisUtil.calculateTravelTimeMS(currentPos, nextTarget, currentSpeed);

        //  向未来注册 到达事件
        engine.scheduleEvent(now + travelTimeMS, EventTypeEnum.ARRIVAL, this.id, nextTarget);
    }

    public void onArrival(Point reachedPoint, long now, SimulationEngine engine) {
        this.posX = reachedPoint.getX();
        this.posY = reachedPoint.getY();
        waypoints.poll();
        onMoveStart(now, engine);
    }

    //  辅助判断逻辑

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