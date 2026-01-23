package model.entity;

import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.FenceStateEnum;
import common.util.GisUtil;
import engine.Tickable;
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
public abstract class BaseDevice implements Tickable {
    private String id;              // 设备编号
    private DeviceTypeEnum type;    // 设备类型
    private DeviceStateEnum state;  // 状态

    // 物理信息
    private Double posX;            // X坐标
    private Double posY;            // Y坐标
    private Double speed;           // 速度

    // 初始化 List 防止空指针
    private List<String> inFenceIds = new ArrayList<>(); // 所在的栅栏列表

    // 指令关联
    private String currWiRefNo;         // 当前指令
    private List<String> notDoneWiList = new ArrayList<>(); // 未完成指令list

    //  外部算法规划好的路径点队列
    private Queue<Point> waypoints = new LinkedList<>();

    // 常用构造函数
    public BaseDevice(String id, DeviceTypeEnum type) {
        this.id = id;
        this.type = type;
    }

    /**
     * 接收外部算法的路径指令
     */
    public void assignWaypoints(List<Point> points) {
        this.waypoints.clear();
        this.waypoints.addAll(points);
        this.state = DeviceStateEnum.MOVING;
    }

    /**
     * 被 SimulationEngine 的定时器推动，每一帧执行一次
     */
    @Override
    public void tick(long deltaMS, long nowMS) {
        // 如果状态不是移动中 或者路径已经走完 则不执行任何物理操作
        if (state != DeviceStateEnum.MOVING || waypoints.isEmpty()) {
            return;
        }

        Point nextStep = waypoints.peek(); // 查看下一个目标点

        // 栅栏阻挡检测 判断前方路段是否被封锁
        if (isBlockedByFence(nextStep)) {
            // 状态改为等待 但保留在队列头 不丢弃任务 等待栅栏放行
            this.state = DeviceStateEnum.WAITING;
            return;
        }

        // 栅栏限速检测 获取当前路段的限速
        double currentSpeed = applyFenceSpeedLimit(this.speed, nextStep);

        // 物理移动 按计算好的速度向目标点移动
        boolean reached = GisUtil.moveTowards(this, nextStep, currentSpeed, deltaMS);

        // 到达逻辑
        if (reached) {
            waypoints.poll(); // 到达该点 移除
            if (waypoints.isEmpty()) {
                this.state = DeviceStateEnum.IDLE; // 路径全部走完 停车
            }
        }
    }

    /**
     * 检查下一步的目标点是否位于处于“锁死/禁止通行”状态的栅栏内
     */
    private boolean isBlockedByFence(Point target) {
        for (Fence fence : GlobalContext.getInstance().getFenceMap().values()) {
            // 如果目标点在栅栏内 且该栅栏的状态匹配 BLOCKED 的枚举 Code
            if (fence.contains(target) && FenceStateEnum.BLOCKED.getCode().equals(fence.getStatus())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据目标点所在的栅栏应用限速
     */
    private double applyFenceSpeedLimit(double defaultSpeed, Point target) {
        for (Fence fence : GlobalContext.getInstance().getFenceMap().values()) {
            // 如果目标点在栅栏内 且栅栏设置了限速
            if (fence.contains(target) && fence.getSpeedLimit() != null) {
                // 取车辆自身速度和栅栏限速中较小的一个
                return Math.min(defaultSpeed, fence.getSpeedLimit());
            }
        }
        return defaultSpeed;
    }
}