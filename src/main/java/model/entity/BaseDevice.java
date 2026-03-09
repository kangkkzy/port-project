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
import engine.context.GlobalContext;

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

    // === 虚拟状态插值字段：用于前端平滑动画 ===
    /** 当前移动段的起步时间（仿真时间） */
    private long moveStartTime;
    /** 当前移动段的起步X坐标 */
    private Double moveStartPosX;
    /** 当前移动段的起步Y坐标 */
    private Double moveStartPosY;
    /** 当前移动段的预计到达时间（仿真时间） */
    private long expectedArrivalTime;

    // 剩余目标点列表（用于分段移动，经过每个节点）
    private List<Point> remainingMoveTargets = new ArrayList<>();

    // === 运动意图字段：由指令处理器写入，由引擎 Tick 推演 ===
    /** 目标 X 坐标（意图） */
    protected Double targetX;
    /** 目标 Y 坐标（意图） */
    protected Double targetY;
    /** 移动速度（米/秒），由指令指定 */
    protected Double moveSpeed;

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

        // 修复：即使已经在位置上，也不要直接调用 onArrival，而是调度一个立即执行的 ARRIVAL 事件
        // 这样可以确保 SimulationEngine 中的 ArrivalHandler 被触发，从而正确生成 REPORT_IDLE 事件并记录日志
        long travelTimeMS = 0;

        if (GisUtil.getDistance(currentPos, currentTargetPos) > arrivalThreshold) {
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
            travelTimeMS = GisUtil.calculateTravelTimeMS(currentPos, currentTargetPos, actualSpeed);
        }

        // 调度到达事件 (即使耗时为0也调度，保持事件链完整)
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

        //  清理运动意图字段（由 CmdMoveHandler/CmdCraneMoveHandler 设置）
        this.targetX = null;
        this.targetY = null;
        this.moveSpeed = null;

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

    // ==================== 虚拟状态插值方法 ====================
    // 用于前端平滑动画：在离散事件之间，通过线性插值计算实时坐标

    /**
     * 获取实时X坐标（线性插值）
     *
     * @param currentSimTime 当前仿真时间
     * @return 插值后的实时X坐标
     */
    public double getRealTimePosX(long currentSimTime) {
        // 如果当前状态不是 MOVING，或者没有移动段信息，直接返回真实坐标
        if (state != DeviceStateEnum.MOVING || moveStartPosX == null || expectedArrivalTime <= moveStartTime) {
            return posX != null ? posX : 0.0;
        }

        // 如果已经超过预计到达时间，返回目标坐标
        if (currentSimTime >= expectedArrivalTime) {
            return targetX != null ? targetX : posX;
        }

        // 如果在移动过程中，计算线性插值
        if (currentSimTime > moveStartTime) {
            double progress = (currentSimTime - moveStartTime) / (double)(expectedArrivalTime - moveStartTime);
            // 限制 progress 在 [0, 1] 范围内
            progress = Math.max(0.0, Math.min(1.0, progress));
            return moveStartPosX + (targetX - moveStartPosX) * progress;
        }

        // 如果还未到达起步时间，返回起步坐标
        return moveStartPosX;
    }

    /**
     * 获取实时Y坐标（线性插值）
     *
     * @param currentSimTime 当前仿真时间
     * @return 插值后的实时Y坐标
     */
    public double getRealTimePosY(long currentSimTime) {
        // 如果当前状态不是 MOVING，或者没有移动段信息，直接返回真实坐标
        if (state != DeviceStateEnum.MOVING || moveStartPosY == null || expectedArrivalTime <= moveStartTime) {
            return posY != null ? posY : 0.0;
        }

        // 如果已经超过预计到达时间，返回目标坐标
        if (currentSimTime >= expectedArrivalTime) {
            return targetY != null ? targetY : posY;
        }

        // 如果在移动过程中，计算线性插值
        if (currentSimTime > moveStartTime) {
            double progress = (currentSimTime - moveStartTime) / (double)(expectedArrivalTime - moveStartTime);
            // 限制 progress 在 [0, 1] 范围内
            progress = Math.max(0.0, Math.min(1.0, progress));
            return moveStartPosY + (targetY - moveStartPosY) * progress;
        }

        // 如果还未到达起步时间，返回起步坐标
        return moveStartPosY;
    }
}