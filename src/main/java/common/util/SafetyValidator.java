package common.util;

import common.consts.DeviceTypeEnum;
import model.bo.GlobalContext;
import model.entity.BaseDevice;
import model.entity.QcDevice;
import model.entity.Truck;

import java.util.*;
import java.util.stream.Collectors;

/**
 * [PDF Part 4] 物理安全与协同约束校验器
 * 涵盖：
 * 1. 4.5.2.1 岸桥防碰撞 (QC Interference) - 硬约束
 * 2. 4.5.2.4 时间协同 (Time Synchronization) - 软约束/目标函数
 */
public class SafetyValidator {

    private static final SafetyValidator INSTANCE = new SafetyValidator();

    // --- 物理参数常量 ---
    // 岸桥间的最小安全距离 (米)
    private static final double MIN_QC_DISTANCE = 15.0;
    // 集卡平均行驶速度 (米/秒) - 用于估算 ETA
    private static final double TRUCK_SPEED_MPS = 5.0;
    // 岸桥大车移动速度 (米/秒)
    private static final double QC_GANTRY_SPEED_MPS = 0.8;
    // 最大允许协同等待时间 (毫秒)，超过此值视为调度失败 (15分钟)
    private static final long MAX_SYNC_WAIT_MS = 15 * 60 * 1000;

    private SafetyValidator() {}

    public static SafetyValidator getInstance() {
        return INSTANCE;
    }

    /**
     * 1. 物理坐标解析器 (模拟 GIS 系统)
     * 将业务位置字符串转换为一维线性坐标 X (米)
     * 假设码头岸线平行于 X 轴
     */
    public double parsePositionToX(String pos) {
        if (pos == null) return 0.0;

        // 解析船位坐标: 假设每个 Bay 宽 20 米
        // 格式: "BAY01-01-01" -> 20.0
        if (pos.startsWith("BAY")) {
            try {
                String numPart = pos.split("-")[0].replace("BAY", "");
                int bayNum = Integer.parseInt(numPart);
                return bayNum * 20.0;
            } catch (Exception e) {
                // 解析失败默认返回 0
                return 0.0;
            }
        }

        // 解析堆场/闸口坐标
        if (pos.startsWith("YARD")) return 100.0; // 假设堆场交接区在 100m 处
        if (pos.equals("GATE")) return 500.0;     // 假设闸口在 500m 处

        return 0.0;
    }

    /**
     * 2. [PDF 4.5.2.1] 岸桥防碰撞校验 (No-Crossing Constraint)
     * 规则：物理顺序相邻的岸桥，其目标位置必须保持安全距离，且不能越过对方。
     * * @param targetQcId 移动的岸桥ID
     * @param targetPosX 目标位置X坐标
     * @return true=安全; false=存在碰撞风险
     */
    public boolean checkQcInterference(String targetQcId, double targetPosX) {
        Map<String, QcDevice> qcMap = GlobalContext.getInstance().getQcMap();

        // 1. 获取所有岸桥并按 ID 排序 (假设 ID 顺序 QC01, QC02... 代表物理部署顺序)
        List<QcDevice> sortedQcs = qcMap.values().stream()
                .sorted(Comparator.comparing(BaseDevice::getId))
                .collect(Collectors.toList());

        // 2. 找到当前岸桥的索引
        int myIndex = -1;
        for (int i = 0; i < sortedQcs.size(); i++) {
            if (sortedQcs.get(i).getId().equals(targetQcId)) {
                myIndex = i;
                break;
            }
        }

        if (myIndex == -1) return true; // 未知设备，默认放行或报错

        // 3. 检查左侧邻居 (Index < myIndex)
        // 规则：我的目标位置必须 > 左邻居当前位置 + 安全距离
        if (myIndex > 0) {
            QcDevice leftNeighbor = sortedQcs.get(myIndex - 1);
            if (targetPosX <= leftNeighbor.getPosX() + MIN_QC_DISTANCE) {
                System.out.printf(">>> [防碰撞拦截] %s(目标:%.1f) 过于靠近左侧 %s(当前:%.1f)，违反安全距离 %.1f%n",
                        targetQcId, targetPosX, leftNeighbor.getId(), leftNeighbor.getPosX(), MIN_QC_DISTANCE);
                return false;
            }
        }

        // 4. 检查右侧邻居 (Index > myIndex)
        // 规则：我的目标位置必须 < 右邻居当前位置 - 安全距离
        if (myIndex < sortedQcs.size() - 1) {
            QcDevice rightNeighbor = sortedQcs.get(myIndex + 1);
            if (targetPosX >= rightNeighbor.getPosX() - MIN_QC_DISTANCE) {
                System.out.printf(">>> [防碰撞拦截] %s(目标:%.1f) 过于靠近右侧 %s(当前:%.1f)，违反安全距离 %.1f%n",
                        targetQcId, targetPosX, rightNeighbor.getId(), rightNeighbor.getPosX(), MIN_QC_DISTANCE);
                return false;
            }
        }

        return true;
    }

    /**
     * 3. [PDF 4.5.2.4] 时间协同校验 (Time Synchronization)
     * 计算集卡和岸桥到达作业点的时间差 (ETA Delta)。
     * * @param truckId 集卡ID
     * @param qcId 岸桥ID
     * @param targetPos 目标作业位置
     * @return 预计等待时间(ms)。如果时间差过大(不可行)，返回 -1。
     */
    public long checkTimeSync(String truckId, String qcId, String targetPos) {
        Truck truck = GlobalContext.getInstance().getTruckMap().get(truckId);
        QcDevice qc = GlobalContext.getInstance().getQcMap().get(qcId);

        if (truck == null || qc == null) return 0L; // 设备不存在，无法计算，跳过

        double targetX = parsePositionToX(targetPos);

        // 计算集卡行程时间 (假设直线运动)
        double truckDist = Math.abs(targetX - truck.getPosX());
        long truckTravelTimeMs = (long) ((truckDist / TRUCK_SPEED_MPS) * 1000);

        // 计算岸桥行程时间
        double qcDist = Math.abs(targetX - qc.getPosX());
        long qcTravelTimeMs = (long) ((qcDist / QC_GANTRY_SPEED_MPS) * 1000);

        // 计算时间差 (谁先到谁等)
        long timeDiff = Math.abs(truckTravelTimeMs - qcTravelTimeMs);

        // 校验：如果等待时间超过阈值，视为协同严重失败 (违反软约束阈值)
        if (timeDiff > MAX_SYNC_WAIT_MS) {
            System.out.printf(">>> [协同失败] %s 与 %s 到达时间差 %.1f 分钟，超过阈值，调度驳回%n",
                    truckId, qcId, timeDiff / 60000.0);
            return -1L;
        }

        // 返回预计产生的等待成本 (用于目标函数统计)
        return timeDiff;
    }
}