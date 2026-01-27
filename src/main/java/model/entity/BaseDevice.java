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
 * 核心原则：无状态记忆、无默认决策。一切行动听从外部指令。
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

    // [注意] 速度属性不再有默认值，必须由每次移动指令显式设置
    private Double speed;

    //  业务数据
    private List<String> inFenceIds = new ArrayList<>();  // 目前的围栏ID
    private String currWiRefNo;                           // 当前绑定的任务号
    private List<String> notDoneWiList = new ArrayList<>(); // 待执行任务列表

    // 单步目标点 (无队列)
    private Point currentTargetPos;

    //  移动插值辅助
    private Point lastStartPos;       // 上次出发点
    private long lastMoveStartTime;   // 上次出发时间

    @SuppressWarnings("unused")
    public BaseDevice(String id, DeviceTypeEnum type) {
        this.id = id;
        this.type = type;
    }

    /**
     * 核心逻辑：开始向目标点移动 (单步)
     */
    public void onMoveStart(long now, SimulationEngine engine, String parentEventId) {
        // 1. 严格参数校验
        if (currentTargetPos == null) {
            // 防御性编程：如果没有目标点，直接置为空闲，防止异常状态卡死
            this.state = DeviceStateEnum.IDLE;
            return;
        }

        // 必须由外部指定速度
        if (this.speed == null || this.speed <= 0) {
            throw new BusinessException(String.format("设备 [%s] 启动失败: 未设置移动速度，外部算法必须在指令中明确指定 speed。", this.id));
        }

        // 2. 校验是否已在目标位置
        Point currentPos = new Point(this.posX, this.posY);
        double arrivalThreshold = GlobalContext.getInstance()
                .getPhysicsConfig()
                .getArrivalThreshold();
        if (GisUtil.getDistance(currentPos, currentTargetPos) <= arrivalThreshold) {
            // 已经在位置上了，直接触发到达
            onArrival(currentTargetPos, now, engine, parentEventId);
            return;
        }

        // 3. 环境约束检查 (围栏)
        Fence blockingFence = getBlockingFence(currentTargetPos);
        if (blockingFence != null) {
            this.state = DeviceStateEnum.WAITING;
            blockingFence.getWaitingTrucks().add(this.id); // 加入围栏等待队列
            return;
        }

        // 4. 执行移动
        // 计算受环境影响后的实际速度 (例如围栏限速)
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
     * 到达目的地后的回调
     */
    public void onArrival(Point reachedPoint, long now, SimulationEngine engine, String parentEventId) {
        Point currentPos = new Point(this.posX, this.posY);
        double distance = GisUtil.getDistance(currentPos, reachedPoint);

        // 1. 更新物理坐标
        this.posX = reachedPoint.getX();
        this.posY = reachedPoint.getY();

        // 2. 物理结算 (如耗电)
        // 注意：耗电率属于设备固有物理属性，保留在此处计算是合理的
        if (this instanceof Truck truck && this.type == DeviceTypeEnum.ELECTRIC_TRUCK) {
            if (truck.getConsumeRate() != null && truck.getConsumeRate() > 0) {
                double consume = distance * truck.getConsumeRate();
                double newPower = Math.max(0.0, truck.getPowerLevel() - consume);
                truck.setPowerLevel(newPower);
            }
        }

        // 3. 状态清理
        this.lastStartPos = null;
        this.currentTargetPos = null;
        this.speed = null; // [关键] 清除速度，强制下一次移动必须重新指定

        // 4. 停止并等待
        // 只有收到下一个 CMD_MOVE 事件，状态才会再次变为 MOVING
        this.state = DeviceStateEnum.IDLE;
    }

    /**
     * 获取当前时刻的估算坐标 (插值)
     */
    public Point getInterpolatedPos(long currentSimTime) {
        // 如果不在移动状态，或者缺乏必要的插值参数，直接返回当前坐标
        if (state != DeviceStateEnum.MOVING || currentTargetPos == null || lastStartPos == null || speed == null) {
            return new Point(posX, posY);
        }

        double totalDist = GisUtil.getDistance(lastStartPos, currentTargetPos);
        double arrivalThreshold = GlobalContext.getInstance()
                .getPhysicsConfig()
                .getArrivalThreshold();
        if (totalDist <= arrivalThreshold) return currentTargetPos;

        long elapsedTime = currentSimTime - lastMoveStartTime;
        // 使用当前指令指定的速度进行插值
        double movedDist = (elapsedTime / 1000.0) * speed;

        if (movedDist >= totalDist) return currentTargetPos;

        double ratio = movedDist / totalDist;
        double newX = lastStartPos.getX() + (currentTargetPos.getX() - lastStartPos.getX()) * ratio;
        double newY = lastStartPos.getY() + (currentTargetPos.getY() - lastStartPos.getY()) * ratio;
        return new Point(newX, newY);
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