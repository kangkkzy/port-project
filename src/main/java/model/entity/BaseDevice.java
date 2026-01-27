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

    //  基础信息
    private String id;               // 设备ID
    private DeviceTypeEnum type;     // 设备类型
    private DeviceStateEnum state = DeviceStateEnum.IDLE; // 当前状态

    //  物理属性
    private Double posX = 0.0;       // X坐标
    private Double posY = 0.0;       // Y坐标
    private Double speed;            // 移动速度

    //  业务数据
    private List<String> inFenceIds = new ArrayList<>();  // 目前的围栏ID
    private String currWiRefNo;                           // 当前绑定的任务号
    private List<String> notDoneWiList = new ArrayList<>(); // 待执行任务列表
    private Queue<Point> waypoints = new LinkedList<>();    // 待行驶的路径点队列

    //  移动插值辅助
    private Point lastStartPos;       // 上次出发点
    private Point currentTargetPos;   // 正在前往的目标点
    private long lastMoveStartTime;   // 上次出发时间

    @SuppressWarnings("unused")
    public BaseDevice(String id, DeviceTypeEnum type) {
        this.id = id;
        this.type = type;
    }

    /**
     * 核心逻辑：开始向下一个点移动
     */
    public void onMoveStart(long now, SimulationEngine engine, String parentEventId) {
        // 无路径点则停止
        if (waypoints.isEmpty()) {
            this.state = DeviceStateEnum.IDLE;
            return;
        }

        Point nextTarget = waypoints.peek();

        // 检查前方是否有关闭的栅栏
        Fence blockingFence = getBlockingFence(nextTarget);
        if (blockingFence != null) {
            this.state = DeviceStateEnum.WAITING;
            blockingFence.getWaitingTrucks().add(this.id); // 加入等待队列
            return;
        }

        // 计算限速后的实际速度
        double currentSpeed = applyFenceSpeedLimit(this.speed, nextTarget);

        // 更新状态为移动中
        this.state = DeviceStateEnum.MOVING;
        this.lastStartPos = new Point(this.posX, this.posY);
        this.currentTargetPos = nextTarget;
        this.lastMoveStartTime = now;

        // 计算到达所需秒数
        long travelTimeMS = GisUtil.calculateTravelTimeMS(new Point(this.posX, this.posY), nextTarget, currentSpeed);

        // 调度未来的到达事件
        SimEvent arrivalEvent = engine.scheduleEvent(parentEventId, now + travelTimeMS, EventTypeEnum.ARRIVAL, nextTarget);

        // 标记事件主体
        if (this.type == DeviceTypeEnum.ASC || this.type == DeviceTypeEnum.QC) {
            arrivalEvent.addSubject("CRANE", this.id);
        } else {
            arrivalEvent.addSubject("TRUCK", this.id);
        }
    }

    /**
     * 获取当前时刻的估算坐标
     */
    public Point getInterpolatedPos(long currentSimTime) {
        if (state != DeviceStateEnum.MOVING || currentTargetPos == null || lastStartPos == null) {
            return new Point(posX, posY);
        }

        double totalDist = GisUtil.getDistance(lastStartPos, currentTargetPos);
        if (totalDist <= 0.001) return currentTargetPos;

        // 计算移动进度
        long elapsedTime = currentSimTime - lastMoveStartTime;
        double movedDist = (elapsedTime / 1000.0) * speed;

        if (movedDist >= totalDist) return currentTargetPos;

        // 线性插值计算当前坐标
        double ratio = movedDist / totalDist;
        double newX = lastStartPos.getX() + (currentTargetPos.getX() - lastStartPos.getX()) * ratio;
        double newY = lastStartPos.getY() + (currentTargetPos.getY() - lastStartPos.getY()) * ratio;
        return new Point(newX, newY);
    }

    /**
     * 到达目的地后的回调
     */
    public void onArrival(Point reachedPoint, long now, SimulationEngine engine, String parentEventId) {
        Point currentPos = new Point(this.posX, this.posY);
        double distance = GisUtil.getDistance(currentPos, reachedPoint);

        //   更新物理坐标
        this.posX = reachedPoint.getX();
        this.posY = reachedPoint.getY();

        //   清理插值数据，移除已到达的点
        this.lastStartPos = null;
        this.currentTargetPos = null;
        waypoints.poll();

        //  电集卡扣减电量
        if (this instanceof Truck truck && this.type == DeviceTypeEnum.ELECTRIC_TRUCK) {
            double consume = distance * truck.getConsumeRate();
            double newPower = Math.max(0.0, truck.getPowerLevel() - consume);
            truck.setPowerLevel(newPower);
        }

        //   继续前往下一个点
        onMoveStart(now, engine, parentEventId);
    }

    // 检查目标点是否在"阻断"状态的围栏内
    private Fence getBlockingFence(Point target) {
        for (Fence fence : GlobalContext.getInstance().getFenceMap().values()) {
            if (fence.contains(target) && FenceStateEnum.BLOCKED.getCode().equals(fence.getStatus())) {
                return fence;
            }
        }
        return null;
    }

    // 获取目标点所在围栏的限速（取最小值）
    private double applyFenceSpeedLimit(double defaultSpeed, Point target) {
        for (Fence fence : GlobalContext.getInstance().getFenceMap().values()) {
            if (fence.contains(target) && fence.getSpeedLimit() != null) {
                return Math.min(defaultSpeed, fence.getSpeedLimit());
            }
        }
        return defaultSpeed;
    }
}