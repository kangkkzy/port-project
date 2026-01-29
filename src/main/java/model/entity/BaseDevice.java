package model.entity;

import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import common.exception.BusinessException;
import common.util.GisUtil;
import engine.SimEvent;
import engine.SimulationEngine;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import model.bo.GlobalContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备基类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseDevice {

    //  基础信息
    private String id;               // 设备ID
    private DeviceTypeEnum type;     // 设备类型
    private DeviceStateEnum state = DeviceStateEnum.IDLE; // 当前状态

    //  物理属性
    private Double posX = 0.0;       // X坐标
    private Double posY = 0.0;       // Y坐标
    private Double speed;            // 速度

    //  业务数据
    private List<String> inFenceIds = new ArrayList<>();  // 目前的围栏ID
    private String currWiRefNo;                           // 当前绑定的任务号
    private List<String> notDoneWiList = new ArrayList<>(); // 待执行任务列表

    // 单步目标点
    private Point currentTargetPos;
    private Point lastStartPos;       // 上次出发点
    private long lastMoveStartTime;   // 上次出发时间

    @SuppressWarnings("unused")
    public BaseDevice(String id, DeviceTypeEnum type) {
        this.id = id;
        this.type = type;
    }

    /**
     * 核心逻辑：开始向目标点移动
     */
    public void onMoveStart(long now, SimulationEngine engine, String parentEventId) {
        //  参数校验
        if (currentTargetPos == null) {
            //  如果没有目标点 直接置为空闲
            this.state = DeviceStateEnum.IDLE;
            return;
        }

        // 必须由外部指定速度
        if (this.speed == null || this.speed <= 0) {
            throw new BusinessException(String.format("设备 [%s] 启动失败: 未设置移动速度，外部算法必须在指令中明确指定 speed。", this.id));
        }

        //   校验是否已在目标位置
        Point currentPos = new Point(this.posX, this.posY);
        double arrivalThreshold = GlobalContext.getInstance()
                .getPhysicsConfig()
                .getArrivalThreshold();
        if (GisUtil.getDistance(currentPos, currentTargetPos) <= arrivalThreshold) {
            // 已经在位置上了 直接触发到达
            onArrival(currentTargetPos, now, engine, parentEventId);
            return;
        }

        //  围栏检查
        Fence blockingFence = getBlockingFence(currentTargetPos);
        if (blockingFence != null) {
            this.state = DeviceStateEnum.WAITING;
            blockingFence.getWaitingTrucks().add(this.id); // 加入围栏等待队列
            return;
        }

        //  移动
        double actualSpeed = applyFenceSpeedLimit(this.speed, currentTargetPos);

        // 更新状态
        this.state = DeviceStateEnum.MOVING;
        this.lastStartPos = currentPos;
        this.lastMoveStartTime = now;

        // 计算物理耗时
        long travelTimeMS = GisUtil.calculateTravelTimeMS(currentPos, currentTargetPos, actualSpeed);

        // 调度到达事件
        SimEvent arrivalEvent = engine.scheduleEvent(parentEventId, now + travelTimeMS, EventTypeEnum.ARRIVAL, currentTargetPos);

        // 标记事件主体
        if (this.type == DeviceTypeEnum.ASC || this.type == DeviceTypeEnum.QC) {
            arrivalEvent.addSubject("CRANE", this.id);
        } else {
            arrivalEvent.addSubject("TRUCK", this.id);
        }
    }

    /**
     * 到达目的地后
     */
    public void onArrival(Point reachedPoint, long now, SimulationEngine engine, String parentEventId) {
        if (reachedPoint == null) {
            this.state = DeviceStateEnum.IDLE;
            this.lastStartPos = null;
            this.currentTargetPos = null;
            this.speed = null;
            return;
        }
        Point currentPos = new Point(this.posX, this.posY);
        double distance = GisUtil.getDistance(currentPos, reachedPoint);

        //  更新物理坐标
        this.posX = reachedPoint.getX() != null ? reachedPoint.getX() : this.posX;
        this.posY = reachedPoint.getY() != null ? reachedPoint.getY() : this.posY;

        //  物理结算 (耗电
        if (this instanceof Truck && this.type == DeviceTypeEnum.ELECTRIC_TRUCK) {
            Truck truck = (Truck) this;
            if (truck.getConsumeRate() != null && truck.getConsumeRate() > 0) {
                double consume = distance * truck.getConsumeRate();
                double newPower = Math.max(0.0, truck.getPowerLevel() - consume);
                truck.setPowerLevel(newPower);
            }
        }

        //  状态清理
        this.lastStartPos = null;
        this.currentTargetPos = null;
        this.speed = null;

        //  停止并等待
        this.state = DeviceStateEnum.IDLE;
    }

    /**
     * 查询/展示用：在给定仿真时刻的估算坐标（离散仿真中真实位置仅在 ARRIVAL 事件时更新，此处为线性插值估算）
     */
    public Point getInterpolatedPos(long currentSimTime) {
        if (state != DeviceStateEnum.MOVING || currentTargetPos == null || lastStartPos == null || speed == null) {
            return new Point(posX, posY);
        }

        double totalDist = GisUtil.getDistance(lastStartPos, currentTargetPos);
        double arrivalThreshold = GlobalContext.getInstance()
                .getPhysicsConfig()
                .getArrivalThreshold();
        if (totalDist <= arrivalThreshold) return currentTargetPos;

        long elapsedTime = currentSimTime - lastMoveStartTime;
        double movedDist = (elapsedTime / 1000.0) * speed;

        if (movedDist >= totalDist) return currentTargetPos;

        double ratio = movedDist / totalDist;
        double newX = lastStartPos.getX() + (currentTargetPos.getX() - lastStartPos.getX()) * ratio;
        double newY = lastStartPos.getY() + (currentTargetPos.getY() - lastStartPos.getY()) * ratio;
        return new Point(newX, newY);
    }

    // 检查目标点是否在 阻断 状态的围栏内
    private Fence getBlockingFence(Point target) {
        for (Fence fence : GlobalContext.getInstance().getFenceMap().values()) {
            if (fence.contains(target) && FenceStateEnum.BLOCKED.getCode().equals(fence.getStatus())) {
                return fence;
            }
        }
        return null;
    }

    // 获取目标点所在围栏的限速 取小
    private double applyFenceSpeedLimit(double defaultSpeed, Point target) {
        for (Fence fence : GlobalContext.getInstance().getFenceMap().values()) {
            if (fence.contains(target) && fence.getSpeedLimit() != null) {
                return Math.min(defaultSpeed, fence.getSpeedLimit());
            }
        }
        return defaultSpeed;
    }
}