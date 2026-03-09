package model.entity;

import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备基类（瘦模型版本）
 *
 * 仅保留：
 * - 基础信息字段
 * - 虚拟状态插值字段（用于前端平滑动画）
 * - getRealTimePosX/Y 插值方法
 *
 * 所有调度逻辑已移至 Handler 处理。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseDevice {

    // ==================== 基础信息 ====================
    private String id;               // 设备ID
    private DeviceTypeEnum type;     // 设备类型
    private DeviceStateEnum state = DeviceStateEnum.IDLE; // 当前状态

    // ==================== 物理属性 ====================
    private Double posX = 0.0;       // X坐标
    private Double posY = 0.0;       // Y坐标
    private Double speed;            // 速度

    // ==================== 业务数据 ====================
    private List<String> inFenceIds = new ArrayList<>();  // 目前的围栏ID
    private String currWiRefNo;                           // 当前绑定的任务号
    private List<String> notDoneWiList = new ArrayList<>(); // 待执行任务列表

    // ==================== 剩余目标点列表（用于分段移动）====================
    private List<Point> remainingMoveTargets = new ArrayList<>();

    // ==================== 虚拟状态插值字段：用于前端平滑动画 ====================
    /** 当前移动段的起步时间（仿真时间） */
    private long moveStartTime;
    /** 当前移动段的起步X坐标 */
    private Double moveStartPosX;
    /** 当前移动段的起步Y坐标 */
    private Double moveStartPosY;
    /** 当前移动段的预计到达时间（仿真时间） */
    private long expectedArrivalTime;

    // ==================== 运动意图字段 ====================
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